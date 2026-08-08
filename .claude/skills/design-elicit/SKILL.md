---
name: design-elicit
description: "Run a structured discovery session to define a NEW app's LOOK & FEEL — its aesthetic target — through conversation with a non-technical person. Use when the user has a plan (app.event-model.edn / PLAN.md) and now needs to decide how the app should LOOK: its personality/mood, color palette, typography, shape & density, and motion. Produces a durable app.design-brief.edn (a plain EDN map) plus a plain-language `## Design direction` summary in PLAN.md. Phased discovery (personality → palette → typography → shape/density → motion), one question at a time, authored incrementally and self-validated against the brief schema. This is aesthetic/brand discovery, NOT domain modeling — for the app's behavior use em-elicit (the event model); for the structural layout quality gate use design-review; for turning a brief into a theme use the design-system phase."
---

# Design elicitation (design-elicit)

## Purpose
Pair with a NON-TECHNICAL person to decide **how their app should look and feel**, and capture that
as a durable, machine-readable **aesthetic target**. You produce exactly one artifact map,
`app.design-brief.edn`, plus a friendly plain-language summary. The domain (what the app DOES) is
already decided — `app.event-model.edn` + `PLAN.md` exist; your job is the *look*, not the behavior.

**Done-criterion (state it up front):** the session ends when `app.design-brief.edn` is written and
**self-validates against the schema below** — every required key present, values from the allowed
sets — AND the user has confirmed the plain-language direction reads right to them. You are not
building anything and you are not choosing exact CSS; you are capturing an agreed *direction* a later
design-system step will turn into a real theme.

## When to use / not use
- **Use** right after planning, when the user wants to shape the app's visual identity.
- **Not** for the app's behavior/data (that's **em-elicit** → the event model).
- **Not** the structural layout critique of built screens (that's **design-review** — spacing,
  alignment, hierarchy of *rendered* pages).
- **Not** the actual theme/CSS generation (that's the downstream *design-system* step, which consumes
  your brief).

## Before you start: read the context
Read `app.event-model.edn` (its `:screens` — the surfaces the look will live on) and `PLAN.md` (what
the app is, and for whom). Ground every aesthetic choice in *this* app and *this* audience — a
child's chore-chart and a legal case tracker want opposite looks.

## Process — gather → capture → confirm → repeat
Talk like a friendly designer, never a form. **One question at a time.** Let the user describe the
vibe in their own words first, then translate. Author `app.design-brief.edn` **incrementally** — add
each phase's keys as you settle them, re-reading the file so you never clobber earlier agreement.
When you genuinely must decide something for them, pick a sensible default and record it as an
`;; OPEN:` note so it surfaces as a question — never silently assume.

Offer concrete, plain-language choices (with a quick "why"), not jargon. "Do you want it to feel more
**calm and minimal**, or **warm and friendly**, or **bold and energetic**?" beats "what's your
saturation preference?"

- **Phase 0 — Vibe in their words (optional).** "If this app were a place or a brand, what would it
  feel like? Any apps/sites whose look you love?" Capture references + adjectives; skip ahead if they
  already name a clear direction.
- **Phase 1 — Personality & audience.** Settle 2–4 personality adjectives (`:personality`) and who
  it's for (`:audience`). These anchor everything else — reflect them back before moving on.
- **Phase 2 — Palette direction.** Light, dark, or both (`:palette :mode`)? A signature accent color
  or hue family (`:primary`, a hex or a named hue like "deep teal")? Warm/cool/neutral greys
  (`:neutral`)? Muted or vivid (`:saturation`)? Tie each to the personality ("calm & trustworthy →
  muted, cool").
- **Phase 3 — Typography.** Sans (modern/neutral), serif (editorial/classic), or mono (technical)
  for `:type :family`, and its character in a phrase (`:character`, e.g. "clean, geometric",
  "warm humanist", "sharp editorial").
- **Phase 4 — Shape & density.** Corner feel (`:shape :radius` — `:sharp | :soft | :round`) and how
  breathable vs compact (`:shape :density` — `:airy | :cozy | :compact`). Relate to the audience
  (non-technical/first-time users usually want airier, softer).
- **Phase 5 — Motion & imagery.** How lively (`:motion` — `:none | :subtle | :expressive`) and any
  imagery notes (illustration vs photography vs none) in `:notes`.

After each phase, reflect the running direction back in one plain sentence and adjust.

## The artifact — `app.design-brief.edn`
A single plain EDN **map** at the workspace root (NOT a macro, NOT code). Schema:

```clojure
{:personality  ["calm" "trustworthy" "modern"]      ; REQUIRED — 2–4 adjectives
 :audience     "non-technical small-business owners" ; REQUIRED — one phrase
 :palette {:mode       :light            ; REQUIRED — :light | :dark | :both
           :primary    "#0f766e"         ; REQUIRED — hex OR a named hue ("deep teal")
           :neutral    :cool             ; :warm | :cool | :neutral
           :saturation :muted}           ; :muted | :balanced | :vivid
 :type    {:family     :sans             ; REQUIRED — :sans | :serif | :mono
           :character  "clean, geometric"}
 :shape   {:radius     :soft             ; :sharp | :soft | :round
           :density    :airy}            ; :airy | :cozy | :compact
 :motion  :subtle                        ; :none | :subtle | :expressive
 :inspirations ["Linear" "Notion"]       ; optional — reference apps/sites
 :notes   "Softly rounded cards, lots of whitespace; no stock photos."}
```

**Required keys:** `:personality`, `:audience`, `:palette` (with `:mode` + `:primary`), `:type`
(with `:family`). Everything else has a sensible default if the user doesn't care — record the
default as an `;; OPEN:` note. Keyword-valued fields MUST use a value from the sets above (a later
step maps them to concrete tokens — off-list values break it).

## The human summary — `## Design direction` in PLAN.md
Append (or update) a `## Design direction` section in `PLAN.md`: 2–4 plain sentences, NO jargon, that
a non-technical person recognizes as *their* choices — e.g. *"A calm, trustworthy look for
small-business owners: a deep-teal accent on cool, muted greys, clean geometric sans-serif type,
generous spacing with softly rounded corners, and gentle, subtle motion."* Keep it in sync with the
brief; never mention EDN, tokens, or CSS.

## Guardrails
- One brief per app; depth over breadth — a few confident choices beat many hedged ones.
- Validate after every increment (re-read the file; keep required keys present + values on-list).
- Record open decisions explicitly (`;; OPEN:`), never silently assume.
- Push back on vagueness kindly; resolve contradictions ("bold but minimal — which wins where?").
- You choose a *direction*, not final CSS. Do not edit app code, `css/main.css`, or any component —
  the design-system step owns that. You only write `app.design-brief.edn` + the `PLAN.md` summary.
- Finish only when the brief self-validates and the user confirms the plain-language direction.
