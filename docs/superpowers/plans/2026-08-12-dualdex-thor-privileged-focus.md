# DualDex AYN Thor Privileged Focus Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the crashing `WRITE_SETTINGS` path with a Shizuku-first, root-second secure-setting bridge that fails closed and disables the Thor focus toggle with a warning when neither provider works.

**Architecture:** Keep focus ownership policy synchronous and pure, but run privileged reads and writes on a dedicated worker. A coordinator performs one explicit single-flight provider chain: authorized Shizuku/Sui UserService, one Shizuku permission request, then fixed root commands. Android publishes an immutable control state to the companion API; the web UI only renders it.

**Tech Stack:** Kotlin/JVM, Android SDK 36, Shizuku API/provider 13.1.5, Android AIDL, JUnit 4, Preact/TypeScript, Vitest, Gradle, GitHub Actions, GitHub CLI, ADB.

---

## File map

- `app/src/main/aidl/com/darkaxt/dualdex/display/IThorFocusPrivilegedService.aidl`: fixed privileged read/write binder contract.
- `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusShizukuService.kt`: Shizuku/Sui UserService for fixed secure-settings commands.
- `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusCommand.kt`: allowlisted command construction and output parsing.
- `app/src/main/java/com/darkaxt/dualdex/display/ShizukuThorFocusProvider.kt`: Shizuku binder, permission, and UserService lifecycle.
- `app/src/main/java/com/darkaxt/dualdex/display/RootThorFocusProvider.kt`: fixed `su` fallback.
- `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusAccessCoordinator.kt`: provider ordering, asynchronous work, and state publication.
- `app/src/main/java/com/darkaxt/dualdex/display/AndroidThorFocusBackend.kt`: ownership persistence and selected-provider access; no direct settings write.
- `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusController.kt`: acquisition/restoration policy with guarded failures.
- `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`: Shizuku listener lifecycle and explicit enable dispatch.
- `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`: desired-setting rollback and runtime bridge.
- `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`: native control-state DTO.
- `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`: complete control-state storage.
- `companion-web/src/pages/SettingsPage.tsx`: disabled toggle and adjacent warning.

### Task 1: Make ownership and command handling crash-safe

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusController.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/display/AndroidThorFocusBackend.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/display/ThorFocusControllerTest.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusCommand.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/display/ThorFocusCommandTest.kt`

- [ ] **Step 1: Write failing crash and allowlist tests**

```kotlin
@Test fun secureSettingExceptionDoesNotEscape() {
    val backend = ThrowingBackend(IllegalArgumentException(
        "You cannot keep your settings in the secure settings.",
    ))
    assertEquals(
        ThorFocusResult.WRITE_FAILED,
        ThorFocusController(backend).sync(true, true, true),
    )
}

@Test fun acceptsOnlyKnownModes() {
    assertEquals(
        listOf("/system/bin/settings", "put", "secure", "screen_focus_lock", "2"),
        ThorFocusCommand.write(ThorFocusMode.BOTTOM),
    )
    assertFailsWith<IllegalArgumentException> { ThorFocusCommand.write(3) }
}
```

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.display.ThorFocusCommandTest --tests com.darkaxt.dualdex.display.ThorFocusControllerTest --rerun-tasks
```

Expected: compile failure because `ThorFocusCommand` and guarded backend reads do not exist.

- [ ] **Step 3: Implement fixed commands and guarded access**

```kotlin
object ThorFocusCommand {
    private const val KEY = "screen_focus_lock"
    fun read() = listOf("/system/bin/settings", "get", "secure", KEY)
    fun write(mode: Int): List<String> {
        require(mode in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM)
        return listOf("/system/bin/settings", "put", "secure", KEY, mode.toString())
    }
    fun parseMode(output: String): Int? = output.trim().toIntOrNull()
        ?.takeIf { it in ThorFocusMode.AUTO..ThorFocusMode.BOTTOM }
}
```

Wrap backend `current` and `write` access in `runCatching`. A failed restoration retains `owned` and `previous`. Remove `Settings.System.canWrite`, `Settings.System.getInt`, and `Settings.System.putInt` from `AndroidThorFocusBackend`.

