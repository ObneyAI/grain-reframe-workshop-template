(ns app.customer.core.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [app.auth.interface :as auth]
            [app.customer.core.read-models :as read-models]
            [clojure.string :as string]
            [cognitect.anomalies :as anomaly]))

(defn project-index
  [customers {:keys [sort status] :or {sort :name-asc}}]
  (let [direction (if (= :name-desc sort) #(compare %2 %1) compare)]
    (->> customers
         vals
         (filter #(or (nil? status) (= status (:status %))))
         (sort-by (comp string/lower-case :name) direction)
         vec)))

(defquery :customer index
  {:authorized? auth/authenticated?
   :grain.event-model/reads #{:customer/customers}}
  [{:keys [query] :as context}]
  {:query/result
   {:customers (project-index (read-models/all-customers context) query)}})

(defquery :customer detail
  {:authorized? auth/authenticated?
   :grain.event-model/reads #{:customer/customers}}
  [{{:keys [customer-id]} :query :as context}]
  (if-let [customer (read-models/customer context customer-id)]
    {:query/result {:customer customer}}
    {::anomaly/category ::anomaly/not-found
     ::anomaly/message "Customer not found."}))
