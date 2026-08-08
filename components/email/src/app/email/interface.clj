(ns app.email.interface
  (:refer-clojure :exclude [send])
  (:require [clojure.string :as string]
            [com.brunobonacci.mulog :as u]))

(defprotocol Email
  (send [this args]))

(defn normalize-message
  "Validate and normalize the provider-neutral email message accepted at this
   seam. Adapters receive vectors for every recipient field and never need to
   understand application-specific templates or authentication workflows."
  [{:keys [from subject body-text body-html] :as message}]
  (let [addresses (fn [value]
                    (cond
                      (nil? value) []
                      (string? value) [value]
                      (sequential? value) (vec value)
                      :else (throw (ex-info "Email recipients must be a string or collection"
                                            {:email/field value}))))
        normalized (-> message
                       (update :to addresses)
                       (update :cc addresses)
                       (update :bcc addresses)
                       (update :reply-to addresses))]
    (when-not (and (string? from) (not (string/blank? from)))
      (throw (ex-info "Email :from is required" {:email/field :from})))
    (when-not (seq (:to normalized))
      (throw (ex-info "Email requires at least one :to recipient" {:email/field :to})))
    (when-not (and (string? subject) (not (string/blank? subject)))
      (throw (ex-info "Email :subject is required" {:email/field :subject})))
    (when-not (or (string? body-text) (string? body-html))
      (throw (ex-info "Email requires :body-text or :body-html"
                      {:email/field :body})))
    normalized))

(defrecord LoggerEmail [sent]
  Email
  (send [_ args]
    (let [message (normalize-message args)]
      (u/log ::email-captured
             :email/to (:to message)
             :email/subject (:subject message)
             :email/has-text? (boolean (:body-text message))
             :email/has-html? (boolean (:body-html message)))
      (when sent
        (swap! sent conj message))
      {:email/status :captured
       :email/message message})))

(defn logger-email
  ([] (logger-email nil))
  ([sent]
   (->LoggerEmail sent)))