- [ ] **Step 4: Run GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.darkaxt.dualdex.display.*' --rerun-tasks
git add app/src/main/java/com/darkaxt/dualdex/display app/src/test/java/com/darkaxt/dualdex/display
git commit -m "fix: fail closed on Thor focus setting errors"
```

Expected: focused display tests pass.

### Task 2: Add the Shizuku/Sui provider

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/aidl/com/darkaxt/dualdex/display/IThorFocusPrivilegedService.aidl`
- Create: `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusShizukuService.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/display/ShizukuThorFocusProvider.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/display/ShizukuThorFocusProviderTest.kt`

- [ ] **Step 1: Write Shizuku permission and binder RED tests**

```kotlin
@Test fun requestsPermissionOnlyOnceForOneEnable() {
    val gateway = FakeShizukuGateway(alive = true, granted = false)
    val provider = ShizukuThorFocusProvider(gateway)
    provider.prepare(userInitiated = true)
    provider.prepare(userInitiated = true)
    assertEquals(1, gateway.permissionRequests)
}
```

Also cover binder-dead unavailable, already-granted single bind, denial, and binder death after binding.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.display.ShizukuThorFocusProviderTest --rerun-tasks
```

Expected: compile failure because the provider and gateway are absent.

- [ ] **Step 3: Add official dependencies and manifest provider**

```toml
shizuku = "13.1.5"
shizuku-api = { group = "dev.rikka.shizuku", name = "api", version.ref = "shizuku" }
shizuku-provider = { group = "dev.rikka.shizuku", name = "provider", version.ref = "shizuku" }
```

```kotlin
implementation(libs.shizuku.api)
implementation(libs.shizuku.provider)
```

Add `rikka.shizuku.ShizukuProvider` with `${applicationId}.shizuku`, exported true, multiprocess false, and `android.permission.INTERACT_ACROSS_USERS_FULL`. Remove `WRITE_SETTINGS` if no remaining production call requires it.

- [ ] **Step 4: Add the typed UserService**

```aidl
package com.darkaxt.dualdex.display;
interface IThorFocusPrivilegedService {
    int readMode();
    boolean writeMode(int mode);
    void destroy();
}
```

`ThorFocusShizukuService` executes only `ThorFocusCommand.read()` and `.write(mode)`, drains output, waits for completion, requires exit code zero, and parses only 0 through 2. Implement Shizuku's documented UserService destroy transaction.

- [ ] **Step 5: Implement binder and permission lifecycle**

Use `Shizuku.pingBinder`, `checkSelfPermission`, `shouldShowRequestPermissionRationale`, `requestPermission`, binder received/dead listeners, and `bindUserService`. Keep one pending permission continuation and never call Shizuku methods while the binder is dead.

- [ ] **Step 6: Run GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.display.ShizukuThorFocusProviderTest --rerun-tasks
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/aidl app/src/main/java/com/darkaxt/dualdex/display app/src/test/java/com/darkaxt/dualdex/display
git commit -m "feat: add Shizuku Thor focus provider"
```

Expected: tests pass and AIDL compiles.

### Task 3: Add root fallback and the single-flight chain

**Files:**
- Create: `app/src/main/java/com/darkaxt/dualdex/display/RootThorFocusProvider.kt`
- Create: `app/src/main/java/com/darkaxt/dualdex/display/ThorFocusAccessCoordinator.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/display/RootThorFocusProviderTest.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/display/ThorFocusAccessCoordinatorTest.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/display/AndroidThorFocusBackend.kt`

- [ ] **Step 1: Write provider-order and passive-start RED tests**

```kotlin
@Test fun explicitEnableFallsBackFromShizukuToRoot() {
    val events = mutableListOf<String>()
    val coordinator = coordinator(
        shizuku = fakeProvider("SHIZUKU", events, PrepareResult.Denied),
        root = fakeProvider("ROOT", events, PrepareResult.Ready),
    )
    coordinator.enable(userInitiated = true)
    assertEquals(listOf("SHIZUKU.prepare", "ROOT.prepare", "ROOT.read", "ROOT.write:1"), events)
}

@Test fun passiveRefreshNeverInvokesRoot() {
    val root = CountingProvider()
    coordinator(root = root).refreshPassive()
    assertEquals(0, root.calls)
}
```

