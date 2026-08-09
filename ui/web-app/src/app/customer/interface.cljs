(ns app.customer.interface
  "Disposable customer feature facade. Query state stays in the generic
   resource module; this namespace only supplies customer descriptors/actions."
  (:require [app.customer.events :as events]
            [app.query-resource.interface :as resource]
            [app.re-frame.interface :refer [use-subscribe]]
            [app.request.interface :as request]
            [re-frame.core :as rf]))

(defn index-key [{:keys [sort status]}]
  [:customer :index (or status :all) (or sort :name-asc)])

(defn index-query [{:keys [sort status]}]
  {:name :customer/index
   :params (cond-> {:sort (or sort :name-asc)} status (assoc :status status))})

(defn detail-key [customer-id] [:customer :detail customer-id])
(defn detail-query [customer-id]
  {:name :customer/detail :params {:customer-id customer-id}})

(rf/reg-sub
 ::optimistic-status
 (fn [db [_ customer-id]]
   (get-in db [:customer :optimistic-status customer-id])))

(defn load-index! [filters]
  (resource/load! (index-key filters) (index-query filters)))

(defn load-detail! [customer-id]
  (resource/load! (detail-key customer-id) (detail-query customer-id)))

(defn create! [customer filters route-query]
  (rf/dispatch [::events/create customer (index-key filters) route-query]))

(defn change-status! [customer-id status filters]
  (rf/dispatch [::events/change-status customer-id status
                (index-key filters) (detail-key customer-id)]))

(defn retry-index! [filters] (resource/retry! (index-key filters)))
(defn retry-detail! [customer-id] (resource/retry! (detail-key customer-id)))
(defn use-index [filters] (resource/use-state (index-key filters)))
(defn use-detail [customer-id] (resource/use-state (detail-key customer-id)))
(defn use-create-state [] (request/use-state events/create-request-key))
(defn use-status-state [customer-id]
  (request/use-state (events/status-request-key customer-id)))
(defn use-optimistic-status [customer-id]
  (use-subscribe [::optimistic-status customer-id]))
