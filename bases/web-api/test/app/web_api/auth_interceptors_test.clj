(ns app.web-api.auth-interceptors-test
  (:require [app.auth.interface :as auth]
            [app.jwt.interface :as jwt]
            [app.user-service.interface :as user]
            [app.web-api.core :as core]
            [cognitect.transit :as transit]
            [clojure.test :refer [deftest is testing]]
            [integrant.core :as ig]))

(defn- decode-transit
  [value]
  (transit/read
   (transit/reader (java.io.ByteArrayInputStream. (.getBytes value)) :json)))

(deftest auth-cookie-lifecycle
  (let [cookie-name "my-app-session"
        leave (:leave (auth/auth-cookie-interceptor {:cookie-name cookie-name
                                                     :secure? true}))]
    (testing "a successful login sets a hardened HTTP-only cookie"
      (let [result (leave {:grain/command {:command/name :user/login}
                           :grain/command-result {:auth/token "signed-token"}})
            cookie (get-in result [:response :cookies cookie-name])]
        (is (= "signed-token" (:value cookie)))
        (is (true? (:http-only cookie)))
        (is (true? (:secure cookie)))
        (is (= :lax (:same-site cookie)))
        (is (= "/" (:path cookie)))))

    (testing "logout expires the cookie"
      (let [result (leave {:grain/command {:command/name :user/logout}
                           :grain/command-result {}})
            cookie (get-in result [:response :cookies cookie-name])]
        (is (= "" (:value cookie)))
        (is (= 0 (:max-age cookie)))))

    (testing "an anomalous command result does not mutate cookies"
      (let [context {:grain/command {:command/name :user/login}
                     :grain/command-result {:cognitect.anomalies/category
                                            :cognitect.anomalies/forbidden}}]
        (is (= context (leave context)))))))

(deftest auth-cookie-name-is-shared-by-extraction-setting-and-clearing
  (let [cookie-name "second-app-session"
        token "second-app-token"
        enter (:enter (auth/extract-auth-cookie-interceptor
                       {:cookie-name cookie-name
                        :verify-token #(when (= token %)
                                         {:user-id (str (random-uuid))})}))
        leave (:leave (auth/auth-cookie-interceptor {:cookie-name cookie-name}))]
    (testing "the configured cookie is extracted"
      (is (some? (get-in (enter {:request {:cookies {cookie-name {:value token}}}})
                         [:grain/additional-context :auth-claims :user-id]))))

    (testing "the old starter cookie name is ignored"
      (is (nil? (get-in (enter {:request {:cookies {"auth-token" {:value token}}}})
                        [:grain/additional-context :auth-claims]))))

    (testing "setting and clearing use that same configured name"
      (is (= token
             (get-in (leave {:grain/command {:command/name :user/login}
                             :grain/command-result {:auth/token token}})
                     [:response :cookies cookie-name :value])))
      (is (= 0
             (get-in (leave {:grain/command {:command/name :user/logout}
                             :grain/command-result {}})
                     [:response :cookies cookie-name :max-age]))))))

(deftest structured-command-anomalies-preserve-field-explanations
  (let [leave (:leave (core/structured-anomaly-interceptor))
        context {:response {:status 409
                            :headers {"Content-Type" "application/transit+json"}
                            :body "generic response"}
                 :grain/command-result
                 {:cognitect.anomalies/category :cognitect.anomalies/conflict
                  :cognitect.anomalies/message "Email already registered."
                  :error/explain {:email-address ["An account already exists."]}}}
        result (leave context)]
    (is (= 409 (get-in result [:response :status])))
    (is (= {:message "Email already registered."
            :error/explain {:email-address ["An account already exists."]}}
           (decode-transit (get-in result [:response :body]))))))

(deftest claims-are-normalized-at-the-auth-seam
  (let [user-id (random-uuid)
        tenant-id (random-uuid)
        claims (auth/normalize-claims {:user-id (str user-id)
                                       :tenant-id (str tenant-id)})]
    (is (= user-id (:user-id claims)))
    (is (= tenant-id (:tenant-id claims)))))

