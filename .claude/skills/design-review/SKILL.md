---
name: design-review
description: Screenshot every primary UIx screen, critique it, and refine until the visual gate passes.
---

# Design review

Functional but visually careless is not done. For each primary route: open it, capture a full-page
screenshot, inspect the image, record pass/fail for the rubric, fix the shared `app.ui.interface` module or
page composition, and repeat.

Rubric:

1. Header separates title/context from primary actions.
2. Spacing follows a consistent rhythm and a readable max width.
3. Rows and form controls align predictably.
4. Typography creates a clear title → section → body → metadata hierarchy.
5. Buttons, surfaces, radii, and states are consistent because pages reuse the UI module.
6. Loading, error, empty, and success states are intentional.
7. Tap targets, focus indicators, labels, contrast, and keyboard use are accessible.
8. Interaction state survives Re-frame data updates; dialogs/menus do not unexpectedly close or lose focus.
9. The page works at mobile and desktop widths without overflow or cramped controls.

Before declaring done, list each route, screenshot path, per-item verdict, and final PASS/FAIL. Fix every
failure; do not grade on vibes.
