(ns app.query-resource.core
  "Pure query-resource state transitions.")

(def idle-state
  {:data nil
   :loading? false
   :stale? true})

(defn resource-state
  [db resource-key]
  (merge idle-state (get-in db [:query-resources resource-key])))

(defn store
  [db resource-key data]
  (-> db
      (assoc-in [:query-resources resource-key :data] data)
      (assoc-in [:query-resources resource-key :loading?] false)
      (assoc-in [:query-resources resource-key :stale?] false)))

(defn begin-load
  [db resource-key query]
  (-> db
      (assoc-in [:query-resources resource-key :query] query)
      (assoc-in [:query-resources resource-key :loading?] true)))

(defn fail-load
  [db resource-key]
  (assoc-in db [:query-resources resource-key :loading?] false))

(defn invalidate
  [db resource-key]
  (assoc-in db [:query-resources resource-key :stale?] true))

(defn should-load?
  [{:keys [loading? stale?]} request-status force?]
  (and (not= :pending request-status)
       (not loading?)
       (or force? stale?)))
