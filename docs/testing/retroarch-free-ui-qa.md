# RetroArch-free UI QA checkpoint

This debug-only checkpoint launches the production `MainActivity`, `DualDexApplication` runtime, `AndroidLoopbackServer`, `DualDexWebView`, parser, and bundled production web UI without requiring RetroArch. It does not provide raw live-memory scenarios; the inert status source remains `CONTENTLESS` until that follow-up work lands.

The package remains `com.darkaxt.dualdex.debug`. With no marker file, the APK keeps ordinary debug behavior and uses the production RetroArch UDP session monitor.

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

## Upload a real ROM through the production path

Choose a ROM outside the repository and APK. The file path and upload name are supplied only at execution time:

```powershell
$rom = Get-Item 'D:\path\chosen-game.gba'
$uploadName = [Uri]::EscapeDataString($rom.Name)
curl.exe --fail-with-body --request POST `
  --header 'Content-Type: application/octet-stream' `
  --data-binary "@$($rom.FullName)" `
  "$hostOrigin/api/load?name=$uploadName"
```

This request enters the existing `POST /api/load` handler, spools the request body, invokes `RomSourceLoader`, and loads the result through the production companion runtime and `CatalogParser`. No ROM is bundled, copied into source, or encoded in the debug application.

When the device session is complete, remove the forwards:

```powershell
adb -s $serial forward --remove tcp:9222
adb -s $serial forward --remove "tcp:$hostPort"
```
