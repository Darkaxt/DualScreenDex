# DualDex Live Battle Responsiveness and Rarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **Supersession note:** Task 6 is retired. Live firmware classified the controller-focus setting as secure, so the setting, provider integration, persisted field, status, and privileged permission were removed before RC13.

**Goal:** Deliver configurable fast battle discovery, stable in-battle navigation, and area-relative half-star rarity in a public signed prerelease installed without any other device interaction.

**Architecture:** Keep platform-independent policy in companion-core and memory scheduling in the Android battle coordinator. Publish structured rarity data to a presentation-only web component. Persist polling as a device-global setting and retain the existing one-request-at-a-time UDP transport.

**Tech Stack:** Kotlin/JVM, AndroidX Activity Result APIs, JUnit 4, Preact/TypeScript, Vitest, Gradle, GitHub Actions, GitHub CLI, ADB.

---

## File map

- `companion-core/.../model/AppModels.kt`: settings, battle lifecycle actions, and internal battle-return state.
- `companion-core/.../owned/PreferredIndividualSelector.kt`: existing IV/DV normalization and tier boundaries.
- `companion-core/.../battle/RarityEvaluator.kt`: new weighted area-level and star policy.
- `companion-core/.../api/ApiModels.kt`: structured rarity projection.
- `companion-core/.../CompanionGateway.kt`: edge-triggered battle navigation.
- `app/.../settings/SettingsRepository.kt`: schema-compatible global polling persistence.
- `app/.../battle/BattleMemoryCoordinator.kt`: adaptive scheduling and cached-layout retention.
- `app/.../setup/RetroArchSetupCoordinator.kt`: inject live polling setting.
- `app/.../web/ProductionCompanionRuntime.kt`: parse polling setting and dispatch start/update.
- `companion-web/src/models.ts`: structured API types.
- `companion-web/src/pages/BattlePage.tsx`: star strip and combined title.
- `companion-web/src/pages/SettingsPage.tsx`: device-global 1–20 ms control.
- `companion-web/src/styles.css`: whole/half-star and settings presentation.
- `release/RELEASE_NOTES_1.0.0.md`, `README.md`, `release/v1-ready.json`: RC13 user-facing metadata and verified test totals.

### Task 1: Persist and expose the battle polling interval

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/pages/SettingsPage.production.test.tsx`

- [ ] **Step 1: Write failing persistence and HTTP-setting tests**

Add assertions that `battlePollingIntervalMs` defaults to 5, round-trips globally, is shared by two ROM profiles, clamps values below 1 and above 20, and is parsed by `ProductionCompanionRuntime.updateSettings`.

```kotlin
val fast = CompanionSettings(battlePollingIntervalMs = 1)
repository.writeForRom(romA, fast)
assertEquals(1, repository.readForRom(romB).battlePollingIntervalMs)
assertEquals(1, SettingsRepository({ "{\"battlePollingIntervalMs\":0}" }, {}).read().battlePollingIntervalMs)
assertEquals(20, SettingsRepository({ "{\"battlePollingIntervalMs\":99}" }, {}).read().battlePollingIntervalMs)
```

- [ ] **Step 2: Run the focused tests and observe RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.settings.SettingsRepositoryTest --rerun-tasks
```

Expected: compilation fails because `CompanionSettings.battlePollingIntervalMs` does not exist.

- [ ] **Step 3: Implement the global setting and web control**

Add the model field and persistence members:

```kotlin
data class CompanionSettings(
    // existing fields
    val battlePollingIntervalMs: Int = 5,
)

private fun sanitize(settings: CompanionSettings) = settings.copy(
    // existing sanitizers
    battlePollingIntervalMs = settings.battlePollingIntervalMs.coerceIn(1, 20),
)
```

Include the value only in complete/global stored settings, copy it into `globals` in `writeForRom`, and omit it from ROM differences. Parse `battlePollingIntervalMs` from the SETTINGS action with `toIntOrNull()?.coerceIn(1, 20)`.

