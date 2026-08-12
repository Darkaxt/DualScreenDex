param(
    [Parameter(Mandatory = $true)]
    [string] $RomManifest,

    [Parameter(Mandatory = $true)]
    [string] $RawReport,

    [Parameter(Mandatory = $true)]
    [string] $ReviewResults,

    [Parameter(Mandatory = $true)]
    [string] $ReviewComplete,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, [int]::MaxValue)]
    [int] $ApkVersionCode,

    [ValidateRange(1, [int]::MaxValue)]
    [int] $ExpectedCount = 50,

    [Parameter(Mandatory = $true)]
    [string] $JsonOutput,

    [Parameter(Mandatory = $true)]
    [string] $MarkdownOutput,

    [string] $ParserArtifacts
)

$ErrorActionPreference = 'Stop'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$newline = "`n"
$policyPath = Join-Path $PSScriptRoot 'CorpusReviewPolicy.ps1'
. $policyPath

function Get-DualDexDocumentationProperty {
    param([object] $Value, [string] $Name)
    if ($null -eq $Value) { return $null }
    if ($Value -is [System.Collections.IDictionary]) {
        return $(if ($Value.Contains($Name)) { $Value[$Name] } else { $null })
    }
    return $Value.PSObject.Properties[$Name]?.Value
}

function Read-DualDexDocumentationJson([string] $Path, [string] $Label) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        throw "$Label does not exist: $fullPath"
    }
    try {
        return Get-Content -LiteralPath $fullPath -Raw | ConvertFrom-Json
    } catch {
        throw "$Label is not valid JSON: $fullPath ($($_.Exception.Message))"
    }
}

function New-DualDexDocumentationIndex {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]] $Rows,
        [Parameter(Mandatory = $true)]
        [scriptblock] $Key,
        [Parameter(Mandatory = $true)]
        [string] $Label
    )
    $index = [System.Collections.Generic.Dictionary[string, object]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    foreach ($row in $Rows) {
        $value = [string] (& $Key $row)
        if ($value -notmatch '^[0-9a-fA-F]{64}$') {
            throw "$Label contains an invalid ROM SHA-256: '$value'"
        }
        $normalized = $value.ToLowerInvariant()
        if ($index.ContainsKey($normalized)) {
            throw "$Label contains duplicate ROM identity $normalized"
        }
        $index[$normalized] = $row
    }
    return $index
}

function Copy-DualDexDocumentationFields {
    param(
        [object] $Source,
        [string[]] $Names
    )
    if ($null -eq $Source) { return $null }
    $copy = [ordered]@{}
    foreach ($name in $Names) {
        $copy[$name] = Get-DualDexDocumentationProperty -Value $Source -Name $name
    }
    return [pscustomobject] $copy
}

function Get-DualDexCapability {
    param([object[]] $Capabilities, [string] $Name)
    $matches = @($Capabilities | Where-Object {
        [string]::Equals(
            [string] (Get-DualDexDocumentationProperty $_ 'capability'),
            $Name,
            [System.StringComparison]::Ordinal
        )
    })
    if ($matches.Count -gt 1) { throw "Raw report contains duplicate $Name capability evidence" }
    return $matches | Select-Object -First 1
}

function Copy-DualDexCapabilityEvidence {
    param([object] $Evidence)
    return Copy-DualDexDocumentationFields -Source $Evidence -Names @(
        'status', 'count', 'confidence', 'validRecords', 'totalRecords',
        'coveredRecords', 'expectedRecords', 'incompleteRecords', 'reviewStatus', 'reasons'
    )
}

