(ns app.form.interface-test
  (:require [app.form.interface :as form]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest normalizes-structured-field-errors
  (testing "one useful message is retained for each field"
    (is (= {:email-address "Email is invalid"
            :password "Password is too short"
            :profile "Profile is invalid"}
           (form/field-errors
            {:error/explain
             {:email-address ["Email is invalid" "Email is already used"]
              :password "Password is too short"
              :profile {:message "Profile is invalid"}
              :ignored nil}}))))

  (testing "the original HTTP response remains a compatibility fallback"
    (is (= {:name "Name is required"}
           (form/field-errors
            {:http/response {:body {:error/explain {:name ["Name is required"]}}}}))))

  (testing "missing explanation data is an empty field map"
    (is (= {} (form/field-errors {:message "General failure"})))))

(deftest orders-errors-for-summary-and-focus
  (is (= [:email :password :server-only]
         (form/ordered-error-fields
          [:email :password :confirmation]
          {:server-only "Unexpected"
           :password "Too short"
           :email "Required"}))))
