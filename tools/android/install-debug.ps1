[CmdletBinding()]
param(
    [string]$ApkPath = (Join-Path $PSScriptRoot '..\..\app\build\outputs\apk\debug\app-debug.apk'),
    [string]$AvdName = 'DualDex_RA_API35',
    [string]$SdkRoot = 'C:\Users\darka\AppData\Local\Android\Sdk'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
. (Join-Path $PSScriptRoot 'resolve-dualdex-device.ps1')
$serial = Resolve-DualDexDevice -AvdName $AvdName -AdbPath $adb
$resolvedApk = (Resolve-Path -LiteralPath $ApkPath).Path

& $adb -s $serial install -r -d $resolvedApk
if ($LASTEXITCODE -ne 0) {
    throw "Debug APK installation failed on '$serial'."
}

$packagePath = & $adb -s $serial shell pm path com.darkaxt.dualdex.debug
if ($packagePath -notmatch '^package:') {
    throw "com.darkaxt.dualdex.debug was not found after installation on '$serial'."
}

Write-Output "Installed com.darkaxt.dualdex.debug on $serial."
