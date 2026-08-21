# Cross-page UI Conformance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce one ROM-derived visual contract across every normal page, make Party and Trainer Card match the supplied game's composition quality, shrink the caught badge by 20%, and confine all diagnostics to Settings → Debug.

**Architecture:** Keep the existing `CatalogTheme` and routing models. Strengthen the browser contract so it inspects computed surfaces on every route/tab, then replace hard-coded legacy chrome with semantic theme aliases scoped to `GAME`. Restructure only Party slot markup where required for the reference hierarchy; remove diagnostic shell/header copy without changing state or navigation.

**Tech Stack:** Preact, TypeScript, CSS custom properties, Vitest, Testing Library, Playwright/Helium, and Vite.

---

### Task 1: Lock the production information boundary

**Files:**
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Modify: `companion-web/src/pages/BattlePage.test.tsx`
- Modify: `companion-web/src/pages/PartyPage.test.tsx`
- Modify: `companion-web/src/pages/TrainerCardPage.test.tsx`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/pages/BattlePage.tsx`
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/pages/TrainerCardPage.tsx`

- [x] Write failing tests proving `.rom-status` is absent and normal pages do not render the ROM filename, CRC, family, `ORGANIC`, `DISCOVERED`, `LIVE · OWNED POKÉMON`, or `LIVE · READ ONLY` as page-header diagnostics.
- [x] Run the focused tests and require failure against RC24.
- [x] Remove the global ROM status strip and `with-rom-status` offset. Remove redundant normal-page kickers; retain diagnostic copy only inside Settings → Debug pages.
- [x] Run the focused tests and require GREEN.
- [x] Commit the information-boundary correction.

### Task 2: Lock Party and caught-badge composition

**Files:**
- Modify: `companion-web/src/components.test.ts`
- Modify: `companion-web/src/pages/PartyPage.test.tsx`
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/styles.css`

- [x] Add RED assertions that `.caught-avatar-badge` computes to 22px and contained ball art/fallback to 18px/15px in the production browser.
- [x] Add RED Party assertions for a dedicated identity row, level/sex placement, optional secondary species name, HP label, EXP-before-HP order, quiet empty slots, and unchanged two-column/six-slot/dialog behavior.
- [x] Refactor each occupied card to:

```tsx
<span class="party-slot-copy">
  <span class="party-slot-heading">
    <strong>{displayName}</strong>
    {member.gender && <i>{member.gender}</i>}
    {member.level != null && <small>Lv {member.level}</small>}
  </span>
  {nicknameDiffers && <span class="party-slot-species">{member.speciesName}</span>}
  <span class="party-slot-vitals"><b>HP</b><i>{hpLabel(member)}</i></span>
  <span class="party-exp-track">…</span>
  <span class="party-hp-track">…</span>
</span>
```

- [x] Replace repeated empty-slot text with a subdued visual placeholder while preserving `aria-label="Party slot N: Empty"`.
- [x] Apply compact ROM-derived card framing, a strong selected state, EXP at half HP height, and reference-like visual density at 4:3.
- [x] Run focused Party/Pokédex tests and require GREEN.
- [x] Commit Party and caught-badge composition.

### Task 3: Enforce the theme on every computed surface

**Files:**
- Modify: `companion-web/e2e/rom-derived-theme.spec.ts`
- Modify: `companion-web/src/styles.css`

- [x] Add a browser helper that checks computed `backgroundColor`, `borderColor`, and foreground color against `theme.tokens` or an explicit `color-mix()` result.
- [x] Visit Pokédex browse; all five detail tabs; Move/Ability Detail; all four Battle tabs; Party and dialog; Trainer Card; local map; Atlas; Pokémon AREA; Settings; Setup; loading/welcome; Capability Report; and Memory Mapper.
- [x] Assert RED for the known legacy surfaces: `.map-page-header`, `.map-control`, `.map-legend-panel`, `.pokemon-area-panel`, `.battle-content`, `.battle-identity`, inactive `.segmented` controls, `.party-content`, `.party-slot`, `.trainer-card-content`, `.trainer-card-shell`, and debug-page cards.
- [x] Add `GAME`-scoped semantic aliases and explicit selectors so all non-semantic surfaces use `--theme-field`, `--theme-field-pattern`, `--theme-header`, `--theme-header-shadow`, `--theme-menu`, `--theme-menu-shadow`, `--theme-panel`, `--theme-border`, `--theme-text`, `--theme-accent`, and `--theme-accent-text`.
- [x] Preserve HP/EXP/status/type colors, Atlas cyan square/player marker, black fog/stage, source rasters, brown raster frame, error red, and fixed Dark/Light/High Contrast behavior.
- [x] Rerun the expanded browser gate and require GREEN with no viewport overflow.
- [x] Commit cross-page theme conformance.

### Task 4: Perform the visual conformance review

**Files:**
- Create evidence only under: `D:\Temp\dualdex-rc25-ui-audit\theme-objective-final13`
- Create: `docs/reports/2026-08-21-ui-font-size-matrix.md`

- [x] Build the production web bundle.
- [x] Capture the full 4:3 page/tab matrix with the extreme yellow/blue theme fixture so any legacy green is visually obvious.
- [x] Compare Party against the lossless crop, measured 2.27:1 reference cards, and `D:\Temp\dualdex-rc25-ui-audit\party-installed-rc24.png`. Final browser geometry is 94% content width, 88% content height, 2.38:1 occupied-card ratio, 52% portrait share, and symmetric 6% top/bottom insets; identity uses two lines and EXP/HP is bottom-aligned.
- [x] Inspect and correct the shared Pokédex action icon in Atlas, Battle, and Pokémon AREA. One monochrome `currentColor` glyph is used everywhere; Battle now renders it directly on the identity rail with no white or olive tile.
- [x] Enrich Rarity with the same accessible five-star meter at the top of the card and a theme-derived shader whose visual intensity follows the rating.
- [x] Expand Pokédex browse rows to portrait-led roster cards with known type metadata, including the sparse Dark-theme matrix, without leaking locked Organic data.
- [x] Correct Capability Report and Memory Mapper contrast using the derived accent foreground/background pair.
- [x] Generate a browser-computed per-layout font matrix, normalize inherited micro-copy to an 11.25px auxiliary tier, and enforce minimum 11.2px plus average 12px across every captured route without flattening title/value hierarchy.
- [x] Fix every observed mismatch and rerun the affected browser cases until the measured matrix is clean.

### Task 5: Verify and prepare the prerelease

**Files:**
- Modify release metadata and notes only after Tasks 1–4 are GREEN.

- [x] Run focused component tests, then `npm.cmd test -- --run` (22 files, 145 tests).
- [x] Run `npm.cmd run build` (also executed by the final Playwright web server).
- [x] Run the full expanded `rom-derived-theme.spec.ts` using Helium and the sanitized fixture (1/1, evidence and font matrix in `theme-objective-final13`).
- [x] Run `git diff --check` and inspect the complete diff for unrelated changes.
- [ ] Commit release metadata and publish the next protected prerelease without installing it on a device.
