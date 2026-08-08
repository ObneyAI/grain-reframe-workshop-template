(ns app.api.interface-test
  (:require [app.api.interface :as api]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest command-and-query-effects-retain-their-grain-shape
  (let [command (::api/request
                 (api/command {:name :contact/create
                               :params {:name "Alex"}}))
        query (::api/request
               (api/query {:name :deal/index
                           :params {:stage :qualified}}))]
    (is (= :command (:kind command)))
    (is (= {:command/name :contact/create :name "Alex"} (:payload command)))
    (is (= :query (:kind query)))
    (is (= {:query/name :deal/index :stage :qualified} (:payload query)))))

(deftest lifecycle-metadata-is-opt-in-and-unique
  (testing "unkeyed auth-style calls retain the original callback behavior"
    (is (nil? (:operation-id (::api/request (api/query {:name :user/session}))))))

  (testing "keyed feature calls get unique operation ids for supersession"
    (let [first-request (::api/request
                         (api/query {:name :deal/index
                                     :request-key [:deals :index]
                                     :retry-event [:deals/load]}))
          second-request (::api/request
                          (api/query {:name :deal/index
                                      :request-key [:deals :index]
                                      :retry-event [:deals/load]}))]
      (is (= [:deals :index] (:request-key first-request)))
      (is (= [:deals/load] (:retry-event first-request)))
      (is (string? (:operation-id first-request)))
      (is (not= (:operation-id first-request) (:operation-id second-request))))))
