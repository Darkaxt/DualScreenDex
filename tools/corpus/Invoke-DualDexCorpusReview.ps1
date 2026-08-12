[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $RomManifest,

    [Parameter(Mandatory = $true)]
    [string] $WorkRoot,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int] $ApkVersionCode,

    [string] $DecisionsPath,

    [string] $CacheRoot,

    [string] $ParserCliPath,

    [switch] $Rebaseline,

    [ValidateRange(1, 2147483647)]
    [int] $MaximumIndex = 2147483647,

    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8
. (Join-Path $PSScriptRoot 'CorpusReviewPolicy.ps1')

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$manifestPath = [System.IO.Path]::GetFullPath($RomManifest)
$workPath = [System.IO.Path]::GetFullPath($WorkRoot)
if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    throw "ROM manifest does not exist: $manifestPath"
}
if ($workPath -eq [System.IO.Path]::GetPathRoot($workPath)) {
    throw "WorkRoot must not be a drive root: $workPath"
}

$reviewRoot = Join-Path $workPath 'review'
$cacheRoot = if ($CacheRoot) {
    [System.IO.Path]::GetFullPath($CacheRoot)
} else {
    Join-Path $workPath 'catalog-cache'
}
if ($cacheRoot -eq [System.IO.Path]::GetPathRoot($cacheRoot)) {
    throw "CacheRoot must not be a drive root: $cacheRoot"
}
$statePath = Join-Path $reviewRoot 'review-state.json'
$pendingPath = Join-Path $reviewRoot 'pending-review.json'
$pendingReportJson = Join-Path $reviewRoot 'pending-parser-report.json'
$pendingReportMarkdown = Join-Path $reviewRoot 'pending-parser-report.md'
$completePath = Join-Path $reviewRoot 'review-complete.json'
$resultsPath = Join-Path $reviewRoot 'review-results.json'
$baselinePath = Join-Path $reviewRoot 'review-baseline.json'
$deltasPath = Join-Path $reviewRoot 'review-deltas.json'
$deltasMarkdownPath = Join-Path $reviewRoot 'review-deltas.md'
$deltaDecisionsPath = Join-Path $reviewRoot 'review-delta-decisions.json'
if (-not $DecisionsPath) {
    $DecisionsPath = Join-Path $reviewRoot 'review-decisions.json'
}
$decisionsFullPath = [System.IO.Path]::GetFullPath($DecisionsPath)
[System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($cacheRoot) | Out-Null

function Write-JsonFile([string] $path, [object] $value) {
    $json = ConvertTo-Json -InputObject $value -Depth 32
    $directory = [System.IO.Path]::GetDirectoryName($path)
    $temporaryPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    $backupPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    try {
        [System.IO.File]::WriteAllText($temporaryPath, $json + [Environment]::NewLine, $utf8)
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            [System.IO.File]::Replace($temporaryPath, $path, $backupPath)
            [System.IO.File]::Delete($backupPath)
        } else {
            [System.IO.File]::Move($temporaryPath, $path)
        }
    } finally {
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            [System.IO.File]::Delete($temporaryPath)
        }
        if (Test-Path -LiteralPath $backupPath -PathType Leaf) {
            [System.IO.File]::Delete($backupPath)
        }
    }
}

function Write-TextFile([string] $path, [string] $value) {
    $directory = [System.IO.Path]::GetDirectoryName($path)
    $temporaryPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    $backupPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    try {
        [System.IO.File]::WriteAllText($temporaryPath, $value, $utf8)
        if (Test-Path -LiteralPath $path -PathType Leaf) {
            [System.IO.File]::Replace($temporaryPath, $path, $backupPath)
            [System.IO.File]::Delete($backupPath)
        } else {
            [System.IO.File]::Move($temporaryPath, $path)
        }
    } finally {
        if (Test-Path -LiteralPath $temporaryPath -PathType Leaf) {
            [System.IO.File]::Delete($temporaryPath)
        }
        if (Test-Path -LiteralPath $backupPath -PathType Leaf) {
            [System.IO.File]::Delete($backupPath)
        }
    }
}

function Test-DualDexApkVersionCodeMatch {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Record,

        [Parameter(Mandatory = $true)]
        [int] $ApkVersionCode
    )

    $recordVersion = $Record.PSObject.Properties['apkVersionCode']?.Value
    if ($null -eq $recordVersion) {
        return $false
    }
    $parsedVersion = 0L
    return [long]::TryParse([string] $recordVersion, [ref] $parsedVersion) -and
        $parsedVersion -eq [long] $ApkVersionCode
}

function Test-DualDexReviewDecisionApplicable {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Decision,

        [Parameter(Mandatory = $true)]
        [object] $ReviewResult,

        [Parameter(Mandatory = $true)]
        [int] $ApkVersionCode
    )

    $decisionVersion = $Decision.PSObject.Properties['apkVersionCode']?.Value
    $reviewVersion = $ReviewResult.PSObject.Properties['apkVersionCode']?.Value
    $parsedDecisionVersion = 0L
    $parsedReviewVersion = 0L
    return $null -ne $decisionVersion -and
        $null -ne $reviewVersion -and
        [long]::TryParse([string] $decisionVersion, [ref] $parsedDecisionVersion) -and
        [long]::TryParse([string] $reviewVersion, [ref] $parsedReviewVersion) -and
        [string]::Equals([string] $Decision.romSha256, [string] $ReviewResult.romSha256, [System.StringComparison]::OrdinalIgnoreCase) -and
        $parsedDecisionVersion -eq [long] $ApkVersionCode -and
        $parsedReviewVersion -eq [long] $ApkVersionCode
}

