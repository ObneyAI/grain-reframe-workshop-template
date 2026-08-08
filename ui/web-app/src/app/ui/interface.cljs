(ns app.ui.interface
  "Reusable UIx/shadcn primitives for the starter. Feature pages compose
   these instead of inventing one-off shells and feedback treatments."
  (:require [app.anomalies :as anomaly]
            [app.auth.interface :as auth]
            [app.form.interface :as form]
            [clojure.string :as string]
            ["@grain/shadcn" :refer [Button Card Input]]
            [uix.core :as uix :refer [defui $]]))

(defui app-shell
  "Generic authenticated application frame. Cloned apps supply navigation and
   page actions as UIx values without replacing the session-aware shell."
  [{:keys [title navigation actions children]}]
  (let [authenticated? (auth/use-authenticated?)
        user (auth/use-user)]
    ($ :div {:class "min-h-screen bg-muted/30 text-foreground"}
       ($ :header {:class "border-b border-border bg-background/95"}
          ($ :div {:class "mx-auto flex h-16 max-w-6xl items-center gap-6 px-4"}
             ($ :div {:class "flex-none"}
                ($ :a {:href "/" :class "text-lg font-semibold tracking-tight"}
                   "Grain Re-frame Workshop Template"))
             (when navigation
               ($ :nav {:class "min-w-0 flex-1" :aria-label "Primary"}
                  navigation))
             ($ :div {:class "ml-auto flex flex-none items-center gap-3"}
                (when authenticated?
                  ($ :span {:class "hidden text-sm text-muted-foreground sm:inline"}
                     (:user/email-address user)))
                (if authenticated?
                  ($ Button {:variant "ghost" :size "sm" :onClick auth/logout!} "Sign out")
                  ($ :a {:class "inline-flex h-7 items-center justify-center rounded-lg bg-primary px-2.5 text-sm font-medium text-primary-foreground hover:bg-primary/80"
                         :href "/auth/sign-in"} "Sign in")))))
       ($ :main {:class "mx-auto w-full max-w-6xl px-4 py-10"}
          (when (or title actions)
            ($ :div {:class "mb-6 flex flex-wrap items-center justify-between gap-4"}
               (when title
                 ($ :h1 {:class "text-3xl font-semibold tracking-tight"} title))
               (when actions
                 ($ :div {:class "flex items-center gap-2"} actions))))
          children))))

(defui surface [{:keys [class children]}]
  ($ Card {:className (str "p-6 shadow-sm " class)}
     children))

(defui auth-layout [{:keys [title description children]}]
  ($ :div {:class "mx-auto flex min-h-[70vh] w-full max-w-md items-center"}
     ($ surface {:class "w-full"}
        ($ :div {:class "mb-6"}
           ($ :h1 {:class "text-2xl font-semibold tracking-tight"} title)
           (when description
             ($ :p {:class "mt-2 text-sm text-muted-foreground"} description)))
        children)))

(defui field
  "Accessible controlled text field with stable label, description, required,
   and field-error semantics. Existing auth callers keep required-by-default."
  [{:keys [id name label description error type value on-change autocomplete
           placeholder disabled required]}]
  (let [generated-id (string/replace (uix/use-id) #":" "")
        field-id (or id (str "field-" generated-id))
        description-id (when description (str field-id "-description"))
        error-id (when error (str field-id "-error"))
        described-by (->> [description-id error-id]
                          (remove nil?)
                          (string/join " "))
        required? (if (nil? required) true required)]
    ($ :div {:class "grid w-full gap-2"}
       ($ :label {:class "text-sm font-medium" :htmlFor field-id}
          label
          (when required?
            ($ :span {:class "ml-1 text-destructive" :aria-hidden true} "*")))
       (when description
         ($ :p {:id description-id :class "text-sm text-muted-foreground"}
            description))
       ($ Input {:id field-id
                 :name name
                 :type (or type "text")
                 :value value
                 :autoComplete autocomplete
                 :placeholder placeholder
                 :disabled disabled
                 :required required?
                 :aria-describedby (when-not (string/blank? described-by) described-by)
                 :aria-invalid (boolean error)
                 :onChange #(on-change (.. % -target -value))})
       (when error
         ($ :p {:id error-id :class "text-sm text-destructive"} error)))))

(defui error-summary
  "Summarize structured server validation, link each message to its field, and
   focus the first invalid field after feedback is committed."
  [{:keys [error field-order field-labels field-ids title focus-first?]
    :or {title "Please fix the following fields" focus-first? true}}]
  (let [errors (form/field-errors error)
        fields (form/ordered-error-fields (or field-order []) errors)
        first-field (first fields)
        first-id (when first-field
                   (get field-ids first-field (name first-field)))]
    (uix/use-effect
     (fn []
       (when (and focus-first? first-field)
         (form/focus-field! first-id)))
     [focus-first? first-field first-id])
    (when (seq fields)
      ($ :div {:class "rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive"
               :role "alert"
               :aria-live "assertive"}
         ($ :p {:class "font-medium"} title)
         ($ :ul {:class "mt-2 list-disc space-y-1 pl-5"}
            (for [field-key fields
                  :let [field-id (get field-ids field-key (name field-key))
                        field-label (get field-labels field-key (name field-key))]]
              ($ :li {:key (str field-key)}
                 ($ :a {:class "underline underline-offset-2" :href (str "#" field-id)}
                    (str field-label ": " (get errors field-key))))))))))

(defui form-feedback
  "Consistent general failure/success feedback. Field failures use error-summary."
  [{:keys [error notice]}]
  (let [error-message (if (map? error) (::anomaly/message error) error)]
    ($ :<>
       (when error-message
         ($ :div {:class "rounded-lg border border-destructive/30 bg-destructive/10 px-4 py-3 text-sm text-destructive"
                  :role "alert"}
            error-message))
       (when notice
         ($ :div {:class "rounded-lg border border-border bg-muted px-4 py-3 text-sm"
                  :role "status"}
            notice)))))

(defui feedback []
  ($ form-feedback {:error (auth/use-error)
                    :notice (auth/use-notice)}))

(defui submit-button [{:keys [busy? disabled? label busy-label]}]
  ($ Button {:className "w-full"
             :type "submit"
             :disabled (or busy? disabled?)
             :aria-busy (boolean busy?)}
     (when busy? ($ :span {:class "size-4 animate-spin rounded-full border-2 border-primary-foreground/30 border-t-primary-foreground"}))
     (if busy? busy-label label)))
