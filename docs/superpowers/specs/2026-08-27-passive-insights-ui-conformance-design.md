# Passive Insights Cross-Feature UI Conformance Design

**Date:** 2026-08-27  
**Status:** Approved design checkpoint  
**Parent specification:** `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`  
**Existing UI contract:** `docs/superpowers/specs/2026-08-21-cross-page-ui-conformance-design.md`  
**Approved visual baseline:** `D:\Temp\dualdex-rc25-ui-audit\theme-objective-final14`

## 1. Purpose

Add a final Stage 7 to the Passive Insights and Progress suite so every newly introduced route is normalized against the established DualDex interface after all six product features exist.

This is a conformance pass, not a redesign. It must not replace established information architecture, alter feature semantics, move data authority, or reopen completed feature contracts. It exists to catch cross-feature drift that cannot be judged reliably while later routes are still absent: inconsistent font tiers, legacy or hard-coded colors, missing paper/grid treatments, mismatched chrome, weak contrast, accidental overflow, inconsistent controls, and ordinary-UI diagnostic text.

Stage 7 owns the final suite-wide audit and one consolidation RC. Stage 6 continues to own only the Portable Challenge Engine Expansion feature and its independent audit and RC.

## 2. Placement and release boundary

The delivery order becomes Stage 0 through Stage 7:

- Stages 0–4 remain complete and unchanged.
- Stage 5 delivers Selected-move Damage Forecast and its feature RC.
- Stage 6 delivers Portable Challenge Engine Expansion and its feature RC.
- Stage 7 performs cross-feature UI conformance, closes the final suite audit, and publishes one consolidation RC.

Moving the final suite audit to Stage 7 avoids claiming whole-suite visual consistency before the final feature exists. Stage 6 still must have no `BLOCKER` or `ERROR` in its own feature audit before it can release.

## 3. Routes in scope

Stage 7 audits every new route and state introduced by the suite:

- Party Analysis entry, summary, member comparison, and linked detail states;
- Atlas Area Guide collapsed, expanded, empty, and populated states;
- Trainer Progress overview, Metrics, Challenges, Timeline, and objective drill-down states;
- Pokédex Specimens list, empty, single-sample, multi-sample, and individual-detail states;
- Battle selected-move Damage Forecast for resolved, incomplete, withheld, and unavailable calculations; and
- Portable Challenge Engine expansion list and detail states.

The established Party, Trainer Card, Pokédex, Atlas, and Battle routes are visual baselines and regression controls. Settings is included only where shared controls or themes affect it. Capability Report, Memory Mapper, and Settings → Debug remain the only diagnostic surfaces.

## 4. Reference and theme contract

The approved reference is the final RC25 route set under `D:\Temp\dualdex-rc25-ui-audit\theme-objective-final14`, interpreted through the durable cross-page UI conformance specification rather than copied as fixed pixels.

Every ordinary route must use the same computed theme-token pipeline for:

- outer page and content background;
- header, title, and the established blue separator line;
- paper, grid, panel, menu, and card surfaces;
- text, muted text, borders, shadows, and focus indicators;
- inactive, active, selected, disabled, and pressed controls;
- icons and their containers; and
- semantic HP, experience, rarity, type, warning, and success colors.

No new route may preserve a legacy forest/olive value, fixed yellow, fixed white panel, or one-off background merely because it was implemented in isolation. Semantic colors may differ from the base theme only when they communicate an established meaning and still meet contrast requirements.

The matrix covers:

- `GAME` themes derived from official Gen I, Gen II, and Gen III controls;
- `GAME` themes derived from Modern Emerald, Pokémon Unbound, and Pokémon Odyssey controls;
- Light;
- Dark; and
- High Contrast.

The audit validates computed styles on rendered elements. Root token values alone are not evidence that a route conforms.

## 5. Typography contract

At the production 1024×768 viewport, Stage 7 measures every visible text-bearing element in every in-scope layout at font scales 85%, 100%, and 135%.

For each captured layout, the evidence records:

- visible text-element count;
- minimum computed font size;
- maximum computed font size; and
- unweighted average computed font size.

