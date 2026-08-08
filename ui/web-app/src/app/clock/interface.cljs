(ns app.clock.interface
  "Application clock and date-presentation seam. Feature code accepts a Clock
   when its decisions depend on time; locale/time-zone come from runtime app
   configuration. Domain rules such as overdue or stage age stay in the clone."
  (:require [app.config :as config]))

(defprotocol Clock
  (-now [clock] "Return the current instant as a fresh JavaScript Date."))

(defrecord SystemClock []
  Clock
  (-now [_] (js/Date.)))

(defrecord FixedClock [epoch-ms]
  Clock
  (-now [_] (js/Date. epoch-ms)))

(def system-clock
  (->SystemClock))

(defn- ->epoch-ms
  [instant]
  (let [date (cond
               (instance? js/Date instant) instant
               (string? instant) (js/Date. instant)
               (number? instant) (js/Date. instant)
               :else nil)
        epoch-ms (some-> date .getTime)]
    (when (or (nil? epoch-ms) (js/isNaN epoch-ms))
      (throw (js/Error. "A fixed clock requires a valid Date, ISO string, or epoch milliseconds.")))
    epoch-ms))

(defn fixed-clock
  [instant]
  (->FixedClock (->epoch-ms instant)))

(defn now
  ([] (-now system-clock))
  ([clock] (-now clock)))

(defn date-time-formatter
  "Build an Intl formatter using the application's explicit locale/time zone."
  ([] (date-time-formatter {}))
  ([options]
   (js/Intl.DateTimeFormat.
    (config/locale)
    (clj->js (merge {:timeZone (config/time-zone)} options)))))

(defn format-date-time
  ([instant] (format-date-time instant {}))
  ([instant options]
   (.format (date-time-formatter options) (js/Date. (->epoch-ms instant)))))
