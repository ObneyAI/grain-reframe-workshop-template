(ns app.crypto.interface-test
  (:require [app.crypto.interface :as crypto]
            [clojure.test :refer [deftest is]]))

(def test-key "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")

(deftest local-adapter-authenticates-and-round-trips
  (let [adapter (crypto/local-crypto test-key)
        envelope (crypto/encrypt adapter "private value")]
    (is (= :local (:crypto/provider envelope)))
    (is (= "private value" (crypto/decrypt adapter envelope)))
    (is (thrown-with-msg?
         clojure.lang.ExceptionInfo
         #"failed authentication"
         (crypto/decrypt adapter (update envelope :crypto/ciphertext
                                         #(str (subs % 0 (dec (count %)))
                                               (if (= "A" (subs % (dec (count %))))
                                                 "B"
                                                 "A"))))))))
