(ns app.router.interface-test
  (:require [app.router.interface :as router]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest route-matching-keeps-selection-and-view-state-in-query-values
  (let [match (router/match-token "/examples/routes?record-id=contact-42&tab=history")]
    (is (= :query-route-example (get-in match [:data :name])))
    (is (= {} (:path-params match)))
    (is (= "contact-42" (get-in match [:query-params :record-id])))
    (is (= "history" (get-in match [:query-params :tab])))
    (is (= "/examples/routes?record-id=contact-42&tab=history" (:token match)))))

(deftest unknown-paths-have-an-explicit-outcome
  (let [match (router/match-token "/there-is-no-page")]
    (is (= :not-found (get-in match [:data :name])))
    (is (= :not-found (get-in match [:data :page])))))

(deftest protected-route-policy-never-flashes-private-content
  (let [home (router/match-token "/")]
    (testing "session checking holds protected rendering"
      (is (= {:action :hold}
             (router/route-decision home :checking))))

    (testing "an authenticated session may render"
      (is (= {:action :render}
             (router/route-decision home :authenticated))))

    (testing "an anonymous session receives a safe return path"
      (is (= {:action :redirect
              :route :sign-in
              :query-params {:return-to "/"}}
             (router/route-decision home :anonymous))))))

(deftest authenticated-users-leave-guest-only-routes
  (testing "a protected local return path is honored"
    (let [sign-in (router/match-token
                   "/auth/sign-in?return-to=%2Fexamples%2Froutes%3Frecord-id%3Dcontact-42%26tab%3Dhistory")]
      (is (= {:action :redirect
              :path "/examples/routes?record-id=contact-42&tab=history"}
             (router/route-decision sign-in :authenticated)))))

  (testing "external, protocol-relative, public, and malformed destinations are rejected"
    (doseq [candidate ["https://attacker.example"
                       "//attacker.example"
                       "/auth/sign-in"
                       "/unknown"
                       "/safe\\unsafe"]]
      (is (nil? (router/safe-return-path candidate))))))

(deftest public-outcome-routes-do-not-wait-for-session-resolution
  (is (= {:action :render}
         (router/route-decision (router/match-token "/unknown") :checking))))

(deftest forbidden-page-is-only-an-authenticated-outcome
  (let [match (router/match-token "/forbidden")]
    (is (= {:action :hold} (router/route-decision match :checking)))
    (is (= {:action :render} (router/route-decision match :authenticated)))
    (is (= :sign-in (:route (router/route-decision match :anonymous))))))
