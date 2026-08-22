# Loading Origin Colour Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Colour loading messages red for a full parse and yellow for a persisted-catalog reopen.

**Architecture:** Derive one presentation-only origin value from `State.loading.phase` in the web layer. Reuse it on both loading surfaces and add scoped CSS colours without changing the progress bar or API.

**Tech Stack:** Preact, TypeScript, Vitest, CSS.

---

### Task 1: Lock the loading-origin contract

**Files:**
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `companion-web/src/App.tsx`

- [x] **Step 1: Add failing component assertions**

Render active `ROM_IDENTITY` and `CACHE_REOPEN` states and require `.loading-origin-parse` and `.loading-origin-cache` respectively on the status surface.

- [x] **Step 2: Run the focused test and verify RED**

Run: `npm test -- --run src/App.production.test.tsx`

Expected: the new origin-class assertions fail because the classifier and classes are absent.

- [x] **Step 3: Implement one presentation classifier**

Add `loadingOriginClass(loading)` returning an empty string for inactive loading, `loading-origin-cache` for `CACHE_REOPEN`, and `loading-origin-parse` for every other active phase. Apply it to `WelcomeLoadingProgress` and the compact `.loading-indicator`.

- [x] **Step 4: Rerun the focused test**

Run: `npm test -- --run src/App.production.test.tsx`

Expected: all `App.production` tests pass.

### Task 2: Apply the approved colours

**Files:**
- Modify: `companion-web/src/styles.css`
- Test: `companion-web/src/App.production.test.tsx`

- [x] **Step 1: Add scoped message colours**

Set the message text to red for `.loading-origin-parse` and yellow for `.loading-origin-cache`. Do not target `.welcome-progress` or its child fill.

- [x] **Step 2: Run the affected web gate**

Run: `npm test -- --run src/App.production.test.tsx src/pages/AbilityDetail.test.tsx src/pages/PartyPage.test.tsx src/pages/PokedexDetailNavigation.test.tsx src/pages/MapPage.test.tsx`

Expected: all selected tests pass with no changed progress assertions.

- [x] **Step 3: Include the change in the next sequential RC**

Run the repository release verification, build the signed APK through the existing release workflow, and publish the next sequential RC without installing it.
