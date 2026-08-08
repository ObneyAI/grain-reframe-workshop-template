---
name: grain-view
description: Build UIx pages backed by Re-frame state in this Grain starter.
---

# Grain view

Pages live under `ui/web-app/src/app/pages` and are UIx modules. Compose them from
`app.ui.interface`; keep backend access out of render functions. Subscribe through `re-frame.uix`, dispatch
domain events from user actions, and put HTTP work behind `app.api.interface` in registered effects.

Use exports from `@grain/shadcn` for accessible React interactions. A feature bridge can own transient
widget state, but its interface accepts serializable props and returns plain values to a Re-frame event.
Add or update the TypeScript bridge under `ui/shadcn`, not inside a CLJS page.

Every data page needs intentional loading, error, empty, and success states. Keep app-db serializable,
keys stable, forms accessible, and primary page functions near 120 lines. Wire a new path in both
`app.router.runtime/routes`, `app.router.core/page-components`, and `app.web-api.core/spa-paths`; then
verify a direct browser refresh.
