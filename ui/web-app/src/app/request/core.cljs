(ns app.request.core
  (:require [app.anomalies :as anomaly]))

(def idle-state
  {:status :idle
   :attempt 0
   :error nil})

(defn request-state
  [db request-key]
  (merge idle-state (get-in db [:requests request-key])))

(defn active?
  [db request-key operation-id]
  (= operation-id (get-in db [:requests request-key :operation-id])))

(defn- compact-error
  [result]
  (if (map? result)
    (select-keys result [::anomaly/category ::anomaly/message :error/explain :http/status])
    {::anomaly/message (str result)}))

(defn begin
  [db request-key operation-id retry-event]
  (let [previous (request-state db request-key)]
    (assoc-in db [:requests request-key]
              (cond-> (assoc previous
                             :status :pending
                             :attempt (inc (:attempt previous))
                             :operation-id operation-id
                             :error nil)
                retry-event (assoc :retry-event retry-event)
                (nil? retry-event) (dissoc :retry-event)))))

(defn settle
  "Settle only the currently active operation. A superseded or cancelled
   response cannot overwrite newer request state."
  [db request-key operation-id outcome result]
  (if-not (active? db request-key operation-id)
    db
    (update-in db [:requests request-key]
               (fn [state]
                 (cond-> (-> state
                             (assoc :status outcome)
                             (dissoc :operation-id))
                   (= :success outcome) (assoc :error nil)
                   (= :failure outcome) (assoc :error (compact-error result)))))))

(defn cancel
  [db request-key]
  (if (= :pending (:status (request-state db request-key)))
    (update-in db [:requests request-key]
               #(-> %
                    (assoc :status :cancelled :error nil)
                    (dissoc :operation-id)))
    db))
