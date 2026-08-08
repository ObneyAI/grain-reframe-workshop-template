(ns app.form.interface
  "Stable presentation interface for structured server validation outcomes.
   Transport normalization stays in app.api.interface; this module turns the
   normalized :error/explain value into field-oriented UI data."
  (:require [clojure.string :as string]))

(defn- message-text
  [value]
  (cond
    (string? value) value
    (sequential? value) (some message-text value)
    (map? value) (or (message-text (:message value))
                     (message-text (:error/message value)))
    (some? value) (str value)
    :else nil))

(defn field-errors
  "Return one displayable message per field from a normalized API anomaly."
  [error]
  (let [explain (or (:error/explain error)
                    (get-in error [:http/response :body :error/explain]))]
    (if (map? explain)
      (reduce-kv
       (fn [errors field messages]
         (if-let [message (message-text messages)]
           (assoc errors field message)
           errors))
       {}
       explain)
      {})))

(defn ordered-error-fields
  "Place known fields in form order and append any server-only keys
   deterministically so validation information is never discarded."
  [field-order errors]
  (let [ordered (filterv #(contains? errors %) field-order)
        known (set ordered)
        remaining (->> (keys errors)
                       (remove known)
                       (sort-by str))]
    (into ordered remaining)))

(defn focus-field!
  "Focus a field by DOM id after React has committed validation feedback."
  [field-id]
  (when-not (string/blank? field-id)
    (js/requestAnimationFrame
     (fn []
       (some-> (.getElementById js/document field-id) .focus)))))
