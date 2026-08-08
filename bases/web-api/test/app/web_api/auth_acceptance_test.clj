(ns app.web-api.auth-acceptance-test
  (:require [app.config.interface :as config]
            [app.email.interface :as email]
            [app.web-api.core :as core]
            [clojure.test :refer [deftest is testing]]
            [cognitect.transit :as transit])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)
           (java.net CookieManager CookiePolicy ServerSocket URI)
           (java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
                          HttpResponse$BodyHandlers)
           (java.nio.file Files Path)
           (java.nio.file.attribute FileAttribute)
           (java.util Comparator)))

(defn- available-port
  []
  (with-open [socket (ServerSocket. 0)]
    (.getLocalPort socket)))

(defn- temporary-directory
  []
  (Files/createTempDirectory "grain-auth-acceptance-"
                             (make-array FileAttribute 0)))

(defn- delete-tree!
  [^Path root]
  (when (Files/exists root (make-array java.nio.file.LinkOption 0))
    (with-open [paths (Files/walk root (make-array java.nio.file.FileVisitOption 0))]
      (.forEach (.sorted paths (Comparator/reverseOrder))
                (reify java.util.function.Consumer
                  (accept [_ path] (Files/deleteIfExists path)))))))

(defn- transit-bytes
  [value]
  (let [output (ByteArrayOutputStream.)]
    (transit/write (transit/writer output :json) value)
    (.toByteArray output)))

(defn- read-transit
  [bytes]
  (transit/read (transit/reader (ByteArrayInputStream. bytes) :json)))

(defn- response-body
  "Decode Transit while preserving a useful diagnostic if a test ever reaches
   a different local listener or receives a non-Transit server response."
  [response]
  (let [^bytes bytes (.body response)]
    (try
      (read-transit bytes)
      (catch Exception error
        {:test/unparseable-response true
         :test/content-type (str (.orElse
                                  (.firstValue (.headers response) "content-type")
                                  "missing"))
         :test/body-preview (subs (String. bytes java.nio.charset.StandardCharsets/UTF_8)
                                  0
                                  (min 240 (alength bytes)))
         :test/decode-error (.getMessage error)}))))

(defn- post
  ([client base-url path body]
   (post client base-url path body nil))
  ([^HttpClient client base-url path body cookie]
   (let [builder (doto (HttpRequest/newBuilder (URI/create (str base-url path)))
                   (.header "Content-Type" "application/transit+json")
                   (.header "Accept" "application/transit+json"))
         builder (if cookie (.header builder "Cookie" cookie) builder)
         request (.build (.POST builder
                                (HttpRequest$BodyPublishers/ofByteArray
                                 (transit-bytes body))))
         response (.send client request (HttpResponse$BodyHandlers/ofByteArray))]
     {:status (.statusCode response)
      :headers (.headers response)
      :body (response-body response)})))

(defn- command
  ([client base-url payload]
   (post client base-url "/command" {:command payload}))
  ([client base-url payload cookie]
   (post client base-url "/command" {:command payload} cookie)))

(defn- query
  ([client base-url payload]
   (post client base-url "/query" {:query payload}))
  ([client base-url payload cookie]
   (post client base-url "/query" {:query payload} cookie)))

(defn- await-email
  [sent]
  (loop [attempt 0]
    (if-let [message (first @sent)]
      message
      (if (< attempt 100)
        (do (Thread/sleep 50) (recur (inc attempt)))
        (throw (ex-info "Verification email was not captured" {}))))))

(defn- acceptance-configuration
  [{:keys [app-name cookie-name email-client port storage tenant-id]}]
  (cond-> (config/load
           {"APP_ENV" "test"
            "APP_NAME" app-name
            "APP_HTTP_PORT" (str port)
            "APP_BASE_URL" (str "http://127.0.0.1:" port)
            "APP_JWT_SECRET" "acceptance-secret"
            "APP_TENANT_ID" (str tenant-id)
            "APP_EMAIL_FROM" "acceptance@example.test"
            "APP_AUTH_COOKIE_NAME" cookie-name
            "APP_STORAGE_DIR" (str storage)})
    email-client (assoc :email-client email-client)))

