[CmdletBinding()]
param(
    [string]$AvdName = 'DualDex_RA_API35',
    [string]$AdbPath = 'C:\Users\darka\AppData\Local\Android\Sdk\platform-tools\adb.exe'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Select-DualDexSerial {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [AllowEmptyCollection()] [object[]]$Candidates,
        [Parameter(Mandatory)] [string]$AvdName
    )

    $matches = @($Candidates | Where-Object { $_.AvdName -eq $AvdName })
    if ($matches.Count -eq 0) {
        throw "DualDex AVD '$AvdName' is not running."
    }
    if ($matches.Count -gt 1) {
        throw "Multiple running emulators report the DualDex AVD name '$AvdName'."
    }
    return [string]$matches[0].Serial
}

function Get-RunningAndroidDevices {
    [CmdletBinding()]
    param([Parameter(Mandatory)] [string]$AdbPath)

    if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
        throw "adb not found at '$AdbPath'."
    }

    $devices = @()
    foreach ($line in (& $AdbPath devices)) {
        if ($line -notmatch '^(\S+)\s+device$') { continue }
        $serial = $Matches[1]
        $avd = $null
        if ($serial -like 'emulator-*') {
            $nameOutput = @(& $AdbPath -s $serial emu avd name 2>$null)
            $avd = ($nameOutput | Where-Object { $_ -and $_ -ne 'OK' } | Select-Object -First 1).Trim()
        }
        $devices += [pscustomobject]@{ Serial = $serial; AvdName = $avd }
    }
    return $devices
}

function Resolve-DualDexDevice {
    [CmdletBinding()]
    param(
        [string]$AvdName = 'DualDex_RA_API35',
        [string]$AdbPath = 'C:\Users\darka\AppData\Local\Android\Sdk\platform-tools\adb.exe'
    )

    return Select-DualDexSerial -Candidates @(Get-RunningAndroidDevices -AdbPath $AdbPath) -AvdName $AvdName
}

if ($MyInvocation.InvocationName -ne '.') {
    Resolve-DualDexDevice -AvdName $AvdName -AdbPath $AdbPath
}