function Get-DualDexRawStableObservation {
    param(
        [object] $RawResult,
        [string] $RomSha256,
        [int] $VersionCode
    )
    $parse = Get-DualDexDocumentationProperty $RawResult 'result'
    $samples = Get-DualDexDocumentationProperty $RawResult 'samples'
    $firstRegisters = if ($null -eq $samples) {
        $null
    } else {
        [pscustomobject][ordered]@{
            speciesPhysical = @(Get-DualDexDocumentationProperty $samples 'species')
            speciesDexOrdered = @(Get-DualDexDocumentationProperty $samples 'speciesByDex')
            moves = @(Get-DualDexDocumentationProperty $samples 'moves')
            types = @(Get-DualDexDocumentationProperty $samples 'types')
            typeChart = @(Get-DualDexDocumentationProperty $samples 'typeChart')
            evolutions = @(Get-DualDexDocumentationProperty $samples 'evolutions')
            learnsets = @(Get-DualDexDocumentationProperty $samples 'learnsets')
            eggMoves = @(Get-DualDexDocumentationProperty $samples 'eggMoves')
            machineMoves = @(Get-DualDexDocumentationProperty $samples 'machineMoves')
            tutorMoves = @(Get-DualDexDocumentationProperty $samples 'tutorMoves')
            abilities = @(Get-DualDexDocumentationProperty $samples 'abilities')
            encounters = @(Get-DualDexDocumentationProperty $samples 'encounters')
            balls = @(Get-DualDexDocumentationProperty $samples 'balls')
        }
    }
    $record = [pscustomobject][ordered]@{
        romSha256 = $RomSha256
        apkVersionCode = $VersionCode
        selectedFamily = Get-DualDexDocumentationProperty $parse 'selectedFamily'
        selectedProfile = Get-DualDexDocumentationProperty $parse 'selectedProfile'
        selectionStatus = Get-DualDexDocumentationProperty $parse 'status'
        runnerUpMargin = Get-DualDexDocumentationProperty $parse 'runnerUpMargin'
        compatibilityPercent = Get-DualDexDocumentationProperty $RawResult 'compatibilityPercent'
        resolvedFeatureCount = Get-DualDexDocumentationProperty $RawResult 'resolvedFeatureCount'
        expectedFeatureCount = Get-DualDexDocumentationProperty $RawResult 'expectedFeatureCount'
        manualReviewRequired = Get-DualDexDocumentationProperty $RawResult 'manualReviewRequired'
        dataCompatibility = Get-DualDexDocumentationProperty $RawResult 'dataCompatibility'
        capabilities = @(Get-DualDexDocumentationProperty $parse 'capabilities')
        catalog = Get-DualDexDocumentationProperty $RawResult 'catalog'
        matchedTableFirstRegisters = $firstRegisters
        referenceErrors = $(if ($null -eq $samples) { @() } else { @(Get-DualDexDocumentationProperty $samples 'referenceErrors') })
        catalogError = Get-DualDexDocumentationProperty $RawResult 'catalogError'
        persistenceError = Get-DualDexDocumentationProperty $RawResult 'persistenceError'
    }
    return ConvertTo-DualDexStableObservation -Record $record
}

function Get-DualDexEngineMarkers([object] $Layout) {
    if ($null -eq $Layout) { return @() }
    $markers = [System.Collections.Generic.List[string]]::new()
    $expansion = Get-DualDexDocumentationProperty $Layout 'pokeemeraldExpansion'
    $tables = Get-DualDexDocumentationProperty $Layout 'tables'
    $moveData = Get-DualDexDocumentationProperty $tables 'moveData'
    $baseStats = Get-DualDexDocumentationProperty $tables 'baseStats'
    $moveFormat = [string] (Get-DualDexDocumentationProperty $moveData 'format')
    if ($null -ne $expansion) { $markers.Add('POKEEMERALD_EXPANSION_PUBLIC_HEADER') }
    if ($moveFormat -eq 'CFRU_MOVE_16') { $markers.Add('CFRU_OR_DPE_WIDENED_MOVE_ABI') }
    if ($moveFormat -eq 'BATTLE_ENGINE_MOVE_20') { $markers.Add('BATTLE_ENGINE_MOVE_ABI') }
    if (
        [int] (Get-DualDexDocumentationProperty $Layout 'generation') -eq 3 -and
        $null -eq $expansion -and
        [int] (Get-DualDexDocumentationProperty $baseStats 'recordSize') -eq 32
    ) {
        $markers.Add('BATTLE_ENGINE_BASE_STATS_ABI')
    }
    if ($markers.Count -eq 0) { $markers.Add('RETAIL_COMPATIBLE_TABLE_ABIS') }
    return @($markers)
}

function Get-DualDexTableSemanticLabel([object] $Layout, [string] $Name, [object] $Table) {
    if ($null -eq $Table) { return $null }
    $generation = Get-DualDexDocumentationProperty $Layout 'generation'
    $expansion = Get-DualDexDocumentationProperty $Layout 'pokeemeraldExpansion'
    $recordSize = Get-DualDexDocumentationProperty $Table 'recordSize'
    if ($Name -eq 'baseStats' -and [int] $generation -eq 3 -and $null -ne $expansion) {
        return 'POKEEMERALD_EXPANSION_SPECIES_INFO'
    }
    if ($Name -eq 'baseStats' -and [int] $generation -eq 3 -and [int] $recordSize -eq 28) {
        return 'GEN3_RETAIL_BASE_STATS_28'
    }
    if ($Name -eq 'baseStats' -and [int] $generation -eq 3 -and [int] $recordSize -eq 32) {
        return 'GEN3_BATTLE_ENGINE_BASE_STATS_32'
    }
    return [string] (Get-DualDexDocumentationProperty $Table 'format')
}