(deftest complete-authentication-lifecycle-over-http
  (let [storage (temporary-directory)
        port (available-port)
        base-url (str "http://127.0.0.1:" port)
        cookie-name "auth-acceptance-session"
        sent (atom [])
        cookie-manager (CookieManager. nil CookiePolicy/ACCEPT_ALL)
        client (-> (HttpClient/newBuilder)
                   (.cookieHandler cookie-manager)
                   (.build))
        configuration (acceptance-configuration
                       {:app-name "Auth Acceptance"
                        :cookie-name cookie-name
                        :email-client (email/logger-email sent)
                        :port port
                        :storage storage
                        :tenant-id #uuid "22222222-2222-4222-8222-222222222222"})
        system (core/start configuration)
        email-address (str "acceptance-" (random-uuid) "@example.test")
        password "StarterPass123"]
    (try
      (testing "sign-up persists an account and emits a captured verification message"
        (let [response (command client base-url
                                {:command/name :user/sign-up
                                 :email-address email-address
                                 :password password
                                 :confirm-password password})]
          (is (= 200 (:status response)))
          (is (= {:account-created true} (:body response)))))

      (let [verification-email (await-email sent)
            verification-token (second
                                (re-find #"verification-token=([^\"]+)"
                                         (:body-html verification-email)))]
        (testing "the test email adapter exposes a usable verification token"
          (is (= [email-address] (:to verification-email)))
          (is (string? verification-token)))

        (testing "email verification succeeds through the public command endpoint"
          (let [response (command client base-url
                                  {:command/name :user/verify-email
                                   :verification-token verification-token})]
            (is (= 200 (:status response)))
            (is (= {:email-verified true} (:body response))))))

      (let [login-response (command client base-url
                                    {:command/name :user/login
                                     :email-address email-address
                                     :password password})
            set-cookie (str (.orElse
                             (.firstValue (:headers login-response) "set-cookie")
                             ""))
            issued-cookie (first
                           (filter #(= cookie-name (.getName %))
                                   (.getCookies (.getCookieStore cookie-manager))))
            old-cookie-header (str cookie-name "=" (.getValue issued-cookie))]
        (testing "login issues the hardened configured session cookie"
          (is (= 200 (:status login-response)))
          (is (= {:authenticated true} (:body login-response)))
          (is (some? issued-cookie))
          (is (re-find #"(?i)HttpOnly" set-cookie))
          (is (re-find #"(?i)SameSite=Lax" set-cookie))
          (is (re-find #"(?i)Path=/" set-cookie)))

        (testing "the cookie authenticates the session query"
          (let [response (query client base-url {:query/name :user/session})]
            (is (= 200 (:status response)))
            (is (= email-address (:user/email-address (:body response))))
            (is (true? (:user/email-verified (:body response))))))

        (testing "logout clears the cookie and invalidates the issued token"
          (let [logout-response (command client base-url {:command/name :user/logout})
                anonymous-client (.build (HttpClient/newBuilder))
                stale-session (query anonymous-client base-url
                                     {:query/name :user/session}
                                     old-cookie-header)]
            (is (= 200 (:status logout-response)))
            (is (= {:authenticated false} (:body logout-response)))
            (is (= 403 (:status stale-session))))))
      (finally
        (core/stop system)
        (delete-tree! storage)))))

(deftest configured-cookie-names-isolate-localhost-apps
  (let [storage (temporary-directory)
        tenant-id #uuid "33333333-3333-4333-8333-333333333333"
        port-a (available-port)
        port-b (loop [candidate (available-port)]
                 (if (= candidate port-a)
                   (recur (available-port))
                   candidate))
        base-a (str "http://127.0.0.1:" port-a)
        base-b (str "http://127.0.0.1:" port-b)
        cookie-a "acceptance-app-a-session"
        cookie-b "acceptance-app-b-session"
        client (.build (HttpClient/newBuilder))
        email-address (str "cookie-isolation-" (random-uuid) "@example.test")
        password "StarterPass123"
        system-a (core/start
                  (acceptance-configuration
                   {:app-name "Acceptance App A"
                    :cookie-name cookie-a
                    :port port-a
                    :storage storage
                    :tenant-id tenant-id}))]
    (try
      (let [sign-up-response (command client base-a
                                      {:command/name :user/sign-up
                                       :email-address email-address
                                       :password password
                                       :confirm-password password})
            login-response (command client base-a
                                    {:command/name :user/login
                                     :email-address email-address
                                     :password password})
            set-cookie (str (.orElse
                             (.firstValue (:headers login-response) "set-cookie")
                             ""))
            token (second (re-find
                           (re-pattern (str "^" cookie-a "=([^;]+)"))
                           set-cookie))]
        (is (= 200 (:status sign-up-response)))
        (is (= 200 (:status login-response)))
        (is (string? token))
        (core/stop system-a)
        (let [system-b (core/start
                        (acceptance-configuration
                         {:app-name "Acceptance App B"
                          :cookie-name cookie-b
                          :port port-b
                          :storage storage
                          :tenant-id tenant-id}))]
          (try
            (testing "app B ignores app A's valid cookie on another localhost port"
              (is (= 403
                     (:status (query client base-b
                                     {:query/name :user/session}
                                     (str cookie-a "=" token))))))
            (testing "the same signed token is accepted when carried by app B's configured cookie"
              (let [response (query client base-b
                                    {:query/name :user/session}
                                    (str cookie-b "=" token))]
                (is (= 200 (:status response)))
                (is (= email-address (:user/email-address (:body response))))))
            (finally
              (core/stop system-b)))))
      (finally
        (try (core/stop system-a) (catch Exception _))
        (delete-tree! storage)))))
