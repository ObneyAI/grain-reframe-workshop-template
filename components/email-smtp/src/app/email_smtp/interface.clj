(ns app.email-smtp.interface
  "A deliberately local-only SMTP adapter. It speaks unauthenticated SMTP for
   Mailpit and similar development inboxes; production configuration rejects
   this adapter so TLS and provider credentials cannot be accidentally skipped."
  (:require [app.email.interface :as email]
            [clojure.string :as string])
  (:import [java.io BufferedReader BufferedWriter InputStreamReader OutputStreamWriter]
           [java.net Socket]
           [java.nio.charset StandardCharsets]
           [java.time ZonedDateTime ZoneOffset]
           [java.time.format DateTimeFormatter]
           [java.util Base64 UUID]))

(defn- clean-header
  [value]
  (let [value (str value)]
    (when (re-find #"[\r\n]" value)
      (throw (ex-info "Email headers may not contain newlines" {})))
    value))

(defn- encoded-header
  [value]
  (str "=?UTF-8?B?"
       (.encodeToString (Base64/getEncoder)
                        (.getBytes (clean-header value) StandardCharsets/UTF_8))
       "?="))

(defn- encoded-body
  [value]
  (.encodeToString (Base64/getMimeEncoder 76 (.getBytes "\r\n" StandardCharsets/US_ASCII))
                   (.getBytes (or value "") StandardCharsets/UTF_8)))

(defn message-data
  "Build the RFC 5322 message sent after SMTP DATA. Public for focused adapter
   verification; callers should use app.email.interface/send."
  [args]
  (let [{:keys [from to cc reply-to subject body-text body-html]}
        (email/normalize-message args)
        boundary (str "grain-" (UUID/randomUUID))
        headers (cond-> [(str "From: " (clean-header from))
                         (str "To: " (string/join ", " (map clean-header to)))
                         (str "Subject: " (encoded-header subject))
                         (str "Date: " (.format DateTimeFormatter/RFC_1123_DATE_TIME
                                                (ZonedDateTime/now ZoneOffset/UTC)))
                         (str "Message-ID: <" (UUID/randomUUID) "@grain.local>")
                         "MIME-Version: 1.0"]
                  (seq cc) (conj (str "Cc: " (string/join ", " (map clean-header cc))))
                  (seq reply-to) (conj (str "Reply-To: "
                                             (string/join ", " (map clean-header reply-to)))))
        body (if (and body-text body-html)
               (string/join
                "\r\n"
                [(str "Content-Type: multipart/alternative; boundary=\"" boundary "\"")
                 ""
                 (str "--" boundary)
                 "Content-Type: text/plain; charset=UTF-8"
                 "Content-Transfer-Encoding: base64"
                 ""
                 (encoded-body body-text)
                 (str "--" boundary)
                 "Content-Type: text/html; charset=UTF-8"
                 "Content-Transfer-Encoding: base64"
                 ""
                 (encoded-body body-html)
                 (str "--" boundary "--")])
               (string/join
                "\r\n"
                [(str "Content-Type: " (if body-html "text/html" "text/plain")
                      "; charset=UTF-8")
                 "Content-Transfer-Encoding: base64"
                 ""
                 (encoded-body (or body-html body-text))]))]
    (str (string/join "\r\n" headers) "\r\n" body)))

(defn- read-response
  [^BufferedReader reader]
  (loop [lines []]
    (let [line (.readLine reader)]
      (when-not line
        (throw (ex-info "SMTP server closed the connection" {:smtp/response lines})))
      (let [lines (conj lines line)]
        (if (and (>= (count line) 4) (= \space (.charAt line 3)))
          {:code (parse-long (subs line 0 3)) :lines lines}
          (recur lines))))))

(defn- expect!
  [reader accepted]
  (let [{:keys [code] :as response} (read-response reader)]
    (when-not (contains? accepted code)
      (throw (ex-info "SMTP command failed" {:smtp/response response})))
    response))

(defn- command!
  [^BufferedWriter writer command]
  (.write writer (str command "\r\n"))
  (.flush writer))

(defrecord SmtpEmail [host port connect-timeout-ms read-timeout-ms]
  email/Email
  (send [_ args]
    (let [{:keys [from to cc bcc] :as message} (email/normalize-message args)
          recipients (concat to cc bcc)]
      (with-open [socket (doto (Socket.)
                           (.connect (java.net.InetSocketAddress. host port)
                                     connect-timeout-ms)
                           (.setSoTimeout read-timeout-ms))
                  reader (BufferedReader. (InputStreamReader. (.getInputStream socket)
                                                               StandardCharsets/US_ASCII))
                  writer (BufferedWriter. (OutputStreamWriter. (.getOutputStream socket)
                                                                StandardCharsets/US_ASCII))]
        (expect! reader #{220})
        (command! writer "EHLO grain.local")
        (expect! reader #{250})
        (command! writer (str "MAIL FROM:<" (clean-header from) ">"))
        (expect! reader #{250})
        (doseq [recipient recipients]
          (command! writer (str "RCPT TO:<" (clean-header recipient) ">"))
          (expect! reader #{250 251}))
        (command! writer "DATA")
        (expect! reader #{354})
        (command! writer (str (message-data message) "\r\n."))
        (let [response (expect! reader #{250})]
          (command! writer "QUIT")
          {:email/status :sent
           :email/provider :smtp
           :email/response response})))))

(defn smtp-email
  [{:keys [host port connect-timeout-ms read-timeout-ms]
    :or {host "localhost"
         port 1025
         connect-timeout-ms 2000
         read-timeout-ms 5000}}]
  (->SmtpEmail host port connect-timeout-ms read-timeout-ms))
