(ns app.config.interface
  (:refer-clojure :exclude [load])
  (:require [clojure.string :as string]))

(def ^:private development-jwt-secret "dev-secret-change-me")
(def ^:private development-crypto-key
  "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
(def ^:private development-tenant-id
  #uuid "b1d4d3e2-0000-4000-8000-000000000001")

(defn- present?
  [value]
  (and (string? value) (not (string/blank? value))))

(defn- parse-environment
  [value]
  (case (some-> (or value "development") string/lower-case)
    ("dev" "development") :development
    "test" :test
    ("prod" "production") :production
    nil))

(defn- parse-integer
  [value]
  (try
    (Integer/parseInt value)
    (catch Exception _ nil)))

(defn- parse-boolean-value
  [value default]
  (case (some-> value string/lower-case)
    nil default
    ("1" "true") true
    ("0" "false") false
    ::invalid))

(defn- parse-uuid-value
  [value]
  (try
    (some-> value java.util.UUID/fromString)
    (catch Exception _ nil)))

(defn- parse-choice
  [value default choices]
  (let [choice (keyword (some-> (or value default) string/lower-case))]
    (when (contains? choices choice) choice)))

(defn- http-url?
  [value]
  (try
    (let [uri (java.net.URI. value)]
      (and (contains? #{"http" "https"} (.getScheme uri))
           (present? (.getHost uri))))
    (catch Exception _ false)))

(defn- uri-scheme
  [value]
  (try
    (some-> value java.net.URI. .getScheme)
    (catch Exception _ nil)))

(defn- cookie-name?
  [value]
  (boolean (and (present? value)
                (re-matches #"[A-Za-z0-9!#$%&'*+.^_`|~-]+" value))))

(defn- locale?
  [value]
  (try
    (let [locale (java.util.Locale/forLanguageTag value)]
      (and (present? value)
           (not (string/blank? (.getLanguage locale)))
           (not= "und" (.toLanguageTag locale))))
    (catch Exception _ false)))

(defn- time-zone?
  [value]
  (try
    (java.time.ZoneId/of value)
    true
    (catch Exception _ false)))

(defn- validation-errors
  [environment values]
  (cond-> []
    (nil? environment)
    (conj "APP_ENV must be development, test, or production")

    (not (present? (:app-name values)))
    (conj "APP_NAME must not be blank")

    (not (and (:http-port values) (<= 1 (:http-port values) 65535)))
    (conj "APP_HTTP_PORT must be an integer from 1 through 65535")

    (not (http-url? (:app-base-url values)))
    (conj "APP_BASE_URL must be an absolute http or https URL")

    (nil? (:tenant-id values))
    (conj "APP_TENANT_ID must be a UUID")

    (not (present? (:email-from values)))
    (conj "APP_EMAIL_FROM must not be blank")

    (nil? (:email-provider values))
    (conj "APP_EMAIL_PROVIDER must be logger, smtp, or ses")

    (not (and (:smtp-port values) (<= 1 (:smtp-port values) 65535)))
    (conj "APP_SMTP_PORT must be an integer from 1 through 65535")

    (nil? (:file-store-provider values))
    (conj "APP_FILE_STORE_PROVIDER must be memory or s3")

    (nil? (:crypto-provider values))
    (conj "APP_CRYPTO_PROVIDER must be local or kms")

    (nil? (:log-destination values))
    (conj "APP_LOG_DESTINATION must be console, json-console, or file")

    (and (= :file (:log-destination values))
         (not (present? (:log-file values))))
    (conj "APP_LOG_FILE is required when APP_LOG_DESTINATION=file")

    (and (= :s3 (:file-store-provider values))
         (not (present? (:s3-bucket values))))
    (conj "APP_S3_BUCKET is required when APP_FILE_STORE_PROVIDER=s3")

    (and (= :kms (:crypto-provider values))
         (not (present? (:kms-key-id values))))
    (conj "APP_KMS_KEY_ID is required when APP_CRYPTO_PROVIDER=kms")

    (and (or (= :ses (:email-provider values))
             (= :s3 (:file-store-provider values))
             (= :kms (:crypto-provider values)))
         (not (present? (:aws-region values))))
    (conj "AWS_REGION is required by the selected AWS provider")

    (and (present? (:aws-endpoint values))
         (not (http-url? (:aws-endpoint values))))
    (conj "AWS_ENDPOINT_URL must be an absolute http or https URL")

    (not (cookie-name? (:auth-cookie-name values)))
    (conj "APP_AUTH_COOKIE_NAME must be a valid cookie name")

    (not (locale? (:locale values)))
    (conj "APP_LOCALE must be a valid BCP 47 language tag")

    (not (time-zone? (:time-zone values)))
    (conj "APP_TIME_ZONE must be a valid IANA or UTC time zone")

    (= ::invalid (:cookie-secure? values))
    (conj "APP_COOKIE_SECURE must be true, false, 1, or 0")

    (= ::invalid (:skip-event-model-guard? values))
    (conj "APP_SKIP_EVENT_MODEL_GUARD must be true, false, 1, or 0")

    (not (present? (:storage-dir values)))
    (conj "APP_STORAGE_DIR must not be blank")

    (and (= :production environment)
         (or (not (present? (:jwt-secret values)))
             (= development-jwt-secret (:jwt-secret values))))
    (conj "APP_JWT_SECRET must be supplied and must not use the development placeholder in production")

    (and (= :production environment)
         (not= "https" (uri-scheme (:app-base-url values))))
    (conj "APP_BASE_URL must use https in production")

    (and (= :production environment)
         (not (true? (:cookie-secure? values))))
    (conj "APP_COOKIE_SECURE must be true in production")

    (and (= :production environment)
         (not= :ses (:email-provider values)))
    (conj "APP_EMAIL_PROVIDER must be ses in production")

    (and (= :production environment)
         (= :memory (:file-store-provider values)))
    (conj "APP_FILE_STORE_PROVIDER must be s3 in production")

    (and (= :production environment)
         (= :local (:crypto-provider values))
         (= development-crypto-key (:crypto-key values)))
    (conj "APP_CRYPTO_KEY must not use the development placeholder in production")

    (and (= :production environment)
         (present? (:aws-endpoint values)))
    (conj "AWS_ENDPOINT_URL must not override AWS endpoints in production")))

(defn load
  "Parse and validate application configuration. With no argument, reads the
   process environment. Tests and tools may pass a string-keyed environment map."
  ([] (load (System/getenv)))
  ([environment-variables]
   (let [environment (parse-environment (get environment-variables "APP_ENV"))
         port-value (or (get environment-variables "APP_HTTP_PORT") "8080")
         http-port (parse-integer port-value)
         production? (= :production environment)
         values {:environment environment
                 :app-name (or (get environment-variables "APP_NAME")
                               "Grain Re-frame Workshop Template")
                 :http-port http-port
                 :app-base-url (or (get environment-variables "APP_BASE_URL")
                                   (str "http://localhost:" port-value))
                 :jwt-secret (or (get environment-variables "APP_JWT_SECRET")
                                 development-jwt-secret)
                 :tenant-id (parse-uuid-value (or (get environment-variables "APP_TENANT_ID")
                                                  (str development-tenant-id)))
                 :email-from (or (get environment-variables "APP_EMAIL_FROM")
                                 "noreply@grain-reframe-workshop-template.local")
                 :email-provider (parse-choice (get environment-variables "APP_EMAIL_PROVIDER")
                                               "logger"
                                               #{:logger :smtp :ses})
                 :smtp-host (or (get environment-variables "APP_SMTP_HOST") "localhost")
                 :smtp-port (parse-integer (or (get environment-variables "APP_SMTP_PORT") "1025"))
                 :file-store-provider
                 (parse-choice (get environment-variables "APP_FILE_STORE_PROVIDER")
                               "memory"
                               #{:memory :s3})
                 :crypto-provider (parse-choice (get environment-variables "APP_CRYPTO_PROVIDER")
                                                "local"
                                                #{:local :kms})
                 :crypto-key (or (get environment-variables "APP_CRYPTO_KEY")
                                 development-crypto-key)
                 :aws-region (or (get environment-variables "AWS_REGION") "us-east-1")
                 :aws-endpoint (get environment-variables "AWS_ENDPOINT_URL")
                 :s3-bucket (or (get environment-variables "APP_S3_BUCKET") "grain-files")
                 :kms-key-id (or (get environment-variables "APP_KMS_KEY_ID")
                                 "alias/grain-local-key")
                 :log-destination
                 (parse-choice (get environment-variables "APP_LOG_DESTINATION")
                               (if production? "json-console" "console")
                               #{:console :json-console :file})
                 :log-file (or (get environment-variables "APP_LOG_FILE")
                               (str (or (get environment-variables "APP_STORAGE_DIR") "storage")
                                    "/application.log"))
                 :auth-cookie-name (or (get environment-variables "APP_AUTH_COOKIE_NAME")
                                       "grain-reframe-workshop-template-session")
                 :locale (or (get environment-variables "APP_LOCALE") "en-US")
                 :time-zone (or (get environment-variables "APP_TIME_ZONE") "UTC")
                 :cookie-secure? (parse-boolean-value
                                  (get environment-variables "APP_COOKIE_SECURE")
                                  production?)
                 :skip-event-model-guard? (parse-boolean-value
                                           (get environment-variables
                                                "APP_SKIP_EVENT_MODEL_GUARD")
                                           false)
                 :storage-dir (or (get environment-variables "APP_STORAGE_DIR") "storage")}
         errors (validation-errors environment values)]
     (when (seq errors)
       (throw (ex-info (str "Invalid application configuration:\n- "
                            (string/join "\n- " errors))
                       {:config/errors errors})))
     values)))

(defn production?
  [configuration]
  (= :production (:environment configuration)))
