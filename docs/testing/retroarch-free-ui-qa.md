# RetroArch-free UI QA checkpoint

This debug-only checkpoint launches the production `MainActivity`, `DualDexApplication` runtime, `AndroidLoopbackServer`, `DualDexWebView`, parser, catalog, session authority, decoder, and bundled production web UI without requiring RetroArch. When the internal marker exists, a debug-only transport supplies sanitized raw EWRAM/IWRAM frames through the production command, memory-read, decode, and publication path. The APK never contains a ROM.

The package remains `com.darkaxt.dualdex.debug`. With no marker file, the APK keeps ordinary debug behavior and uses the production RetroArch UDP transport. If the marked simulator asset cannot be loaded, the simulator fails closed to `CONTENTLESS` rather than crashing the application or falling through to UDP.

## Debug-only asset provenance

`app/src/debug/assets/retroarch-free-ui-qa/raw-live-memory-scenarios.json` contains six full EWRAM/IWRAM frame pairs, not decoded or semantic UI state. `tools/android/sanitize-modern-emerald-qa-memory.py` reproduces the asset only from the reviewed source dump with SHA-256 `40958796e0acd76bac20aef3c484d451685fffa255c45a5eec57df6a0511f5a5`. The script removes capture IDs and timestamps, replaces player/Pokémon/OT/box display names with fixed QA values, replaces the trainer ID, clears known social/free-text save regions, and verifies that collected original fixed-width sensitive fields do not remain outside protected encrypted Pokémon records. The EWRAM/IWRAM geometry, pointers, encrypted Pokémon payloads/checksums, and runtime map, clock, battle, party, and specimen numeric state remain raw.

The asset belongs only to `src/debug`; release-source and built-APK isolation tests reject it from release artifacts. The generation input remains external and must never be committed.

## Match the Thor lower-screen app area

The physical lower display is `1240×1080` at `369 dpi` with a `55 px` bottom navigation region and no top status bar. Run the activity on a secondary emulator display sized to the resulting `1240×1025` app area. Do not use the emulator's primary display: its top status bar removes another `55 px` and produces the wrong WebView height.

Configure the dedicated AVD's physical display and font scale, then create the secondary app-area display:

```powershell
$serial = 'emulator-5566'
adb -s $serial shell wm size 1240x1080
adb -s $serial shell wm density 369
adb -s $serial shell settings put system font_scale 0.95
adb -s $serial shell settings put global overlay_display_devices '1240x1025/369'

$displays = (adb -s $serial shell dumpsys window displays) -join "`n"
$blocks = @([regex]::Matches(
    $displays,
    '(?ms)^  Display: mDisplayId=(\d+).*?(?=^  Display: mDisplayId=|\z)'
) | Where-Object { $_.Value -match 'init=1240x1025 369dpi' })
if ($blocks.Count -ne 1) { throw "Expected one Thor app-area display, found $($blocks.Count)" }
$displayId = $blocks[0].Groups[1].Value
if ($blocks[0].Value -notmatch 'cur=1240x1025 app=1240x1025') {
    throw 'Thor app-area display bounds do not match'
}
if ((adb -s $serial shell settings get system font_scale).Trim() -ne '0.95') {
    throw 'Thor font scale does not match'
}
```

Record `$displayId`. After enabling the marker in the next section, launch on that display with:

```powershell
adb -s $serial shell am start --display $displayId -n 'com.darkaxt.dualdex.debug/com.darkaxt.dualdex.MainActivity'
```

After forwarding WebView DevTools as described below, the packaged page must report these metrics before screenshots or usability findings are accepted:

```text
innerWidth=538
innerHeight=445
devicePixelRatio=2.3062500953674316
visualViewport.width≈538.103
visualViewport.height≈445.312
orientation=landscape
(max-width: 680px)=true
```

## Enable before launch

Install the debug APK, then create the internal marker before the first launch:

```powershell
$package = 'com.darkaxt.dualdex.debug'
$apk = 'D:\Temp\dualdex-qa-hardening\app\build\outputs\apk\debug\app-debug.apk'
adb -s $serial install -r $apk
adb -s $serial shell am force-stop $package
adb -s $serial shell run-as $package mkdir -p files
adb -s $serial shell run-as $package touch files/retroarch-free-ui-qa
```

Launch the exact production activity:

```powershell
adb -s $serial shell am start --display $displayId -n 'com.darkaxt.dualdex.debug/com.darkaxt.dualdex.MainActivity'
```

To restore ordinary real-RetroArch debug behavior, stop the app and remove the marker before relaunching:

```powershell
adb -s $serial shell am force-stop $package
adb -s $serial shell run-as $package rm -f files/retroarch-free-ui-qa
adb -s $serial shell am start --display $displayId -n 'com.darkaxt.dualdex.debug/com.darkaxt.dualdex.MainActivity'
```

## Discover the dynamic WebView origin

The app binds its HTTP server to a dynamic device-loopback port. After launch, inspect the debuggable WebView target rather than assuming a port:

```powershell
$appPid = (adb -s $serial shell pidof -s $package).Trim()
adb -s $serial forward tcp:9222 "localabstract:webview_devtools_remote_$appPid"
$targets = @(Invoke-RestMethod 'http://127.0.0.1:9222/json')
$target = $targets | Where-Object { $_.url -match '^http://127\.0\.0\.1:\d+/' } | Select-Object -First 1
if ($null -eq $target) { throw 'DualDex WebView loopback target was not found' }
$deviceOrigin = ([Uri]$target.url).GetLeftPart([UriPartial]::Authority)
$devicePort = ([Uri]$deviceOrigin).Port
$hostPort = (adb -s $serial forward tcp:0 "tcp:$devicePort").Trim()
$hostOrigin = "http://127.0.0.1:$hostPort"
$deviceOrigin
$hostOrigin
```

`$deviceOrigin` is the exact origin loaded by the production `DualDexWebView`. `$hostOrigin` is the host-side ADB forwarding endpoint for HTTP requests.

## Index exact Modern Emerald through the production path

Choose the externally held Modern Emerald v3.5 ROM used for the sanitized capture. It must remain outside the repository and APK. Verify the file before making it available to the emulator:

```powershell
$rom = Get-Item 'D:\path\Modern Emerald (v3.5).gba'
$expectedSha256 = '21A0306C4E5B5DC15CA70B74E713E3140612C1045AA298072993A6C5DD8D6895'
if ((Get-FileHash -Algorithm SHA256 $rom.FullName).Hash -ne $expectedSha256) {
    throw 'Modern Emerald ROM SHA-256 does not match the QA scenario authority'
}

