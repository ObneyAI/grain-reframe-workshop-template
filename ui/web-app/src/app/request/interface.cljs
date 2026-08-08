(ns app.request.interface
  "Keyed request lifecycle state for concurrent Re-frame reads and writes.
   app.api.interface owns HTTP; this module owns pending/success/failure,
   supersession, cancellation, and caller-supplied retry events."
  (:require [app.re-frame.interface :refer [use-subscribe]]
            [app.request.core :as core]
            [re-frame.core :as rf]))

(rf/reg-sub
 ::state
 (fn [db [_ request-key]]
   (core/request-state db request-key)))

(rf/reg-event-db
 ::begin
 (fn [db [_ request-key operation-id retry-event]]
   (core/begin db request-key operation-id retry-event)))

(rf/reg-event-fx
 ::finish
 (fn [{:keys [db]} [_ request-key operation-id outcome callback result]]
   (if (core/active? db request-key operation-id)
     (cond-> {:db (core/settle db request-key operation-id outcome result)}
       callback (assoc :dispatch (conj callback result)))
     {})))

(rf/reg-event-db
 ::cancel
 (fn [db [_ request-key]]
   (core/cancel db request-key)))

(rf/reg-event-fx
 ::retry
 (fn [{:keys [db]} [_ request-key]]
   (if-let [retry-event (get-in db [:requests request-key :retry-event])]
     {:dispatch retry-event}
     {})))

(defn begin!
  [request-key operation-id retry-event]
  (rf/dispatch [::begin request-key operation-id retry-event]))

(defn finish!
  [request-key operation-id outcome callback result]
  (rf/dispatch [::finish request-key operation-id outcome callback result]))

(defn cancel!
  [request-key]
  (rf/dispatch [::cancel request-key]))

(defn retry!
  [request-key]
  (rf/dispatch [::retry request-key]))

(defn use-state
  [request-key]
  (use-subscribe [::state request-key]))

(defn use-pending?
  [request-key]
  (= :pending (:status (use-state request-key))))
