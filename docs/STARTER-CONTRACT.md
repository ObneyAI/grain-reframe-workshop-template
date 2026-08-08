# Grain Re-frame Workshop Template contract

This document separates what the starter guarantees from work that belongs to each cloned application.

## Guaranteed at a verified starter tag

- A Polylith Grain backend with SQLite events, LMDB projections, Allium specifications, and an Event
  Model topology gate.
- A UIx + Re-frame browser application with Reitit routing, Tailwind CSS, and a compiled shadcn Base UI
  module. React/Base UI details stay behind the `@grain/shadcn` interface, while the generic application
  shell exposes navigation, page-title, page-action, and content slots. Starter routes prefer stable page
  paths with record selection, tabs, and filters represented in one query-parameter map.
- Same-origin Grain command/query transport behind `app.api.interface`, including Transit envelopes,
  credentials, anomaly normalization, preserved structured field explanations, and a deterministic test
  adapter. Optional stable request keys integrate with `app.request.interface` for independent lifecycle
  state and stale-response suppression.
- Account and session workflows behind `app.auth.interface`, with required email verification and safe
  resend, configurable isolated cookies, tenant-bearing signed claims, authenticated routing, safe return
  paths, and explicit forbidden, not-found, loading, and application-error outcomes.
- A small `app.clock.interface` with production and fixed adapters. `APP_LOCALE` and `APP_TIME_ZONE` are
  validated by backend configuration and exposed to the browser as CSP-safe document metadata.
- A single configured deployment tenant by default without baking single-tenancy into domain modules.
- Validated development and production configuration, guarded local storage reset/seed commands,
  recursive credential redaction, and isolated integration-test storage.
- Provider-neutral email, file storage, presigning, and encryption seams with deterministic local adapters;
  SES, S3, and KMS production adapters; pinned Mailpit/LocalStack development infrastructure; and a
  persistent Mailpit SQLite volume with an explicit inbox-only reset.
- A provider-neutral webhook processor with raw-body preservation, HMAC verification, process-local
  idempotency/audit receipts, and failed-receipt replay.
- Correlation IDs, request metrics, safe dependency diagnostics, and configurable μ/log destinations.
- A managed `scripts/dev` lifecycle for humans and coding agents, including health-checked background
  startup, a same-JVM loopback nREPL with live Grain coding-agent tools, status/log/stop commands,
  project-owned Portless routing with localhost fallback, a complete specification/build gate, a
  tracked-files fresh-clone acceptance check, and browser contracts for development and release-compiled
  frontend assets.

The guarantee is attached to a starter tag only when its clean Linux CI workflow passes.

## What initialization changes

`bb scripts/init_project.bb` changes application identity and local deployment defaults: slug/package
identity, display name, port, Portless hostname and base URL, cookie name, generated development tenant
UUID, email sender, HTML metadata, environment example, README title, and starter provenance. It rejects
stale executable starter identity after the rewrite.

Initialization deliberately leaves these structural contracts alone:

- The generic `app` top namespace and Polylith layout.
- Grain dependency coordinates and the Allium/Event Model workflow.
- The API, authentication, configuration, shadcn, routing, and UI module seams.
- Generic account capabilities and example routes.
- Production secrets, infrastructure choices, and application domain concepts.

Delete examples only after the cloned app has equivalent acceptance coverage for the seam they prove.

## What every cloned application must supply

- A real production `APP_JWT_SECRET`, HTTPS `APP_BASE_URL`, secure-cookie setting, canonical base URL,
  tenant UUID, email sender, and isolated storage path.
- Production provider credentials, secret management, backup/restore, deployment configuration, and any
  hosted log/metric exporter appropriate to its environment.
- Durable webhook receipt storage before accepting production webhooks; the starter's process-local adapter
  proves behavior but cannot preserve idempotency across restarts or multiple instances.
- Its own domain services, authorization rules, seed adapter, navigation, visual identity, and
  loading/error/empty/success states.
- Date-driven business rules such as overdue status, follow-up cadence, and pipeline stage age.
- A request tenant resolver and tenant-membership authorization before one deployment serves more than
  one tenant. The existing deployment-tenant context and session-claim checks are the migration seam,
  not the complete multi-tenant feature.

## Do not add to the starter

A cloned application's domain is its own. The starter owns the machinery for expressing and testing these
decisions; it must not make them.

- Domain entities, their relationships, and lifecycle/state rules.
- Workflow or pipeline stages and their transition rules.
- Scoring, prioritization, or ranking heuristics.
- Cross-entity invariants (for example, "every X must always have a pending Y").
- Recurrence, cadence, and overdue rules.
- Domain-specific routes, navigation, tables, cards, boards, and forms.
- Seed and mock data.
- Visual identity and theme policy.
- Domain terminology and the distinctions it encodes.
- Phased or roadmap feature scope.

## Updating a cloned application

The starter is not a framework package and clones are expected to diverge. Record the originating tag,
then update intentionally:

1. Compare the clone's recorded tag with the desired later starter tag.
2. Review commits between those tags by module seam rather than copying the whole tree.
3. Port security, lifecycle, and verification changes first; keep application-specific domain and UI
   decisions in the clone.
4. Update local adapters or configuration names only where the later contract requires it.
5. Run `./scripts/verify-specs.sh`, both browser contracts, and the clone's domain acceptance tests.
6. Record the adopted starter tag or commit and any deliberately skipped changes in the clone README.

## Releasing the starter

Before tagging a release, verify only committed files in an isolated temporary copy:

```bash
./scripts/verify_fresh_clone.sh
```

That acceptance command initializes a synthetic app, runs `bun install --frozen-lockfile`, adds and compiles a fresh shadcn
component through the locked CLI, prepares Clojure dependencies, runs the complete gate, starts the
managed full stack on a temporary port and storage directory, checks known and unknown browser routes,
then stops every managed process and removes its temporary files.

The supported release path refuses a dirty worktree, a non-`main` branch, an existing tag, or any failed
fresh-clone/browser gate before creating an annotated local tag:

```bash
./scripts/release_starter.sh v0.1.0
```

Push `main` first and wait for its clean Linux CI gate. Then push the tag; tag-triggered CI reruns the same
full gate. Repository rules on the canonical remote should require `Full clone-ready gate` before merging
to `main` and restrict creation of `v*` tags to the release owner.

Remaining before the first tagged release:

- Create the canonical Git remote for `grain-reframe-workshop-template` and push `main`.
- Establish a version and tag the first cloneable release (for example, `v0.1.0`).
- Record the originating starter tag in every cloned application's README.
- Confirm the clean Linux CI gate passes on the tagged commit (the guarantee attaches only then).