$deviceRom = '/sdcard/ROMs/Modern Emerald (v3.5).gba'
adb -s $serial shell mkdir -p /sdcard/ROMs
adb -s $serial push $rom.FullName $deviceRom
adb -s $serial shell appops set $package MANAGE_EXTERNAL_STORAGE allow
adb -s $serial shell am force-stop $package
adb -s $serial shell am start --display $displayId -n `
  'com.darkaxt.dualdex.debug/com.darkaxt.dualdex.MainActivity'
```

Repeat **Discover the dynamic WebView origin** because the process restart changes the loopback port. The all-files grant is an emulator-only equivalent of the production storage grant; `RetroArchSetupCoordinator` must discover the external ROM, index it by CRC32/SHA-256, and activate it through the production session authority. `POST /api/load` alone loads a catalog but does not grant or index a ROM for session authority, so it is not sufficient for this checkpoint.

The expected ROM CRC32 is `8C7DBECA`. Accept the checkpoint only when `GET /api/state` reports `catalogReady=true`, `gameAccessReady=true`, `retroArch.resolution=ACTIVE`, `retroArch.activeSource="Modern Emerald (v3.5).gba"`, and the exact SHA-256 above. No ROM is bundled, copied into source, or encoded in the debug application. Remove the staged emulator ROM before saving the final AVD snapshot.

A clean Task #288 reinstall exposed a QA provisioning limit that the earlier warm catalog cache had masked: the stock AVD `dalvik.vm.heapgrowthlimit=192m` reproducibly terminates first-time Modern Emerald catalog preparation. Task #294 tracks a debug-only repeatable solution that must leave release behavior unchanged. The measured usability run temporarily used `512m`, restarted the owned Android runtime, completed the catalog once, and restored `192m` before snapshotting. Do not treat a warm-cache success as cold-start acceptance; see [the Thor usability analysis](thor-emulator-usability-analysis-2026-08-30.md).

## Control the raw-memory timeline

The simulator starts paused on `overworld-1`. Open the debug-native control activity on the exact app-area display:

```powershell
adb -s $serial shell am start --display $displayId -n `
  'com.darkaxt.dualdex.debug/com.darkaxt.dualdex.RawLiveMemoryControlActivity'
```

The controller is owned by `RetroArchFreeUiQaApplication`, so pause/play/step/scenario state survives control-activity recreation. **Pause** freezes the current raw frame, **Play** advances one frame per second, and **Step** advances exactly one frame and pauses. **Open companion** returns to the production `MainActivity`; Back returns to the controls.

Available scenarios are:

- `modern-normal`: six sanitized full EWRAM/IWRAM frames from overworld through battle and back to overworld.
- `modern-unreadable`: every EWRAM read is rejected.
- `modern-partial`: matching EWRAM reads return one byte short.
- `modern-malformed`: matching EWRAM reads return malformed wire data.
- `stale-identity`: raw memory remains available, but the advertised basename and CRC no longer match the indexed ROM.

Scenario changes reset to frame zero and pause playback. Existing command endpoints receive one `CONTENTLESS` boundary; existing memory endpoints permanently reject reads so old and new scenario chunks cannot mix. Newly opened endpoints read the selected scenario. Faulted runtime modules must disable themselves without crashing the app. Select `modern-normal` to return to the valid identity and raw timeline.

When the device session is complete, remove the forwards:

```powershell
adb -s $serial forward --remove tcp:9222
adb -s $serial forward --remove "tcp:$hostPort"
```
