# Gen III Map Core Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore a fail-closed normalized Gen III world-map parser/compositor/catalog pipeline on RC24 using exact real-ROM controls.

**Architecture:** Two explicit source-family-independent GBA format contracts render normalized rasters: one-byte 8bpp affine `64x64`, and banked 4bpp text `30x20`. Parser discovery terminates at this normalized boundary; catalog persistence and asset serving consume only raster bytes, dimensions, grid geometry, and semantic locations.

**Tech Stack:** Kotlin/JVM, JUnit 4, Gradle, SQLite catalog store, Android loopback server unit tests.

---

### Task 1: Freeze exact compositor controls

**Files:**
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3WorldMapRealControlTest.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/sprite/TileRendererTest.kt`

- [ ] Add environment-gated tests that read official Emerald, Modern Emerald, Classic, FireRed, and LeafGreen controls; assert the ROM hashes before using them.
- [ ] Extract only source-oracle stages in test code, then assert decoded stream hashes, exact ARGB hashes, canonical PNG hashes, dimensions, and representative pixels from the matrix.
- [ ] Add one decoy regression using the proven old 2,048-byte Classic stream shape; require typed rejection rather than a raster.
- [ ] Run the focused tests and record RED because the normalized compositor API does not exist.

### Task 2: Implement the two normalized compositor paths

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/GbaWorldMapCompositor.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/TileRenderer.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/sprite/TileRendererTest.kt`

- [ ] Add 8bpp affine rendering whose tilemap entries are one byte and whose fixed validated crop is `(1,2,28,15)`.
- [ ] Add 4bpp text rendering for little-endian entries with tile index, horizontal/vertical flip, and palette-bank semantics; validate the fixed crop `(4,4,22,15)`.
- [ ] Return a sealed success/rejection outcome and never reinterpret one format as the other.
- [ ] Run the exact real-control tests GREEN and write normalized PNG/manifest artifacts to `parser-core/build/map-controls`.

### Task 3: Restore normalized catalog projection

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen3MapLocationResolver.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3WorldMapResolver.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`
- Create/modify focused tests under `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog`.

- [ ] Write a RED real-control resolver test for exact official, Modern, and Classic normalized output, including Classic's fixed `28x15` canvas despite its `19x15` entry extent.
- [ ] Port only the prior world-map models and location join needed by the resolver.
- [ ] Discover and rank structurally classified co-referenced stages, with typed unavailable/ambiguous/budget results.
- [ ] Keep FRLG catalog resolution fail closed if its four layout identities cannot be uniquely joined; assert there is no fallback image.
- [ ] Run the focused parser tests GREEN.

### Task 4: Restore catalog persistence

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] Write a RED round-trip test that persists a real normalized raster plus region/grid/location metadata.
- [ ] Port the minimal schema/reader/writer behavior needed to retain the map catalog and PNG bytes.
- [ ] Run the focused catalog-store test GREEN.

### Task 5: Restore asset serving without navigation

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt`

- [ ] Write a RED server test requesting the persisted normalized map asset by catalog key.
- [ ] Port only the binary asset response path, content type, key validation, and missing-asset response.
- [ ] Assert no World Map page, companion route, Pokédex toolbar, or navigation source was restored.
- [ ] Run the focused server test GREEN.

### Task 6: Verify and commit

**Files:**
- Modify: `docs/reports/2026-08-13-gen3-world-map-stage-matrix.md` only if implementation-generated canonical hashes differ, preserving both the reason and corrected value.

- [ ] Run focused real controls with all five ROM environment variables.
- [ ] Run `:parser-core:test`, `:catalog-store:test`, and `:app:testDebugUnitTest` once after focused checks.
- [ ] Run `git diff --check`, scan tracked files for ROM extensions/private absolute paths, and inspect the complete diff.
- [ ] Commit only on `poc/map-core-integration`; do not push, merge, deploy, release, or start an emulator/device.
