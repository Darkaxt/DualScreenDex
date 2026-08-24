# Runtime Performance Observability and Churn Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add bounded load/runtime profiling, isolate unified-state section changes, remove coalesced live-memory allocation churn, and close the Android performance evidence gap without exposing diagnostics on normal pages.

**Architecture:** An application-owned `PerformanceRecorder` emits structured snapshots to logcat and a bounded app-private rolling store, with minute sampling driven by the existing RetroArch heartbeat. `UnifiedGameStateDecoder` publishes explicit changed sections, `CompanionGateway` rejects reducer no-ops, and core-memory sessions reuse one scratch buffer and transfer completed buffers once. Every stage ends with a complete RP-01 through RP-19 specification matrix.

**Tech Stack:** Kotlin/JVM, Android Debug/Process APIs, Gson NDJSON, Android WebView and loopback HTTP, Preact/TypeScript, JUnit, Vitest, Gradle, ADB read-only process inspection.

---

## File map

| Responsibility | Files |
| --- | --- |
| Profiler models/recording | Create `app/src/main/java/com/darkaxt/dualdex/performance/PerformanceModels.kt`, `PerformanceRecorder.kt` |
| Android metrics and bounded log | Create `app/src/main/java/com/darkaxt/dualdex/performance/AndroidPerformanceSampler.kt`, `AndroidPerformanceLog.kt` |
| Profiler wiring and export | Modify `DualDexApplication.kt`, `MainActivity.kt`, `web/NativeSetupRoute.kt`, `web/ProductionCompanionRuntime.kt`, `setup/RetroArchSetupCoordinator.kt`, `companion-web/src/pages/SettingsPage.tsx` |
| Profiler tests | Create `app/src/test/java/com/darkaxt/dualdex/performance/PerformanceRecorderTest.kt`, `AndroidPerformanceLogTest.kt`; modify Settings and route tests |
| Unified section changes | Modify `live/TransientGameStateSource.kt`, `live/UnifiedGameStateDecoder.kt`, `web/ProductionCompanionRuntime.kt`, `companion-core/.../CompanionGateway.kt` and their tests |
| Memory transport ownership | Modify `retroarch-session/.../CoreMemoryReader.kt` and `CoreMemoryReaderTest.kt` |
| Section fingerprints/reuse | Create `battle-memory/.../Gen3LiveSectionFingerprints.kt`; modify `UnifiedGameStateDecoder.kt` and tests |
| Stage evidence | Create `docs/superpowers/plans/2026-08-24-runtime-performance-stage-{1,2,3,4}-validation.md` |

## Stage 1 — Runtime observability

### Task 1: Define and persist structured profiler events

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/performance/PerformanceModels.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/performance/PerformanceRecorder.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/performance/AndroidPerformanceLog.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/performance/BoundedPerformanceWorkDispatcher.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/performance/PerformanceRecorderTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/performance/AndroidPerformanceLogTest.kt`

- [x] **Step 1: Write RED recorder tests**

Test that a deterministic recorder emits one load start, closes stages on transitions, emits one runtime event for each newly observed minute bucket without catch-up, truncates a ROM identity to a twelve-character SHA prefix, and excludes forbidden keys/values such as `path`, `player`, `save`, and raw bytes.

- [x] **Step 2: Run the focused tests and confirm RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.performance.PerformanceRecorderTest" --tests "com.darkaxt.dualdex.performance.AndroidPerformanceLogTest"
```

Expected: compilation/test failure because the performance package does not exist.

- [x] **Step 3: Implement the minimal pure recorder and two-segment log**

Use these stable event kinds:

```kotlin
enum class PerformanceEventKind {
    LOAD_STARTED, CACHE_DECISION, STAGE_FINISHED, CATALOG_READY,
    WAITING_FOR_GAME_ACCESS, GAME_ACCESS_READY, LOAD_FAILED, RUNTIME_MINUTE,
}
```

`PerformanceRecorder` accepts injected clocks, sampler, bounded work dispatcher, and sink. `runtimeHeartbeat()` compares monotonic minute buckets and emits only the current bucket. Production sampling and persistence run through the bounded dispatcher, outside runtime-state locks. `AndroidPerformanceLog` writes UTF-8 NDJSON into `performance.ndjson` and rotates to `performance.previous.ndjson` at 512 KiB, keeping total storage at or below 1 MiB; failed rotation drops the incoming record.

- [x] **Step 4: Run focused tests and confirm GREEN**

Run the Step 2 command. Expected: all selected tests pass.

