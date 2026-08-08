(ns app.file-store.interface-test
  (:require [app.file-store.interface :as files]
            [clojure.test :refer [deftest is]]))

(deftest memory-adapter-round-trips-defensive-byte-copies
  (let [store (files/memory-file-store)
        original (.getBytes "hello")]
    (files/put! store {:key "tenant/documents/hello.txt"
                       :bytes original
                       :content-type "text/plain"})
    (aset-byte original 0 (byte 88))
    (let [first-read (files/get-object store "tenant/documents/hello.txt")]
      (is (= "hello" (String. ^bytes (:bytes first-read))))
      (aset-byte ^bytes (:bytes first-read) 0 (byte 89)))
    (is (= "hello" (String. ^bytes (:bytes (files/get-object store "tenant/documents/hello.txt")))))
    (files/delete! store "tenant/documents/hello.txt")
    (is (nil? (files/get-object store "tenant/documents/hello.txt")))))

(deftest object-keys-cannot-escape-the-owned-prefix
  (is (thrown? clojure.lang.ExceptionInfo
               (files/put! (files/memory-file-store)
                           {:key "../secret" :bytes (byte-array 0)}))))
