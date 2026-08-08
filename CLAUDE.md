# Grain Re-frame Workshop Template — agent guide

This is a clean-canvas Polylith Grain application. The backend is event-sourced Clojure; the browser is
a UIx/React application whose state and effects are organized with Re-frame.

## The charter

Every app built from this starter must be correct and idiomatic, built to the brief, reachable at `/`,
secure where data is scoped, visually intentional, verified, and honestly reported.

## Stack and layout

- Grain CQRS, Clojure 1.12, Polylith, top namespace `app`.
- SQLite events in `storage/events.db`; LMDB projections in `storage/cache`.
- UIx + React, Re-frame, Reitit/Pushy, Tailwind CSS 4, shadcn Base UI, Shadow CLJS.
- The backend serves the compiled SPA and `/command` + `/query` from one origin.

```text
bases/web-api/src/app/web_api/core.clj       Integrant system, auth interceptors, routes, SPA fallback
components/auth                              cookie/claim seam
components/config                            validated runtime configuration module
components/jwt                               token signing
components/email                             email adapter
components/user-service                      account domain
ui/web-app/src/app/api                       frontend Grain transport module
ui/web-app/src/app/auth                      frontend account/session module
ui/web-app/src/app/clock                     time and date-presentation adapters
ui/web-app/src/app/request                   keyed request lifecycle module
ui/web-app/src/app/router                    browser route runtime/outlet
ui/web-app/src/app/pages                     feature pages
ui/web-app/src/app/ui/interface.cljs         reusable visual primitives
ui/shadcn/src/components/ui                  shadcn-owned open React source
ui/shadcn/src/index.tsx                      compiled React interface consumed by UIx
```

## Golden path

1. Specify observable behaviour in Allium.
2. Map commands, events, read models, queries, processors, screens, and flows in `defeventmodel`.
3. Register Malli schemas for every command, event, query, and read model.
4. Implement command handlers that validate rules and emit events.
5. Implement pure read-model reducers.
6. Implement backend queries that return data via `:query/result`. They never render HTML.
7. Add Re-frame events/effects/subscriptions. Effects call `app.api.interface`; event handlers stay pure.
8. Add a UIx page and wire it through `app.router.runtime` and `app.router.core`.
9. Verify through the live app and the full gate.

Use `bb scripts/new_grain.bb service <name>` for the known-good shape, then replace the example item
domain and add its Allium trace links.

## Frontend module rules

`app.api.interface` is a deep module. It owns request IDs/timestamps, Transit envelopes, credentials,
HTTP status normalization, and success/failure dispatch. Configure it once in `app.core`. Tests use its
stub adapter. Feature code must not require `cljs-http` or carry an API client in events.

`app.auth.interface` owns session initialization and account workflows. Pages use its actions and hooks.
The session cookie is HTTP-only, SameSite=Lax, and optionally Secure via `APP_COOKIE_SECURE=true`; no
token belongs in app-db or browser storage.

`app.request.interface` owns request state by stable feature key. Pass `:request-key` and a safe,
payload-free `:retry-event` to `app.api.interface/command` or `query`; use its hooks for pending/error
presentation. A later response from a superseded or cancelled operation is ignored before its feature
callback dispatches. Do not store credential-bearing payloads in retry events.

```clojure
(api/query {:name :item/index
            :request-key [:items :index]
            :retry-event [::load-items]
            :on-success [::items-loaded]
            :on-failure [::items-failed]})
```

Read that state with `app.request.interface/use-state`. Keep returned records and feature decisions in the
feature module; the request module retains only lifecycle metadata and compact errors.

Use `app.clock.interface/now` for time-dependent feature code and pass a fixed clock in tests. Runtime
locale and time-zone come from validated application configuration. Keep calculations such as overdue,
cadence, and stage age in the cloned application's domain modules.

`app.config.interface` owns backend environment parsing, typed defaults, and production validation. Pass
an explicit string-keyed environment map to its interface in tests; executable modules must not call
`System/getenv` themselves. Application identity, ports, storage paths, tenant identity, cookie settings,
and secrets flow from this module into the Integrant system.

Keep app-db serializable. Store server data and UI state, not clients, functions, channels, DOM nodes, or
React values. Keep I/O in registered effects. Give each feature its own namespaced events and
subscriptions; colocating a small feature in one file is fine, split it when navigation becomes hard.

