(ns app.url-presigner.interface-test
  (:require [app.url-presigner.interface :as presigner]
            [clojure.test :refer [deftest is]]))

(deftest stub-adapter-is-deterministic-and-observable
  (let [calls (atom [])
        adapter (presigner/stub-presigner "memory://objects" calls)
        result (presigner/presign-upload adapter {:key "tenant/photo one.jpg"
                                                  :content-type "image/jpeg"})]
    (is (= :put (:method result)))
    (is (= {"Content-Type" "image/jpeg"} (:headers result)))
    (is (= :upload (:operation (first @calls))))))
