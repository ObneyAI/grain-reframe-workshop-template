(ns app.email-ses.interface
  (:require [app.email.interface :as email]
            [cognitect.anomalies :as anomalies]
            [cognitect.aws.client.api :as aws]))

(defn- attachment
  [{:keys [filename content-type bytes]}]
  (cond-> {:RawContent bytes
           :FileName filename
           :ContentDisposition "ATTACHMENT"
           :ContentTransferEncoding "BASE64"}
    content-type (assoc :ContentType content-type)))

(defn send-request
  "Translate the provider-neutral email message into one SES v2 request."
  [args]
  (let [{:keys [from to cc bcc reply-to subject body-text body-html attachments]}
        (email/normalize-message args)]
    (cond-> {:FromEmailAddress from
             :Destination (cond-> {:ToAddresses to}
                            (seq cc) (assoc :CcAddresses cc)
                            (seq bcc) (assoc :BccAddresses bcc))
             :Content {:Simple
                       (cond-> {:Subject {:Data subject :Charset "UTF-8"}
                                :Body (cond-> {}
                                        body-text (assoc :Text {:Data body-text :Charset "UTF-8"})
                                        body-html (assoc :Html {:Data body-html :Charset "UTF-8"}))}
                         (seq attachments) (assoc :Attachments (mapv attachment attachments)))}}
      (seq reply-to) (assoc :ReplyToAddresses reply-to))))

(defrecord SesEmail [client]
  email/Email
  (send [_ message]
    (let [response (aws/invoke client {:op :SendEmail
                                       :request (send-request message)})]
      (when (::anomalies/category response)
        (throw (ex-info "SES rejected the email"
                        {:email/provider :ses
                         :email/anomaly (::anomalies/category response)})))
      {:email/status :sent
       :email/provider :ses
       :email/message-id (:MessageId response)})))

(defn ses-email
  [{:keys [region]}]
  (->SesEmail (aws/client (cond-> {:api :sesv2}
                            region (assoc :region region)))))
