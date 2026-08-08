(ns app.observability.interface-test
  (:require [app.observability.interface :as observability]
            [clojure.test :refer [deftest is]]))

(deftest metrics-summarize-counters-and-observations
  (let [metrics (observability/metrics)]
    (observability/increment! metrics :requests 2)
    (observability/observe! metrics :latency 10)
    (observability/observe! metrics :latency 20)
    (is (= 2 (get-in (observability/snapshot metrics) [:counters :requests])))
    (is (= {:count 2 :sum 30.0 :min 10 :max 20 :average 15.0}
           (get-in (observability/snapshot metrics) [:observations :latency])))))

(deftest health-report-is-honest-about-failures
  (let [report (observability/health-report
                {:application (constantly "ready")
                 :dependency #(throw (ex-info "down" {}))})]
    (is (= :error (:status report)))
    (is (= :ok (get-in report [:checks :application :status])))
    (is (= :error (get-in report [:checks :dependency :status])))))

(deftest correlation-ids-are-propagated-but-untrusted-values-are-replaced
  (let [metrics (observability/metrics)
        interceptor (observability/request-observability-interceptor {:metrics metrics})
        enter (:enter interceptor)
        leave (:leave interceptor)
        accepted (enter {:request {:headers {"x-request-id" "request-123"}}})
        rejected (enter {:request {:headers {"x-request-id" "bad\nvalue"}}})]
    (is (= "request-123" (get-in accepted [:request :request-id])))
    (is (not= "bad\nvalue" (get-in rejected [:request :request-id])))
    (is (= "request-123"
           (get-in (leave (assoc accepted :response {:status 200}))
                   [:response :headers "X-Request-ID"])))))
