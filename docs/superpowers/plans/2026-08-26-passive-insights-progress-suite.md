# DualScreenDex Passive Insights and Progress Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Party Analysis, Atlas Area Guide, Trainer Progress and Save Timeline, Pokédex Specimens, selected-move Damage Forecast, and portable Challenges as six independently complete, measured, and releasable features.

**Architecture:** Keep `UnifiedGameStateDecoder` and its immutable `ResolvedGameSnapshot` as the only current-state authority. Add deterministic companion-core projections for analysis, guide, progress, storage, and forecast; expose them through the existing API; render them through the existing Preact navigation stack; store only historical events and preferences in one ROM-SHA-plus-save-identity journal. Static facts come from the active parsed catalog, never from ROM names, filenames, hashes, retail offsets, or presumed ancestry.

**Tech Stack:** Kotlin/JVM, Android Kotlin, Gradle, Gson, Preact, TypeScript, Vitest, Playwright, Node.js tooling, GitHub Actions protected Android signing.

**Governing specification:** `docs/superpowers/specs/2026-08-26-passive-insights-progress-suite-design.md`

**Execution order:** Reference corpus → Party Analysis → Atlas Area Guide → Trainer Progress/Timeline → Pokédex Specimens → Damage Forecast → challenge expansion. This ordering implements the later requirement to deliver the easiest complete wins first. It supersedes the older feature order without weakening any feature contract.

**Evidence rule:** Use the real official and source-backed ROM/save/memory/source controls named by the specification first. Synthetic inputs are allowed only after the real structure is established, and only for malformed, ambiguous, boundary, mutation, or lifecycle cases. A synthetic pass is never compatibility evidence.

**Repository rule:** Work from a clean isolated branch. Stage only paths owned by the active task. Preserve unrelated upstream work and rebase or merge deliberately before the feature audit.

---

## Release and stage invariants

Every feature stage follows this exact sequence:

1. Add the smallest failing contract test.
2. Implement the pure model or projection.
3. Integrate it vertically through the shared state/API/UI path.
4. Run focused tests, then the affected module suites.
5. Validate the required real controls and generate numeric compatibility evidence.
6. Compare every applicable specification requirement against implementation, automated, and real-data evidence.
7. Record unresolved items using only `BLOCKER`, `DEFERRED`, `NOT_APPLICABLE`, `NOT_FOUND`, or `ERROR`.
8. Fix every `BLOCKER` and `ERROR` before advancing.
9. Publish exactly one next unused numeric RC only after every current-feature requirement is `SATISFIED` or genuinely `NOT_APPLICABLE`, with any cross-feature dependency assigned to a named later stage as `DEFERRED`.

Stage 0 is reference preparation, not a product feature, and creates no RC. Intermediate commits, tooling, models, and UI slices create no RC. The RC number is discovered from remote tags only after the feature gate passes; RC numbers are never reserved at stage start.

No plan task installs or launches the APK on a console or emulator. Device acceptance remains user-owned unless separately requested.

### Required audit artifacts

Create and update these paths throughout execution:

- `docs/reports/passive-insights-progress/deferrals.md` — one durable register with requirement, originating stage, reason, target stage, and closure evidence.
- `docs/reports/passive-insights-progress/stage-0-reference-audit.md`
- `docs/reports/passive-insights-progress/party-analysis-audit.md`
- `docs/reports/passive-insights-progress/party-analysis-compatibility.json`
- `docs/reports/passive-insights-progress/area-guide-audit.md`
- `docs/reports/passive-insights-progress/area-guide-compatibility.json`
- `docs/reports/passive-insights-progress/progress-timeline-audit.md`
- `docs/reports/passive-insights-progress/progress-timeline-compatibility.json`
- `docs/reports/passive-insights-progress/specimens-audit.md`
- `docs/reports/passive-insights-progress/specimens-compatibility.json`
- `docs/reports/passive-insights-progress/damage-forecast-audit.md`
- `docs/reports/passive-insights-progress/damage-forecast-compatibility.json`
- `docs/reports/passive-insights-progress/challenge-expansion-audit.md`
- `docs/reports/passive-insights-progress/challenge-expansion-compatibility.json`

Every audit table uses:

```text
Requirement | Implementation evidence | Automated evidence | Real-data evidence | Result | Classification
```

Every compatibility JSON reports numeric percentages by applicable field or semantic family and preserves `NOT_FOUND`, `NOT_APPLICABLE`, and `ERROR` as separate counts.

---

## Shared file and responsibility map

### Existing authorities to extend

