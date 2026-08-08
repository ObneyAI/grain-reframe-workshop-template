(ns app.questionnaire.events-test
  (:require [app.questionnaire.events :as events]
            [app.store :as store]
            [cljs.test :refer-macros [deftest is testing]]
            [re-frame.core :as rf]
            [re-frame.db :as rf-db]))

(deftest completed-values-enter-app-db-as-data
  (testing "the React bridge hands serializable answers to Re-frame"
    (let [answers {:direction "domain-slice"
                   :states ["loading" "success"]
                   :grain-path "both"}]
      (rf/dispatch-sync [::store/initialize])
      (rf/dispatch-sync [::events/submitted answers])
      (is (= answers (get-in @rf-db/app-db [:questionnaire :answers]))))))
