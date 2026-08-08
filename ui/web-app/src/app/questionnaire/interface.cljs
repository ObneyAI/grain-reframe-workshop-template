(ns app.questionnaire.interface
  "Re-frame seam for values emitted by React-owned shadcn interactions."
  (:require [app.questionnaire.events :as events]
            [app.questionnaire.subs :as subs]
            [app.re-frame.interface :refer [use-subscribe]]
            [re-frame.core :as rf]))

(defn submit! [js-answers]
  (rf/dispatch [::events/submitted (js->clj js-answers :keywordize-keys true)]))

(defn use-answers []
  (use-subscribe [::subs/answers]))
