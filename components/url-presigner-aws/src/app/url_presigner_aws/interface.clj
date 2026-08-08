(ns app.url-presigner-aws.interface
  (:require [app.file-store.interface :as files]
            [app.url-presigner.interface :as presigner])
  (:import [java.net URI]
           [java.time Duration]
           [software.amazon.awssdk.auth.credentials AwsBasicCredentials DefaultCredentialsProvider
            StaticCredentialsProvider]
           [software.amazon.awssdk.regions Region]
           [software.amazon.awssdk.services.s3 S3Configuration]
           [software.amazon.awssdk.services.s3.model GetObjectRequest PutObjectRequest]
           [software.amazon.awssdk.services.s3.presigner S3Presigner]
           [software.amazon.awssdk.services.s3.presigner.model GetObjectPresignRequest
            PutObjectPresignRequest]))

(defn- make-presigner
  [{:keys [region endpoint]}]
  (let [builder (S3Presigner/builder)]
    (.region builder (Region/of region))
    (.credentialsProvider
     builder
     (if endpoint
       (StaticCredentialsProvider/create (AwsBasicCredentials/create "test" "test"))
       (DefaultCredentialsProvider/create)))
    (when endpoint
      (.endpointOverride builder (URI. endpoint))
      (.serviceConfiguration
       builder
       (-> (S3Configuration/builder)
           (.pathStyleAccessEnabled true)
           (.build))))
    (.build builder)))

(defrecord AwsURLPresigner [configuration]
  presigner/URLPresigner
  (presign-upload [_ {:keys [key content-type expires-in-seconds]
                      :or {expires-in-seconds 900}}]
    (let [{:keys [bucket]} configuration
          key (files/valid-key key)
          object-request (-> (PutObjectRequest/builder)
                             (.bucket bucket)
                             (.key key)
                             (.contentType content-type)
                             (.build))
          sign-request (-> (PutObjectPresignRequest/builder)
                           (.signatureDuration (Duration/ofSeconds expires-in-seconds))
                           (.putObjectRequest object-request)
                           (.build))]
      (with-open [client (make-presigner configuration)]
        {:url (str (.url (.presignPutObject ^S3Presigner client sign-request)))
         :method :put
         :headers (cond-> {} content-type (assoc "Content-Type" content-type))
         :expires-in-seconds expires-in-seconds
         :key key})))
  (presign-download [_ {:keys [key expires-in-seconds]
                        :or {expires-in-seconds 900}}]
    (let [{:keys [bucket]} configuration
          key (files/valid-key key)
          object-request (-> (GetObjectRequest/builder)
                             (.bucket bucket)
                             (.key key)
                             (.build))
          sign-request (-> (GetObjectPresignRequest/builder)
                           (.signatureDuration (Duration/ofSeconds expires-in-seconds))
                           (.getObjectRequest object-request)
                           (.build))]
      (with-open [client (make-presigner configuration)]
        {:url (str (.url (.presignGetObject ^S3Presigner client sign-request)))
         :method :get
         :headers {}
         :expires-in-seconds expires-in-seconds
         :key key}))))

(defn aws-url-presigner
  [{:keys [region bucket] :as configuration}]
  (when-not (and (seq region) (seq bucket))
    (throw (ex-info "AWS URL presigner requires :region and :bucket" {})))
  (->AwsURLPresigner configuration))