function Get-DualDexValidatedManifestEntry {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Entry,

        [Parameter(Mandatory = $true)]
        [int] $Index
    )

    $sha = [string] $Entry.PSObject.Properties['RomSha256']?.Value
    if ($sha -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Manifest ROM SHA-256 is invalid at index $Index`: '$sha'"
    }
    $extractedPath = [string] $Entry.PSObject.Properties['ExtractedPath']?.Value
    if ([string]::IsNullOrWhiteSpace($extractedPath)) {
        throw "Manifest ExtractedPath is missing at index $Index for $sha"
    }
    $romPath = [System.IO.Path]::GetFullPath($extractedPath)
    if (-not (Test-Path -LiteralPath $romPath -PathType Leaf)) {
        throw "Extracted ROM is missing at index $Index`: $romPath"
    }
    $expectedBytesValue = $Entry.PSObject.Properties['Bytes']?.Value
    $expectedBytes = 0L
    if ($null -eq $expectedBytesValue -or
        -not [long]::TryParse([string] $expectedBytesValue, [ref] $expectedBytes) -or
        $expectedBytes -lt 0) {
        throw "Manifest byte size is invalid at index $Index for $sha"
    }
    $actual = Get-Item -LiteralPath $romPath
    if ($actual.Length -ne $expectedBytes) {
        throw "Extracted ROM size changed at index $Index`: expected $expectedBytes, found $($actual.Length): $romPath"
    }
    $canonicalSha = $sha.ToLowerInvariant()
    $actualSha = (Get-FileHash -LiteralPath $romPath -Algorithm SHA256).Hash.ToLowerInvariant()
    if (-not [string]::Equals($actualSha, $canonicalSha, [System.StringComparison]::Ordinal)) {
        throw "Extracted ROM SHA-256 changed at index $Index`: expected $canonicalSha, found $actualSha`: $romPath"
    }

    $normalized = [ordered]@{}
    foreach ($property in $Entry.PSObject.Properties) {
        $normalized[$property.Name] = $property.Value
    }
    $normalized['RomSha256'] = $canonicalSha
    $normalized['ExtractedPath'] = $romPath
    $normalized['Bytes'] = $expectedBytes
    return [pscustomobject] $normalized
}

function Get-DualDexCompatibilityPercent {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Result
    )

    $percentProperty = $Result.PSObject.Properties['compatibilityPercent']
    if ($null -eq $percentProperty) {
        return $(if ($Result.PSObject.Properties['dataCompatibility']?.Value -eq 'COMPLETE') { 100.0 } else { 0.0 })
    }
    $percent = 0.0
    $parsed = [double]::TryParse(
        [string] $percentProperty.Value,
        [System.Globalization.NumberStyles]::Float,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref] $percent
    )
    if (-not $parsed -or [double]::IsNaN($percent) -or [double]::IsInfinity($percent) -or $percent -lt 0.0 -or $percent -gt 100.0) {
        throw "Compatibility percentage must be finite and between 0 and 100; found '$($percentProperty.Value)'"
    }
    return $percent
}

function Assert-DualDexParserResultIdentity {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Result,

        [Parameter(Mandatory = $true)]
        [string] $ExpectedSha256
    )

    $reportedSha = [string] $Result.PSObject.Properties['result']?.Value?.PSObject.Properties['sha256']?.Value
    if ($reportedSha -notmatch '^[0-9a-fA-F]{64}$' -or
        -not [string]::Equals($reportedSha, $ExpectedSha256, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Parser report ROM SHA-256 mismatch: expected $ExpectedSha256, found '$reportedSha'"
    }
    return $reportedSha.ToLowerInvariant()
}

function ConvertTo-DualDexNormalizedReviewResult {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Record,

        [Parameter(Mandatory = $true)]
        [object] $ManifestEntry,

        [Parameter(Mandatory = $true)]
        [int] $Index,

        [Parameter(Mandatory = $true)]
        [int] $ApkVersionCode
    )

    $normalized = [ordered]@{}
    foreach ($property in $Record.PSObject.Properties) {
        $normalized[$property.Name] = $property.Value
    }
    $normalized['schemaVersion'] = 3
    $normalized['index'] = $Index
    $normalized['archive'] = $ManifestEntry.PSObject.Properties['ArchiveRelativePath']?.Value
    $normalized['archiveSha256'] = $ManifestEntry.PSObject.Properties['ArchiveSha256']?.Value
    $normalized['entry'] = $ManifestEntry.PSObject.Properties['EntryPath']?.Value
    $normalized['platformFolder'] = $ManifestEntry.PSObject.Properties['PlatformFolder']?.Value
    $normalized['extension'] = $ManifestEntry.PSObject.Properties['Extension']?.Value
    $normalized['romSha256'] = [string] $ManifestEntry.RomSha256
    $normalized['bytes'] = [long] $ManifestEntry.Bytes
    $normalized['apkVersionCode'] = $ApkVersionCode
    $normalized['compatibilityPercent'] = Get-DualDexCompatibilityPercent -Result $Record
    # Decisions are external review input, never cache truth. A validated current
    # decision is reapplied later only when this normalized result still needs it.
    $normalized['decision'] = $null
    $normalized['decisionReason'] = $null
    return [pscustomobject] $normalized
}

