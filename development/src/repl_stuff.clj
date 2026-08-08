(ns repl-stuff
  (:require [app.web-api.core :as service]))

(comment
  ;; Start the API + Re-frame static host on :8080 (code-agent-tools installed)
  (do
    (def service (service/start))
    (def context (::service/context service))
    (def event-store (:event-store context)))

  ;; Inspect the live app the way the code agent does:
  (require '[ai.obney.grain.code-agent-tools.interface :as tools])
  (tools/catalog)
  (tools/diagnostics)

  ;; Stop
  (service/stop service))
