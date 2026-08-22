# Continuity, Cache, Map, Navigation, and Party Icon Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver one RC in which verified-ROM cache activation is ordered correctly, Local-map following moves continuously with configurable acceleration, nested pages return through a real UI route stack, and Party uses one recognizable Poké Ball icon.

**Architecture:** RetroArch remains the sole owner of automatic ROM activation: the exact SHA is known before cache lookup begins. Map following moves from a fixed CSS transition to a requestAnimationFrame-driven follower whose velocity survives coordinate updates. Client-only pages use a typed overlay stack while server-owned screens gain an internal screen history, preserving battle priority and existing API fields.

**Tech Stack:** Kotlin/JVM and Android, Preact/TypeScript, Vitest/Testing Library, JUnit 4, Gradle 8, SQLite catalog cache.

---

### Task 1: Order cache activation behind verified ROM identity

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write the failing runtime tests**

Add cache-hit and cache-miss tests using `ImmediateExecutorService` and the existing recording repositories. Record every `CatalogLoadingState.phase` emitted by the gateway. Require a hit to remain `CACHE_REOPEN` and never invoke `parseCatalog`; require a miss to emit `CACHE_REOPEN` before `ROM_IDENTITY` and invoke parsing exactly once.

```kotlin
assertEquals(listOf("CACHE_REOPEN"), activePhases)
assertEquals(0, parseCalls)

assertEquals("CACHE_REOPEN", activePhases.first())
assertTrue(activePhases.indexOf("ROM_IDENTITY") > activePhases.indexOf("CACHE_REOPEN"))
assertEquals(1, parseCalls)
```

- [ ] **Step 2: Run the focused tests and verify RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.web.ProductionCompanionRuntimeTest`

Expected: FAIL because `loadInternal` currently emits `ROM_IDENTITY` before `readComplete`.

- [ ] **Step 3: Implement the single ordered activation path**

Remove `lastCatalogSha256?.let(runtime::restoreCatalogAsync)` from application startup. In `loadInternal`, start the verified-ROM transition as `CACHE_REOPEN`; after `readComplete` returns null, publish the first real parser phase before calling `parseCatalog`. Do not add another executor, retry, or timeout.

```kotlin
val generation = beginCatalogTransition(rom.sha256, name, "CACHE_REOPEN")
parserWorker.execute {
    val cached = catalogRepository?.readComplete(rom.sha256)
    if (cached != null) { publishReopened(generation, name, cached.catalog); return@execute }
    publishWork(generation, CatalogWorkProgress(CatalogWorkModule.ROM_IDENTITY, 0, CatalogWorkModule.entries.size), name)
    // existing parser path
}
```

- [ ] **Step 4: Run the focused runtime tests and verify GREEN**

Run the Step 2 command.

Expected: PASS, including the new phase-order assertions.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt
git commit -m "fix: order cache activation behind ROM identity"
```

### Task 2: Persist global Local-map follow smoothing

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt`
- Modify: `companion-web/src/pages/SettingsPage.production.test.tsx`

- [ ] **Step 1: Write failing persistence and UI tests**

Require a default of 25, clamping to 0..100, and global persistence across two ROM overrides. Render Settings and require a `Map follow smoothing` range with the current percentage.

```kotlin
assertEquals(25, repository.readGlobal().mapFollowSmoothingPercent)
assertEquals(100, repository.readGlobal().mapFollowSmoothingPercent)
```

```tsx
expect(screen.getByRole('slider', { name: 'Map follow smoothing' })).toHaveValue('25');
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `./gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.settings.SettingsRepositoryTest`

Run: `npm test -- --run src/pages/SettingsPage.production.test.tsx`

Expected: FAIL because the setting does not exist.

- [ ] **Step 3: Implement the global setting**

Add `mapFollowSmoothingPercent: Int = 25` to `CompanionSettings`; sanitize to 0..100. Store it with other device-global fields in `SettingsRepository`. Parse it in runtime `SETTINGS` updates, expose it in the TypeScript model/default state, and add the slider under `LOCAL MAP DETAILS`.

```tsx
<input aria-label="Map follow smoothing" type="range" min="0" max="100" step="5"
  value={settings.mapFollowSmoothingPercent ?? 25}
  onInput={event => update({ mapFollowSmoothingPercent: Number(event.currentTarget.value) })} />
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

Run both Step 2 commands.

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt app/src/main/java/com/darkaxt/dualdex/settings/SettingsRepository.kt app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt companion-web/src/models.ts companion-web/src/App.tsx companion-web/src/pages/SettingsPage.tsx app/src/test/java/com/darkaxt/dualdex/settings/SettingsRepositoryTest.kt companion-web/src/pages/SettingsPage.production.test.tsx
git commit -m "feat: configure global map follow smoothing"
```