Expose a numeric range in Settings:

```tsx
<label class="range-setting">
  <span>BATTLE DISCOVERY POLLING <b>{settings.battlePollingIntervalMs} ms</b></span>
  <input aria-label="Battle discovery polling" type="range" min="1" max="20" step="1"
    value={settings.battlePollingIntervalMs}
    onInput={event => update({ battlePollingIntervalMs: Number(event.currentTarget.value) })} />
</label>
```

- [ ] **Step 4: Run settings and web tests GREEN**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.settings.SettingsRepositoryTest --rerun-tasks
Push-Location companion-web
npm test -- --run src/pages/SettingsPage.production.test.tsx
Pop-Location
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt companion-web/src/models.ts companion-web/src/pages/SettingsPage.tsx companion-web/src/pages/SettingsPage.production.test.tsx
git commit -m "feat: add configurable battle discovery polling"
```

### Task 2: Add adaptive scheduling and retain validated layouts

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`

- [ ] **Step 1: Write failing scheduler and cache tests**

Introduce a fake scheduler that captures requested delays without sleeping. Assert discovery schedules the live setting, cached mode schedules 20 ms, changing the provider affects the next discovery heartbeat, only one read is sent per reply cycle, zero battler count retains the layout, and ROM identity change clears it.

```kotlin
val delay = AtomicInteger(5)
val scheduler = RecordingBattleHeartbeatScheduler()
val coordinator = BattleMemoryCoordinator(
    catalogProvider = { context() },
    publisher = updates::add,
    pollingIntervalProvider = delay::get,
    scheduler = scheduler,
)
assertEquals(5L, scheduler.nextDelay())
delay.set(1)
scheduler.runNext()
assertEquals(1L, scheduler.nextDelay())
```

- [ ] **Step 2: Run focused coordinator tests RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.battle.BattleMemoryCoordinatorTest --rerun-tasks
```

Expected: compilation fails on the absent scheduler and polling provider.

- [ ] **Step 3: Implement the scheduler seam and adaptive loop**

Define a small scheduler contract in `BattleMemoryCoordinator.kt`:

```kotlin
interface BattleHeartbeatScheduler : AutoCloseable {
    fun schedule(delayMillis: Long, action: () -> Unit)
}
```

Back it with the existing single-thread `ScheduledExecutorService`. After each `safeHeartbeat`, schedule exactly one next callback using:

```kotlin
private fun nextHeartbeatDelay(): Long = when {
    !eligible -> CACHED_HEARTBEAT_MILLIS
    cachedLayout == null || readMode == ReadMode.DISCOVERY -> pollingIntervalProvider().coerceIn(1, 20).toLong()
    else -> CACHED_HEARTBEAT_MILLIS
}
```

Do not use `scheduleWithFixedDelay`, do not pipeline reads, and make `close()` prevent rescheduling.

- [ ] **Step 4: Retain validated layouts across non-battle and outcomes**

Remove the unconditional `cachedLayout = null` after a battle outcome. In the Gen III cached path, preserve the layout when the rebased battler-count byte is structurally present and zero:

```kotlin
val resolved = gen3Resolver.resolveKnown(bytes, rebased, context.catalog)?.copy(layout = absolute)
if (resolved == null && !knownGen3NonBattle(bytes, rebased)) cachedLayout = null
resolved
```

Keep clearing on session reset, ROM/generation change, or nonzero invalid cached data.

- [ ] **Step 5: Inject the live setting and verify GREEN**

In `RetroArchSetupCoordinator`, construct the coordinator with:

```kotlin
pollingIntervalProvider = runtime::battlePollingIntervalMs
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.battle.BattleMemoryCoordinatorTest --rerun-tasks
```

Expected: all coordinator tests pass without wall-clock sleeps.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt
git commit -m "fix: accelerate and cache battle discovery"
```

### Task 3: Make automatic Combat navigation edge-triggered

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write the repeated-sample navigation RED**

