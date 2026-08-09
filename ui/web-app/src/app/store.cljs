(ns app.store
  (:require [re-frame.core :as rf]))

(def initial-db
  {:auth {:status :checking
          :user nil
          :busy? false
          :error nil
          :notice nil}
   :query-resources {}
   :questionnaire {:answers nil}})

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   initial-db))
