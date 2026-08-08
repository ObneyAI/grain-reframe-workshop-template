---
name: visual-build
description: Build reviewable static UIx mock screens from the design brief before backend wiring.
---

# Visual build

Read the design brief and event-model screens. Build a `/design-system` UIx page plus one client-side mock
route per primary screen using realistic local sample data. Compose `app.ui.interface`, Tailwind, and
the installed shadcn components; do not create backend commands/queries for static previews. Add missing
open-code components through `bun run shadcn:add -- <name>` and expose only the needed React interface.
Wire every preview through the Reitit
route runtime and outlet, run the frontend build, and review at mobile and desktop widths. Once approved,
reuse the page modules and replace sample data with Re-frame subscriptions/effects.
