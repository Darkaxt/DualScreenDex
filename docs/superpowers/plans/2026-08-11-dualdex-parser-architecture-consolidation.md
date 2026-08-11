# DualDex Parser Architecture Consolidation Implementation Plan

> **For Codex:** Execute this plan inline with RED-first TDD. Do not advance corpus validation past index 33 until every architecture and differential gate in Task 10 passes.

**Goal:** Replace the accumulated parser monolith with one bounded, typed, evidence-driven analysis pipeline while preserving all accepted ROM behavior and completing save-detected level-up ruleset selection.

**Architecture:** Create one immutable `RomAnalysisSession` per parse, one shared bounded GBA reference index, and a common typed resolution kernel. Migrate datasets vertically into focused resolver/codec units, then compose them through dependency-ordered family definitions. Materializers consume resolved typed layouts only and share the same codecs used for validation.

**Tech stack:** Kotlin/JVM, Gradle, JUnit 4, Gson/SQLite catalog persistence, Android runtime, Preact/TypeScript companion UI, PowerShell corpus review tooling.

---

## Task 1: Lock architectural behavior and dependency boundaries

**Files:**

- Add: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/architecture/ParserArchitectureTest.kt`
- Add: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/resolution/CandidateSelectorTest.kt`
- Add: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/analysis/RomAnalysisSessionTest.kt`

### Step 1: Write the failing architecture contracts

Add tests proving:

- one GBA reference index is built lazily once per analysis session;
- candidate strength never uses offset, proximity, discovery order, or enumeration order;
- equal independent candidates remain ambiguous;
- materializer packages cannot import discovery or candidate-selection packages;
- resolver packages cannot import catalog persistence, Android, server, or UI packages;
- whole-ROM target maps are built only by the shared bounded reference index;
- checked extent helpers reject negative, overflowing, truncated, and over-budget spans.

### Step 2: Observe RED

```powershell
./gradlew :parser-core:test --tests '*ParserArchitectureTest' --tests '*CandidateSelectorTest' --tests '*RomAnalysisSessionTest' --rerun-tasks
```

Expected: compilation/assertion failures because the new boundaries and shared contracts do not exist.

### Step 3: Keep tests narrow and deterministic

Use synthetic ROM fixtures and source-boundary inspection. Do not use ROM hashes, filenames, corpus indexes, timing, or environment-dependent ordering.

## Task 2: Introduce the shared analysis and resolution kernel

**Files:**

- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/analysis/ResolutionLimits.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/analysis/RomAnalysisSession.kt`
- Move/refactor: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/GbaReferenceIndex.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/resolution/DatasetResolution.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/resolution/CandidateSelector.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/resolution/CapabilityEvidenceAdapter.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/ParserOrchestrator.kt`

### Step 1: Implement checked limits and the immutable session

Create one session containing ROM/header/profile identity, immutable deterministic work limits, the lazy shared reference index, and bounded compiled-evidence caches. Budget exhaustion must be a typed result, never truncated ranking or cancellation timeout.

### Step 2: Implement typed candidates and outcomes

Define `DatasetKind`, `CandidateSource`, `CandidateStrength`, `DatasetCandidate<T>`, and sealed `DatasetResolution<T>` outcomes: `Resolved`, `Partial`, `Ambiguous`, `Unavailable`, and `BudgetExceeded`.

### Step 3: Implement selection and public evidence adaptation

Rank only eligible candidates by authority, semantic coverage, structural coverage, independent reference strength, then dataset-specific structural quality. Deduplicate identical layouts before comparison. Convert every outcome to public capability evidence through one adapter while preserving validator-review provenance, ambiguity, raw/semantic counts, reasons, and budgets.

### Step 4: Run focused tests to GREEN

```powershell
./gradlew :parser-core:test --tests '*ParserArchitectureTest' --tests '*CandidateSelectorTest' --tests '*RomAnalysisSessionTest' --tests '*ParserOrchestratorTest'
```

## Task 3: Migrate descriptions and evolutions into focused codec/resolver units

**Files:**

- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/descriptions/DescriptionCodec.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/descriptions/DescriptionResolver.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/evolutions/EvolutionCodec.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/evolutions/EvolutionResolver.kt`
- Add matching focused tests under `parser-core/src/test/kotlin/.../dataset/`
- Reduce: `DatasetResolvers.kt`, `PokemonDatasetValidators.kt`, `RelationshipMaterializers.kt`

### Step 1: Move characterization tests before code

Port the approved compiled-reference, decoy, ambiguity, boundary, off-by-one recovery, structural-slot, and special-evolution-method cases into focused tests. Add a codec parity test showing validation and materialization return the same row classification.

### Step 2: Observe RED, then implement codecs

Each codec returns `Decoded`, `StructuralEmpty`, or `Malformed` with bounded evidence. Recovery is explicit typed provenance; informational trimming does not become validator recovery.

### Step 3: Implement resolvers using the shared session and selector