- `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt` — the only live/recovery merger.
- `app/src/main/java/com/darkaxt/dualdex/live/TransientGameStateSource.kt` — immutable resolved snapshot and section availability.
- `app/src/main/java/com/darkaxt/dualdex/live/RecoveryProjection.kt` — validated SaveRAM recovery input.
- `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt` — snapshot-to-companion runtime adapter.
- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt` — product state/actions.
- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt` — browser API projection.
- `companion-web/src/navigation.ts` — real bounded UI route stack.
- `companion-web/src/App.tsx` — route and screen composition.
- `companion-web/src/models.ts` — serialized API contracts.
- `companion-web/src/styles.css` — ROM-themed 4:3 presentation.
- `app/src/main/java/com/darkaxt/dualdex/performance/` — existing load/runtime instrumentation.

### Shared new code

- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/semantic/SemanticFacts.kt`
- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/semantic/SemanticEvents.kt`
- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/semantic/SnapshotTransitionEvaluator.kt`
- `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournal.kt`
- `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournalCodec.kt`
- `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournalStore.kt`
- `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournalCoordinator.kt`

No new subsystem may add a RetroArch poller, independently read a whole ROM/save/memory image, or cache a second mutable copy of current state.

---

## Stage 0 — Reference corpus and semantic vocabulary — no RC

### Task 0.1: Freeze the authenticated official achievement manifest

**Files:**

- Create: `tools/retroachievements/extract-pokemon-achievements.mjs`
- Create: `tools/retroachievements/extract-pokemon-achievements.test.mjs`
- Create: `docs/research/retroachievements/official-gen1-gen3-manifest.json`
- Modify: `.gitignore`

- [x] **Step 1: Add failing extractor tests.**

Test the authenticated eleven-game iteration, exact game IDs, normalized title/description/classification/provenance fields, deterministic sorting, payload hashing, retryable API errors, and fail-closed malformed responses. `API_GetGameExtended` returns one complete achievement set per game and therefore has no pagination contract. The test writes only beneath the process temporary directory.

- [x] **Step 2: Run the focused RED test.**

```powershell
node --test tools/retroachievements/extract-pokemon-achievements.test.mjs
```

Expected: failure because the extractor does not exist.

- [x] **Step 3: Implement the extractor.**

Accept the API credential through an environment variable, never a command-line argument or repository file. Persist only the permitted sanitized research fields under `D:\Temp\dualdex-retroachievements\research`; reject unexpected fields on reuse. Write a commit-safe manifest containing game ID, generation, retrieval time, achievement count, and SHA-256 for each uncommitted research payload. Do not persist the wider authenticated API response.

- [x] **Step 4: Run the test and one authenticated extraction.**

Require 11 exact official game IDs and record the extracted count without assuming the earlier 1,003 total is unchanged.

- [x] **Step 5: Commit the extractor and manifest only.**

```powershell
git add .gitignore tools/retroachievements/extract-pokemon-achievements.mjs tools/retroachievements/extract-pokemon-achievements.test.mjs docs/research/retroachievements/official-gen1-gen3-manifest.json
git commit -m "research: freeze official Pokemon achievement manifest"
```

### Task 0.2: Classify descriptions into an independent semantic vocabulary

**Files:**

- Create: `tools/retroachievements/classify-pokemon-achievements.mjs`
- Create: `tools/retroachievements/classify-pokemon-achievements.test.mjs`
- Create: `docs/research/retroachievements/semantic-vocabulary.schema.json`
- Create: `docs/research/retroachievements/official-gen1-gen3-classification.json`
- Create: `docs/reports/passive-insights-progress/reference-classification.md`

- [x] **Step 1: Write RED tests for deterministic classification.**

Cover boolean facts, typed comparisons, set membership/counts, event counts/order, temporal scopes, forbidden events, bounded-group completion, progress targets, reset/pause/miss/completion predicates, and Tier 4 research exclusions.

- [x] **Step 2: Run the classifier RED test.**

```powershell
node --test tools/retroachievements/classify-pokemon-achievements.test.mjs
```

- [x] **Step 3: Implement independently worded templates.**

The output retains source IDs for traceability but does not copy achievement logic or claim RetroAchievements credit. Each record declares its required facts, events, catalog roles, temporal scope, portability tier, classification outcome, and reason.

- [x] **Step 4: Generate and validate the report.**

Report `classified / extracted`, `expressible / classified`, and exclusions by reason. Validate output against the schema and prove identical input produces byte-identical derived JSON.

- [x] **Step 5: Commit the vocabulary and derived evidence.**

```powershell
git add tools/retroachievements docs/research/retroachievements docs/reports/passive-insights-progress/reference-classification.md
git commit -m "research: classify portable Pokemon challenge semantics"
```

### Task 0.3: Audit Stage 0 and register later dependencies

**Files:**

- Create: `docs/reports/passive-insights-progress/stage-0-reference-audit.md`
- Create: `docs/reports/passive-insights-progress/deferrals.md`

- [x] **Step 1: Compare Sections 4, 5.2–5.4, 11.1–11.2, and 15.2 of the specification against the artifacts.**
- [x] **Step 2: Mark runtime evaluation and UI as named Stage 3/6 deferrals.**
- [x] **Step 3: Resolve every Stage 0 blocker/error.**
- [x] **Step 4: Commit the audit. Do not change release metadata, create a tag, or publish an APK.**

---

## Stage 1 — Feature A: Party Analysis — first feature RC

### Task 1.1: Expose immutable analysis inputs

**Files:**

- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`

