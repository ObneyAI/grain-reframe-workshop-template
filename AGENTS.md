# AGENTS.md

This is the Grain Re-frame Workshop Template: a Polylith Grain backend and a UIx/Re-frame frontend. Read
`CLAUDE.md` before changing it; that file owns the stack, golden path, interfaces, and verification gate.

Quick contract:

- Specify behaviour in Allium, map topology with `defeventmodel`, then implement schema → command →
  read model → query → Re-frame event/effect/subscription → UIx page.
- Backend feature work belongs in a new Polylith service component. Frontend rendering belongs under
  `ui/web-app/src/app`; never add server-rendered page queries.
- Frontend modules call `app.api.interface/command` or `query`. Never construct HTTP requests in pages,
  and never pass an API client through Re-frame event vectors.
- Account workflows go through `app.auth.interface`. The browser cannot authorize itself: every
  account-scoped command/query also declares a real backend `:authorized?` predicate.
- UI is library-first. Add open-code components through the shadcn CLI, expose a small interface from
  `ui/shadcn/src/index.tsx`, and compose it through UIx/Re-frame. Deepen `app.ui.interface` when a
  ClojureScript-side pattern repeats.
- Keep Re-frame handlers pure. HTTP and browser effects live behind registered effects; app-db contains
  serializable data, not clients, callbacks, or React values.
- Application modules use the provider-neutral email, file-store, URL-presigner, crypto, and webhook
  interfaces. AWS, SMTP, and vendor request shapes stay inside adapters; tests use local adapters.
- When asked to start the app, use `./scripts/dev up`, confirm with `./scripts/dev status`, and report its
  application URL. Use `./scripts/dev logs` to diagnose startup and `./scripts/dev down` to stop it.
- Run `./scripts/verify-specs.sh` before reporting success. For routing, auth, or shadcn changes, also run
  `npm run test:browser`; be explicit about any failing gate.