Resolvers may discover and validate candidates but may not create catalog records. Remove the corresponding discovery/ranking/decoding logic from the old facades and materializer.

### Step 4: Verify focused and existing regression suites

```powershell
./gradlew :parser-core:test --tests '*Description*' --tests '*Evolution*' --tests '*DatasetResolversTest' --tests '*RelationshipMaterializersTest'
```

## Task 4: Migrate typed learnsets and finish save-detected LEVEL_UP selection

**Files:**

- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/learnsets/LearnsetCodec.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/dataset/learnsets/LearnsetResolver.kt`
- Move/refactor: `Gen3LearnsetSelectorExtractor.kt`
- Modify: `LearnsetRulesetMaterializer.kt`, `CatalogModels.kt`, `CatalogParser.kt`
- Modify: `SaveModels.kt`, `Gen3SaveReader.kt`
- Modify Android runtime, companion server, API models, and Settings UI tests already touched by the selector work
- Add old-JSON migration tests in `save-core` and `catalog-store`

### Step 1: Characterize all four Gen III ABIs and alternatives

Write RED tests proving cross-ABI candidates compete once; each alternate keeps its own typed format; adjacency alone proves nothing; direct compiled selector evidence is required to retain alternatives; equal independent roots are ambiguous; unsorted legal levels materialize; malformed bounds and budgets fail closed.

### Step 2: Implement codec/resolver and remove materializer discovery

The resolver publishes a typed primary plus selector-bound alternatives. The materializer decodes exactly those layouts with their codecs and performs no pointer-window search or format inference.

### Step 3: Complete save/runtime selection truthfully

Persist the LEVEL_UP selector descriptor. Evaluate it only against checksum-valid reconstructed SaveBlock1. Android `AUTO` chooses the unique detected table. Multiple tables without proof remain unresolved. Manual override remains explicitly recovery/debug. Companion server without SaveRAM fails closed. Do not claim Egg or TM selection.

### Step 4: Verify focused suites and the real Modern save

```powershell
./gradlew :parser-core:test :save-core:test :catalog-store:test :companion-core:test :companion-server:test :app:testDebugUnitTest --tests '*Learnset*' --tests '*Gen3SaveReader*' --tests '*Ruleset*'
```

Then parse the verified Modern Emerald ROM and read the existing mGBA SaveRAM read-only. Confirm the checksum-valid selector chooses the compiled-proven modern level-up root. Confirm Clover and Unbound retain their accepted roots and catalog counts.

## Task 5: Migrate core names, stats, moves, abilities, and type charts

**Files:**

- Add focused units under `parser/dataset/core`, `parser/dataset/abilities`, and `parser/dataset/types`
- Reduce: `TableValidators.kt`, `FamilyParsers.kt`, `Gen3DynamicTableResolver.kt`, `GbaPublishedHeaderResolver.kt`, `Gen3PublishedPartialBaseStatsResolver.kt`
- Move matching tests from monolithic test classes into focused suites

### Step 1: Establish RED boundaries

Cover published-header tri-state, 28/32-byte stats, partial anchored stats, ability width/count semantics, CFRU aliases, 12/16/20-byte moves, u16/u32/triplet type charts, ambiguity, reference budgets, checked extents, and semantic active-domain projection.

### Step 2: Implement focused resolvers/codecs

Recover base stats before dependent active-type and ability work. Preserve direct-ID versus species-conditioned alias semantics. Keep unsupported compiled behavior explicit rather than synthesizing records.

### Step 3: Verify focused and official/derived controls

```powershell
./gradlew :parser-core:test --tests '*BaseStat*' --tests '*Move*' --tests '*Ability*' --tests '*TypeChart*' --tests '*PublishedHeader*'
```

## Task 6: Migrate media, encounters, and move acquisition

**Files:**

- Add focused units under `parser/dataset/media`, `parser/dataset/encounters`, and `parser/dataset/acquisition`
- Reduce: `SpriteValidators.kt`, `SpriteMaterializer.kt`, `EncounterMaterializer.kt`, `MoveAcquisitionMaterializer.kt`
- Add focused codec/resolver/materializer parity tests

### Step 1: RED-first capability boundaries

Cover LZ/raw/SMOL modes, palette decoys, inactive expansion slots, same-name/Dex sprite aliases, Gen II Unown indirection, standard/Classic24 encounters, empty-first tables, bounded negative LRU/work caps, mixed time windows, and acquisition reference integrity.

### Step 2: Separate resolution from materialization

Move all root discovery, format inference, ranking, and ambiguity policy into resolvers. Materializers consume typed resolved layouts and perform bounded decode/join only.

### Step 3: Run focused suites

```powershell
./gradlew :parser-core:test --tests '*Sprite*' --tests '*Encounter*' --tests '*Acquisition*'
```

## Task 7: Replace monolithic family orchestration with phased family composition

**Files:**

- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/EngineFamilyDefinition.kt`
- Add: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/family/FamilyProbeCoordinator.kt`
- Add family definitions for supported Gen I, Gen II, Gen III retail, CFRU, and pokeemerald-expansion lineages
- Reduce/delete implementation logic in `FamilyParsers.kt`
- Modify: `ParserOrchestrator.kt`

### Step 1: Write RED phase-order and applicability tests

Prove identity/published roots precede core datasets, semantic domain precedes dependent datasets, recovered stats precede type/ability consumers, family applicability is not generation identity, exact profiles are authoritative only on exact identity, and a dataset failure does not clear independent capabilities.

### Step 2: Implement immutable phased results

Family definitions compose strategies and applicability. The coordinator executes phases once through the shared session. Family scoring remains separate from dataset success.

### Step 3: Make old facades thin or remove them

The final architecture test must reject discovery, ranking, validation, and decoding logic remaining in `FamilyParsers`, `DatasetResolvers`, `TableValidators`, or catalog materializers.

## Task 8: Complete persistence and API migration coverage

**Files:**

- Modify/add tests in `catalog-store`, `save-core`, `companion-core`, `companion-server`, `app`, and `companion-web`
- Bump parser schema only if persisted interpretation requires it

### Step 1: RED old-data migration tests

Prove legacy catalog and save JSON without selector/provenance fields load with safe defaults; current rows round-trip exactly; stale schemas are reparsed rather than silently reused.

### Step 2: Verify user-facing LEVEL_UP behavior

Prove Settings labels the selector as `LEVEL-UP RULESET`; Auto reports save-detected or unresolved; manual choices are recovery/debug; Egg/TM are not claimed.

### Step 3: Run complete module tests

```powershell
./gradlew test :app:testDebugUnitTest
Set-Location companion-web
npm test
npm run build
```

## Task 9: Run live architecture controls and persistence checks

**Inputs:**

- Official Gen I, Gen II, and Gen III fixtures already used by the project
- Representative CFRU and pokeemerald-expansion controls
- Clover, Modern Emerald, and Unbound
- Existing checksum-valid Modern Emerald mGBA SaveRAM, read-only

### Step 1: Build a fresh parser CLI

```powershell
./gradlew :parser-cli:installDist
```

### Step 2: Parse controls into an owned `D:\Temp` output root

Verify selected family, capability states/counts, reference errors, catalog counts, selector metadata, SQLite reopen equality, `PRAGMA integrity_check=ok`, and foreign-key integrity. Compare stable observations to the last accepted pass, including 100%-to-100% offsets/counts/sample changes.

### Step 3: Clean only owned temporary outputs

Preserve source checkouts, ROM archives, SaveRAM, corpus inputs, and shared reports. Remove the exact task-created live-control directory after evidence is recorded.

## Task 10: Run the frozen corpus 1–33 differential gate

**Files/tools:**

- `tools/corpus/Invoke-DualDexCorpusValidation.ps1`
- `tools/corpus/Invoke-DualDexCorpusReview.ps1`
- `tools/corpus/tests/CorpusReviewPolicy.Tests.ps1`

### Step 1: Verify the gate itself

```powershell
pwsh -NoProfile -Command "Invoke-Pester -Script tools/corpus/tests/CorpusReviewPolicy.Tests.ps1"
```

Expected: all gate, observation-envelope, migration, JSON Pointer, atomic baseline, and differential-review tests pass.

### Step 2: Reparse indices 1–33 with the current parser/APK version

Use explicit rebaseline only through `-ReviewIncomplete`. Do not accept compatibility decisions as parser-drift acknowledgements. Inspect every stable delta, including family/profile/status, layouts, counts, formats, semantic coverage, catalog counts, first-register samples, and persistence/reference errors.

### Step 3: Correct or explicitly acknowledge every delta

No baseline advances on unacknowledged drift. The architecture gate is complete only after indices 1–33 have current-schema observation envelopes and no pending parser-drift review.

### Step 4: Commit the consolidation

Commit focused logical stages as they become independently green. Before the final architecture commit, run `git diff --check`, inspect staged scope, and ensure no ROM, SaveRAM, corpus payload, generated catalog, or task temp is included.

## Task 11: Resume validation 34–50, documentation, APK, and release

Only after Task 10 passes:

1. Resume corpus validation at index 34 and continue through 50, re-running the differential gate after parser changes.
2. Generate compatibility documentation grouped `Generation -> ROM family -> ROM`.
3. Add objective ROM properties: active species count, moves, abilities, types, table ABIs, engine lineage, rulesets, mechanics, encounters, source-authored gaps, and review status.
4. Validate the production APK/UI/API contract, including the capability report and save-detected LEVEL_UP setting.
5. Build, sign, locally deploy, and verify the release candidate through the established signing pipeline.
6. Run the full final corpus and runtime gate, publish the release, verify signer/version/SHA/download, then refresh the existing GAFT listing.
