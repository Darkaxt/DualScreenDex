# ROM-derived companion theme implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `executing-plans` and preserve checkbox state as each task is completed. Execute continuously through verification and a dedicated commit; do not stop for intermediate approval.

**Goal:** Replace the fixed `GAME` shell palette with one deterministic, catalog-persisted Emerald-hybrid theme derived from the loaded ROM's normalized visual evidence, while keeping `DARK`, `LIGHT`, high contrast, navigation, maps, and information policy unchanged.

**Architecture:** Add a complete `CatalogTheme` value to `ParsedCatalog`. A pure `RomThemeMaterializer` consumes normalized raster evidence after asset materialization, samples each asset class with equal bounded influence, quantizes deterministically, assigns semantic roles, and enforces contrast. Missing, malformed, ambiguous, or insufficient evidence returns a complete neutral theme without failing the catalog. CatalogStore persists the theme as its own schema section; the companion API projects it; the web shell applies its tokens only for `GAME` through CSS custom properties.

**Tech Stack:** Kotlin/JVM parser and catalog modules, Gson/SQLite catalog persistence, Kotlin companion API, TypeScript/Preact companion web, CSS custom properties, Gradle, Vitest, Playwright/Helium.

---

## Task 1: Implement the deterministic theme contract and materializer

**Files:**

- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogTheme.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RomThemeMaterializer.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/RomThemeMaterializerTest.kt`

- [x] Add `CatalogThemeMethod` (`DIRECT_UI_PALETTE`, `MULTI_ASSET_QUANTIZATION`, `NEUTRAL_FALLBACK`), `CatalogThemeAssetClass`, complete semantic RGB tokens, provenance, and a valid readable neutral factory.
- [x] Write RED tests for deterministic output under reordered assets, equal per-class influence, transparent-pixel exclusion, single-class rejection, malformed/empty evidence fallback, neutral DMG treatment, and complete output.
- [x] Implement a pure materializer over normalized `RgbaSprite` inputs. Cap samples per asset and per class, reduce RGB precision before histogramming, use stable count/RGB ordering, and never depend on input iteration order.
- [x] Assign field/header/menu/panel/border/text/accent roles by luminance, chroma, population, and distance. Derive related shadows/patterns without altering source rasters.
- [x] Enforce WCAG contrast: 4.5:1 for normal text and 3:1 for large/control surfaces. Record whether token correction was required; fall back to neutral if a complete readable set cannot be produced.
- [x] Add a direct-palette input seam that succeeds only when one complete structurally nominated palette is supplied; multiple nominees fall through to multi-asset resolution.
- [x] Run only `RomThemeMaterializerTest` until GREEN.

## Task 2: Materialize and persist one stable theme per catalog

**Files:**

- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParserTest.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [x] Add non-null `theme: CatalogTheme` to `ParsedCatalog`, defaulting to the explicit neutral theme for source compatibility and failure safety.
- [x] Build normalized evidence after species sprites, Trainer assets, world maps, and local maps have resolved. Use bounded representative species IDs and every available independent non-species asset class; do not read ROM title/family/SHA or raw linked offsets.
- [x] Wrap only optional theme materialization in `runCatching`; any exception publishes `NEUTRAL_FALLBACK` while preserving the rest of the catalog.
- [x] Add `theme` as a required CatalogStore section and bump `parserSchemaVersion` from 19 to 20 so stale caches are rebuilt rather than silently receiving a synthetic theme.
- [x] Write RED/GREEN tests for complete catalog themes, optional failure isolation, exact token/provenance SQLite round-trip, required-section enforcement, and schema invalidation.
- [x] Run the focused parser/catalog tests and commit the core/persistence checkpoint.

## Task 3: Project the theme through the API and apply it only to GAME

**Files:**

- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/ApiViewBuilderTest.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/styles.css`
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `companion-web/src/pages/SettingsPage.test.tsx`

- [x] Add a complete API theme view with lowercase CSS hex tokens plus method, asset classes, and contrast-correction provenance.
- [x] Write RED API and web tests proving exact token projection and root CSS-variable application.
- [x] In `App.tsx`, apply the persisted tokens as typed inline custom properties only when the selected setting is `GAME`; omit them for `DARK` and `LIGHT`.
- [x] Refactor the default shell variables in `styles.css` to semantic `--theme-*` tokens and add the approved Emerald-hybrid grammar: subtle field pattern, compact chromatic header, cream/menu surface, hard paired borders, restrained pixel shadow, and readable panels.
- [x] Preserve semantic type/status/HP colors and all raster images. Keep fixed Dark/Light selectors and high-contrast overrides authoritative over the ROM theme.
- [x] Run the focused API/Vitest suites and a production web build.

## Task 4: Freeze exact real-ROM themes and the user-visible visual contract

**Files:**

- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/RomDerivedThemeLiveRomTest.kt`
- Create or modify: `app/src/test/java/com/darkaxt/dualdex/web/RomDerivedThemeApiRealControlTest.kt`
- Create: `companion-web/e2e/rom-derived-theme.spec.ts`
- Create: `docs/reports/2026-08-20-rom-derived-companion-theme.md`

- [x] Parse official Red, official Crystal, official Emerald, Unbound v2.1.1.1, and Odyssey v4.1.1 twice from the consolidated/reference fixture paths; assert exact rehash, complete theme, deterministic tokens, method, and contributor classes.
- [x] Require every exact control to persist/reopen the same theme and return the same API JSON. Record direct, multi-asset, or neutral provenance truthfully; do not count fallback as decoded palette evidence.
- [x] Serve representative fixture states for Atlas, local map, Pokédex, detail, Trainer Card, Party, Battle, loading, and Settings in a production 4:3 Helium viewport.
- [x] Assert the same GAME variables on every screen, fixed Dark/Light variables after switching, effective high-contrast overrides, unchanged navigation targets, unchanged map pixels/fog, and no layout overflow.
- [x] Capture screenshots under `D:\Temp\dualdex-rom-theme-evidence` and visually inspect the approved Emerald-hybrid grammar before freezing the report.

## Task 5: Verify, document, clean, and commit

- [x] Run the focused theme tests first, then one affected-module gate: `:parser-core:test`, `:catalog-store:test`, `:companion-core:test`, relevant `:app:testDebugUnitTest`, `companion-web` Vitest, production build, and the theme Playwright spec.
- [x] Search production code for ROM names, titles, hashes, fixed addresses, base-ROM theme switches, and partial theme construction; require zero identity selectors.
- [x] Run `git diff --check`, remove task-owned diagnostics/evidence not referenced by the report, and keep unrelated/map work untouched.
- [x] Complete the report with exact per-ROM tokens/provenance, fallback boundaries, persistence/API evidence, visual evidence, commands, and hashes.
- [x] Commit the completed ROM-derived theme stage as one implementation commit after all gates are GREEN.

## Completion boundary

The task is complete only when every loaded catalog has one readable, deterministic, persisted theme; `GAME` uses it consistently across all companion screens; `DARK`, `LIGHT`, and high contrast retain their existing semantics; optional theme failure cannot abort parsing; and exact real-ROM plus browser evidence is committed. A palette guessed from a ROM identity, a single dominant sprite, an unpersisted CSS-only preview, or fallback described as decoded evidence does not count.
