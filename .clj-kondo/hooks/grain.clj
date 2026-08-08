(ns hooks.grain
  (:require [clj-kondo.hooks-api :as api]))

(defn defeventmodel
  [{:keys [node]}]
  (let [args (rest (:children node))]
    {:node (api/list-node (list* (api/token-node 'do) args))}))
