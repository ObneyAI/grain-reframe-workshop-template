import * as React from "react"

import { Button } from "#components/ui/button"

type ApplicationErrorBoundaryProps = {
  children: React.ReactNode
}
type ApplicationErrorBoundaryState = {
  failed: boolean
}

export class ApplicationErrorBoundary extends React.Component<
  ApplicationErrorBoundaryProps,
  ApplicationErrorBoundaryState
> {
  state: ApplicationErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): ApplicationErrorBoundaryState {
    return { failed: true }
  }

  render() {
    if (!this.state.failed) return this.props.children

    return (
      <main className="grid min-h-screen place-items-center bg-background px-6 text-foreground">
        <section className="w-full max-w-lg rounded-xl border border-border bg-card p-8 shadow-sm">
          <p className="text-sm font-medium text-muted-foreground">Application error</p>
          <h1 className="mt-3 text-3xl font-semibold tracking-tight">
            This page could not be rendered
          </h1>
          <p className="mt-4 text-sm leading-6 text-muted-foreground">
            Try rendering the page again. If the problem continues, reload the application.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <Button type="button" onClick={() => this.setState({ failed: false })}>
              Try again
            </Button>
            <Button type="button" variant="outline" onClick={() => window.location.reload()}>
              Reload application
            </Button>
          </div>
        </section>
      </main>
    )
  }
}
