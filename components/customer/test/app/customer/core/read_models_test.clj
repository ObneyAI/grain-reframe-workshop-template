(ns app.customer.core.read-models-test
  (:require [app.customer.core.read-models :as read-models]
            [clojure.test :refer [deftest is]]))

(deftest customer-created-projects-a-browsable-customer
  (let [customer-id (random-uuid)
        state (read-models/customers*
               {}
               {:event/type :customer/customer-created
                :customer-id customer-id
                :name "Northstar Studio"
                :email-address "hello@northstar.example"})]
    (is (= {:customer-id customer-id
            :name "Northstar Studio"
            :email-address "hello@northstar.example"
            :status :lead
            :activity [{:type :customer-created
                        :status :lead}]}
           (get state customer-id)))))

(deftest status-changes-update-the-customer-and-append-activity
  (let [customer-id (random-uuid)
        created (read-models/customers*
                 {}
                 {:event/type :customer/customer-created
                  :customer-id customer-id
                  :name "Northstar Studio"
                  :email-address "hello@northstar.example"})
        active (read-models/customers*
                created
                {:event/type :customer/status-changed
                 :customer-id customer-id
                 :status :active})]
    (is (= :active (get-in active [customer-id :status])))
    (is (= [{:type :customer-created :status :lead}
            {:type :status-changed :status :active}]
           (get-in active [customer-id :activity])))))
