(ns app.config.interface-test
  (:require [app.config.interface :as config]
            [clojure.test :refer [deftest is testing]]))

(deftest development-configuration-has-safe-local-defaults
  (let [configuration (config/load {})]
    (is (= :development (:environment configuration)))
    (is (= "Grain Re-frame Workshop Template" (:app-name configuration)))
    (is (= 8080 (:http-port configuration)))
    (is (= 7888 (:nrepl-port configuration)))
    (is (= ".nrepl-port" (:nrepl-port-file configuration)))
    (is (= "http://localhost:8080" (:app-base-url configuration)))
    (is (= "grain-reframe-workshop-template-session" (:auth-cookie-name configuration)))
    (is (= :logger (:email-provider configuration)))
    (is (= :memory (:file-store-provider configuration)))
    (is (= :local (:crypto-provider configuration)))
    (is (= :console (:log-destination configuration)))
    (is (= "en-US" (:locale configuration)))
    (is (= "UTC" (:time-zone configuration)))
    (is (false? (:cookie-secure? configuration)))
    (is (false? (:skip-event-model-guard? configuration)))
    (is (= "storage" (:storage-dir configuration)))))

(deftest local-base-url-follows-the-configured-port
  (is (= "http://localhost:9191"
         (:app-base-url (config/load {"APP_HTTP_PORT" "9191"})))))

(deftest development-nrepl-accepts-an-ephemeral-port
  (let [configuration (config/load {"APP_NREPL_PORT" "0"
                                    "APP_NREPL_PORT_FILE" "/tmp/test.nrepl-port"})]
    (is (= 0 (:nrepl-port configuration)))
    (is (= "/tmp/test.nrepl-port" (:nrepl-port-file configuration)))))

(deftest browser-test-email-capture-requires-an-explicit-file
  (let [configuration (config/load {"APP_EMAIL_PROVIDER" "capture"
                                    "APP_EMAIL_CAPTURE_FILE" "/tmp/browser-emails.edn"})]
    (is (= :capture (:email-provider configuration)))
    (is (= "/tmp/browser-emails.edn" (:email-capture-file configuration))))

  (let [failure (try
                  (config/load {"APP_EMAIL_PROVIDER" "capture"})
                  nil
                  (catch clojure.lang.ExceptionInfo error error))]
    (is (some #{"APP_EMAIL_CAPTURE_FILE is required when APP_EMAIL_PROVIDER=capture"}
              (:config/errors (ex-data failure))))))

(deftest invalid-values-fail-together-at-the-configuration-interface
  (let [failure (try
                  (config/load {"APP_ENV" "elsewhere"
                                "APP_HTTP_PORT" "many"
                                "APP_TENANT_ID" "not-a-uuid"
                                "APP_AUTH_COOKIE_NAME" "has spaces"
                                "APP_LOCALE" "und"
                                "APP_TIME_ZONE" "not/a-zone"
                                "APP_COOKIE_SECURE" "sometimes"})
                  nil
                  (catch clojure.lang.ExceptionInfo error error))
        errors (:config/errors (ex-data failure))]
    (is (some? failure))
    (is (= 8 (count errors)))
    (is (some #{"APP_ENV must be development, test, or production"} errors))
    (is (some #{"APP_HTTP_PORT must be an integer from 1 through 65535"} errors))))

(deftest production-rejects-development-security-defaults
  (testing "production cannot silently inherit starter credentials or HTTP cookies"
    (let [failure (try
                    (config/load {"APP_ENV" "production"
                                  "APP_BASE_URL" "http://app.example.com"
                                  "APP_COOKIE_SECURE" "false"})
                    nil
                    (catch clojure.lang.ExceptionInfo error error))
          errors (:config/errors (ex-data failure))]
      (is (some #{"APP_JWT_SECRET must be supplied and must not use the development placeholder in production"}
                errors))
      (is (some #{"APP_BASE_URL must use https in production"} errors))
      (is (some #{"APP_COOKIE_SECURE must be true in production"} errors))))

  (testing "an explicit production configuration parses into typed values"
    (let [tenant-id (random-uuid)
          configuration (config/load
                         {"APP_ENV" "production"
                          "APP_NAME" "My App"
                          "APP_HTTP_PORT" "443"
                          "APP_BASE_URL" "https://app.example.com"
                          "APP_JWT_SECRET" "production-secret-from-a-secret-store"
                          "APP_CRYPTO_KEY" "YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
                          "APP_TENANT_ID" (str tenant-id)
                          "APP_EMAIL_FROM" "app@example.com"
                          "APP_EMAIL_PROVIDER" "ses"
                          "APP_FILE_STORE_PROVIDER" "s3"
                          "APP_S3_BUCKET" "my-app-files"
                          "APP_AUTH_COOKIE_NAME" "my-app-session"
                          "APP_COOKIE_SECURE" "true"
                          "APP_STORAGE_DIR" "/var/lib/my-app"})]
      (is (config/production? configuration))
      (is (= tenant-id (:tenant-id configuration)))
      (is (= 443 (:http-port configuration)))
      (is (true? (:cookie-secure? configuration))))))

(deftest production-rejects-local-provider-shortcuts
  (let [failure (try
                  (config/load {"APP_ENV" "production"
                                "APP_BASE_URL" "https://app.example.com"
                                "APP_COOKIE_SECURE" "true"
                                "APP_JWT_SECRET" "production-secret"
                                "APP_EMAIL_PROVIDER" "smtp"
                                "APP_FILE_STORE_PROVIDER" "memory"
                                "AWS_ENDPOINT_URL" "http://localhost:4566"})
                  nil
                  (catch clojure.lang.ExceptionInfo error error))
        errors (:config/errors (ex-data failure))]
    (is (some #{"APP_EMAIL_PROVIDER must be ses in production"} errors))
    (is (some #{"APP_FILE_STORE_PROVIDER must be s3 in production"} errors))
    (is (some #{"AWS_ENDPOINT_URL must not override AWS endpoints in production"} errors))))
