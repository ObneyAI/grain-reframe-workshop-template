(ns app.pages.auth
  (:require [app.auth.interface :as auth]
            [app.form.interface :as form]
            [app.ui.interface :as ui]
            [uix.core :as uix :refer [defui $]]))

(defn- query-param [key]
  (.get (js/URLSearchParams. (.-search js/window.location)) key))

(defn- use-clean-feedback []
  (uix/use-effect
   (fn [] (auth/clear-feedback!))
   []))

(defui sign-in-page []
  (use-clean-feedback)
  (let [[email set-email!] (uix/use-state "")
        [password set-password!] (uix/use-state "")
        busy? (auth/use-busy?)
        error (auth/use-error)
        errors (form/field-errors error)]
    ($ ui/auth-layout {:title "Welcome back"
                       :description "Sign in with your email and password."}
       ($ :form {:class "space-y-4"
                 :on-submit (fn [event]
                              (.preventDefault event)
                              (auth/login! {:email-address email :password password}))}
          ($ ui/feedback)
          ($ ui/error-summary {:error error
                               :field-order [:email-address :password]
                               :field-labels {:email-address "Email" :password "Password"}
                               :field-ids {:email-address "sign-in-email"
                                           :password "sign-in-password"}})
          ($ ui/field {:id "sign-in-email" :label "Email" :type "email" :value email
                       :error (:email-address errors)
                       :autocomplete "email" :on-change set-email!})
          ($ ui/field {:id "sign-in-password" :label "Password" :type "password" :value password
                       :error (:password errors)
                       :autocomplete "current-password" :on-change set-password!})
          ($ ui/submit-button {:busy? busy? :label "Sign in" :busy-label "Signing in…"})
          ($ :button {:type "button"
                      :class "w-full text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline disabled:opacity-50"
                      :disabled (or busy? (empty? email))
                      :on-click #(auth/request-email-verification! email)}
             "Resend verification email")
          ($ :div {:class "flex justify-between text-sm"}
             ($ :a {:class "underline-offset-4 hover:underline" :href "/auth/forgot-password"} "Forgot password?")
             ($ :a {:class "text-primary underline-offset-4 hover:underline" :href "/auth/sign-up"} "Create account"))))))

(defui sign-up-page []
  (use-clean-feedback)
  (let [[email set-email!] (uix/use-state "")
        [password set-password!] (uix/use-state "")
        [confirm set-confirm!] (uix/use-state "")
        [local-error set-local-error!] (uix/use-state nil)
        busy? (auth/use-busy?)
        error (auth/use-error)
        errors (form/field-errors error)]
    ($ ui/auth-layout {:title "Create your account"
                       :description "Use at least 8 characters with uppercase, lowercase, and a number."}
       ($ :form {:class "space-y-4"
                 :on-submit (fn [event]
                              (.preventDefault event)
                              (if (= password confirm)
                                (do (set-local-error! nil)
                                    (auth/sign-up! {:email-address email
                                                    :password password
                                                    :confirm-password confirm}))
                                (set-local-error! "Passwords do not match.")))}
          ($ ui/feedback)
          ($ ui/error-summary {:error error
                               :field-order [:email-address :password :confirm-password]
                               :field-labels {:email-address "Email"
                                              :password "Password"
                                              :confirm-password "Confirm password"}
                               :field-ids {:email-address "sign-up-email"
                                           :password "sign-up-password"
                                           :confirm-password "sign-up-confirm-password"}})
          (when local-error
            ($ :div {:class "rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive"}
               local-error))
          ($ ui/field {:id "sign-up-email" :label "Email" :type "email" :value email
                       :error (:email-address errors)
                       :autocomplete "email" :on-change set-email!})
          ($ ui/field {:id "sign-up-password" :label "Password" :type "password" :value password
                       :error (:password errors)
                       :autocomplete "new-password" :on-change set-password!})
          ($ ui/field {:id "sign-up-confirm-password" :label "Confirm password" :type "password" :value confirm
                       :error (:confirm-password errors)
                       :autocomplete "new-password" :on-change set-confirm!})
          ($ ui/submit-button {:busy? busy? :label "Create account" :busy-label "Creating…"})
          ($ :button {:type "button"
                      :class "w-full text-sm text-muted-foreground underline-offset-4 hover:text-foreground hover:underline disabled:opacity-50"
                      :disabled (or busy? (empty? email))
                      :on-click #(auth/request-email-verification! email)}
             "Resend verification email")
          ($ :p {:class "text-center text-sm text-muted-foreground"}
             "Already registered? "
             ($ :a {:class "text-primary underline-offset-4 hover:underline" :href "/auth/sign-in"} "Sign in"))))))

(defui forgot-password-page []
  (use-clean-feedback)
  (let [[email set-email!] (uix/use-state "")
        busy? (auth/use-busy?)]
    ($ ui/auth-layout {:title "Reset your password"
                       :description "We'll send a reset link if an account matches that email."}
       ($ :form {:class "space-y-4"
                 :on-submit (fn [event]
                              (.preventDefault event)
                              (auth/request-password-reset! email))}
          ($ ui/feedback)
          ($ ui/field {:label "Email" :type "email" :value email
                       :autocomplete "email" :on-change set-email!})
          ($ ui/submit-button {:busy? busy? :label "Send reset link" :busy-label "Sending…"})
          ($ :p {:class "text-center text-sm"}
             ($ :a {:class "underline-offset-4 hover:underline" :href "/auth/sign-in"} "Back to sign in"))))))

(defui verify-email-page []
  (use-clean-feedback)
  (let [token (query-param "verification-token")
        busy? (auth/use-busy?)]
    (uix/use-effect
     (fn []
       (when token (auth/verify-email! token)))
     [token])
    ($ ui/auth-layout {:title "Verify your email"
                       :description (if token "We're confirming your link." "This verification link is incomplete.")}
       ($ :div {:class "space-y-4"}
          ($ ui/feedback)
          (when (and token busy?)
            ($ :div {:class "flex items-center gap-3 text-sm text-muted-foreground"}
               ($ :span {:class "size-4 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-muted-foreground"})
               "Verifying…"))
          ($ :a {:class "inline-flex h-8 w-full items-center justify-center rounded-lg bg-primary px-2.5 text-sm font-medium text-primary-foreground hover:bg-primary/80"
                 :href "/auth/sign-in"} "Go to sign in")))))

(defui reset-password-page []
  (use-clean-feedback)
  (let [token (query-param "reset-token")
        [password set-password!] (uix/use-state "")
        [confirm set-confirm!] (uix/use-state "")
        [local-error set-local-error!] (uix/use-state nil)
        busy? (auth/use-busy?)]
    ($ ui/auth-layout {:title "Choose a new password"
                       :description "Use at least 8 characters with uppercase, lowercase, and a number."}
       (if-not token
         ($ :div {:class "rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive"}
            "This reset link is incomplete.")
         ($ :form {:class "space-y-4"
                   :on-submit (fn [event]
                                (.preventDefault event)
                                (if (= password confirm)
                                  (do (set-local-error! nil)
                                      (auth/reset-password! token password))
                                  (set-local-error! "Passwords do not match.")))}
            ($ ui/feedback)
            (when local-error
              ($ :div {:class "rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive"}
                 local-error))
            ($ ui/field {:label "New password" :type "password" :value password
                         :autocomplete "new-password" :on-change set-password!})
            ($ ui/field {:label "Confirm password" :type "password" :value confirm
                         :autocomplete "new-password" :on-change set-confirm!})
            ($ ui/submit-button {:busy? busy? :label "Reset password" :busy-label "Resetting…"}))))))
