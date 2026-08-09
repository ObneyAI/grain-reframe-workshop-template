(ns app.pages.customer
  (:require [app.customer.components :as components]
            [app.customer.interface :as customer]
            [app.router.interface :as router]
            [app.ui.interface :as ui]
            [uix.core :as uix :refer [defui $]]))

(def statuses #{:lead :active :inactive})

(defn- parsed-status [value]
  (let [status (some-> value keyword)]
    (when (contains? statuses status) status)))

(defn- parsed-sort [value]
  (if (= value "name-desc") :name-desc :name-asc))

(defn- parsed-uuid [value]
  (when value
    (try (uuid value) (catch :default _ nil))))

(defn- route-query [filters record-id tab]
  (cond-> {:sort (name (:sort filters))}
    (:status filters) (assoc :status (name (:status filters)))
    record-id (assoc :record-id (str record-id))
    tab (assoc :tab tab)))

(defui page []
  (let [match (router/current)
        params (:query-params match)
        status (parsed-status (:status params))
        sort (parsed-sort (:sort params))
        filters {:status status :sort sort}
        customer-id (parsed-uuid (:record-id params))
        tab (or (:tab params) "summary")
        index-state (customer/use-index filters)
        detail-state (customer/use-detail customer-id)]
    (uix/use-effect
     (fn [] (customer/load-index! {:status status :sort sort}))
     [status sort])
    (uix/use-effect
     (fn [] (when customer-id (customer/load-detail! customer-id)))
     [customer-id])
    ($ ui/app-shell
       {:title "Customer workbench"
        :actions ($ components/create-customer
                    {:filters filters
                     :route-query (route-query filters customer-id tab)})}
       ($ :div {:class "space-y-6"}
          ($ components/workbench-intro)
          ($ components/index-controls
             {:filters filters
              :on-change (fn [next-filters]
                           (router/navigate! :customer-workbench
                                             (route-query next-filters customer-id tab)))})
          ($ components/customer-index
             {:state index-state
              :on-retry #(customer/retry-index! filters)
              :on-select #(router/navigate! :customer-workbench
                                            (route-query filters % tab))
              :on-status #(customer/change-status! %1 %2 filters)})
          ($ components/customer-detail
             {:customer-id customer-id
              :state detail-state
              :tab tab
              :on-close #(router/navigate! :customer-workbench
                                           (route-query filters nil nil))
              :on-retry #(customer/retry-detail! customer-id)
              :on-tab #(router/navigate! :customer-workbench
                                         (route-query filters customer-id %))
              :on-status #(customer/change-status! customer-id % filters)})))))
