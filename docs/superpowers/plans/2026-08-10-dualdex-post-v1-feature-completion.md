# DualDex Post-v1 Feature Completion Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship live read-only battle context, Area-filter time-of-day markers, and a bounded resizable overlay without weakening the ROM/SaveRAM Pokédex.

**Architecture:** Add a pure-Kotlin `battle-memory` module for structure discovery and battle-state interpretation, extract the non-blocking `READ_CORE_MEMORY` state machine into `retroarch-session`, and bridge normalized updates through the existing companion gateway. Extend encounter and overlay policies as independently tested changes so unsupported memory layouts cannot affect catalog availability.

**Tech Stack:** Kotlin/JVM 17, Android API 30+, JUnit 4, Gson/SQLite catalog sections, RetroArch Network Commands over localhost UDP, Preact/TypeScript/Vitest/Testing Library, Gradle Kotlin DSL.

---

## File map

- `parser-core/.../CatalogModels.kt` and `EncounterMaterializer.kt`: carry exact parsed encounter time windows.
- `companion-core/.../ApiModels.kt`: serialize encounter windows and battle capabilities.
- `companion-web/src/components.tsx` and `pages/PokedexBrowse.tsx`: accessible sun/moon Area markers.
- `app/.../overlay/OverlayPanelSizer.kt`, `OverlaySizeStore.kt`, and `FloatingCompanionService.kt`: pure bounds policy, persisted scale, drag handle.
- `retroarch-session/.../CoreMemoryReader.kt`: reusable read-only command grammar and non-blocking region reads.
- `battle-memory/.../Gen3BattleLayoutResolver.kt`: catalog-coupled `BattlePokemon` candidate scan and relative-global validation.
- `battle-memory/.../BattleObservationTracker.kt`: selected move, target mode, PP deltas, and lifecycle state.
- `app/.../battle/BattleMemoryCoordinator.kt`: Android heartbeat and production-runtime bridge.
- `ProductionCompanionRuntime.kt` and `DualDexApplication.kt`: publish live state without importing the diagnostic mapper.
- `companion-web/src/pages/BattlePage.tsx` and `SettingsPage.tsx`: capability-aware automatic/manual targeting and accurate status copy.

### Task 1: Preserve encounter time windows end to end

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/EncounterMaterializer.kt`
- Modify: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/EncounterMaterializerTest.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogReader.kt`
- Modify: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt`

- [ ] **Step 1: Write failing parser and persistence tests**

Assert that Generation II grass records map to `MORNING`, `DAY`, and `NIGHT`, water and Gen I/III records map to `ANY`, and a catalog round-trip preserves the enum.

```kotlin
assertEquals(setOf(EncounterWindow.MORNING), morning.windows)
assertEquals(setOf(EncounterWindow.DAY), day.windows)
assertEquals(setOf(EncounterWindow.NIGHT), night.windows)
assertEquals(setOf(EncounterWindow.ANY), water.windows)
assertEquals(source.encounterAreas, reopened.catalog.encounterAreas)
```

- [ ] **Step 2: Run the focused tests and verify the missing property fails compilation**

Run: `./gradlew.bat :parser-core:test --tests "*EncounterMaterializerTest" :catalog-store:test --tests "*CatalogStoreTest"`

Expected: FAIL because `EncounterWindow` and `EncounterArea.windows` do not exist.

- [ ] **Step 3: Add the model and explicit method mapping**

```kotlin
enum class EncounterWindow { ANY, MORNING, DAY, NIGHT }

