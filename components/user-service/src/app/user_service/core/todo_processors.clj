(ns app.user-service.core.todo-processors
  (:require [ai.obney.grain.todo-processor-v2.interface :refer [defprocessor]]
            [app.email.interface :as email]))

(defprocessor :user email-verification-email
  {:topics #{:user/email-verification-requested}
   ;; Side-effect only (sends an email); issues no command.
   :grain.event-model/produces #{}}
  [{{:keys [email-address verification-token]} :event
    :keys [app-base email-client email-from]}]
  {:result/effect
   (fn []
     (email/send
      email-client
      {:from email-from
       :to [email-address]
       :subject "Verify your email"
       :body-html (format "<p>Verify your email: <a href=\"%s/auth/verify-email?verification-token=%s\">Verify email</a></p>"
                          app-base
                          verification-token)}))
   :result/checkpoint :after})

(defprocessor :user password-reset-email
  {:topics #{:user/password-reset-requested}
   ;; Side-effect only (sends an email); issues no command.
   :grain.event-model/produces #{}}
  [{{:keys [email-address reset-token]} :event
    :keys [app-base email-client email-from]}]
  {:result/effect
   (fn []
     (email/send
      email-client
      {:from email-from
       :to [email-address]
       :subject "Reset your password"
       :body-html (format "<p>Reset your password: <a href=\"%s/auth/reset-password?reset-token=%s\">Reset password</a></p>"
                          app-base
                          reset-token)}))
   :result/checkpoint :after})
