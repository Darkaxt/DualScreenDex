# RC44 Cache, Trainer, and Party Continuity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Produce RC44 with reliable large-catalog cache reopening, source-backed Modern Emerald trainer artwork, and the shared Combat Pokédex icon in Party.

**Architecture:** Persist compressed catalog sections as bounded ordered SQLite chunks and expose cache decisions only through logcat. Extend the existing semantic Gen III runtime resolver with real-ROM evidence for Modern Emerald's standard SaveBlock2 trainer fields, then reuse the existing trainer-asset API and shared web icon component.

**Tech Stack:** Kotlin/JVM, Android SQLite, Gson/GZip, JUnit 5, Vitest/Preact, Gradle, ADB, GitHub releases.

---

### Task 1: Persist catalog sections in Android-safe chunks

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogMigration.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogWriter.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Test: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] **Step 1: Write failing large-section and migration tests**

Add a test database wrapper that rejects any returned BLOB above 1 MiB, create a deliberately incompressible `local_maps` payload above that threshold, and assert that the completed catalog still reopens. Assert every stored chunk is at most `CatalogSchema.sectionChunkBytes`, indices are contiguous, and at least two chunks exist. Extend the parser-schema migration test to seed `catalog_section_chunks` and `save_snapshot`, then assert chunks/catalog metadata are cleared while the save row remains.

- [ ] **Step 2: Run the focused store tests and confirm the old single-row design fails**

Run: `./gradlew :catalog-store:test --tests com.darkaxt.dualdex.catalog.CatalogStoreTest`

Expected: the large-section reopen and chunk-table assertions fail before implementation.

- [ ] **Step 3: Add the chunk schema and version gate**

Set `parserSchemaVersion = 33`, add `sectionChunkBytes = 256 * 1024`, create `catalog_section_chunks`, include it in physical-schema drops, and delete chunks alongside manifests during parser-schema invalidation.

- [ ] **Step 4: Write and read ordered chunks**

For every changed section, delete its previous chunks, insert `payload.copyOfRange(start, end)` rows with ascending `chunk_index`, and store a `gzip+json+chunks-v1` manifest. In `CatalogReader`, query `chunk_index, payload ORDER BY chunk_index`, require exact `0..lastIndex`, concatenate with `ByteArrayOutputStream`, and decode through the unchanged codec.

- [ ] **Step 5: Run the focused store tests**

Run: `./gradlew :catalog-store:test --tests com.darkaxt.dualdex.catalog.CatalogStoreTest`

Expected: all `CatalogStoreTest` cases pass, including large-section and save-preserving migration controls.

- [ ] **Step 6: Commit the persistence fix**

Run: `git add catalog-store && git commit -m "fix: chunk large catalog sections"`

### Task 2: Log every cache decision without exposing diagnostics in normal UI

**Files:**
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogCache.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Test: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] **Step 1: Write failing cache-event tests**

Construct `CatalogCache` with an event collector. Assert separate events for absent file, successful hit, incomplete/incompatible catalog, and a database factory that throws `IllegalStateException("oversized row")`. Assert the rejection event includes the exception class/message before the exact cache file is invalidated.

- [ ] **Step 2: Run the focused tests and confirm the collector is unsupported**

Run: `./gradlew :catalog-store:test --tests com.darkaxt.dualdex.catalog.CatalogStoreTest`

Expected: cache-event tests fail until `CatalogCache` accepts and invokes the sink.

- [ ] **Step 3: Implement typed cache events and Android logging**

Add `CatalogCacheEvent` values for absent, incompatible, hit, and exception rejection; include SHA and optional failure. Inject `(CatalogCacheEvent) -> Unit = {}` into `CatalogCache`, emit at every return branch, and wire it in `DualDexApplication` to `Log.i`/`Log.w` with tag `DualDexCache`.

- [ ] **Step 4: Verify events and normal-UI isolation**

Run: `./gradlew :catalog-store:test :app:testDebugUnitTest`

Expected: tests pass and no cache diagnostic strings occur under `companion-web/src`.

- [ ] **Step 5: Commit observability**

Run: `git add catalog-store app && git commit -m "fix: report catalog cache decisions"`

