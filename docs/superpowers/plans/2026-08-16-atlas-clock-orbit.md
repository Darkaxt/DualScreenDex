# Atlas Clock Orbit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the redundant Atlas marker toggle and render a 20-percent-larger in-game clock with one source-authorized sun-or-moon icon moving through its active phase.

**Architecture:** The Gen III parser persists a validated day/night schedule beside the live-clock address. Android converts live ROM time plus that schedule into a normalized `DAY`/`NIGHT` phase and `0..1` progress, the API transports those values without inference, and one shared Preact clock component renders the number and orbit in every header. Atlas markers become policy-driven only: fog/reveal state controls visibility, with no user override button.

**Tech Stack:** Kotlin/JVM parser and companion modules, Android Kotlin runtime, Gson/SQLite catalog persistence, Preact/TypeScript, Vitest, Gradle.

---

### Task 1: Persist the source-validated clock schedule

**Files:**
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogModels.kt`
- Modify: `parser-core/src/main/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3RuntimeMemoryLayoutResolver.kt`
- Modify: `catalog-store/src/main/kotlin/com/darkaxt/dualdex/catalog/CatalogSchema.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/catalog/CatalogRuntimeMemoryLayoutTest.kt`
- Test: `parser-core/src/test/kotlin/com/enrpau/dualscreendex/parser/parse/Gen3RuntimeMemoryLayoutResolverTest.kt`
- Test: `catalog-store/src/test/kotlin/com/darkaxt/dualdex/catalog/CatalogStoreTest.kt`

- [ ] **Step 1: Write failing model and resolver assertions**

Add assertions for a normalized schedule and for fail-closed all-or-nothing clock evidence:

```kotlin
assertEquals(CatalogGameClockSchedule(dayStartHour = 6, nightStartHour = 21), resolved.liveClockSchedule)
assertThrows(IllegalArgumentException::class.java) {
    validLayout.copy(liveClockAddress = null, liveClockSchedule = CatalogGameClockSchedule(6, 21))
}
```

Extend the existing exact Modern Emerald expected layout with the same schedule and extend the catalog round-trip fixture to assert it survives SQLite serialization.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```powershell
.\gradlew.bat :parser-core:test --tests "*CatalogRuntimeMemoryLayoutTest" --tests "*Gen3RuntimeMemoryLayoutResolverTest" :catalog-store:test --tests "*CatalogStoreTest"
```

Expected: compilation fails because `CatalogGameClockSchedule` and `liveClockSchedule` do not exist.

- [ ] **Step 3: Add the minimal normalized schedule**

Add:

```kotlin
data class CatalogGameClockSchedule(val dayStartHour: Int, val nightStartHour: Int) {
    init {
        require(dayStartHour in 0..23)
        require(nightStartHour in 0..23)
        require(dayStartHour != nightStartHour)
    }
}
```

Add `liveClockSchedule: CatalogGameClockSchedule? = null` to `CatalogGen3RuntimeMemoryLayout`, require it only when `liveClockAddress` is present, and change clock resolution to return both the unique address and `CatalogGameClockSchedule(EARLY_NIGHT_LAST_HOUR + 1, LATE_NIGHT_FIRST_HOUR)` only after the existing complete compiled predicate proof succeeds. Increment `CatalogSchema.parserSchemaVersion` from 17 to 18 so RC12 caches without schedule evidence are rebuilt.

- [ ] **Step 4: Re-run the focused tests and confirm GREEN**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit the parser slice**

```powershell
git add parser-core catalog-store
git commit -m "feat: persist validated game time phases"
```

### Task 2: Normalize phase and orbit progress before the API

**Files:**
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/AppModels.kt`
- Create: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/model/GameClockProjection.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/model/GameClockProjectionTest.kt`
- Test: `companion-core/src/test/kotlin/com/enrpau/dualscreendex/companion/api/ApiViewBuilderTest.kt`
- Test: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeTest.kt`

- [ ] **Step 1: Write the boundary and fail-closed tests**

Define the wished-for API and assert the four exact boundaries:

```kotlin
assertClock(5, 59, GameClockPhase.NIGHT, 539.0 / 540.0)
assertClock(6, 0, GameClockPhase.DAY, 0.0)
assertClock(20, 59, GameClockPhase.DAY, 899.0 / 900.0)
assertClock(21, 0, GameClockPhase.NIGHT, 0.0)
assertEquals(GameClock(12, 34), projectGameClock(12, 34, null))
```

Assert `ApiViewBuilder.state` emits the phase/progress exactly, and assert the production runtime projects a live Modern clock using the schedule stored in the active catalog.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run:

```powershell
.\gradlew.bat :companion-core:test --tests "*GameClockProjectionTest" --tests "*ApiViewBuilderTest" :app:testDebugUnitTest --tests "*ProductionCompanionRuntimeTest"
```

Expected: compilation fails on the missing phase/projection API.

- [ ] **Step 3: Implement the minimal normalized projection**

Add `GameClockPhase { DAY, NIGHT }` and optional `phase`/`phaseProgress` fields to `GameClock`, requiring both or neither and requiring progress in `0.0..1.0`. Implement `projectGameClock(hours, minutes, schedule)` using minute arithmetic, including the wrapping night interval. Extend `GameClockView` with nullable string phase and progress, and update `ProductionCompanionRuntime.publishSelectedPlayerSnapshot()` to pass the parser schedule from `catalog.runtimeMetadata.gen3RuntimeMemoryLayout`.

- [ ] **Step 4: Re-run the focused tests and confirm GREEN**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit the runtime/API slice**

```powershell
git add companion-core app
git commit -m "feat: project normalized day night progress"
```

### Task 3: Render one shared celestial clock

