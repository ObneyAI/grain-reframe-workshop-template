(ns app.api.core
  (:require [app.anomalies :as anomaly]
            [cljs-http.client :as http]
            [cljs.core.async :refer [go <!]]))

(defprotocol Client
  (send! [client request]
    "Send a Grain command or query request and return a core.async channel."))

(defn- status->category [status]
  (cond
    (#{401 403} status) ::anomaly/forbidden
    (= 404 status) ::anomaly/not-found
    (= 409 status) ::anomaly/conflict
    (= 429 status) ::anomaly/busy
    (= 503 status) ::anomaly/unavailable
    (nil? status) ::anomaly/unavailable
    :else ::anomaly/fault))

(defn response->result [{:keys [status body] :as response}]
  (if (and status (<= 200 status 299))
    body
    (cond->
     {::anomaly/category (status->category status)
      ::anomaly/message (or (:message body)
                            (::anomaly/message body)
                            (:cognitect.anomalies/message body)
                            (if status
                              (str "Request failed with status " status ".")
                              "The server could not be reached."))
      :http/status status
      :http/response response}
      (or (:error/explain body) (:explain body))
      (assoc :error/explain (or (:error/explain body) (:explain body))))))

(defn- add-request-metadata [kind payload]
  (merge payload
         {(keyword (name kind) "id") (str (random-uuid))
          (keyword (name kind) "timestamp") (js/Date.)}))

(deftype RemoteClient [base-url headers]
  Client
  (send! [_ {:keys [kind payload]}]
    (let [path (case kind :command "/command" :query "/query")
          envelope-key kind
          request-body {envelope-key (add-request-metadata kind payload)}]
      (go
        (-> (http/post (str base-url path)
                       {:transit-params request-body
                        :with-credentials? true
                        :headers (merge {"Content-Type" "application/transit+json"}
                                        headers)})
            <!
            response->result)))))

(deftype StubClient [handler]
  Client
  (send! [_ request]
    (handler request)))

(defn remote-client [{:keys [base-url headers]
                      :or {base-url "" headers {}}}]
  (RemoteClient. base-url headers))

(defn stub-client [handler]
  (StubClient. handler))
