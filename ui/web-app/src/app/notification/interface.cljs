(ns app.notification.interface
  "One Re-frame effect for application notifications. Feature event handlers
   describe feedback as data; the shadcn toast manager owns rendering."
  (:require [re-frame.core :as rf]
            ["@grain/shadcn" :refer [notify]]))

(rf/reg-fx
 ::notify
 (fn [options]
   (notify (clj->js options))))

(defn effect
  [options]
  {::notify options})
