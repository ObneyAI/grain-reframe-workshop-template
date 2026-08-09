(ns app.core
  (:require [app.api.interface :as api]
            [app.auth.interface :as auth]
            [app.config :as config]
            [app.router.core :as router]
            [app.router.interface :as router-interface]
            [app.store :as store]
            [re-frame.core :as rf]
            ["@grain/shadcn" :refer [ApplicationErrorBoundary Toaster]]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]))

(defui app []
  ($ :<>
     ($ ApplicationErrorBoundary
        ($ router/router-outlet))
     ($ Toaster)))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn ^:dev/after-load render []
  (uix.dom/render-root
   ($ uix/strict-mode ($ app))
   root))

(defn ^:export init []
  (api/configure! (api/remote-client {:base-url (config/api-base-url)}))
  (rf/dispatch-sync [::store/initialize])
  (router-interface/start!)
  (auth/initialize!)
  (render))
