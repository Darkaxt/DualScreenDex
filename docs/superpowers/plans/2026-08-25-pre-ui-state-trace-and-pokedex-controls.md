# Pre-UI State Trace and Pokédex Controls Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Capture every changed resolved-state revision before UI publication and move Pokédex search/count controls to a fixed bottom dock.

**Architecture:** `UnifiedGameStateDecoder` remains the single transient-state authority and emits a privacy-safe field diff to an injected sink immediately before listeners run. Production writes the immutable event to `DualDexState` synchronously and queues the same event into the existing bounded diagnostic log without taking per-change process metrics. `PokedexBrowse` separates top filters, scrolling results, and a bottom search/count dock without changing filter behavior.

**Tech Stack:** Kotlin/JVM, Android logcat, Gson NDJSON, JUnit 5, Preact, TypeScript, CSS Grid, Vitest/Testing Library, Gradle.

---

### Task 1: Add the pre-UI trace contract and failing decoder controls

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/live/ResolvedStateTrace.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt`

- [ ] **Step 1: Define the trace vocabulary in a new focused file**

Create typed triggers, field summaries, field changes, events, and a sink:

```kotlin
enum class ResolvedStateTraceTrigger {
    SESSION_BEGIN, LIVE_SAMPLE, RECOVERY_APPLIED, RECOVERY_STATUS,
    RECOVERY_CLEARED, LIVE_SUSPENDED, BATTLE_TRACKING, SESSION_END,
}

data class ResolvedStateFieldTrace(
    val source: ResolvedValueSource?,
    val available: Boolean,
    val count: Int? = null,
    val fingerprint: String? = null,
)

data class ResolvedStateFieldChange(
    val field: String,
    val before: ResolvedStateFieldTrace?,
    val after: ResolvedStateFieldTrace?,
)

data class ResolvedStateTraceEvent(
    val schemaVersion: Int = 1,
    val revision: Long,
    val trigger: ResolvedStateTraceTrigger,
    val romSha256Prefix: String?,
    val generation: Int?,
    val sampleId: Long?,
    val recoveryApplicationId: Long?,
    val recoveryObservationKind: SaveObservationKind?,
    val changedSections: Set<ResolvedGameSection>,
    val fields: List<ResolvedStateFieldChange>,
)

fun interface ResolvedStateTraceSink {
    fun append(event: ResolvedStateTraceEvent)
}
```

Add a package-private builder that flattens the previous and next `ResolvedGameSnapshot` into named privacy-safe summaries. Include trainer field presence/source; seen/caught source, count, and deterministic set fingerprint; party/stored source, count, and fingerprint; battle/location/clock/bag/event/ruleset source plus fingerprint; and recovery application/kind/reset metadata. Never serialize trainer/save identities or raw values.

- [ ] **Step 2: Write failing transition tests**

Add tests with an in-memory `ResolvedStateTraceSink` that assert:

```kotlin
assertEquals(ResolvedStateTraceTrigger.LIVE_SAMPLE, events.last().trigger)
assertEquals(1, events.last().fields.single { it.field == "pokedex.caught" }.after?.count)
assertEquals(ResolvedValueSource.LIVE, events.last().fields.single { it.field == "pokedex.caught" }.after?.source)
```

Cover these exact sequences:

1. recovery caught count 52 accepted before live, then live caught count 1;
2. established live caught count 1, then a live-unavailable sample falling back to already-held recovery count 52;
3. live caught count 52 followed by live caught count 1;
4. equal consecutive snapshots emit no event;
5. a throwing sink does not prevent listener publication;
6. event fields contain no player name, public ID, save identity, source path, or species IDs.

- [ ] **Step 3: Run the focused tests and confirm the intended failure**

Run:

```powershell
$env:GRADLE_USER_HOME='D:\Temp\dualdex-rc65-gradle-home'
$env:TEMP='D:\Temp\dualdex-rc65-test-temp'
$env:TMP=$env:TEMP
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.live.UnifiedGameStateDecoderTest"
```

Expected: compilation/test failure because the trace types, sink constructor parameter, and trigger-aware publication do not exist yet.

- [ ] **Step 4: Implement trigger-aware publication at the authoritative boundary**

Add a no-op-by-default sink and a `traceRevision` counter to `UnifiedGameStateDecoder`. Change every call to `publishResolved()` to pass its exact trigger. In `publishResolved(trigger)`, compute `next` and `changedSections`, build the immutable trace event, call the sink inside `runCatching`, and only then call `notifyListeners`.

Use the same traced clear helper for session replacement/end so a non-null snapshot changing to null is observable. Do not trace polling samples that resolve to an equal snapshot.

- [ ] **Step 5: Re-run the focused decoder tests**

Run the Task 1 Step 3 command again.

Expected: all `UnifiedGameStateDecoderTest` tests pass, including the three distinct caught-count source transitions.

- [ ] **Step 6: Commit the decoder trace contract**

```powershell
git add -- app/src/main/java/com/darkaxt/dualdex/live/ResolvedStateTrace.kt app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt
git commit -m "feat: trace resolved state before ui publication"
```

### Task 2: Persist state events without per-change performance sampling

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/performance/PerformanceModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/performance/PerformanceRecorder.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/performance/PerformanceRecorderTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/performance/AndroidPerformanceLogTest.kt`