- [x] **Step 1: Add a RED API test for the active parsed type chart.**

Assert that a mutated real catalog matchup projects as data and that an empty/failed chart projects as unavailable rather than neutral.

- [x] **Step 2: Run the focused RED test.**

```powershell
.\gradlew.bat :companion-core:test --tests "*ApiViewBuilderTest" --no-daemon --console=plain
```

- [x] **Step 3: Add `TypeMatchupView(attackingTypeId, defendingTypeId, multiplierPercent)` to `CatalogView`.**

Project `ParsedCatalog.typeChart` directly. Do not create a retail fallback matrix.

- [x] **Step 4: Run the focused test GREEN and commit.**

### Task 1.2: Implement deterministic Party Analysis

**Files:**

- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/analysis/PartyAnalysis.kt`
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/analysis/PartyAnalyzer.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/analysis/PartyAnalyzerTest.kt`

- [x] **Step 1: Add RED tests for empty, partial, fainted, single-member, and six-member parties.**
- [x] **Step 2: Add a real-catalog mutation test proving changed type matchups change the result without ROM identity checks.**
- [x] **Step 3: Implement immutable models for team summary, offensive coverage, defensive profile, and development.**
- [x] **Step 4: Withhold only calculations whose inputs are absent; never coerce unknown power/category/ability semantics to neutral.**
- [x] **Step 5: Run GREEN.**

```powershell
.\gradlew.bat :companion-core:test --tests "*PartyAnalyzerTest" --no-daemon --console=plain
```

- [x] **Step 6: Commit the pure analysis slice.**

### Task 1.3: Add the Party Analysis API and route

**Files:**

- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/navigation.ts`
- Modify: `companion-web/src/navigation.test.ts`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/App.production.test.tsx`

- [x] **Step 1: Add RED serialization and navigation tests for `PARTY_ANALYSIS`.**
- [x] **Step 2: Project analysis from the current `AppSnapshot.party` and active catalog only.**
- [x] **Step 3: Add `PARTY_ANALYSIS` to `UiRoute`; preserve Party roster/scroll state and unwind member/move/nature/ability/species routes one level at a time.**
- [x] **Step 4: Run Kotlin and browser tests GREEN.**

```powershell
.\gradlew.bat :companion-core:test --tests "*ApiViewBuilderTest" --no-daemon --console=plain
Push-Location companion-web
npm test -- --run src/navigation.test.ts src/App.production.test.tsx
Pop-Location
```

- [x] **Step 5: Commit the vertical contract.**

### Task 1.4: Build the approved Party Analysis page

**Files:**