**Files:**
- Create: `companion-web/src/GameClockIndicator.tsx`
- Create: `companion-web/src/GameClockIndicator.test.tsx`
- Modify: `companion-web/src/components.tsx`
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write the failing component tests**

Assert a plain clock has no orbit, a day clock has exactly one semantic sun, and a night clock has exactly one semantic moon:

```tsx
expect(container.querySelector('[data-semantic-icon="sun"]')).toBeTruthy();
expect(container.querySelector('[data-semantic-icon="moon"]')).toBeNull();
expect(container.querySelectorAll('.game-time-celestial')).toHaveLength(1);
expect(container.querySelector('.game-time-celestial')?.getAttribute('style')).toContain('75%');
```

Also assert the shared `Header` and `MapPage` both render `.header-game-clock`, making duplicate presentation impossible.

- [ ] **Step 2: Run the focused web tests and confirm RED**

Run:

```powershell
npm.cmd test -- GameClockIndicator.test.tsx MapPage.test.tsx
```

Expected: compilation fails because `GameClockIndicator` does not exist.

- [ ] **Step 3: Implement the shared renderer and clean Option B styling**

Create `GameClockIndicator` with a centered numeric `<time>`, a compact elliptical SVG track, and one inline SVG icon selected solely from normalized `phase`. Clamp progress to `0..1`, calculate a shallow parabolic arc, and position the icon with inline `left`/`bottom`. Update the TypeScript state model with nullable `phase` and `phaseProgress`, use the component from both `Header` and `MapPage`, and set `.header-game-time` to `1.03em` (20 percent above `.86em`). Keep the orbit non-interactive and below the number without occupying any action slot.

- [ ] **Step 4: Re-run the focused tests and confirm GREEN**

Run the command from Step 2. Expected: all selected tests pass.

- [ ] **Step 5: Commit the shared clock UI**

```powershell
git add companion-web
git commit -m "feat: show source-aware clock orbit"
```

### Task 4: Remove the Atlas marker toggle and integrate nodes

**Files:**
- Modify: `companion-web/src/pages/MapPage.tsx`
- Modify: `companion-web/src/pages/MapPage.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Change the Atlas test to the required permanent-marker behavior**

Replace the toggle interaction with assertions that the toggle is absent and eligible markers remain present after zoom/recenter:

```tsx
expect(screen.queryByRole('button', { name: 'Toggle map markers' })).toBeNull();
expect(screen.getByRole('button', { name: 'Current location: Route 101' })).toBeTruthy();
expect(screen.getByRole('button', { name: 'Oldale Town' })).toBeTruthy();
```

Keep the existing assertions that Organic hides Petalburg and Discovered reveals it.

- [ ] **Step 2: Run the Atlas test and confirm RED**

Run:

```powershell
npm.cmd test -- MapPage.test.tsx
```

Expected: failure because the toggle button still exists.

- [ ] **Step 3: Remove the toggle state and control**

Delete `markersVisible`, remove its dependency from `markerLocations`, always select `revealedLocations` under fog and all region locations in Discovered mode, remove the top-left marker button markup, and delete only now-unused marker-control styling. Preserve the multi-region chooser and local-map switch.

- [ ] **Step 4: Re-run the Atlas test and confirm GREEN**

Run the command from Step 2. Expected: all MapPage tests pass.

- [ ] **Step 5: Commit the Atlas simplification**

```powershell
git add companion-web/src/pages/MapPage.tsx companion-web/src/pages/MapPage.test.tsx companion-web/src/styles.css
git commit -m "fix: integrate atlas markers into standard view"
```

### Task 5: Verify and prepare RC13

**Files:**
- Modify: `README.md`
- Modify: `release/v1-ready.json`
- Create: `release/RELEASE_NOTES_1.1.0-rc.13.md`
- Modify: `.github/workflows/release.yml`

- [ ] **Step 1: Run focused and affected-module verification**

Run:

```powershell
.\gradlew.bat :parser-core:test --tests "*CatalogRuntimeMemoryLayoutTest" --tests "*Gen3RuntimeMemoryLayoutResolverTest" :catalog-store:test --tests "*CatalogStoreTest" :companion-core:test --tests "*GameClockProjectionTest" --tests "*ApiViewBuilderTest" :app:testDebugUnitTest --tests "*ProductionCompanionRuntimeTest"
Push-Location companion-web
npm.cmd test
npm.cmd run build
Pop-Location
git diff --check
```

Expected: every command exits 0, with no test failure and no whitespace error.

- [ ] **Step 2: Review the exact user contract**

Confirm from tests and diff that: no Atlas marker toggle remains; Organic/Hidden and Discovered policies are unchanged; the clock is `1.03em`; exactly one icon appears; sun covers `06:00..20:59`; moon covers `21:00..05:59`; missing schedule yields numeric-only; no Android wall-time fallback exists; Atlas/local/Pokédex/Settings controls remain.

- [ ] **Step 3: Update RC13 documentation and metadata**

Describe the user-facing change concisely, increment prospective release metadata from RC12 to RC13, and keep the public RC12 tag/history unchanged.

- [ ] **Step 4: Commit release preparation**

```powershell
git add README.md CHANGELOG.md release .github
git commit -m "chore: prepare v1.1.0-rc.13"
```

- [ ] **Step 5: Re-run release metadata checks and publish through the existing protected workflow**

Run the repository's established release metadata/workflow tests discovered by `rg -n "release-metadata|release-workflow" package.json scripts .github`. If green, push only `release/v1.1.0-rc.13` and the exact `v1.1.0-rc.13` tag, then verify the protected workflow and signed APK artifact. Do not install or launch the APK; device acceptance remains with the user.