data class EncounterArea(
    val id: Int,
    val name: CatalogField<String>,
    val methodId: Int,
    val slots: List<EncounterSlot>,
    val windows: Set<EncounterWindow> = setOf(EncounterWindow.ANY),
)
```

Map only `GRASS_MORNING`, `GRASS_DAY`, and `GRASS_NIGHT` to restricted windows. Never infer restrictions from RTC support alone.

- [ ] **Step 4: Expose windows through `AreaView` and migrate old cached JSON to `ANY`**

Add `windows: List<String>` to `AreaView`. Normalize a missing/empty legacy encounter-window field in `CatalogReader` before constructing `ParsedCatalog`, and add an explicit old-section JSON fixture assertion; do not rely on Gson invoking Kotlin default arguments.

- [ ] **Step 5: Run parser, catalog, and companion-core suites**

Run: `./gradlew.bat :parser-core:test :catalog-store:test :companion-core:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add parser-core catalog-store companion-core
git commit -m "feat: preserve encounter time windows"
```

### Task 2: Render Area-filter sun and moon markers

**Files:**
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/pages/PokedexBrowse.tsx`
- Create: `companion-web/src/pages/PokedexAreaWindows.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write failing UI tests for every marker state**

Create catalog areas for the same base area with day-only, night-only, split day/night, and unrestricted slots. With `state.filter = 'AREA'`, assert accessible labels `Day encounter`, `Night encounter`, and `Day and night encounter`; assert the unrestricted species has no time icon. Switch to `ALL` and assert all time icons disappear.

- [ ] **Step 2: Run the focused Vitest file**

Run: `npm test -- --run src/pages/PokedexAreaWindows.test.tsx`

Expected: FAIL because `EncounterWindowMarks` is absent.

- [ ] **Step 3: Add a pure window-summary selector and SVG component**

```tsx
export function encounterWindows(catalog: Catalog, areaIds: number[], speciesId: number): string[] {
  const windows = catalog.areas
    .filter(area => areaIds.includes(area.id) && area.speciesIds.includes(speciesId))
    .flatMap(area => area.windows);
  return windows.includes('ANY') ? [] : [...new Set(windows)];
}
```

Render inline path-based sun/moon SVGs after the species name only when the Area filter is active. Use `role="img"`, an exact label, and CSS colors from the current theme; do not use Unicode pictographs.

- [ ] **Step 4: Run focused and full web checks**

Run: `npm test -- --run src/pages/PokedexAreaWindows.test.tsx`

Run: `npm test -- --run && npm run build`

Expected: PASS with no body overflow regression.

- [ ] **Step 5: Commit**

```powershell
git add companion-web
git commit -m "feat: show area encounter time markers"
```

### Task 3: Add bounded overlay sizing and persisted scale

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/overlay/OverlayPanelSizer.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/overlay/OverlayPanelSizerTest.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/overlay/OverlaySizeStore.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/overlay/OverlaySizeStoreTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write failing sizing-policy tests**

Cover 16:10, 21:9, portrait, and Thor bounds. Assert 4:3, a minimum of `320 x 240 dp` when the display permits it, maximum containment inside insets, gutter preference on wide screens, normalized scale clamping to `0.45..1.0`, and deterministic refit after rotation.

```kotlin
val fitted = OverlayPanelSizer.fit(2520, 1080, Insets(left = 0, top = 48, right = 0, bottom = 72), scale = .6)
assertEquals(4.0 / 3.0, fitted.width.toDouble() / fitted.height, .01)
assertTrue(fitted.bounds.right <= 2520)
```

- [ ] **Step 2: Run the focused Android unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*OverlayPanelSizerTest" --tests "*OverlaySizeStoreTest" --tests "*SettingsRepositoryTest"`

Expected: FAIL because insets, scale, placement, and store types are absent.

- [ ] **Step 3: Implement the pure policy and scale store**

Use `OverlayPanelPlacement(size, x, y, scale, gutter)` with immutable integer bounds. Persist only normalized scale and restore `1.0` for legacy settings. Do not persist raw pixels across displays.

- [ ] **Step 4: Re-run focused tests**

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/darkaxt/dualdex/overlay app/src/test/java/com/darkaxt/dualdex/overlay app/src/main/java/com/darkaxt/dualdex/settings app/src/test/java/com/darkaxt/dualdex/settings
git commit -m "feat: define resizable overlay bounds"
```

### Task 4: Add the Android overlay resize handle

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/overlay/OverlayResizeHandle.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/overlay/FloatingCompanionService.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/pages/SettingsPage.production.test.tsx`

- [ ] **Step 1: Add a failing production copy test**

Assert Settings describes a resizable 4:3 panel and no longer says `fixed 4:3 panel`.

- [ ] **Step 2: Implement a dedicated bottom-right handle**

Attach the handle above the WebView in the panel `FrameLayout`. On drag, compute scale from the initial diagonal delta, pass it through `OverlayPanelSizer`, update the same `WindowManager.LayoutParams`, clamp position, and persist the final normalized scale on `ACTION_UP`. The panel remains `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL`.

- [ ] **Step 3: Verify resize never consumes WebView or Poké Ball drag gestures**

Run the app unit tests plus `npm test -- --run src/pages/SettingsPage.production.test.tsx`.

Expected: PASS.

- [ ] **Step 4: Commit**

```powershell
git add app companion-web
git commit -m "feat: make the overlay panel resizable"
```

### Task 5: Extract reusable read-only core-memory transport

**Files:**
- Create: `retroarch-session/src/main/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReader.kt`
- Create: `retroarch-session/src/test/kotlin/com/darkaxt/dualdex/retroarch/CoreMemoryReaderTest.kt`
- Modify: `memory-mapper-lab/src/main/kotlin/com/darkaxt/dualdex/mapper/HeartbeatMemoryReader.kt`
- Modify: `memory-mapper-lab/src/test/kotlin/com/darkaxt/dualdex/mapper/HeartbeatMemoryReaderTest.kt`

- [ ] **Step 1: Write protocol and state-machine tests**