- Create: `companion-web/src/pages/PartyAnalysisPage.tsx`
- Create: `companion-web/src/pages/PartyAnalysisPage.test.tsx`
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/pages/PartyPage.test.tsx`
- Modify: `companion-web/src/styles.css`
- Modify: `companion-web/src/layoutStyles.test.ts`

- [x] **Step 1: Add RED tests for the Analysis action and all four ordered sections.**
- [x] **Step 2: Render matrices, type chips, member portraits, unavailable-input omissions, accessible names, and non-color-only states.**
- [x] **Step 3: Preserve the approved six-slot 2×3 Party board unchanged.**
- [x] **Step 4: Assert there are no subjective team grades, replacement recommendations, debug labels, redundant subtitles, or 4:3 overflow.**
- [x] **Step 5: Run browser tests and production build GREEN.**

```powershell
Push-Location companion-web
npm test -- --run src/pages/PartyPage.test.tsx src/pages/PartyAnalysisPage.test.tsx src/layoutStyles.test.ts src/App.production.test.tsx
npm run build
Pop-Location
```

- [x] **Step 6: Commit the UI slice.**

### Task 1.5: Validate, audit, and release Party Analysis

**Files:**

- Create: `tools/reports/party-analysis-compatibility.mjs`
- Create: `tools/reports/party-analysis-compatibility.test.mjs`
- Create: `docs/reports/passive-insights-progress/party-analysis-compatibility.json`
- Create: `docs/reports/passive-insights-progress/party-analysis-audit.md`
- Modify: `docs/reports/passive-insights-progress/deferrals.md`
- Modify at release gate only: `release/v1-ready.json`
- Modify at release gate only: `.github/workflows/release.yml`
- Modify at release gate only: `tools/release/release-workflow.test.mjs`

- [x] **Step 1: Run the report against all required official controls plus Modern Emerald, Unbound, and Odyssey.**
- [x] **Step 2: Report numeric coverage independently for party fields, moves, categories, type chart, evolutions, and proven ability modifiers.**
- [x] **Step 3: Run the affected full suites and performance regression checks.**

```powershell
.\gradlew.bat :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest --no-daemon --console=plain
Push-Location companion-web
npm test
npm run build
Pop-Location
```

- [x] **Step 4: Compare Sections 3, 5, 6, 12, 14, 15, and 17 against the evidence table.**
- [x] **Step 5: Close every blocker/error; assign only genuine later-feature dependencies to a named stage in `deferrals.md`.**
- [x] **Step 6: Commit the complete feature and audit before touching release metadata.**
- [x] **Step 7: Discover the next unused numeric RC, add one Party Analysis readiness gate, run release tests/build, publish through protected signing, and verify the signed artifact version, checksum, and certificate. Do not install it.**

---

## Stage 2 — Feature D: Atlas Area Guide — second feature RC

### Task 2.1: Derive one knowledge-safe Area Guide projection

**Files:**

- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/map/AreaGuide.kt`
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/map/AreaGuideBuilder.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/map/AreaGuideBuilderTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`

- [x] **Step 1: Add RED tests for tracked area, manually selected area, Organic projection, Discovered projection, and unsupported sections.**
- [x] **Step 2: Build Overview, Encounters, Places/Services, Trainers/People, Items, and Objectives from the same area/POI/knowledge inputs already used by Atlas.**
- [x] **Step 3: Omit unsupported sections; never emit `Place`, duplicate area names, hidden POIs, or raw parser/capability values.**
- [x] **Step 4: Normalize sign text to its first meaningful expanded line and use `Your` when live player substitution is unavailable.**
- [x] **Step 5: Project `AreaGuideView` through `StateView` and run focused tests GREEN.**
- [x] **Step 6: Commit the projection.**

### Task 2.2: Implement the drawer without disturbing map state

**Files:**

- Create: `companion-web/src/pages/AreaGuideDrawer.tsx`
- Create: `companion-web/src/pages/AreaGuideDrawer.test.tsx`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/styles.css`
- Modify: `companion-web/src/layoutStyles.test.ts`

- [x] **Step 1: Add RED tests for opening/closing, tracked-area changes, manual selection, item/POI visibility, and filter reuse.**
- [x] **Step 2: Add one Area Guide control styled consistently with existing Atlas controls.**
- [x] **Step 3: Render a drawer over the current map and virtualize long sections.**
- [x] **Step 4: Keep center, zoom, tracking, fog, POI filter state, label thresholds, and mounted-raster set unchanged across drawer operations.**
- [x] **Step 5: Allow centering/highlighting only for already knowledge-visible POIs.**
- [x] **Step 6: Run the focused browser tests and production build GREEN.**
- [x] **Step 7: Commit the drawer.**

### Task 2.3: Prove map continuity and real content

**Files:**

- Create: `tools/reports/area-guide-compatibility.mjs`
- Create: `tools/reports/area-guide-compatibility.test.mjs`
- Create: `docs/reports/passive-insights-progress/area-guide-compatibility.json`
- Modify: the active performance collector and its test discovered under `app/src/main/java/com/darkaxt/dualdex/performance/`.

- [x] **Step 1: Validate official Emerald and FRLG plus Modern Emerald, Unbound, and Odyssey for names, exits, encounters, POIs, and filters.**
- [x] **Step 2: Validate Gen I/II official controls and report omitted facts as `NOT_APPLICABLE` only when the format genuinely cannot prove them.**
- [x] **Step 3: Assert hidden items remain absent until the player enters the tile or one of its eight neighbors.**
- [x] **Step 4: Record drawer projection/render time and retained item count in Debug logs only; prove no ROM parse, raster copy, duplicate poller, or persistent render loop.**

### Task 2.4: Audit and release Area Guide

**Files:**

- Create: `docs/reports/passive-insights-progress/area-guide-audit.md`
- Modify: `docs/reports/passive-insights-progress/deferrals.md`
- Modify at release gate only: release readiness/workflow files.

