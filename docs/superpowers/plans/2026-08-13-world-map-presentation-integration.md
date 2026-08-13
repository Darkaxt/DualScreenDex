# World Map Presentation Integration Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Present normalized catalog world maps in RC24 with the approved full-stage atlas controls, gestures, fog, and navigation contract while preserving the existing Pokédex when no map is available.

**Architecture:** Extend the read-only `CatalogView` with optional normalized regions, locations, and image URLs; raw ROM formats remain absent from web production logic. Keep Map navigation as local Preact presentation state so the existing companion screen state machine is unchanged. Put reusable fit/zoom/gesture/fog logic in a focused module, render it through one `MapPage`, and verify the real 4:3 browser contract through an intercepted bootstrap response.

**Tech Stack:** Kotlin/JVM API projections, Preact/TypeScript, Canvas 2D, Pointer Events, Vitest, Vite, Playwright/Chromium.

---

### Task 1: Normalized API projection

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `companion-web/src/models.ts`

- [ ] Add a failing `ApiViewBuilderTest` assertion for region identity, raster dimensions, grid dimensions, `/api/maps/{encoded-key}.png`, location base-area IDs, and cell geometry.
- [ ] Run `./gradlew :companion-core:test --tests '*ApiViewBuilderTest*'` and confirm the missing `worldMaps` projection fails.
- [ ] Add immutable view models and project `ParsedCatalog.worldMaps` without source-family or binary-layout fields.
- [ ] Run the focused companion-core test and confirm it passes.

### Task 2: Viewport and gesture engine

**Files:**
- Create: `companion-web/src/mapEngine.ts`
- Create: `companion-web/src/mapEngine.test.ts`

- [ ] Add failing Vitest cases for intrinsic contain-fit, center-anchored button zoom, one-pointer pan, two-pointer midpoint preservation on pinch-out and pinch-in, cancel cleanup, pinch selection suppression, and exact black canvas edges.
- [ ] Run `npm test -- mapEngine.test.ts` and confirm imports or assertions fail because the engine is absent.
- [ ] Implement finite bounded transforms, `GestureTracker`, and exact edge checking with no parser/source assumptions.
- [ ] Run the focused Vitest file and confirm all engine cases pass.

### Task 3: Map page and no-map routing

**Files:**
- Create: `companion-web/src/pages/MapPage.tsx`
- Create: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`

- [ ] Add failing component tests that require a semantic Map icon inside the existing Pokédex header only when normalized maps exist; require no dedicated navigation row; require Settings/Legend then Area Dex in the Map utility rail; and require the semantic Dex icon.
- [ ] Add failing interaction tests for visible zoom/recenter controls, recenter reset, fog and marker toggles, pointer capture/cancel wiring, and current-region selection.
- [ ] Run the focused component tests and confirm they fail for the missing presentation.
- [ ] Add optional local `mapOpen` state, extend the existing header action cell, implement `MapPage`, and keep no-map fixtures byte-for-byte equivalent in structure below the header.
- [ ] Run the focused component tests and confirm they pass.

### Task 4: Refined atlas styling

**Files:**
- Modify: `companion-web/src/styles.css`
- Modify: `companion-web/src/layoutStyles.test.ts`

- [ ] Add failing static layout assertions for a two-row Map page only, `touch-action: none`, visible 40px-or-larger utility controls, full black stage background, and absence of any map toolbar/navigation row selector.
- [ ] Run `npm test -- layoutStyles.test.ts` and confirm the new assertions fail.
- [ ] Add the approved deep-forest field-atlas header, black full stage, pixel-crisp map plane, overlay rail, responsive controls, and dark/high-contrast compatibility without adding a diagnostic panel or helper copy.
- [ ] Run the layout and component tests and confirm they pass.

### Task 5: Real 4:3 browser contract

**Files:**
- Create: `companion-web/test/map-browser-contract.js`
- Create: `companion-web/test/map-browser-fixture.json`
- Create: `companion-web/output/playwright/map-fit.png`
- Create: `companion-web/output/playwright/map-zoomed.png`
- Create: `companion-web/output/playwright/map-panned.png`
- Create: `companion-web/output/playwright/map-fog.png`
- Create: `companion-web/output/playwright/map-browser-report.json`

- [ ] Build the production web bundle and serve it with a local fixture responder that returns one normalized region and a sanitized local PNG through `/api/maps/{key}.png`.
- [ ] Open the page with Playwright Chromium at `1024x768`, snapshot the accessible controls, and assert the Pokédex Map shortcut uses a semantic map glyph without adding a row.
- [ ] Open Map and assert intrinsic/rendered aspect, stage-height ratio, visible controls, Settings/Legend→Area Dex order, and semantic icons.
- [ ] Exercise button zoom/recenter, one-pointer drag, genuine CDP two-touch pinch-out/pinch-in with midpoint anchoring, pointer cancel, and pinch selection suppression.
- [ ] Toggle fog and sample the intrinsic fog canvas to prove every pixel on all four edges is opaque black.
- [ ] Capture fit, zoomed, panned, and fog screenshots plus a JSON report; confirm no browser console errors.

### Task 6: Final affected verification and commit

**Files:**
- Review all files above.

- [ ] Run focused Kotlin, Vitest, production build, and browser verification from fresh outputs.
- [ ] Run `git diff --check` and inspect the complete diff for UI scope, icon semantics, optional no-map behavior, and accidental ROM/source identifiers.
- [ ] Commit only this clean presentation slice with `feat: present normalized world maps`.
