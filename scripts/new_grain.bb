#!/usr/bin/env bb
;; Scaffold a Grain service and its Re-frame page.
;;
;;   bb scripts/new_grain.bb service <name>
;;   bb scripts/new_grain.bb page <name>
;;
;; The service generator emits an internally consistent command -> event ->
;; read-model -> query slice plus a UIx/Re-frame page. It never passes an API
;; client through event vectors; app.api.interface owns that seam.

(ns new-grain
  (:require [babashka.fs :as fs]
            [clojure.string :as str]))

(defn kebab [value]
  (-> value
      (str/replace #"_" "-")
      (str/replace #"([a-z0-9])([A-Z])" "$1-$2")
      str/lower-case))

(defn snake [value] (str/replace (kebab value) #"-" "_"))
(defn title-case [value]
  (->> (str/split (kebab value) #"-") (map str/capitalize) (str/join " ")))

(defn render [template {:keys [svc title area]}]
  (-> template
      (str/replace "{{svc}}" svc)
      (str/replace "{{Title}}" title)
      (str/replace "{{area}}" area)))

(defn write! [path content]
  (fs/create-dirs (fs/parent path))
  (spit path content)
  (println "  +" path))

(def component-deps
  "{:paths [\"src\"]\n :deps {}\n :aliases {:test {:extra-paths [\"test\"] :extra-deps {}}}}\n")

(def interface-template
  "(ns app.{{svc}}.interface
  (:require [app.{{svc}}.interface.schemas]
            [app.{{svc}}.core.commands]
            [app.{{svc}}.core.queries]
            [app.{{svc}}.core.todo-processors]
            [app.{{svc}}.core.read-models :as read-models]
            [app.{{svc}}.interface.event-model]))

(defn all-items [context] (read-models/all-items context))
(defn item [context item-id] (read-models/item context item-id))
")

(def schemas-template
  "(ns app.{{svc}}.interface.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas event-schemas
  {{{area}}/item-created [:map [:item-id :uuid] [:name :string]]})

(defschemas command-schemas
  {{{area}}/create-item
   [:map [:name [:string {:min 1 :error/message \"Name is required\"}]]]})

(defschemas query-schemas
  {{{area}}/index [:map]})

(defschemas read-model-schemas
  {{{area}}/items
   [:map-of :uuid [:map [:item-id :uuid] [:name :string]]]})
")

(def read-models-template
  "(ns app.{{svc}}.core.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(def item-event-types #{{{area}}/item-created})

(defmulti items* (fn [_state event] (:event/type event)))
(defmethod items* {{area}}/item-created
  [state {:keys [item-id name]}]
  (assoc state item-id {:item-id item-id :name name}))
(defmethod items* :default [state _event] state)

(defreadmodel {{area}} items
  {:events item-event-types :version 1}
  [state event]
  (items* state event))

(defn all-items [context] (rmp/project context {{area}}/items))
(defn item [context item-id] (get (all-items context) item-id))
")

(def commands-template
  "(ns app.{{svc}}.core.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [clojure.string :as string]
            [cognitect.anomalies :as anomaly]))

(defcommand {{area}} create-item
  {:authorized? (constantly true)
   :grain.event-model/reads #{}
   :grain.event-model/produces #{{{area}}/item-created}}
  [{{:keys [name]} :command}]
  (if (string/blank? name)
    {::anomaly/category ::anomaly/incorrect
     ::anomaly/message \"Name is required.\"}
    (let [item-id (random-uuid)]
      {:command-result/events
       [(->event {:type {{area}}/item-created
                  :tags #{[:item item-id]}
                  :body {:item-id item-id :name name}})]
       :command/result {:item-id item-id}})))
")

(def queries-template
  "(ns app.{{svc}}.core.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [app.{{svc}}.core.read-models :as read-models]))

(defquery {{area}} index
  {:authorized? (constantly true)
   :grain.event-model/reads #{{{area}}/items}}
  [context]
  {:query/result
   {:items (->> (read-models/all-items context) vals (sort-by :name) vec)}})
")

(def todo-template
  "(ns app.{{svc}}.core.todo-processors
  \"Register event-driven side effects here and model each one in event_model.clj.\")
")

(def event-model-template
  "(ns app.{{svc}}.interface.event-model
  (:require [ai.obney.grain.event-model.interface :refer [defeventmodel]]))

(defeventmodel {{area}}
  {:description \"{{Title}} creates items and projects them for browsing.\"
   :commands
   {{{area}}/create-item
    {:description \"Creates an item.\"
     :schema [:map [:name [:string {:min 1 :error/message \"Name is required\"}]]]
     :reads #{}
     :produces #{{{area}}/item-created}
     :given-when-thens
     [{:given \"a non-blank name\" :when \"create-item\" :then \"item-created is recorded\"}
      {:given \"a blank name\" :when \"create-item\" :then \"the command is rejected\"}]}}
   :events
   {{{area}}/item-created
    {:description \"An item was created.\"
     :schema [:map [:item-id :uuid] [:name :string]]}}
   :read-models
   {{{area}}/items
    {:description \"All created items.\"
     :consumes #{{{area}}/item-created}
     :version 1}}
   :queries
   {{{area}}/index
    {:description \"Returns all items for the frontend.\"
     :schema [:map]
     :reads #{{{area}}/items}}}
   :todo-processors {}
   :screens
   {{{area}}/index
    {:description \"{{Title}} list and create form.\"
     :queries #{{{area}}/index}
     :commands #{{{area}}/create-item}}}})
")

(def test-template
  "(ns app.{{svc}}.core.read-models-test
  (:require [app.{{svc}}.core.read-models :as read-models]
            [clojure.test :refer [deftest is]]))

(deftest item-created-projects
  (let [item-id (random-uuid)
        state (read-models/items* {} {:event/type {{area}}/item-created
                                      :item-id item-id
                                      :name \"Example\"})]
    (is (= \"Example\" (get-in state [item-id :name])))))
")

(def page-template
  "(ns app.pages.{{svc}}
  (:require [app.api.interface :as api]
            [app.ui.interface :as ui]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [uix.core :as uix :refer [defui $]]))

(rf/reg-sub ::state (fn [db _] (get db {{area}} {:items [] :loading? true})))
(rf/reg-event-fx
 ::load
 (fn [{:keys [db]} _]
   (merge {:db (assoc-in db [{{area}} :loading?] true)}
          (api/query {:name {{area}}/index
                      :on-success [::loaded]
                      :on-failure [::failed]}))))
(rf/reg-event-db ::loaded
 (fn [db [_ result]] (assoc db {{area}} (assoc result :loading? false :error nil))))
(rf/reg-event-db ::failed
 (fn [db [_ error]] (assoc db {{area}} {:items [] :loading? false :error error})))
(rf/reg-event-fx
 ::create
 (fn [_ [_ name]]
   (api/command {:name {{area}}/create-item
                 :params {:name name}
                 :on-success [::created]
                 :on-failure [::failed]})))
(rf/reg-event-fx ::created (fn [_ _] {:dispatch [::load]}))

(defui page []
  (let [[name set-name!] (uix/use-state \"\")
        {:keys [items loading? error]} (use-subscribe [::state])]
    (uix/use-effect (fn [] (rf/dispatch [::load])) [])
    ($ ui/app-shell {:title \"{{Title}}\"}
       ($ ui/surface {}
          ($ :form {:class \"mb-5 flex gap-2\"
                    :on-submit (fn [event]
                                 (.preventDefault event)
                                 (rf/dispatch [::create name])
                                 (set-name! \"\"))}
             ($ :input {:class \"input input-bordered flex-1\"
                        :value name
                        :placeholder \"Name\"
                        :on-change #(set-name! (.. % -target -value))})
             ($ :button {:class \"btn btn-primary\" :type \"submit\"} \"Add\"))
          (cond
            loading? ($ :span {:class \"loading loading-spinner\"})
            error ($ :div {:class \"alert alert-error\"} \"Could not load items.\")
            (seq items) ($ :ul {:class \"divide-y divide-base-300\"}
                           (for [{:keys [item-id name]} items]
                             ($ :li {:key (str item-id) :class \"py-3\"} name)))
            :else ($ :p {:class \"py-8 text-center text-base-content/60\"}
                     \"Nothing yet—add the first item.\"))))))
")

(def static-page-template
  "(ns app.pages.{{svc}}
  (:require [app.ui.interface :as ui]
            [uix.core :refer [defui $]]))

(defui page []
  ($ ui/app-shell {:title \"{{Title}}\"}
     ($ ui/surface {}
        ($ :p {:class \"py-8 text-center text-base-content/60\"}
           \"Build this page.\"))))
")

(defn context [name]
  {:svc (kebab name) :title (title-case name) :area (str ":" (kebab name))})

(defn new-service [name]
  (let [{:keys [svc] :as ctx} (context name)
        source-name (snake name)
        base (str "components/" svc)
        source (str base "/src/app/" source-name)
        test-source (str base "/test/app/" source-name)]
    (when (fs/exists? base)
      (println "! component already exists:" base)
      (System/exit 1))
    (println "Scaffolding service:" svc)
    (write! (str base "/deps.edn") component-deps)
    (write! (str source "/interface.clj") (render interface-template ctx))
    (write! (str source "/interface/schemas.clj") (render schemas-template ctx))
    (write! (str source "/interface/event_model.clj") (render event-model-template ctx))
    (write! (str source "/core/read_models.clj") (render read-models-template ctx))
    (write! (str source "/core/commands.clj") (render commands-template ctx))
    (write! (str source "/core/queries.clj") (render queries-template ctx))
    (write! (str source "/core/todo_processors.clj") (render todo-template ctx))
    (write! (str test-source "/core/read_models_test.clj") (render test-template ctx))
    (write! (str "ui/web-app/src/app/pages/" source-name ".cljs") (render page-template ctx))
    (println)
    (println "Wire the generated slice:")
    (println (str "  root deps.edn: poly/" svc " {:local/root \"" base "\"}"))
    (println (str "  web-api require: [app." svc ".interface]"))
    (println "  add the route, page-components entry, and direct-load path in app.web-api.core/spa-paths")
    (println (str "  load live: (app.web-api.core/load-component! \"" svc "\")"))
    (println "  specify behaviour in an Allium file, add trace links, then run ./scripts/verify-specs.sh")))

(defn new-page [name]
  (let [{:keys [svc] :as ctx} (context name)
        path (str "ui/web-app/src/app/pages/" (snake name) ".cljs")]
    (when (fs/exists? path)
      (println "! page already exists:" path)
      (System/exit 1))
    (write! path (render static-page-template ctx))
    (println "Add the client route, page mapping, and direct-load path in app.web-api.core/spa-paths.")))

(defn -main [& args]
  (case (first args)
    "service" (if-let [name (second args)] (new-service name) (System/exit 1))
    "page" (if-let [name (second args)] (new-page name) (System/exit 1))
    (do
      (println "Usage:")
      (println "  bb scripts/new_grain.bb service <name>")
      (println "  bb scripts/new_grain.bb page <name>")
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