- [x] **Step 1: Compare Sections 3, 9, 12, 14, 15, and 17 against the audit table.**
- [x] **Step 2: Record Objectives population as a Stage 3 deferral while the absent section remains truthful.**
- [x] **Step 3: Fix every other blocker/error and run affected full Kotlin/browser suites.**
- [x] **Step 4: Commit the complete feature and audit.**
- [x] **Step 5: Only now discover and publish the next unused numeric RC using the common signed-artifact gate. Do not install it.**

---

## Stage 3 — Feature B: Trainer Progress, Challenges, and Save Timeline — third feature RC

### Task 3.1: Normalize facts and deduplicated semantic transitions

**Files:**

- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/semantic/SemanticFacts.kt`
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/semantic/SemanticEvents.kt`
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/semantic/SnapshotTransitionEvaluator.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/semantic/SnapshotTransitionEvaluatorTest.kt`

- [x] **Step 1: Add RED tests for captures, evolutions, area visits, POI discoveries, battle start/end, Party changes, and save observations.**
- [x] **Step 2: Reproduce `48/48 -> 1/1 -> 1/2`, reconnect, cache reload, repeated poll, and live/recovery authority transitions; assert zero false events.**
- [x] **Step 3: Implement pure comparison over stable immutable snapshots and established battle/save lifecycle epochs.**
- [x] **Step 4: Run GREEN and commit.**

### Task 3.2: Add one ROM-save-scoped journal and atomic persistence

**Files:**

- Create: `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournal.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournalCodec.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournalStore.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/progress/PlaythroughJournalCoordinator.kt`
- Create: corresponding tests under `app/src/test/java/com/darkaxt/dualdex/progress/`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpoint.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCodec.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/knowledge/SaveKnowledgeCheckpointCoordinator.kt`

- [x] **Step 1: Add RED identity, migration, sanitization, atomic-write, and 512-entry deterministic compaction tests.**
- [x] **Step 2: Store only historical facts, tracked counts, challenge state, timeline entries, and designated preferences.**
- [x] **Step 3: Reuse the validated changed-save fingerprint and checkpoint write path; write nothing for `INITIAL`, `UNCHANGED`, malformed, mismatched, or recovery-only observations.**
- [x] **Step 4: Keep current Trainer, Party, PC, Pokédex, money, clock, location, bag, and battle state out of journal authority.**
- [x] **Step 5: Run knowledge/save/progress tests GREEN and commit.**

### Task 3.3: Implement baseline challenge evaluation

**Files:**

- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/progress/ChallengeModels.kt`
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/progress/ChallengeEngine.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/progress/ChallengeEngineTest.kt`
- Create: `app/src/main/assets/challenges/portable-baseline.json`

- [x] **Step 1: Add RED tests for each initial predicate operator and capability-gated applicability.**
- [x] **Step 2: Implement only independently worded Tier 1 templates required for baseline Progress.**
- [x] **Step 3: Hide or generically mask future undiscovered entities in Organic mode.**
- [x] **Step 4: Preserve first proven completion save/time and evaluate incrementally without rescanning the full journal.**
- [x] **Step 5: Run GREEN and commit.**

### Task 3.4: Expose Metrics, Challenges, Timeline, and Atlas objectives

**Files:**

- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/navigation.ts`
- Create: `companion-web/src/pages/ProgressPage.tsx`
- Create: `companion-web/src/pages/ProgressPage.test.tsx`
- Modify: `companion-web/src/pages/TrainerCardPage.tsx`
- Modify: `companion-web/src/pages/TrainerCardPage.test.tsx`
- Modify: `companion-web/src/pages/AreaGuideDrawer.tsx`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/styles.css`

- [x] **Step 1: Add RED tests for CARD/PROGRESS and METRICS/CHALLENGES/TIMELINE navigation, remembered ROM-save-scoped selection, and immediate Trainer-license availability.**
- [x] **Step 2: Expose game totals from the current resolved snapshot and tracked journey values from the journal with explicit player-facing scope.**
- [x] **Step 3: Render challenge progress and meaningful save deltas without addresses, parser stages, flag IDs, provenance, or internal capability labels.**
- [x] **Step 4: Populate Area Guide Objectives only with applicable knowledge-safe local challenges.**
- [x] **Step 5: Prove Trainer Card and Pokédex totals are identical projections of the resolved snapshot.**
- [x] **Step 6: Run focused Kotlin/browser tests and commit.**

### Task 3.5: Profile, audit, and release Progress/Timeline

**Files:**

- Create: `tools/reports/progress-timeline-compatibility.mjs`
- Create: `docs/reports/passive-insights-progress/progress-timeline-compatibility.json`
- Create: `docs/reports/passive-insights-progress/progress-timeline-audit.md`
- Modify: `docs/reports/passive-insights-progress/deferrals.md`
- Modify: existing performance collector/tests.
- Modify at release gate only: release readiness/workflow files.

