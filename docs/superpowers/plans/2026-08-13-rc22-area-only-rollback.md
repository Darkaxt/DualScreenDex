# RC22 Area-Only Rollback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publish an RC19-based signed prerelease that retains only the Organic Area masked roster, ordered with known Pokémon first.

**Architecture:** Keep RC19's models, runtime, navigation, and parser unchanged. Derive the candidate Area roster entirely in `PokedexBrowse` from the already-published `catalog.areas` and `state.currentAreaIds`, then render identity masking through the shared sprite component. This deliberately carries no Map First code.

**Tech Stack:** Preact, TypeScript, Vitest, Vite, Android/Gradle protected release workflow.

---

### Task 1: Prove the Area-only contract

**Files:**
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Modify: `companion-web/src/App.production.test.tsx`

- [ ] Add a failing Area test with one seen encounter, one caught encounter, one unseen encounter, and one caught non-encounter. Assert visible order is seen then caught then `??????????`; assert `#???`, disabled unidentified row, and absence of the caught non-encounter.
- [ ] Add production assertions that the screen union excludes `MAP`, `App.tsx` has no `WorldMapPage`, and Pokédex headers expose no `Open Map` or `Show on Map` action.
- [ ] Run `npm test -- --run src/pages/PokedexBrowse.test.tsx src/App.production.test.tsx` and confirm the Area test fails because Organic currently removes unseen rows.

### Task 2: Implement the minimum roster and masking

**Files:**
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] Extend `Sprite` with `silhouette?: boolean`, use an identity-neutral alt label, and export a name masker that preserves spaces while replacing every other character with `?`.
- [ ] In Organic Area only, derive unique parsed species IDs from current area rows, retain unknown candidates, sort known identities ahead of unknown identities while preserving catalog order within each group, and suppress hidden identities during search.
- [ ] Render hidden rows disabled with `#???`, masked names, and a black sprite filter. Continue using parsed day/night markers.
- [ ] Rerun the focused tests and require zero failures.

### Task 3: Validate and publish RC22

**Files:**
- Modify: `release/RELEASE_NOTES_1.0.0.md`
- Modify: `README.md`

- [ ] Document that RC22 removes Map First completely and retains the Organic Area roster with known-first ordering.
- [ ] Run the complete web suite and production build.
- [ ] Run `:app:lintDebug`, affected JVM tests, and `:app:assembleRelease` through the repository's release workflow prerequisites.
- [ ] Confirm `git diff v1.0.0-rc.19` contains no world-map production path or `MAP` screen literal.
- [ ] Commit, push, publish protected signed `v1.0.0-rc.22`, independently verify its SHA-256 and signer, and install only that signed production APK on Thor serial `bfa98654`.
