#!/usr/bin/env bb

(ns dev-data
  (:require [babashka.fs :as fs]
            [babashka.process :as process]
            [clojure.string :as string]))

(def usage
  (str "Usage: bb dev reset|seed [--storage-dir <path>] [--yes]\n\n"
       "reset  Deletes an explicitly safe development storage directory.\n"
       "seed   Creates/marks the storage directory and runs scripts/app_seed.bb when present.\n"
       "--yes  Skips the interactive RESET confirmation (development only)."))

(def marker-name ".grain-development-storage")
(def seed-hook "scripts/app_seed.bb")

(defn fail!
  [message]
  (binding [*out* *err*]
    (println "Development data command failed:" message)
    (println usage))
  (System/exit 1))

(defn parse-options
  [args]
  (loop [remaining args
         options {}]
    (if (empty? remaining)
      options
      (let [[option & tail] remaining]
        (case option
          "--yes" (recur tail (assoc options :yes? true))
          "--storage-dir"
          (if-let [value (first tail)]
            (recur (rest tail) (assoc options :storage-dir value))
            (fail! "--storage-dir requires a path"))
          (fail! (str "Unknown option " (pr-str option))))))))

(defn normalized-path
  [path]
  (-> path fs/path .toAbsolutePath .normalize))

(defn safe-location?
  [repo-root temporary-root storage-path]
  (let [default-storage (.resolve repo-root "storage")
        dev-data-root (.resolve repo-root ".dev-data")
        file-name (some-> storage-path .getFileName str)]
    (or (= storage-path default-storage)
        (.startsWith storage-path dev-data-root)
        (and (= repo-root (.getParent storage-path))
             (string/starts-with? (or file-name "") "storage"))
        (.startsWith storage-path temporary-root))))

(defn validate-storage-path!
  [storage-dir]
  (let [repo-root (normalized-path ".")
        home-root (normalized-path (System/getProperty "user.home"))
        temporary-root (normalized-path (System/getProperty "java.io.tmpdir"))
        storage-path (normalized-path storage-dir)]
    (when-not (and (fs/regular-file? (.resolve repo-root "deps.edn"))
                   (fs/regular-file? (.resolve repo-root "workspace.edn")))
      (fail! "Run this command from the application repository root."))
    (when (or (= storage-path (.getRoot storage-path))
              (= storage-path repo-root)
              (= storage-path home-root)
              (= storage-path temporary-root))
      (fail! (str "Refusing broad storage path " storage-path)))
    (when-not (safe-location? repo-root temporary-root storage-path)
      (fail! (str "Refusing storage path outside storage*, .dev-data/, or the system temporary directory: "
                  storage-path)))
    (when (and (fs/exists? storage-path) (fs/sym-link? storage-path))
      (fail! (str "Refusing symbolic-link storage path " storage-path)))
    (loop [candidate storage-path]
      (when (and candidate
                 (not= candidate repo-root)
                 (not= candidate temporary-root))
        (when (and (fs/exists? candidate) (fs/sym-link? candidate))
          (fail! (str "Refusing storage beneath symbolic link " candidate)))
        (recur (.getParent candidate))))
    storage-path))

(defn assert-development!
  []
  (let [environment (string/lower-case (or (System/getenv "APP_ENV") "development"))]
    (when-not (contains? #{"dev" "development" "test"} environment)
      (fail! (str "APP_ENV=" environment " is not a development/test environment.")))))

(defn marker-path
  [storage-path]
  (.resolve storage-path marker-name))

(defn mark-storage!
  [storage-path]
  (fs/create-dirs storage-path)
  (spit (str (marker-path storage-path))
        "This directory is owned by the Grain development-data lifecycle.\n"))

(defn confirmed?
  [yes? storage-path]
  (if yes?
    true
    (do
      (println "This will permanently delete development data at:")
      (println " " (str storage-path))
      (print "Type RESET to continue: ")
      (flush)
      (= "RESET" (read-line)))))

(defn reset!
  [{:keys [yes? storage-dir]}]
  (let [storage-path (validate-storage-path! storage-dir)
        default-storage (normalized-path "storage")]
    (when (and (fs/exists? storage-path)
               (not= storage-path default-storage)
               (not (fs/regular-file? (marker-path storage-path))))
      (fail! (str "Refusing unmarked custom storage directory " storage-path
                  "; run `bb dev seed` for that path before using reset.")))
    (if-not (confirmed? yes? storage-path)
      (fail! "Reset was not confirmed.")
      (do
        (when (fs/exists? storage-path)
          (fs/delete-tree storage-path))
        (println "Reset development storage:" (str storage-path))))))

(defn seed!
  [{:keys [storage-dir]}]
  (let [storage-path (validate-storage-path! storage-dir)]
    (mark-storage! storage-path)
    (if (fs/regular-file? seed-hook)
      (do
        (println "Running application seed adapter:" seed-hook)
        (process/shell {:extra-env {"APP_ENV" "development"
                                    "APP_STORAGE_DIR" (str storage-path)}}
                       "bb" seed-hook))
      (println "No application seed adapter is configured; created development storage only."))
    (println "Seed target:" (str storage-path))))

(defn -main
  [& args]
  (assert-development!)
  (let [[action & option-args] args
        options (merge {:storage-dir (or (System/getenv "APP_STORAGE_DIR") "storage")}
                       (parse-options option-args))]
    (case action
      "reset" (reset! options)
      "seed" (seed! options)
      (fail! "Expected reset or seed."))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
