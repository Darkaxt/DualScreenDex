[CmdletBinding()]
param(
    [Parameter(Mandatory)] [string]$ApkPath,
    [Parameter(Mandatory)] [string]$ExpectedSha256,
    [Parameter(Mandatory)] [string]$ExpectedVersionName,
    [Parameter(Mandatory)] [int]$ExpectedVersionCode,
    [ValidateSet('DedicatedAvd', 'Thor')] [string]$Target = 'DedicatedAvd',
    [switch]$Install,
    [string]$AvdName = 'DualDex_RA_API35',
    [string]$ThorSerial = 'bfa98654',
    [string]$SdkRoot = 'C:\Users\darka\AppData\Local\Android\Sdk',
    [string]$ExpectedCertificatePath = (Join-Path $PSScriptRoot '..\..\signing\dualdex-release-cert.sha256')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Normalize-Sha256 {
    param([Parameter(Mandatory)] [string]$Value, [Parameter(Mandatory)] [string]$Description)

    $normalized = ($Value -replace '[:\s]', '').ToUpperInvariant()
    if ($normalized -notmatch '^[A-F0-9]{64}$') {
        throw "$Description is not a SHA-256 value."
    }
    return $normalized
}

$resolvedApk = (Resolve-Path -LiteralPath $ApkPath -ErrorAction Stop).Path
$resolvedCertificate = (Resolve-Path -LiteralPath $ExpectedCertificatePath -ErrorAction Stop).Path
$expectedApkSha256 = Normalize-Sha256 -Value $ExpectedSha256 -Description 'Expected APK hash'
$expectedCertificateSha256 = Normalize-Sha256 `
    -Value (Get-Content -LiteralPath $resolvedCertificate -Raw) `
    -Description 'Pinned certificate fingerprint'

$actualApkSha256 = (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToUpperInvariant()
if ($actualApkSha256 -ne $expectedApkSha256) {
    throw "APK hash mismatch. Expected $expectedApkSha256, got $actualApkSha256."
}

$buildToolsRoot = Join-Path $SdkRoot 'build-tools'
$buildTools = Get-ChildItem -LiteralPath $buildToolsRoot -Directory |
    Sort-Object { [version]$_.Name } -Descending |
    Select-Object -First 1
if ($null -eq $buildTools) {
    throw "No Android build tools found under '$buildToolsRoot'."
}

$apksignerPath = Join-Path $buildTools.FullName 'apksigner.bat'
$aaptPath = Join-Path $buildTools.FullName 'aapt.exe'
foreach ($tool in @($apksignerPath, $aaptPath)) {
    if (-not (Test-Path -LiteralPath $tool -PathType Leaf)) {
        throw "Required Android build tool is missing: $tool"
    }
}
Set-Alias -Name apksigner -Value $apksignerPath -Scope Local

$verification = @(apksigner verify --verbose --print-certs $resolvedApk 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "APK signature verification failed: $($verification -join [Environment]::NewLine)"
}

$actualCertificateSha256 = $null
foreach ($line in $verification) {
    if ([string]$line -match '^(?:Signer #[0-9]+|V[0-9.]+ Signer):? certificate SHA-256 digest:\s*([A-Fa-f0-9:]+)\s*$') {
        $actualCertificateSha256 = Normalize-Sha256 -Value $Matches[1] -Description 'APK signer fingerprint'
        break
    }
}
if ($null -eq $actualCertificateSha256) {
    throw 'apksigner did not report a signer certificate fingerprint.'
}
if ($actualCertificateSha256 -ne $expectedCertificateSha256) {
    throw "APK signer mismatch. Expected $expectedCertificateSha256, got $actualCertificateSha256."
}

$badging = & $aaptPath dump badging $resolvedApk | Select-Object -First 1
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($badging)) {
    throw 'aapt could not read the signed APK identity.'
}
$packageMatch = [regex]::Match(
    [string]$badging,
    "^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'"
)
if (-not $packageMatch.Success) {
    throw "Unexpected aapt package output: $badging"
}

$applicationId = $packageMatch.Groups[1].Value
$versionCode = [int]$packageMatch.Groups[2].Value
$versionName = $packageMatch.Groups[3].Value
if ($applicationId -ne 'com.darkaxt.dualdex') {
    throw "Unexpected production application ID: $applicationId"
}
if ($versionCode -ne $ExpectedVersionCode -or $versionName -ne $ExpectedVersionName) {
    throw "Unexpected APK version: $versionName ($versionCode)."
}

$serial = $null
if ($Install.IsPresent) {
    $adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adb -PathType Leaf)) {
        throw "adb not found at '$adb'."
    }

    if ($Target -eq 'DedicatedAvd') {
        . (Join-Path $PSScriptRoot 'resolve-dualdex-device.ps1')
        $serial = Resolve-DualDexDevice -AvdName $AvdName -AdbPath $adb
    } else {
        $deviceState = (& $adb -s $ThorSerial get-state 2>$null).Trim()
        if ($LASTEXITCODE -ne 0 -or $deviceState -ne 'device') {
            throw "Thor '$ThorSerial' is not connected and authorized."
        }
        $model = (& $adb -s $ThorSerial shell getprop ro.product.model).Trim()
        if ($LASTEXITCODE -ne 0 -or $model -notmatch '(?i)thor') {
            throw "Device '$ThorSerial' does not identify as an AYN Thor (model '$model')."
        }
        $serial = $ThorSerial
    }

    $installOutput = @(& $adb -s $serial install -r $resolvedApk 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Signed APK installation failed on '$serial': $($installOutput -join [Environment]::NewLine)"
    }

    $packagePath = & $adb -s $serial shell pm path com.darkaxt.dualdex
    if ($LASTEXITCODE -ne 0 -or $packagePath -notmatch '^package:') {
        throw "com.darkaxt.dualdex was not found after installation on '$serial'."
    }
    $packageDump = @(& $adb -s $serial shell dumpsys package com.darkaxt.dualdex)
    $installedVersionCode = ($packageDump | Select-String -Pattern 'versionCode=(\d+)' | Select-Object -First 1).Matches.Groups[1].Value
    $installedVersionName = ($packageDump | Select-String -Pattern 'versionName=([^\s]+)' | Select-Object -First 1).Matches.Groups[1].Value
    if ([int]$installedVersionCode -ne $ExpectedVersionCode -or $installedVersionName -ne $ExpectedVersionName) {
        throw "Installed package identity did not match the validated APK on '$serial'."
    }
}

[pscustomobject]@{
    ApplicationId = $applicationId
    VersionName = $versionName
    VersionCode = $versionCode
    ApkSha256 = $actualApkSha256
    CertificateSha256 = $actualCertificateSha256
    Installed = $Install.IsPresent
    TargetSerial = $serial
}