function Get-DualDexTableAbis([object] $Layout) {
    if ($null -eq $Layout) { return $null }
    $tables = Get-DualDexDocumentationProperty $Layout 'tables'
    $result = [ordered]@{}
    foreach ($name in @(
        'speciesNames', 'baseStats', 'moveNames', 'moveData', 'typeChart',
        'evolutions', 'learnsets', 'sprites', 'descriptions', 'abilities'
    )) {
        $table = Get-DualDexDocumentationProperty $tables $name
        if ($null -eq $table) {
            $result[$name] = $null
            continue
        }
        $copy = [ordered]@{
            semanticLabel = Get-DualDexTableSemanticLabel $Layout $name $table
        }
        foreach ($field in @(
            'offset', 'count', 'recordSize', 'stride', 'elementSize', 'variableLength',
            'valuesArePointers', 'format', 'bank', 'banks', 'pointerOffsets',
            'bankAdjustment', 'bankRemap'
        )) {
            $copy[$field] = Get-DualDexDocumentationProperty $table $field
        }
        $result[$name] = [pscustomobject] $copy
    }
    return [pscustomobject] $result
}

function Get-DualDexRulesetDetails([object] $Catalog) {
    $details = @(Get-DualDexDocumentationProperty $Catalog 'rulesetDetails')
    return @($details | Where-Object { $null -ne $_ } | ForEach-Object {
        [pscustomobject][ordered]@{
            id = Get-DualDexDocumentationProperty $_ 'id'
            label = Get-DualDexDocumentationProperty $_ 'label'
            sourceOffset = Get-DualDexDocumentationProperty $_ 'sourceOffset'
            confidence = Get-DualDexDocumentationProperty $_ 'confidence'
            primary = Get-DualDexDocumentationProperty $_ 'primary'
            levelUpSelector = Copy-DualDexDocumentationFields `
                -Source (Get-DualDexDocumentationProperty $_ 'levelUpSelector') `
                -Names @('saveBlock1ByteOffset', 'mask', 'expectedValue')
        }
    })
}

function Get-DualDexReviewStatus([object] $Review) {
    $decision = [string] (Get-DualDexDocumentationProperty $Review 'decision')
    if (-not [string]::IsNullOrWhiteSpace($decision)) {
        if ($decision -notin @('PARTIAL_ACCEPTED', 'DESIGN_INCOMPATIBLE', 'SOURCE_DATA_DAMAGED', 'EXCLUDED_BY_SCOPE')) {
            throw "Review contains unsupported final decision '$decision'"
        }
        return $decision
    }
    if (
        [double] (Get-DualDexDocumentationProperty $Review 'compatibilityPercent') -eq 100.0 -and
        -not [bool] (Get-DualDexDocumentationProperty $Review 'manualReviewRequired')
    ) {
        return 'FULLY_COMPATIBLE'
    }
    throw "Completed review contains JUDGMENT_REQUIRED record $((Get-DualDexDocumentationProperty $Review 'romSha256'))"
}

function Get-DualDexGapClassification([string] $ReviewStatus) {
    switch ($ReviewStatus) {
        'SOURCE_DATA_DAMAGED' { 'SOURCE_DATA_DAMAGED' }
        'DESIGN_INCOMPATIBLE' { 'DESIGN_INCOMPATIBLE' }
        'EXCLUDED_BY_SCOPE' { 'EXCLUDED_BY_SCOPE' }
        'PARTIAL_ACCEPTED' { 'PARTIAL_ACCEPTED' }
        default { 'UNCLASSIFIED' }
    }
}

function Get-DualDexGaps {
    param([object] $RawResult, [string] $ReviewStatus)
    $parse = Get-DualDexDocumentationProperty $RawResult 'result'
    $capabilities = @(Get-DualDexDocumentationProperty $parse 'capabilities')
    $classification = Get-DualDexGapClassification $ReviewStatus
    $sourceAuthored = $ReviewStatus -eq 'SOURCE_DATA_DAMAGED'
    $gaps = [System.Collections.Generic.List[object]]::new()
    foreach ($capability in @($capabilities | Where-Object {
        (Get-DualDexDocumentationProperty $_ 'status') -in @('PARTIAL', 'AMBIGUOUS', 'NOT_FOUND')
    })) {
        $gaps.Add([pscustomobject][ordered]@{
            capability = Get-DualDexDocumentationProperty $capability 'capability'
            status = Get-DualDexDocumentationProperty $capability 'status'
            coveredRecords = Get-DualDexDocumentationProperty $capability 'coveredRecords'
            expectedRecords = Get-DualDexDocumentationProperty $capability 'expectedRecords'
            reasons = @(Get-DualDexDocumentationProperty $capability 'reasons')
            classification = $classification
            sourceAuthored = $sourceAuthored
        })
    }
    $samples = Get-DualDexDocumentationProperty $RawResult 'samples'
    $errors = [ordered]@{
        REFERENCE_INTEGRITY = @(Get-DualDexDocumentationProperty $samples 'referenceErrors')
        CATALOG = @(Get-DualDexDocumentationProperty $RawResult 'catalogError')
        PERSISTENCE = @(Get-DualDexDocumentationProperty $RawResult 'persistenceError')
    }
    foreach ($entry in $errors.GetEnumerator()) {
        $reasons = @($entry.Value | Where-Object { $null -ne $_ -and -not [string]::IsNullOrWhiteSpace([string] $_) })
        if ($reasons.Count -eq 0) { continue }
        $gaps.Add([pscustomobject][ordered]@{
            capability = $entry.Key
            status = 'ERROR'
            coveredRecords = $null
            expectedRecords = $null
            reasons = $reasons
            classification = $classification
            sourceAuthored = $sourceAuthored
        })
    }
    return @($gaps)
}

