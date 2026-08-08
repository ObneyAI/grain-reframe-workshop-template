(ns app.file-store.interface
  (:require [clojure.string :as string]))

(defprotocol FileStore
  (put! [this object]
    "Store {:key string :bytes byte[] :content-type string? :metadata map?}.")
  (get-object [this key]
    "Return the stored object map, including :bytes, or nil when absent.")
  (delete! [this key]
    "Delete an object. Deleting an absent key is idempotent.")
  (location [this key]
    "Return a provider-neutral description of where the object lives."))

(defn valid-key
  [key]
  (when-not (and (string? key)
                 (not (string/blank? key))
                 (not (string/starts-with? key "/"))
                 (not-any? #{".."} (string/split key #"/"))
                 (not (re-find #"[\p{Cntrl}]" key)))
    (throw (ex-info "File object key must be a safe relative key"
                    {:file-store/key key})))
  key)

(defn- copy-bytes
  [bytes]
  (when-not (= (class (byte-array 0)) (class bytes))
    (throw (ex-info "File :bytes must be a byte array" {})))
  (aclone ^bytes bytes))

(defrecord MemoryFileStore [objects]
  FileStore
  (put! [_ {:keys [key bytes content-type metadata]}]
    (let [key (valid-key key)
          object {:key key
                  :bytes (copy-bytes bytes)
                  :content-type content-type
                  :metadata (or metadata {})
                  :size (alength ^bytes bytes)}]
      (swap! objects assoc key object)
      (dissoc object :bytes)))
  (get-object [_ key]
    (some-> (clojure.core/get @objects (valid-key key))
            (update :bytes copy-bytes)))
  (delete! [_ key]
    (swap! objects dissoc (valid-key key))
    {:file-store/status :deleted :key key})
  (location [_ key]
    {:provider :memory :key (valid-key key)}))

(defn memory-file-store
  ([] (memory-file-store (atom {})))
  ([objects]
   (->MemoryFileStore objects)))
