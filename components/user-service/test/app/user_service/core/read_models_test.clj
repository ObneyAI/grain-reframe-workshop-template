(ns app.user-service.core.read-models-test
  (:require [app.user-service.core.read-models :as read-models]
            [clojure.test :refer [deftest is testing]]))

(deftest password-changes-project-the-new-password-and-token-version
  (let [user-id (random-uuid)
        initial {user-id {:user/id user-id
                          :user/password "old-password"
                          :user/token-version 2
                          :user/pending-reset-token "reset-token"}}]
    (testing "authenticated password changes revoke all prior sessions"
      (let [projected (read-models/users*
                       initial
                       {:event/type :user/password-set
                        :user-id user-id
                        :password "new-password"
                        :token-version 3})]
        (is (= "new-password" (get-in projected [user-id :user/password])))
        (is (= 3 (get-in projected [user-id :user/token-version])))))

    (testing "reset-token password changes revoke sessions and consume the reset token"
      (let [projected (read-models/users*
                       initial
                       {:event/type :user/password-reset
                        :user-id user-id
                        :password "reset-password"
                        :token-version 3})]
        (is (= "reset-password" (get-in projected [user-id :user/password])))
        (is (= 3 (get-in projected [user-id :user/token-version])))
        (is (nil? (get-in projected [user-id :user/pending-reset-token])))))))
