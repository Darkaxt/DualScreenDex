# RetroArch-free UI QA checkpoint

This debug-only checkpoint launches the production `MainActivity`, `DualDexApplication` runtime, `AndroidLoopbackServer`, `DualDexWebView`, parser, and bundled production web UI without requiring RetroArch. It does not provide raw live-memory scenarios; the inert status source remains `CONTENTLESS` until that follow-up work lands.

The package remains `com.darkaxt.dualdex.debug`. With no marker file, the APK keeps ordinary debug behavior and uses the production RetroArch UDP session monitor.

## Enable before launch

Install the debug APK, then create the internal marker before the first launch:

```powershell
$package = 'com.darkaxt.dualdex.debug'
$apk = 'D:\Temp\dualdex-qa-hardening\app\build\outputs\apk\debug\app-debug.apk'
adb install -r $apk
adb shell am force-stop $package
adb shell run-as $package mkdir -p files
adb shell run-as $package touch files/retroarch-free-ui-qa
```

Launch the exact production activity:

```powershell
adb shell am start -n 'com.darkaxt.dualdex.debug/com.darkaxt.dualdex.MainActivity'
```

To restore ordinary real-RetroArch debug behavior, stop the app and remove the marker before relaunching:

```powershell
adb shell am force-stop $package
adb shell run-as $package rm -f files/retroarch-free-ui-qa
adb shell am start -n 'com.darkaxt.dualdex.debug/com.darkaxt.dualdex.MainActivity'
```

## Discover the dynamic WebView origin

The app binds its HTTP server to a dynamic device-loopback port. After launch, inspect the debuggable WebView target rather than assuming a port:

```powershell
$appPid = (adb shell pidof -s $package).Trim()
adb forward tcp:9222 "localabstract:webview_devtools_remote_$appPid"
$targets = @(Invoke-RestMethod 'http://127.0.0.1:9222/json')
$target = $targets | Where-Object { $_.url -match '^http://127\.0\.0\.1:\d+/' } | Select-Object -First 1
if ($null -eq $target) { throw 'DualDex WebView loopback target was not found' }
$deviceOrigin = ([Uri]$target.url).GetLeftPart([UriPartial]::Authority)
$devicePort = ([Uri]$deviceOrigin).Port
$hostPort = (adb forward tcp:0 "tcp:$devicePort").Trim()
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
adb forward --remove tcp:9222
adb forward --remove "tcp:$hostPort"
```
