(ns app.auth.interface
  (:require [cognitect.anomalies :as anom]
            [io.pedestal.interceptor :as interceptor]))

;; Single source for the starter default session-cookie name. The runtime always
;; passes an explicit name from app.config.interface; this default only backs
;; direct/test callers. scripts/init_project.bb rewrites this one literal per clone.
(def ^:private default-session-cookie-name "grain-reframe-workshop-template-session")

(defn authenticated?
  [ctx]
  (some? (:auth-claims ctx)))

(defn auth-user-id
  [ctx]
  (get-in ctx [:auth-claims :user-id]))

(defn user-tag
  [user-id]
  [:user user-id])

(defn- parse-uuid-value
  [value]
  (cond
    (uuid? value) value
    (string? value) (try
                      (java.util.UUID/fromString value)
                      (catch Exception _ value))
    :else value))

(defn normalize-claims
  [claims]
  (when (map? claims)
    (cond-> claims
      (contains? claims :user-id) (update :user-id parse-uuid-value)
      (contains? claims :tenant-id) (update :tenant-id parse-uuid-value))))

(defn extract-auth-cookie-interceptor
  [{:keys [verify-token cookie-name]
    :or {cookie-name default-session-cookie-name}}]
  (interceptor/interceptor
   {:name ::extract-auth-cookie
    :enter
    (fn [ctx]
      (let [token (get-in ctx [:request :cookies cookie-name :value])
            claims (when (and token verify-token)
                     (try
                       (normalize-claims (verify-token token))
                       (catch Exception _ nil)))]
        (cond-> ctx
          claims (assoc-in [:grain/additional-context :auth-claims] claims))))}))

(def current-user-context-interceptor
  (interceptor/interceptor
   {:name ::current-user-context
    :enter
    (fn [ctx]
      (let [user-id (get-in ctx [:grain/additional-context :auth-claims :user-id])]
        (cond-> ctx
          user-id (assoc-in [:grain/additional-context :current-user/id] user-id))))}))

(defn anomaly?
  [result]
  (contains? result ::anom/category))

(defn set-cookie
  [ctx token {:keys [cookie-name secure?]
              :or {cookie-name default-session-cookie-name
                   secure? false}}]
  (assoc-in ctx [:response :cookies cookie-name]
            {:value token
             :http-only true
             :secure secure?
             :same-site :lax
             :path "/"}))

(defn clear-cookie
  [ctx {:keys [cookie-name secure?]
        :or {cookie-name default-session-cookie-name
             secure? false}}]
  (assoc-in ctx [:response :cookies cookie-name]
            {:value ""
             :http-only true
             :secure secure?
             :same-site :lax
             :path "/"
             :max-age 0}))

(defn auth-cookie-interceptor
  [cookie-options]
  (interceptor/interceptor
   {:name ::auth-cookie
    :leave
    (fn [ctx]
      (let [command (:grain/command ctx)
            result (:grain/command-result ctx)]
        (cond
          (or (nil? command) (anomaly? result))
          ctx

          (and (= :user/login (:command/name command))
               (:auth/token result))
          (set-cookie ctx (:auth/token result) cookie-options)

          (contains? #{:user/logout :user/set-password :user/reset-password}
                     (:command/name command))
          (clear-cookie ctx cookie-options)

          :else ctx)))}))
