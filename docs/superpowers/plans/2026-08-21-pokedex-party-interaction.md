# Pokédex Badge and Party Roster Interaction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Attach affirmative caught state to Pokédex portraits, replace the persistent Party split view with an interactive 2×3 roster, and compose existing Trainer data as one cohesive card.

**Architecture:** Add a focused portrait wrapper that composes the existing sprite and a caught-only badge, leaving non-Organic eye state in row metadata. Reuse the existing `PartyDetail` content inside an accessible transient layer while making the six-slot grid the only persistent Party layout. Restructure only the Trainer Card markup/CSS so all existing normalized fields live inside one card shell.

**Tech Stack:** Preact, TypeScript, CSS, Vitest, Testing Library, Vite.

---

### Task 1: Lock Pokédex badge behavior

**Files:**
- Modify: `companion-web/src/components.test.ts`
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Modify: `companion-web/src/pages/PokedexDetail.test.ts`

- [ ] **Step 1: Write failing tests for portrait-owned caught state**

Assert that a caught species renders one `.caught-avatar-badge` inside `.pokedex-avatar`, that `.species-row-meta` contains no ball, and that an uncaught species has no caught badge. Keep the existing Organic eye-absence assertion.

- [ ] **Step 2: Run tests and verify RED**

Run:

```powershell
npm.cmd test -- --run src/components.test.ts src/pages/PokedexBrowse.test.tsx src/pages/PokedexDetailNavigation.test.tsx
```

Expected: FAIL because `.pokedex-avatar` and `.caught-avatar-badge` do not exist.

### Task 2: Implement the portrait badge

**Files:**
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/pages/PokedexDetail.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Add the caught-only components**

Add `CaughtBadge` and `PokedexAvatar`. `CaughtBadge` returns `null` unless `state?.caught` is true, uses catalog ball art when available, and otherwise renders the existing generic affirmative mark. `PokedexAvatar` positions that badge over `Sprite` without changing sprite knowledge behavior.

- [ ] **Step 2: Move Pokédex pages to the portrait wrapper**

Use `PokedexAvatar` in browse and detail. Remove Poké Ball rendering from `StatusMarks`; retain only the non-Organic eye there.

- [ ] **Step 3: Add portrait badge styling**

Make `.pokedex-avatar` a fitted relative wrapper and place `.caught-avatar-badge` at its lower-right corner with a compact, high-contrast plate that does not cover the Pokémon silhouette.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the Task 1 command. Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add companion-web/src/components.tsx companion-web/src/components.test.ts companion-web/src/pages/PokedexBrowse.tsx companion-web/src/pages/PokedexBrowse.test.tsx companion-web/src/pages/PokedexDetail.tsx companion-web/src/pages/PokedexDetail.test.ts companion-web/src/styles.css
git commit -m "Refine Pokédex caught badges"
```

### Task 3: Lock click-open Party behavior

**Files:**
- Modify: `companion-web/src/pages/PartyPage.test.tsx`

- [ ] **Step 1: Replace persistent-detail expectations with interaction expectations**

Assert six `.party-slot` buttons and a `.party-grid` whose contract is two columns. Assert nature, ability, moves, and the dialog are absent initially. Click an occupied card and assert the named dialog and existing details appear. Assert close and Escape remove it, while an empty slot does not open it.

- [ ] **Step 2: Add HP-bar and disappearing-selection tests**

Assert an occupied member with known HP renders a proportional `.party-hp-fill`; rerender with the selected slot empty and assert the dialog closes.

- [ ] **Step 3: Run the Party test and verify RED**

Run:

```powershell
npm.cmd test -- --run src/pages/PartyPage.test.tsx
```

Expected: FAIL because Party details are initially present and no dialog/close interaction exists.

### Task 4: Implement the Party roster and detail layer

**Files:**
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Make selection transient**

Keep the selected detail slot as nullable local state. Do not initialize it from the first occupied member. Card clicks set the slot and preserve the existing `onSelectSlot` callback. Close and Escape clear it. A party refresh that empties the selected slot also clears it.

- [ ] **Step 2: Add truthful HP bars to occupied cards**

Render `.party-hp-track > .party-hp-fill` only when current and maximum HP are known and the maximum is positive. Clamp the displayed percentage to 0–100 and retain numeric HP text.

- [ ] **Step 3: Move PartyDetail into an accessible layer**

Render the detail only for an explicitly selected occupied member. Wrap it in `.party-detail-layer` with `role="dialog"`, `aria-modal="true"`, a Pokémon-derived accessible name, a backdrop, and a visible close button.

- [ ] **Step 4: Preserve the 2×3 topology**

Change `.party-content` to a single roster region, keep `.party-grid` at two columns across supported widths, size its rows to share available height, and compact the cards under the existing narrow-layout media query instead of switching to one column.

- [ ] **Step 5: Run the Party test and verify GREEN**

Run the Task 3 command. Expected: all Party tests pass.

- [ ] **Step 6: Commit**

```powershell
git add companion-web/src/pages/PartyPage.tsx companion-web/src/pages/PartyPage.test.tsx companion-web/src/styles.css
git commit -m "Turn Party into an interactive roster"
```

### Task 5: Compose the existing Trainer Card details

**Files:**
- Modify: `companion-web/src/pages/TrainerCardPage.test.tsx`
- Modify: `companion-web/src/pages/TrainerCardPage.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write the failing shell-composition test**

