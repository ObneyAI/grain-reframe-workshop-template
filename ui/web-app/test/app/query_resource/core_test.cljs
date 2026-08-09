(ns app.query-resource.core-test
  (:require [app.query-resource.core :as resource]
            [cljs.test :refer-macros [deftest is]]))

(deftest a-loaded-resource-is-fresh-and-addressed-by-its-stable-key
  (let [resource-key [:session :current]
        db (resource/store {} resource-key {:user/id "user-1"})]
    (is (= {:data {:user/id "user-1"}
            :loading? false
            :stale? false}
           (resource/resource-state db resource-key)))
    (is (= resource/idle-state
           (resource/resource-state db [:session :other])))))

(deftest invalidation-keeps-visible-data-while-marking-it-stale
  (let [resource-key [:session :current]
        loaded (resource/store {} resource-key {:user/id "user-1"})
        invalidated (resource/invalidate loaded resource-key)]
    (is (= {:data {:user/id "user-1"}
            :loading? false
            :stale? true}
           (resource/resource-state invalidated resource-key)))))

(deftest loads-are-deduplicated-unless-a-fresh-resource-is-forced
  (is (true? (resource/should-load? resource/idle-state :idle false)))
  (is (false? (resource/should-load? resource/idle-state :pending false)))
  (is (false? (resource/should-load? {:data nil :loading? true :stale? true} :idle false)))
  (is (false? (resource/should-load? {:data {:id 1} :loading? false :stale? false} :success false)))
  (is (true? (resource/should-load? {:data {:id 1} :loading? false :stale? false} :success true))))

(deftest load-lifecycle-preserves-the-query-and-visible-data
  (let [resource-key [:customer :index]
        query {:name :customer/index :params {:status :lead}}
        loaded (resource/store {} resource-key {:customers [{:name "Acme"}]})
        loading (resource/begin-load loaded resource-key query)
        failed (resource/fail-load loading resource-key)]
    (is (= query (get-in loading [:query-resources resource-key :query])))
    (is (true? (:loading? (resource/resource-state loading resource-key))))
    (is (= {:customers [{:name "Acme"}]}
           (:data (resource/resource-state loading resource-key))))
    (is (false? (:loading? (resource/resource-state failed resource-key))))))
