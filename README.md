# Grain Re-frame Workshop Template

A clean-canvas Polylith Grain app with a UIx + Re-frame frontend. It carries the current Silo seed's
event-model, Allium, SQLite, LMDB, code-agent-tools, and verification foundation without its Datastar
rendering tier.

The boundary between starter guarantees and cloned-application responsibilities — what initialization
changes, what a clone must supply, and how to publish a tagged release — is documented in
[docs/STARTER-CONTRACT.md](docs/STARTER-CONTRACT.md).

## Initialize a fresh clone

Run the initializer once from the root of a newly cloned starter:

```bash
bb scripts/init_project.bb \
  --slug my-app \
  --name "My App" \
  --port 8080 \
  --time-zone America/Chicago
```

It generates a development tenant UUID and application-specific cookie name, then updates runtime and
frontend identity, HTML metadata, package identity, local defaults, and README provenance. Optional
`--cookie-name`, `--base-url`, `--tenant-id`, `--locale`, and `--time-zone` values override the generated
defaults. It fails if stale starter identity remains in executable configuration.

## What ships

- Grain CQRS backend: commands → events → read models → queries.
- SQLite event store and LMDB projection cache; no AWS or LocalStack.
- Account foundation: sign-up, email verification, login/logout, password reset, HTTP-only sessions.
- Protected, anonymous-only, and public route policies with safe post-login return paths and explicit
  403, 404, session-loading, and application-error outcomes.
- UIx + Re-frame + Reitit frontend styled with Tailwind CSS and shadcn Base UI.
- One wrapped API module (`app.api.interface`) and one wrapped auth module (`app.auth.interface`).
- Accessible form fields, structured server-error summaries, and consistent busy/failure/success feedback.
- Keyed request lifecycle state with independent pending, retry, cancellation, and stale-response handling.
- A system/fixed clock interface plus explicit runtime locale and time-zone settings, without domain date rules.
- One validated backend configuration module (`app.config.interface`) with secure production checks.
- Recursive credential redaction before logging so requests, cookies, headers, passwords, session/JWT
  secrets, verification/reset tokens, and token-bearing email HTML are not published by the development
  console adapter.
- A real shadcn TypeScript workspace whose compiled React interface is consumable from UIx.
- Allium + `defeventmodel` composition checks, Polylith tests, Clojure lint, frontend tests, and a
  production frontend build in one gate.
- Playwright browser contracts that exercise real auth, routing, and the shadcn bridge against both
  development-compiled and release-compiled frontend assets.

## Prerequisites

Have these on `PATH` before the first `npm run dev` or `./scripts/verify-specs.sh`:

- Java 21
- Clojure CLI — the project pins Clojure 1.12 in `deps.edn`
- Node.js and npm
- Babashka (`bb`) — runs the Grain slice and page generator
- clj-kondo — the lint stage of the gate
- ripgrep (`rg`) — the frontend seam-discipline checks in the gate
- Allium CLI (3.5) — the specification stage of the gate

Playwright's Chromium bundle is also required for the browser contract. Install the pinned bundle after
`npm ci` with `npx playwright install chromium`; clean Linux CI uses `--with-deps` as well.

On macOS with Homebrew OpenJDK, Java 21 is at `/opt/homebrew/opt/openjdk@21/bin`. If
`java -version` does not report 21, put it on `PATH` for the session:

```bash
export PATH="/opt/homebrew/opt/openjdk@21/bin:$PATH"
```

## Run it

```bash
./scripts/dev up
./scripts/dev status
```

`up` installs the locked JavaScript dependencies when needed, claims
<https://reframe-template.localhost> through Portless when it is installed, builds the
shadcn bridge, starts Vite, Tailwind, Shadow CLJS, and the Clojure backend, then waits for the health check.
Without Portless it falls back to <http://localhost:8080>. The lifecycle is designed for developers and
coding agents alike:

```bash
./scripts/dev logs --follow
./scripts/dev restart
./scripts/dev down
```

Use `npm run dev`, `./scripts/dev foreground`, or the compatibility adapters `./scripts/dev.sh` and
`./portless.sh` when a foreground process is preferable. For interactive backend work, the separate nREPL
workflow remains available:

