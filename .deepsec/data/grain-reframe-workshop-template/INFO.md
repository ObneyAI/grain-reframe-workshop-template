# Grain Re-frame Workshop Template

## What this codebase does

This is a cloneable information-system starter. Its Clojure backend uses Grain
CQRS/event sourcing in a Polylith layout: validated commands emit immutable
events, pure reducers build LMDB projections, and queries return read models.
SQLite stores local events. A UIx/React browser uses Re-frame, Reitit, Tailwind,
and a compiled shadcn Base UI bridge. Pedestal serves the SPA and Grain
`/command` and `/query` endpoints from one origin.

Provider-neutral interfaces isolate email, object storage, URL presigning,
encryption, webhooks, observability, clocks, and configuration. Development
uses Mailpit, LocalStack, memory/test adapters, and local encryption; production
adapters include SES, S3, and KMS. The `customer` component is disposable
tracer-bullet example code, not a reusable CRM domain.

## Auth shape

- `app.auth.interface/extract-auth-cookie-interceptor` verifies the HTTP-only
  session cookie and adds normalized `:auth-claims` to Grain context.
- `app.auth.interface/authenticated?` is the minimum authorization predicate;
  protected `defcommand` and `defquery` declarations must use it or something
  stricter. `auth-user-id` is the trusted current-user identity accessor.
- `::auth-token-verifier` accepts a JWT only when its tenant claim matches the
  configured deployment tenant and its token version matches the projection.
- `app.auth.interface/auth-cookie-interceptor` alone sets or clears SameSite=Lax,
  HTTP-only session cookies after successful login, logout, or password-change
  commands.
- Public account commands intentionally use `(constantly true)` for sign-up,
  login, email verification/resend, password-reset request, and reset completion.
- Public account-command throttling is an explicit deployment-gateway control,
  documented in `docs/STARTER-CONTRACT.md`; the application layer preserves
  non-enumerating responses and does not carry a process-local limiter.

## Threat model

The highest-impact failures are authorization bypass, cross-user or cross-tenant
data access, forged/replayed webhooks, leaked session/reset/verification tokens,
and unscoped object-storage grants. An attacker may control command/query
payloads, query strings, cookies, uploaded bytes, and webhook headers/bodies.
Production configuration must reject starter secrets, insecure cookies, HTTP
base URLs, local-only providers, and AWS endpoint overrides.

## Project-specific patterns to flag

- A protected `defcommand` or `defquery` missing `:authorized?`, or using
  `(constantly true)` outside the intentional public account allowlist.
- Identity or tenant scope taken from request data instead of `auth-user-id`,
  verified session claims, and the configured deployment tenant.
- A webhook route parsing before `capture-raw-body`, verifying after dispatch,
  skipping constant-time signature comparison, or lacking event-ID idempotency.
- Direct AWS/SMTP/provider construction inside domain components instead of the
  provider-neutral interfaces and Integrant wiring in `app.web-api.core`.
- Presigned/object-store keys that are not tenant/domain owned, secrets or signed
  URLs entering logs, or browser code storing credentials in Re-frame app-db.

## Known false-positives

- Development-only JWT/AES keys, LocalStack credentials, HTTP URLs, Mailpit SMTP,
  memory stores, and deterministic presigned URLs are deliberate local defaults;
  `app.config.interface` rejects the unsafe combinations in production.
- Public `/healthcheck`, `/health`, `/metrics`, SPA routes, and the public account
  command allowlist are intentional. Health output must remain non-sensitive.
- Missing in-process throttling on the intentional public account command
  allowlist is accepted: every production clone must enforce it at its gateway.
- The webhook receipt store is intentionally process-local starter machinery;
  the contract requires durable storage before production webhook consumption.
- `components/*/test`, browser fixtures, capture adapters, and `scripts/dev*`
  deliberately use test credentials and destructive operations with guards.
- Compiled files under `bases/web-api/resources/public/js`, `.shadow-cljs`, `out`,
  and `ui/shadcn/dist` are generated artifacts and must not be security-reviewed.