```kotlin
gateway.dispatch(CompanionAction.BattleStarted(firstBattle))
gateway.dispatch(CompanionAction.OpenSpecies(25))
val updated = gateway.dispatch(CompanionAction.BattleUpdated(secondBattle))
assertEquals(AppScreen.DETAIL, updated.screen)
assertEquals(secondBattle, updated.battle)

val ended = gateway.dispatch(CompanionAction.BattleEnded)
assertEquals(AppScreen.DETAIL, ended.screen)
assertEquals(AppScreen.POKEDEX, ended.priorScreen)

val next = gateway.dispatch(CompanionAction.BattleStarted(firstBattle))
assertEquals(AppScreen.BATTLE, next.screen)
```

- [ ] **Step 2: Run the reducer test RED**

```powershell
.\gradlew.bat :companion-core:test --tests com.enrpau.dualscreendex.companion.CompanionGatewayTest --rerun-tasks
```

Expected: compilation fails because `BattleUpdated` and battle return state do not exist.

- [ ] **Step 3: Implement explicit start/update/end actions**

Add internal `battleReturnScreen` to `AppSnapshot`. `BattleStarted` records it and optionally opens Combat. `BattleUpdated` changes only battle data and selected target. `BattleEnded` returns only when the current screen is Combat; otherwise it preserves the screen and rewrites a `priorScreen == BATTLE` back-link to the recorded destination.

```kotlin
is CompanionAction.BattleUpdated -> state.copy(
    battle = action.battle,
    selectedSpeciesId = action.battle.opponents.getOrNull(action.battle.targetIndex)?.speciesId,
)
```

In `applyBattleTracking`, dispatch `BattleStarted` only when `gateway.bootstrap().battle == null`; dispatch `BattleUpdated` otherwise.

- [ ] **Step 4: Run reducer and runtime tests GREEN**

```powershell
.\gradlew.bat :companion-core:test --tests com.enrpau.dualscreendex.companion.CompanionGatewayTest --rerun-tasks
.\gradlew.bat :app:testDebugUnitTest --rerun-tasks
```

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test
git commit -m "fix: preserve manual navigation during battles"
```

### Task 4: Implement weighted area-relative rarity policy

**Files:**
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/battle/RarityEvaluator.kt`
- Create: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/battle/RarityEvaluatorTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: relevant API model tests under `companion-core/src/test`

- [ ] **Step 1: Write exhaustive rarity policy tests**

Define tests for all innate boundaries, normalized four/five-DV inputs, all relative boundaries, weighted slot midpoints, 0/5 clamping, agreeing and disagreeing tables, missing weights, invalid ranges, nonmatching species/level, unmatched area, and non-MATCHED SaveRAM status.

