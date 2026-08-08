(ns app.router.core
  (:require [app.auth.interface :as auth]
            [app.pages.auth :as auth-pages]
            [app.pages.examples :as examples]
            [app.pages.home :as home]
            [app.pages.outcomes :as outcomes]
            [app.router.interface :as router]
            [uix.core :as uix :refer [defui $]]))

(def page-modules
  {:home home/page
   :questionnaire-example examples/questionnaire-page
   :query-route-example examples/query-route-page
   :sign-in auth-pages/sign-in-page
   :sign-up auth-pages/sign-up-page
   :forgot-password auth-pages/forgot-password-page
   :verify-email auth-pages/verify-email-page
   :reset-password auth-pages/reset-password-page
   :forbidden outcomes/forbidden-page
   :not-found outcomes/not-found-page})

(defui router-outlet []
  (let [[match set-match!] (uix/use-state @router/current-match)
        auth-status (auth/use-status)
        decision (router/route-decision match auth-status)]
    (uix/use-effect
     (fn []
       (let [watch-key ::router-outlet]
         (add-watch router/current-match watch-key
                    (fn [_ _ _ next-match] (set-match! next-match)))
         #(remove-watch router/current-match watch-key)))
     [])
    (uix/use-effect
     (fn []
       (when (= :redirect (:action decision))
         (if-let [path (:path decision)]
           (router/navigate-path! path)
           (router/navigate! (:route decision) (:query-params decision)))))
     [decision])
    (case (:action decision)
      :render (if-let [page-module (get page-modules (get-in match [:data :page]))]
                ($ page-module)
                ($ outcomes/not-found-page))
      ($ outcomes/session-loading-page))))