- [x] **Step 1: Validate all 11 official controls plus Modern Emerald, Unbound, and Odyssey with real save/memory/source evidence where applicable.**
- [x] **Step 2: Report current-total fields, observable event families, baseline applicable templates, and validated templates as separate percentages.**
- [x] **Step 3: Prove exact identity isolation, APK-update retention, unchanged-save no-write behavior, bounded journal growth, and absence of false transition events.**
- [x] **Step 4: Compare Sections 3, 5, 7, 11.1–11.2, 12–15, and 17 against the audit table.**
- [x] **Step 5: Close the Stage 2 Objectives deferral and assign Tier 2/3 expansion to Stage 6. Fix every blocker/error.**
- [ ] **Step 6: Run full affected suites, commit the complete feature/audit, then discover and publish exactly one next numeric RC. Do not install it.**

---

## Stage 4 — Feature C: Pokédex Specimens — fourth feature RC

### Task 4.1: Replace flat recovery-only storage with unified owned storage

**Files:**

- Modify: `app/src/main/java/com/darkaxt/dualdex/live/TransientGameStateSource.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/ResolvedStateTrace.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateRealControlTest.kt`

- [ ] **Step 1: Add RED tests for `ResolvedOwnedStorageState(party, boxes)`, live-over-recovery replacement, validated empty live boxes, and unsupported live storage fallback.**
- [ ] **Step 2: Preserve `UnifiedGameStateDecoder` as the sole merger and remove direct feature access to `SaveSnapshot.storedIndividuals`.**
- [ ] **Step 3: Deduplicate Party/storage by stable individual identity where available, otherwise by ROM-save-scoped storage location plus validated record digest.**
- [ ] **Step 4: Run GREEN and commit.**

### Task 4.2: Decode live PC storage without a second poller

**Files:**

- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3PlayerRuntimeLayoutResolver.kt`
- Modify: the active Gen I/II runtime layout resolvers discovered with `rg "RuntimeLayoutResolver" parser-core/src`.
- Modify: the active generation-specific live snapshot readers discovered with `rg "LiveGameState|LiveMemoryReader" battle-memory/src app/src`.
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Create: generation-specific real-control tests beside the affected resolvers/readers.

- [ ] **Step 1: Freeze official real ROM/save/memory/source tuples for each applicable generation before code changes.**
- [ ] **Step 2: Add narrow RED tests for structurally validated storage ranges, record checksum/encryption, empty boxes, partial boxes, and corrupt records.**
- [ ] **Step 3: Extend the existing read plan with bounded storage regions; do not add an independent poll loop or whole-memory copy.**
- [ ] **Step 4: Prove Modern Emerald, Unbound, and Odyssey with source-backed layouts; fail closed for unresolved hacks.**
- [ ] **Step 5: Run parser/save/battle/live tests GREEN and commit.**

### Task 4.3: Add specimen API, routes, and shared individual details

**Files:**

- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/navigation.ts`
- Create: `companion-web/src/pages/SpecimensPage.tsx`
- Create: `companion-web/src/pages/SpecimensPage.test.tsx`
- Create: `companion-web/src/pages/OwnedIndividualDetail.tsx`
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/pages/PokedexDetail.tsx`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Add RED tests proving one caught species lists all and only its Party/PC instances and a caught flag alone invents no card.**
- [ ] **Step 2: Expose only validated specimen fields and a player-facing Party/box-slot location.**
- [ ] **Step 3: Reuse one individual-detail component for Party and Specimens, including nature/ability/move/species child routes.**
- [ ] **Step 4: Preserve species-list scroll position and one-level Back behavior.**
- [ ] **Step 5: Keep uncaught species, hidden encounters, opponent knowledge, and undiscovered evolution identities hidden under existing Organic policy.**
- [ ] **Step 6: Run focused tests/build GREEN and commit.**

### Task 4.4: Validate, audit, and release Specimens

**Files:**

- Create: `tools/reports/specimens-compatibility.mjs`
- Create: `docs/reports/passive-insights-progress/specimens-compatibility.json`
- Create: `docs/reports/passive-insights-progress/specimens-audit.md`
- Modify: `docs/reports/passive-insights-progress/deferrals.md`
- Modify at release gate only: release readiness/workflow files.

- [ ] **Step 1: Validate official Gen I/II/III plus Modern Emerald, Unbound, and Odyssey using real record controls.**
- [ ] **Step 2: Report numeric coverage for identity, species/form, level, nickname, gender, HP/status, EXP, nature, ability, held item, moves, IV/DV, rarity, and storage location.**
- [ ] **Step 3: Prove Party-to-PC and PC-to-Party movement neither duplicates nor loses individuals.**
- [ ] **Step 4: Compare Sections 3, 8, 12–15, and 17; fix every blocker/error.**
- [ ] **Step 5: Run affected full suites, commit the complete feature/audit, then discover and publish exactly one next numeric RC. Do not install it.**

---

## Stage 5 — Feature E: Selected-move Damage Forecast — fifth feature RC

### Task 5.1: Define capability-gated forecast input and output

**Files:**

- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/battle/DamageForecast.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/battle/DamageForecastModelsTest.kt`

