(ns app.email.interface-test
  (:require [app.email.interface :as email]
            [clojure.test :refer [deftest is testing]]))

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
