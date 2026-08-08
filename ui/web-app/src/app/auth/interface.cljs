(ns app.auth.interface
  "Small interface for session state and account workflows. UI modules never
   receive or pass an API client."
  (:require [app.auth.events :as events]
            [app.auth.subs :as subs]
            [app.re-frame.interface :refer [use-subscribe]]
            [re-frame.core :as rf]))

(defn initialize! [] (rf/dispatch [::events/initialize]))
(defn login! [credentials] (rf/dispatch [::events/login credentials]))
(defn logout! [] (rf/dispatch [::events/logout]))
(defn sign-up! [account] (rf/dispatch [::events/sign-up account]))
(defn request-email-verification! [email]
  (rf/dispatch [::events/request-email-verification email]))
(defn request-password-reset! [email] (rf/dispatch [::events/request-password-reset email]))
(defn verify-email! [token] (rf/dispatch [::events/verify-email token]))
(defn reset-password! [token password] (rf/dispatch [::events/reset-password token password]))
(defn clear-feedback! [] (rf/dispatch [::events/clear-feedback]))

(defn use-status [] (use-subscribe [::subs/status]))
(defn use-user [] (use-subscribe [::subs/user]))
(defn use-authenticated? [] (use-subscribe [::subs/authenticated?]))
(defn use-busy? [] (use-subscribe [::subs/busy?]))
(defn use-error [] (use-subscribe [::subs/error]))
(defn use-notice [] (use-subscribe [::subs/notice]))
