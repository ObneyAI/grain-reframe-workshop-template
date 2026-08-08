(ns app.router.runtime
  (:require [clojure.string :as string]
            [pushy.core :as pushy]
            [reitit.frontend :as reitit]))

(def routes
  [["/" {:name :home :page :home :auth :required}]
   ["/examples/questionnaire"
    {:name :questionnaire-example :page :questionnaire-example :auth :required}]
   ["/examples/routes"
    {:name :query-route-example :page :query-route-example :auth :required}]
   ["/auth/sign-in" {:name :sign-in :page :sign-in :auth :anonymous}]
   ["/auth/sign-up" {:name :sign-up :page :sign-up :auth :anonymous}]
   ["/auth/forgot-password" {:name :forgot-password :page :forgot-password :auth :public}]
   ["/auth/verify-email" {:name :verify-email :page :verify-email :auth :public}]
   ["/auth/reset-password" {:name :reset-password :page :reset-password :auth :public}]
   ["/forbidden" {:name :forbidden :page :forbidden :auth :required}]])

(defonce router (reitit/router routes))

(defn- query-params
  [query-string]
  (when-not (string/blank? query-string)
    (-> (js/Object.fromEntries (.entries (js/URLSearchParams. query-string)))
        (js->clj :keywordize-keys true))))

(defn match-token
  "Resolve one browser token into a route match, including query parameters.
   Unknown paths become an explicit not-found match without adding a wildcard
   that conflicts with every concrete Reitit route."
  [token]
  (let [[path query-string] (string/split (or token "/") #"\?" 2)]
    (assoc (or (reitit/match-by-path router path)
               {:path path
                :data {:name :not-found :page :not-found :auth :public}})
           :query-params (query-params query-string)
           :token token)))

(defonce current-match (atom nil))
(defonce ^:private !history (atom nil))

(defn- history!
  []
  (or @!history
      (reset! !history
              (pushy/pushy #(reset! current-match %)
                           match-token))))

(defn start! []
  (pushy/start! (history!)))

(defn navigate-path!
  [path]
  (pushy/set-token! (history!) path))

(defn navigate!
  ([route-name] (navigate! route-name {}))
  ([route-name query-params]
   (when-let [path (:path (reitit/match-by-name router route-name))]
     (let [query-string (when (seq query-params)
                          (->> query-params
                               (sort-by (comp name key))
                               (map (fn [[key value]]
                                      (str (name key) "=" (js/encodeURIComponent value))))
                               (string/join "&")
                               (str "?")))]
       (navigate-path! (str path query-string))))))

(defn safe-return-path
  "Accept only a local path that resolves to a protected application route.
   This prevents external/open redirects and redirect loops back into auth."
  [candidate]
  (when (and (string? candidate)
             (string/starts-with? candidate "/")
             (not (string/starts-with? candidate "//"))
             (not (string/includes? candidate "\\")))
    (let [match (match-token candidate)]
      (when (= :required (get-in match [:data :auth]))
        candidate))))

(defn route-decision
  "Return a rendering or navigation outcome for a route and session status.
   Protected pages never render while session state is unresolved."
  [match auth-status]
  (let [auth-policy (get-in match [:data :auth] :public)
        return-path (safe-return-path (get-in match [:query-params :return-to]))]
    (case auth-policy
      :required
      (case auth-status
        :checking {:action :hold}
        :authenticated {:action :render}
        {:action :redirect
         :route :sign-in
         :query-params {:return-to (or (:token match) "/")}})

      :anonymous
      (case auth-status
        :checking {:action :hold}
        :authenticated {:action :redirect :path (or return-path "/")}
        {:action :render})

      {:action :render})))
