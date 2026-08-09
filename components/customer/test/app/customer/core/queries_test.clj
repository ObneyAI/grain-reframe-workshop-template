(ns app.customer.core.queries-test
  (:require [app.customer.core.queries :as queries]
            [clojure.test :refer [deftest is testing]]))

(def customers
  {#uuid "00000000-0000-0000-0000-000000000001"
   {:customer-id #uuid "00000000-0000-0000-0000-000000000001"
    :name "Zenith" :email-address "z@example.test" :status :lead}
   #uuid "00000000-0000-0000-0000-000000000002"
   {:customer-id #uuid "00000000-0000-0000-0000-000000000002"
    :name "Acme" :email-address "a@example.test" :status :active}})

(deftest customer-index-filters-and-sorts
  (testing "defaults to ascending name order"
    (is (= ["Acme" "Zenith"]
           (mapv :name (queries/project-index customers {})))))
  (testing "filters by status and can reverse the order"
    (is (= ["Zenith"]
           (mapv :name (queries/project-index customers
                                              {:status :lead
                                               :sort :name-desc}))))))
