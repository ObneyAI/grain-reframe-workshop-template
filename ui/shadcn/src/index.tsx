import * as React from "react"

import { ApplicationErrorBoundary } from "./application-error-boundary"
import { Badge } from "#components/ui/badge"
import { Button } from "#components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "#components/ui/card"
import { Input } from "#components/ui/input"
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
} from "#components/ui/combobox"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "#components/ui/dropdown-menu"
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "#components/ui/sheet"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "#components/ui/table"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "#components/ui/tabs"
import { Toaster, toast } from "#components/ui/toast"
import {
  Questionnaire,
  QuestionnaireActions,
  QuestionnaireChoice,
  QuestionnaireChoiceDescription,
  QuestionnaireChoices,
  QuestionnaireDescription,
  QuestionnaireError,
  QuestionnaireInput,
  QuestionnaireItem,
  QuestionnaireNext,
  QuestionnairePrevious,
  QuestionnaireProgress,
  QuestionnaireSkip,
  QuestionnaireSubmit,
  QuestionnaireTitle,
} from "#components/ui/questionnaire"

const starterItems = [
  {
    choices: [
      { value: "domain-slice" },
      { value: "workflow" },
      { value: "integration" },
    ],
    name: "direction",
    required: true,
  },
  {
    choices: [
      { value: "loading" },
      { value: "empty" },
      { value: "failure" },
      { value: "success" },
    ],
    name: "states",
  },
  {
    choices: [
      { value: "command" },
      { value: "query" },
      { value: "both" },
    ],
    name: "grain-path",
    required: true,
  },
] as const

export type StarterQuestionnaireAnswers = {
  direction: string | null
  states: string[]
  "grain-path": string | null
}

export type StarterQuestionnaireProps = {
  className?: string
  onSubmit?: (answers: StarterQuestionnaireAnswers) => void
}

export type NotificationOptions = {
  description?: string
  title: string
  type?: "error" | "info" | "loading" | "success" | "warning"
}

export function notify({ description, title, type = "info" }: NotificationOptions) {
  return toast.add({
    description,
    priority: type === "error" ? "high" : "low",
    title,
    type,
  })
}

export function StarterQuestionnaire({
  className,
  onSubmit,
}: StarterQuestionnaireProps) {
  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const formData = new FormData(event.currentTarget)

    onSubmit?.({
      direction: formData.get("direction")?.toString() ?? null,
      states: formData.getAll("states").map(String),
      "grain-path": formData.get("grain-path")?.toString() ?? null,
    })
  }

  return (
    <Questionnaire
      className={className}
      defaultItem="direction"
      items={starterItems}
      shortcuts="letters"
      onSubmit={handleSubmit}
    >
      <QuestionnaireProgress />
      <QuestionnaireItem name="direction" required>
        <QuestionnaireTitle>What should this Grain app prove first?</QuestionnaireTitle>
        <QuestionnaireDescription>
          Pick the first tracer bullet or write your own.
        </QuestionnaireDescription>
        <QuestionnaireChoices>
          <QuestionnaireChoice value="domain-slice">
            <span className="font-medium">A complete domain slice</span>
            <QuestionnaireChoiceDescription>
              Command, event, read model, query, and screen.
            </QuestionnaireChoiceDescription>
          </QuestionnaireChoice>
          <QuestionnaireChoice value="workflow">
            <span className="font-medium">A guided workflow</span>
            <QuestionnaireChoiceDescription>
              A multi-step experience with explicit transitions.
            </QuestionnaireChoiceDescription>
          </QuestionnaireChoice>
          <QuestionnaireChoice value="integration">
            <span className="font-medium">An external integration</span>
            <QuestionnaireChoiceDescription>
              Exercise a production adapter and its local stand-in.
            </QuestionnaireChoiceDescription>
          </QuestionnaireChoice>
          <QuestionnaireInput aria-label="Another direction" placeholder="Another direction…" />
        </QuestionnaireChoices>
        <QuestionnaireError />
      </QuestionnaireItem>

      <QuestionnaireItem name="states" multiple>
        <QuestionnaireTitle>Which UI states matter on day one?</QuestionnaireTitle>
        <QuestionnaireDescription>Select all that deserve an intentional design.</QuestionnaireDescription>
        <QuestionnaireChoices>
          <QuestionnaireChoice value="loading">Loading</QuestionnaireChoice>
          <QuestionnaireChoice value="empty">Empty</QuestionnaireChoice>
          <QuestionnaireChoice value="failure">Failure</QuestionnaireChoice>
          <QuestionnaireChoice value="success">Success</QuestionnaireChoice>
        </QuestionnaireChoices>
        <QuestionnaireError />
      </QuestionnaireItem>

      <QuestionnaireItem name="grain-path" required>
        <QuestionnaireTitle>Which Grain path should lead?</QuestionnaireTitle>
        <QuestionnaireDescription>The generated slice can include both.</QuestionnaireDescription>
        <QuestionnaireChoices>
          <QuestionnaireChoice value="command">Command-first</QuestionnaireChoice>
          <QuestionnaireChoice value="query">Query-first</QuestionnaireChoice>
          <QuestionnaireChoice value="both">Command and query together</QuestionnaireChoice>
        </QuestionnaireChoices>
        <QuestionnaireError />
      </QuestionnaireItem>

      <QuestionnaireActions>
        <QuestionnairePrevious />
        <QuestionnaireSkip />
        <QuestionnaireNext>Next</QuestionnaireNext>
        <QuestionnaireSubmit>Save answers</QuestionnaireSubmit>
      </QuestionnaireActions>
    </Questionnaire>
  )
}

export {
  ApplicationErrorBoundary,
  Badge,
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
  Input,
  Questionnaire,
  QuestionnaireActions,
  QuestionnaireChoice,
  QuestionnaireChoiceDescription,
  QuestionnaireChoices,
  QuestionnaireDescription,
  QuestionnaireError,
  QuestionnaireInput,
  QuestionnaireItem,
  QuestionnaireNext,
  QuestionnairePrevious,
  QuestionnaireProgress,
  QuestionnaireSkip,
  QuestionnaireSubmit,
  QuestionnaireTitle,
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Toaster,
}
