(ns app.questionnaire.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
 ::answers
 (fn [db _]
   (get-in db [:questionnaire :answers])))
