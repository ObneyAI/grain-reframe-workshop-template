(ns app.email-ses.interface-test
  (:require [app.email-ses.interface :as ses]
            [clojure.test :refer [deftest is]]))

(deftest translates-email-without-leaking-bcc-into-content
  (let [request (ses/send-request {:from "from@example.test"
                                   :to "to@example.test"
                                   :bcc "hidden@example.test"
                                   :reply-to "reply@example.test"
                                   :subject "Hello"
                                   :body-text "Plain"
                                   :body-html "<p>HTML</p>"})]
    (is (= ["to@example.test"] (get-in request [:Destination :ToAddresses])))
    (is (= ["hidden@example.test"] (get-in request [:Destination :BccAddresses])))
    (is (= ["reply@example.test"] (:ReplyToAddresses request)))
    (is (= "<p>HTML</p>" (get-in request [:Content :Simple :Body :Html :Data])))))
