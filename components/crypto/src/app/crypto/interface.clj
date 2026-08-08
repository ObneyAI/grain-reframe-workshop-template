(ns app.crypto.interface
  (:import [java.nio.charset StandardCharsets]
           [java.security SecureRandom]
           [java.util Base64]
           [javax.crypto AEADBadTagException Cipher]
           [javax.crypto.spec GCMParameterSpec SecretKeySpec]))

(defprotocol Crypto
  (encrypt [this plaintext]
    "Encrypt a UTF-8 string into a versioned provider-neutral envelope.")
  (decrypt [this envelope]
    "Decrypt an envelope previously returned by encrypt."))

(defn encrypt-aes-gcm
  [^bytes key plaintext ^SecureRandom random]
  (let [iv (byte-array 12)
        cipher (Cipher/getInstance "AES/GCM/NoPadding")]
    (.nextBytes random iv)
    (.init cipher Cipher/ENCRYPT_MODE
           (SecretKeySpec. key "AES")
           (GCMParameterSpec. 128 iv))
    {:ciphertext (.doFinal cipher (.getBytes ^String plaintext StandardCharsets/UTF_8))
     :iv iv}))

(defn decrypt-aes-gcm
  [^bytes key ^bytes ciphertext ^bytes iv]
  (let [cipher (Cipher/getInstance "AES/GCM/NoPadding")]
    (.init cipher Cipher/DECRYPT_MODE
           (SecretKeySpec. key "AES")
           (GCMParameterSpec. 128 iv))
    (String. (.doFinal cipher ciphertext) StandardCharsets/UTF_8)))

(defn encode-bytes
  [bytes]
  (.encodeToString (Base64/getEncoder) ^bytes bytes))

(defn decode-bytes
  [value]
  (.decode (Base64/getDecoder) ^String value))

(defrecord LocalCrypto [key random]
  Crypto
  (encrypt [_ plaintext]
    (when-not (string? plaintext)
      (throw (ex-info "Crypto plaintext must be a string" {})))
    (let [{:keys [ciphertext iv]} (encrypt-aes-gcm key plaintext random)]
      {:crypto/version 1
       :crypto/algorithm :aes-256-gcm
       :crypto/provider :local
       :crypto/ciphertext (encode-bytes ciphertext)
       :crypto/iv (encode-bytes iv)}))
  (decrypt [_ envelope]
    (when-not (and (= 1 (:crypto/version envelope))
                   (= :aes-256-gcm (:crypto/algorithm envelope))
                   (= :local (:crypto/provider envelope)))
      (throw (ex-info "Unsupported local crypto envelope"
                      {:crypto/version (:crypto/version envelope)
                       :crypto/provider (:crypto/provider envelope)})))
    (try
      (decrypt-aes-gcm key
                       (decode-bytes (:crypto/ciphertext envelope))
                       (decode-bytes (:crypto/iv envelope)))
      (catch AEADBadTagException cause
        (throw (ex-info "Encrypted value failed authentication"
                        {:crypto/category :invalid-ciphertext}
                        cause))))))

(defn local-crypto
  ([base64-key]
   (local-crypto base64-key (SecureRandom.)))
  ([base64-key random]
   (let [key (try
               (decode-bytes base64-key)
               (catch Exception cause
                 (throw (ex-info "APP_CRYPTO_KEY must be valid Base64" {} cause))))]
     (when-not (= 32 (alength ^bytes key))
       (throw (ex-info "APP_CRYPTO_KEY must decode to exactly 32 bytes" {})))
     (->LocalCrypto key random))))
