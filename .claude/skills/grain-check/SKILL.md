---
name: grain-check
description: Diagnose structural drift across Grain backend and Re-frame frontend.
---

# Grain check

Run `./scripts/verify-specs.sh`, then inspect the live app.

Check that every command/query/event/read model has a schema and event-model entry; declared reads and
produces match runtime handlers; protected data has real authorization; queries return `:query/result`
data only; no backend namespace references browser UI; feature effects call `app.api.interface`; no event
vector carries an API client; app-db is serializable; route tables and page mappings agree; `/` and direct
route refreshes load; loading/error/empty/success states render; and the production frontend build passes.

Use code-agent-tools to validate commands before invoking and to prove events/projections after mutation.
