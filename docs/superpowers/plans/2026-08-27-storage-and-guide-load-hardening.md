# Storage and Guide Load Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep All Files Access truthful through indexing and turn guide memory failures into recoverable, diagnosable, explicitly retryable outcomes.

**Architecture:** `RetroArchSetupCoordinator` keeps permission and ROM-index state separate and latches a failed activation by source. `ProductionCompanionRuntime` sanitizes recoverable failures while recording the original failure class and stage in Debug performance events. Existing catalog publication remains atomic.

**Tech Stack:** Kotlin/JVM, Android, Preact/TypeScript, JUnit 4, Vitest, Gradle, GitHub protected release workflow.

---

### Task 1: Separate storage permission from indexing presentation

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/storage/StorageSetupStatusPolicy.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/storage/StorageSetupStatusPolicyTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `companion-web/src/pages/SetupPage.tsx`
- Modify: `companion-web/src/pages/SetupPage.test.tsx`

- [x] **Step 1: Write RED Kotlin policy tests.**

```kotlin
assertEquals(StorageSetupStatus("GRANTED", "INDEXING"), StorageSetupStatusPolicy.indexing(true))
assertEquals(StorageSetupStatus("GRANTED", "FAILED"), StorageSetupStatusPolicy.failed(true, false, false))
assertEquals(StorageSetupStatus("GRANTED", "GRANTED"), StorageSetupStatusPolicy.failed(true, true, false))
assertEquals(StorageSetupStatus("MISSING", "GRANTED"), StorageSetupStatusPolicy.available(false, false, true))
```

- [x] **Step 2: Run `./gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.storage.StorageSetupStatusPolicyTest --no-daemon --console=plain`; expect compilation/test failure because the policy does not exist.**
- [x] **Step 3: Implement the immutable status policy and wire initial, indexing, success, and failure coordinator transitions through it.**
- [x] **Step 4: Write RED Preact tests asserting the grant link is absent for `GRANTED`, indexing says `Finding your games…`, failure retains `Ready` permission, and `MISSING` alone exposes the grant link.**
- [x] **Step 5: Run `npm test -- SetupPage.test.tsx`; expect the granted-state link assertion to fail.**
- [x] **Step 6: Render the grant action conditionally and add explicit `INDEXING`/`FAILED` player-facing ROM-index messages without exposing raw state names.**
- [x] **Step 7: Run both focused suites GREEN and commit with `fix(setup): separate storage permission from indexing`.**

### Task 2: Contain and diagnose guide-load memory failures

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/web/GuideLoadFailure.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/web/GuideLoadFailureTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [x] **Step 1: Write RED policy tests proving memory failures use `There was not enough free memory to open this game guide. Close other apps and try again.` and ordinary failures use `This game guide could not be opened. You can try again.`**
- [x] **Step 2: Run the focused JUnit classes; expect failure because `GuideLoadFailure` does not exist.**
- [x] **Step 3: Implement `GuideLoadFailure.from(Throwable)` while retaining the original cause and never copying its message into player-facing text.**
- [x] **Step 4: Add a RED runtime test whose injected parser throws `OutOfMemoryError`; require that `load()` does not throw, the callback receives sanitized failure, catalog readiness remains false, and Debug events contain the final stage plus `OutOfMemoryError`.**
- [x] **Step 5: Run the runtime test; expect `OutOfMemoryError` to escape.**
- [x] **Step 6: Catch `Exception` and `OutOfMemoryError` separately, record the original failure, publish a failed transition, and notify with the sanitized failure. Do not catch other `Error` types.**
- [x] **Step 7: Run the focused runtime and performance tests GREEN and commit with `fix(runtime): contain guide memory failures`.**

### Task 3: Stop automatic crash loops and add explicit retry

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/NativeSetupRoute.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/NativeSetupRouteTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/overlay/FloatingCompanionService.kt`
- Modify: `companion-web/src/pages/SetupPage.tsx`
- Modify: `companion-web/src/pages/SetupPage.test.tsx`

- [x] **Step 1: Add a RED native-route assertion for exact `dualdex://guide/retry` and malformed-route rejection.**
- [x] **Step 2: Add a RED Setup-page test requiring `RETRY OPENING GAME GUIDE` only while `resolution == FAILED`.**
- [x] **Step 3: Run both focused tests and confirm the route/action are absent.**
- [x] **Step 4: Add `RETRY_GUIDE`, dispatch it from Activity/overlay, and implement `retryGuideLoad()` by clearing the failed-source latch before retrying the current resolved entry.**
- [x] **Step 5: In `activate`, reject the latched exact source, sanitize both ordinary and memory failures, and clear the activation owner through the single `GuideActivationGate` completion path. Clear the latch on successful indexing, successful activation, source replacement, and explicit retry.**
- [x] **Step 6: Run native-route, Setup-page, runtime, storage, and activation-gate tests GREEN and commit with `fix(setup): make failed guide loads explicitly retryable`.**

### Task 4: Audit, integrate, and publish RC71

**Files:**
- Create: `docs/reports/2026-08-27-storage-and-guide-load-hardening.md`
- Modify: release readiness/workflow metadata discovered from the RC70 release commit.

- [x] **Step 1: Cross-check SG-01 through SG-06 and GL-01 through GL-07; record every requirement once with evidence and zero blocker/error rows.**
- [x] **Step 2: Run `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --no-daemon --console=plain`.**
- [x] **Step 3: Run `npm test` and `npm run build` from `companion-web`.**
- [x] **Step 4: Run `node --test tools/release/release-workflow.test.mjs tools/release/release-metadata.test.mjs`.**
- [ ] **Step 5: Commit the audit, fetch tags, derive the next unused numeric RC, update release metadata, and run the release checks again.**
- [ ] **Step 6: Push the source commits and tag through the protected signing workflow; verify public APK version, checksum, signer, provenance, and exact tag commit. Do not install or launch it.**
- [ ] **Step 7: Merge the hardening commits into `feature/passive-insights-stage4-specimens`, resolve only direct overlaps, rerun affected focused tests, and resume Stage 4 Task 4.3.**
