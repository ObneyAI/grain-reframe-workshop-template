(ns app.user-service.interface.event-model
  "The service-area-first event model for the :user area, registered with
   `defeventmodel`. Loading this namespace registers the model so the boot-guard
   (event-model-validator/verify-or-throw!) can reconcile it against the live
   runtime and refuse to start if they disagree. Behaviour lives in the companion
   user-service.allium spec; commands and screens trace to it with :grain/allium."
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]
            ;; Loaded for the ::s/* schema refs below to resolve to the same
            ;; qualified keywords the live registry holds.
            [app.user-service.interface.schemas :as s]))

(defeventmodel :user
  {:description
   "Identity & accounts: sign-up with email verification, login/logout, password
    set/reset. Projects all users into the :user/users read-model."

   :commands
   {:user/sign-up
    {:description "Registers a new account (unique email, matching passwords) and requests email verification."
     :schema [:map [:email-address ::s/email] [:password ::s/password] [:confirm-password ::s/password]]
     :reads #{:user/users}
     :produces #{:user/signed-up :user/email-verification-requested}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "SignUp"}]}

    :user/login
    {:description "Authenticates an active, email-verified account by email + password and issues a session token."
     :schema [:map
              [:email-address [:string {:min 1 :error/message "Email is required"}]]
              [:password [:string {:min 1 :error/message "Password is required"}]]]
     :reads #{:user/users}
     :produces #{:user/logged-in}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "Login"}]}

    :user/logout
    {:description "Ends the session by bumping the account's token-version, invalidating outstanding tokens."
     :schema [:map]
     :reads #{:user/users}
     :produces #{:user/logged-out}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "Logout"}]}

    :user/set-password
    {:description "Sets a new password for the authenticated user and invalidates outstanding sessions."
     :schema [:map [:password ::s/password]]
     :reads #{:user/users}
     :produces #{:user/password-set}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "SetPassword"}]}

    :user/verify-email
    {:description "Verifies an account's email using the pending verification token."
     :schema [:map [:verification-token :string]]
     :reads #{:user/users}
     :produces #{:user/email-verified}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "VerifyEmail"}]}

    :user/request-email-verification
    {:description "Requests a fresh verification link without revealing whether an unverified account exists."
     :schema [:map [:email-address ::s/email]]
     :reads #{:user/users}
     :produces #{:user/email-verification-requested}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "RequestEmailVerification"}]}

    :user/request-password-reset
    {:description "Requests a password reset; emits a reset-requested event when the email matches an account."
     :schema [:map [:email-address ::s/email]]
     :reads #{:user/users}
     :produces #{:user/password-reset-requested}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "RequestPasswordReset"}]}

    :user/reset-password
    {:description "Resets the password using a valid, unused reset token and invalidates outstanding sessions."
     :schema [:map [:reset-token :string] [:password ::s/password]]
     :reads #{:user/users}
     :produces #{:user/password-reset}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :rule :name "ResetPassword"}]}}

   :events
   {:user/signed-up
    {:description "An account was registered."
     :schema [:map [:user-id :uuid] [:email-address :string] [:password :string]]}
    :user/logged-in
    {:description "An account authenticated successfully."
     :schema [:map [:user-id :uuid] [:email-address :string]]}
    :user/logged-out
    {:description "An account's session was ended (token-version bumped)."
     :schema [:map [:user-id :uuid] [:token-version nat-int?]]}
    :user/password-set
    {:description "An account's password was set and its session token version advanced."
     :schema [:map
              [:user-id :uuid]
              [:password :string]
              [:token-version {:optional true} nat-int?]]}
    :user/email-verification-requested
    {:description "A verification email was requested for an account."
     :schema [:map [:user-id :uuid] [:email-address :string] [:verification-token :string]]}
    :user/email-verified
    {:description "An account's email was verified."
     :schema [:map [:user-id :uuid] [:email-address :string]]}
    :user/password-reset-requested
    {:description "A password reset was requested for an account."
     :schema [:map [:user-id :uuid] [:email-address :string] [:reset-token :string]]}
    :user/password-reset
    {:description "An account's password was reset and its session token version advanced."
     :schema [:map
              [:user-id :uuid]
              [:password :string]
              [:token-version {:optional true} nat-int?]]}}

   :read-models
   {:user/users
    {:description "All user accounts, projected from the user lifecycle events."
     :consumes #{:user/signed-up
                 :user/logged-out
                 :user/password-set
                 :user/email-verification-requested
                 :user/email-verified
                 :user/password-reset-requested
                 :user/password-reset}
     :version 2}}

   :queries
   {:user/session
    {:description "Returns the authenticated account's safe session profile."
     :schema [:map]
     :reads #{:user/users}}}

   :todo-processors
   {:user/email-verification-email
    {:description "Sends the verification email when an account requests verification."
     :subscribes #{:user/email-verification-requested}
     :produces #{}}
    :user/password-reset-email
    {:description "Sends the reset email when a password reset is requested."
     :subscribes #{:user/password-reset-requested}
     :produces #{}}}

   :screens
   {:user/sign-in
    {:description "Sign-in page: lets a returning user authenticate."
     :queries #{}
     :commands #{:user/login :user/request-email-verification}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :surface :name "SignIn"}]}
    :user/sign-up
    {:description "Create-account page: registers a new user."
     :queries #{}
     :commands #{:user/sign-up :user/request-email-verification}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :surface :name "SignUp"}]}
    :user/forgot-password
    {:description "Forgot-password page: requests a reset link."
     :queries #{}
     :commands #{:user/request-password-reset}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :surface :name "ForgotPassword"}]}
    :user/verify-email
    {:description "Email-verification landing: confirms an account's email."
     :queries #{}
     :commands #{:user/verify-email}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :surface :name "VerifyEmail"}]}
    :user/reset-password
    {:description "Reset-password page: sets a new password from a reset link."
     :queries #{}
     :commands #{:user/reset-password}
     :grain/allium [{:spec "components/user-service/user-service.allium" :kind :surface :name "ResetPassword"}]}}})