Also assert both failures reset the preference, publish the exact warning, and disable the control; repeated enable calls while pending do not duplicate work.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.display.RootThorFocusProviderTest --tests com.darkaxt.dualdex.display.ThorFocusAccessCoordinatorTest --rerun-tasks
```

Expected: compile failure because the root provider and coordinator are absent.

- [ ] **Step 3: Implement fixed root commands**

Execute `su -c` with a command string built only from the fixed settings binary, fixed namespace/key, and a validated integer mode. Validate exit code and parsed output. Run this process only on the coordinator's single worker.

- [ ] **Step 4: Implement immutable state and provider ordering**

```kotlin
data class ThorFocusControlState(
    val status: String,
    val available: Boolean,
    val provider: String? = null,
    val warning: String? = null,
    val operationInFlight: Boolean = false,
)
```

Explicit enable tries Shizuku, then root after a terminal Shizuku failure. Passive refresh inspects Shizuku only. Use `AtomicBoolean` single-flight state. Publish pending before work and clear it on every completion. Call `onDisableDesiredSetting` only after both explicit providers fail.

- [ ] **Step 5: Preserve ownership across providers**

Persist previous mode and owned state, not a provider dependency. Restoration uses the best currently authorized provider and keeps recovery state after failure. If the global value changed away from Top, release local ownership without overwriting it.

- [ ] **Step 6: Run GREEN and commit**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.darkaxt.dualdex.display.*' --rerun-tasks
git add app/src/main/java/com/darkaxt/dualdex/display app/src/test/java/com/darkaxt/dualdex/display
git commit -m "feat: fall back to root for Thor focus"
```

Expected: display tests pass with Shizuku always preceding root.

### Task 4: Wire lifecycle, API state, and preference rollback

**Files:**
- Modify: `app/src/main/java/com/darkaxt/dualdex/MainActivity.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/DualDexApplication.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/ProductionCompanionRuntime.kt`
- Modify: `app/src/main/java/com/darkaxt/dualdex/web/AndroidLoopbackServer.kt`
- Modify: `companion-core/src/main/kotlin/com/enrpau/dualscreendex/companion/api/ApiModels.kt`
- Modify: `app/src/test/java/com/darkaxt/dualdex/web/ProductionCompanionRuntimeThorFocusTest.kt`
- Create: `app/src/test/java/com/darkaxt/dualdex/display/ThorFocusLifecyclePolicyTest.kt`

- [ ] **Step 1: Write lifecycle/API RED tests**

```kotlin
runtime.updateThorFocusState(
    ThorFocusControlView("UNAVAILABLE", false, null, WARNING, false),
)
val state = runtime.stateView().thorFocus
assertFalse(state.available)
assertEquals(WARNING, state.warning)
```

Assert passive resume cannot request Shizuku/root, web toggle is user initiated, and both-provider failure persists `thorTopScreenFocus=false` globally.