```bash
./scripts/nrepl.sh # nREPL, default 7888
```

The backend has safe local defaults. To customize it, copy `.env.example` to `.env`, edit it, and export
the values into the shell. `scripts/dev` loads `.env` automatically; for the nREPL workflow, export it
before starting the REPL:

```bash
set -a
. ./.env
set +a
```

Then start the backend from the REPL:

```clojure
(require 'app.web-api.core)
(app.web-api.core/start!)
```

The backend serves the compiled frontend and Grain's `/command` and
`/query` endpoints from the same origin, so session cookies do not need a development CORS workaround.

## Layout

```text
bases/web-api/          HTTP entry point, Grain routes, SPA fallback
components/             backend Polylith components
ui/web-app/src/app/
  api/                  Grain HTTP client seam + remote/stub adapters
  auth/                 session/account Re-frame module
  clock/                system/fixed clocks + configured date presentation
  request/              keyed pending/success/failure/retry/cancellation state
  questionnaire/        example Re-frame state module
  pages/                UIx pages
  re_frame/             local UIx subscription adapter
  router/               Reitit/Pushy runtime and outlet
  ui/interface.cljs     reusable UI primitives + navigation/title/action/content shell slots
ui/shadcn/
  components.json       shadcn CLI configuration (Base UI + Nova)
  src/components/ui/    open-code shadcn components owned by this repo
  src/index.tsx          small React interface exported to UIx
```

Add another shadcn component with:

```bash
npm run shadcn:add -- dialog
```

The command uses the lockfile-installed CLI, so a clone does not silently select a different CLI release.
Review the generated source, then export the component—or a small feature-specific bridge—from
`ui/shadcn/src/index.tsx`. UIx can import those exports from `@grain/shadcn`. Stateful shadcn widgets
own only ephemeral interaction state; they emit plain values into Re-frame, as the starter questionnaire
demonstrates at `/examples/questionnaire`.

Compose cloned-app navigation and per-page actions through the `app.ui.interface/app-shell` slots. Keep
session controls and the frame inside that module; application-specific links, filters, and actions belong
in the clone.

Create a complete example slice with:

```bash
bb scripts/new_grain.bb service inventory
```

The generator prints the backend and frontend wiring still needed. Verify the whole starter with:

```bash
./scripts/verify-specs.sh
```

Exercise real browser behavior against both frontend build modes with:

```bash
npm run test:browser
```

Tagging a starter release — fresh-clone acceptance, `release_starter.sh`, the CI/tag rules, and the work
still open before the first tag — is documented under
[Releasing the starter](docs/STARTER-CONTRACT.md#releasing-the-starter).

## Development data

Reset the configured development store with an explicit confirmation:

```bash
bb dev reset
```

Automation may pass `--yes`; the command still refuses production environments, repository/home roots,
symlinks, and paths outside `storage*`, `.dev-data/`, or the system temporary directory. Use a distinct
path such as `.dev-data/app-a` for parallel local instances:

```bash
APP_STORAGE_DIR=.dev-data/app-a bb dev seed
APP_STORAGE_DIR=.dev-data/app-a bb dev reset --yes
```

The starter's seed command only creates and marks the safe storage target. A cloned application may add a
committed `scripts/app_seed.bb` adapter for its own fixtures; application records and other domain seed
data do not belong in the starter.

Set `APP_COOKIE_SECURE=true` in HTTPS deployments. Override the frontend API origin at compile time
only when the UI genuinely deploys separately; same-origin is the default and preferred topology.
Production startup also requires a non-placeholder `APP_JWT_SECRET`, an HTTPS `APP_BASE_URL`, and secure
cookies. Invalid settings fail together at boot with actionable messages.

Set `APP_LOCALE` to a BCP 47 language tag and `APP_TIME_ZONE` to an IANA zone (or `UTC`). The backend
validates both and publishes them to the same-origin browser document for `app.clock.interface` formatting.

`APP_TENANT_ID` selects the one tenant served by the default deployment; it is an operating default, not a
single-tenant domain decision. Enabling a second tenant is a cloned-application responsibility — see
[docs/STARTER-CONTRACT.md](docs/STARTER-CONTRACT.md).