UIx pages compose `app.ui.interface`. Add repeated primitives there rather than cloning markup. Pages
must have deliberate loading, error, empty, and success states. Keep page functions around 120 lines and
split large visual sections into named UIx modules.

`ui/shadcn` is the TypeScript module at the React/CLJS seam. Add components with
`npm run shadcn:add -- <name>`, review the generated source, and export only the primitives or
feature-level bridges that UIx needs from `src/index.tsx`. shadcn widgets may own transient focus,
navigation, disclosure, and form interaction. Application state and completed values cross the interface
as plain data and belong in Re-frame. Never put React elements, callbacks, or Base UI state into app-db.

## Backend and auth rules

- Model service-first. One capability has one owning component.
- Call other components only through their interface namespaces.
- Validate before invoking commands; commands emit events, read models are pure reducers, queries read
  projections.
- Public browsing may use `(constantly true)`. Per-user/account data and mutations require
  `app.auth.interface/authenticated?` or a stricter role/tenant predicate.
- Identity is a UUID. Use `auth/auth-user-id`; do not compare it with an unparsed token string.
- Tenant isolation is structural; do not invent `[:tenant ...]` tags.
- `APP_TENANT_ID` is the default deployment tenant, not a permanent single-tenant constraint. Session
  claims must match it. Do not add request-time tenant resolution until a second tenant creates a real
  adapter seam; when that happens, resolve the tenant before Grain dispatch and authorize membership.
- Keep `(:missing-schemas (tools/catalog))` empty and the event model reconciled.

## Running and reloading

```bash
./scripts/dev up
./scripts/dev status
```

That is the canonical non-interactive full-stack path for humans and coding agents. It installs locked
JavaScript dependencies when absent, starts the stack detached, waits for `/healthcheck`, and reports the
application URL. When Portless is installed it owns `APP_DEV_HOSTNAME.localhost` (the initialized app slug)
and otherwise falls back to the direct localhost port. Never claim another project's hostname.

When asked to start the app, run `./scripts/dev up`, then `./scripts/dev status`, and report the application
URL. Do not launch a duplicate foreground stack. Diagnose failures with `./scripts/dev logs`; use
`./scripts/dev restart` after environment or system-level changes and `./scripts/dev down` when asked to
stop it. Use `./scripts/dev foreground` only for an attached terminal or process supervisor.

`./scripts/nrepl.sh` remains the separate interactive backend workflow; start the backend with
`(app.web-api.core/start!)` there.

From the REPL:

```clojure
(require 'app.web-api.core)
(app.web-api.core/start!)
(app.web-api.core/restart!)
```

For an edited backend namespace, reload that exact namespace only. For a new component, add it to root
`deps.edn` and call `(app.web-api.core/load-component! "name")`. If `core.clj` or the Integrant system map
changed, use `(app.web-api.core/reload-and-restart!)`. Never use `:reload-all`; redefining Grain protocols
poisons live instances.

Shadow CLJS hot reloads frontend namespaces. A route addition requires edits to both
`app.router.runtime/routes` and `app.router.core/page-modules`, plus the direct-load path in
`app.web-api.core/spa-paths`.

Prefer stable page paths with query parameters for selected records, tabs, filters, and other routing
data. This keeps callers on the single `:query-params` map. Reitit still supports path parameters when a
cloned application deliberately needs a canonical hierarchical resource URL.

## Verification gate

Run `./scripts/verify-specs.sh`. It must pass:

1. Allium check/analyse.
2. Allium ↔ event-model composition and topology validation.
3. All Polylith tests.
4. Clojure/ClojureScript lint.
5. shadcn TypeScript checks and Re-frame tests.
6. Production shadcn bridge, CSS, and Shadow CLJS build.
7. Patch hygiene.

For routing, auth, shadcn, or release-readiness changes, also run `npm run test:browser`. It builds and
tests both development and production frontend modes with isolated ports and storage, exercising health,
direct auth loads, protected redirects and return paths, sign-up/login, home, query-driven routes, the
questionnaire bridge, honest not-found behavior, and unexpected console/network failures.

Then exercise any feature-specific acceptance checks not covered by automation. Confirm protected backend
operations reject anonymous requests and loading/error/empty/success UI states remain usable. If any check
fails, report it precisely.
