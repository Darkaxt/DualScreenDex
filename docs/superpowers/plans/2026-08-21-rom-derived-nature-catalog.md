# ROM-derived Nature Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Decode Gen III Nature names, stat effects, and flavor affinities from the loaded ROM and remove the companion's canonical Nature tables.

**Architecture:** A `Gen3NatureResolver` uses the shared ROM-analysis session and compiled-reference evidence to resolve one coherent name-pointer/stat-table contract plus an optional independent flavor table. The normalized records flow through `ParsedCatalog`, SQLite, the companion API, Party, and Nature Detail; Gen I/II remain truthfully not applicable and unresolved Gen III data never falls back to canonical values.

**Tech Stack:** Kotlin/JVM, existing ARMv4T/Thumb IR and ROM reference index, Gson catalog sections, TypeScript/Preact, Gradle/JUnit, Vitest.

---

### Task 1: Freeze the normalized Nature model

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/natures/NatureModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/model/RomModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/natures/NatureModelsTest.kt`

- [ ] Write a failing model test requiring five ordered stat modifiers, five optional flavor modifiers, positive/neutral/negative stat multipliers, immutable record ordering, and rejection of invalid row widths or modifier values.
- [ ] Run `./gradlew.bat :parser-core:test --tests '*NatureModelsTest' --no-daemon --console=plain` and confirm RED because the types are absent.
- [ ] Add `NatureStat`, `NatureFlavor`, `NatureRecord`, `NatureCatalog`, and `RomCapability.NATURES`; add `naturesById` to `ParsedCatalog`.
- [ ] Rerun the focused model test and require GREEN.

### Task 2: Resolve real Gen III Nature tables

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/natures/Gen3NatureResolver.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/natures/Gen3NatureResolverTest.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/dataset/natures/Gen3NatureResolverLiveRomTest.kt`

- [ ] Write RED controls using Modern Emerald, Unbound, and Odyssey as the primary fixtures, with official Emerald, official FireRed, and Classic as regressions. Assert ROM-native names, stat directions, 110/100 and 90/100 multipliers, and independent Spicy/Dry/Sweet/Bitter/Sour affinities.
- [ ] Add a ROM-byte mutation that changes one decoded Nature name and one valid modifier row; assert the parsed result follows the bytes. Add truncation, unsupported modifier, and duplicate-candidate fail-close controls.
- [ ] Run `./gradlew.bat :parser-core:test --tests '*Gen3NatureResolver*' --no-daemon --console=plain` and confirm RED because the resolver is absent.
- [ ] Implement candidate nomination from `RomAnalysisSession.gbaReferenceIndex`, complete pointer-string domain decoding, same-domain signed stat/flavor decoding, decoded indexed-consumer validation, multiplier extraction, and exact-one-contract selection.
- [ ] Rerun the resolver controls and require GREEN with no ROM names, hashes, source symbols, or fixed offsets in production.

### Task 3: Materialize Nature capability in the shared parser session

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/NatureCatalogMaterializationLiveRomTest.kt`

- [ ] Write a failing vertical parser test requiring `naturesById`, `RomCapability.NATURES`, deterministic results, Gen I/II `NOT_APPLICABLE`, and Gen III `NOT_FOUND` rather than `NOT_APPLICABLE` when evidence is missing.
- [ ] Extend `CatalogAnalysisContext` with a shared-session Nature resolver and materialize its records and capability evidence without opening another ROM-analysis session.
- [ ] Run the focused materialization test and `ParserArchitectureTest`; require GREEN.

### Task 4: Persist the Nature catalog

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Test: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] Write a failing write/reopen test with noncanonical Nature names and modifiers.
- [ ] Add the required `natures` section, codec type, encode/decode mapping, and increment `parserSchemaVersion` so stale caches invalidate.
- [ ] Run `./gradlew.bat :catalog-store:test --tests '*CatalogStoreTest' --no-daemon --console=plain` and require exact round-trip GREEN.

### Task 5: Replace the Kotlin and TypeScript hardcoded tables

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Modify: `companion-web/src/api.ts`
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/pages/NatureDetail.tsx`
- Delete: `companion-web/src/natureDetails.ts`
- Modify: `companion-web/src/natureDetails.test.ts`

- [ ] Write RED API and UI tests using a noncanonical ROM-derived Nature record and requiring unknown IDs to remain absent rather than canonicalized.
- [ ] Replace `NATURE_NAMES` and `NATURE_DETAILS` lookups with the parsed typed Nature view. Preserve the current visual layout and omit the link when the catalog has no matching record.
- [ ] Run focused companion-core and Vitest Nature/Party tests; require GREEN and confirm `rg 'Hardy.*Lonely|NATURE_NAMES|NATURE_DETAILS' companion-core companion-web` finds no production hardcode.

### Task 6: Real vertical verification and commit

**Files:**
- Test: `app/src/test/kotlin/com/darkaxt/dualdex/web/NatureCatalogApiRealControlTest.kt`
- Update: `docs/superpowers/specs/2026-08-21-rom-derived-nature-catalog-design.md`

- [ ] Add real CatalogParser materialization controls for Modern Emerald, Unbound, and Odyssey, plus SQLite round-trip and typed API/UI controls. Keep official Emerald, FireRed, and Classic as resolver regressions.
- [ ] Run the focused real vertical tests, `:parser-core:test`, `:catalog-store:test`, `:companion-core:test`, `:app:testDebugUnitTest`, full Vitest, and production Vite build.
- [ ] Run `git diff --check`, inspect the complete diff, preserve unrelated release-document edits, and commit only the Nature parser vertical as `feat: parse Nature catalogs from ROM data`.
