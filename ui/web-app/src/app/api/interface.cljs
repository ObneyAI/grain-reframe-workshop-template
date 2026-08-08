(ns app.api.interface
  "The frontend's single seam for Grain HTTP access.

   Callers describe a command or query and the module owns metadata, Transit,
   credentials, anomaly normalization, and Re-frame dispatch."
  (:require [app.anomalies :as anomaly]
            [app.api.core :as core]
            [app.request.interface :as request-state]
            [cljs.core.async :refer [go <!]]
            [re-frame.core :as rf]))

(defonce ^:private !client (atom nil))

(defn configure! [client]
  (reset! !client client))

(defn remote-client [config]
  (core/remote-client config))

(defn stub-client [handler]
  (core/stub-client handler))

(defn- dispatch-result! [event result]
  (when event
    (rf/dispatch (conj event result))))

(defn- finish-request!
  [{:keys [request-key operation-id]} outcome callback result]
  (if request-key
    (request-state/finish! request-key operation-id outcome callback result)
    (dispatch-result! callback result)))

(rf/reg-fx
 ::request
 (fn [{:keys [kind payload on-success on-failure request-key operation-id retry-event]
       :as request}]
   (when request-key
     (request-state/begin! request-key operation-id retry-event))
   (if-let [client @!client]
     (go
       (let [result (<! (core/send! client {:kind kind :payload payload}))]
         (if (anomaly/anomaly? result)
           (finish-request! request :failure on-failure result)
           (finish-request! request :success on-success result))))
     (finish-request!
      request
      :failure
      on-failure
      {::anomaly/category ::anomaly/fault
       ::anomaly/message "The API client has not been configured."}))))

(defn- effect
  [kind {:keys [name params on-success on-failure request-key retry-event]}]
  {::request {:kind kind
              :payload (assoc (or params {})
                              (case kind :command :command/name :query :query/name)
                              name)
              :on-success on-success
              :on-failure on-failure
              :request-key request-key
              :retry-event retry-event
              :operation-id (when request-key (str (random-uuid)))}})

(defn command [options]
  (effect :command options))

(defn query [options]
  (effect :query options))