- [ ] **Step 2: Run RED**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.web.ProductionCompanionRuntimeThorFocusTest --tests com.darkaxt.dualdex.display.ThorFocusLifecyclePolicyTest --rerun-tasks
```

Expected: compile failure because structured state and lifecycle policy are absent.

- [ ] **Step 3: Remove the invalid Android permission flow**

Delete `ACTION_MANAGE_WRITE_SETTINGS`, its Activity Result launcher, and `ThorFocusPermissionPolicy`. Register/remove Shizuku binder and permission listeners through the provider. `onPostResume` calls passive refresh only; native SETTINGS calls explicit enable or release.

- [ ] **Step 4: Publish structured state**

Add `ThorFocusControlView` to `StateView` and pass it through `ApiViewBuilder`, `ProductionCompanionRuntime`, and `AndroidLoopbackServer`. Invalidate cached state on change. Keep `thorFocusStatus` only as a derived compatibility field if an existing client requires it.

- [ ] **Step 5: Roll back failed desired state**

Copy global settings with `thorTopScreenFocus=false`, persist them, update the gateway, then publish unavailable state. Do not alter ROM-scoped settings.

- [ ] **Step 6: Run GREEN and commit**

```powershell
.\gradlew.bat :companion-core:test --tests com.enrpau.dualscreendex.companion.api.ApiViewBuilderTest --rerun-tasks
.\gradlew.bat :app:testDebugUnitTest --tests com.darkaxt.dualdex.web.ProductionCompanionRuntimeThorFocusTest --tests 'com.darkaxt.dualdex.display.*' --rerun-tasks
git add companion-core/src/main app/src/main app/src/test
git commit -m "fix: publish truthful Thor focus availability"
```

Expected: API and lifecycle tests pass with no Modify System Settings path.

### Task 5: Disable the web control and show the warning

**Files:**
- Modify: `companion-web/src/models.ts`
- Modify: `companion-web/src/pages/SettingsPage.tsx`
- Modify: `companion-web/src/pages/SettingsPage.production.test.tsx`
- Modify: `companion-web/src/styles.css`

- [ ] **Step 1: Write UI RED tests**

```tsx
expect(screen.getByLabelText('Keep controls on top screen')).toBeDisabled();
expect(screen.getByText('Thor focus unavailable — Shizuku or root access is required.')).toBeTruthy();
```

Also assert pending disables the toggle, READY enables it, ACTIVE identifies SHIZUKU or ROOT, and the warning is adjacent.

- [ ] **Step 2: Run RED**

```powershell
Push-Location companion-web
npm test -- --run src/pages/SettingsPage.production.test.tsx
Pop-Location
```

Expected: current toggle remains enabled and structured state is absent.

- [ ] **Step 3: Implement presentation-only rendering**

```tsx
<Toggle
  label="Keep controls on top screen"
  checked={settings.thorTopScreenFocus ?? false}
  disabled={!thor.available || thor.operationInFlight}
  onChange={thorTopScreenFocus => update({ thorTopScreenFocus })}
/>
{thor.warning && <p class="setting-warning thor-focus-warning">{thor.warning}</p>}
```

Render provider beside ACTIVE. Do not show or link Android Modify System Settings.

- [ ] **Step 4: Run GREEN and commit**

```powershell
Push-Location companion-web
npm test -- --run src/pages/SettingsPage.production.test.tsx
npm test -- --run
npm run build
Pop-Location
git add companion-web/src
git commit -m "fix: disable unavailable Thor focus control"
```

Expected: all web tests and production build pass.

### Task 6: Verify, release RC13, and install once

**Files:**
- Modify: `release/RELEASE_NOTES_1.0.0.md`
- Modify: `release/v1-ready.json`
- Modify: release version metadata

- [ ] **Step 1: Run full verification**

```powershell
.\gradlew.bat :companion-core:test :companion-server:test :app:testDebugUnitTest :app:lintRelease :app:assembleRelease --rerun-tasks
Push-Location companion-web
npm test -- --run
npm run build
Pop-Location
```

Expected: tests, lint, web build, and release assembly all pass.

- [ ] **Step 2: Inspect the packaged integration**

Confirm the Shizuku provider exists, `WRITE_SETTINGS` is absent if unused, and no `WRITE_SECURE_SETTINGS` request was added. Record the APK SHA-256 and signer.

- [ ] **Step 3: Update release evidence and commit**

Record exact test totals, hash, signer, and RC13 notes, then commit with `release: prepare public DualDex 1.0.0 rc13`.

- [ ] **Step 4: Push and publish a public prerelease**

Push the branch, tag `v1.0.0-rc.13`, and use the existing workflow. Verify `isDraft=false`, `isPrerelease=true`, and workflow success.

- [ ] **Step 5: Download and verify the public APK**

Download stable assets into a fresh `D:\Temp` directory. Compare SHA-256 and signer with the local release artifact; refuse installation on mismatch.

- [ ] **Step 6: Perform the only authorized device action**

```powershell
$publishedApk = (Get-ChildItem -LiteralPath 'D:\Temp\dualdex-rc13-install' -Filter '*.apk' -File | Select-Object -Single).FullName
adb -s bfa98654 install -r $publishedApk
```

Expected: `Success`. Do not launch, query state, grant permissions, change settings, send input, capture screens, or read logs afterward.

- [ ] **Step 7: Report evidence**

Report the public prerelease URL, workflow result, APK hash, signer, and single ADB install result. State that functional validation remains manual.
