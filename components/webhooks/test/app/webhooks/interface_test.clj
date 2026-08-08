(ns app.webhooks.interface-test
  (:require [app.webhooks.interface :as webhooks]
            [clojure.test :refer [deftest is]])
  (:import [java.io ByteArrayInputStream]
           [java.nio.charset StandardCharsets]
           [java.util HexFormat]
           [javax.crypto Mac]
           [javax.crypto.spec SecretKeySpec]))

(defn- signature
  [secret body]
  (let [mac (doto (Mac/getInstance "HmacSHA256")
              (.init (SecretKeySpec. (.getBytes secret StandardCharsets/UTF_8)
                                     "HmacSHA256")))]
    (str "sha256=" (.formatHex (HexFormat/of)
                                (.doFinal mac (.getBytes body StandardCharsets/UTF_8))))))

(deftest raw-body-can-be-verified-and-read-again
  (let [request (webhooks/capture-raw-body
                 {:body (ByteArrayInputStream. (.getBytes "payload"))})]
    (is (= "payload" (String. ^bytes (:webhook/raw-body request))))
    (is (= "payload" (slurp (:body request))))))

(deftest processing-is-signed-idempotent-audited-and-replayable
  (let [secret "test-secret"
        attempts (atom 0)
        fail? (atom true)
        processor (webhooks/processor
                   {:provider :example
                    :verify-signature (webhooks/hmac-sha256-verifier {:secret secret})
                    :event-id (constantly "evt-1")
                    :handler (fn [_]
                               (swap! attempts inc)
                               (when @fail? (throw (ex-info "temporary" {})))
                               {:handled true})})
        request {:headers {"x-webhook-signature" (signature secret "payload")}
                 :body (.getBytes "payload")}
        first-result (webhooks/receive! processor request)
        receipt-id (get-in first-result [:webhook/receipt :webhook/receipt-id])]
    (is (= :failed (:webhook/status first-result)))
    (is (= :duplicate (:webhook/status (webhooks/receive! processor request))))
    (is (= 1 @attempts))
    (reset! fail? false)
    (is (= :processed (:webhook/status (webhooks/replay! processor receipt-id))))
    (is (= 2 (:webhook/attempts (webhooks/receipt processor receipt-id))))
    (is (nil? (:webhook/raw-body (webhooks/receipt processor receipt-id))))))

(deftest invalid-signatures-never-reach-the-handler
  (let [called? (atom false)
        processor (webhooks/processor
                   {:provider :example
                    :verify-signature (webhooks/hmac-sha256-verifier {:secret "secret"})
                    :event-id (constantly "evt-1")
                    :handler #(reset! called? true)})]
    (is (= :rejected
           (:webhook/status
            (webhooks/receive! processor {:headers {"x-webhook-signature" "sha256=00"}
                                          :body (.getBytes "payload")}))))
    (is (false? @called?))))