function Write-DualDexAtomicText([string] $Path, [string] $Value) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $directory = [System.IO.Path]::GetDirectoryName($fullPath)
    [System.IO.Directory]::CreateDirectory($directory) | Out-Null
    $temporaryPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    $backupPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    try {
        [System.IO.File]::WriteAllText($temporaryPath, $Value, $utf8)
        if (Test-Path -LiteralPath $fullPath -PathType Leaf) {
            [System.IO.File]::Replace($temporaryPath, $fullPath, $backupPath)
            [System.IO.File]::Delete($backupPath)
        } else {
            [System.IO.File]::Move($temporaryPath, $fullPath)
        }
    } finally {
        foreach ($cleanup in @($temporaryPath, $backupPath)) {
            if (Test-Path -LiteralPath $cleanup -PathType Leaf) {
                [System.IO.File]::Delete($cleanup)
            }
        }
    }
}

function ConvertTo-DualDexMarkdown([object] $Document) {
    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('# DualDex ROM Hacks Compatibility')
    $lines.Add('')
    $lines.Add("Generated from parser report schema $($Document.reportSchemaVersion) and completed review for APK version $($Document.apkVersionCode).")
    $lines.Add('')
    $lines.Add("Verification scope: $($Document.verificationScope.strategy).")
    $lines.Add('')
    foreach ($generationGroup in $Document.groups) {
        $lines.Add("## Generation $($generationGroup.generation)")
        $lines.Add('')
        foreach ($familyGroup in $generationGroup.families) {
            $lines.Add("### $($familyGroup.family)")
            $lines.Add('')
            foreach ($rom in $familyGroup.roms) {
                $lines.Add("#### $($rom.identity.manifestIndex). $($rom.identity.displayName)")
                $lines.Add('')
                $lines.Add('| Property | Value |')
                $lines.Add('| --- | --- |')
                $lines.Add("| SHA-256 | ``$($rom.identity.sha256)`` |")
                $lines.Add("| Routing | $($rom.routing.status) / $($rom.routing.family) |")
                $lines.Add("| Compatibility | $($rom.compatibility.percent)% ($($rom.compatibility.dataCompatibility)) |")
                $lines.Add("| Active species / moves / abilities / types | $($rom.active.species) / $($rom.active.moves) / $($rom.active.abilities) / $($rom.active.types) |")
                $lines.Add("| Rulesets | $($rom.rulesets.count) |")
                $lines.Add("| Encounters | $($rom.encounters.areas) areas |")
                $lines.Add("| Review | $($rom.review.status) |")
                $lines.Add('')
                if (@($rom.gaps).Count -gt 0) {
                    $lines.Add('Gaps:')
                    $lines.Add('')
                    foreach ($gap in $rom.gaps) {
                        $reason = (@($gap.reasons) -join '; ').Replace('|', '\|')
                        $reasonSuffix = if ([string]::IsNullOrWhiteSpace($reason)) { '' } else { " - $reason" }
                        $lines.Add("- $($gap.capability): $($gap.status) / $($gap.classification)$reasonSuffix")
                    }
                    $lines.Add('')
                }
            }
        }
    }
    return (($lines -join $newline).TrimEnd("`r", "`n")) + $newline
}

$manifestDocument = @(Read-DualDexDocumentationJson $RomManifest 'ROM manifest')
$report = Read-DualDexDocumentationJson $RawReport 'raw parser report'
$reviewRows = @(Read-DualDexDocumentationJson $ReviewResults 'review results')
$complete = Read-DualDexDocumentationJson $ReviewComplete 'review completion marker'

