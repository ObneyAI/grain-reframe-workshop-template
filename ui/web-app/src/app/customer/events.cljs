(ns app.customer.events
  (:require [app.api.interface :as api]
            [app.notification.interface :as notification]
            [app.query-resource.interface :as resource]
            [app.router.interface :as router]
            [re-frame.core :as rf]))

(def create-request-key [:customer :create])
(defn status-request-key [customer-id] [:customer :change-status customer-id])

(rf/reg-event-fx
 ::create
 (fn [_ [_ customer index-key route-query]]
   (api/command {:name :customer/create-customer
                 :params customer
                 :request-key create-request-key
                 :retry-event [::create customer index-key route-query]
                 :on-success [::created index-key route-query]
                 :on-failure [::create-failed]})))

(rf/reg-event-fx
 ::created
 (fn [_ [_ index-key route-query {:keys [customer-id]}]]
   (merge
    {:dispatch (resource/refresh-event index-key)}
    (router/navigation-effect :customer-workbench
                              (assoc route-query :record-id (str customer-id)))
    (notification/effect {:title "Customer created"
                          :description "The event was projected into the customer read model."
                          :type "success"}))))

(rf/reg-event-fx
 ::create-failed
 (fn [_ [_ error]]
   (notification/effect {:title "Customer was not created"
                         :description (:cognitect.anomalies/message error)
                         :type "error"})))

(rf/reg-event-fx
 ::change-status
 (fn [{:keys [db]} [_ customer-id status index-key detail-key]]
   (merge
    {:db (assoc-in db [:customer :optimistic-status customer-id] status)}
    (api/command {:name :customer/change-status
                  :params {:customer-id customer-id :status status}
                  :request-key (status-request-key customer-id)
                  :retry-event [::change-status customer-id status index-key detail-key]
                  :on-success [::status-changed customer-id index-key detail-key]
                  :on-failure [::status-change-failed customer-id]}))))

(rf/reg-event-fx
 ::status-changed
 (fn [{:keys [db]} [_ customer-id index-key detail-key _result]]
   (merge
    {:db (update-in db [:customer :optimistic-status] dissoc customer-id)
     :dispatch-n [(resource/refresh-event index-key)
                  (resource/refresh-event detail-key)]}
    (notification/effect {:title "Status updated"
                          :description "The projected customer view is refreshing."
                          :type "success"}))))

(rf/reg-event-fx
 ::status-change-failed
 (fn [{:keys [db]} [_ customer-id error]]
   (merge
    {:db (update-in db [:customer :optimistic-status] dissoc customer-id)}
    (notification/effect {:title "Status was not updated"
                          :description (:cognitect.anomalies/message error)
                          :type "error"}))))
