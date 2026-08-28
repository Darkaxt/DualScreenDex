# RetroAchievement Semantic Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Recover every clearly described official Generation I-III reference that the regex classifier rejected, without importing RetroAchievements trigger bytecode, memory expressions, or runtime dependencies.

**Architecture:** Keep authenticated title/description payloads outside the repository. Commit a sanitized, fingerprint-bound semantic override registry containing only source IDs, description hashes, semantic families, portability tiers, and recovery paths. The developer classifier applies a curated override only when both source identity and description hash match; changed or unknown prose remains fail-closed. Runtime challenge definitions remain capability-gated and are not created merely because a reference is classified.

**Tech Stack:** Node.js ESM developer tools and tests, JSON research artifacts, Kotlin challenge runtime unchanged unless an already-decoded semantic fact can prove a complete objective.

**Delivery decision:** The user requested one final local commit only. The task therefore creates no intermediate commits, push, APK, version bump, tag, or RC; that decision supersedes the generic commit/push snippets below.

---

### Task 1: Define and validate the sanitized override contract

**Files:**
- Create: `docs/research/retroachievements/official-gen1-gen3-semantic-overrides.json`
- Modify: `tools/retroachievements/classify-pokemon-achievements.mjs`
- Test: `tools/retroachievements/classify-pokemon-achievements.test.mjs`

- [x] **Step 1: Write failing tests for exact fingerprint-bound overrides**

Add tests proving that `classifyAchievement()` accepts an override only when `sourceGameId`, `achievementId`, and `sourceDescriptionSha256` all match; a stale hash, duplicate key, malformed recovery path, or unknown semantic family must fail closed.

- [x] **Step 2: Run the focused test and confirm the expected failure**

Run: `node --test tools/retroachievements/classify-pokemon-achievements.test.mjs`

Expected: failure because the classifier does not yet accept or validate semantic overrides.

- [x] **Step 3: Implement the minimal override loader and validator**

Supported recovery paths are exactly:

```text
PERSISTENT_SOURCE_FACT
NORMALIZED_LIVE_RULE
GAME_SPECIFIC_ADAPTER
SEQUENCE_SENSITIVE
```

Overrides may select an existing semantic family and portability tier 2 or 3. They may not contain titles, descriptions, trigger expressions, addresses, offsets, ROM hashes, or executable predicates. `classifyReferenceCorpus()` must reject duplicate or orphaned override records and apply an override only after validating the current description hash.

- [x] **Step 4: Run the focused test and confirm it passes**

Run: `node --test tools/retroachievements/classify-pokemon-achievements.test.mjs`

Expected: all classifier tests pass with zero failures.

- [x] **Step 5: Stage for the user-requested single final commit**

```text
git add tools/retroachievements/classify-pokemon-achievements.mjs tools/retroachievements/classify-pokemon-achievements.test.mjs docs/research/retroachievements/official-gen1-gen3-semantic-overrides.json
git commit -m "feat: add curated achievement semantic overrides"
```

### Task 2: Recover all 120 rejected references

**Files:**
- Modify: `docs/research/retroachievements/official-gen1-gen3-semantic-overrides.json`
- Modify: `docs/research/retroachievements/official-gen1-gen3-classification.json`
- Modify: `docs/reports/passive-insights-progress/reference-classification.md`
- Test: `tools/retroachievements/classify-pokemon-achievements.test.mjs`

- [x] **Step 1: Write a failing whole-corpus expectation**

Add a test using the immutable manifest and authenticated research directory that expects:

```text
classified = 1003
unclassified = 0
expressible = 1003
PERSISTENT_SOURCE_FACT = 56
NORMALIZED_LIVE_RULE = 13
GAME_SPECIFIC_ADAPTER = 43
SEQUENCE_SENSITIVE = 8
```

The test must also prove all 120 prior exclusions are present exactly once and every override matches its description fingerprint.

- [x] **Step 2: Run the focused corpus test and confirm it fails at 883/1003**

Run with `DUALDEX_RA_RESEARCH_DIRECTORY=D:\Temp\dualdex-retroachievements\research`:

`node --test tools/retroachievements/classify-pokemon-achievements.test.mjs`

- [x] **Step 3: Populate the 120 sanitized override records**

Use the completed manual audit. Assign 56 persistent source facts to tier 2, 13 normalized live rules to tier 2, 43 exact adapters to tier 3, and eight sequence-sensitive objectives to tier 3. Do not copy source prose into the repository.

- [x] **Step 4: Regenerate classification artifacts atomically**

Run: `node tools/retroachievements/classify-pokemon-achievements.mjs`

Expected: `Classified 1003/1003; 0 remain unclassified.`

- [x] **Step 5: Run focused tests and stage for the final commit**

