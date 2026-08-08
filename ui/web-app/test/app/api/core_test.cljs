(ns app.api.core-test
  (:require [app.anomalies :as anomaly]
            [app.api.core :as api]
            [cljs.core.async :refer [go <! to-chan!]]
            [cljs.test :refer-macros [async deftest is testing]]))

(deftest response-normalization
  (testing "successful responses expose the Grain result"
    (is (= {:ok true}
           (api/response->result {:status 200 :body {:ok true}}))))

  (testing "failed responses have one stable anomaly shape"
    (let [result (api/response->result {:status 409 :body {:message "Already exists"}})]
      (is (= ::anomaly/conflict (::anomaly/category result)))
      (is (= "Already exists" (::anomaly/message result)))))

  (testing "Grain anomaly messages survive the transport boundary"
    (is (= "Session expired"
           (::anomaly/message
            (api/response->result
             {:status 403
              :body {:cognitect.anomalies/message "Session expired"}})))))

  (testing "structured field explanations survive transport normalization"
    (is (= {:email-address ["Email is invalid"]}
           (:error/explain
            (api/response->result
             {:status 400
              :body {:cognitect.anomalies/message "Invalid account"
                     :error/explain {:email-address ["Email is invalid"]}}})))))

  (testing "Grain schema explanations use the stable field-error key"
    (is (= {:email-address ["Email is required"]}
           (:error/explain
            (api/response->result
             {:status 400
              :body {:message "Invalid Command"
                     :explain {:email-address ["Email is required"]}}})))))

  (testing "network failures are unavailable, not generic faults"
    (is (= ::anomaly/unavailable
           (::anomaly/category (api/response->result {:status nil :body nil}))))))

(deftest stub-is-a-real-test-adapter
  (async done
    (let [client (api/stub-client #(to-chan! [{:request % :ok true}]))]
      (go
        (let [result (<! (api/send! client {:kind :query :payload {:query/name :example/all}}))]
          (is (= :query (get-in result [:request :kind])))
          (is (true? (:ok result)))
          (done))))))
