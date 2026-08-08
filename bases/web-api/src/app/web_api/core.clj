(ns app.web-api.core
  (:require [ai.obney.grain.code-agent-tools.interface :as code-agent-tools]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.command-request-handler-v2.interface :as crh]
            ;; The event-model boot-guard: verify-or-throw! mandates that every
            ;; registered defeventmodel strictly reconciles against the live runtime.
            [ai.obney.grain.event-model-validator.interface :as emv]
            [ai.obney.grain.event-store-v3.interface :as es]
            ;; Loading this namespace registers the :sqlite event-store backend.
            [ai.obney.grain.event-store-sqlite-v3.interface]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.query-processor.interface :as query-processor]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.webserver.interface :as ws]
            [app.auth.interface :as auth]
            [app.config.interface :as config]
            [app.email.interface :as email]
            [app.jwt.interface :as jwt]
            ;; Loading the user-service interface registers its commands/queries/read-models.
            [app.user-service.interface :as user]
            [cognitect.anomalies :as anom]
            [cognitect.transit :as transit]
            [clojure.java.io :as io]
            [clojure.set :as set]
            [clojure.string :as string]
            [com.brunobonacci.mulog :as u]
            [integrant.core :as ig]
            [io.pedestal.http :as http]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.interceptor :as interceptor]))

(declare spa-not-found-interceptor)

(def default-browser-settings
  {:locale "en-US"
   :time-zone "UTC"})

(defn- transit-body
  [value]
  (let [out (java.io.ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) value)
    (.toString out)))

(defn structured-anomaly-interceptor
  "Preserve application field explanations that Grain's generic command HTTP
   adapter omits from conflict responses. This is the stable application
   transport seam consumed by the frontend form interface."
  []
  (interceptor/interceptor
   {:name ::structured-anomaly
    :leave
    (fn [context]
      (let [result (:grain/command-result context)
            explain (:error/explain result)]
        (if (and (::anom/category result) (map? explain))
          (-> context
              (assoc-in [:response :headers "Content-Type"] "application/transit+json")
              (assoc-in [:response :body]
                        (transit-body {:message (::anom/message result)
                                       :error/explain explain})))
          context)))}))

(defn- secure-headers
  [configuration]
  (let [production? (config/production? configuration)]
    {:content-security-policy-settings
     {:default-src "'self'"
      :script-src (if production? "'self'" "'self' 'unsafe-eval'")
      :connect-src (if production? "'self'" "'self' ws:")
      :style-src "'self' 'unsafe-inline'"
      :font-src "'self' data:"
      :img-src "'self' data:"}}))

(defn system-map
  "Build the Integrant system from validated application configuration."
  [{:keys [app-base-url auth-cookie-name cookie-secure? email-client email-from http-port
           jwt-secret locale storage-dir tenant-id time-zone]
    :as configuration}]
  {::logger {}

   ::event-store {:event-pubsub (ig/ref ::event-pubsub)
                  :conn {:type :sqlite
                         :database-file (str storage-dir "/events.db")}}

   ::event-pubsub {:type :core-async
                   :topic-fn :event/type}

   ::cache {:storage-dir (str storage-dir "/cache")}

   ::auth-token-verifier {:context (ig/ref ::context)}

   ::context {:event-store (ig/ref ::event-store)
              :cache (ig/ref ::cache)
              :tenant-id tenant-id
              :event-pubsub (ig/ref ::event-pubsub)
              :jwt-secret jwt-secret
              :locale locale
              :time-zone time-zone
              :app-base app-base-url
              :email-from email-from
              :email-client (or email-client (email/logger-email))}

   ::processors {:event-store (ig/ref ::event-store)
                 :cache (ig/ref ::cache)
                 :tenant-id tenant-id
                 :context (ig/ref ::context)}

   ::routes {:context (ig/ref ::context)
             :browser-settings {:locale locale :time-zone time-zone}}

   ::webserver {::http/routes (ig/ref ::routes)
                ::auth-token-verifier (ig/ref ::auth-token-verifier)
                ::auth-cookie-name auth-cookie-name
                ::cookie-secure? cookie-secure?
                ::http/port http-port
                ::http/join? false
                ::http/resource-path "public"
                ::http/not-found-interceptor
                (spa-not-found-interceptor {:locale locale :time-zone time-zone})
                ::http/secure-headers (secure-headers configuration)}})

(defn- html-attribute
  [value]
  (-> (str value)
      (string/replace "&" "&amp;")
      (string/replace "\"" "&quot;")
      (string/replace "<" "&lt;")
      (string/replace ">" "&gt;")))

