# Loading Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace startup setup actions with real catalog-loading progress while preserving recovery actions after idle or failure states.

**Architecture:** Keep the existing Kotlin/API loading model unchanged. Add a focused Preact loading component inside `App.tsx`, feed it `busy` plus `CatalogLoading`, and style its determinate and indeterminate states in the existing Welcome screen.

**Tech Stack:** Preact, TypeScript, Vitest/Testing Library, CSS, Vite.

---

### Task 1: Lock the Welcome loading contract

**Files:**
- Modify: `companion-web/src/App.production.test.tsx`

- [x] **Step 1: Add a failing catalog-loading test**

Render a bootstrap response with `catalog: null` and `loading.active: true`; assert `Loading ROM identity`, a progress bar with `aria-valuenow="0"` and `aria-valuemax="5"`, and the absence of `LOAD ROM OR ZIP` and `CONNECT RETROARCH`.

- [x] **Step 2: Add a failing bootstrap-pending test**

Hold the bootstrap promise unresolved; assert an indeterminate `Loading companion state` progress bar and no setup actions.

- [x] **Step 3: Run the focused test and observe RED**

Run: `npm.cmd test -- --run src/App.production.test.tsx`

Expected: FAIL because the Welcome screen still renders setup actions and has no progress bar.

### Task 2: Implement the progress presentation

**Files:**
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/styles.css`

- [x] **Step 1: Pass loading state into Welcome**

Replace the `busy={busy || state.loading.active}` contract with separate `busy`, `loading`, and derived phase label inputs. Suppress the floating loading badge when no catalog exists because the Welcome progress block owns that state.

- [x] **Step 2: Add `WelcomeLoadingProgress`**

Use the real completed and total units for determinate width and ARIA values. Use an indeterminate CSS class when only bootstrap is active or total units are unavailable.

- [x] **Step 3: Render actions only while idle**

Keep the existing upload and RetroArch controls byte-for-byte in behavior, but render them only when `busy` and `loading.active` are both false.

- [x] **Step 4: Style the progress block**

Add a compact bar that fits the existing Welcome layout, uses the existing acid/forest palette, and respects reduced-motion by disabling indeterminate animation.

- [x] **Step 5: Run focused tests and observe GREEN**

Run: `npm.cmd test -- --run src/App.production.test.tsx`

Expected: all App production tests pass.

### Task 3: Simplify and frame the Atlas header

**Files:**
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/styles.css`

- [x] **Step 1: Lock the header contract RED**

Assert the generic world-map/family title is absent, selected location context is left-aligned, and Settings plus Area Pokédex are the two right header actions.

- [x] **Step 2: Implement the right header actions**

Export the existing Settings glyph, move Settings and Area Pokédex into a header action group, and keep the canvas utility rail only for marker visibility and multi-region selection.

- [x] **Step 3: Frame the map raster**

Add a warm-brown non-layout-affecting perimeter shadow to the map plane so its edge remains visually stable while panning.

- [x] **Step 4: Run focused tests GREEN**

Run: `npm.cmd test -- --run src/pages/MapPage.test.tsx`

Expected: all Map presentation tests pass.

### Task 4: Verify, commit, and publish

**Files:**
- Modify: `README.md`
- Create: `release/RELEASE_NOTES_1.1.0-rc.4-hotfix.3.md`
- Modify: `release/v1-ready.json`
- Modify: `.github/workflows/release.yml`

- [x] **Step 1: Run the affected web gate**

Run: `npm.cmd test -- --run` and `npm.cmd run build` from `companion-web`.

Expected: Vitest and production Vite build succeed.

- [ ] **Step 2: Commit the feature**

Commit the Preact, CSS, tests, design, and plan as `feat: show catalog loading progress`.

- [ ] **Step 3: Prepare monotonic hotfix 3 metadata**

Document the loading-only UI change, require its ready marker, and point the protected workflow at the new release notes.

- [ ] **Step 4: Commit and publish**

Commit as `release: prepare DualDex rc4 hotfix 3`, tag `v1.1.0-rc.4-hotfix.3`, push to `Darkaxt/DualScreenDex`, and dispatch the protected signing workflow.

- [ ] **Step 5: Install without launching**

Install the signed APK over the existing Thor package with `adb -s bfa98654 install -r`, remove the temporary APK, and do not launch or inspect the app.
