(ns app.user-service.core.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [app.auth.interface :as auth]
            [app.jwt.interface :as jwt]
            [app.user-service.core.read-models :as rm]
            [buddy.hashers :as hashers]
            [cognitect.anomalies :as anom]))

(defn anomaly
  [category message]
  {::anom/category category ::anom/message message})

(defn field-anomaly
  [category message field field-message]
  (assoc (anomaly category message) :error/explain {field [field-message]}))

(defn conflict [message] (anomaly ::anom/conflict message))
(defn field-conflict [message field field-message]
  (field-anomaly ::anom/conflict message field field-message))
(defn forbidden [message] (anomaly ::anom/forbidden message))
(defn not-found [message] (anomaly ::anom/not-found message))

(defn invalid-credentials []
  (field-conflict "Invalid credentials."
                  :password
                  "Invalid credentials."))

(defn make-event
  [event]
  (->event event))

(defn make-session-token
  [{:keys [jwt-secret tenant-id]} user]
  (jwt/sign {:payload {:user-id (str (:user/id user))
                       :email (:user/email-address user)
                       :tenant-id (str tenant-id)
                       :token-version (or (:user/token-version user) 0)}
             :secret jwt-secret
             :expire-in [24 :hours]}))

(defn make-reset-token
  [{:keys [jwt-secret]} user]
  (jwt/sign {:payload {:user-id (str (:user/id user))
                       :email (:user/email-address user)}
             :secret jwt-secret
             :expire-in [30 :minutes]}))

(defn make-email-verification-token
  [{:keys [jwt-secret]} user]
  (jwt/sign {:payload {:user-id (str (:user/id user))
                       :email (:user/email-address user)
                       :token-id (str (random-uuid))}
             :secret jwt-secret
             :expire-in [24 :hours]}))

(defcommand :user sign-up
  {:authorized? (constantly true)
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/signed-up :user/email-verification-requested}}
  [{{:keys [email-address password confirm-password]} :command :as context}]
  (let [email-address (rm/normalize-email email-address)]
    (cond
      (contains? (rm/email-addresses context) email-address)
      (field-conflict "Email already registered."
                      :email-address
                      "An account already exists for this email.")

      (not= password confirm-password)
      (field-conflict "Passwords do not match."
                      :confirm-password
                      "Passwords do not match.")

      :else
      (let [user-id (random-uuid)]
        {:command-result/events
         (let [user {:user/id user-id
                     :user/email-address email-address}
               verification-token (make-email-verification-token context user)]
           [(make-event {:type :user/signed-up
                         :tags #{[:user user-id]}
                         :body {:user-id user-id
                                :email-address email-address
                                :password (hashers/derive password)}})
            (make-event {:type :user/email-verification-requested
                         :tags #{[:user user-id]}
                         :body {:user-id user-id
                                :email-address email-address
                                :verification-token verification-token}})])
         :command/result {:account-created true}}))))

(defcommand :user login
  {:authorized? (constantly true)
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/logged-in}}
  [{{:keys [email-address password]} :command :as context}]
  (let [{user-id :user/id
         stored-password :user/password
         active? :user/active
         email-verified? :user/email-verified
         :as user} (rm/user-by-email context email-address)]
    (cond
      (nil? user)
      (invalid-credentials)

      (not (:valid (hashers/verify password stored-password)))
      (invalid-credentials)

      (false? active?)
      (forbidden "Account is inactive.")

      (not (true? email-verified?))
      (forbidden "Verify your email before signing in.")

      :else
      {:command-result/events
       [(make-event {:type :user/logged-in
                     :tags #{[:user user-id]}
                     :body {:user-id user-id
                            :email-address (:user/email-address user)}})]
       :auth/token (make-session-token context user)
       :command/result {:authenticated true}})))

(defcommand :user logout
  {:authorized? auth/authenticated?
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/logged-out}}
  [context]
  (let [user-id (auth/auth-user-id context)
        current-version (rm/token-version context user-id)]
    (if (nil? current-version)
      (not-found "User not found.")
      {:command-result/events
       [(make-event {:type :user/logged-out
                     :tags #{[:user user-id]}
                     :body {:user-id user-id
                            :token-version (inc current-version)}})]
       :command/result {:authenticated false}})))

