(ns app.dev.check
  "Fast backend contract preflight for the Grain/Re-frame starter."
  (:require [ai.obney.grain.code-agent-tools.interface :as tools]
            [ai.obney.grain.query-processor.interface :as query-processor]))

(defn- finding [severity check detail & {:keys [subject]}]
  (cond-> {:severity severity :check check :detail detail}
    subject (assoc :subject subject)))

(defn- missing-schema-findings []
  (for [[kind names] (:missing-schemas (tools/catalog))
        :when (seq names)]
    (finding :error :missing-schema
             (str (count names) " " (name kind) " missing schemas: " (pr-str names)))))

(defn- authorization-findings []
  (concat
   (for [[command entry] (:commands (tools/catalog))
         :when (false? (:authorized?/present? entry))]
     (finding :error :missing-authorization
              "Command has no :authorized? predicate." :subject command))
   (for [[query opts] (query-processor/global-query-registry)
         :when (not (contains? opts :authorized?))]
     (finding :error :missing-authorization
              "Query has no :authorized? predicate." :subject query))))

(defn grain-check []
  (let [findings (vec (concat (missing-schema-findings)
                              (authorization-findings)))
        grouped (group-by :severity findings)]
    {:findings findings
     :errors (count (:error grouped))
     :warnings (count (:warning grouped))}))

(defn report []
  (let [{:keys [findings errors warnings]} (grain-check)]
    (doseq [{:keys [severity check detail subject]} findings]
      (println (format "%-8s %-28s %s %s"
                       (str "[" (name severity) "]")
                       (name check)
                       (or subject "")
                       detail)))
    (println (format "grain check: %d error, %d warning" errors warnings))
    (zero? errors)))
