# DualDex Catalog Materialization Implementation Plan

**Goal:** Turn validated GB/GBC/GBA table locations into a typed, ROM-native catalog containing every static dataset required by the POC, including decoded Pokémon and ball sprites.

**Architecture:** `parser-core` remains pure Kotlin/JVM and Android-portable. Family parsers return a validated `ResolvedRomLayout`; independent materializers decode records into immutable catalog models. Pixel decoders return indexed or ARGB pixels and never depend on AWT. The CLI may serialize metadata and hashes but must never include copyrighted decoded text or pixels in compatibility reports.

**Verification contract:** Every decoder begins with a failing synthetic fixture test. Private-ROM integration tests are opt-in and assert counts, representative names, dimensions, opaque-pixel ratios, and stable hashes without committing ROM data.

## Task 1: Preserve resolved layouts

**Files:**

- Modify `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`.
- Modify `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/FamilyParsers.kt`.
- Modify `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`.
- Add `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/ResolvedLayoutTest.kt`.

**Steps:**

1. Add a test proving the selected parser exposes the exact inferred counts, record sizes, banks, pointer offsets, and family generation used during validation.
2. Introduce `ResolvedRomLayout` and `FamilyProbeResult`; keep `ParserProbe` as the report-safe view.
3. Make `FamilyParser.resolve` return evidence plus layout and have `ParserOrchestrator.resolve` select the winning layout using the same score/margin rules as `analyze`.
4. Run `./gradlew.bat :parser-core:test --tests '*ResolvedLayoutTest'`, then all parser-core tests.
5. Commit as `refactor: preserve resolved ROM layouts`.

## Task 2: Define the normalized catalog

**Files:**

- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`.
- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogCapabilities.kt`.
- Add `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModelsTest.kt`.

**Steps:**

1. Test ROM-native ID preservation and tri-state missing-field representation.
2. Add immutable `ParsedCatalog`, `SpeciesRecord`, `BaseStats`, `MoveRecord`, `TypeRecord`, `TypeMatchup`, `LearnsetEntry`, `EvolutionEdge`, `AbilityRecord`, `EncounterArea`, `CaptureBallRecord`, and `RgbaSprite` models.
3. Add static capabilities `AREA_ENCOUNTERS`, `TYPE_PRESENTATION`, and `BALL_CATALOG`; leave independent failures explicit.
4. Require `RgbaSprite.argb.size == width * height` and expose no PNG/AWT type in parser-core.
5. Run focused and module tests; commit as `feat: define ROM catalog model`.

## Task 3: Materialize names, stats, moves, abilities, and type charts

**Files:**

- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt`.
- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/TypeMappings.kt`.
- Extend `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/text/PokemonTextCodec.kt`.
- Add `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/RecordMaterializersTest.kt`.

**Steps:**

1. Build synthetic Gen I, II, and III records and assert exact decoded fields.
2. Decode fixed and variable names, base stats and types, move power/type/accuracy/PP/effect/category, ability names, and terminated type-chart triples.
3. Preserve unknown type/move IDs rather than substituting modern external data. Derive physical/special in Gen I/II from the ROM type class and in Gen III from the engine's move-category rules, with capability evidence when a hack extends the layout.
4. Expand text control-code handling only where needed to render descriptions safely; preserve unsupported control sequences as normalized spacing rather than leaking replacement glyphs.
5. Run focused tests and `:parser-core:test`; commit as `feat: materialize ROM records`.

## Task 4: Decode descriptions, evolutions, and level-up learnsets

**Files:**

- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RelationshipMaterializers.kt`.
- Add `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/RelationshipMaterializersTest.kt`.

**Steps:**

1. Add fixtures for banked Gen I/II pointer tables, combined evolution/learnset streams, Gen III description records, evolution arrays, and learnset pointers.
2. Decode every validated species record independently; a corrupt entry produces a diagnostic and does not erase neighboring records.
3. Emit only level-up learnsets for simulator plausibility and label other mechanisms when structurally decoded.
4. Retain method IDs and raw parameters for hack-defined evolution mechanisms.
5. Run tests; commit as `feat: materialize ROM relationships`.

## Task 5: Decode sprite compression and palettes

**Files:**

- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/Gen1SpriteDecoder.kt`.
- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/Lz3Decoder.kt`.
- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/GbaLz77Decoder.kt`.
- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/TileRenderer.kt`.
- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/SpriteMaterializer.kt`.
- Add matching tests under `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/sprite/`.

**Steps:**

1. Pin known compressed byte fixtures to exact decompressed bytes before rendering.
2. Test 1bpp/2bpp/4bpp tile ordering, transparency, BGR555 conversion, Gen I delta/XOR transforms, Gen II LZ3 repeat/flip/reverse commands, and GBA LZ77 back-references.
3. Decode front Pokémon sprites for every validated species. Use ROM palettes where present; otherwise use an engine-derived monochrome palette without synthetic glyphs or bundled artwork.
4. Locate and decode the GBA ball graphics/palette table into `CaptureBallRecord`; decode the game's generic ball art for earlier engines when structurally available.
5. Keep sprite failures record-local and diagnostic. No emoji or generated illustration is an allowed fallback.
6. Run sprite tests and all parser-core tests; commit as `feat: decode ROM sprites and capture balls`.

## Task 6: Add encounters and type presentation

**Files:**

- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/EncounterMaterializer.kt`.
- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/TypePresentationMaterializer.kt`.
- Add corresponding tests.

**Steps:**

1. Add family-shaped fixtures for encounter headers/slots and palette sources.
2. Materialize area IDs/names when present, encounter methods, slots, levels, species IDs, and weights without claiming authentic current-area state.
3. Extract type colors from ROM UI palettes where validated; otherwise emit a deterministic family/accessibility fallback marked with its source.
4. Run tests; commit as `feat: materialize encounters and type presentation`.

## Task 7: Public catalog API and CLI validation

**Files:**

- Add `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`.
- Modify `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/Main.kt`.
- Add `parser-cli/src/main/kotlin/com/enrpau/dualscreendex/parser/cli/CatalogReportWriter.kt`.
- Add/modify tests under `parser-cli/src/test/kotlin/...`.

**Steps:**

1. Test `CatalogParser.parse(rom)` returns the selection result, resolved layout, catalog, per-record diagnostics, and static capability ledger.
2. Add a CLI `catalog-check` mode for direct ROMs and streamed ZIP entries. Output names/counts/dimensions/hashes only when explicitly requested for local inspection; the checked-in corpus report remains structural and copyright-safe.
3. Ensure ZIP entry streams are bounded by the archive entry and do not extract files to disk.
4. Run `./gradlew.bat :parser-core:test :parser-cli:test` and scan for committed ROM bytes or decoded catalog dumps.
5. Commit as `feat: expose materialized ROM catalogs`.

## Task 8: Private corpus acceptance

**Files:**

- Add `parser-cli/src/test/kotlin/com/enrpau/dualscreendex/parser/cli/PrivateCatalogCorpusTest.kt`.
- Update `README.md` and `reports/dualdex-parser-compatibility.md` only with verified aggregate evidence.

**Steps:**

1. Run against the 11 official mainline entries and the three in-scope derivatives under `H:\My Drive\Roms`.
2. Require non-empty catalogs, valid representative front sprites, valid move metadata, learnsets, descriptions/evolutions where capability is `AVAILABLE`, and no emoji/synthetic image fallback.
3. Record exact failures per dataset as `N/F`, never as success-by-family inheritance.
4. Run the full Gradle test suite, CLI compatibility scan, `git diff --check`, and `rg -n "emoji|placeholder sprite|synthetic sprite"` over production assets.
5. Commit as `test: validate materialized ROM corpus`.