if ([int] (Get-DualDexDocumentationProperty $report 'schemaVersion') -ne 11) {
    throw "Raw parser report schemaVersion must be 11"
}
if (
    [int] (Get-DualDexDocumentationProperty $complete 'schemaVersion') -ne 3 -or
    [string] (Get-DualDexDocumentationProperty $complete 'reviewStatus') -ne 'COMPLETE'
) {
    throw 'Review completion marker must be schemaVersion 3 with reviewStatus COMPLETE'
}
if ([int] (Get-DualDexDocumentationProperty $complete 'apkVersionCode') -ne $ApkVersionCode) {
    throw 'Review completion marker belongs to a different APK version code'
}
foreach ($countName in @('uniqueRomsReviewed', 'eligibleUniqueRoms', 'maximumIndex')) {
    if ([int] (Get-DualDexDocumentationProperty $complete $countName) -ne $ExpectedCount) {
        throw "Review completion marker $countName must equal $ExpectedCount"
    }
}
$completionVerificationScope = Get-DualDexDocumentationProperty $complete 'verificationScope'
if ($null -eq $completionVerificationScope) {
    throw 'Review completion marker must declare verificationScope'
}
$verificationStrategy = [string] (Get-DualDexDocumentationProperty $completionVerificationScope 'strategy')
$finalRerunIndices = @(
    Get-DualDexDocumentationProperty $completionVerificationScope 'finalRerunIndices' |
        ForEach-Object { [int] $_ }
)
$baseRunOnlyIndices = @(
    Get-DualDexDocumentationProperty $completionVerificationScope 'baseRunOnlyIndices' |
        ForEach-Object { [int] $_ }
)
$verificationIndices = @($finalRerunIndices + $baseRunOnlyIndices)
if (
    [string]::IsNullOrWhiteSpace($verificationStrategy) -or
    @($verificationIndices | Where-Object { $_ -lt 1 -or $_ -gt $ExpectedCount }).Count -gt 0 -or
    @($verificationIndices | Group-Object | Where-Object Count -ne 1).Count -gt 0 -or
    @($verificationIndices | Sort-Object -Unique).Count -ne $ExpectedCount
) {
    throw "Review completion verificationScope must partition indices 1..$ExpectedCount"
}
$verificationScope = [pscustomobject][ordered]@{
    strategy = $verificationStrategy
    finalRerunIndices = @($finalRerunIndices | Sort-Object)
    baseRunOnlyIndices = @($baseRunOnlyIndices | Sort-Object)
}

$eligibleManifest = @($manifestDocument | Where-Object {
    (Test-DualDexCorpusEntryInScope -EntryPath ([string] (Get-DualDexDocumentationProperty $_ 'EntryPath'))) -and
    ([string] (Get-DualDexDocumentationProperty $_ 'Extension')).ToLowerInvariant() -in @('.gb', '.gbc', '.gba')
})
$manifest = @(Select-DualDexUniqueManifestEntries -Manifest $eligibleManifest | Select-Object -First $ExpectedCount)
if ($manifest.Count -ne $ExpectedCount) {
    throw "Manifest contains $($manifest.Count) in-scope first-occurrence ROM identities; expected $ExpectedCount"
}
$manifestIndex = New-DualDexDocumentationIndex -Rows $manifest -Label 'manifest' -Key {
    param($row) Get-DualDexDocumentationProperty $row 'RomSha256'
}
$rawResults = @(Get-DualDexDocumentationProperty $report 'results')
$rawIndex = New-DualDexDocumentationIndex -Rows $rawResults -Label 'raw report' -Key {
    param($row) Get-DualDexDocumentationProperty (Get-DualDexDocumentationProperty $row 'result') 'sha256'
}
$reviewIndex = New-DualDexDocumentationIndex -Rows $reviewRows -Label 'review results' -Key {
    param($row) Get-DualDexDocumentationProperty $row 'romSha256'
}
if ($rawIndex.Count -ne $ExpectedCount -or $reviewIndex.Count -ne $ExpectedCount) {
    throw "Raw report and review results must each contain exactly $ExpectedCount unique ROM identities"
}
foreach ($sha in $manifestIndex.Keys) {
    if (-not $rawIndex.ContainsKey($sha) -or -not $reviewIndex.ContainsKey($sha)) {
        throw "ROM identity $sha is missing from the report/review join"
    }
}
foreach ($sha in @($rawIndex.Keys) + @($reviewIndex.Keys)) {
    if (-not $manifestIndex.ContainsKey($sha)) { throw "Unexpected ROM identity $sha in report/review join" }
}

