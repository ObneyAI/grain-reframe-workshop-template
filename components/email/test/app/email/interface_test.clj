(ns app.email.interface-test
  (:require [app.email.interface :as email]
            [clojure.edn :as edn]
            [clojure.test :refer [deftest is testing]])
  (:import (java.nio.file Files)
           (java.nio.file.attribute FileAttribute)))

(deftest logger-adapter-captures-normalized-messages
  (let [sent (atom [])
        adapter (email/logger-email sent)
        result (email/send adapter {:from "from@example.test"
                                    :to "to@example.test"
                                    :subject "Verify"
                                    :body-html "<p>secret link</p>"})]
    (is (= :captured (:email/status result)))
    (is (= ["to@example.test"] (:to (first @sent))))
    (is (= [] (:cc (first @sent))))))

(deftest invalid-messages-fail-at-the-email-interface
  (testing "missing recipients"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"at least one"
                          (email/send (email/logger-email)
                                      {:from "from@example.test"
                                       :subject "Hello"
                                       :body-text "Hi"})))))

(deftest file-capture-adapter-exposes-messages-to-browser-tests
  (let [directory (Files/createTempDirectory "grain-email-capture-"
                                              (make-array FileAttribute 0))
        capture-path (.resolve directory "messages.edn")
        adapter (email/file-capture-email (str capture-path))]
    (try
      (email/send adapter {:from "from@example.test"
                           :to "to@example.test"
                           :subject "Verify"
                           :body-html "<p>verification-token=test-token</p>"})
      (let [message (edn/read-string (Files/readString capture-path))]
        (is (= ["to@example.test"] (:to message)))
        (is (= "<p>verification-token=test-token</p>" (:body-html message))))
      (finally
        (Files/deleteIfExists capture-path)
        (Files/deleteIfExists directory)))))