```kotlin
val area = encounterArea(
    baseId = 0x0203,
    slots = listOf(
        EncounterSlot(speciesId = 1, minimumLevel = 8, maximumLevel = 12, weight = 80),
        EncounterSlot(speciesId = 2, minimumLevel = 14, maximumLevel = 16, weight = 20),
    ),
)
val result = RarityEvaluator.evaluate(
    individual = OwnedPokemon("battle", 2, 3, 14, ivs = List(6) { 24 }),
    currentAreaBaseId = 0x0203,
    encounterAreas = listOf(area),
)
assertEquals("COMPETENT", result.relativeTier?.name)
assertEquals("VETERAN", result.innateTier?.name)
assertEquals(3.5, result.stars, 0.0)
```

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :companion-core:test --tests com.enrpau.dualscreendex.companion.battle.RarityEvaluatorTest --rerun-tasks
```

Expected: compilation fails because `RarityEvaluator` is absent.

- [ ] **Step 3: Implement immutable rarity results**

Create enums and result:

```kotlin
enum class RelativeTier(val adjustment: Double) {
    WEAK(-0.5), ORDINARY(0.0), COMPETENT(0.5), STRONG(0.5), MAJOR(0.5)
}
enum class InnateTier(val baseStars: Int) {
    FODDER(0), STANDARD(1), TRAINED(2), VETERAN(3), ELITE(4), ACE(5)
}
data class RarityAssessment(
    val relativeTier: RelativeTier?,
    val innateTier: InnateTier?,
    val baseStars: Int?,
    val areaAdjustment: Double?,
    val stars: Double?,
)
```

Filter areas by `area.id / 10 == currentAreaBaseId`; candidate tables must contain the opponent species at its observed level. Reject a candidate if any participating slot lacks positive weight or has incoherent levels. Calculate the weighted midpoint across the entire table and require all candidates to yield the same relative tier. Use `PreferredIndividualSelector.innateAverage` for IV/DV truth and clamp final stars to `0.0..5.0`.

- [ ] **Step 4: Project structured rarity through the API**

Replace `OpponentView.rarity: String` with:

```kotlin
data class RarityView(
    val relativeTier: String?,
    val innateTier: String?,
    val baseStars: Int?,
    val areaAdjustment: Double?,
    val stars: Double?,
)
```

Pass `snapshot.ledger.currentAreaBaseId` only when `saveRam.status == "MATCHED"`. Do no calculation in the web layer.

- [ ] **Step 5: Run GREEN and commit**

```powershell
.\gradlew.bat :companion-core:test --tests com.enrpau.dualscreendex.companion.battle.RarityEvaluatorTest --rerun-tasks
.\gradlew.bat :companion-core:test --rerun-tasks
git add companion-core/src/main companion-core/src/test
git commit -m "feat: score rarity against weighted encounter levels"
```

Expected: companion-core passes.

### Task 5: Render stars and the two-tier title

**Files:**
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/BattlePage.tsx`
- Modify: `companion-web/src/pages/BattlePage.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write UI tests RED**

Assert the DOM order is heading, stars, shortcut; five star containers render; `3.5` fills three whole and one half; the accessible label includes both components; the card title is `WEAK STANDARD`; area-unavailable title is `UNKNOWN STANDARD`; innate-unavailable card is `RARITY UNAVAILABLE`.

```tsx
expect(container.querySelector('.battle-name-row')?.children[0].tagName).toBe('H1');
expect(container.querySelector('.battle-name-row')?.children[1].classList.contains('rarity-stars')).toBe(true);
expect(container.querySelectorAll('.rarity-star')).toHaveLength(5);
expect(screen.getByText('WEAK STANDARD')).toBeTruthy();
```

- [ ] **Step 2: Run RED**

```powershell
Push-Location companion-web
npm test -- --run src/pages/BattlePage.test.tsx
Pop-Location
```

Expected: tests fail because structured rarity and star elements are absent.

- [ ] **Step 3: Implement presentation-only stars**

Render each star with an outline plus clipped fill:

```tsx
const fill = stars == null ? 0 : Math.max(0, Math.min(1, stars - index));
return <span class="rarity-star" aria-hidden="true">
  <span class="rarity-star-outline">☆</span>
  <span class="rarity-star-fill" style={{ width: `${fill * 100}%` }}>★</span>
