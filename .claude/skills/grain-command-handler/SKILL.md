---
name: grain-command-handler
description: Implement Grain command handlers for this Re-frame starter.
---

# Grain command handler

Read `CLAUDE.md`, the owning component's schemas, event model, read models, and a working command first.

Use `defcommand :area name` with explicit `:authorized?`, `:grain.event-model/reads`, and
`:grain.event-model/produces`. Validate business rules before emitting events. Success returns
`:command-result/events` and may return serializable data under `:command/result`; it never returns UI,
browser redirects, or frontend state. Failures are Cognitect anomalies with a useful message and field
explanation when applicable.

Account-scoped mutations use `app.auth.interface/authenticated?` or a stricter predicate and derive the
UUID identity with `auth/auth-user-id`. Keep the schemas and event model exact, test observable results,
then validate and invoke through code-agent-tools before exercising the Re-frame flow.
