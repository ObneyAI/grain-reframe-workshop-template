(ns app.auth.events
  (:require [app.api.interface :as api]
            [re-frame.core :as rf]))

(defn- begin [db]
  (-> db
      (assoc-in [:auth :busy?] true)
      (assoc-in [:auth :error] nil)
      (assoc-in [:auth :notice] nil)))

(rf/reg-event-fx
 ::initialize
 (fn [{:keys [db]} _]
   (merge
    {:db (-> db
             (assoc-in [:auth :status] :checking)
             (assoc-in [:auth :error] nil))}
    (api/query {:name :user/session
                :on-success [::session-loaded]
                :on-failure [::session-missing]}))))

(rf/reg-event-db
 ::session-loaded
 (fn [db [_ user]]
   (assoc db :auth {:status :authenticated
                    :user user
                    :busy? false
                    :error nil
                    :notice nil})))

(rf/reg-event-db
 ::session-missing
 (fn [db _]
   (assoc db :auth {:status :anonymous
                    :user nil
                    :busy? false
                    :error nil
                    :notice nil})))

(rf/reg-event-fx
 ::login
 (fn [{:keys [db]} [_ credentials]]
   (merge
    {:db (begin db)}
    (api/command {:name :user/login
                  :params credentials
                  :on-success [::login-succeeded]
                  :on-failure [::operation-failed]}))))

(rf/reg-event-fx
 ::login-succeeded
 (fn [{:keys [db]} _]
   {:db (assoc-in db [:auth :busy?] false)
    :dispatch [::initialize]}))

(rf/reg-event-fx
 ::logout
 (fn [{:keys [db]} _]
   (merge
    {:db (begin db)}
    (api/command {:name :user/logout
                  :on-success [::logout-succeeded]
                  :on-failure [::operation-failed]}))))

(rf/reg-event-db
 ::logout-succeeded
 (fn [db _]
   (assoc db :auth {:status :anonymous
                    :user nil
                    :busy? false
                    :error nil
                    :notice "You are signed out."})))

(rf/reg-event-fx
 ::sign-up
 (fn [{:keys [db]} [_ account]]
   (merge
    {:db (begin db)}
    (api/command {:name :user/sign-up
                  :params account
                  :on-success [::operation-succeeded
                               "Account created. Check your email to verify it."]
                  :on-failure [::operation-failed]}))))

(rf/reg-event-fx
 ::request-password-reset
 (fn [{:keys [db]} [_ email-address]]
   (merge
    {:db (begin db)}
    (api/command {:name :user/request-password-reset
                  :params {:email-address email-address}
                  :on-success [::operation-succeeded
                               "If that account exists, a reset link has been sent."]
                  :on-failure [::operation-failed]}))))

(rf/reg-event-fx
 ::request-email-verification
 (fn [{:keys [db]} [_ email-address]]
   (merge
    {:db (begin db)}
    (api/command {:name :user/request-email-verification
                  :params {:email-address email-address}
                  :on-success [::operation-succeeded
                               "If an unverified account exists, a new verification email has been sent."]
                  :on-failure [::operation-failed]}))))

(rf/reg-event-fx
 ::verify-email
 (fn [{:keys [db]} [_ verification-token]]
   (merge
    {:db (begin db)}
    (api/command {:name :user/verify-email
                  :params {:verification-token verification-token}
                  :on-success [::operation-succeeded "Email verified. You can sign in."]
                  :on-failure [::operation-failed]}))))

(rf/reg-event-fx
 ::reset-password
 (fn [{:keys [db]} [_ reset-token password]]
   (merge
    {:db (begin db)}
    (api/command {:name :user/reset-password
                  :params {:reset-token reset-token :password password}
                  :on-success [::operation-succeeded "Password reset. You can sign in."]
                  :on-failure [::operation-failed]}))))

(rf/reg-event-db
 ::operation-succeeded
 (fn [db [_ notice _result]]
   (-> db
       (assoc-in [:auth :busy?] false)
       (assoc-in [:auth :error] nil)
       (assoc-in [:auth :notice] notice))))

(rf/reg-event-db
 ::operation-failed
 (fn [db [_ error]]
   (-> db
       (assoc-in [:auth :busy?] false)
       (assoc-in [:auth :error] error)
       (assoc-in [:auth :notice] nil))))

(rf/reg-event-db
 ::clear-feedback
 (fn [db _]
   (-> db
       (assoc-in [:auth :error] nil)
       (assoc-in [:auth :notice] nil))))
