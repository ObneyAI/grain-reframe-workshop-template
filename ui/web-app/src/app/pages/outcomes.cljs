(ns app.pages.outcomes
  (:require [app.ui.interface :as ui]
            [uix.core :refer [defui $]]))

(defui session-loading-page []
  ($ :main {:class "grid min-h-screen place-items-center bg-background px-6 text-foreground"}
     ($ :div {:class "flex items-center gap-3 text-sm text-muted-foreground"
        :role "status"
        :aria-live "polite"}
        ($ :span {:class "size-4 animate-spin rounded-full border-2 border-muted-foreground/30 border-t-muted-foreground"})
        "Checking your session…")))

(defui not-found-page []
  ($ ui/app-shell {}
     ($ :div {:class "mx-auto max-w-xl py-20 text-center"}
        ($ :p {:class "text-sm font-medium text-muted-foreground"} "404")
        ($ :h1 {:class "mt-3 text-4xl font-semibold tracking-tight"} "Page not found")
        ($ :p {:class "mt-4 text-muted-foreground"}
           "That address does not match a page in this application.")
        ($ :a {:class "mt-7 inline-flex h-9 items-center justify-center rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/80"
               :href "/"}
           "Return home"))))

(defui forbidden-page []
  ($ ui/app-shell {}
     ($ :div {:class "mx-auto max-w-xl py-20 text-center"}
        ($ :p {:class "text-sm font-medium text-muted-foreground"} "403")
        ($ :h1 {:class "mt-3 text-4xl font-semibold tracking-tight"} "Access denied")
        ($ :p {:class "mt-4 text-muted-foreground"}
           "You are signed in, but your account cannot access this page.")
        ($ :a {:class "mt-7 inline-flex h-9 items-center justify-center rounded-lg bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/80"
               :href "/"}
           "Return home"))))