Assert exact command formatting `READ_CORE_MEMORY 02000000 2048`, one in-flight request, fragmented region completion, malformed-response failure, and no command other than `READ_CORE_MEMORY`.

- [ ] **Step 2: Run both focused suites and verify the new API is missing**

Run: `./gradlew.bat :retroarch-session:test --tests "*CoreMemoryReaderTest" :memory-mapper-lab:test --tests "*HeartbeatMemoryReaderTest"`

Expected: FAIL before implementation.

- [ ] **Step 3: Implement `CoreMemoryReadSession`**

Expose `start(regions)`, `heartbeat()`, and sealed states `Idle`, `Reading`, `Complete`, `Failed`. A heartbeat sends or polls one bounded packet and never cancels work by elapsed time.

- [ ] **Step 4: Make the mapper adapter delegate to the shared reader**

Preserve the mapper public API and export bytes exactly; this is a behavior-preserving extraction.

- [ ] **Step 5: Run focused and module suites**

Run: `./gradlew.bat :retroarch-session:test :memory-mapper-lab:test`

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add retroarch-session memory-mapper-lab
git commit -m "refactor: share read-only core memory transport"
```

### Task 6: Resolve Generation III battle layouts dynamically

**Files:**
- Modify: `settings.gradle.kts`
- Create: `battle-memory/build.gradle.kts`
- Create: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/BattleMemoryModels.kt`
- Create: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/Gen3BattleLayoutResolver.kt`
- Create: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/Gen3BattleLayoutResolverTest.kt`

- [ ] **Step 1: Add synthetic single, double, stale, and ambiguous fixtures**

Build byte arrays with `0x58` records using the real field offsets, two/four battler topology, catalog species/moves/types, and relative globals. Tests must assert:

```kotlin
assertEquals(weedleId, result.opponents.single().speciesId)
assertEquals(listOf(leftOpponentId, rightOpponentId), doubleResult.opponents.map { it.speciesId })
assertEquals(TargetMode.AUTOMATIC, doubleResult.target.mode)
assertIs<LayoutResolution.Ambiguous>(resolver.resolve(twoValidArrays, catalog))
```

- [ ] **Step 2: Run the new module test and confirm failure**

Run: `./gradlew.bat :battle-memory:test`

Expected: FAIL because the module/classes are absent.

- [ ] **Step 3: Implement catalog-coupled scanning**

Scan aligned EWRAM offsets for a unique contiguous array. Validate count `2` or `4`, positions, side bit, absent flags, species, level, HP/max HP, four moves, PP bounds, types, stats, ability, `gBattleOutcome`, and complete record bounds. A nonzero validated outcome closes/rejects stale battle data. Return per-field capabilities rather than a single global score.

- [ ] **Step 4: Implement target-cursor competition**

First validate source-shaped relative candidates including `gMultiUsePlayerCursor`, remembered cursor/move, active battler, selected-move cursor, and opponents' side/liveness. Prefer a live cursor transition; otherwise publish all opponents with `MANUAL_TARGET_FALLBACK`.

- [ ] **Step 5: Run the module suite**

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add settings.gradle.kts battle-memory
git commit -m "feat: infer gen3 battle layouts"
```

### Task 7: Track battle lifecycle and Organic observations

**Files:**
- Create: `battle-memory/src/main/kotlin/com/darkaxt/dualdex/battle/BattleObservationTracker.kt`
- Create: `battle-memory/src/test/kotlin/com/darkaxt/dualdex/battle/BattleObservationTrackerTest.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/knowledge/KnowledgePolicyTest.kt`

- [ ] **Step 1: Write failing lifecycle and PP-delta tests**

Assert the first validated identity establishes a baseline, PP drops emit counts, PP increases/switches reset without observations, duplicate snapshots emit none, single missed samples retain the battle, validated non-battle samples close it, and ROM changes clear all baselines.

```kotlin
assertEquals(mapOf(poisonSting to 1, stringShot to 2), tracker.update(final).observations)
assertTrue(tracker.update(switchedOpponent).observations.isEmpty())
```

- [ ] **Step 2: Run the tests and confirm failure**

Run: `./gradlew.bat :battle-memory:test :companion-core:test`

- [ ] **Step 3: Implement count-only observation merging**

Key baselines by ROM identity, battler position, species, and personality identity when available. Export aggregate increments only; do not retain timestamps or order.

- [ ] **Step 4: Add battle capability/target mode to the normalized model**

Keep captured opponents free of frequency display at the presentation layer, while retaining the global ledger for future uncaught encounters.

- [ ] **Step 5: Run both suites and commit**

```powershell
git add battle-memory companion-core
git commit -m "feat: track live battle observations"
```

### Task 8: Bridge passive live memory into the production runtime

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinator.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/battle/BattleMemoryCoordinatorTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/setup/RetroArchSetupCoordinator.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/mapper/MapperIsolationBoundaryTest.kt`