- [ ] **Step 1: Add RED model tests for exact, bounded, and absent outcomes.**
- [ ] **Step 2: Define one immutable input containing formula evidence, attacker, target, move, field state, type chart, and proven modifiers.**
- [ ] **Step 3: Require explicit semantic evidence for every admitted mechanic; absence must withhold or safely bound the result.**
- [ ] **Step 4: Run GREEN and commit.**

### Task 5.2: Implement official formula families and mutation rejection

**Files:**

- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/battle/DamageForecastCalculator.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/battle/DamageForecastCalculatorTest.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/battle/DamageForecastRealControlTest.kt`

- [ ] **Step 1: Add RED golden tests for official Gen I, II, and III real ROM-derived move/type inputs.**
- [ ] **Step 2: Independently cover physical/special rules, STAB, effectiveness, random factor, status, critical, weather, multi-hit, fixed damage, immunity, ability, and item paths where proven.**
- [ ] **Step 3: Add source-backed Modern Emerald, Unbound, and Odyssey mutation controls that reject altered or unknown semantics rather than applying retail rules.**
- [ ] **Step 4: Implement integer-accurate calculation and bounded uncertainty; run GREEN and commit.**

### Task 5.3: Assemble forecast input from the unified live snapshot

**Files:**

- Modify: the active `LiveBattleState` model and tests discovered with `rg "data class LiveBattleState" battle-memory/src app/src`.
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`

- [ ] **Step 1: Add RED tests for selected move, resolved double-battle command owner, automatic/manual target, attacker/target changes, and late stable updates.**
- [ ] **Step 2: Add only missing live fields to the existing bounded read plan.**
- [ ] **Step 3: Memoize by immutable forecast-input equality; no recalculation occurs on irrelevant polling samples.**
- [ ] **Step 4: Preserve Organic opponent privacy and omit forecasts when hidden ability/state can make the result misleading.**
- [ ] **Step 5: Run GREEN and commit.**

### Task 5.4: Integrate the forecast into Battle Attack

**Files:**

- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/BattlePage.tsx`
- Modify: `companion-web/src/pages/BattlePage.test.tsx`
- Modify: `companion-web/src/styles.css`
- Modify: `companion-web/src/layoutStyles.test.ts`

- [ ] **Step 1: Add RED tests for exact, bounded, and absent presentation in the selected-move Attack panel.**
- [ ] **Step 2: Show HP range, target-HP percentage, hit range to knock out, accuracy, effectiveness, and only active player-visible conditions.**
- [ ] **Step 3: Keep existing move metadata/effectiveness when the forecast is absent and show no technical error panel.**
- [ ] **Step 4: Assert no mechanic IDs, THUMB provenance, pointer/source labels, or capability codes reach ordinary UI.**
- [ ] **Step 5: Run browser tests/build GREEN and commit.**

### Task 5.5: Profile, audit, and release Damage Forecast

**Files:**

- Create: `tools/reports/damage-forecast-compatibility.mjs`
- Create: `docs/reports/passive-insights-progress/damage-forecast-compatibility.json`
- Create: `docs/reports/passive-insights-progress/damage-forecast-audit.md`
- Modify: `docs/reports/passive-insights-progress/deferrals.md`
- Modify: existing performance collector/tests.
- Modify at release gate only: release readiness/workflow files.

- [ ] **Step 1: Report numeric exact, bounded, absent, `NOT_FOUND`, `NOT_APPLICABLE`, and `ERROR` outcomes by required mechanic for every control.**
- [ ] **Step 2: Prove no duplicate memory read, polling loop, stale attacker/target flicker, or sustained render loop.**
- [ ] **Step 3: Compare Sections 3, 10, 12, 14, 15, and 17; fix every blocker/error.**
- [ ] **Step 4: Run all affected suites, commit the complete feature/audit, then discover and publish exactly one next numeric RC. Do not install it.**

---

## Stage 6 — Feature F: Portable Challenge Engine Expansion — sixth feature RC

### Task 6.1: Expand Tier 2 and Tier 3 templates from classified evidence

**Files:**

- Modify: `app/src/main/assets/challenges/portable-baseline.json`
- Create: `app/src/main/assets/challenges/portable-extended.json`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/progress/ChallengeEngine.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/progress/ChallengeEngineTest.kt`

