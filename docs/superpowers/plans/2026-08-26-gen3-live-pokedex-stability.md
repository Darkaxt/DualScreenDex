# Gen III Live Pokédex Stability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent a one-poll false Gen III Pokédex layout from reaching the UI while keeping normal live changes immediate.

**Architecture:** Preserve the candidate `ownedOffset` already returned by `Gen3PokedexCodec`, then pass each live candidate through a session-scoped stabilizer before the unified snapshot is published. Only suspicious first-layout candidates need two identical polls; confirmed layouts are pinned for the session.

**Tech Stack:** Kotlin/JVM, JUnit 4, Gradle, Android release workflow.

---

### Task 1: Reproduce candidate instability before implementation

**Files:**
- Create: `app/src/test/java/com/darkaxt/dualdex/live/Gen3LivePokedexStabilizerTest.kt`

- [ ] **Step 1: Write the failing stabilizer tests**

Define wished-for `Gen3LivePokedexStabilizer.accept(candidate, party)` behavior for:

```kotlin
assertEquals(0, stabilizer.accept(empty, party(0)).caughtCount())
assertEquals(0, stabilizer.accept(candidate(offset = 0x80, caught = 48, seen = 48), party(1)).caughtCount())
assertEquals(1, stabilizer.accept(candidate(offset = 0x28, caught = 1, seen = 1), party(1)).caughtCount())
```

Also assert two identical large candidates publish on the second poll, a confirmed `0x28` offset rejects `0x2C`, same-offset seen `1 -> 2` is immediate, a double-battle `1 -> 3` is immediate, and `reset()` permits a new session offset.

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
$env:JAVA_HOME='C:\Users\darka\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2'
$env:GRADLE_USER_HOME='D:\Temp\dualdex-rc66-gradle-home'
$env:TEMP='D:\Temp\dualdex-rc66-test-temp'
$env:TMP=$env:TEMP
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.darkaxt.dualdex.live.Gen3LivePokedexStabilizerTest"
```

Expected: compilation fails because `Gen3LivePokedexStabilizer` and the internal live offset do not exist.

### Task 2: Preserve the candidate offset and implement stabilization

**Files:**
- Modify: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/LiveMemoryModels.kt`
- Modify: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3LiveMemoryCodecs.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/live/Gen3LivePokedexStabilizer.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/live/UnifiedGameStateDecoder.kt`

- [ ] **Step 1: Retain the codec-selected offset**

Add `ownedFlagOffset: Int? = null` to `LivePokedexState` and populate it from `Gen3PokedexSnapshot.ownedOffset` in `decodePlayerOverview`. The default preserves all existing constructors.

- [ ] **Step 2: Implement the minimal state machine**

Create a stabilizer with `confirmedOffset`, `pendingCandidate`, and `lastAccepted`. Its decision order is:

```kotlin
candidate offset unavailable and confirmed -> retain last accepted
confirmed offset matches -> accept immediately
confirmed offset conflicts -> retain last accepted
no previous accepted value -> accept and confirm
plausible addition -> accept and confirm
same suspicious candidate twice -> accept and confirm
first or changed suspicious candidate -> retain last accepted
```

A suspicious candidate adds more caught values than `maxOf(1, partyCount)` or more seen values than `maxOf(2, partyCount + 1)` compared with the last accepted live sets.

- [ ] **Step 3: Integrate before unified publication**

In `acceptGen3LiveSample`, replace `player.pokedex` with `gen3PokedexStabilizer.accept(player.pokedex, party)` before constructing `LiveGameSnapshot`. Call `reset()` from `beginSession` and `endSession`, but not `suspendLive`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the Task 1 command again. Expected: every stability test passes.

- [ ] **Step 5: Run affected regressions**

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests "com.darkaxt.dualdex.live.Gen3LivePokedexStabilizerTest" --tests "com.darkaxt.dualdex.live.UnifiedGameStateDecoderTest" --tests "com.darkaxt.dualdex.battle.Gen3LiveMemoryCodecsTest"
```

Expected: all selected tests pass.

- [ ] **Step 6: Commit implementation**

```powershell
git add app/src battle-memory/src
git commit -m "fix: stabilize live gen3 pokedex layout"
```

### Task 3: Validate and publish RC66

**Files:**
- Create: `docs/reports/2026-08-26-rc66-live-pokedex-stability-validation.md`
- Create: `release/RELEASE_NOTES_1.1.0-rc.66.md`
- Modify: `release/v1-ready.json`

- [ ] **Step 1: Cross-check PS-01 through PS-08**

Record exact code/test evidence and retain physical reproduction as user validation rather than claiming it automatically.

- [ ] **Step 2: Run release gates**

Run the complete web suite, production web build, release-policy tests, Android lint, and RC66 release assembly with `versionName=1.1.0-rc.66` and `versionCode=1010066`.

- [ ] **Step 3: Reconcile upstream and prepare release metadata**

Fetch `fork/master`, preserve any concurrent commit, add RC66 readiness flags and release notes, then re-run focused tests after reconciliation.

- [ ] **Step 4: Publish and independently verify**

Push the final source to `fork/master`, create annotated tag `v1.1.0-rc.66`, and dispatch the protected release workflow from that tag. Verify the anonymously downloaded APK package, numeric version, SHA-256, one-signature certificate, tag commit, and workflow provenance. Do not install or operate the APK.