(deftest session-token-must-belong-to-the-configured-tenant
  (let [configured-tenant (random-uuid)
        other-tenant (random-uuid)
        user-id (random-uuid)
        verifier (ig/init-key ::core/auth-token-verifier
                              {:context {:tenant-id configured-tenant}})]
    (testing "a token for the active tenant is accepted"
      (with-redefs [jwt/unsign (fn [_]
                                {:user-id (str user-id)
                                 :tenant-id (str configured-tenant)
                                 :token-version 2})
                     user/token-version (fn [_ _] 2)]
        (is (= configured-tenant (:tenant-id (verifier "token"))))))

    (testing "a correctly signed token for another tenant is rejected"
      (with-redefs [jwt/unsign (fn [_]
                                {:user-id (str user-id)
                                 :tenant-id (str other-tenant)
                                 :token-version 2})
                     user/token-version (fn [_ _] 2)]
        (is (nil? (verifier "other-tenant-token")))))))

(deftest query-driven-client-routes-have-direct-load-support
  (is (contains? (set core/spa-paths) "/examples/routes")))

(deftest direct-load-not-found-keeps-an-honest-status-and-spa-document
  (let [response (core/spa-not-found {})]
    (is (= 404 (:status response)))
    (is (= "text/html; charset=utf-8" (get-in response [:headers "Content-Type"])))
    (is (re-find #"<div id=\"root\"></div>" (:body response)))))

(deftest spa-document-exposes-runtime-date-settings-without-inline-script
  (let [response (core/spa-index {:locale "fr-CA" :time-zone "America/Toronto"} {})]
    (is (re-find #"<html lang=\"fr-CA\"" (:body response)))
    (is (re-find #"<meta name=\"app-locale\" content=\"fr-CA\">" (:body response)))
    (is (re-find #"<meta name=\"app-time-zone\" content=\"America/Toronto\">"
                 (:body response)))
    (is (not (re-find #"<script[^>]*>[^<]+</script>" (:body response))))))

(deftest not-found-interceptor-only-fills-an-unhandled-response
  (let [leave (:leave (core/spa-not-found-interceptor))]
    (testing "an existing backend response is never replaced"
      (let [context {:request {:request-method :get}
                     :response {:status 200 :body "OK"}}]
        (is (= context (leave context)))))

    (testing "an unmatched GET receives the SPA document with HTTP 404"
      (let [response (:response (leave {:request {:request-method :get}}))]
        (is (= 404 (:status response)))
        (is (= "text/html; charset=utf-8"
               (get-in response [:headers "Content-Type"])))
        (is (re-find #"<div id=\"root\"></div>" (:body response)))))

    (testing "an unmatched non-GET receives a plain 404"
      (let [response (:response (leave {:request {:request-method :post}}))]
        (is (= 404 (:status response)))
        (is (= "text/plain; charset=utf-8"
               (get-in response [:headers "Content-Type"])))
        (is (= "Not Found" (:body response)))))))

(deftest request-traces-are-redacted-before-publication
  (let [request-key :ai.obney.grain.query-request-handler.core/request
        event {:mulog/event-name :query/trace
               :command {:command/name :user/sign-up
                         :password "password-secret"
                         :confirm-password "password-secret"}
               :event {:verification-token "verification-secret"}
               :email/body-html "html-with-secret-token"
               request-key {:request-method :post
                            :uri "/api/query"
                            :headers {"cookie" "session=secret"
                                      "authorization" "Bearer secret"}
                            :cookies {"session" {:value "secret"}}
                            :transit-params {:command {:password "secret"}}}}
        redacted (core/redact-log-event event)]
    (is (= :query/trace (:mulog/event-name redacted)))
    (is (= {:request-method :post :uri "/api/query"}
           (get redacted request-key)))
    (is (= :user/sign-up (get-in redacted [:command :command/name])))
    (is (= "[REDACTED]" (get-in redacted [:command :password])))
    (is (= "[REDACTED]" (get-in redacted [:event :verification-token])))
    (is (= "[REDACTED]" (:email/body-html redacted)))
    (is (not (re-find #"secret" (pr-str redacted))))))
