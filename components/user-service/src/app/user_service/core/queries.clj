(ns app.user-service.core.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [app.auth.interface :as auth]
            [app.user-service.core.read-models :as rm]
            [cognitect.anomalies :as anom]))

(defquery :user session
  {:authorized? auth/authenticated?
   :grain.event-model/reads #{:user/users}}
  [context]
  (if-let [user (rm/user context (auth/auth-user-id context))]
    {:query/result
     {:user/id (:user/id user)
      :user/email-address (:user/email-address user)
      :user/email-verified (:user/email-verified user)}}
    {::anom/category ::anom/not-found
     ::anom/message "User not found."}))