The existing physical gates remain mandatory:

- no visible text below 11.2 px; and
- no layout average below 12 px.

Normalization must preserve hierarchy. Page titles, primary values, card headings, body copy, labels, and auxiliary metadata use shared semantic tiers; they are not flattened to one size merely to pass an average. The pass also checks line height, wrapping, truncation, and label/value alignment at every supported font scale.

## 6. Surface, pattern, and geometry contract

Every route must use the established pattern appropriate to its page family. Detail, Party, Trainer Card, Battle, and analytical surfaces must not silently lose their approved paper/grid background. Atlas retains its map-specific canvas and chrome rather than inheriting a detail-page treatment.

The audit checks:

- consistent header height, title alignment, separator line, and back/navigation placement;
- shared card borders, corner treatment, shadows, internal padding, and vertical centering;
- consistent segmented tabs, action buttons, icon buttons, filter controls, and touch targets;
- balanced use of available 4:3 space without artificial empty regions or compressed content;
- exactly one intended scrolling owner per route;
- no body overflow, clipped content, inaccessible controls, or accidental nested scrolling;
- focus visibility and keyboard/touch accessibility; and
- stable layouts at 85%, 100%, and 135% font scale.

Touch targets must retain the established minimum physical size even when visible icons are smaller. Empty, unavailable, and capability-gated states must occupy the same visual system as populated states and must not resemble diagnostics.

## 7. Content and diagnostic boundary

Normal headers and cards use player-facing names only. They must not show parser provenance, raw offsets, capability labels, implementation source, memory authority, cache state, extraction method, confidence internals, or Organic/Discovered state as a debug subtitle.

The conformance scan must cover text nodes, accessible names, tooltips, empty states, withheld-result explanations, and error fallbacks. Technical details are allowed only inside Settings → Debug and its explicitly linked diagnostic routes.

## 8. Automated evidence

Stage 7 extends the existing browser conformance approach instead of creating a second visual framework. The final suite produces:

- a deterministic route/theme/font-scale capture matrix;
- a minimum/maximum/average font-size matrix for every layout;
- a computed-style matrix for backgrounds, panels, borders, text, controls, icons, patterns, and separators;
- contrast, overflow, scroll-ownership, focus, and touch-target assertions;
- an ordinary-UI diagnostic-leakage scan;
- screenshots for every matrix row and important state; and
- regression comparisons against the established Party, Trainer Card, Pokédex, Atlas, and Battle controls.

The evidence is written under `docs/reports/passive-insights-progress/` in durable machine-readable and human-readable forms. Large screenshot sets may remain workflow artifacts, but their manifest, measured values, failures, and representative references must be retained in the repository.

## 9. Acceptance gate

Stage 7 is complete only when:

- every in-scope route has a recorded result for every required theme and font-scale control;
- all typography floors and averages pass without destroying hierarchy;
- all rendered surfaces consume the correct shared theme and pattern contract;
- all contrast, overflow, scroll, focus, touch-target, and navigation assertions pass;
- no ordinary route exposes diagnostic or provenance text;
- all six feature audits remain valid after normalization;
- the final Sections 3–18 suite cross-check contains no `BLOCKER` or `ERROR`;
- compatibility percentages and Organic-mode evidence remain unchanged or are truthfully regenerated when presentation fixes expose a real contract issue;
- browser, Android, release-metadata, performance, and signed-artifact gates pass; and
- exactly one next numeric consolidation RC is published and verified without installing or launching it.

Visual discrepancies are blockers when they violate a contract above. Subjective redesign ideas that do not violate the contract are recorded as future work rather than expanded into Stage 7.

## 10. Explicit non-goals

Stage 7 does not:

- redesign the application;
- add another product feature;
- change ROM parsing, live-memory authority, recovery, or journal semantics;
- change Organic/Discovered disclosure rules;
- alter map rendering, fog, tracking, zoom, or POI behavior;
- change damage or challenge calculations;
- add debug data to ordinary UI;
- consume a feature RC for partial normalization; or
- install, launch, or interact with a console during release validation.

