(ns app.customer.interface
  (:require [app.customer.interface.schemas]
            [app.customer.core.commands]
            [app.customer.core.queries]
            [app.customer.core.todo-processors]
            [app.customer.core.read-models :as read-models]
            [app.customer.interface.event-model]))

(defn all-customers [context] (read-models/all-customers context))
(defn customer [context customer-id] (read-models/customer context customer-id))
