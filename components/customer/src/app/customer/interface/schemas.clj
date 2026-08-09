(ns app.customer.interface.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(def customer-status [:enum :lead :active :inactive])

(def activity-entry
  [:map
   [:type [:enum :customer-created :status-changed]]
   [:status customer-status]])

(def customer
  [:map
   [:customer-id :uuid]
   [:name [:string {:min 1}]]
   [:email-address [:string {:min 3}]]
   [:status customer-status]
   [:activity [:vector activity-entry]]])

(defschemas event-schemas
  {:customer/customer-created
   [:map
    [:customer-id :uuid]
    [:name [:string {:min 1}]]
    [:email-address [:string {:min 3}]]]

   :customer/status-changed
   [:map
    [:customer-id :uuid]
    [:status customer-status]]})

(defschemas command-schemas
  {:customer/create-customer
   [:map
    [:name [:string {:min 1 :error/message "Name is required"}]]
    [:email-address [:string {:min 3 :error/message "Email is required"}]]]

   :customer/change-status
   [:map
    [:customer-id :uuid]
    [:status customer-status]]})

(defschemas query-schemas
  {:customer/index
   [:map
    [:status {:optional true} customer-status]
    [:sort {:optional true} [:enum :name-asc :name-desc]]]

   :customer/detail
   [:map [:customer-id :uuid]]})

(defschemas read-model-schemas
  {:customer/customers [:map-of :uuid customer]})
