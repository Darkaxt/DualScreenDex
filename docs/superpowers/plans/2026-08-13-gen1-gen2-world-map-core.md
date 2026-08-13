# Gen I and Gen II World Map Core Implementation Plan

Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce exact, encounter-bound normalized world maps for source-built Red/Blue/Yellow and Gold/Silver/Crystal without identity-based production selection.

**Architecture:** Keep Gen I and Gen II loader/data classification generation-specific, but return one typed `WorldMapResolution` consumed by `CatalogParser`. Exact source assets and control identities live only in tests; production proves loaders, codecs, entry/landmark tables, and map-header joins before emitting `WorldMapCatalog`.

**Tech Stack:** Kotlin/JVM 17, JUnit 4, existing parser-core tile renderer, CatalogStore SQLite, companion-server HTTP tests, RGBDS source builds.

---

### Task 1: Freeze independent official source oracles

**Files:**
- Create: `docs/reports/2026-08-13-gen1-gen2-world-map-source-oracles.md`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1WorldMapRealControlTest.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2WorldMapRealControlTest.kt`

- [ ] Build Red/Blue, Yellow, Gold/Silver, and Crystal from the four frozen source commits with the required RGBDS version; run each source repository's checksum comparison.
- [ ] Derive expected decoded asset hashes, 160x144 ARGB hashes, region keys, anchor geometry, and encounter bindings directly from source assets/symbols into the evidence report.
- [ ] Write six environment-driven real-control tests. Gen I calls the wished-for `Gen1WorldMapResolver.resolve(session, baseAreaIds)`; Gen II calls `Gen2WorldMapResolver.resolve(session, baseAreaIds)`. Assert exact `WorldMapResolution.Resolved`, region identities, dimensions, location fingerprints, and raster hashes.
- [ ] Run the two test classes and confirm RED because the common resolution type and Gen I/II production resolvers are absent.
- [ ] Commit source-oracle evidence and RED tests separately.

### Task 2: Implement the Gen I structural chain

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/WorldMapResolution.kt`
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1WorldMapResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen1WorldMapResolverTest.kt`

- [ ] Define `WorldMapResolution` with `Resolved`, typed-stage `Unavailable`, typed-stage `Ambiguous`, and typed-stage `BudgetExceeded` outcomes.
- [ ] Implement exact 16-tile 2bpp copying, 360-cell nibble-RLE decoding, Red/Blue and Yellow loader-role validation, external/internal entry-table parsing, text validation, map-ID coverage, and in-grid one-cell anchors.
- [ ] Run the official Red/Blue/Yellow controls until all exact hashes and geometry pass.
- [ ] Add one truncation test and one duplicate-complete-chain test derived from the proven official invariants; confirm RED, implement the minimal fail-closed behavior, then confirm GREEN.
- [ ] Run `:parser-core:test --tests '*Gen1WorldMap*'` and commit the Gen I core checkpoint.

### Task 3: Implement the Gen II structural chain

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2WorldMapResolver.kt`
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen2WorldMapResolverTest.kt`

- [ ] Implement the source-proven Gen II LZ codec needed for exactly 48 2bpp tiles and validate the decompressed size.
- [ ] Prove the two 360-cell-plus-terminator map planes through their loader, the 48-byte nibble palette map, and the default six-bank BGR555 palette chain; compose exact Johto/Kanto rasters.
- [ ] Prove the four-byte landmark table and map-group/header lookup chain, accept but exclude valid off-map special records, join every required encounter base ID, and group shared landmark identities into the correct region.
- [ ] Run Gold/Silver/Crystal controls until exact two-region hashes, identities, and geometry pass.
- [ ] Add one malformed-special test and one duplicate-authority test from the real invariants; watch each fail before adding its minimal guard.
- [ ] Run `:parser-core:test --tests '*Gen2WorldMap*'` and commit the Gen II core checkpoint.

### Task 4: Materialize, persist, serve, and reclassify

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogWorldMapMaterializationRealControlTest.kt`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/WorldMapCatalogApiRealControlTest.kt`
- Modify: `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/MapFirst50Matrix.kt`
- Create: `docs/reports/2026-08-13-map-first50-gen2-refresh.json`

- [ ] Route generations 1/2/3 to their own resolver over the shared normalized result; compute encounter base IDs once and map typed stages to capability evidence. Supported Gen I/II failures become `NOT_FOUND` or `AMBIGUOUS`, never `NOT_APPLICABLE`.
- [ ] Extend the real materialization test to all six official Gen I/II controls and assert exact region identities/hashes through `CatalogParser`.
- [ ] Round-trip each catalog through SQLite and the runtime/API asset endpoint; assert metadata plus exact PNG bytes and ETags.
- [ ] Run two fresh analyses for the four GBC rows in exact first50 and mechanically record `RESOLVED` or typed resolver failure, determinism, and safe fallback.
- [ ] Run parser-core, catalog-store, companion-server, and focused app/companion suites; rerun the five Gen III real controls; run `git diff --check`.
- [ ] Commit the clean integration checkpoint and report exact hashes, statuses, artifacts, and remaining unsupported variants.