Assert one `.trainer-card-shell` contains the Trainer name, ID, money, play time, seen, caught, stars, avatar, and all eight badge positions. Assert the old separate `.trainer-identity`, `.trainer-facts`, and `.trainer-badges.paper-panel` composition is absent.

- [ ] **Step 2: Run the Trainer Card test and verify RED**

```powershell
npm.cmd test -- --run src/pages/TrainerCardPage.test.tsx
```

Expected: FAIL because `.trainer-card-shell` does not exist.

- [ ] **Step 3: Compose the existing data inside one card**

Retain the current `Header`, callbacks, fields, artwork fallbacks, labels, and badge order. Replace only the internal layout with a title/ID strip, identity body, aligned facts, and badge strip inside `.trainer-card-shell`.

- [ ] **Step 4: Run the Trainer Card test and verify GREEN**

Run the Step 2 command. Expected: all Trainer Card tests pass.

- [ ] **Step 5: Commit**

```powershell
git add companion-web/src/pages/TrainerCardPage.tsx companion-web/src/pages/TrainerCardPage.test.tsx companion-web/src/styles.css
git commit -m "Compose Trainer details as a card"
```

### Task 6: Verify and release

**Files:**
- Modify: `README.md`
- Modify: `release/v1-ready.json`
- Create: `release/RELEASE_NOTES_1.1.0-rc.24.md`
- Modify: `.github/workflows/release.yml`
- Modify: `tools/release/release-workflow.test.mjs`

- [ ] **Step 1: Run focused UI tests**

```powershell
npm.cmd test -- --run src/components.test.ts src/pages/PokedexBrowse.test.tsx src/pages/PokedexDetailNavigation.test.tsx src/pages/PartyPage.test.tsx src/pages/TrainerCardPage.test.tsx
```

- [ ] **Step 2: Run the complete web regression and build**

```powershell
npm.cmd test -- --run
npm.cmd run build
```

Expected: zero failed tests and a successful Vite production build.

- [ ] **Step 3: Prepare RC24 metadata**

Document the portrait badge and interactive Party board, add readiness markers, and make the protected workflow consume the RC24 release notes.

- [ ] **Step 4: Run release-policy tests**

```powershell
node --test tools/release/*.test.mjs
```

Expected: zero failures.

- [ ] **Step 5: Commit and publish through the protected tag workflow**

Commit the RC24 metadata, fast-forward only after fetching the current remote state, create the exact `v1.1.0-rc.24` tag, dispatch from that tag, and verify package identity, SHA-256 checksums, provenance, and signing certificate without installing the APK.
