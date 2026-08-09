(ns app.router.interface
  (:require [app.router.runtime :as runtime]
            [re-frame.core :as rf]))

(rf/reg-fx
 ::navigate
 (fn [{:keys [query-params route]}]
   (runtime/navigate! route query-params)))

(def current-match runtime/current-match)
(defn current [] @runtime/current-match)
(defn start! [] (runtime/start!))
(defn match-token [token] (runtime/match-token token))
(defn route-decision [match auth-status] (runtime/route-decision match auth-status))
(defn safe-return-path [candidate] (runtime/safe-return-path candidate))
(defn navigate-path! [path] (runtime/navigate-path! path))
(defn navigate!
  ([route-name] (runtime/navigate! route-name))
  ([route-name query-params] (runtime/navigate! route-name query-params)))

(defn navigation-effect
  ([route] (navigation-effect route {}))
  ([route query-params]
   {::navigate {:route route :query-params query-params}}))