```text
node --test tools/retroachievements/classify-pokemon-achievements.test.mjs
git add docs/research/retroachievements docs/reports/passive-insights-progress/reference-classification.md tools/retroachievements
git commit -m "docs: recover official achievement semantics"
```

### Task 3: Keep research classification separate from runtime support

**Files:**
- Modify: `docs/reports/passive-insights-progress/challenge-expansion-audit.md`
- Modify: `docs/reports/passive-insights-progress/deferrals.md`
- Modify: `docs/research/retroachievements/README.md`
- Test: `tools/reports/challenge-expansion-compatibility.test.mjs`

- [x] **Step 1: Write a failing documentation-policy test**

Require the reports to state separately:

```text
semantic classification: 1003/1003
new exact runtime equivalents from this recovery: 0/120
bytecode or trigger expressions imported: 0
```

The test must reject the old blanket statement that all 120 are ambiguous, glitch, trade, or unavailable-frame references.

- [x] **Step 2: Run the report test and confirm the old documentation fails**

Run: `node --test tools/reports/challenge-expansion-compatibility.test.mjs`

- [x] **Step 3: Correct the reports without claiming runtime support**

Document the four recovery paths numerically. Preserve `NOT_FOUND`, `NOT_APPLICABLE`, and `ERROR` as separate outcomes. State that an exact challenge remains absent until its normalized fact or source-backed adapter is observable through `ResolvedGameSnapshot`.

- [x] **Step 4: Run report and classifier tests and stage for the final commit**

```text
node --test tools/retroachievements/classify-pokemon-achievements.test.mjs tools/reports/challenge-expansion-compatibility.test.mjs
git add docs tools/reports
git commit -m "docs: correct achievement recovery coverage"
```

### Task 4: Audit already-decoded runtime facts for exact quick wins

**Files:**
- Inspect: `app/src/main/java/com/darkaxt/dualdex/live/ResolvedGameSnapshot.kt`
- Inspect: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/semantic/SemanticFacts.kt`
- Inspect: `app/src/main/java/com/darkaxt/dualdex/progress/ResolvedSemanticFactProjector.kt`
- Modify only if proven: corresponding focused Kotlin production and test files
- Create: `docs/reports/passive-insights-progress/semantic-recovery-runtime-audit.md`

- [x] **Step 1: Enumerate exact facts already decoded and stabilized**

For each of the 120 references, record whether the complete condition is available in the immutable resolved snapshot, not merely somewhere in raw WRAM or SaveRAM.

- [x] **Step 2: Add a failing Kotlin test for each complete, already-decoded objective**

Do not add partial predicates. A challenge is eligible only when completion, reset, miss, and temporal boundaries required by its description are all observable.

- [x] **Step 3: Implement only proven projections and definitions**

No direct memory reader, raw event flag, name/SHA/offset selection, RetroAchievements expression parser, or runtime network access may be added. If no reference satisfies the full contract, record `0/120` rather than manufacturing a release.

- [x] **Step 4: Run the affected Kotlin tests and record exact results**

Run the smallest affected Gradle module tests first. Run the complete release gate only if production Kotlin changes.

- [x] **Step 5: Stage the runtime audit for the final commit**

```text
git add docs/reports/passive-insights-progress app companion-core
git commit -m "audit: bound achievement recovery to resolved facts"
```

### Task 5: Specification cross-check and delivery decision

**Files:**
- Modify: `docs/reports/passive-insights-progress/semantic-recovery-runtime-audit.md`
- Modify: this plan checkbox state

- [x] **Step 1: Cross-check specification Sections 4, 5, 7, 11, 15 and 17**

Record every requirement as satisfied, blocked, or deferred with numeric evidence. A missing runtime fact is a tracked deferral; a false positive or identity leak is a blocker.

- [x] **Step 2: Run the complete developer-tool verification**

```text
node --test tools/retroachievements/classify-pokemon-achievements.test.mjs tools/reports/challenge-expansion-compatibility.test.mjs
npm test
```

If production Kotlin changed, additionally run the existing Gradle release gate from the specification. If only research tooling and documentation changed, do not manufacture an APK RC because no player-facing feature changed.

Observed: all 72 RetroAchievements, compatibility-report, and release-policy Node tests passed. `companion-web` test execution was attempted but its dependencies are not installed in this isolated worktree; it stopped before test discovery. Because no browser, Android, or production Kotlin source changed, no dependency installation, browser build, Gradle build, APK, or RC was added to this research-only change.

- [x] **Step 3: Create the user-requested single local commit**

```text
git status --short
git push -u fork feature/retroachievement-semantic-recovery
```

Do not merge into the active `qa/project-wide-hardening` worktree. Compare the branch with its clean committed checkpoint before later convergence. The final local commit is intentionally not pushed under the user's delivery instruction.