(defcommand :user set-password
  {:authorized? auth/authenticated?
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/password-set}}
  [{{:keys [password]} :command :as context}]
  (let [user-id (auth/auth-user-id context)
        current-version (rm/token-version context user-id)]
    (if (nil? current-version)
      (not-found "User not found.")
      {:command-result/events
       [(make-event {:type :user/password-set
                     :tags #{[:user user-id]}
                     :body {:user-id user-id
                            :password (hashers/derive password)
                            :token-version (inc current-version)}})]
       :command/result {:password-updated true}})))

(defcommand :user verify-email
  {:authorized? (constantly true)
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/email-verified}}
  [{{:keys [verification-token]} :command :keys [jwt-secret] :as context}]
  (try
    (let [payload (jwt/unsign {:token verification-token :secret jwt-secret})
          user-id (parse-uuid (:user-id payload))
          user (rm/user context user-id)
          pending-token (rm/pending-email-verification-token context user-id)]
      (cond
        (nil? user)
        (not-found "Email verification link is invalid.")

        (nil? pending-token)
        (conflict "Email is already verified.")

        (not= verification-token pending-token)
        (conflict "Email verification link is invalid.")

        :else
        {:command-result/events
         [(make-event {:type :user/email-verified
                       :tags #{[:user user-id]}
                       :body {:user-id user-id
                              :email-address (:user/email-address user)}})]
         :command/result {:email-verified true}}))
    (catch Exception _e
      (conflict "Email verification link is invalid."))))

(defcommand :user request-email-verification
  {:authorized? (constantly true)
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/email-verification-requested}}
  [{{:keys [email-address]} :command :as context}]
  (if-let [user (rm/user-by-email context email-address)]
    (if (true? (:user/email-verified user))
      {:command/result {:verification-requested true}}
      (let [user-id (:user/id user)
            verification-token (make-email-verification-token context user)]
        {:command-result/events
         [(make-event {:type :user/email-verification-requested
                       :tags #{[:user user-id]}
                       :body {:user-id user-id
                              :email-address (:user/email-address user)
                              :verification-token verification-token}})]
         :command/result {:verification-requested true}}))
    {:command/result {:verification-requested true}}))

(defcommand :user request-password-reset
  {:authorized? (constantly true)
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/password-reset-requested}}
  [{{:keys [email-address]} :command :as context}]
  (if-let [user (rm/user-by-email context email-address)]
    (let [reset-token (make-reset-token context user)
          user-id (:user/id user)]
      {:command-result/events
       [(make-event {:type :user/password-reset-requested
                     :tags #{[:user user-id]}
                     :body {:user-id user-id
                            :email-address (:user/email-address user)
                            :reset-token reset-token}})]
       :command/result {:reset-requested true}})
    {:command/result {:reset-requested true}}))

(defcommand :user reset-password
  {:authorized? (constantly true)
   :grain.event-model/reads #{:user/users}
   :grain.event-model/produces #{:user/password-reset}}
  [{{:keys [reset-token password]} :command :keys [jwt-secret] :as context}]
  (try
    (let [payload (jwt/unsign {:token reset-token :secret jwt-secret})
          user-id (parse-uuid (:user-id payload))
          pending-token (rm/pending-reset-token context user-id)
          current-version (rm/token-version context user-id)]
      (cond
        (nil? pending-token)
        (conflict "Password reset link is invalid.")

        (not= reset-token pending-token)
        (conflict "Password reset link has already been used.")

        :else
        {:command-result/events
         [(make-event {:type :user/password-reset
                       :tags #{[:user user-id]}
                       :body {:user-id user-id
                              :password (hashers/derive password)
                              :token-version (inc current-version)}})]
         :command/result {:password-reset true}}))
    (catch Exception _e
      (conflict "Password reset link is invalid."))))