### Task 3: Replace polling-shaped jumps with accelerated continuous following

**Files:**
- Modify: `companion-web/src/mapEngine.ts`
- Modify: `companion-web/src/mapEngine.test.ts`
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write failing follower tests**

Add a pure `AcceleratedMapFollower` test seam. Require the first frame to move less than a later frame, a changed target to preserve velocity, a large discontinuity to request a snap, and 100% smoothing to advance more slowly than 0%.

```ts
const follower = new AcceleratedMapFollower({ x: 0, y: 0 }, 25);
follower.target({ x: 100, y: 0 });
const first = follower.step(16);
const later = Array.from({ length: 10 }, () => follower.step(16)).at(-1)!;
expect(later.x - first.x).toBeGreaterThan(first.x);
```

- [ ] **Step 2: Run the map-engine tests and verify RED**

Run: `npm test -- --run src/mapEngine.test.ts`

Expected: FAIL because the follower does not exist.

- [ ] **Step 3: Implement the pure follower**

Implement frame-delta-based acceleration with a capped velocity and stopping-distance deceleration. Do not reset velocity when a new nearby coordinate arrives. Expose `target`, `step`, `settled`, and `reset`; keep `shouldGlideCamera` as the discontinuity gate.

- [ ] **Step 4: Run map-engine tests and verify GREEN**

Run the Step 2 command.

Expected: PASS.

- [ ] **Step 5: Write the failing MapPage integration tests**

Use fake animation frames to prove that player coordinates/sprite update immediately while camera pan advances across multiple frames; manual pan stops following; recenter resumes it; reduced motion and discontinuities snap.

- [ ] **Step 6: Run MapPage tests and verify RED**

Run: `npm test -- --run src/pages/MapPage.test.tsx`

Expected: FAIL because the page still uses a 140 ms CSS transition.

- [ ] **Step 7: Integrate requestAnimationFrame following**

Keep one follower and one animation-frame handle in refs. On each nearby live coordinate, update only the target and ensure the frame loop is running. Each frame calculates a viewport and writes it through `setViewport`; cancel the loop on manual pan/zoom, unmount, map/area discontinuity, reduced motion, and after settling. Remove `.is-camera-gliding` transition timing from CSS.

- [ ] **Step 8: Run MapPage and map-engine tests and verify GREEN**

Run both focused Vitest commands.

Expected: PASS without lingering fake timers or animation frames.

- [ ] **Step 9: Commit**

```powershell
git add companion-web/src/mapEngine.ts companion-web/src/mapEngine.test.ts companion-web/src/pages/MapPage.tsx companion-web/src/pages/MapPage.test.tsx companion-web/src/styles.css
git commit -m "feat: accelerate continuous local map following"
```

### Task 4: Introduce a real navigation history for server and nested client routes

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt`
- Modify: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt`
- Create: `companion-web/src/navigation.ts`
- Create: `companion-web/src/navigation.test.ts`
- Modify: `companion-web/src/App.tsx`
- Modify: `companion-web/src/App.production.test.tsx`
- Modify: `companion-web/src/pages/PartyPage.tsx`
- Modify: `companion-web/src/pages/PartyPage.test.tsx`

- [ ] **Step 1: Write failing core history tests**

Require `POKEDEX -> DETAIL -> TRAINER -> PARTY` to return one step at a time without self-loops. Require a battle interruption to return to the pre-battle route and not pollute ordinary history.

```kotlin
gateway.dispatch(CompanionAction.OpenSpecies(25))
gateway.dispatch(CompanionAction.OpenTrainer)
gateway.dispatch(CompanionAction.OpenParty)
assertEquals(AppScreen.TRAINER, gateway.dispatch(CompanionAction.BackToPokedex).screen)
assertEquals(AppScreen.DETAIL, gateway.dispatch(CompanionAction.BackToPokedex).screen)
assertEquals(AppScreen.POKEDEX, gateway.dispatch(CompanionAction.BackToPokedex).screen)
```

- [ ] **Step 2: Run core tests and verify RED**

Run: `./gradlew.bat :companion-core:test --tests com.enrpau.dualscreendex.companion.CompanionGatewayTest`

Expected: FAIL because only `priorScreen` is stored.

- [ ] **Step 3: Implement bounded screen history**

