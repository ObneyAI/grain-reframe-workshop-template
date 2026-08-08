(ns app.questionnaire.events
  (:require [re-frame.core :as rf]))

(rf/reg-event-db
 ::submitted
 (fn [db [_ answers]]
   (assoc-in db [:questionnaire :answers] answers)))