- [ ] **Step 1: Write failing recorder and bounded-log tests**

Add tests that require `PerformanceEventKind.STATE_CHANGED`, an optional `stateChange` payload, and `PerformanceRecorder.stateChanged(trace)`. Assert the state event has empty `PerformanceMetrics`, the metric sampler invocation count does not increase, and the existing two 512 KiB segments still bound mixed performance/state events.

- [ ] **Step 2: Run the focused performance tests and confirm failure**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.performance.PerformanceRecorderTest" --tests "com.darkaxt.dualdex.performance.AndroidPerformanceLogTest"
```

Expected: compilation/test failure for the missing state-change event path.

- [ ] **Step 3: Add the lightweight persistence path**

Add `STATE_CHANGED` and `stateChange: ResolvedStateTraceEvent?` to the event model. Bump the diagnostics schema version once. Implement `stateChanged` so it captures the current session metadata and queues an event with empty metrics; it must not invoke `AndroidPerformanceSampler` or component counters.

In `DualDexApplication`, construct the decoder with a sink that:

```kotlin
ResolvedStateTraceSink { trace ->
    runCatching { Log.i(STATE_LOG_TAG, performanceGson.toJson(trace)) }
    performanceRecorder.stateChanged(trace)
}
```

The direct `DualDexState` call occurs before the listener. The existing `DualDexPerf` sink skips `STATE_CHANGED` to prevent duplicate logcat output, while `AndroidPerformanceLog` retains it in the same bounded app-private stream.

- [ ] **Step 4: Re-run the focused performance and decoder tests**

Run the Task 2 Step 2 command, followed by the Task 1 Step 3 command.

Expected: both focused suites pass and sampler-count assertions prove state tracing is lightweight.

- [ ] **Step 5: Commit production trace wiring**

```powershell
git add -- app/src/main/java/com/darkaxt/dualdex/performance/PerformanceModels.kt app/src/main/java/com/darkaxt/dualdex/performance/PerformanceRecorder.kt app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt app/src/test/java/com/darkaxt/dualdex/performance/PerformanceRecorderTest.kt app/src/test/java/com/darkaxt/dualdex/performance/AndroidPerformanceLogTest.kt
git commit -m "feat: retain bounded pre-ui state diagnostics"
```

### Task 3: Dock Pokédex search and count at the bottom

**Files:**
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write a failing structural layout test**

Render the default Pokédex and assert the screen children resolve in this order:

```ts
expect(Array.from(container.querySelector('.pokedex-screen')!.children).map(node =>
  node.classList.contains('app-header') ? 'header' : node.className,
)).toEqual(['header', 'browse-tools', 'species-list', 'pokedex-search-dock']);
```

Retain the current Team-tab assertions and additionally assert `.pokedex-search-dock` is absent for Team.

- [ ] **Step 2: Run the focused web test and confirm failure**

```powershell
npm --prefix companion-web test -- PokedexBrowse.test.tsx
```

Expected: the new order/dock test fails because search currently precedes the filter strip inside `.browse-tools`.

- [ ] **Step 3: Move controls without changing behavior**

Keep only `.filter-strip` inside `.browse-tools`. Move the existing conditional `.pokedex-search-row` after `.species-list`, wrapped in `.pokedex-search-dock`. Do not change search state, counts, filters, labels, or action payloads.

Set:

```css
.pokedex-screen { grid-template-rows: auto auto minmax(0, 1fr) auto; }
.pokedex-search-dock {
  padding: 10px 12px;
  background: var(--paper-deep);
  border-top: 1px solid var(--line);
}
```

Keep the existing responsive search-row and result-count rules.

- [ ] **Step 4: Re-run focused and full web tests/build**

```powershell
npm --prefix companion-web test -- PokedexBrowse.test.tsx
npm --prefix companion-web test
npm --prefix companion-web run build
```

Expected: all tests pass, the build succeeds, no more than 60 virtualized species rows mount, and Team has no search dock.

- [ ] **Step 5: Commit the Pokédex layout change**

```powershell
git add -- companion-web/src/pages/PokedexBrowse.tsx companion-web/src/pages/PokedexBrowse.test.tsx companion-web/src/styles.css
git commit -m "ui: dock pokedex search below results"
```

### Task 4: Cross-check the specification and publish the next prerelease

**Files:**
- Create: `docs/reports/2026-08-25-rc65-state-trace-validation.md`
- Modify: release notes/version metadata selected by the repository release workflow

- [ ] **Step 1: Run the affected Android regression gate**

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Expected: all tasks succeed. No device installation, game launch, or UI operation is part of this automated gate.

- [ ] **Step 2: Cross-check every specification requirement**

Create a table mapping ST-01 through ST-10 and PX-01 through PX-06 to exact tests/code evidence. A missing implementation is a blocker; device reproduction is recorded as deferred user validation, not claimed as a pass.

- [ ] **Step 3: Verify privacy and scope**

Run:

```powershell
rg -n "DualDexState|STATE_CHANGED|pokedex-search-dock" app companion-web
git diff fork/master...HEAD --check
git status --short
```

Expected: state diagnostics appear only in app-private/logcat paths, normal page strings contain no diagnostic output, the diff is clean, and only intended files are changed.

- [ ] **Step 4: Commit validation evidence**

```powershell
git add -- docs/reports/2026-08-25-rc65-state-trace-validation.md
git commit -m "docs: validate rc65 state trace and pokedex layout"
```

- [ ] **Step 5: Reconcile current upstream before publication**

Fetch `fork`, confirm no concurrent upstream commit is lost, and rebase/merge only if needed. Re-run the focused decoder, performance, and Pokédex tests after reconciliation.

- [ ] **Step 6: Publish and independently verify `v1.1.0-rc.65`**

Use the protected release workflow so tag, APK filename, package, numeric `versionName=1.1.0-rc.65`, and `versionCode=1010065` agree. Verify the anonymously downloaded APK SHA-256, package identity, version, signer certificate, tag commit, and successful workflow provenance. Do not install or operate the app unless the user explicitly requests it.

- [ ] **Step 7: Commit publication evidence and clean exact temporary build roots**

Record the release URL, workflow run, commit, APK SHA-256, package/version, and signer certificate. Remove only the exact task-owned temporary Gradle/test/release directories after verifying they are not active; preserve all unrelated worktrees and user changes.