### Task 2: Add Android metric sampling and load/runtime hooks

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/performance/AndroidPerformanceSampler.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinatorTest.kt`

- [x] **Step 1: Write RED integration tests**

Add a fake `PerformanceRecorder` sink and assert:

```text
cold load: LOAD_STARTED -> CACHE_DECISION(MISS) -> parser stages -> CATALOG_READY
cached load: LOAD_STARTED -> CACHE_DECISION(HIT) -> CATALOG_READY, with zero parser stages
heartbeat: at most one RUNTIME_MINUTE for each current monotonic minute
game access: WAITING_FOR_GAME_ACCESS once, then GAME_ACCESS_READY once
failure: LOAD_FAILED closes the active load session
```

- [x] **Step 2: Run tests and confirm RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest" --tests "com.darkaxt.dualdex.setup.RetroArchSetupCoordinatorTest"
```

Expected: assertions fail because no profiler hooks exist.

- [x] **Step 3: Implement Android sampler and hooks**

`AndroidPerformanceSampler` records `Runtime` heap, `Debug.getNativeHeapAllocatedSize()`, `Debug.MemoryInfo.totalPss`, `Process.getElapsedCpuTime()`, ART GC runtime stats when parseable, and the current thread count. Runtime component providers add map-cache statistics and later-stage counters without holding the runtime lock during file I/O.

Wire parser work transitions, cache decisions, catalog ready/failure, game-access transitions, and the existing two-second setup heartbeat. Profiler exceptions are caught inside the recorder.

- [x] **Step 4: Run focused tests and confirm GREEN**

Run the Step 2 command. Expected: selected tests pass.

### Task 3: Add Debug-only profiler export

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/NativeSetupRoute.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/NativeSetupRouteTest.kt`
- Test: `companion-web/src/pages/SettingsPage.production.test.tsx`

- [x] **Step 1: Write RED route and UI-boundary tests**

Assert `dualdex://performance/export` resolves only to the native export action, Settings exposes `EXPORT PERFORMANCE LOG` inside `.mapper-setting`, and no normal page contains profiler terminology.

- [x] **Step 2: Run tests and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.web.NativeSetupRouteTest"
Push-Location companion-web; npm test -- --run src/pages/SettingsPage.production.test.tsx; Pop-Location
```

- [x] **Step 3: Implement export**

Add a native create-document launcher for `dualdex-performance.ndjson`, export the bounded store, and place one button beside the existing capability/memory debug actions. Do not add profiler state to the public bootstrap/state API.

- [x] **Step 4: Run route, Settings, and production-shell tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.web.NativeSetupRouteTest"
Push-Location companion-web; npm test -- --run src/pages/SettingsPage.production.test.tsx src/App.production.test.tsx; Pop-Location
```

Expected: all selected tests pass and diagnostics remain confined to Debug.

### Task 4: Stage 1 specification cross-check

**Files:**
- Create: `docs/superpowers/plans/2026-08-24-runtime-performance-stage-1-validation.md`

- [x] **Step 1: Run Stage 1 gate**

```powershell
.\gradlew.bat :app:testDebugUnitTest
Push-Location companion-web; npm test -- --run; npm run build; Pop-Location
```

- [x] **Step 2: Write the RP-01 through RP-19 matrix**

RP-01 through RP-08 must be `PASS` or the stage stops with `BLOCKER`. RP-09 through RP-18 are `DEFERRED` to their exact stage. RP-19 passes only if every requirement appears once and no item is unassigned.

- [x] **Step 3: Commit Stage 1**

```powershell
git add app companion-web docs/superpowers/specs/2026-08-24-runtime-performance-observability-and-churn-design.md docs/superpowers/plans/2026-08-24-runtime-performance-observability-and-churn.md docs/superpowers/plans/2026-08-24-runtime-performance-stage-1-validation.md
git commit -m "perf: add bounded runtime profiling"
```

## Stage 2 — Unified-state change isolation

### Task 5: Publish exact changed sections

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/TransientGameStateSource.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt`

- [x] **Step 1: Write RED section-delta tests**

Define `ResolvedGameSection` values `RECOVERY`, `PLAYER`, `PARTY`, `OVERWORLD`, and `BATTLE`. Test initial publication includes every available section, sample-ID-only changes publish nothing, seconds-only clock changes include only `OVERWORLD`, and a Pokédex mutation includes only `PLAYER`.

- [x] **Step 2: Run and confirm RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.live.UnifiedGameStateDecoderTest"
```

- [x] **Step 3: Implement `ResolvedGameStateUpdate`**

Listeners receive the complete snapshot plus the exact changed-section set computed against the previous resolved snapshot with sample IDs excluded. Null/session transitions publish every section necessary to clear state.

- [x] **Step 4: Run and confirm GREEN**

Run the Step 2 command. Expected: all selected tests pass.

