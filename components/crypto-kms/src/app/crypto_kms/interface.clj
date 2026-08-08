(ns app.crypto-kms.interface
  (:require [app.crypto.interface :as crypto]
            [cognitect.anomalies :as anomalies]
            [cognitect.aws.client.api :as aws]
            [clojure.java.io :as io])
  (:import [java.io ByteArrayOutputStream InputStream]
           [java.net URI]
           [java.nio ByteBuffer]
           [java.security SecureRandom]
           [java.util Arrays]))

(defn- endpoint-override
  [endpoint]
  (when endpoint
    (let [uri (URI. endpoint)]
      {:protocol (keyword (.getScheme uri))
       :hostname (.getHost uri)
       :port (if (neg? (.getPort uri))
               (if (= "https" (.getScheme uri)) 443 80)
               (.getPort uri))})))

(defn- blob-bytes
  [blob]
  (cond
    (= (class (byte-array 0)) (class blob)) (aclone ^bytes blob)
    (instance? ByteBuffer blob) (let [copy (.duplicate ^ByteBuffer blob)
                                      bytes (byte-array (.remaining copy))]
                                  (.get copy bytes)
                                  bytes)
    (instance? InputStream blob) (with-open [input blob
                                             output (ByteArrayOutputStream.)]
                                   (io/copy input output)
                                   (.toByteArray output))
    :else (throw (ex-info "Unsupported AWS blob response" {:crypto/blob-type (type blob)}))))

(defn- result!
  [operation response]
  (when (::anomalies/category response)
    (throw (ex-info (str "KMS " (name operation) " failed")
                    {:crypto/provider :kms
                     :crypto/operation operation
                     :crypto/anomaly (::anomalies/category response)})))
  response)

(defrecord KmsCrypto [client key-id random]
  crypto/Crypto
  (encrypt [_ plaintext]
    (when-not (string? plaintext)
      (throw (ex-info "Crypto plaintext must be a string" {})))
    (let [response (result! :generate-data-key
                            (aws/invoke client {:op :GenerateDataKey
                                                :request {:KeyId key-id
                                                          :KeySpec "AES_256"}}))
          data-key (blob-bytes (:Plaintext response))
          wrapped-key (blob-bytes (:CiphertextBlob response))]
      (try
        (let [{:keys [ciphertext iv]} (crypto/encrypt-aes-gcm data-key plaintext random)]
          {:crypto/version 1
           :crypto/algorithm :aes-256-gcm
           :crypto/provider :kms
           :crypto/key-id key-id
           :crypto/ciphertext (crypto/encode-bytes ciphertext)
           :crypto/iv (crypto/encode-bytes iv)
           :crypto/wrapped-key (crypto/encode-bytes wrapped-key)})
        (finally
          (Arrays/fill ^bytes data-key (byte 0))))))
  (decrypt [_ envelope]
    (when-not (and (= 1 (:crypto/version envelope))
                   (= :aes-256-gcm (:crypto/algorithm envelope))
                   (= :kms (:crypto/provider envelope)))
      (throw (ex-info "Unsupported KMS crypto envelope"
                      {:crypto/version (:crypto/version envelope)
                       :crypto/provider (:crypto/provider envelope)})))
    (let [response (result! :decrypt
                            (aws/invoke client {:op :Decrypt
                                                :request {:CiphertextBlob
                                                          (crypto/decode-bytes
                                                           (:crypto/wrapped-key envelope))}}))
          data-key (blob-bytes (:Plaintext response))]
      (try
        (crypto/decrypt-aes-gcm data-key
                                (crypto/decode-bytes (:crypto/ciphertext envelope))
                                (crypto/decode-bytes (:crypto/iv envelope)))
        (finally
          (Arrays/fill ^bytes data-key (byte 0)))))))

(defn kms-crypto
  [{:keys [region endpoint key-id]}]
  (when-not (and (seq region) (seq key-id))
    (throw (ex-info "KMS crypto requires :region and :key-id" {})))
  (->KmsCrypto
   (aws/client (cond-> {:api :kms :region region}
                 endpoint (assoc :endpoint-override (endpoint-override endpoint))))
   key-id
   (SecureRandom.)))
