(ns app.request.core-test
  (:require [app.request.core :as request]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest request-keys-remain-independent
  (let [db (-> {}
               (request/begin [:deals :index] "deals-1" [:deals/load])
               (request/begin [:relationships :index] "relationships-1" [:relationships/load]))
        deals-finished (request/settle db [:deals :index] "deals-1" :success nil)]
    (is (= :success (:status (request/request-state deals-finished [:deals :index]))))
    (is (= :pending (:status (request/request-state deals-finished [:relationships :index]))))
    (is (= [:relationships/load]
           (:retry-event (request/request-state deals-finished [:relationships :index]))))))

(deftest superseded-and-cancelled-responses-cannot-settle
  (testing "a newer operation owns the stable request key"
    (let [db (-> {}
                 (request/begin :contacts "contacts-1" [:contacts/load])
                 (request/begin :contacts "contacts-2" [:contacts/load]))
          stale-result (request/settle db :contacts "contacts-1" :failure {:message "stale"})]
      (is (= db stale-result))
      (is (= 2 (:attempt (request/request-state db :contacts))))))

  (testing "cancellation invalidates the operation id"
    (let [db (request/begin {} :contacts "contacts-1" [:contacts/load])
          cancelled (request/cancel db :contacts)
          late-result (request/settle cancelled :contacts "contacts-1" :success nil)]
      (is (= :cancelled (:status (request/request-state cancelled :contacts))))
      (is (= cancelled late-result)))))

(deftest failure-and-retry-state-is-consistent
  (let [pending (request/begin {} :pipeline "pipeline-1" [:pipeline/load])
        failed (request/settle pending :pipeline "pipeline-1" :failure
                               {:app.anomalies/message "Offline"
                                :http/status 503
                                :http/response {:headers {:authorization "do not retain"}}})
        state (request/request-state failed :pipeline)]
    (is (= :failure (:status state)))
    (is (= {:app.anomalies/message "Offline" :http/status 503} (:error state)))
    (is (= [:pipeline/load] (:retry-event state)))))
