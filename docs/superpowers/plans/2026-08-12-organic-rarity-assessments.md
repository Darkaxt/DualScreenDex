# Organic Rarity Assessments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace technical rarity explanations with stable recruitment advice for each final-star band.

**Architecture:** Add a pure copy-selection function beside the rarity UI and render its result whenever a final star rating exists. Preserve the existing title, star visualization, and unavailable fallback.

**Tech Stack:** TypeScript, Preact, Vitest, Testing Library.

---

### Task 1: Lock the player-facing star bands

**Files:**
- Modify: `companion-web/src/pages/BattlePage.test.tsx`
- Modify: `companion-web/src/pages/BattlePage.tsx`

- [x] **Step 1: Write the failing test**

Add table-driven assertions covering 0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5, and 5 stars. Assert the expected player-facing assessment and assert that the rendered rarity panel contains none of `Compared with`, `encounter table`, `First word`, `Second`, or a route name.

- [x] **Step 2: Run the focused test to verify it fails**

Run: `npm test -- --run src/pages/BattlePage.test.tsx`

Expected: FAIL because the current panel still renders the encounter-table explanation.

- [x] **Step 3: Implement the minimal rating-to-copy mapping**

Add a pure `rarityAssessment(stars: number): string` function with five upper-bound bands and render it whenever `rarity.stars` is non-null. When the rating is unavailable, omit the explanatory paragraph instead of exposing diagnostic text.

- [x] **Step 4: Run focused tests**

Run: `npm test -- --run src/pages/BattlePage.test.tsx`

Expected: all BattlePage tests pass.

- [x] **Step 5: Run complete web verification**

Run: `npm test -- --run && npm run build`

Expected: all web tests pass and Vite produces the production bundle.

- [ ] **Step 6: Commit and release**

Commit the verified copy change, publish the next public non-draft prerelease through the protected signing workflow, verify the signed APK, and install it with `adb install -r` without launching the app.