function Get-DualDexValidatedCachedObservation {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Record,

        [Parameter(Mandatory = $true)]
        [string] $ExpectedSha256
    )

    $schemaProperty = $Record.PSObject.Properties['observationSchemaVersion']
    if ($null -eq $schemaProperty) {
        # Pre-envelope rows are migration input, not parser baselines. They are
        # deliberately reparsed so absent historical fields cannot create a
        # synthetic null-to-value delta.
        return $null
    }
    $schemaVersion = 0L
    if (-not [long]::TryParse([string] $schemaProperty.Value, [ref] $schemaVersion) -or
        $schemaVersion -ne 1) {
        throw "Cached observation schema is unsupported for ROM $ExpectedSha256`: '$($schemaProperty.Value)'"
    }

    $storedHash = [string] $Record.PSObject.Properties['observationHash']?.Value
    if ($storedHash -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Cached observation hash is invalid for ROM $ExpectedSha256`: '$storedHash'"
    }
    $storedValue = $Record.PSObject.Properties['stableObservation']?.Value
    if ($null -eq $storedValue) {
        throw "Cached stable observation is missing for ROM $ExpectedSha256"
    }
    $storedObservation = ConvertTo-DualDexStableGenericValue -Value $storedValue
    $storedSha = [string] (Get-DualDexObservationPropertyValue -Record $storedObservation -Name 'romSha256')
    if (-not [string]::Equals($storedSha, $ExpectedSha256, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Cached stable observation identity mismatch for ROM $ExpectedSha256`: '$storedSha'"
    }
    $actualHash = Get-DualDexObservationHash -Observation $storedObservation
    if (-not [string]::Equals($actualHash, $storedHash, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Cached stable observation hash mismatch for ROM $ExpectedSha256`: expected $storedHash, found $actualHash"
    }

    $derivedObservation = ConvertTo-DualDexStableObservation -Record $Record
    $derivedHash = Get-DualDexObservationHash -Observation $derivedObservation
    if (-not [string]::Equals($derivedHash, $actualHash, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals(
            (ConvertTo-DualDexCanonicalJson -Value $derivedObservation),
            (ConvertTo-DualDexCanonicalJson -Value $storedObservation),
            [System.StringComparison]::Ordinal
        )) {
        throw "Cached review row and stable observation disagree for ROM $ExpectedSha256"
    }
    return $storedObservation
}

function Get-DualDexObservationKey {
    param(
        [Parameter(Mandatory = $true)]
        [string] $RomSha256
    )

    return $RomSha256.ToLowerInvariant()
}

function ConvertTo-DualDexValidatedBaselineEntry {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Entry,
        [Parameter(Mandatory = $true)]
        [int] $Index
    )

    $observationSchemaVersion = 0L
    $schemaProperty = $Entry.PSObject.Properties['observationSchemaVersion']
    if ($null -eq $schemaProperty) {
        # Baselines written before the complete stable-observation contract are
        # migration input. A fresh parse must replace them rather than diffing
        # absent historical fields against current parser output.
        return $null
    }
    if (-not [long]::TryParse([string] $schemaProperty.Value, [ref] $observationSchemaVersion) -or
        $observationSchemaVersion -ne 1) {
        throw "Review baseline observationSchemaVersion must be 1 at index $Index`; found '$($schemaProperty.Value)'"
    }

    $sha = [string] $Entry.PSObject.Properties['romSha256']?.Value
    if ($sha -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Review baseline ROM SHA-256 is invalid at index $Index`: '$sha'"
    }
    $version = 0L
    if (-not [long]::TryParse([string] $Entry.PSObject.Properties['apkVersionCode']?.Value, [ref] $version) -or $version -lt 1) {
        throw "Review baseline APK version is invalid at index $Index"
    }
    $hash = [string] $Entry.PSObject.Properties['observationHash']?.Value
    if ($hash -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Review baseline observation hash is invalid at index $Index`: '$hash'"
    }
    $observation = $Entry.PSObject.Properties['observation']?.Value
    if ($null -eq $observation) {
        throw "Review baseline observation is missing at index $Index"
    }
    $stableObservation = ConvertTo-DualDexStableGenericValue -Value $observation
    $observationSha = [string] (Get-DualDexObservationPropertyValue -Record $stableObservation -Name 'romSha256')
    if (-not [string]::Equals($observationSha, $sha, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Review baseline identity mismatch at index $Index"
    }
    $actualHash = Get-DualDexObservationHash -Observation $stableObservation
    if (-not [string]::Equals($actualHash, $hash, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Review baseline observation hash mismatch at index $Index`: expected $hash, found $actualHash"
    }
    return [pscustomobject][ordered]@{
        romSha256 = $sha.ToLowerInvariant()
        apkVersionCode = $version
        observationSchemaVersion = 1
        observationHash = $actualHash
        observation = $stableObservation
    }
}

if (-not $SkipBuild) {
    & (Join-Path $projectRoot 'gradlew.bat') '--project-dir' $projectRoot ':parser-cli:installDist' '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle parser CLI build failed with exit code $LASTEXITCODE"
    }
}

$parserCli = if ($ParserCliPath) {
    [System.IO.Path]::GetFullPath($ParserCliPath)
} else {
    Join-Path $projectRoot 'parser-cli\build\install\parser-cli\bin\parser-cli.bat'
}
if (-not (Test-Path -LiteralPath $parserCli -PathType Leaf)) {
    throw "Parser CLI distribution does not exist: $parserCli"
}
Write-Output "APK version code: $ApkVersionCode"

$manifest = @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
$eligible = @($manifest | Where-Object {
    (Test-DualDexCorpusEntryInScope -EntryPath ([string] $_.EntryPath)) -and
    $_.Extension -in '.gb', '.gbc', '.gba'
})
$discoveredUnique = @(Select-DualDexUniqueManifestEntries -Manifest $eligible)
$effectiveMaximumIndex = [Math]::Min($MaximumIndex, $discoveredUnique.Count)
$unique = @(if ($effectiveMaximumIndex -gt 0) { $discoveredUnique | Select-Object -First $effectiveMaximumIndex })
$unique = @(for ($index = 0; $index -lt $unique.Count; $index++) {
    Get-DualDexValidatedManifestEntry -Entry $unique[$index] -Index ($index + 1)
})
Write-Output "Eligible unique ROMs: $($unique.Count) (discovered: $($discoveredUnique.Count); maximum index: $effectiveMaximumIndex)"
$eligibleRomSha256 = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
$unique | ForEach-Object { [void] $eligibleRomSha256.Add([string] $_.RomSha256) }
$manifestBySha = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
$manifestIndexBySha = [System.Collections.Generic.Dictionary[string, int]]::new([System.StringComparer]::OrdinalIgnoreCase)
for ($index = 0; $index -lt $unique.Count; $index++) {
    $manifestBySha[[string] $unique[$index].RomSha256] = $unique[$index]
    $manifestIndexBySha[[string] $unique[$index].RomSha256] = $index + 1
}

# Parse and validate every decision for this APK before mutating markers or results.
# Old/legacy decisions remain inert and do not block a new release review.
$decisions = @()
if (Test-Path -LiteralPath $decisionsFullPath -PathType Leaf) {
    $decisions = @(Get-Content -LiteralPath $decisionsFullPath -Raw | ConvertFrom-Json)
}
$currentDecisionsBySha = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($decision in $decisions) {
    if (-not (Test-DualDexApkVersionCodeMatch -Record $decision -ApkVersionCode $ApkVersionCode)) {
        continue
    }
    $decisionSha = [string] $decision.PSObject.Properties['romSha256']?.Value
    if ($decisionSha -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Review decision has an invalid ROM SHA-256 for APK version $ApkVersionCode`: '$decisionSha'"
    }
    $decisionSha = $decisionSha.ToLowerInvariant()
    if ($currentDecisionsBySha.ContainsKey($decisionSha)) {
        throw "Duplicate review decision for ROM $decisionSha and APK version $ApkVersionCode"
    }
    if ($decision.decision -notin 'PARTIAL_ACCEPTED', 'DESIGN_INCOMPATIBLE', 'SOURCE_DATA_DAMAGED', 'EXCLUDED_BY_SCOPE') {
        throw "Unsupported review decision '$($decision.decision)' for $decisionSha"
    }
    if ([string]::IsNullOrWhiteSpace([string] $decision.reason)) {
        throw "Review decision for $decisionSha requires a non-blank reason"
    }
    $currentDecisionsBySha[$decisionSha] = $decision
}

$currentDeltaDecisions = [System.Collections.Generic.List[object]]::new()
if (Test-Path -LiteralPath $deltaDecisionsPath -PathType Leaf) {
    $deltaDecisions = @(Get-Content -LiteralPath $deltaDecisionsPath -Raw | ConvertFrom-Json)
    $deltaDecisionBindings = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($decision in $deltaDecisions) {
        if (-not (Test-DualDexApkVersionCodeMatch -Record $decision -ApkVersionCode $ApkVersionCode)) {
            continue
        }
        $decisionSha = [string] $decision.PSObject.Properties['romSha256']?.Value
        $beforeHash = [string] $decision.PSObject.Properties['beforeHash']?.Value
        $afterHash = [string] $decision.PSObject.Properties['afterHash']?.Value
        if ($decisionSha -notmatch '^[0-9a-fA-F]{64}$') {
            throw "Delta decision has an invalid ROM SHA-256 for APK version $ApkVersionCode`: '$decisionSha'"
        }
        if ($beforeHash -notmatch '^[0-9a-fA-F]{64}$' -or $afterHash -notmatch '^[0-9a-fA-F]{64}$') {
            throw "Delta decision for $decisionSha has an invalid observation hash"
        }
        if ([string]::IsNullOrWhiteSpace([string] $decision.PSObject.Properties['reason']?.Value)) {
            throw "Delta decision for $decisionSha requires a non-blank reason"
        }
        $binding = "$($decisionSha.ToLowerInvariant())|$($beforeHash.ToLowerInvariant())|$($afterHash.ToLowerInvariant())"
        if (-not $deltaDecisionBindings.Add($binding)) {
            throw "Duplicate delta decision for ROM $decisionSha and APK version $ApkVersionCode with the same observation hashes"
        }
        $currentDeltaDecisions.Add($decision)
    }
}

$baselineByKey = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
if (Test-Path -LiteralPath $baselinePath -PathType Leaf) {
    $baselineFile = Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json
    $baselineSchemaVersion = 0L
    if (-not [long]::TryParse([string] $baselineFile.PSObject.Properties['schemaVersion']?.Value, [ref] $baselineSchemaVersion) -or
        $baselineSchemaVersion -ne 1) {
        throw "Review baseline schemaVersion must be 1"
    }
    $baselineEntries = @($baselineFile.PSObject.Properties['observations']?.Value)
    for ($baselineIndex = 0; $baselineIndex -lt $baselineEntries.Count; $baselineIndex++) {
        $baselineEntry = ConvertTo-DualDexValidatedBaselineEntry -Entry $baselineEntries[$baselineIndex] -Index ($baselineIndex + 1)
        if ($null -eq $baselineEntry) {
            continue
        }
        $baselineKey = Get-DualDexObservationKey -RomSha256 $baselineEntry.romSha256
        if ($baselineByKey.ContainsKey($baselineKey)) {
            throw "Duplicate review baseline observation for ROM $($baselineEntry.romSha256)"
        }
        $baselineByKey[$baselineKey] = $baselineEntry
    }
}

$completed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
if (-not $Rebaseline -and (Test-Path -LiteralPath $statePath -PathType Leaf)) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    if (Test-DualDexApkVersionCodeMatch -Record $state -ApkVersionCode $ApkVersionCode) {
        @($state.completedRomSha256) | ForEach-Object {
            if ($_ -and $eligibleRomSha256.Contains([string] $_)) { [void] $completed.Add([string] $_) }
        }
    }
}

$validatedCachedResults = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
$cachedObservationsBySha = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
if (Test-Path -LiteralPath $resultsPath -PathType Leaf) {
    @(Get-Content -LiteralPath $resultsPath -Raw | ConvertFrom-Json) | ForEach-Object {
        if ($_.romSha256 -and
            $eligibleRomSha256.Contains([string] $_.romSha256) -and
            (Test-DualDexApkVersionCodeMatch -Record $_ -ApkVersionCode $ApkVersionCode)) {
            $resultSha = ([string] $_.romSha256).ToLowerInvariant()
            if ($validatedCachedResults.ContainsKey($resultSha)) {
                throw "Duplicate cached review result for ROM $resultSha and APK version $ApkVersionCode"
            }
            $validatedResult = ConvertTo-DualDexNormalizedReviewResult `
                -Record $_ `
                -ManifestEntry $manifestBySha[$resultSha] `
                -Index $manifestIndexBySha[$resultSha] `
                -ApkVersionCode $ApkVersionCode
            $validatedCachedResults[$resultSha] = $validatedResult
            $cachedObservation = Get-DualDexValidatedCachedObservation `
                -Record $validatedResult `
                -ExpectedSha256 $resultSha
            if ($null -ne $cachedObservation) {
                $cachedObservationsBySha[$resultSha] = $cachedObservation
            }
        }
    }
}

$reviewResults = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
if (-not $Rebaseline) {
    foreach ($sha in $validatedCachedResults.Keys) {
        $reviewResults[$sha] = $validatedCachedResults[$sha]
    }
}

# First adoption must preserve the last validated rows as reviewed evidence,
# including before a rebaseline clears the working result set.
$baselineStage = [pscustomobject]@{ dirty = $false }
foreach ($sha in $cachedObservationsBySha.Keys) {
    $baselineKey = Get-DualDexObservationKey -RomSha256 $sha
    if (-not $baselineByKey.ContainsKey($baselineKey)) {
        $observation = $cachedObservationsBySha[$sha]
        $baselineByKey[$baselineKey] = [pscustomobject][ordered]@{
            romSha256 = $sha
            apkVersionCode = $ApkVersionCode
            observationSchemaVersion = 1
            observationHash = Get-DualDexObservationHash -Observation $observation
            observation = $observation
        }
        $baselineStage.dirty = $true
    }
}

function Save-ReviewBaseline {
    Write-JsonFile $baselinePath ([ordered]@{
        schemaVersion = 1
        observations = @($baselineByKey.Values | Sort-Object apkVersionCode, romSha256)
    })
}

$reviewDeltas = [System.Collections.Generic.List[object]]::new()
function Save-ReviewDeltas {
    $orderedDeltas = @($reviewDeltas | Sort-Object romSha256, beforeHash, afterHash)
    Write-JsonFile $deltasPath ([ordered]@{
        schemaVersion = 1
        apkVersionCode = $ApkVersionCode
        deltas = $orderedDeltas
    })
    $markdown = [System.Text.StringBuilder]::new()
    [void] $markdown.AppendLine('# Parser observation deltas')
    [void] $markdown.AppendLine()
    [void] $markdown.AppendLine("APK version code: $ApkVersionCode")
    [void] $markdown.AppendLine()
    [void] $markdown.AppendLine('Changed paths use RFC 6901 JSON Pointer encoding.')
    [void] $markdown.AppendLine()
    if ($orderedDeltas.Count -eq 0) {
        [void] $markdown.AppendLine('No material parser observation changes.')
    } else {
        foreach ($delta in $orderedDeltas) {
            [void] $markdown.AppendLine("## $($delta.romSha256)")
            [void] $markdown.AppendLine()
            [void] $markdown.AppendLine(('- Before: `{0}`' -f $delta.beforeHash))
            [void] $markdown.AppendLine(('- After: `{0}`' -f $delta.afterHash))
            [void] $markdown.AppendLine("- Acknowledged: $($delta.acknowledged)")
            if ($delta.acknowledged) {
                [void] $markdown.AppendLine("- Reason: $($delta.acknowledgementReason)")
            }
            [void] $markdown.AppendLine()
            foreach ($change in @($delta.changes)) {
                $beforeJson = if ($null -ne $change.PSObject.Properties['beforeJson']?.Value) {
                    [string] $change.beforeJson
                } else {
                    ConvertTo-DualDexCanonicalJson -Value $change.before
                }
                $afterJson = if ($null -ne $change.PSObject.Properties['afterJson']?.Value) {
                    [string] $change.afterJson
                } else {
                    ConvertTo-DualDexCanonicalJson -Value $change.after
                }
                [void] $markdown.AppendLine(('- `{0}`: `{1}` -> `{2}`' -f $change.path, $beforeJson, $afterJson))
            }
            [void] $markdown.AppendLine()
        }
    }
    Write-TextFile $deltasMarkdownPath $markdown.ToString()
}

function Commit-DualDexBaseline {
    if ($baselineStage.dirty) {
        Save-ReviewBaseline
        $baselineStage.dirty = $false
    }
}

# All current inputs are now validated. Stale markers and parser reports can be
# retired safely; a fresh parser report remains only when this run pauses.
foreach ($stalePath in @($pendingPath, $completePath, $pendingReportJson, $pendingReportMarkdown)) {
    if (Test-Path -LiteralPath $stalePath -PathType Leaf) {
        Remove-Item -LiteralPath $stalePath -Force
    }
}

$accepted = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($sha in @($reviewResults.Keys)) {
    $reviewedResult = $reviewResults[$sha]
    if (Test-DualDexCorpusRecordComplete -Record $reviewedResult) {
        [void] $completed.Add($sha)
        continue
    }
    [void] $completed.Remove($sha)
    if ($currentDecisionsBySha.ContainsKey($sha) -and
        (Test-DualDexReviewDecisionApplicable `
            -Decision $currentDecisionsBySha[$sha] `
            -ReviewResult $reviewedResult `
            -ApkVersionCode $ApkVersionCode)) {
        [void] $accepted.Add($sha)
    }
}

function Save-ReviewResults {
    Write-JsonFile $resultsPath @($reviewResults.Values | Sort-Object index, archive, entry, romSha256)
}

function Save-ReviewState {
    foreach ($sha in @($completed)) {
        [void] $accepted.Remove($sha)
    }
    Write-JsonFile $statePath ([ordered]@{
        schemaVersion = 3
        apkVersionCode = $ApkVersionCode
        eligibleUniqueRoms = $unique.Count
        discoveredUniqueRoms = $discoveredUnique.Count
        maximumIndex = $effectiveMaximumIndex
        completedRomSha256 = @($completed | Sort-Object)
        acceptedDesignExceptions = @($accepted | Sort-Object)
        updatedAt = (Get-Date).ToString('o')
    })
}

function Invoke-DualDexObservationGate {
    param(
        [Parameter(Mandatory = $true)]
        [object] $ReviewResult,
        [Parameter(Mandatory = $true)]
        [object] $ManifestEntry,
        [Parameter(Mandatory = $true)]
        [int] $Index,
        [object] $Observation
    )

    $sha = ([string] $ReviewResult.romSha256).ToLowerInvariant()
    $observation = if ($PSBoundParameters.ContainsKey('Observation')) {
        ConvertTo-DualDexStableGenericValue -Value $Observation
    } else {
        ConvertTo-DualDexStableObservation -Record $ReviewResult
    }
    $afterHash = Get-DualDexObservationHash -Observation $observation
    $baselineKey = Get-DualDexObservationKey -RomSha256 $sha
    if (-not $baselineByKey.ContainsKey($baselineKey)) {
        $baselineByKey[$baselineKey] = [pscustomobject][ordered]@{
            romSha256 = $sha
            apkVersionCode = $ApkVersionCode
            observationSchemaVersion = 1
            observationHash = $afterHash
            observation = $observation
        }
        $baselineStage.dirty = $true
        return [pscustomobject]@{ status = 'FIRST_OBSERVATION'; delta = $null }
    }

    $beforeEntry = $baselineByKey[$baselineKey]
    if ([string]::Equals([string] $beforeEntry.observationHash, $afterHash, [System.StringComparison]::OrdinalIgnoreCase)) {
        if ([long] $beforeEntry.apkVersionCode -ne [long] $ApkVersionCode) {
            $baselineByKey[$baselineKey] = [pscustomobject][ordered]@{
                romSha256 = $sha
                apkVersionCode = $ApkVersionCode
                observationSchemaVersion = 1
                observationHash = $afterHash
                observation = $observation
            }
            $baselineStage.dirty = $true
        }
        return [pscustomobject]@{ status = 'UNCHANGED'; delta = $null }
    }

    $changes = @(Compare-DualDexObservation -Before $beforeEntry.observation -After $observation)
    $deltaBinding = [pscustomobject][ordered]@{
        romSha256 = $sha
        apkVersionCode = $ApkVersionCode
        beforeHash = [string] $beforeEntry.observationHash
        afterHash = $afterHash
    }
    $acknowledgement = @($currentDeltaDecisions | Where-Object {
        Test-DualDexDeltaDecisionApplicable -Decision $_ -Delta $deltaBinding -ApkVersionCode $ApkVersionCode
    }) | Select-Object -First 1
    $acknowledged = $null -ne $acknowledgement
    $delta = [pscustomobject][ordered]@{
        romSha256 = $sha
        apkVersionCode = $ApkVersionCode
        beforeHash = [string] $beforeEntry.observationHash
        afterHash = $afterHash
        acknowledged = $acknowledged
        acknowledgementReason = $(if ($acknowledged) { [string] $acknowledgement.reason } else { $null })
        changes = @($changes)
    }
    $reviewDeltas.Add($delta)
    Save-ReviewDeltas

    if ($acknowledged) {
        $baselineByKey[$baselineKey] = [pscustomobject][ordered]@{
            romSha256 = $sha
            apkVersionCode = $ApkVersionCode
            observationSchemaVersion = 1
            observationHash = $afterHash
            observation = $observation
        }
        $baselineStage.dirty = $true
        return [pscustomobject]@{ status = 'ACKNOWLEDGED'; delta = $delta }
    }

    Write-JsonFile $pendingPath ([ordered]@{
        schemaVersion = 4
        reviewStatus = 'PARSER_DRIFT_REVIEW_REQUIRED'
        index = $Index
        totalUniqueRoms = $unique.Count
        discoveredUniqueRoms = $discoveredUnique.Count
        maximumIndex = $effectiveMaximumIndex
        archive = $ManifestEntry.ArchiveRelativePath
        entry = $ManifestEntry.EntryPath
        romSha256 = $sha
        apkVersionCode = $ApkVersionCode
        compatibilityPercent = $ReviewResult.compatibilityPercent
        manualReviewRequired = $ReviewResult.manualReviewRequired
        dataCompatibility = $ReviewResult.dataCompatibility
        beforeHash = $delta.beforeHash
        afterHash = $delta.afterHash
        changes = $delta.changes
        deltaReportJson = $deltasPath
        deltaReportMarkdown = $deltasMarkdownPath
        deltaDecisionsFile = $deltaDecisionsPath
        requiredDeltaDecisionBinding = $deltaBinding
        nextActions = @(
            'Review every changed parser observation JSON Pointer path and its before/after values',
            'Fix unintended parser drift and rerun',
            'Or acknowledge the exact ROM/APK/beforeHash/afterHash binding with a non-blank reason'
        )
    })
    return [pscustomobject]@{ status = 'PAUSED'; delta = $delta }
}

Save-ReviewResults
Save-ReviewDeltas

for ($index = 0; $index -lt $unique.Count; $index++) {
    $item = $unique[$index]
    $sha = [string] $item.RomSha256
    $existingResult = if ($reviewResults.ContainsKey($sha)) { $reviewResults[$sha] } else { $null }
    $existingComplete = $null -ne $existingResult -and (Test-DualDexCorpusRecordComplete -Record $existingResult)
    $existingVersionMatches = $null -ne $existingResult -and
        (Test-DualDexApkVersionCodeMatch -Record $existingResult -ApkVersionCode $ApkVersionCode) -and
        $cachedObservationsBySha.ContainsKey($sha)
    if ($existingVersionMatches) {
        $observationGate = Invoke-DualDexObservationGate `
            -ReviewResult $existingResult `
            -ManifestEntry $item `
            -Index ($index + 1) `
            -Observation $cachedObservationsBySha[$sha]
        if ($observationGate.status -eq 'PAUSED') {
            [void] $completed.Remove($sha)
            [void] $accepted.Remove($sha)
            Save-ReviewState
            Write-Output "Parser drift review paused: $pendingPath"
            Write-Output "Observation changed: $($observationGate.delta.beforeHash) -> $($observationGate.delta.afterHash)"
            return
        }
        if (-not $existingComplete -and
            $currentDecisionsBySha.ContainsKey($sha) -and
            (Test-DualDexReviewDecisionApplicable `
                -Decision $currentDecisionsBySha[$sha] `
                -ReviewResult $existingResult `
                -ApkVersionCode $ApkVersionCode)) {
            [void] $accepted.Add($sha)
            $existingResult.decision = $currentDecisionsBySha[$sha].decision
            $existingResult.decisionReason = $currentDecisionsBySha[$sha].reason
            Save-ReviewResults
        }
    }
    if ($existingVersionMatches -and
        (($completed.Contains($sha) -and $existingComplete) -or $accepted.Contains($sha))) {
        continue
    }
    $romPath = [string] $item.ExtractedPath

    Write-Output "[$($index + 1)/$($unique.Count)] Reviewing $($item.EntryPath)"
    & $parserCli $romPath '--json' $pendingReportJson '--markdown' $pendingReportMarkdown '--cache-dir' $cacheRoot '--all-roms'
    if ($LASTEXITCODE -ne 0) {
        throw "Parser CLI failed with exit code $LASTEXITCODE for $romPath"
    }
    $singleReport = Get-Content -LiteralPath $pendingReportJson -Raw | ConvertFrom-Json
    $result = @($singleReport.results) | Select-Object -First 1
    if ($null -eq $result) {
        throw "Parser produced no result for $romPath"
    }
    Assert-DualDexParserResultIdentity -Result $result -ExpectedSha256 $sha | Out-Null
    $compatibilityPercent = Get-DualDexCompatibilityPercent -Result $result
    $resolvedFeatureCount = $result.PSObject.Properties['resolvedFeatureCount']?.Value
    $expectedFeatureCount = $result.PSObject.Properties['expectedFeatureCount']?.Value
    $manualReviewProperty = $result.PSObject.Properties['manualReviewRequired']
    $manualReviewRequired = if ($null -ne $manualReviewProperty) {
        [bool] $manualReviewProperty.Value
    } else {
        $result.result.status -eq 'AMBIGUOUS' -or @($result.result.capabilities | Where-Object {
            $_.status -eq 'AMBIGUOUS' -or $_.PSObject.Properties['reviewStatus']?.Value -eq 'MANUAL_REVIEW'
        }).Count -gt 0
    }
    $needsReview = Test-DualDexCorpusNeedsReview `
        -CompatibilityPercent $compatibilityPercent `
        -ManualReviewRequired $manualReviewRequired
    $missing = @($result.result.capabilities | Where-Object status -eq 'NOT_FOUND' | ForEach-Object capability)
    $partialCapabilities = @($result.result.capabilities | Where-Object status -in 'PARTIAL', 'AMBIGUOUS' | ForEach-Object { [ordered]@{
        capability = $_.capability
        status = $_.status
        validRecords = $_.PSObject.Properties['validRecords']?.Value
        totalRecords = $_.PSObject.Properties['totalRecords']?.Value
        coveredRecords = $_.PSObject.Properties['coveredRecords']?.Value
        expectedRecords = $_.PSObject.Properties['expectedRecords']?.Value
        reviewStatus = $_.PSObject.Properties['reviewStatus']?.Value
        reasons = @($_.reasons)
    } })
    $catalogError = $result.PSObject.Properties['catalogError']?.Value
    $persistenceError = $result.PSObject.Properties['persistenceError']?.Value
    $samples = $result.PSObject.Properties['samples']?.Value
    $referenceErrors = if ($null -eq $samples) { @() } else { @($samples.PSObject.Properties['referenceErrors']?.Value) }
    $selectedFamily = $result.result.PSObject.Properties['selectedFamily']?.Value
    $firstRegisters = if ($null -eq $samples) { $null } else { [ordered]@{
        speciesPhysical = @($samples.species)
        speciesDexOrdered = @($samples.PSObject.Properties['speciesByDex']?.Value)
        moves = @($samples.moves)
        types = @($samples.types)
        typeChart = @($samples.typeChart)
        evolutions = @($samples.evolutions)
        learnsets = @($samples.learnsets)
        eggMoves = @($samples.PSObject.Properties['eggMoves']?.Value)
        machineMoves = @($samples.PSObject.Properties['machineMoves']?.Value)
        tutorMoves = @($samples.PSObject.Properties['tutorMoves']?.Value)
        abilities = @($samples.abilities)
        encounters = @($samples.encounters)
        balls = @($samples.balls)
    } }
    $capabilities = @($result.result.capabilities | ForEach-Object { [ordered]@{
        capability = $_.capability
        compatible = $_.PSObject.Properties['compatible']?.Value
        status = $_.status
        count = $_.PSObject.Properties['count']?.Value
        offset = $_.PSObject.Properties['offset']?.Value
        recordSize = $_.PSObject.Properties['recordSize']?.Value
        elementSize = $_.PSObject.Properties['elementSize']?.Value
        validRecords = $_.PSObject.Properties['validRecords']?.Value
        totalRecords = $_.PSObject.Properties['totalRecords']?.Value
        coveredRecords = $_.PSObject.Properties['coveredRecords']?.Value
        expectedRecords = $_.PSObject.Properties['expectedRecords']?.Value
        reviewStatus = $_.PSObject.Properties['reviewStatus']?.Value
        validatorReviewRecommended = $_.PSObject.Properties['validatorReviewRecommended']?.Value
        confidence = $_.PSObject.Properties['confidence']?.Value
        format = $_.PSObject.Properties['format']?.Value
        reasons = @($_.reasons)
    } })
    $freshReviewResult = [ordered]@{
        schemaVersion = 3
        index = $index + 1
        archive = $item.ArchiveRelativePath
        archiveSha256 = $item.PSObject.Properties['ArchiveSha256']?.Value
        entry = $item.EntryPath
        platformFolder = $item.PSObject.Properties['PlatformFolder']?.Value
        extension = $item.Extension
        romSha256 = $sha
        bytes = [long] $item.Bytes
        apkVersionCode = $ApkVersionCode
        compatibilityPercent = $compatibilityPercent
        resolvedFeatureCount = $resolvedFeatureCount
        expectedFeatureCount = $expectedFeatureCount
        manualReviewRequired = $manualReviewRequired
        dataCompatibility = $result.dataCompatibility
        selectionStatus = $result.result.status
        selectedFamily = $selectedFamily
        selectedProfile = $result.result.PSObject.Properties['selectedProfile']?.Value
        runnerUpMargin = $result.result.PSObject.Properties['runnerUpMargin']?.Value
        missingStructures = $missing
        matchedTableFirstRegisters = $firstRegisters
        capabilities = $capabilities
        catalog = $result.PSObject.Properties['catalog']?.Value
        referenceErrors = $referenceErrors
        catalogError = $catalogError
        persistenceError = $persistenceError
        decision = $null
        decisionReason = $null
        verifiedAt = (Get-Date).ToString('o')
    }
    $freshObservation = ConvertTo-DualDexStableObservation -Record ([pscustomobject] $freshReviewResult)
    $freshReviewResult['observationSchemaVersion'] = 1
    $freshReviewResult['observationHash'] = Get-DualDexObservationHash -Observation $freshObservation
    $freshReviewResult['stableObservation'] = $freshObservation
    $reviewResults[$sha] = $freshReviewResult
    $observationGate = Invoke-DualDexObservationGate `
        -ReviewResult $reviewResults[$sha] `
        -ManifestEntry $item `
        -Index ($index + 1)
    if ($observationGate.status -eq 'PAUSED') {
        # A fresh result that exposed unacknowledged parser drift is evidence,
        # not a reusable cache entry. Keep it in the delta/pending artifacts,
        # but force the parser to run again after a fix.
        [void] $reviewResults.Remove($sha)
        Save-ReviewResults
        [void] $completed.Remove($sha)
        [void] $accepted.Remove($sha)
        Save-ReviewState
        Write-Output "Parser drift review paused: $pendingPath"
        Write-Output "Observation changed: $($observationGate.delta.beforeHash) -> $($observationGate.delta.afterHash)"
        return
    }
    Save-ReviewResults

    $decision = if ($needsReview -and $currentDecisionsBySha.ContainsKey($sha)) {
        $currentDecisionsBySha[$sha]
    } else {
        $null
    }
    if ($null -ne $decision) {
        $reviewResults[$sha].decision = $decision.decision
        $reviewResults[$sha].decisionReason = $decision.reason
        Save-ReviewResults
    }

    if (-not $needsReview) {
        [void] $completed.Add($sha)
        [void] $accepted.Remove($sha)
        Save-ReviewState
        continue
    }
    [void] $completed.Remove($sha)
    if ($null -ne $decision) {
        [void] $accepted.Add($sha)
        Save-ReviewState
        continue
    }

    [void] $accepted.Remove($sha)
    Save-ReviewState

    Write-JsonFile $pendingPath ([ordered]@{
        schemaVersion = 3
        reviewStatus = 'JUDGMENT_REQUIRED'
        index = $index + 1
        totalUniqueRoms = $unique.Count
        discoveredUniqueRoms = $discoveredUnique.Count
        maximumIndex = $effectiveMaximumIndex
        archive = $item.ArchiveRelativePath
        entry = $item.EntryPath
        romSha256 = $sha
        apkVersionCode = $ApkVersionCode
        compatibilityPercent = $compatibilityPercent
        resolvedFeatureCount = $resolvedFeatureCount
        expectedFeatureCount = $expectedFeatureCount
        manualReviewRequired = $manualReviewRequired
        dataCompatibility = $result.dataCompatibility
        missingStructures = $missing
        partialCapabilities = $partialCapabilities
        matchedTableFirstRegisters = $firstRegisters
        referenceErrors = $referenceErrors
        catalogError = $catalogError
        persistenceError = $persistenceError
        parserReportJson = $pendingReportJson
        parserReportMarkdown = $pendingReportMarkdown
        decisionsFile = $decisionsFullPath
        requiredDecisionBinding = [ordered]@{
            romSha256 = $sha
            apkVersionCode = $ApkVersionCode
        }
        nextActions = @(
            'Fix a generally derivable parser layout and rerun this script',
            'Or add PARTIAL_ACCEPTED with a reason and the required ROM SHA/APK version code after validating decoded samples',
            'Or add DESIGN_INCOMPATIBLE / SOURCE_DATA_DAMAGED / EXCLUDED_BY_SCOPE with a reason and the required ROM SHA/APK version code'
        )
    })
    Commit-DualDexBaseline
    Write-Output "Review paused: $pendingPath"
    Write-Output "Compatibility: $compatibilityPercent% ($resolvedFeatureCount/$expectedFeatureCount features resolved); manual review: $manualReviewRequired"
    Write-Output "Missing structures: $($missing -join ', ')"
    foreach ($partialCapability in $partialCapabilities) {
        Write-Output "Partial structure: $($partialCapability.capability) $($partialCapability.validRecords)/$($partialCapability.totalRecords) [$($partialCapability.status)]"
    }
    if ($null -ne $firstRegisters) {
        Write-Output "First physical species registers: $(@($firstRegisters.speciesPhysical) -join ' | ')"
        Write-Output "First Dex-ordered species registers: $(@($firstRegisters.speciesDexOrdered) -join ' | ')"
        Write-Output "First move registers: $(@($firstRegisters.moves) -join ' | ')"
        Write-Output "First tutor registers: $(@($firstRegisters.tutorMoves) -join ' | ')"
    }
    return
}

foreach ($stalePath in @($pendingPath, $pendingReportJson, $pendingReportMarkdown)) {
    if (Test-Path -LiteralPath $stalePath -PathType Leaf) {
        Remove-Item -LiteralPath $stalePath -Force
    }
}
Save-ReviewState
Write-JsonFile $completePath ([ordered]@{
    schemaVersion = 3
    reviewStatus = 'COMPLETE'
    apkVersionCode = $ApkVersionCode
    uniqueRomsReviewed = $unique.Count
    eligibleUniqueRoms = $unique.Count
    discoveredUniqueRoms = $discoveredUnique.Count
    maximumIndex = $effectiveMaximumIndex
    fullyCompatible = $completed.Count
    acceptedDesignExceptions = $accepted.Count
    completedAt = (Get-Date).ToString('o')
})
Commit-DualDexBaseline
Write-Output "Review complete: $completePath"
Write-Output "Eligible unique ROM denominator: $($unique.Count)"
