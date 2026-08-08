(ns app.clock.interface-test
  (:require [app.clock.interface :as clock]
            [app.config :as config]
            [cljs.test :refer-macros [deftest is testing]]))

(deftest fixed-clock-is-deterministic-and-defensive
  (let [subject (clock/fixed-clock "2026-01-15T12:30:00Z")
        first-now (clock/now subject)
        second-now (clock/now subject)]
    (is (= "2026-01-15T12:30:00.000Z" (.toISOString first-now)))
    (is (not (identical? first-now second-now)))
    (.setUTCFullYear first-now 2030)
    (is (= "2026-01-15T12:30:00.000Z" (.toISOString (clock/now subject))))))

(deftest invalid-fixed-instants-fail-at-the-interface
  (is (thrown-with-msg? js/Error #"requires a valid Date" (clock/fixed-clock "not-a-date"))))

(deftest date-presentation-uses-explicit-application-settings
  (testing "tests without a browser document use the declared safe defaults"
    (is (= "en-US" (config/locale)))
    (is (= "UTC" (config/time-zone))))
  (is (= "01/15/2026, 12:30 PM"
         (clock/format-date-time
          "2026-01-15T12:30:00Z"
          {:year "numeric" :month "2-digit" :day "2-digit"
           :hour "2-digit" :minute "2-digit" :hour12 true}))))
