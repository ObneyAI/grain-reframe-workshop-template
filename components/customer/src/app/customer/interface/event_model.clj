(ns app.customer.interface.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel :customer
  {:description "A disposable customer workbench proving the full Grain and Re-frame path."
   :commands
   {:customer/create-customer
    {:description "Creates a customer in lead status."
     :schema [:map
              [:name [:string {:min 1 :error/message "Name is required"}]]
              [:email-address [:string {:min 3 :error/message "Email is required"}]]]
     :reads #{:customer/customers}
     :produces #{:customer/customer-created}
     :grain/allium [{:spec "components/customer/customer.allium" :kind :rule :name "CreateCustomer"}]}

    :customer/change-status
    {:description "Moves a customer to another lifecycle status."
     :schema [:map
              [:customer-id :uuid]
              [:status [:enum :lead :active :inactive]]]
     :reads #{:customer/customers}
     :produces #{:customer/status-changed}
     :grain/allium [{:spec "components/customer/customer.allium" :kind :rule :name "ChangeCustomerStatus"}]}}

   :events
   {:customer/customer-created
    {:description "A customer was created as a lead."
     :schema [:map
              [:customer-id :uuid]
              [:name [:string {:min 1}]]
              [:email-address [:string {:min 3}]]]}

    :customer/status-changed
    {:description "A customer's lifecycle status changed."
     :schema [:map
              [:customer-id :uuid]
              [:status [:enum :lead :active :inactive]]]}}

   :read-models
   {:customer/customers
    {:description "Customers keyed by ID with current status and activity."
     :consumes #{:customer/customer-created :customer/status-changed}
     :version 1}}

   :queries
   {:customer/index
    {:description "Lists customers with optional status and sort controls."
     :schema [:map
              [:status {:optional true} [:enum :lead :active :inactive]]
              [:sort {:optional true} [:enum :name-asc :name-desc]]]
     :reads #{:customer/customers}}

    :customer/detail
    {:description "Returns one customer and its projected activity."
     :schema [:map [:customer-id :uuid]]
     :reads #{:customer/customers}}}

   :todo-processors {}
   :screens
   {:customer/workbench
    {:description "Browse, create, select, and update customers using query-string view state."
     :queries #{:customer/index :customer/detail}
     :commands #{:customer/create-customer :customer/change-status}
     :grain/allium [{:spec "components/customer/customer.allium" :kind :surface :name "CustomerWorkbench"}]}}})
