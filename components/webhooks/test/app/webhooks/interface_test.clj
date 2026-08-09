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
        request {:headers {"x-webhook-signature" (signature secret "payload")
                           "authorization" "Bearer private-provider-token"
                           "x-provider-debug" "internal-routing-value"}
                 :body (.getBytes "payload")}
        first-result (webhooks/receive! processor request)
        receipt-id (get-in first-result [:webhook/receipt :webhook/receipt-id])
        duplicate-result (webhooks/receive! processor request)]
    (is (= :failed (:webhook/status first-result)))
    (is (nil? (get-in first-result [:webhook/receipt :webhook/headers])))
    (is (= :duplicate (:webhook/status duplicate-result)))
    (is (nil? (get-in duplicate-result [:webhook/receipt :webhook/headers])))
    (is (= 1 @attempts))
    (reset! fail? false)
    (let [replay-result (webhooks/replay! processor receipt-id)
          stored-receipt (webhooks/receipt processor receipt-id)]
      (is (= :processed (:webhook/status replay-result)))
      (is (nil? (get-in replay-result [:webhook/receipt :webhook/headers])))
      (is (= 2 (:webhook/attempts stored-receipt)))
      (is (nil? (:webhook/raw-body stored-receipt)))
      (is (nil? (:webhook/headers stored-receipt))))))

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