### Task 3: Resolve Modern Emerald trainer state from the real ROM

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3PlayerRuntimeLayoutResolver.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3PlayerRuntimeLayoutResolverRealControlTest.kt`
- Test data: `D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba`
- Source oracle: `D:/Temp/PokemonHacks/sources/Game Boy Advance/Modern Emerald/include/global.h`

- [ ] **Step 1: Add a failing Modern Emerald real-ROM control**

Verify SHA-256 `21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895`, parse the runtime layout, and assert non-null, distinct SaveBlock1/SaveBlock2 pointer globals plus the standard source-confirmed trainer offsets for gender, trainer ID, and play time.

- [ ] **Step 2: Run only the real control and confirm current failure**

Run: `./gradlew :parser-core:test --tests com.enrpau.dualscreendex.parser.parse.Gen3PlayerRuntimeLayoutResolverRealControlTest`

Expected: Modern Emerald fails because `saveBlock2PointerAddress` and `saveRuntimeAbi` are null.

- [ ] **Step 3: Extend semantic pointer evidence narrowly**

Inspect the real ROM's Thumb consumers for the SaveBlock2 field tuple. Adjust tracing to follow the compiled register/data-flow shape actually emitted by Modern Emerald while keeping the four-field requirement, unique-candidate selection, and fail-closed coherent SaveBlock pair. Do not add identity or fixed-address profiles.

- [ ] **Step 4: Verify official and Modern Emerald real controls**

Run: `./gradlew :parser-core:test --tests com.enrpau.dualscreendex.parser.parse.Gen3PlayerRuntimeLayoutResolverRealControlTest --tests com.enrpau.dualscreendex.parser.catalog.CatalogRuntimeMemoryLayoutTest`

Expected: official Emerald, FireRed, and Modern Emerald pass; decoy/ambiguity controls remain rejected.

- [ ] **Step 5: Verify avatar publication with the parsed ABI**

Run: `./gradlew :companion-core:test --tests com.enrpau.dualscreendex.companion.api.ApiViewBuilderTest`

Expected: a decoded live trainer gender maps to the catalog's 64x64 trainer avatar URL.

- [ ] **Step 6: Commit the trainer resolver fix**

Run: `git add parser-core companion-core && git commit -m "fix: resolve Modern Emerald trainer state"`

### Task 4: Reuse the Combat Pokédex icon in Party

**Files:**
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/styles.css`
- Modify: `companion-web/src/pages/PartyPage.test.tsx`

- [ ] **Step 1: Write a failing Party icon contract test**

Render an owned Party member with `openSpecies`, locate its accessible Pokédex action, assert it contains `.dex-icon`, and assert it contains no literal `DEX` text.

- [ ] **Step 2: Run the focused web test and confirm failure**

Run: `npm --prefix companion-web test -- --run src/pages/PartyPage.test.tsx`

Expected: the icon assertion fails against the current text square.

- [ ] **Step 3: Use the shared component and CSS contract**

Import `DexIcon`, replace the text child with `<DexIcon />`, and combine `.party-dex-link` with `.battle-dex-link` for identical dimensions, transparent background, theme color, focus behavior, and SVG size.

- [ ] **Step 4: Run Party and Combat tests**

Run: `npm --prefix companion-web test -- --run src/pages/PartyPage.test.tsx src/pages/BattlePage.test.tsx`

Expected: both suites pass and use the shared icon contract.

- [ ] **Step 5: Commit the UI correction**

Run: `git add companion-web && git commit -m "fix: share Pokédex action icon"`

### Task 5: Integrate and release RC44

**Files:**
- Modify: version metadata files found by `rg "rc\.43|versionCode" app build.gradle.kts -g '*.kts' -g '*.properties' -g '*.xml'`
- Modify: release notes/changelog files used by RC43

- [ ] **Step 1: Run bounded regression verification**

Run the focused commands from Tasks 1-4, then `./gradlew :app:assembleRelease`.

Expected: all focused tests and the release assembly pass.

- [ ] **Step 2: Inspect the release APK contract**

Verify APK `versionName`, `versionCode`, filename, signature, and SHA-256 agree on `1.1.0-rc.44`. Do not launch the app or operate the game UI.

- [ ] **Step 3: Commit and tag RC44**

Commit only the RC44 version/release-note changes, create the matching annotated tag, and push the branch/tag to GitHub.

- [ ] **Step 4: Publish the signed APK**

Create the GitHub prerelease with the signed APK and concise notes covering the cursor-window cache fix, cache logs, Modern Emerald trainer artwork, and shared Party icon.

- [ ] **Step 5: Hand the signed APK to the user**

Download and verify the signed release artifact, checksum, certificate, and provenance. Do not install or launch it; device and runtime acceptance belong to the user.