- [ ] **Step 1: Select only classified achievements that require existing operators and proven normalized roles/events.**
- [ ] **Step 2: Add one RED test per new semantic family before adding its template.**
- [ ] **Step 3: Bind badge, leader, area collectible, regional target, and other entities through parsed catalog roles only.**
- [ ] **Step 4: Require a proven adapter for Tier 3; keep Tier 4 in research evidence and outside runtime.**
- [ ] **Step 5: Run deterministic inventory/evaluation tests GREEN and commit in small semantic-family commits.**

### Task 6.2: Validate dynamic binding and mutation rejection

**Files:**

- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/progress/ChallengeOfficialControlsTest.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/progress/ChallengeHackControlsTest.kt`
- Create: `tools/reports/challenge-expansion-compatibility.mjs`
- Create: `docs/reports/passive-insights-progress/challenge-expansion-compatibility.json`

- [ ] **Step 1: Freeze deterministic inventories and results for all 11 official controls.**
- [ ] **Step 2: Prove Modern Emerald, Unbound, and Odyssey dynamic binding without ROM-name/hash/offset selectors.**
- [ ] **Step 3: Mutate flag roles, identifiers, temporal windows, and ambiguous entities; require dependent challenges to disappear or report `NOT_FOUND`, never silently rebind.**
- [ ] **Step 4: Report the five required challenge percentages from specification Section 15.2.**

### Task 6.3: Audit the completed suite and release challenge expansion

**Files:**

- Create: `docs/reports/passive-insights-progress/challenge-expansion-audit.md`
- Modify: `docs/reports/passive-insights-progress/deferrals.md`
- Modify: `README.md`
- Modify: compatibility documentation linked by `README.md`.
- Modify at release gate only: release readiness/workflow files.

- [ ] **Step 1: Compare Sections 3–18 against the final audit, including all six independent feature audits.**
- [ ] **Step 2: Close every in-scope deferral; retain Tier 4 only as a documented research exclusion.**
- [ ] **Step 3: Re-run diagnostic-leakage scans, navigation tests, Organic privacy tests, identity/persistence tests, compatibility reports, and performance gates.**
- [ ] **Step 4: Run the complete repository verification.**

```powershell
.\gradlew.bat :save-core:test :battle-memory:test :parser-core:test :catalog-store:test :companion-core:test :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-daemon --console=plain
Push-Location companion-web
npm test
npm run build
Pop-Location
node --test tools/release/release-workflow.test.mjs tools/release/release-metadata.test.mjs
```

- [ ] **Step 5: Commit documentation and final suite evidence before release metadata.**
- [ ] **Step 6: Discover and publish exactly one next unused numeric RC through the protected signing workflow; verify version, checksum, certificate, release notes, and compatibility assets. Do not install it.**

---

## Common feature release procedure

Use this only after the current feature audit contains no `BLOCKER` or `ERROR` and its user contract is complete.

- [ ] Fetch `fork/master` and all remote tags; reconcile upstream without absorbing unrelated work.
- [ ] Derive the next unused `v1.1.0-rc.N` with `N <= 98`; never infer it from local branch names or preallocate it.
- [ ] Add exactly one feature readiness field to `release/v1-ready.json` and require it in `.github/workflows/release.yml`.
- [ ] Add a workflow test proving the new readiness gate and required audit/compatibility assets.
- [ ] Run release metadata/workflow tests and the feature's complete build gate.
- [ ] Commit release metadata separately from implementation.
- [ ] Push the audited completion through the repository's protected main/release flow and create one tag.
- [ ] Wait for GitHub signing to finish; download the produced APK and checksums.
- [ ] Verify numeric `versionName`, `versionCode`, tag, filename, SHA-256, and certificate fingerprint agree.
- [ ] Do not install, launch, or interact with the console.

If implementation, evidence, or packaging validation fails before publication, repair it without consuming an RC number. If a published immutable candidate is objectively defective, document the failure and use the next number for the corrected complete feature; never overwrite the tag.

---

## Final specification cross-check

Before declaring the plan complete, verify that execution has produced:

- six independent feature audits and six numeric compatibility reports;
- one Stage 0 audit and immutable reference manifest;
- one resolved current-state authority and one isolated historical journal;
- zero ordinary-UI diagnostic/provenance strings;
- zero unresolved blockers/errors;
- every deferral assigned to and closed by a named later stage, except documented Tier 4 research exclusions;
- Organic-mode evidence for every feature;
- official Gen I–III and Modern Emerald/Unbound/Odyssey evidence where applicable;
- no duplicate pollers, full-memory copies, unbounded journal growth, or persistent render loops; and
- exactly one RC after each fully implemented feature, with no RC for Stage 0 or partial work.
