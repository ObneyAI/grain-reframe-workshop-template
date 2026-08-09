(ns app.user-service.core.commands-test
  (:require [app.auth.interface :as auth]
            [app.jwt.interface :as jwt]
            [app.user-service.core.commands :as commands]
            [app.user-service.core.read-models :as read-models]
            [buddy.hashers :as hashers]
            [clojure.test :refer [deftest is testing]]))

(deftest password-changes-advance-the-session-token-version
  (let [user-id (random-uuid)]
    (testing "an authenticated password change invalidates previously issued sessions"
      (with-redefs [auth/auth-user-id (constantly user-id)
                    read-models/token-version (fn [_ _] 4)
                    hashers/derive (constantly "derived-password")]
        (let [result (commands/user-set-password
                      {:command {:password "Replacement123"}
                       :auth-claims {:user-id user-id}})
              event (first (:command-result/events result))]
          (is (= :user/password-set (:event/type event)))
          (is (= {:user-id user-id
                  :password "derived-password"
                  :token-version 5}
                 (dissoc event :event/type :event/tags))))))

    (testing "a reset-token password change invalidates previously issued sessions"
      (with-redefs [jwt/unsign (fn [_] {:user-id (str user-id)})
                    read-models/pending-reset-token (fn [_ _] "reset-token")
                    read-models/token-version (fn [_ _] 7)
                    hashers/derive (constantly "reset-derived-password")]
        (let [result (commands/user-reset-password
                      {:command {:reset-token "reset-token"
                                 :password "Replacement456"}
                       :jwt-secret "test-secret"})
              event (first (:command-result/events result))]
          (is (= :user/password-reset (:event/type event)))
          (is (= {:user-id user-id
                  :password "reset-derived-password"
                  :token-version 8}
                 (dissoc event :event/type :event/tags))))))))