$parserArtifactValue = if ([string]::IsNullOrWhiteSpace($ParserArtifacts)) {
    $null
} else {
    $artifactDocument = Read-DualDexDocumentationJson $ParserArtifacts 'parser artifact manifest'
    $artifactRows = if ($artifactDocument -is [System.Array]) {
        @($artifactDocument)
    } else {
        $declaredArtifacts = Get-DualDexDocumentationProperty $artifactDocument 'artifacts'
        if ($null -ne $declaredArtifacts) { @($declaredArtifacts) } else { @($artifactDocument) }
    }
    @($artifactRows | ForEach-Object {
        $relativePath = [string] (Get-DualDexDocumentationProperty $_ 'relativePath')
        $bytes = Get-DualDexDocumentationProperty $_ 'bytes'
        $sha256 = [string] (Get-DualDexDocumentationProperty $_ 'sha256')
        if (
            [string]::IsNullOrWhiteSpace($relativePath) -or
            [System.IO.Path]::IsPathRooted($relativePath) -or
            $relativePath -match '(^|[\\/])\.\.([\\/]|$)' -or
            $null -eq $bytes -or [long] $bytes -lt 0 -or
            $sha256 -notmatch '^[0-9a-fA-F]{64}$'
        ) {
            throw "Parser artifact manifest contains an invalid relativePath/bytes/sha256 record"
        }
        [pscustomobject][ordered]@{
            relativePath = $relativePath.Replace('\\', '/')
            bytes = [long] $bytes
            sha256 = $sha256.ToLowerInvariant()
        }
    })
}
$romRecords = [System.Collections.Generic.List[object]]::new()
for ($manifestOffset = 0; $manifestOffset -lt $manifest.Count; $manifestOffset++) {
    $manifestEntry = $manifest[$manifestOffset]
    $sha = ([string] (Get-DualDexDocumentationProperty $manifestEntry 'RomSha256')).ToLowerInvariant()
    $rawResult = $rawIndex[$sha]
    $review = $reviewIndex[$sha]
    $parse = Get-DualDexDocumentationProperty $rawResult 'result'
    if ([int] (Get-DualDexDocumentationProperty $review 'schemaVersion') -ne 3) {
        throw "Review row $sha must use schemaVersion 3"
    }
    if ([int] (Get-DualDexDocumentationProperty $review 'apkVersionCode') -ne $ApkVersionCode) {
        throw "Review row $sha belongs to a stale APK version code"
    }
    $rawSize = [long] (Get-DualDexDocumentationProperty $parse 'size')
    $manifestBytes = Get-DualDexDocumentationProperty $manifestEntry 'Bytes'
    if ($null -ne $manifestBytes -and $rawSize -ne [long] $manifestBytes) {
        throw "ROM byte-size mismatch for $sha"
    }
    if ($rawSize -ne [long] (Get-DualDexDocumentationProperty $review 'bytes')) {
        throw "Review byte-size mismatch for $sha"
    }
    if ([int] (Get-DualDexDocumentationProperty $review 'observationSchemaVersion') -ne 1) {
        throw "Review row $sha must use observationSchemaVersion 1"
    }
    $persistedObservation = Get-DualDexDocumentationProperty $review 'stableObservation'
    if ($null -eq $persistedObservation) {
        throw "Review row $sha must contain stableObservation"
    }
    $reviewHash = [string] (Get-DualDexDocumentationProperty $review 'observationHash')
    if ($reviewHash -notmatch '^[0-9a-fA-F]{64}$') { throw "Review observation hash is invalid for $sha" }
    $reviewDerivedObservation = ConvertTo-DualDexStableObservation -Record $review
    if (-not [string]::Equals(
        (ConvertTo-DualDexCanonicalJson -Value $persistedObservation),
        (ConvertTo-DualDexCanonicalJson -Value $reviewDerivedObservation),
        [System.StringComparison]::Ordinal
    )) {
        throw "Review persisted stable observation mismatch for ROM $sha"
    }
    $persistedHash = Get-DualDexObservationHash -Observation $persistedObservation
    $reviewDerivedHash = Get-DualDexObservationHash -Observation $reviewDerivedObservation
    $rawDerivedHash = Get-DualDexObservationHash -Observation (
        Get-DualDexRawStableObservation -RawResult $rawResult -RomSha256 $sha -VersionCode $ApkVersionCode
    )
    if (
        -not [string]::Equals($reviewHash, $persistedHash, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals($reviewHash, $reviewDerivedHash, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not [string]::Equals($reviewHash, $rawDerivedHash, [System.StringComparison]::OrdinalIgnoreCase)
    ) {
        throw "Raw/report review observation mismatch for ROM $sha"
    }

    $selectionStatus = [string] (Get-DualDexDocumentationProperty $parse 'status')
    $selectedFamily = Get-DualDexDocumentationProperty $parse 'selectedFamily'
    $selectedProbe = $null
    $layout = $null
    $isSelected = $selectionStatus -eq 'SELECTED'
    if ($isSelected) {
        $selected = @((Get-DualDexDocumentationProperty $parse 'probes') | Where-Object {
            [string] (Get-DualDexDocumentationProperty $_ 'family') -eq [string] $selectedFamily
        })
        if ($selected.Count -ne 1) { throw "Selected ROM $sha must have exactly one selected-family probe" }
        $selectedProbe = $selected[0]
        $layout = Get-DualDexDocumentationProperty $selectedProbe 'resolvedLayout'
        if ($null -eq $layout) { throw "Selected ROM $sha has no resolved layout" }
    }
    $generation = if ($null -eq $layout) { 'Unresolved' } else { [int] (Get-DualDexDocumentationProperty $layout 'generation') }
    $family = if ($isSelected) { [string] $selectedFamily } else { 'Unresolved' }
    $profile = if ($isSelected) { Get-DualDexDocumentationProperty $parse 'selectedProfile' } else { $null }
    $catalog = if ($isSelected) { Get-DualDexDocumentationProperty $rawResult 'catalog' } else { $null }
    $capabilities = @(Get-DualDexDocumentationProperty $parse 'capabilities')
    $reviewStatus = Get-DualDexReviewStatus $review
    $countEvidence = $null
    if ($isSelected) {
        $selectedCountEvidence = [ordered]@{}
        $countCapabilities = [ordered]@{
            species = 'SPECIES_CATALOG'; moves = 'MOVE_CATALOG'; abilities = 'ABILITIES'; types = 'TYPE_PRESENTATION'
        }
        foreach ($entry in $countCapabilities.GetEnumerator()) {
            $selectedCountEvidence[$entry.Key] = Copy-DualDexCapabilityEvidence (Get-DualDexCapability $capabilities $entry.Value)
        }
        $countEvidence = [pscustomobject] $selectedCountEvidence
    }
    $abilityMechanics = Get-DualDexCapability $capabilities 'ABILITY_MECHANICS'
    $encounterEvidence = Get-DualDexCapability $capabilities 'AREA_ENCOUNTERS'
    $persistence = Get-DualDexDocumentationProperty $rawResult 'persistence'
    $samples = Get-DualDexDocumentationProperty $rawResult 'samples'
    $romRecords.Add([pscustomobject][ordered]@{
        identity = [pscustomobject][ordered]@{
            manifestIndex = $manifestOffset + 1
            displayName = Get-DualDexDocumentationProperty $manifestEntry 'EntryPath'
            sha256 = $sha
            crc32 = Get-DualDexDocumentationProperty $parse 'crc32'
            bytes = $rawSize
            header = Copy-DualDexDocumentationFields `
                -Source (Get-DualDexDocumentationProperty $parse 'header') `
                -Names @('platform', 'title', 'gameCode', 'revision')
        }
        routing = [pscustomobject][ordered]@{
            status = $selectionStatus
            family = $family
            profile = $profile
            runnerUpMargin = Get-DualDexDocumentationProperty $parse 'runnerUpMargin'
            exactProfile = Get-DualDexDocumentationProperty $selectedProbe 'exactProfile'
        }
        generation = $(if ($generation -eq 'Unresolved') { $null } else { $generation })
        platform = Get-DualDexDocumentationProperty $layout 'platform'
        compatibility = [pscustomobject][ordered]@{
            percent = Get-DualDexDocumentationProperty $rawResult 'compatibilityPercent'
            resolvedFeatures = Get-DualDexDocumentationProperty $rawResult 'resolvedFeatureCount'
            expectedFeatures = Get-DualDexDocumentationProperty $rawResult 'expectedFeatureCount'
            manualReviewRequired = Get-DualDexDocumentationProperty $rawResult 'manualReviewRequired'
            dataCompatibility = Get-DualDexDocumentationProperty $rawResult 'dataCompatibility'
        }
        active = [pscustomobject][ordered]@{
            species = Get-DualDexDocumentationProperty $catalog 'species'
            moves = Get-DualDexDocumentationProperty $catalog 'moves'
            abilities = Get-DualDexDocumentationProperty $catalog 'abilities'
            types = Get-DualDexDocumentationProperty $catalog 'types'
            countEvidence = $countEvidence
        }
        engineLineage = [pscustomobject][ordered]@{
            baseFamily = $family
            structuralAncestor = $profile
            qualification = $(if ($null -eq $selectedProbe) { $null } elseif ([bool] (Get-DualDexDocumentationProperty $selectedProbe 'exactProfile')) { 'EXACT_PROFILE' } else { 'STRUCTURAL_ANCESTOR' })
            markers = @(Get-DualDexEngineMarkers $layout)
        }
        tableAbis = Get-DualDexTableAbis $layout
        expansionAbi = Copy-DualDexDocumentationFields `
            -Source (Get-DualDexDocumentationProperty $layout 'pokeemeraldExpansion') `
            -Names @(
                'versionMajor', 'versionMinor', 'versionPatch', 'speciesRecordSize',
                'speciesNameOffset', 'speciesNameWidth', 'categoryOffset', 'nationalDexOffset',
                'heightOffset', 'weightOffset', 'descriptionPointerOffset', 'frontSpritePointerOffset',
                'normalPalettePointerOffset', 'abilitiesOffset', 'growthRateOffset', 'levelUpPointerOffset',
                'teachablePointerOffset', 'eggMovePointerOffset', 'evolutionPointerOffset', 'moveRecordSize',
                'abilityRecordSize', 'abilityNameWidth', 'abilityDescriptionPointerOffset'
            )
        rulesets = $(if ($isSelected) {
            [pscustomobject][ordered]@{
                count = Get-DualDexDocumentationProperty $catalog 'learnsetRulesets'
                layoutEvidence = [pscustomobject][ordered]@{
                    tables = @(Get-DualDexDocumentationProperty $layout 'learnsetTables')
                    selector = Get-DualDexDocumentationProperty $layout 'learnsetSelector'
                }
                details = @(Get-DualDexRulesetDetails $catalog)
            }
        } else { $null })
        mechanics = [pscustomobject][ordered]@{
            abilityMechanics = [pscustomobject][ordered]@{
                materializedAbilityCount = Get-DualDexDocumentationProperty $catalog 'abilitiesWithMechanics'
                status = Get-DualDexDocumentationProperty $abilityMechanics 'status'
                count = Get-DualDexDocumentationProperty $abilityMechanics 'count'
                confidence = Get-DualDexDocumentationProperty $abilityMechanics 'confidence'
                reviewStatus = Get-DualDexDocumentationProperty $abilityMechanics 'reviewStatus'
                reasons = @(Get-DualDexDocumentationProperty $abilityMechanics 'reasons')
            }
            moveAcquisition = [pscustomobject][ordered]@{
                eggMoveLinks = Get-DualDexDocumentationProperty $catalog 'eggMoveLinks'
                machineMoveLinks = Get-DualDexDocumentationProperty $catalog 'machineMoveLinks'
                tutorMoveLinks = Get-DualDexDocumentationProperty $catalog 'tutorMoveLinks'
                eggMoves = Copy-DualDexCapabilityEvidence (Get-DualDexCapability $capabilities 'EGG_MOVES')
                machineMoves = Copy-DualDexCapabilityEvidence (Get-DualDexCapability $capabilities 'MACHINE_MOVES')
                tutorMoves = Copy-DualDexCapabilityEvidence (Get-DualDexCapability $capabilities 'TUTOR_MOVES')
            }
        }
        encounters = [pscustomobject][ordered]@{
            areas = Get-DualDexDocumentationProperty $catalog 'encounterAreas'
            coverage = Copy-DualDexCapabilityEvidence $encounterEvidence
        }
        gaps = @(Get-DualDexGaps $rawResult $reviewStatus)
        review = [pscustomobject][ordered]@{
            status = $reviewStatus
            reason = Get-DualDexDocumentationProperty $review 'decisionReason'
        }
        integrity = [pscustomobject][ordered]@{
            crossReferenceErrors = @(Get-DualDexDocumentationProperty $samples 'referenceErrors')
            catalogError = Get-DualDexDocumentationProperty $rawResult 'catalogError'
            persistence = [pscustomobject][ordered]@{
                bytes = Get-DualDexDocumentationProperty $persistence 'bytes'
                sections = Get-DualDexDocumentationProperty $persistence 'sections'
                writeMillis = Get-DualDexDocumentationProperty $persistence 'writeMillis'
                reopenMillis = Get-DualDexDocumentationProperty $persistence 'reopenMillis'
                error = Get-DualDexDocumentationProperty $rawResult 'persistenceError'
            }
        }
        provenance = [pscustomobject][ordered]@{
            reportSchemaVersion = 11
            apkVersionCode = $ApkVersionCode
            observationHash = $reviewHash.ToLowerInvariant()
            parserArtifacts = $parserArtifactValue
        }
        _generationGroup = $generation
        _familyGroup = $family
    })
}

$sortedRecords = @($romRecords | Sort-Object `
    @{ Expression = { if ($_._generationGroup -eq 'Unresolved') { [int]::MaxValue } else { [int] $_._generationGroup } } },
    @{ Expression = { [string] $_._familyGroup } },
    @{ Expression = { [int] $_.identity.manifestIndex } })
$groups = [System.Collections.Generic.List[object]]::new()
foreach ($generationGroup in @($sortedRecords | Group-Object _generationGroup)) {
    $families = [System.Collections.Generic.List[object]]::new()
    foreach ($familyGroup in @($generationGroup.Group | Group-Object _familyGroup)) {
        $roms = @($familyGroup.Group | ForEach-Object {
            $_.PSObject.Properties.Remove('_generationGroup')
            $_.PSObject.Properties.Remove('_familyGroup')
            $_
        })
        $families.Add([pscustomobject][ordered]@{
            family = $familyGroup.Name
            roms = $roms
        })
    }
    $generationValue = if ($generationGroup.Name -eq 'Unresolved') { 'Unresolved' } else { [int] $generationGroup.Name }
    $groups.Add([pscustomobject][ordered]@{
        generation = $generationValue
        families = @($families)
    })
}
$document = [pscustomobject][ordered]@{
    schemaVersion = 1
    reportSchemaVersion = 11
    apkVersionCode = $ApkVersionCode
    romCount = $romRecords.Count
    grouping = 'Generation -> ROM family -> manifest-first-occurrence ROM'
    verificationScope = $verificationScope
    groups = @($groups)
}
$json = (ConvertTo-Json -InputObject $document -Depth 64).Replace("`r`n", $newline)
$markdown = ConvertTo-DualDexMarkdown $document

if ([System.IO.Path]::GetFullPath($JsonOutput) -eq [System.IO.Path]::GetFullPath($MarkdownOutput)) {
    throw 'JSON and Markdown outputs must be different files'
}
Write-DualDexAtomicText $JsonOutput ($json + $newline)
Write-DualDexAtomicText $MarkdownOutput $markdown
Write-Output "Generated compatibility/property JSON: $([System.IO.Path]::GetFullPath($JsonOutput))"
Write-Output "Generated compatibility/property Markdown: $([System.IO.Path]::GetFullPath($MarkdownOutput))"
