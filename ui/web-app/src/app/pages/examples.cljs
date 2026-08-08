(ns app.pages.examples
  (:require [app.questionnaire.interface :as questionnaire]
            [app.router.interface :as router]
            [app.ui.interface :as ui]
            ["@grain/shadcn" :refer [StarterQuestionnaire]]
            [uix.core :refer [defui $]]))

(defui questionnaire-page []
  (let [answers (questionnaire/use-answers)]
    ($ ui/app-shell {:title "Questionnaire bridge"}
       ($ ui/surface {}
          ($ :div {:class "mb-6 max-w-2xl"}
             ($ :div {:class "text-sm font-medium text-muted-foreground"} "Starter example")
             ($ :h2 {:class "mt-2 text-2xl font-semibold tracking-tight"}
                "Plan the first tracer bullet")
             ($ :p {:class "mt-2 text-sm text-muted-foreground"}
                "This shadcn Base UI module owns transient interaction state and emits plain answers into Re-frame."))
          ($ StarterQuestionnaire {:className "max-w-2xl" :onSubmit questionnaire/submit!})
          (when answers
            ($ :pre {:class "mt-6 overflow-x-auto rounded-lg bg-muted p-4 text-xs"}
               (pr-str answers)))))))

(defui query-route-page []
  (let [match (router/current)
        record-id (get-in match [:query-params :record-id])
        tab (get-in match [:query-params :tab])]
    ($ ui/app-shell {:title "Query-string route"}
       ($ ui/surface {}
          ($ :p {:class "text-sm text-muted-foreground"}
             "This protected page keeps selection and view state in one query-parameter map across direct browser loads.")
          ($ :dl {:class "mt-6 grid gap-3 text-sm sm:grid-cols-[8rem_1fr]"}
             ($ :dt {:class "font-medium"} "Record ID")
             ($ :dd {:class "font-mono text-muted-foreground"} record-id)
             ($ :dt {:class "font-medium"} "Selected tab")
             ($ :dd {:class "font-mono text-muted-foreground"} (or tab "none")))))))