(defn spa-index
  "Serve the compiled Re-frame application for client-side routes. Static assets
   are handled first by Pedestal's resource interceptor."
  ([request]
   (spa-index default-browser-settings request))
  ([{:keys [locale time-zone]} _request]
   (let [document (slurp (io/resource "public/index.html"))
         runtime-meta (str "    <meta name=\"app-locale\" content=\""
                           (html-attribute locale) "\">\n"
                           "    <meta name=\"app-time-zone\" content=\""
                           (html-attribute time-zone) "\">\n")]
     {:status 200
      :headers {"Content-Type" "text/html; charset=utf-8"}
      :body (-> document
                (string/replace "<html lang=\"en\""
                                (str "<html lang=\"" (html-attribute locale) "\""))
                (string/replace "    <title>" (str runtime-meta "    <title>")))})))

(defn spa-not-found
  "Return the SPA document with an honest HTTP 404 so the client can render its
   not-found page on a direct browser load. Concrete backend routes remain more
   specific than this GET-only fallback."
  ([request]
   (spa-not-found default-browser-settings request))
  ([browser-settings request]
   (assoc (spa-index browser-settings request) :status 404)))

(defn spa-not-found-interceptor
  ([] (spa-not-found-interceptor default-browser-settings))
  ([browser-settings]
   (interceptor/interceptor
    {:name ::spa-not-found
     :leave (fn [context]
              (if (http/response? (:response context))
                context
                (assoc context :response
                       (if (= :get (get-in context [:request :request-method]))
                         (spa-not-found browser-settings (:request context))
                         {:status 404
                          :headers {"Content-Type" "text/plain; charset=utf-8"}
                          :body "Not Found"}))))})))

(def sensitive-log-field-names
  #{"auth-token" "authorization" "body-html" "confirm-password" "cookie"
    "cookies" "headers" "jwt-secret" "password" "reset-token" "secret"
    "token" "verification-token"})

(defn- log-field-name
  [key]
  (cond
    (keyword? key) (name key)
    (string? key) (string/lower-case key)
    :else nil))

(declare redact-log-value)

(defn- redact-log-map
  [value]
  (reduce-kv
   (fn [redacted key field-value]
     (let [field-name (log-field-name key)]
       (assoc redacted key
              (cond
                (= "request" field-name)
                (select-keys field-value [:request-method :uri :path-info])

                (contains? sensitive-log-field-names field-name)
                "[REDACTED]"

                :else
                (redact-log-value field-value)))))
   (empty value)
   value))

(defn redact-log-value
  [value]
  (cond
    (map? value) (redact-log-map value)
    (vector? value) (mapv redact-log-value value)
    (set? value) (set (map redact-log-value value))
    (sequential? value) (doall (map redact-log-value value))
    :else value))

(defn redact-log-event
  "Remove request internals and recursively replace credential-bearing fields
   before a μ/log event crosses the publisher adapter seam."
  [event]
  (redact-log-value event))

(defn redact-log-events
  [events]
  (map redact-log-event events))

(def spa-paths
  "Browser routes that must resolve to the SPA on a direct load. Keep this in
   sync with app.router.runtime/routes. Explicit routes avoid a GET wildcard
   swallowing health checks or future webhook endpoints."
  ["/"
   "/auth/sign-in"
   "/auth/sign-up"
   "/auth/forgot-password"
   "/auth/verify-email"
   "/auth/reset-password"
   "/forbidden"
   "/examples/questionnaire"
   "/examples/routes"])

