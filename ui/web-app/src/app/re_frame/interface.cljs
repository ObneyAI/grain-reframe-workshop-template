(ns app.re-frame.interface
  "UIx adapter for si-frame subscriptions.

   si-frame 1.4.3's re-frame.uix hook creates its subscription under a nil
   Signaali observer. That is intentionally detached, but it also triggers
   re-frame's development warning for every subscription. This adapter keeps
   the same useSyncExternalStore behavior while using a non-owning observer."
  (:require ["react" :as react]
            [re-frame.core :as rf]
            [signaali.reactive :as sr]
            [uix.core :as uix]))

(def detached-observer
  (reify
    sr/IRunObserver
    (notify-deref-on-signal-source [_ _])
    (add-clean-up-callback [_ _])))

(defn use-subscribe
  [query]
  (let [[subscribe get-snapshot]
        (uix/use-memo
         (fn []
           (let [subscription (sr/with-observer detached-observer
                                #(rf/subscribe query))]
             [(fn [notify-change]
                (let [watcher
                      (reify
                        sr/ISignalWatcher
                        (notify-signal-watcher [_ _ _]
                          (notify-change)))]
                  (sr/add-signal-watcher subscription watcher)
                  #(sr/remove-signal-watcher subscription watcher)))
              #(deref subscription)]))
         [query])]
    (react/useSyncExternalStore subscribe get-snapshot)))
