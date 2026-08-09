(ns app.customer.components
  (:require [app.customer.interface :as customer]
            [app.ui.interface :as ui]
            ["@grain/shadcn" :refer [Badge Button Combobox ComboboxContent
                                     ComboboxEmpty ComboboxInput ComboboxItem
                                     ComboboxList DropdownMenu
                                     DropdownMenuContent DropdownMenuItem
                                     DropdownMenuTrigger Sheet
                                     SheetContent SheetDescription SheetHeader
                                     SheetTitle Table TableBody TableCell
                                     TableHead TableHeader TableRow Tabs
                                     TabsContent TabsList TabsTrigger]]
            [uix.core :as uix :refer [defui $]]))

(def status-options
  [{:value :lead :label "Lead"}
   {:value :active :label "Active"}
   {:value :inactive :label "Inactive"}])

(defn- status-label [status]
  (some #(when (= status (:value %)) (:label %)) status-options))

(defui status-badge [{:keys [status]}]
  ($ Badge {:variant (case status :active "default" :inactive "secondary" "outline")}
     (or (status-label status) "Unknown")))

(defui status-menu [{:keys [customer-id current on-change]}]
  ($ DropdownMenu
     ($ DropdownMenuTrigger
        {:class "inline-flex h-8 items-center rounded-lg border border-input px-2.5 text-sm font-medium hover:bg-muted"
         :onClick #(.stopPropagation %)}
        "Change status")
     ($ DropdownMenuContent {:align "end"}
        (for [{:keys [label value]} status-options]
          ($ DropdownMenuItem
             {:key (name value)
              :disabled (= value current)
              :onClick (fn [event]
                         (.stopPropagation event)
                         (on-change customer-id value))}
             label)))))

(defui workbench-intro []
  ($ ui/surface {:class "border-dashed bg-background/60"}
     ($ :div {:class "flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between"}
        ($ :div
           ($ :p {:class "text-sm font-medium"} "Disposable architecture example")
           ($ :p {:class "mt-1 max-w-3xl text-sm text-muted-foreground"}
              "Every change below travels through a Grain command, event, projection, query resource, and Re-frame subscription. Replace this vocabulary after your clone has equivalent coverage."))
        ($ Badge {:variant "outline"} "Event-sourced"))))

(defui create-customer [{:keys [filters route-query]}]
  (let [[open? set-open!] (uix/use-state false)
        [name set-name!] (uix/use-state "")
        [email set-email!] (uix/use-state "")
        request (customer/use-create-state)
        busy? (= :pending (:status request))]
    ($ :<>
       ($ Button {:onClick #(set-open! true)} "New customer")
       ($ Sheet {:open open? :onOpenChange set-open!}
          ($ SheetContent
             ($ SheetHeader
                ($ SheetTitle "Create a customer")
                ($ SheetDescription "This records customer-created and refreshes the keyed index resource."))
             ($ :form
                {:class "space-y-5 px-4"
                 :onSubmit (fn [event]
                             (.preventDefault event)
                             (set-open! false)
                             (customer/create! {:name name :email-address email}
                                               filters route-query))}
                ($ ui/field {:label "Name" :name "name" :value name :on-change set-name!
                             :placeholder "Northstar Studio"})
                ($ ui/field {:label "Email" :name "email-address" :type "email"
                             :value email :on-change set-email! :placeholder "hello@example.com"})
                ($ ui/form-feedback {:error (:error request)})
                ($ Button {:type "submit" :disabled busy? :className "w-full"}
                   (if busy? "Creating…" "Create customer"))))))))

(defui index-controls [{:keys [filters on-change]}]
  ($ :div {:class "flex flex-col gap-3 sm:flex-row"}
     ($ :div {:class "grid gap-1.5"}
        ($ :label {:class "text-xs font-medium text-muted-foreground"} "Status")
        ($ Combobox
           {:value (if-let [status (:status filters)] (name status) "all")
            :onValueChange #(on-change (assoc filters :status
                                              (when-not (= % "all") (keyword %))))}
           ($ ComboboxInput {:className "w-48" :placeholder "All statuses"})
           ($ ComboboxContent
              ($ ComboboxList
                 ($ ComboboxItem {:value "all"} "All statuses")
                 (for [{:keys [label value]} status-options]
                   ($ ComboboxItem {:key (name value) :value (name value)} label)))
              ($ ComboboxEmpty "No status found."))))
     ($ :div {:class "grid gap-1.5"}
        ($ :label {:class "text-xs font-medium text-muted-foreground"} "Sort")
        ($ :select
           {:class "h-8 rounded-lg border border-input bg-background px-2.5 text-sm"
            :value (name (:sort filters))
            :onChange #(on-change (assoc filters :sort (keyword (.. % -target -value))))}
           ($ :option {:value "name-asc"} "Name A–Z")
           ($ :option {:value "name-desc"} "Name Z–A")))))

(defui customer-index [{:keys [state on-retry on-select on-status]}]
  (let [customers (get-in state [:data :customers])]
    ($ ui/surface {:class "overflow-hidden p-0"}
       (cond
         (and (:loading? state) (nil? (:data state)))
         ($ :div {:class "p-10 text-center text-sm text-muted-foreground"} "Loading customers…")

         (:error state)
         ($ :div {:class "space-y-3 p-10 text-center"}
            ($ :p {:class "text-sm text-destructive"} "The customer query failed.")
            ($ Button {:variant "outline" :onClick on-retry} "Retry query"))

         (empty? customers)
         ($ :div {:class "p-10 text-center"}
            ($ :p {:class "font-medium"} "No customers yet")
            ($ :p {:class "mt-1 text-sm text-muted-foreground"} "Create one to emit the example's first domain event."))

         :else
         ($ Table
            ($ TableHeader
               ($ TableRow
                  ($ TableHead "Customer") ($ TableHead "Status")
                  ($ TableHead {:className "hidden md:table-cell"} "Email")
                  ($ TableHead {:className "text-right"} "Action")))
            ($ TableBody
               (for [{:keys [customer-id email-address name status]} customers]
                 ($ TableRow {:key (str customer-id) :data-testid "customer-row"}
                    ($ TableCell
                       ($ :button {:class "font-medium hover:underline" :onClick #(on-select customer-id)} name))
                    ($ TableCell ($ status-badge {:status status}))
                    ($ TableCell {:className "hidden text-muted-foreground md:table-cell"} email-address)
                    ($ TableCell {:className "text-right"}
                       ($ status-menu {:customer-id customer-id :current status :on-change on-status}))))))))))

(defui customer-detail [{:keys [customer-id state tab on-close on-retry on-tab on-status]}]
  (let [projected (get-in state [:data :customer])
        optimistic (customer/use-optimistic-status customer-id)
        status (or optimistic (:status projected))]
    ($ Sheet {:open (boolean customer-id) :onOpenChange #(when-not % (on-close))}
       ($ SheetContent {:className "sm:max-w-lg"}
          (cond
            (and (:loading? state) (nil? projected))
            ($ :div {:class "p-6 text-sm text-muted-foreground"} "Loading projected detail…")

            (:error state)
            ($ :div {:class "space-y-3 p-6"}
               ($ :p {:class "text-sm text-destructive"} "The customer detail query failed.")
               ($ Button {:variant "outline" :onClick on-retry} "Retry query"))

            projected
            ($ :<>
               ($ SheetHeader
                  ($ :div {:class "pr-10"}
                     ($ SheetTitle (:name projected))
                     ($ SheetDescription (:email-address projected)))
                  ($ :div {:class "mt-3 flex items-center gap-2"}
                     ($ status-badge {:status status})
                     (when optimistic ($ :span {:class "text-xs text-muted-foreground"} "Saving…")))
                  ($ status-menu {:customer-id customer-id :current status
                                  :on-change (fn [_ next-status] (on-status next-status))}))
               ($ Tabs {:className "px-4" :value tab :onValueChange on-tab}
                  ($ TabsList
                     ($ TabsTrigger {:value "summary"} "Summary")
                     ($ TabsTrigger {:value "activity"} "Activity"))
                  ($ TabsContent {:value "summary" :className "pt-4"}
                     ($ :dl {:class "grid gap-3 text-sm"}
                        ($ :div ($ :dt {:class "text-muted-foreground"} "Customer ID")
                           ($ :dd {:class "mt-1 break-all font-mono text-xs"} (str customer-id)))
                        ($ :div ($ :dt {:class "text-muted-foreground"} "Current status")
                           ($ :dd {:class "mt-1 font-medium"} (status-label status)))))
                  ($ TabsContent {:value "activity" :className "pt-4"}
                     ($ :ol {:class "space-y-3"}
                        (map-indexed
                         (fn [index {:keys [type status]}]
                           ($ :li {:key index :class "rounded-lg border p-3 text-sm"}
                              ($ :p {:class "font-medium"}
                                 (if (= type :customer-created) "Customer created" "Status changed"))
                              ($ :p {:class "mt-1 text-muted-foreground"}
                                 (str "Projected status: " (status-label status)))))
                         (:activity projected)))))))))))
