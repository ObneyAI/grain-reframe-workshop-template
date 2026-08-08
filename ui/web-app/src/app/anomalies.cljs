(ns app.anomalies)

(def category ::category)
(def message ::message)

(defn anomaly? [value]
  (contains? value category))
