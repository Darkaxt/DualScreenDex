# Evolution and Pokédex Identity Presentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Apply the approved unknown, seen, and captured sprite/name behavior consistently to evolution targets, Pokédex rows, and the Pokédex detail avatar while preserving current visibility and navigation policies.

**Architecture:** Centralize knowledge-state derivation and identity masking in `components.tsx`, then make the evolution and Pokédex consumers render through that shared contract. No API, catalog, knowledge-ledger, or filter-membership change is required.

**Tech Stack:** Preact, TypeScript, Vitest, Testing Library, CSS, Vite, Android loopback-hosted companion web assets.

---

### Task 1: Freeze the shared identity contract

**Files:**
- Modify: `companion-web/src/components.test.ts`
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Modify: `companion-web/src/pages/PokedexDetailNavigation.test.tsx`

- [x] **Step 1: Add failing component tests**

Assert that Organic state derives `unknown` for no ledger entry, `seen` for `seen=true`, and `captured` for `caught=true` even when `seen=false`; assert that Discovered derives `captured` presentation.

- [x] **Step 2: Add failing Pokédex-row tests**

Use an Organic Area fixture containing unknown, seen, and captured species. Require unknown to render a black silhouette and masked name without navigation; seen to render grayscale with the real name and navigation; captured to render full color with the real name and navigation. Require the same sprite treatment for the selected species on Pokédex detail. Preserve Organic All exclusion.

- [x] **Step 3: Retain evolution navigation assertions**

Require the same three classes/names in evolution rows and verify that only seen/captured targets dispatch `OPEN_SPECIES` for the exact target ID.

- [x] **Step 4: Run RED**

```powershell
npm.cmd test -- --run src/components.test.ts src/pages/PokedexBrowse.test.tsx src/pages/PokedexDetailNavigation.test.tsx
```

Expected: new shared-helper/import or Pokédex seen-grayscale assertions fail before implementation.

### Task 2: Implement one shared species-identity renderer

**Files:**
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/pages/PokedexDetail.tsx`
- Modify: `companion-web/src/styles.css`

- [x] **Step 1: Define the shared state**

Add `SpeciesIdentityKnowledge = 'unknown' | 'seen' | 'captured'` and a pure `speciesIdentityKnowledge(mode, state)` function. Return captured outside Organic; inside Organic, caught wins over seen and missing state is unknown.

- [x] **Step 2: Extend `Sprite`**

Replace the silhouette-only boolean with the shared knowledge value. Map unknown to `identity-silhouette` and an unidentified accessible label, seen to `identity-seen` and the real ROM name, and captured to no filter class.

- [x] **Step 3: Migrate Pokédex rows**

Derive knowledge once per row, mask the name only for unknown, disable only unknown rows already exposed by Organic Area, and retain the exact `OPEN_SPECIES` action for seen/captured rows.

- [x] **Step 4: Migrate evolution rows and detail avatar**

Use the same helper, mask function, sprite classes, and navigation predicate. Keep the existing target lookup and reset the detail tab to Entry before opening the exact target. Pass the selected species knowledge state through the shared sprite component for the large detail avatar.

- [x] **Step 5: Unify CSS**

Apply black silhouette and grayscale styles through shared identity classes for both sprite-frame sizes. Remove evolution-only duplicate filtering while retaining evolution layout styles.

- [x] **Step 6: Run GREEN**

```powershell
npm.cmd test -- --run src/components.test.ts src/pages/PokedexBrowse.test.tsx src/pages/PokedexDetailNavigation.test.tsx
```

Expected: all focused tests pass.

### Task 3: Verify the APK web vertical

**Files:**
- Modify only if a regression is found: `companion-web/src/App.production.test.tsx`
- Modify only if a regression is found: `app/src/test/kotlin/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt`

- [x] **Step 1: Run the complete web suite**

```powershell
npm.cmd test -- --run
npm.cmd run build
```

Expected: all Vitest tests and the production Vite build pass.

- [x] **Step 2: Run the Android-host seam**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.darkaxt.dualdex.web.AndroidLoopbackServerTest' --no-daemon --console=plain
```

Expected: loopback assets/API remain green; no catalog/schema changes are needed.

- [x] **Step 3: Review and commit**

```powershell
git diff --check
git add companion-web/src/components.tsx companion-web/src/components.test.ts companion-web/src/pages/PokedexBrowse.tsx companion-web/src/pages/PokedexBrowse.test.tsx companion-web/src/pages/PokedexDetail.tsx companion-web/src/pages/PokedexDetailNavigation.test.tsx companion-web/src/styles.css
git commit -m "feat: unify Pokedex identity discovery states"
```

Expected: a focused frontend commit after the parser/evidence commit, with no device, emulator, or release action.