- [ ] **Step 1: Write failing coordinator/runtime tests**

Use a fake transport to drive discovery, steady bounded reads, single/double updates, manual-target actions, selected move, observation merge, catalog switch, disconnect, and non-battle return. Assert mapper disabled/failure remains irrelevant.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "*BattleMemoryCoordinatorTest" --tests "*ProductionCompanionRuntimeTest" --tests "*MapperIsolationBoundaryTest"`

- [ ] **Step 3: Implement two-phase reading**

When a GBA catalog is active and RetroArch is `PLAYING`/`PAUSED`, discover from EWRAM once, then poll only the bounded relative layout window. If the cached layout stops validating, return to discovery. GB/GBC and unsupported layouts publish `UNAVAILABLE` without touching the catalog.

- [ ] **Step 4: Publish through existing actions**

Translate normalized samples to `BattleStarted`, `SelectTarget`, `SelectMove`, `BattleEnded`, and a ledger merge. Add production action support for `BATTLE_TAB` and manual `SELECT_TARGET`; reject invalid indices.

- [ ] **Step 5: Prove the production runtime has no mapper dependency**

Update the boundary test to allow `READ_CORE_MEMORY` only through `battle-memory`/`retroarch-session`, while continuing to reject imports from `memory-mapper-lab` into production runtime and companion state.

- [ ] **Step 6: Run app and core suites; commit**

```powershell
git add app companion-core
git commit -m "feat: publish passive live battle state"
```

### Task 9: Complete capability-aware battle UI

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/BattlePage.tsx`
- Modify: `companion-web/src/pages/BattlePage.test.tsx`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write failing tests for automatic and manual double targets**

Assert automatic mode follows the published target, fallback mode renders both target buttons, the Pokédex icon opens the target entry, selected move metadata/effectiveness render from the ROM, uncaught Organic Moves show observed counts without levels, and captured Moves omit frequency.

- [ ] **Step 2: Run the focused test**

Run: `npm test -- --run src/pages/BattlePage.test.tsx`

Expected: FAIL on missing target mode and live capabilities.

- [ ] **Step 3: Implement the compact battle behavior**

Reuse the existing four tabs and small-screen layout. Do not add a bottom bar, simulator inputs, hidden move slots, exact IV values, or emoji. Update Settings copy to state that the production reader is passive and mapper exports are diagnostic.

- [ ] **Step 4: Run the complete web suite/build and commit**

```powershell
npm test -- --run
npm run build
git add companion-core companion-web
git commit -m "feat: render live battle capabilities"
```

### Task 10: Convergence, device validation, documentation, and release

**Files:**
- Modify: `README.md`
- Modify: `docs/v1-requirement-matrix.md`
- Modify: `docs/v1-delivery-ledger.md`
- Modify: `docs/reports/modern-emerald-memory-mapper-analysis.md`
- Modify: `release/RELEASE_NOTES_1.0.0.md`

- [x] **Step 1: Run full local convergence**

Run: `./gradlew.bat test :app:assembleDebug`

Run: `npm test -- --run && npm run build` in `companion-web`.

Run: `git diff --check`.

Expected: all pass; no private ROM/save/dump/keystore artifacts are tracked.

- [x] **Step 2: Validate debug only on `emulator-5556`**

Install `com.darkaxt.dualdex.debug`, verify time markers with a Generation II fixture/catalog, resize the overlay at wide and Thor-like bounds, and use fake-memory instrumentation to exercise single/double battle transitions. Do not address `emulator-5554`.

- [x] **Step 3: Push a draft PR and wait for CI**

Push the feature branch, open the draft PR, mark ready after local evidence, and require all GitHub checks to pass before merge.

- [x] **Step 4: Publish the next monotonic GitHub-signed release candidate**

Create the next `v1.0.0-rc.N` tag from merged `master`, dispatch the protected release workflow, and independently verify package ID, version, SHA-256, and pinned signer fingerprint from the anonymous release asset.

- [ ] **Step 5: Validate the signed candidate on the Thor** *(public RC9 installed; live play/resize checks pending)*

Install only the verified GitHub-signed APK. With RetroArch focused, validate automatic single battle, inferred/manual double targeting, battle exit, Pokédex link, Organic frequency, resizable overlay, and unchanged ROM/SaveRAM hashes. Record unsupported or inferred capabilities honestly.

- [ ] **Step 6: Update public documentation and publish the release** *(RC9 public; stable release waits for Step 5)*

Remove obsolete claims that all live battle features, time markers, and resize are deferred. Add exact coverage and limitations, attach compact screenshots only after the signed runtime passes, commit the evidence, merge the documentation PR, and make the GitHub release public.
