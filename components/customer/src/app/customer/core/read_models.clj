(ns app.customer.core.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(def customer-event-types
  #{:customer/customer-created
    :customer/status-changed})

(defmulti customers* (fn [_state event] (:event/type event)))
(defmethod customers* :customer/customer-created
  [state {:keys [customer-id email-address name]}]
  (assoc state customer-id
         {:customer-id customer-id
          :name name
          :email-address email-address
          :status :lead
          :activity [{:type :customer-created
                      :status :lead}]}))
(defmethod customers* :customer/status-changed
  [state {:keys [customer-id status]}]
  (if (contains? state customer-id)
    (-> state
        (assoc-in [customer-id :status] status)
        (update-in [customer-id :activity]
                   conj
                   {:type :status-changed
                    :status status}))
    state))
(defmethod customers* :default [state _event] state)

(defreadmodel :customer customers
  {:events customer-event-types :version 1}
  [state event]
  (customers* state event))

(defn all-customers [context] (rmp/project context :customer/customers))
(defn customer [context customer-id] (get (all-customers context) customer-id))
