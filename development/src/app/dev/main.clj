(ns app.dev.main
  (:gen-class)
  (:require [app.config.interface :as config]
            [app.web-api.core :as web-api]
            [clojure.java.io :as io]
            [nrepl.server :as nrepl]))

(defonce ^:private !nrepl (atom nil))

(defn- bound-port
  [server requested-port]
  (if-let [socket (:server-socket server)]
    (.getLocalPort socket)
    requested-port))

(defn- start-nrepl!
  [{:keys [nrepl-port nrepl-port-file]}]
  (let [server (nrepl/start-server :bind "127.0.0.1" :port nrepl-port)
        port (bound-port server nrepl-port)]
    (io/make-parents nrepl-port-file)
    (spit nrepl-port-file (str port "\n"))
    (reset! !nrepl {:server server :port port :port-file nrepl-port-file})
    port))

(defn- stop-nrepl!
  []
  (when-let [{:keys [server port-file]} @!nrepl]
    (reset! !nrepl nil)
    (nrepl/stop-server server)
    (io/delete-file port-file true)))

(defn- stop!
  []
  (stop-nrepl!)
  (web-api/stop!))

(defn -main
  [& _args]
  (let [configuration (config/load)]
    (try
      (web-api/start!)
      (let [nrepl-port (start-nrepl! configuration)]
        (.addShutdownHook
         (Runtime/getRuntime)
         (Thread. ^Runnable (fn [] (stop!))))
        (println (str "Backend ready at " (:app-base-url configuration)))
        (println (str "Health check: " (:app-base-url configuration) "/healthcheck"))
        (println (str "nREPL ready at 127.0.0.1:" nrepl-port))
        @(promise))
      (catch Throwable error
        (stop!)
        (throw error)))))
