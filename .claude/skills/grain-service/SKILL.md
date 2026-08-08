---
name: grain-service
description: Scaffold a complete Grain backend service and Re-frame page.
---

# Grain service

Read `CLAUDE.md`, then run:

```bash
bb scripts/new_grain.bb service <name>
```

The generator emits schemas, command, event, read model, query, event model, reducer test, and a UIx page
whose Re-frame effects use `app.api.interface`. Replace the example item vocabulary with the actual
service capability. Add the component to root `deps.edn`, require its interface from the web base, and add
the client route, page mapping, and backend `spa-paths` entry. Specify behaviour in Allium and link commands/screens before running
`./scripts/verify-specs.sh`.

Do not introduce server-rendered pages or pass API clients through event vectors.
