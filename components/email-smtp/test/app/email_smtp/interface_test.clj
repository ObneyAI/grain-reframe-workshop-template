(ns app.email-smtp.interface-test
  (:require [app.email-smtp.interface :as smtp]
            [clojure.string :as string]
            [clojure.test :refer [deftest is]]))

(deftest builds-a-private-multipart-message
  (let [data (smtp/message-data {:from "from@example.test"
                                 :to ["to@example.test"]
                                 :bcc ["hidden@example.test"]
                                 :subject "Hello"
                                 :body-text "Plain"
                                 :body-html "<p>HTML</p>"})]
    (is (string/includes? data "multipart/alternative"))
    (is (string/includes? data "To: to@example.test"))
    (is (not (string/includes? data "hidden@example.test")))
    (is (not (string/includes? data "<p>HTML</p>")))))

(deftest rejects-header-injection
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"headers"
                        (smtp/message-data {:from "from@example.test"
                                            :to ["to@example.test\r\nBcc: bad@example.test"]
                                            :subject "Hello"
                                            :body-text "Plain"}))))
