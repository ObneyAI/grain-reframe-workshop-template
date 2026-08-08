(ns app.config
  (:require [clojure.string :as string]))

(goog-define API_BASE_URL "")

(defn api-base-url []
  (string/replace API_BASE_URL #"/$" ""))

(defn- meta-content
  [name fallback]
  (or (when (exists? js/document)
        (some-> js/document
                (.querySelector (str "meta[name='" name "']"))
                (.getAttribute "content")
                not-empty))
      fallback))

(defn locale []
  (meta-content "app-locale" "en-US"))

(defn time-zone []
  (meta-content "app-time-zone" "UTC"))
