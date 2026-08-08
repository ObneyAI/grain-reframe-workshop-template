(ns app.file-store-s3.interface
  (:require [app.file-store.interface :as files]
            [cognitect.anomalies :as anomalies]
            [cognitect.aws.client.api :as aws]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream]
           [java.net URI]))

(defn- endpoint-override
  [endpoint]
  (when endpoint
    (let [uri (URI. endpoint)]
      {:protocol (keyword (.getScheme uri))
       :hostname (.getHost uri)
       :port (if (neg? (.getPort uri))
               (if (= "https" (.getScheme uri)) 443 80)
               (.getPort uri))})))

(defn- result!
  [operation response]
  (when (::anomalies/category response)
    (throw (ex-info (str "S3 " (name operation) " failed")
                    {:file-store/provider :s3
                     :file-store/operation operation
                     :file-store/anomaly (::anomalies/category response)})))
  response)

(defn- body-bytes
  [body]
  (with-open [input body
              output (ByteArrayOutputStream.)]
    (io/copy input output)
    (.toByteArray output)))

(defrecord S3FileStore [client bucket]
  files/FileStore
  (put! [_ {:keys [key bytes content-type metadata]}]
    (let [key (files/valid-key key)
          response (result!
                    :put
                    (aws/invoke client
                                {:op :PutObject
                                 :request (cond-> {:Bucket bucket
                                                   :Key key
                                                   :Body bytes}
                                            content-type (assoc :ContentType content-type)
                                            (seq metadata) (assoc :Metadata metadata))}))]
      {:provider :s3
       :bucket bucket
       :key key
       :size (alength ^bytes bytes)
       :etag (:ETag response)}))
  (get-object [_ key]
    (let [key (files/valid-key key)
          response (aws/invoke client {:op :GetObject
                                       :request {:Bucket bucket :Key key}})]
      (if (= :cognitect.anomalies/not-found (::anomalies/category response))
        nil
        (let [response (result! :get response)]
          {:provider :s3
           :bucket bucket
           :key key
           :bytes (body-bytes (:Body response))
           :content-type (:ContentType response)
           :metadata (or (:Metadata response) {})
           :size (:ContentLength response)
           :etag (:ETag response)}))))
  (delete! [_ key]
    (let [key (files/valid-key key)]
      (result! :delete
               (aws/invoke client {:op :DeleteObject
                                   :request {:Bucket bucket :Key key}}))
      {:file-store/status :deleted :provider :s3 :bucket bucket :key key}))
  (location [_ key]
    {:provider :s3 :bucket bucket :key (files/valid-key key)}))

(defn s3-file-store
  [{:keys [region endpoint bucket]}]
  (when-not (seq bucket)
    (throw (ex-info "S3 file store requires :bucket" {})))
  (->S3FileStore
   (aws/client (cond-> {:api :s3}
                 region (assoc :region region)
                 endpoint (assoc :endpoint-override (endpoint-override endpoint))))
   bucket))
