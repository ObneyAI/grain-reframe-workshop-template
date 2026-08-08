(ns app.dev.nrepl-check
  "Confirm that an nREPL endpoint exposes the coding-agent tools installed in
   the same live Grain runtime as the backend."
  (:require [clojure.edn :as edn]
            [nrepl.core :as nrepl]))

(def ^:private catalog-expression
  "(do
     (require '[ai.obney.grain.code-agent-tools.interface :as tools])
     (let [catalog (tools/catalog)]
       {:commands (set (keys (:commands catalog)))
        :queries (set (keys (:queries catalog)))
        :read-models (set (keys (:read-models catalog)))}))")

(defn check!
  [port]
  (with-open [connection (nrepl/connect :host "127.0.0.1" :port port)]
    (let [client (nrepl/client connection 10000)
          responses (doall (nrepl/message client {:op "eval"
                                                  :code catalog-expression}))
          errors (seq (keep #(or (:ex %) (:err %)) responses))
          value (some :value (reverse responses))]
      (when errors
        (throw (ex-info "nREPL evaluation failed" {:responses responses})))
      (let [catalog (some-> value edn/read-string)]
        (when-not (and (contains? (:commands catalog) :user/login)
                       (contains? (:queries catalog) :user/session)
                       (contains? (:read-models catalog) :user/users))
          (throw (ex-info "nREPL did not expose the expected live Grain catalog"
                          {:catalog catalog})))
        catalog))))

(defn -main
  [& [port-value]]
  (let [port (parse-long (or port-value ""))
        catalog (when port (check! port))]
    (when-not catalog
      (throw (ex-info "Usage: app.dev.nrepl-check <port>" {})))
    (println (format "Live nREPL Grain catalog: %d commands, %d queries, %d read models"
                     (count (:commands catalog))
                     (count (:queries catalog))
                     (count (:read-models catalog))))))
