(ns app.url-presigner.interface
  (:require [app.file-store.interface :as files]
            [clojure.string :as string])
  (:import [java.net URLEncoder]
           [java.nio.charset StandardCharsets]))

(defprotocol URLPresigner
  (presign-upload [this request])
  (presign-download [this request]))

(defn- encode
  [value]
  (URLEncoder/encode (str value) StandardCharsets/UTF_8))

(defrecord StubURLPresigner [base-url calls]
  URLPresigner
  (presign-upload [_ {:keys [key content-type expires-in-seconds]
                      :or {expires-in-seconds 900}}]
    (let [request {:operation :upload
                   :key (files/valid-key key)
                   :content-type content-type
                   :expires-in-seconds expires-in-seconds}]
      (swap! calls conj request)
      {:url (str (string/replace base-url #"/$" "") "/" (encode key)
                 "?operation=upload")
       :method :put
       :headers (cond-> {} content-type (assoc "Content-Type" content-type))
       :expires-in-seconds expires-in-seconds
       :key key}))
  (presign-download [_ {:keys [key expires-in-seconds]
                        :or {expires-in-seconds 900}}]
    (let [request {:operation :download
                   :key (files/valid-key key)
                   :expires-in-seconds expires-in-seconds}]
      (swap! calls conj request)
      {:url (str (string/replace base-url #"/$" "") "/" (encode key)
                 "?operation=download")
       :method :get
       :headers {}
       :expires-in-seconds expires-in-seconds
       :key key})))

(defn stub-presigner
  ([] (stub-presigner "memory://files" (atom [])))
  ([base-url calls]
   (->StubURLPresigner base-url calls)))