### Task 6: Suppress no-op and cross-section gateway work

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt`

- [x] **Step 1: Write RED no-op and routing tests**

Assert a reducer no-op preserves the exact snapshot object/version and emits no listener callback. Assert a seconds-only update does not run player/party/battle consumers, does not advance the gateway version after readiness is already true, and an unchanged active battle does not emit `BattleUpdated`.

- [x] **Step 2: Run and confirm RED**

```powershell
.\gradlew.bat :companion-core:test --tests "com.enrpau.dualscreendex.companion.CompanionGatewayTest" :app:testDebugUnitTest --tests "com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest"
```

- [x] **Step 3: Implement minimal routing and equality guards**

Route each update only to its changed consumers. Compare incoming Trainer Card and seen/caught additions exactly. Compare projected battle state before dispatch. In `CompanionGateway.dispatch`, return the current snapshot without increment/listeners when `reduce(before, action) == before`.

- [x] **Step 4: Run and confirm GREEN**

Run the Step 2 command. Expected: all selected tests pass.

### Task 7: Prove seconds do not reach the web state

**Files:**
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/AndroidLoopbackServerTest.kt`
- Modify: `companion-web/src/App.production.test.tsx`

- [x] **Step 1: Add the regression controls**

After readiness, feed a seconds-only live update and assert the gateway version is unchanged, `/api/state?sinceVersion=` returns 204/zero bytes, and the client performs zero JSON parse/update calls.

