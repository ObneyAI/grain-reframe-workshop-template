(ns app.query-resource.interface
  "Keyed Grain query resources for feature modules.

   Callers supply a stable resource key and a Grain query descriptor. This
   module owns cache freshness and load deduplication; app.request.interface
   continues to own request lifecycle, retry, cancellation, and supersession."
  (:require [app.api.interface :as api]
            [app.query-resource.core :as core]
            [app.re-frame.interface :refer [use-subscribe]]
            [app.request.core :as request-core]
            [app.request.interface :as request]
            [re-frame.core :as rf]))

(defn- request-key
  [resource-key]
  [:query-resource resource-key])

(rf/reg-sub
 ::state
 (fn [db [_ resource-key]]
   (let [resource (core/resource-state db resource-key)
         lifecycle (request-core/request-state db (request-key resource-key))]
     (merge resource
            (select-keys lifecycle [:status :attempt :error])))))

(rf/reg-event-fx
 ::load
 (fn [{:keys [db]} [_ resource-key {:keys [name params] :as query} force?]]
   (let [lifecycle (request-core/request-state db (request-key resource-key))]
     (if (core/should-load? (core/resource-state db resource-key)
                            (:status lifecycle)
                            force?)
       (merge
        {:db (core/begin-load db resource-key query)}
        (api/query {:name name
                    :params params
                    :request-key (request-key resource-key)
                    :retry-event [::load resource-key query true]
                    :on-success [::loaded resource-key]
                    :on-failure [::failed resource-key]}))
       {}))))

(rf/reg-event-db
 ::loaded
 (fn [db [_ resource-key result]]
   (let [query (get-in db [:query-resources resource-key :query])]
     (cond-> (core/store db resource-key result)
       query (assoc-in [:query-resources resource-key :query] query)))))

(rf/reg-event-db
 ::failed
 (fn [db [_ resource-key _error]]
   (core/fail-load db resource-key)))

(rf/reg-event-db
 ::invalidate
 (fn [db [_ resource-key]]
   (core/invalidate db resource-key)))

(rf/reg-event-fx
 ::refresh
 (fn [{:keys [db]} [_ resource-key]]
   (if-let [query (get-in db [:query-resources resource-key :query])]
     {:dispatch [::load resource-key query true]}
     {})))

(rf/reg-event-fx
 ::cancel
 (fn [{:keys [db]} [_ resource-key]]
   {:db (core/fail-load db resource-key)
    :dispatch (request/cancel-event (request-key resource-key))}))

(defn load-event
  ([resource-key query] [::load resource-key query false])
  ([resource-key query force?] [::load resource-key query force?]))

(defn invalidate-event [resource-key] [::invalidate resource-key])
(defn refresh-event [resource-key] [::refresh resource-key])

(defn load!
  ([resource-key query]
   (load! resource-key query false))
  ([resource-key query force?]
   (rf/dispatch [::load resource-key query force?])))

(defn invalidate!
  [resource-key]
  (rf/dispatch [::invalidate resource-key]))

(defn retry!
  [resource-key]
  (request/retry! (request-key resource-key)))

(defn cancel!
  [resource-key]
  (rf/dispatch [::cancel resource-key]))

(defn use-state
  [resource-key]
  (use-subscribe [::state resource-key]))
