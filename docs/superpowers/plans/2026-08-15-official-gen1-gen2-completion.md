# Official Gen I/II Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make canonical English Red, Blue, Yellow, Gold, Silver, and Crystal Rev 1 reach truthful 100% parser and catalog compatibility.

**Architecture:** Add one narrowly scoped Gen I detached-record resolver shared by validation and materialization, correct Gen I semantic description coverage, and extend the existing move-description materializer with a fail-closed Gen II bank-local pointer-table decoder. Verify only with real ROM controls first, then focused modules and the six-ROM CLI cohort.

**Tech Stack:** Kotlin/JVM 17, JUnit 4, Gradle, parser-cli, SQLite catalog store.

---

### Task 1: Freeze the real official-ROM failures

**Files:**
- Create: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/OfficialGen12CompletionLiveRomTest.kt`

- [ ] **Step 1: Write SHA-bound tests using the six existing official-ROM environment variables.** Assert Mew has 100/100/100/100/100/100 stats and a decoded sprite in Red/Blue; Gen I semantic capability coverage is 151/151; and Gen II exposes all 251 move descriptions with exact Pound and Struggle samples.
- [ ] **Step 2: Run `.\gradlew.bat :parser-core:test --tests '*OfficialGen12CompletionLiveRomTest' --rerun-tasks --no-daemon --console=plain`.** Expect Red/Blue to fail on Mew and Gen II to fail on move descriptions.

### Task 2: Recover detached Gen I records

**Files:**
- Create: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/Gen1DetachedSpeciesResolver.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/RecordMaterializers.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/sprite/SpriteMaterializer.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/CoreDatasetsStrategy.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/DependentDatasetsStrategy.kt`

- [ ] **Step 1: Resolve only a missing Gen I Dex row outside the ordinary table.** Require a unique 28-byte candidate whose leading Dex ID matches, stats/types/dimensions/pointers validate, front and back streams decode in the candidate bank, and address participates in the source-proven 28-byte far-copy consumer.
- [ ] **Step 2: Reuse the resolver in validation and materialization.** Augment evidence and materialize the matching internal species; leave Yellow unchanged when the contiguous table is complete.
- [ ] **Step 3: Rerun the three official Gen I tests.** Expect all to pass.

### Task 3: Correct Gen I description semantics

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidators.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/SemanticDomainStrategy.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/validate/PokemonDatasetValidatorsTest.kt`

- [ ] **Step 1: Keep validating 190 raw pointers but set semantic coverage against the independently resolved Dex count.**
- [ ] **Step 2: Add a regression where one expected positive-Dex description is missing.** Confirm evidence stays partial, then rerun official Gen I controls.

### Task 4: Decode Gen II move descriptions

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializer.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogParser.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/MoveDescriptionMaterializerTest.kt`

- [ ] **Step 1: Scan each ROM bank for a complete run of `moveCount` bank-local 16-bit text pointers.** Decode using `PokemonTextCodec.gbEnglish`, require complete natural-language coverage, and accept only one candidate.
- [ ] **Step 2: Publish generation 2 descriptions as AVAILABLE, unresolved generation 2 as NOT_FOUND, and retain generation 1 as NOT_APPLICABLE.**
- [ ] **Step 3: Rerun Gold, Silver, and Crystal real controls.** Expect 251 decoded descriptions each.

### Task 5: Verify and commit

**Files:**
- Modify only if a failure identifies a directly related defect.

- [ ] **Step 1: Run `.\gradlew.bat :parser-core:test :parser-cli:test :catalog-store:test --no-daemon --console=plain`.** Expect zero failures.
- [ ] **Step 2: Run `:parser-cli:installDist` and parse the six extracted ROMs into a fresh cache/report.** Expect 6 selected, 6 at 100.00%, no manual review, reference, catalog, or persistence errors.
- [ ] **Step 3: Run SQLite quick/foreign-key checks, `git diff --check`, and verify no map paths changed.**
- [ ] **Step 4: Commit with `Complete official Gen I and II catalogs`.**
