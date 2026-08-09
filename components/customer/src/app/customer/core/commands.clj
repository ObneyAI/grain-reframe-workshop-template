(ns app.customer.core.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [app.auth.interface :as auth]
            [app.customer.core.read-models :as read-models]
            [clojure.string :as string]
            [cognitect.anomalies :as anomaly]))

(defn- reject
  [category message]
  {::anomaly/category category
   ::anomaly/message message})

(defn- normalized-email
  [email-address]
  (some-> email-address string/trim string/lower-case))

(defcommand :customer create-customer
  {:authorized? auth/authenticated?
   :grain.event-model/reads #{:customer/customers}
   :grain.event-model/produces #{:customer/customer-created}}
  [{{:keys [email-address name]} :command :as context}]
  (let [email-address (normalized-email email-address)
        email-taken? (some #(= email-address (:email-address %))
                           (vals (read-models/all-customers context)))]
    (cond
      (string/blank? name)
      (reject ::anomaly/incorrect "Name is required.")

      (string/blank? email-address)
      (reject ::anomaly/incorrect "Email is required.")

      email-taken?
      (reject ::anomaly/conflict "A customer with that email already exists.")

      :else
      (let [customer-id (random-uuid)]
        {:command-result/events
         [(->event {:type :customer/customer-created
                    :tags #{[:customer customer-id]}
                    :body {:customer-id customer-id
                           :name (string/trim name)
                           :email-address email-address}})]
         :command/result {:customer-id customer-id}}))))

(defcommand :customer change-status
  {:authorized? auth/authenticated?
   :grain.event-model/reads #{:customer/customers}
   :grain.event-model/produces #{:customer/status-changed}}
  [{{:keys [customer-id status]} :command :as context}]
  (if-let [customer (read-models/customer context customer-id)]
    (if (= status (:status customer))
      {:command/result {:customer-id customer-id :status status}}
      {:command-result/events
       [(->event {:type :customer/status-changed
                  :tags #{[:customer customer-id]}
                  :body {:customer-id customer-id :status status}})]
       :command/result {:customer-id customer-id :status status}})
    (reject ::anomaly/not-found "Customer not found.")))