(defn spa-routes
  ([] (spa-routes default-browser-settings))
  ([browser-settings]
   (->> spa-paths
        (map-indexed
         (fn [index path]
           [path :get [(partial spa-index browser-settings)]
            :route-name (keyword "app.web-api.core" (str "spa-" index))]))
        (into #{}))))

(defmethod ig/init-key ::logger [_ _]
  (let [console-pub-stop-fn
        (u/start-publisher! {:type :console
                             :pretty? true
                             :transform redact-log-events})]
    (fn []
      (console-pub-stop-fn))))

(defmethod ig/halt-key! ::logger [_ stop-fn]
  (stop-fn))

(defmethod ig/init-key ::event-store [_ config]
  (es/start config))

(defmethod ig/halt-key! ::event-store [_ event-store]
  (es/stop event-store))

(defmethod ig/init-key ::event-pubsub [_ config]
  (ps/start config))

(defmethod ig/halt-key! ::event-pubsub [_ event-pubsub]
  (ps/stop event-pubsub))

(defmethod ig/init-key ::cache [_ {:keys [storage-dir]}]
  (kv/start
   (lmdb/->KV-Store-LMDB {:storage-dir storage-dir
                          :db-name "read-model-cache"})))

(defmethod ig/halt-key! ::cache [_ cache]
  (kv/stop cache))

(defmethod ig/init-key ::auth-token-verifier [_ {:keys [context]}]
  (fn [token]
    (try
      (let [claims (auth/normalize-claims
                    (jwt/unsign {:token token :secret (:jwt-secret context)}))
            user-id (:user-id claims)
            tenant-id (:tenant-id claims)
            token-version (get claims :token-version 0)]
        (when (and (= (:tenant-id context) tenant-id)
                   (= token-version (user/token-version context user-id)))
          claims))
      (catch Exception _ nil))))

(defmethod ig/init-key ::context [_ context]
  (assoc context
         :command-registry (cp/global-command-registry)
         :query-registry (query-processor/global-query-registry)))

(defmethod ig/init-key ::processors [_ {:keys [event-store tenant-id context]}]
  (tp/start-tenant-poller
   {:event-store event-store
    :tenant-ids #{tenant-id}
    :context context
    :poll-interval-ms 250}))

(defmethod ig/halt-key! ::processors [_ poller]
  (tp/stop-tenant-poller poller))

(defmethod ig/init-key ::routes [_ {:keys [browser-settings context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   (spa-routes browser-settings)
   #{["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]
     ["/favicon.ico" :get [(fn [_] {:status 204 :body ""})] :route-name ::favicon]}))

(defmethod ig/init-key ::webserver
  [_ {::keys [auth-cookie-name auth-token-verifier cookie-secure?] :as system-config}]
  (ws/start
   (-> (dissoc system-config ::auth-cookie-name ::auth-token-verifier ::cookie-secure?)
       http/default-interceptors
       (update ::http/interceptors
               conj
               middlewares/cookies
               (auth/extract-auth-cookie-interceptor
                {:verify-token auth-token-verifier
                 :cookie-name auth-cookie-name})
               auth/current-user-context-interceptor
               (structured-anomaly-interceptor)
               (auth/auth-cookie-interceptor {:cookie-name auth-cookie-name
                                              :secure? cookie-secure?})))))

(defmethod ig/halt-key! ::webserver [_ webserver]
  (ws/stop webserver))

(defn start
  ([] (start (config/load)))
  ([configuration]
   (let [app (ig/init (system-map configuration))]
    (u/set-global-context!
     {:app-name (:app-name configuration)
      :env (name (:environment configuration))})
    (code-agent-tools/install!
     {:system app
      :context (::context app)
      :mode :dev})
    ;; Production boot guard checks Grain topology against the live runtime.
    ;; Allium syntax and cross-spec trace links are a development/CI concern;
    ;; run (code-agent-tools/validate-spec-composition model) in the quality gate.
    ;; While authoring a not-yet-complete model, set APP_SKIP_EVENT_MODEL_GUARD=true
    ;; to boot anyway and iterate with (validate-event-model model) in the REPL.
    (when-not (:skip-event-model-guard? configuration)
      (emv/verify-or-throw!))
    app)))

(defn stop
  [app]
  (ig/halt! app))

;; --- Held lifecycle (so the builder can restart in place over nREPL) -------
;; New components' HTTP routes only register at boot, so after adding/renaming a
;; route or component, call (restart!) to make it live, then verify in the browser.
(defonce ^:private !system (atom nil))

(defn start!
  "Start (idempotently) and hold the running system."
  []
  (or @!system (reset! !system (start))))

(defn restart!
  "Halt + re-init on the same JVM so newly-added routes/components register.
   The nREPL server is separate, so it survives."
  []
  (when @!system (ig/halt! @!system))
  (reset! !system (start))
  :restarted)

(defn reload-and-restart!
  "Like restart!, but FIRST reloads this namespace so edits to `system`, the
   `ig/init-key`/`halt-key!` defmethods, or `start` itself go live. A bare
   (restart!) re-inits the already-loaded `system` def + methods, so a change to
   core.clj (e.g. adding an Integrant key) needs this. Uses :reload (NOT
   :reload-all — that redefines library protocols and poisons the image); the
   held `!system` atom survives because it is a defonce. The nREPL server is
   separate, so it survives."
  []
  (require 'app.web-api.core :reload)
  ((resolve 'app.web-api.core/restart!))
  :reloaded-and-restarted)

(defn stop!
  []
  (when @!system (ig/halt! @!system))
  (reset! !system nil))

(defn load-component!
  "Safely load a NEW component into the running app and register its routes — WITHOUT
   corrupting the live image. Pass the component dir name, e.g. (load-component! \"staff\")
   for components/staff (namespace app.staff.interface). Also add it to deps.edn :dev so
   JVM restarts include it.

   This adds the component's path then does a PLAIN require (it never reloads already-loaded
   library namespaces), then restarts the app to register routes. Do NOT use :reload-all to
   load components — it redefines library protocols and breaks the running system."
  [component-name]
  (require 'clojure.repl.deps)
  ((resolve 'clojure.repl.deps/add-libs)
   {(symbol "poly" component-name) {:local/root (str "components/" component-name)}})
  (require (symbol (str "app." component-name ".interface")))
  (restart!)
  :loaded)

(comment
  (start!)
  (restart!)
  (reload-and-restart!)
  (stop!)
  )
