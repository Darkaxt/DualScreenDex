[CmdletBinding()]
param(
    [string]$AvdName = 'DualDex_RA_API35',
    [string]$AvdHome = 'D:\Android\avd',
    [string]$SdkRoot = 'C:\Users\darka\AppData\Local\Android\Sdk',
    [string]$SystemImage = 'system-images;android-35;google_apis;x86_64',
    [string]$DeviceProfile = 'pixel_tablet'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$avdManager = Join-Path $SdkRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$systemImagePath = Join-Path $SdkRoot ($SystemImage.Replace(';', '\'))
if (-not (Test-Path -LiteralPath $avdManager -PathType Leaf)) {
    throw "avdmanager not found at '$avdManager'."
}
if (-not (Test-Path -LiteralPath $systemImagePath -PathType Container)) {
    throw "Android system image '$SystemImage' is not installed."
}

New-Item -ItemType Directory -Path $AvdHome -Force | Out-Null
$avdDirectory = Join-Path $AvdHome "$AvdName.avd"
$avdIni = Join-Path $AvdHome "$AvdName.ini"

if ((Test-Path -LiteralPath $avdDirectory) -or (Test-Path -LiteralPath $avdIni)) {
    if ((Test-Path -LiteralPath (Join-Path $avdDirectory 'config.ini') -PathType Leaf) -and
        (Test-Path -LiteralPath $avdIni -PathType Leaf)) {
        Write-Output "DualDex AVD already exists at '$avdDirectory'."
        exit 0
    }
    throw "Partial AVD state exists for '$AvdName' in '$AvdHome'; refusing to overwrite it."
}

$previousAvdHome = $env:ANDROID_AVD_HOME
try {
    $env:ANDROID_AVD_HOME = $AvdHome
    'no' | & $avdManager create avd --name $AvdName --package $SystemImage --device $DeviceProfile
    if ($LASTEXITCODE -ne 0) {
        throw "avdmanager failed with exit code $LASTEXITCODE."
    }
} finally {
    $env:ANDROID_AVD_HOME = $previousAvdHome
}

if (-not (Test-Path -LiteralPath (Join-Path $avdDirectory 'config.ini') -PathType Leaf)) {
    throw "AVD creation did not produce '$avdDirectory\config.ini'."
}

Write-Output "Created '$AvdName' at '$avdDirectory'."
