(ns app.webhooks.interface
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io ByteArrayInputStream ByteArrayOutputStream InputStream]
           [java.nio.charset StandardCharsets]
           [java.security MessageDigest]
           [java.time Clock Instant]
           [java.util HexFormat UUID]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(defprotocol WebhookProcessor
  (receive! [this request]
    "Verify and process one {:headers map :body byte[]} request exactly once.")
  (replay! [this receipt-id]
    "Retry a failed receipt from its captured raw body.")
  (receipt [this receipt-id]
    "Return a safe receipt without captured request headers or raw body."))

(defn body-bytes
  [body]
  (cond
    (= (class (byte-array 0)) (class body)) (aclone ^bytes body)
    (string? body) (.getBytes ^String body StandardCharsets/UTF_8)
    (instance? InputStream body) (with-open [input body
                                             output (ByteArrayOutputStream.)]
                                   (io/copy input output)
                                   (.toByteArray output))
    :else (throw (ex-info "Webhook body must be bytes, a string, or an input stream"
                          {:webhook/body-type (type body)}))))

(defn capture-raw-body
  "Preserve the exact bytes used for signature verification and replace the
   request stream so a later JSON/Transit parser can still consume it."
  [request]
  (let [raw (body-bytes (:body request))]
    (assoc request
           :webhook/raw-body raw
           :body (ByteArrayInputStream. raw))))

(defn- header-value
  [headers header-name]
  (let [wanted (string/lower-case header-name)]
    (some (fn [[key value]]
            (when (= wanted (string/lower-case (name key))) value))
          headers)))

(defn- hmac
  [secret bytes]
  (let [mac (doto (Mac/getInstance "HmacSHA256")
              (.init (SecretKeySpec. (.getBytes ^String secret StandardCharsets/UTF_8)
                                     "HmacSHA256")))]
    (.doFinal mac ^bytes bytes)))

(defn- hex-bytes
  [value]
  (try
    (.parseHex (HexFormat/of) ^String value)
    (catch Exception _ nil)))

(defn hmac-sha256-verifier
  "Create a verifier for the common `sha256=<hex>` webhook scheme. When a
   timestamp header is configured, the signed value is `<timestamp>.<body>` and
   timestamps outside the tolerance are rejected. Vendor-specific schemes can
   supply another verifier to processor."
  [{:keys [secret signature-header signature-prefix timestamp-header
           tolerance-seconds clock]
    :or {signature-header "x-webhook-signature"
         signature-prefix "sha256="
         tolerance-seconds 300
         clock (Clock/systemUTC)}}]
  (when-not (seq secret)
    (throw (ex-info "Webhook verifier requires :secret" {})))
  (fn [{:keys [headers] :as request}]
    (let [raw (or (:webhook/raw-body request) (body-bytes (:body request)))
          supplied (some-> (header-value headers signature-header) str)
          signature (when (and supplied (string/starts-with? supplied signature-prefix))
                      (subs supplied (count signature-prefix)))
          timestamp (when timestamp-header
                      (some-> (header-value headers timestamp-header) str parse-long))
          now (.getEpochSecond (Instant/now clock))
          timely? (or (nil? timestamp-header)
                      (and timestamp (<= (abs (- now timestamp)) tolerance-seconds)))
          signed-bytes (if timestamp-header
                         (.getBytes (str timestamp "."
                                         (String. ^bytes raw StandardCharsets/UTF_8))
                                    StandardCharsets/UTF_8)
                         raw)
          expected (hmac secret signed-bytes)
          actual (some-> signature hex-bytes)]
      (boolean (and timely? actual (MessageDigest/isEqual expected actual))))))

(defn- sha256
  [bytes]
  (.formatHex (HexFormat/of)
              (.digest (MessageDigest/getInstance "SHA-256") ^bytes bytes)))

(defn- public-receipt
  [value]
  (dissoc value :webhook/raw-body :webhook/headers))

(defn- run-handler!
  [receipts receipt-id handler]
  (let [current (clojure.core/get @receipts receipt-id)]
    (try
      (let [result (handler {:provider (:webhook/provider current)
                             :event-id (:webhook/event-id current)
                             :headers (:webhook/headers current)
                             :body (:webhook/raw-body current)})]
        (swap! receipts update receipt-id merge
               {:webhook/status :processed
                :webhook/result result
                :webhook/completed-at (Instant/now)})
        {:webhook/status :processed
         :webhook/receipt (public-receipt (clojure.core/get @receipts receipt-id))})
      (catch Exception cause
        (swap! receipts update receipt-id merge
               {:webhook/status :failed
                :webhook/error (.getMessage cause)
                :webhook/completed-at (Instant/now)})
        {:webhook/status :failed
         :webhook/receipt (public-receipt (clojure.core/get @receipts receipt-id))}))))

(defrecord InMemoryWebhookProcessor
    [provider verify-signature event-id handler clock receipts event-index]
  WebhookProcessor
  (receive! [_ request]
    (let [raw (or (:webhook/raw-body request) (body-bytes (:body request)))
          request (assoc request :webhook/raw-body raw)]
      (if-not (verify-signature request)
        {:webhook/status :rejected :webhook/reason :invalid-signature}
        (let [event-id (event-id request)]
          (when-not (and (string? event-id) (not (string/blank? event-id)))
            (throw (ex-info "Webhook event-id function must return a non-blank string" {})))
          (locking receipts
            (if-let [receipt-id (clojure.core/get @event-index [provider event-id])]
              {:webhook/status :duplicate
               :webhook/receipt (public-receipt (clojure.core/get @receipts receipt-id))}
              (let [receipt-id (UUID/randomUUID)
                    value {:webhook/receipt-id receipt-id
                           :webhook/provider provider
                           :webhook/event-id event-id
                           :webhook/status :processing
                           :webhook/attempts 1
                           :webhook/received-at (Instant/now clock)
                           :webhook/payload-sha256 (sha256 raw)
                           :webhook/headers (:headers request)
                           :webhook/raw-body raw}]
                (swap! receipts assoc receipt-id value)
                (swap! event-index assoc [provider event-id] receipt-id)
                (run-handler! receipts receipt-id handler))))))))
  (replay! [_ receipt-id]
    (locking receipts
      (let [current (clojure.core/get @receipts receipt-id)]
        (cond
          (nil? current)
          {:webhook/status :not-found}

          (not= :failed (:webhook/status current))
          {:webhook/status :not-replayable
           :webhook/receipt (public-receipt current)}

          :else
          (do
            (swap! receipts update receipt-id
                   #(-> %
                        (assoc :webhook/status :processing)
                        (update :webhook/attempts inc)
                        (dissoc :webhook/error :webhook/completed-at)))
            (run-handler! receipts receipt-id handler))))))
  (receipt [_ receipt-id]
    (some-> (clojure.core/get @receipts receipt-id) public-receipt)))

(defn processor
  [{:keys [provider verify-signature event-id handler clock receipts event-index]
    :or {clock (Clock/systemUTC)
         receipts (atom {})
         event-index (atom {})}}]
  (when-not (and provider (fn? verify-signature) (fn? event-id) (fn? handler))
    (throw (ex-info "Webhook processor requires :provider, :verify-signature, :event-id, and :handler"
                    {})))
  (->InMemoryWebhookProcessor provider verify-signature event-id handler clock receipts event-index))
