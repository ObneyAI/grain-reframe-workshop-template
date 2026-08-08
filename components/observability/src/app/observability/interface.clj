(ns app.observability.interface
  (:require [com.brunobonacci.mulog :as u]
            [io.pedestal.interceptor :as interceptor])
  (:import [java.time Clock Instant]
           [java.util UUID]))

(defprotocol Metrics
  (increment! [this metric amount])
  (observe! [this metric value])
  (snapshot [this]))

(defrecord InMemoryMetrics [state]
  Metrics
  (increment! [_ metric amount]
    (swap! state update-in [:counters metric] (fnil + 0) amount)
    nil)
  (observe! [_ metric value]
    (swap! state update-in [:observations metric]
           (fn [{:keys [count sum min max]
                 :or {count 0 sum 0.0}}]
             {:count (inc count)
              :sum (+ sum value)
              :min (if (nil? min) value (clojure.core/min min value))
              :max (if (nil? max) value (clojure.core/max max value))}))
    nil)
  (snapshot [_]
    (let [{:keys [counters observations]} @state]
      {:counters (or counters {})
       :observations
       (into {}
             (map (fn [[metric {:keys [count sum] :as values}]]
                    [metric (assoc values :average (when (pos? count) (/ sum count)))]))
             observations)})))

(defn metrics
  ([] (metrics (atom {})))
  ([state] (->InMemoryMetrics state)))

(defn start-logger
  "Start the configured μ/log adapter. Destinations are :console, :json-console,
   and :file. The caller supplies the redaction transform so every destination
   receives the same sanitized events."
  [{:keys [destination filename transform pretty?]
    :or {destination :console pretty? true}}]
  (case destination
    :console (u/start-publisher! {:type :console :pretty? pretty? :transform transform})
    :json-console (u/start-publisher! {:type :console-json :transform transform})
    :file (u/start-publisher! {:type :simple-file :filename filename :transform transform})
    (throw (ex-info "Unknown log destination" {:observability/destination destination}))))

(defn- safe-request-id
  [value]
  (when (and (string? value) (re-matches #"[A-Za-z0-9._:-]{1,128}" value))
    value))

(defn request-observability-interceptor
  "Attach or generate a correlation ID, emit one completion event, and update
   transport-level request metrics without retaining request bodies or headers."
  [{:keys [metrics clock] :or {clock (Clock/systemUTC)}}]
  (interceptor/interceptor
   {:name ::request-observability
    :enter
    (fn [context]
      (let [request-id (or (safe-request-id
                            (get-in context [:request :headers "x-request-id"]))
                           (str (UUID/randomUUID)))]
        (increment! metrics :http/active 1)
        (-> context
            (assoc ::started-nanos (System/nanoTime))
            (assoc-in [:request :request-id] request-id))))
    :leave
    (fn [context]
      (let [request-id (get-in context [:request :request-id])
            status (or (get-in context [:response :status]) 500)
            duration-ms (/ (- (System/nanoTime) (::started-nanos context)) 1000000.0)]
        (increment! metrics :http/active -1)
        (increment! metrics :http/requests 1)
        (increment! metrics (keyword "http.status" (str status)) 1)
        (observe! metrics :http/duration-ms duration-ms)
        (u/log ::request-completed
               :request-id request-id
               :request-method (get-in context [:request :request-method])
               :request-path (get-in context [:request :uri])
               :response-status status
               :duration-ms duration-ms
               :occurred-at (str (Instant/now clock)))
        (assoc-in context [:response :headers "X-Request-ID"] request-id)))
    :error
    (fn [context exception]
      (increment! metrics :http/active -1)
      (increment! metrics :http/errors 1)
      (u/log ::request-failed
             :request-id (get-in context [:request :request-id])
             :request-method (get-in context [:request :request-method])
             :request-path (get-in context [:request :uri])
             :error-class (.getName (class exception)))
      (throw exception))}))

(defn health-report
  "Run small non-destructive diagnostic checks. A check returns any value for a
   healthy dependency and throws (or returns false) when unhealthy."
  [checks]
  (let [results
        (into (sorted-map)
              (map (fn [[check-name check]]
                     [check-name
                      (try
                        (let [detail (check)]
                          (if (false? detail)
                            {:status :error}
                            {:status :ok :detail detail}))
                        (catch Exception exception
                          {:status :error
                           :error-class (.getName (class exception))}))]))
              checks)
        healthy? (every? #(= :ok (:status %)) (vals results))]
    {:status (if healthy? :ok :error)
     :checks results}))
