---
name: grain-ui-component
description: Extend the starter's reusable UIx and shadcn modules.
---

# Grain UI module

Shared visual primitives live behind `app.ui.interface`. Add a primitive when two or more pages need the
same interaction or treatment; keep one-off domain composition in its page. Prefer a small props map,
children, semantic HTML, keyboard support, and visible focus. Search the installed shadcn components
first. Add missing components with `npm run shadcn:add -- <name>`, review their open source, and export a
small interface from `ui/shadcn/src/index.tsx`. UI primitives may use presentation hooks but must not call
backend queries or commands.

React-owned interaction state stays inside the shadcn module. Send completed plain values through a
callback and immediately dispatch them into Re-frame; never store React values or callbacks in app-db.

Check the module's depth: callers should learn less than the implementation hides. Render every affected
state and screen before considering the change complete.