</span>;
```

Place `<RarityStars rarity={opponent.rarity} />` between `<h1>` and the Pokédex button. Build the card title from `relativeTier ?? 'UNKNOWN'` plus `innateTier`, remove the old secondary relative-level line, and keep exact hidden values undisclosed.

- [ ] **Step 4: Run web GREEN and commit**

```powershell
Push-Location companion-web
npm test -- --run src/pages/BattlePage.test.tsx
npm test -- --run
npm run build
Pop-Location
git add companion-web/src
git commit -m "feat: show area-adjusted battle rarity stars"
```

Expected: all web tests and production build pass.

### Task 6: Retire unsupported AYN Thor controller-focus automation

- [x] Remove the settings control, API field, native lifecycle bridge, and persisted setting.
- [x] Remove the Shizuku/Sui provider and service integration.
- [x] Remove `WRITE_SETTINGS` and do not replace it with another privileged permission.
- [x] Preserve Docked/Overlay display selection and the compatibility-document and GAFT registry contracts.
- [x] Add migration and UI regressions proving the legacy field is scrubbed and the control is absent.

### Task 7: Full verification, public prerelease, and install-only handoff

**Files:**
- Modify: `release/RELEASE_NOTES_1.0.0.md`
- Modify: `README.md`
- Modify: `release/v1-ready.json`
- Verify: `.github/workflows/release.yml`

- [ ] **Step 1: Run all local verification gates**

```powershell
Push-Location companion-web
npm ci
npm test -- --run
npm run build
Pop-Location
node --test tools/release/*.test.mjs
pwsh -File tools/android/Test-DualDexAndroidTools.ps1
.\gradlew.bat test :app:lintDebug :app:assembleRelease --stacktrace
git diff --check
```

Expected: every command succeeds. Local release APK remains unsigned and is not installed.

- [ ] **Step 2: Update release notes and readiness evidence**

Document configurable discovery polling, cache reuse, manual-navigation preservation, weighted area rarity, and removal of the unsupported controller-focus feature. Update actual test totals in `release/v1-ready.json`; do not change the compatibility documents or GAFT registry artifacts.

- [ ] **Step 3: Commit and push the release source**

```powershell
git add README.md release/RELEASE_NOTES_1.0.0.md release/v1-ready.json
git commit -m "release: prepare DualDex 1.0.0 rc13"
git push origin codex/dualdex-parser-spec
git tag -a v1.0.0-rc.13 -m "DualDex 1.0.0 rc13"
git push origin v1.0.0-rc.13
```

Expected: branch and new non-replacing tag are present on origin.

- [ ] **Step 4: Dispatch and wait for the protected release workflow**

```powershell
gh workflow run release.yml --repo Darkaxt/DualScreenDex --ref v1.0.0-rc.13 -f tag=v1.0.0-rc.13
gh run list --repo Darkaxt/DualScreenDex --workflow release.yml --limit 1 --json databaseId,status,conclusion,headSha,url
```

Monitor the single run to completion without cancellation. Expected: conclusion `success`; the GitHub release exists with `isDraft=false` and `isPrerelease=true`.

- [ ] **Step 5: Download and verify the public artifact locally**

Use `D:\Temp\dualdex-rc13-install` for the bounded download. Verify the release metadata, SHA256SUMS, APK SHA, package ID, version `1.0.0-rc.13`, version code, and pinned signer locally with `aapt` and `apksigner`. Do not contact the Android device during verification.

```powershell
gh release view v1.0.0-rc.13 --repo Darkaxt/DualScreenDex --json isDraft,isPrerelease,tagName,url
gh release download v1.0.0-rc.13 --repo Darkaxt/DualScreenDex --dir D:\Temp\dualdex-rc13-install
```

- [ ] **Step 6: Perform the sole authorized device action**

Do not use `validate-signed-candidate.ps1 -Install`, because it performs forbidden device queries. Run only the install/update command against the previously authorized Thor serial:

```powershell
& 'C:\Users\darka\AppData\Local\Android\Sdk\platform-tools\adb.exe' -s bfa98654 install -r 'D:\Temp\dualdex-rc13-install\DualDex-v1.0.0-rc.13.apk'
```

Expected: ADB itself returns `Success`. Do not run `adb devices`, `get-state`, `getprop`, `pm`, `dumpsys`, `am start`, `input`, `logcat`, screenshots, permission grants, or settings commands.

- [ ] **Step 7: Clean bounded temporary release files and report**

After verifying the exact directory is `D:\Temp\dualdex-rc12-install`, remove only that directory if the environment permits the narrow cleanup. Report the release URL, source commit, APK hash, signer fingerprint, workflow result, and literal ADB install result. State explicitly that no launch or device validation occurred.
