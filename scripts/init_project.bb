#!/usr/bin/env bb
;; Give a fresh Grain Re-frame Workshop Template clone its application identity.
;;
;; bb scripts/init_project.bb --slug my-app --name "My App" --port 8080

(ns init-project
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as string]))

(def usage
  "Usage: bb scripts/init_project.bb --slug <kebab-case> --name <display name> --port <port>\n\nOptional: --cookie-name <name> --base-url <url> --tenant-id <uuid> --locale <BCP-47> --time-zone <IANA zone>")

(defn fail!
  [message]
  (binding [*out* *err*]
    (println "Project initialization failed:" message)
    (println usage))
  (System/exit 1))

(defn parse-options
  [args]
  (loop [remaining args
         options {}]
    (if (empty? remaining)
      options
      (let [[option value & tail] remaining]
        (when-not (and (string/starts-with? (or option "") "--") value)
          (fail! (str "Expected --option value, received " (pr-str remaining))))
        (recur tail (assoc options (keyword (subs option 2)) value))))))

(defn valid-url?
  [value]
  (try
    (let [uri (java.net.URI. value)]
      (and (contains? #{"http" "https"} (.getScheme uri))
           (not (string/blank? (.getHost uri)))))
    (catch Exception _ false)))

(defn valid-locale?
  [value]
  (try
    (let [locale (java.util.Locale/forLanguageTag value)]
      (and (not (string/blank? (.getLanguage locale)))
           (not= "und" (.toLanguageTag locale))))
    (catch Exception _ false)))

(defn valid-time-zone?
  [value]
  (try (java.time.ZoneId/of value) true (catch Exception _ false)))

(defn validate!
  [{:keys [base-url cookie-name locale name port slug tenant-id time-zone] :as options}]
  (let [unknown-options (seq (remove #{:base-url :cookie-name :locale :name :port :slug
                                      :tenant-id :time-zone}
                                     (keys options)))]
    (when unknown-options
      (fail! (str "Unknown options: " (string/join ", " (map #(str "--" (clojure.core/name %))
                                                             unknown-options))))))
  (when-not (re-matches #"[a-z0-9]+(?:-[a-z0-9]+)*" (or slug ""))
    (fail! "--slug must be lower-case kebab-case"))
  (when (string/blank? (or name ""))
    (fail! "--name must not be blank"))
  (when-not (try (<= 1 (Integer/parseInt (or port "")) 65535)
                 (catch Exception _ false))
    (fail! "--port must be an integer from 1 through 65535"))
  (when-not (re-matches #"[A-Za-z0-9!#$%&'*+.^_`|~-]+" (or cookie-name ""))
    (fail! "--cookie-name must be a valid cookie name"))
  (when-not (valid-url? base-url)
    (fail! "--base-url must be an absolute http or https URL"))
  (when-not (try (java.util.UUID/fromString tenant-id) true (catch Exception _ false))
    (fail! "--tenant-id must be a UUID"))
  (when-not (valid-locale? locale)
    (fail! "--locale must be a valid BCP 47 language tag"))
  (when-not (valid-time-zone? time-zone)
    (fail! "--time-zone must be a valid IANA or UTC time zone")))

(defn replace-required!
  [path old new]
  (let [content (slurp path)]
    (when-not (string/includes? content old)
      (fail! (str path " no longer contains expected starter text " (pr-str old))))
    (spit path (string/replace content old new))))

(defn starter-ref
  []
  (or (not-empty (System/getenv "STARTER_REF"))
      (try
        (let [result (process/shell {:out :string :err :string :continue true}
                                    "git describe --tags --always --dirty")]
          (if (zero? (:exit result))
            (string/trim (:out result))
            "unreleased"))
        (catch Exception _ "unreleased"))))

(defn assert-starter-root!
  []
  (doseq [path ["README.md" "deps.edn" "workspace.edn"
                "components/config/src/app/config/interface.clj"
                "bases/web-api/resources/public/index.html"]]
    (when-not (fs/regular-file? path)
      (fail! (str "Run this command from the Grain starter root; missing " path)))))

;; Identity tokens that must not survive initialization. Most are rewritten by
;; initialize!; legacy tokens are forbidden outright. The post-init scan is the
;; backstop that fails the run if any remains in executable configuration.
(def starter-identity-tokens
  ["Grain Re-frame Workshop Template"
   "grain-reframe-workshop-template"
   "reframe-template"
   "b1d4d3e2-0000-4000-8000-000000000001"
   "Grain Starter" "grain-starter-session" "noreply@grain-starter.local"
   "\"auth-token\"" "SEED_"])

(defn stale-identity-matches
  []
  (let [paths [".env.example" "package.json" "package-lock.json"
               "bases/web-api/resources/public/index.html"
               "components/auth/src/app/auth/interface.clj"
               "components/config/src/app/config/interface.clj"
               "components/config/test/app/config/interface_test.clj"
               "scripts/dev"
               "ui/web-app/src/app/ui/interface.cljs"]]
    (for [path paths
          :let [content (slurp path)]
          stale starter-identity-tokens
          :when (string/includes? content stale)]
      [path stale])))

(defn initialize!
  [{:keys [base-url cookie-name locale name port slug tenant-id time-zone]}]
  (let [email-from (str "noreply@" slug ".local")
        origin (starter-ref)]
    (replace-required! "README.md"
                       "# Grain Re-frame Workshop Template"
                       (str "# " name
                            "\n\n> Initialized from `grain-reframe-workshop-template` at `"
                            origin "`."))
    (replace-required! "README.md"
                       "<https://reframe-template.localhost>"
                       (str "<" base-url ">"))
    (replace-required! "package.json"
                       "\"name\": \"grain-reframe-workshop-template\""
                       (str "\"name\": \"" slug "\""))
    (replace-required! "package-lock.json"
                       "\"name\": \"grain-reframe-workshop-template\""
                       (str "\"name\": \"" slug "\""))
    (replace-required! "bases/web-api/resources/public/index.html"
                       "content=\"A Grain application\""
                       (str "content=\"" name "\""))
    (replace-required! "bases/web-api/resources/public/index.html"
                       "<title>Grain Re-frame Workshop Template</title>"
                       (str "<title>" name "</title>"))
    (replace-required! "ui/web-app/src/app/ui/interface.cljs"
                       "\"Grain Re-frame Workshop Template\""
                       (pr-str name))
    (replace-required! "components/auth/src/app/auth/interface.clj"
                       "\"grain-reframe-workshop-template-session\""
                       (pr-str cookie-name))
    (doseq [[old new] [["APP_NAME=\"Grain Re-frame Workshop Template\""
                        (str "APP_NAME=\"" name "\"")]
                       ["APP_HTTP_PORT=8080" (str "APP_HTTP_PORT=" port)]
                       ["APP_BASE_URL=https://reframe-template.localhost"
                        (str "APP_BASE_URL=" base-url)]
                       ["APP_DEV_HOSTNAME=reframe-template"
                        (str "APP_DEV_HOSTNAME=" slug)]
                       ["APP_TENANT_ID=b1d4d3e2-0000-4000-8000-000000000001"
                        (str "APP_TENANT_ID=" tenant-id)]
                       ["APP_EMAIL_FROM=noreply@grain-reframe-workshop-template.local"
                        (str "APP_EMAIL_FROM=" email-from)]
                       ["APP_AUTH_COOKIE_NAME=grain-reframe-workshop-template-session"
                        (str "APP_AUTH_COOKIE_NAME=" cookie-name)]
                       ["APP_LOCALE=en-US" (str "APP_LOCALE=" locale)]
                       ["APP_TIME_ZONE=UTC" (str "APP_TIME_ZONE=" time-zone)]]]
      (replace-required! ".env.example" old new))
    (doseq [[old new] [["\"Grain Re-frame Workshop Template\"" (pr-str name)]
                       ["(get environment-variables \"APP_HTTP_PORT\") \"8080\""
                        (str "(get environment-variables \"APP_HTTP_PORT\") " (pr-str port))]
                       ["#uuid \"b1d4d3e2-0000-4000-8000-000000000001\""
                        (str "#uuid \"" tenant-id "\"")]
                       ["\"noreply@grain-reframe-workshop-template.local\"" (pr-str email-from)]
                       ["\"grain-reframe-workshop-template-session\"" (pr-str cookie-name)]]]
      (replace-required! "components/config/src/app/config/interface.clj" old new))
    (doseq [[old new] [["(get environment-variables \"APP_LOCALE\") \"en-US\""
                        (str "(get environment-variables \"APP_LOCALE\") " (pr-str locale))]
                       ["(get environment-variables \"APP_TIME_ZONE\") \"UTC\""
                        (str "(get environment-variables \"APP_TIME_ZONE\") " (pr-str time-zone))]]]
      (replace-required! "components/config/src/app/config/interface.clj" old new))
    (doseq [[old new] [["\"Grain Re-frame Workshop Template\"" (pr-str name)]
                       ["(= 8080 (:http-port configuration))"
                        (str "(= " port " (:http-port configuration))")]
                       ["\"http://localhost:8080\""
                        (pr-str (str "http://localhost:" port))]
                       ["\"grain-reframe-workshop-template-session\""
                        (pr-str cookie-name)]]]
      (replace-required! "components/config/test/app/config/interface_test.clj" old new))
    (doseq [[old new] [["(= \"en-US\" (:locale configuration))"
                        (str "(= " (pr-str locale) " (:locale configuration))")]
                       ["(= \"UTC\" (:time-zone configuration))"
                        (str "(= " (pr-str time-zone) " (:time-zone configuration))")]]]
      (replace-required! "components/config/test/app/config/interface_test.clj" old new))
    (replace-required! "scripts/dev"
                       "${APP_HTTP_PORT:-8080}"
                       (str "${APP_HTTP_PORT:-" port "}"))
    (replace-required! "scripts/dev"
                       "${APP_DEV_HOSTNAME:-reframe-template}"
                       (str "${APP_DEV_HOSTNAME:-" slug "}"))
    (replace-required! "development/src/repl_stuff.clj"
                       "on :8080"
                       (str "on :" port))
    (let [stale (vec (stale-identity-matches))]
      (when (seq stale)
        (fail! (str "stale starter identity remains: " (pr-str stale)))))
    (println "Initialized" name)
    (println "  slug:" slug)
    (println "  URL:" base-url)
    (println "  cookie:" cookie-name)
    (println "  tenant:" tenant-id)
    (println "  locale:" locale)
    (println "  time zone:" time-zone)
    (println "Next: ./scripts/dev up")))

(defn -main
  [& args]
  (assert-starter-root!)
  (let [provided (parse-options args)
        port (:port provided)
        slug (:slug provided)
        options (merge provided
                       {:base-url (or (:base-url provided)
                                     (when slug (str "https://" slug ".localhost")))
                        :cookie-name (or (:cookie-name provided)
                                         (when slug (str slug "-session")))
                        :locale (or (:locale provided) "en-US")
                        :time-zone (or (:time-zone provided) "UTC")
                        :tenant-id (or (:tenant-id provided) (str (random-uuid)))})]
    (validate! options)
    (initialize! options)))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
