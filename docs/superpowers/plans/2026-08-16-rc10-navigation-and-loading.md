# RC10 Navigation and Loading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Atlas Pokédex icon return to the last-used Pokédex tab, use truthful cyan-square Atlas markers, hide idle guidance during loading, and display validated live game time in a correctly aligned upper toolbar.

**Architecture:** Keep navigation, marker, loading, and alignment changes inside the existing Preact application boundary. Extend the existing parser-owned Gen III runtime descriptor with an optional structurally resolved live-clock address; the battle-memory coordinator validates and publishes hours/minutes through the existing companion state/API stream. `MapPage` exposes a parameterless global Pokédex callback, while `App` owns the normal `SCREEN=POKEDEX` action; `Welcome` conditionally renders its idle sentence from the same loading predicate already used for actions and progress.

**Tech Stack:** Kotlin, parser-core, battle-memory, companion-core, Android runtime, Preact, TypeScript, Vitest, Testing Library, Vite

---

### Task 1: Lock the Atlas navigation contract

**Files:**
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/App.production.test.tsx`

- [ ] **Step 1: Write failing tests**

Change the MapPage callback to `onOpenPokedex: () => void`, assert the header action remains enabled after selecting a map point with no encounters, click it, and assert the callback fires. In the application shell, open Atlas, click the Pokédex action, and assert `action('SCREEN', { screen: 'POKEDEX' })` is sent without a `filter` field.

- [ ] **Step 2: Run the tests and observe RED**

Run: `npm.cmd test -- --run src/pages/MapPage.test.tsx src/App.production.test.tsx`

Expected: TypeScript/test failures because Atlas still exposes `onOpenAreaDex` and sends `MAP_AREA`.

- [ ] **Step 3: Implement global Pokédex navigation**

In `MapPage.tsx`, replace the area callback with a parameterless Pokédex callback, remove encounter-based enablement, and leave the button enabled. In `App.tsx`, close Atlas and send `SCREEN` with `{ screen: 'POKEDEX' }`.

- [ ] **Step 4: Run the focused tests**

Run: `npm.cmd test -- --run src/pages/MapPage.test.tsx src/App.production.test.tsx`

Expected: both test files pass.

### Task 2: Hide idle guidance while loading

**Files:**
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `companion-web/src/App.tsx`

- [ ] **Step 1: Add loading-state assertions**

Assert `Choose a Pokémon game to begin.` remains present in the idle welcome test and is absent in both determinate and indeterminate loading tests.

- [ ] **Step 2: Run the loading tests and observe RED**

Run: `npm.cmd test -- --run src/App.production.test.tsx`

Expected: the two loading assertions fail because the idle sentence is unconditional.

- [ ] **Step 3: Render idle guidance only when inactive**

Move the welcome sentence into the inactive branch immediately before the setup actions. Keep the branding and progress component unchanged.

- [ ] **Step 4: Run the focused tests**

Run: `npm.cmd test -- --run src/App.production.test.tsx src/pages/MapPage.test.tsx`

Expected: both test files pass.

### Task 3: Replace Poké Ball location markers

**Files:**
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Add marker semantics assertions**

Assert Atlas location buttons expose the `atlas-location-marker` class, the current location retains `is-current`, and the Local Map player position retains `map-player-marker` without the Atlas marker class.

- [ ] **Step 2: Run the focused test and observe RED**

Run: `npm.cmd test -- --run src/pages/MapPage.test.tsx`

Expected: marker-class assertions fail because Atlas and Local Map still share the generic `map-marker` styling.

- [ ] **Step 3: Implement cyan-square Atlas markers**

Add `atlas-location-marker` only to Atlas location buttons. Style it as a compact cyan square with the existing current pulse and selected outline. Keep the Local Map player marker circular and visually independent.

- [ ] **Step 4: Run the focused test**

Run: `npm.cmd test -- --run src/pages/MapPage.test.tsx`

Expected: marker semantics and navigation tests pass.

### Task 4: Resolve and publish the live game clock

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3RuntimeMemoryLayoutResolver.kt`
- Modify: `battle-memory/src/main/kotlin/com/darkaxt/dualscreendex/battle/Gen3RuntimeMemoryDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualscreendex/battle/BattleMemoryCoordinator.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/components.tsx`

- [ ] **Step 1: Freeze the real Modern Emerald contract**

Add a real-ROM parser control that proves one unambiguous EWRAM clock root through compiled hour/minute consumers and publishes the signed-byte field layout. Production selection must not consume fixture identity, source symbols, or fixed ROM/RAM offsets.

- [ ] **Step 2: Publish only validated live time**

Read the clock through the existing live-memory cycle. Accept hours `0..23` and minutes `0..59`; otherwise publish no time. Propagate the optional value through companion state and API without substituting Android wall time or trainer play time.

- [ ] **Step 3: Render and align the header**

Render zero-padded `HH:MM` at the geometric center of the upper toolbar when available. Keep the Pokédex title left-aligned in its own column and ensure the clock never intercepts toolbar actions.

- [ ] **Step 4: Run focused real/runtime/UI verification**

Run the exact Modern parser control, runtime decoder/coordinator tests, companion API tests, and focused header tests. Confirm unavailable/invalid clocks remain absent.

### Task 5: Verify and release RC10

**Files:**
- Modify: `README.md`
- Modify: `.github/workflows/release.yml`
- Modify: `release/v1-ready.json`
- Create: `release/RELEASE_NOTES_1.1.0-rc.10.md`

- [ ] **Step 1: Run the complete web verification**

Run: `npm.cmd test -- --run`

Expected: all companion-web tests pass.

Run: `npm.cmd run build`

Expected: TypeScript and Vite production build pass.

- [ ] **Step 2: Prepare non-replacing RC10 metadata**

Update workflow examples and release-note selection to `v1.1.0-rc.10`; record the navigation/loading marker in `release/v1-ready.json`; document the behavior in README and RC10 notes.

- [ ] **Step 3: Verify release policy and diff**

Run: `node --test tools/release/*.test.mjs`

Expected: all release policy tests pass.

Run: `git diff --check`

Expected: no output.

- [ ] **Step 4: Commit and publish**

Commit the implementation and RC10 metadata, push `release/v1.1.0-rc.10`, create annotated tag `v1.1.0-rc.10`, and dispatch the protected release workflow from that exact tag. Do not install or launch the APK on a device.
