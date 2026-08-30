$ErrorActionPreference = 'Stop'

$toolRoot = $PSScriptRoot
$required = @(
    'create-dualdex-avd.ps1',
    'start-dualdex-avd.ps1',
    'resolve-dualdex-device.ps1',
    'install-debug.ps1',
    'validate-signed-candidate.ps1'
)

foreach ($name in $required) {
    $path = Join-Path $toolRoot $name
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing Android tool: $name"
    }
}

. (Join-Path $toolRoot 'resolve-dualdex-device.ps1')

$selected = Select-DualDexSerial -Candidates @(
    [pscustomobject]@{ Serial = 'emulator-5554'; AvdName = 'NavicReaderLab' },
    [pscustomobject]@{ Serial = 'emulator-5562'; AvdName = 'DualDex_RA_API35' },
    [pscustomobject]@{ Serial = 'R52W60CFTRL'; AvdName = $null }
) -AvdName 'DualDex_RA_API35'

if ($selected -ne 'emulator-5562') {
    throw "Expected emulator-5562, got $selected"
}

try {
    Select-DualDexSerial -Candidates @() -AvdName 'DualDex_RA_API35'
    throw 'Missing AVD selection did not fail'
} catch {
    if ($_.Exception.Message -notmatch 'not running') { throw }
}

try {
    Select-DualDexSerial -Candidates @(
        [pscustomobject]@{ Serial = 'emulator-5562'; AvdName = 'DualDex_RA_API35' },
        [pscustomobject]@{ Serial = 'emulator-5564'; AvdName = 'DualDex_RA_API35' }
    ) -AvdName 'DualDex_RA_API35'
    throw 'Duplicate AVD selection did not fail'
} catch {
    if ($_.Exception.Message -notmatch 'Multiple') { throw }
}

$installText = Get-Content -Raw (Join-Path $toolRoot 'install-debug.ps1')
if ($installText -notmatch '\.\s+\(Join-Path\s+\$PSScriptRoot\s+''resolve-dualdex-device\.ps1''\)\s+-AvdName\s+\$AvdName\s+-AdbPath\s+\$adb') {
    throw 'install-debug.ps1 must preserve caller-selected AVD parameters while dot-sourcing the resolver'
}
if ($installText -match '(?m)^\s*&\s*\$adb\s+(?!-s\b)') {
    throw 'install-debug.ps1 contains an adb invocation without -s'
}

$startText = Get-Content -Raw (Join-Path $toolRoot 'start-dualdex-avd.ps1')
if ($startText -notmatch '\.\s+\$resolver\s+-AvdName\s+\$AvdName\s+-AdbPath\s+\$adb') {
    throw 'start-dualdex-avd.ps1 must preserve caller-selected AVD parameters while dot-sourcing the resolver'
}
if ($startText -match 'emulator-5554') {
    throw 'start-dualdex-avd.ps1 must not target the existing emulator serial'
}
if ($startText -match '\bget-state\b' -or $startText -notmatch '&\s+\$adb\s+devices') {
    throw 'start-dualdex-avd.ps1 must enumerate adb devices without failing while its serial is still offline'
}
if ($startText -notmatch 'sys\.boot_completed') {
    throw 'start-dualdex-avd.ps1 must wait for Android boot completion'
}

$candidateText = Get-Content -Raw (Join-Path $toolRoot 'validate-signed-candidate.ps1')
foreach ($requiredCandidatePattern in @(
    'Get-FileHash',
    'apksigner(?:\.bat)?\s+verify\s+--verbose\s+--print-certs',
    '\(\?:Signer #\[0-9\]\+\|V\[0-9\.\]\+ Signer\):\?',
    'dualdex-release-cert\.sha256',
    'com\.darkaxt\.dualdex',
    'Resolve-DualDexDevice',
    "ValidateSet\('DedicatedAvd',\s*'Thor'\)",
    '\$Install\.IsPresent'
)) {
    if ($candidateText -notmatch $requiredCandidatePattern) {
        throw "validate-signed-candidate.ps1 is missing policy pattern: $requiredCandidatePattern"
    }
}
if ($candidateText -notmatch '\.\s+\(Join-Path\s+\$PSScriptRoot\s+''resolve-dualdex-device\.ps1''\)\s+-AvdName\s+\$AvdName\s+-AdbPath\s+\$adb') {
    throw 'validate-signed-candidate.ps1 must preserve caller-selected AVD parameters while dot-sourcing the resolver'
}
if ($candidateText -match 'com\.darkaxt\.dualdex\.debug') {
    throw 'Signed candidate validation must never target the debug package'
}
if ($candidateText -match '(?m)^\s*&\s*\$adb\s+(?!-s\b)') {
    throw 'validate-signed-candidate.ps1 contains an adb invocation without -s'
}
if ($candidateText -match '(?m)^\s*&\s*\$adb\s+-s\s+[^\r\n]+\sinstall\s+[^\r\n]*(?:^|\s)-d(?:\s|$)') {
    throw 'Signed candidate installation must not permit version downgrade'
}

Write-Output 'DualDex Android tool tests passed.'