- [x] **Step 2: Run the focused server/web tests**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.darkaxt.dualdex.web.AndroidLoopbackServerTest"
Push-Location companion-web; npm test -- --run src/App.production.test.tsx; Pop-Location
```

Expected: all selected tests pass.

### Task 8: Stage 2 specification cross-check

**Files:**
- Create: `docs/superpowers/plans/2026-08-24-runtime-performance-stage-2-validation.md`

- [x] **Step 1: Run Stage 2 gate**

```powershell
.\gradlew.bat :companion-core:test :app:testDebugUnitTest
Push-Location companion-web; npm test -- --run; Pop-Location
```

- [x] **Step 2: Write the complete matrix**

RP-01 through RP-12 must be `PASS`. RP-13 through RP-18 remain explicitly deferred. Any failure in the Stage 1 controls is a blocker.

- [x] **Step 3: Commit Stage 2**

```powershell
git add app companion-core companion-web docs/superpowers/plans/2026-08-24-runtime-performance-stage-2-validation.md
git commit -m "perf: isolate unified state changes"
```

## Stage 3 — Live-memory allocation and decode reuse

### Task 9: Reuse packet storage and transfer completed buffers

**Files:**
- Modify: `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReader.kt`
- Modify: `retroarch-session/src/test/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReaderTest.kt`

- [x] **Step 1: Write RED allocation/ownership tests**

Inject a scratch-buffer construction counter, complete a multi-packet overlapping read, and assert one scratch construction, exact scattered bytes, and the same region-array identities in the terminal result across repeated terminal heartbeats.

- [x] **Step 2: Run and confirm RED**

```powershell
.\gradlew.bat :retroarch-session:test --tests "com.darkaxt.dualdex.retroarch.CoreMemoryReaderTest"
```

- [x] **Step 3: Implement one scratch buffer and ownership transfer**

Allocate `ByteArray(maximumChunkBytes)` once per session, parse each response into it, scatter only the request length, and return the owned `buffers.toMap()` without cloning region arrays. Terminal sessions never send, parse, scatter, or mutate again.

- [x] **Step 4: Run and confirm GREEN**

Run the Step 2 command. Expected: all selected tests pass.

### Task 10: Cache translated live sections without retaining raw memory

**Files:**
- Create: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveSectionFingerprints.kt`
- Create: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen3LiveSectionFingerprintsTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoderTest.kt`

- [x] **Step 1: Write RED fingerprint and reuse tests**

For each Trainer/Pokédex, Party, clock/location, Bag/event-flags input group, change one relevant byte and assert only that fingerprint changes. Feed identical samples with new sample IDs and assert decoder counters show reuse; replace the runtime context and assert every cache is rebuilt. Assert no prior `ByteArray` region is retained by the cache model.

- [x] **Step 2: Run and confirm RED**

```powershell
.\gradlew.bat :battle-memory:test --tests "com.darkaxt.dualdex.battle.Gen3LiveSectionFingerprintsTest" :app:testDebugUnitTest --tests "com.darkaxt.dualdex.live.UnifiedGameStateDecoderTest"
```

- [x] **Step 3: Implement compact fingerprints and translated-value caches**

Use a reusable SHA-256 digest over ABI-defined slices and store only digest bytes plus translated values. Include context generation in cache identity. Clear caches on `beginSession`, `suspendLive`, and `endSession`. Expose cumulative decode/reuse counters to the profiler without logging values.

- [x] **Step 4: Run and confirm GREEN**

Run the Step 2 command. Expected: all selected tests pass.

### Task 11: Stage 3 specification cross-check

**Files:**
- Create: `docs/superpowers/plans/2026-08-24-runtime-performance-stage-3-validation.md`

- [x] **Step 1: Run Stage 3 gate**

```powershell
.\gradlew.bat :retroarch-session:test :battle-memory:test :app:testDebugUnitTest
```

- [x] **Step 2: Record exact before/after counters and matrix**

RP-01 through RP-15 must be `PASS`; RP-16 through RP-18 remain explicitly deferred to the Stage 4 bounds, compatibility, and device-evidence gates. RP-19 passes only with every row present and no blocker.

- [x] **Step 3: Commit Stage 3**

```powershell
git add retroarch-session battle-memory app docs/superpowers/plans/2026-08-24-runtime-performance-stage-3-validation.md
git commit -m "perf: reuse live memory decoding buffers"
```

## Stage 4 — Complete regression and Android evidence

### Task 12: Run automated compatibility and performance gates

**Files:**
- Create: `docs/superpowers/plans/2026-08-24-runtime-performance-stage-4-validation.md`

- [ ] **Step 1: Run web and complete Gradle gates**

```powershell
Push-Location companion-web; npm test -- --run; npm run build; Pop-Location
.\gradlew.bat verifySecureBuildDependencies test :app:lintDebug :app:assembleRelease -PdualdexVersionName=1.1.0-rc.55 -PdualdexVersionCode=1010055
node --test tools/release/*.test.mjs
```

- [ ] **Step 2: Record exact automated evidence**

Record test counts, failures, lint errors, artifact path/size/hash, original R1-R14 bounds, and official Gen I-III/Modern Emerald/Odyssey/Unbound control results. Do not infer device behavior from host Gradle residency.

### Task 13: Capture authorized Android runtime evidence

**Files:**
- Modify: `docs/superpowers/plans/2026-08-24-runtime-performance-stage-4-validation.md`

- [ ] **Step 1: Capture app-private profiler records during reference runs**

Use cold parse and cached reopen for Red, Crystal, Emerald, Modern Emerald, Odyssey, and Unbound. Use long sessions for official Emerald, Odyssey, and Unbound. Export the bounded profiler log from Debug or collect `DualDexPerf` logcat records.

- [ ] **Step 2: Capture renderer evidence read-only**

Use the connected device serial discovered by `adb devices`, then record:

```powershell
adb -s <serial> shell dumpsys meminfo com.darkaxt.dualdex
adb -s <serial> shell dumpsys meminfo | Select-String -Pattern 'webview|sandboxed_process|dualdex'
adb -s <serial> shell ps -A -o PID,PPID,NAME,ARGS | Select-String -Pattern 'dualdex|webview'
```

These are read-only measurements. Do not install, launch, navigate, or send emulator/game commands solely to collect them without separate authorization.

- [ ] **Step 3: Close RP-18 and the original R15**

Record Java/native/PSS, GC, CPU, owned-WebView count, renderer PSS, stage durations, cache decisions, and runtime minute samples by ROM SHA/phase. Any missing named metric is a blocker, not a pass.

### Task 14: Final specification cross-check, release, and push

**Files:**
- Modify: `docs/superpowers/plans/2026-08-24-runtime-performance-stage-4-validation.md`
- Create: `release/RELEASE_NOTES_1.1.0-rc.55.md`

- [ ] **Step 1: Re-read the specification and close the matrix**

Ensure RP-01 through RP-19 appear exactly once and are all `PASS`. Reconcile the original performance design so R15 reflects the new Android evidence. A missing feature is either added and verified or remains a blocker; nothing is silently omitted.

- [ ] **Step 2: Verify release identity and provenance**

Use the repository release tooling to sign/package RC55, then independently verify numeric `versionName`, `versionCode`, package name, signer certificate, SHA-256, filename, and tag alignment.

- [ ] **Step 3: Commit, push, tag, and publish only after the clean gate**

```powershell
git add docs release
git commit -m "docs: validate runtime performance hardening"
git push fork HEAD:master
```

Create and push `v1.1.0-rc.55`, publish the verified signed APK and release notes, redownload the artifact, and repeat checksum/signer/package verification. If device evidence is unavailable or any matrix row remains a blocker, stop before publishing and report the exact blocker.

## Plan self-review

- RP-01 through RP-08 map to Tasks 1-4.
- RP-09 through RP-12 map to Tasks 5-8.
- RP-13 through RP-15 map to Tasks 9-11.
- RP-16 through RP-18 map to Tasks 12-13.
- RP-19 is enforced by Tasks 4, 8, 11, and 14.
- No production behavior is implemented without a named RED test.
- No normal UI receives profiler data.
- No cancellation timeout, symlink, filename/SHA parser selection, raw-memory logging, or gameplay write is introduced.
