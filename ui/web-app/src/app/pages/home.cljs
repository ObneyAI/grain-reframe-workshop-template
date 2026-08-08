(ns app.pages.home
  (:require [app.auth.interface :as auth]
            [app.ui.interface :as ui]
            ["@grain/shadcn" :refer [Button]]
            [uix.core :refer [defui $]]))

(defui page []
  (let [user (auth/use-user)]
    ($ ui/app-shell {:title "A clean Grain canvas"}
       ($ :div {:class "grid gap-6 lg:grid-cols-[1.4fr_1fr]"}
          ($ ui/surface {}
             ($ :div {:class "mb-4 inline-flex rounded-full border border-border bg-muted px-2.5 py-1 text-xs font-medium"}
                "UIx + Re-frame + shadcn")
             ($ :h2 {:class "max-w-xl text-4xl font-semibold leading-tight tracking-tight"}
                "Event-sourced on the server. Calm and explicit in the browser.")
             ($ :p {:class "mt-4 max-w-2xl text-lg text-muted-foreground"}
                "The protected application shell is ready for commands, read models, and deliberate feature states.")
             ($ :div {:class "mt-7 flex flex-wrap gap-3"}
                ($ :a {:class "inline-flex h-8 items-center justify-center rounded-lg bg-primary px-2.5 text-sm font-medium text-primary-foreground hover:bg-primary/80"
                       :href "/examples/questionnaire"}
                   "Open questionnaire example")
                ($ :a {:class "inline-flex h-8 items-center justify-center rounded-lg px-2.5 text-sm font-medium hover:bg-muted"
                       :href "/examples/routes?record-id=example-record&tab=summary"}
                   "Test a query-string route")))
          ($ ui/surface {}
             ($ :h3 {:class "text-lg font-semibold"} "Session")
             ($ :div {:class "mt-5 space-y-2"}
                ($ :div {:class "inline-flex rounded-full border border-border px-2.5 py-1 text-xs font-medium"}
                   "Authenticated")
                ($ :p {:class "font-medium"} (:user/email-address user))
                ($ :p {:class "text-sm text-muted-foreground"}
                   (if (:user/email-verified user) "Email verified" "Email verification pending"))
                ($ Button {:className "mt-3" :variant "outline" :onClick auth/logout!}
                   "Sign out")))))))
