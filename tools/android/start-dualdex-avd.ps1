[CmdletBinding()]
param(
    [string]$AvdName = 'DualDex_RA_API35',
    [string]$AvdHome = 'D:\Android\avd',
    [string]$SdkRoot = 'C:\Users\darka\AppData\Local\Android\Sdk'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$emulator = Join-Path $SdkRoot 'emulator\emulator.exe'
$resolver = Join-Path $PSScriptRoot 'resolve-dualdex-device.ps1'
. $resolver

$serial = $null
$process = $null
try {
    $serial = Resolve-DualDexDevice -AvdName $AvdName -AdbPath $adb
} catch {
    if ($_.Exception.Message -notmatch 'not running') { throw }
}

if ($null -eq $serial) {
    $avdDirectory = Join-Path $AvdHome "$AvdName.avd"
    if (-not (Test-Path -LiteralPath (Join-Path $avdDirectory 'config.ini') -PathType Leaf)) {
        throw "DualDex AVD '$AvdName' does not exist in '$AvdHome'."
    }
    if (-not (Test-Path -LiteralPath $emulator -PathType Leaf)) {
        throw "Android emulator not found at '$emulator'."
    }

    $activePorts = @(
        & $adb devices |
            Where-Object { $_ -match '^emulator-(\d+)\s+' } |
            ForEach-Object { [int]$Matches[1] }
    )
    $port = 5556
    while ($activePorts -contains $port) { $port += 2 }
    $serial = "emulator-$port"

    $previousAvdHome = $env:ANDROID_AVD_HOME
    try {
        $env:ANDROID_AVD_HOME = $AvdHome
        $process = Start-Process -FilePath $emulator -ArgumentList @(
            '-avd', $AvdName,
            '-port', $port,
            '-no-boot-anim'
        ) -WindowStyle Hidden -PassThru
    } finally {
        $env:ANDROID_AVD_HOME = $previousAvdHome
    }
}

$heartbeat = 0
while ($true) {
    if ($null -ne $process -and $process.HasExited) {
        throw "DualDex emulator exited with code $($process.ExitCode) before becoming ready."
    }
    $state = (& $adb -s $serial get-state 2>$null | Select-Object -First 1)
    if ($state -eq 'device') {
        $bootComplete = (& $adb -s $serial shell getprop sys.boot_completed 2>$null | Select-Object -First 1).Trim()
        if ($bootComplete -eq '1') {
            $reported = @(& $adb -s $serial emu avd name 2>$null) |
                Where-Object { $_ -and $_ -ne 'OK' } |
                Select-Object -First 1
            if ($reported.Trim() -ne $AvdName) {
                throw "Serial '$serial' reports AVD '$reported', expected '$AvdName'."
            }
            Write-Output $serial
            exit 0
        }
    }
    if (($heartbeat % 5) -eq 0) {
        Write-Output "Waiting for $AvdName Android services on $serial..."
    }
    $heartbeat++
    Start-Sleep -Seconds 2
}
