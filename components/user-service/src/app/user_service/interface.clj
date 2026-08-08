(ns app.user-service.interface
  "Public interface for the user-service component.

   Requiring this namespace registers the service's schemas, commands,
   queries, read-models, todo-processors, and event model with Grain's global
   registries."
  (:require [app.user-service.interface.schemas]
            [app.user-service.core.commands]
            [app.user-service.core.queries]
            [app.user-service.core.todo-processors]
            [app.user-service.core.read-models :as rm]
            ;; Registers the :user (defeventmodel) area so the boot-guard can
            ;; reconcile it against the live runtime.
            [app.user-service.interface.event-model]))

;; Re-export read-model helpers as the stable public API.
(defn all-users [context] (rm/all-users context))
(defn user [context user-id] (rm/user context user-id))
(defn user-by-email [context email-address] (rm/user-by-email context email-address))
(defn email-addresses [context] (rm/email-addresses context))
(defn token-version [context user-id] (rm/token-version context user-id))
(defn normalize-email [email-address] (rm/normalize-email email-address))