Add an internal `navigationHistory: List<AppScreen>` to `AppSnapshot`, capped at 16 entries. Centralize push/pop helpers in `CompanionGateway`. Continue populating `priorScreen` and `settingsReturnScreen` for API compatibility, but make `BackToPokedex` pop the history. Keep battle return handling separate and remove stale `BATTLE` entries when battle ends.

- [ ] **Step 4: Run core tests and verify GREEN**

Run the Step 2 command.

Expected: PASS.

- [ ] **Step 5: Write failing typed client-route tests**

Define routes for `MAP`, `MAPPER`, `CAPABILITIES`, `PARTY_MEMBER`, `MOVE`, `ABILITY`, and `NATURE`. Require push/pop to preserve a Party member beneath its Ability page and prevent duplicate adjacent routes.

```ts
const routes = pushRoute(pushRoute([], { kind: 'PARTY_MEMBER', slot: 0 }), { kind: 'ABILITY', id: 65 });
expect(popRoute(routes).at(-1)).toEqual({ kind: 'PARTY_MEMBER', slot: 0 });
```

- [ ] **Step 6: Run navigation tests and verify RED**

Run: `npm test -- --run src/navigation.test.ts src/App.production.test.tsx src/pages/PartyPage.test.tsx`

Expected: FAIL because route state is fragmented across booleans, IDs, and Party-local modal state.

- [ ] **Step 7: Implement the client route stack**

Replace `mapperOpen`, `capabilityReportOpen`, `mapOpen`, the three detail IDs, and Party-local `detailSlot` with one `UiRoute[]`. Render from the top route. Hardware and visual Back pop exactly one client route before sending server `BACK`. Pass the active `PARTY_MEMBER` slot into a controlled `PartyPage`, so `Party -> Pokémon -> Ability -> Back` restores the same Pokémon detail dialog.

- [ ] **Step 8: Run client navigation tests and verify GREEN**

Run the Step 6 command.

Expected: PASS for click navigation and `dualdexback` paths.

- [ ] **Step 9: Commit**

```powershell
git add companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/CompanionGateway.kt companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/CompanionGatewayTest.kt companion-web/src/navigation.ts companion-web/src/navigation.test.ts companion-web/src/App.tsx companion-web/src/App.production.test.tsx companion-web/src/pages/PartyPage.tsx companion-web/src/pages/PartyPage.test.tsx
git commit -m "fix: preserve complete companion navigation history"
```

### Task 5: Replace the Party glyph with one detailed Poké Ball

**Files:**
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/components.test.ts`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write the failing semantic icon test**

Render a Header with `onParty`. Require one Poké Ball body, one horizontal divider, one center button, and no second ball body.

- [ ] **Step 2: Run the component test and verify RED**

Run: `npm test -- --run src/components.test.ts`

Expected: FAIL because the current glyph contains two equal circles.

- [ ] **Step 3: Draw the single Poké Ball icon**

Use one 24 px circular body inside the existing 28 px viewBox, an upper filled hemisphere, a divider, and a concentric center button. Inherit currentColor and preserve the `Party` accessible label on the button.

- [ ] **Step 4: Run the component test and verify GREEN**

Run the Step 2 command.

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add companion-web/src/components.tsx companion-web/src/components.test.ts companion-web/src/styles.css
git commit -m "fix: clarify the Party shortcut icon"
```

### Task 6: Verify, package, publish, and install the next RC

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `CHANGELOG.md`
- Modify: release documentation if the repository release process requires it

- [ ] **Step 1: Run focused regression suites**

Run the focused commands from Tasks 1-5.

Expected: all PASS.

- [ ] **Step 2: Run the proportional aggregate gates**

Run: `npm test -- --run`

Run: `./gradlew.bat :companion-core:test :catalog-store:test :app:testDebugUnitTest :app:assembleRelease`

Expected: all tasks successful and a signed release APK produced.

- [ ] **Step 3: Prepare the next sequential RC**

Increment exactly once from RC42 to RC43: `versionName=1.1.0-rc.43`, `versionCode=1010043`, tag `v1.1.0-rc.43`, and matching APK filename. Document the four user-visible continuity fixes without diagnostic wording.

- [ ] **Step 4: Verify the artifact**

Inspect APK metadata and SHA-256. Confirm the working tree contains only intended changes.

- [ ] **Step 5: Commit, tag, push, and publish**

Commit the RC metadata, create the annotated tag, push the current branch/tag to the configured GitHub remote, and publish the signed APK as the RC43 prerelease.

- [ ] **Step 6: Install only and perform read-only ADB checks**

Install RC43 on `bfa98654`. Do not operate RetroArch or the game. Verify installed version metadata and use ADB observation only if the user has the UI open.
