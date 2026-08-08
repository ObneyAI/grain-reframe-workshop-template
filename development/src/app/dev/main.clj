(ns app.dev.main
  (:gen-class)
  (:require [app.config.interface :as config]
            [app.web-api.core :as web-api]))

(defn -main
  [& _args]
  (let [configuration (config/load)]
    (.addShutdownHook
     (Runtime/getRuntime)
     (Thread. ^Runnable (fn [] (web-api/stop!))))
    (web-api/start!)
    (println (str "Backend ready at " (:app-base-url configuration)))
    (println (str "Health check: " (:app-base-url configuration) "/healthcheck"))
    @(promise)))
