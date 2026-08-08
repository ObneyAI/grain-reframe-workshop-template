(ns app.auth.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub ::state (fn [db _] (:auth db)))
(rf/reg-sub ::status :<- [::state] (fn [auth _] (:status auth)))
(rf/reg-sub ::user :<- [::state] (fn [auth _] (:user auth)))
(rf/reg-sub ::busy? :<- [::state] (fn [auth _] (:busy? auth)))
(rf/reg-sub ::error :<- [::state] (fn [auth _] (:error auth)))
(rf/reg-sub ::notice :<- [::state] (fn [auth _] (:notice auth)))
(rf/reg-sub ::authenticated? :<- [::status] (fn [status _] (= :authenticated status)))
