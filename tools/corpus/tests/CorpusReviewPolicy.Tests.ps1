$policyPath = Join-Path $PSScriptRoot '..\CorpusReviewPolicy.ps1'
. $policyPath
$policyTokens = $null
$policyParseErrors = $null
$policyScriptAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $policyPath,
    [ref] $policyTokens,
    [ref] $policyParseErrors
)
if ($policyParseErrors.Count -gt 0) {
    throw "Corpus review policy has parse errors: $($policyParseErrors.Message -join '; ')"
}

$reviewScriptPath = Join-Path $PSScriptRoot '..\Invoke-DualDexCorpusReview.ps1'
$validationScriptPath = Join-Path $PSScriptRoot '..\Invoke-DualDexCorpusValidation.ps1'
$tokens = $null
$parseErrors = $null
$reviewScriptAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $reviewScriptPath,
    [ref] $tokens,
    [ref] $parseErrors
)
if ($parseErrors.Count -gt 0) {
    throw "Corpus review script has parse errors: $($parseErrors.Message -join '; ')"
}
$validationTokens = $null
$validationParseErrors = $null
$validationScriptAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $validationScriptPath,
    [ref] $validationTokens,
    [ref] $validationParseErrors
)
if ($validationParseErrors.Count -gt 0) {
    throw "Corpus validation script has parse errors: $($validationParseErrors.Message -join '; ')"
}

function Import-CorpusReviewFunction([string] $Name) {
    $definition = $reviewScriptAst.FindAll({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $Name
    }, $true) | Select-Object -First 1
    if ($null -eq $definition) {
        throw "Corpus review function '$Name' was not found"
    }
    Invoke-Expression "function global:$Name $($definition.Body.Extent.Text)"
}

function Import-CorpusPolicyFunction([string] $Name) {
    $definition = $policyScriptAst.FindAll({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $Name
    }, $true) | Select-Object -First 1
    if ($null -eq $definition) {
        throw "Corpus review policy function '$Name' was not found"
    }
    Invoke-Expression "function global:$Name $($definition.Body.Extent.Text)"
}

function Import-CorpusValidationFunction([string] $Name) {
    $definition = $validationScriptAst.FindAll({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $Name
    }, $true) | Select-Object -First 1
    if ($null -eq $definition) {
        throw "Corpus validation function '$Name' was not found"
    }
    Invoke-Expression "function global:$Name $($definition.Body.Extent.Text)"
}

function Invoke-AndCaptureError([scriptblock] $Action) {
    try {
        & $Action | Out-Null
        return $null
    } catch {
        return $_
    }
}

function New-DualDexTestRomEntry {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Root,

        [Parameter(Mandatory = $true)]
        [string] $FileName,

        [Parameter(Mandatory = $true)]
        [byte[]] $Payload,

        [string] $ArchiveRelativePath = 'sample.zip',

        [string] $EntryPath = $FileName
    )

    $romPath = Join-Path $Root $FileName
    [System.IO.File]::WriteAllBytes($romPath, $Payload)
    return [pscustomobject]@{
        ArchiveRelativePath = $ArchiveRelativePath
        EntryPath = $EntryPath
        Extension = [System.IO.Path]::GetExtension($FileName).ToLowerInvariant()
        RomSha256 = (Get-FileHash -LiteralPath $romPath -Algorithm SHA256).Hash.ToLowerInvariant()
        ExtractedPath = $romPath
        Bytes = $Payload.Length
    }
}

function Write-DualDexTestJson([string] $Path, [object] $Value) {
    [System.IO.File]::WriteAllText($Path, (ConvertTo-Json -InputObject $Value -Depth 12), [System.Text.UTF8Encoding]::new($false))
}

function New-DualDexTestReviewRow {
    param(
        [string] $RomSha256 = ('a' * 64),
        [int] $ApkVersionCode = 1000011,
        [int] $Offset = 4096,
        [int] $Count = 151,
        [string] $Sample = 'BULBASAUR',
        [object] $Confidence = 1.0
    )

    return [pscustomobject][ordered]@{
        schemaVersion = 3
        romSha256 = $RomSha256
        apkVersionCode = $ApkVersionCode
        compatibilityPercent = 100
        resolvedFeatureCount = 2
        expectedFeatureCount = 2
        manualReviewRequired = $false
        dataCompatibility = 'COMPLETE'
        selectionStatus = 'SELECTED'
        selectedFamily = 'EMERALD'
        selectedProfile = 'Pokemon Emerald (USA/Europe)'
        runnerUpMargin = 20
        matchedTableFirstRegisters = [ordered]@{
            speciesPhysical = @($Sample, 'IVYSAUR')
            speciesDexOrdered = @($Sample, 'IVYSAUR')
            moves = @('POUND')
        }
        capabilities = @(
            [ordered]@{
                capability = 'SPECIES_CATALOG'
                compatible = $true
                status = 'AVAILABLE'
                reviewStatus = 'VALIDATED'
                validatorReviewRecommended = $false
                validRecords = $Count
                totalRecords = $Count
                coveredRecords = $Count
                expectedRecords = $Count
                offset = $Offset
                count = $Count
                recordSize = 28
                elementSize = 2
                format = 'GEN3_BASE_STATS'
                confidence = $Confidence
                reasons = @('stable reason b', 'stable reason a')
            },
            [ordered]@{
                capability = 'MOVE_CATALOG'
                compatible = $true
                status = 'AVAILABLE'
                reviewStatus = 'VALIDATED'
                validatorReviewRecommended = $false
                validRecords = 354
                totalRecords = 354
                coveredRecords = 354
                expectedRecords = 354
                offset = 8192
                count = 354
                recordSize = 12
                elementSize = $null
                format = 'GEN3_MOVES'
                confidence = 0.99
                reasons = @()
            }
        )
        catalog = [ordered]@{
            species = $Count
            namedSpecies = $Count
            moves = 354
        }
        referenceErrors = @()
        catalogError = $null
        persistenceError = $null
        durationMillis = 42
        verifiedAt = '2026-08-11T00:00:00.0000000+03:00'
        parserReportJson = 'D:\Temp\generated-report-1.json'
        cachePath = 'D:\Temp\cache-a'
        generatedFileName = 'generated-a.sqlite'
        reopenMillis = 7
    }
}

function New-DualDexTestBaselineEntry([object] $ReviewRow) {
    $observation = ConvertTo-DualDexStableObservation -Record $ReviewRow
    return [ordered]@{
        romSha256 = $ReviewRow.romSha256
        apkVersionCode = [int] $ReviewRow.apkVersionCode
        observationSchemaVersion = 1
        observationHash = Get-DualDexObservationHash -Observation $observation
        observation = $observation
    }
}

function Set-DualDexTestObservationEnvelope {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Record,
        [object] $ObservationRecord = $Record
    )

    $observation = ConvertTo-DualDexStableObservation -Record $ObservationRecord
    $Record | Add-Member -NotePropertyName observationSchemaVersion -NotePropertyValue 1 -Force
    $Record | Add-Member -NotePropertyName observationHash -NotePropertyValue (Get-DualDexObservationHash -Observation $observation) -Force
    $Record | Add-Member -NotePropertyName stableObservation -NotePropertyValue $observation -Force
}

Import-CorpusReviewFunction 'Test-DualDexApkVersionCodeMatch'
Import-CorpusReviewFunction 'Test-DualDexReviewDecisionApplicable'
Import-CorpusReviewFunction 'Get-DualDexValidatedManifestEntry'
Import-CorpusReviewFunction 'Get-DualDexCompatibilityPercent'
Import-CorpusReviewFunction 'Assert-DualDexParserResultIdentity'
Import-CorpusReviewFunction 'ConvertTo-DualDexNormalizedReviewResult'
Import-CorpusValidationFunction 'Test-DualDexPathEqualOrAncestor'
Import-CorpusValidationFunction 'Assert-DualDexValidationOptions'

function Import-DualDexDifferentialReviewFunctions {
    foreach ($name in @(
        'ConvertTo-DualDexStableObservation',
        'ConvertTo-DualDexCanonicalJson',
        'Get-DualDexObservationHash',
        'Compare-DualDexObservation',
        'Test-DualDexDeltaDecisionApplicable'
    )) {
        if ($null -eq (Get-Command $name -ErrorAction SilentlyContinue)) {
            Import-CorpusPolicyFunction $name
        }
    }
}

function New-DualDexDifferentialFixture {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Root,
        [int] $BeforeOffset = 4096,
        [int] $AfterOffset = 4097,
        [int] $BeforeCount = 151,
        [int] $AfterCount = 152,
        [string] $BeforeSample = 'BULBASAUR',
        [string] $AfterSample = 'MISSINGNO',
        [int] $BeforeApkVersionCode = 1000011,
        [int] $AfterApkVersionCode = 1000011,
        [switch] $NoBaseline,
        [object] $AfterConfidence = 1.0
    )

    $workRoot = Join-Path $Root 'work'
    $reviewRoot = Join-Path $workRoot 'review'
    $manifestPath = Join-Path $Root 'manifest.json'
    [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
    $entry = New-DualDexTestRomEntry -Root $Root -FileName 'sample.gba' -Payload ([byte[]] @(1, 2, 3, 4))
    $before = New-DualDexTestReviewRow `
        -RomSha256 $entry.RomSha256 `
        -ApkVersionCode $BeforeApkVersionCode `
        -Offset $BeforeOffset `
        -Count $BeforeCount `
        -Sample $BeforeSample
    $after = New-DualDexTestReviewRow `
        -RomSha256 $entry.RomSha256 `
        -ApkVersionCode $AfterApkVersionCode `
        -Offset $AfterOffset `
        -Count $AfterCount `
        -Sample $AfterSample `
        -Confidence $AfterConfidence
    if ([string] $AfterConfidence -eq 'NaN') {
        $validObservationRow = New-DualDexTestReviewRow `
            -RomSha256 $entry.RomSha256 `
            -ApkVersionCode $AfterApkVersionCode `
            -Offset $AfterOffset `
            -Count $AfterCount `
            -Sample $AfterSample `
            -Confidence 1.0
        Set-DualDexTestObservationEnvelope -Record $after -ObservationRecord $validObservationRow
    } else {
        Set-DualDexTestObservationEnvelope -Record $after
    }
    Write-DualDexTestJson $manifestPath @($entry)
    Write-DualDexTestJson (Join-Path $reviewRoot 'review-results.json') @($after)
    Write-DualDexTestJson (Join-Path $reviewRoot 'review-state.json') ([ordered]@{
        schemaVersion = 3
        apkVersionCode = 1000011
        completedRomSha256 = @($entry.RomSha256)
    })
    if (-not $NoBaseline) {
        Write-DualDexTestJson (Join-Path $reviewRoot 'review-baseline.json') ([ordered]@{
            schemaVersion = 1
            observations = @(New-DualDexTestBaselineEntry -ReviewRow $before)
        })
    }
    return [pscustomobject]@{
        workRoot = $workRoot
        reviewRoot = $reviewRoot
        manifestPath = $manifestPath
        entry = $entry
        before = $before
        after = $after
    }
}

Describe 'DualDex corpus review policy' {
    It 'continues only when compatibility is 100 percent and no review is required' {
        (Test-DualDexCorpusNeedsReview -CompatibilityPercent 100 -ManualReviewRequired $false) | Should Be $false
        (Test-DualDexCorpusNeedsReview -CompatibilityPercent 99.99 -ManualReviewRequired $false) | Should Be $true
        (Test-DualDexCorpusNeedsReview -CompatibilityPercent 100 -ManualReviewRequired $true) | Should Be $true
    }

    It 'reads numeric completion first and supports legacy complete records' {
        $numeric = [pscustomobject]@{
            compatibilityPercent = 100
            manualReviewRequired = $false
            dataCompatibility = 'PARTIAL'
        }
        $legacy = [pscustomobject]@{ dataCompatibility = 'COMPLETE' }

        (Test-DualDexCorpusRecordComplete -Record $numeric) | Should Be $true
        (Test-DualDexCorpusRecordComplete -Record $legacy) | Should Be $true
    }

    It 'excludes every out of scope TCG spelling from the eligible denominator' {
        (Test-DualDexCorpusEntryInScope -EntryPath 'Pokemon Trading Card Game.gbc') | Should Be $false
        (Test-DualDexCorpusEntryInScope -EntryPath 'Pokemon TCG Neo.gba') | Should Be $false
        (Test-DualDexCorpusEntryInScope -EntryPath 'PokemonTCG.gbc') | Should Be $false
        (Test-DualDexCorpusEntryInScope -EntryPath 'Pokemon Crystal.gbc') | Should Be $true
    }

    It 'preserves manifest first occurrence order and keeps manifest positions 43 and 44 locked' {
        $manifest = @(1..44 | ForEach-Object {
            $ordinal = $_
            [pscustomobject]@{
                ArchiveRelativePath = ('{0:d3}.zip' -f $ordinal)
                EntryPath = "Pokemon Test $ordinal.gba"
                Extension = '.gba'
                RomSha256 = ('{0:x64}' -f $ordinal)
            }
        })
        $manifest += [pscustomobject]@{
            ArchiveRelativePath = '000-duplicate.zip'
            EntryPath = 'Lexically earlier duplicate of 43.gba'
            Extension = '.gba'
            RomSha256 = $manifest[42].RomSha256.ToUpperInvariant()
        }

        $selected = @(Select-DualDexUniqueManifestEntries -Manifest $manifest)

        $selected.Count | Should Be 44
        $selected[42].EntryPath | Should Be 'Pokemon Test 43.gba'
        $selected[43].EntryPath | Should Be 'Pokemon Test 44.gba'
    }

    It 'reuses results only within the supplied positive APK version code' {
        $current = [pscustomobject]@{ apkVersionCode = 1000011 }
        $stale = [pscustomobject]@{ apkVersionCode = 1000010 }
        $malformed = [pscustomobject]@{ apkVersionCode = 'not-a-version' }
        $legacy = [pscustomobject]@{ dataCompatibility = 'COMPLETE' }

        (Test-DualDexApkVersionCodeMatch -Record $current -ApkVersionCode 1000011) | Should Be $true
        (Test-DualDexApkVersionCodeMatch -Record $stale -ApkVersionCode 1000011) | Should Be $false
        (Test-DualDexApkVersionCodeMatch -Record $malformed -ApkVersionCode 1000011) | Should Be $false
        (Test-DualDexApkVersionCodeMatch -Record $legacy -ApkVersionCode 1000011) | Should Be $false
    }

    It 'applies manager decisions only to the reviewed ROM and APK version code' {
        $review = [pscustomobject]@{
            romSha256 = 'rom-a'
            apkVersionCode = 1000011
        }
        $bound = [pscustomobject]@{
            romSha256 = 'rom-a'
            decision = 'PARTIAL_ACCEPTED'
            reason = 'Reviewed decoded samples'
            apkVersionCode = 1000011
        }
        $legacy = [pscustomobject]@{
            romSha256 = 'rom-a'
            decision = 'PARTIAL_ACCEPTED'
            reason = 'Old unbound decision'
        }
        $staleVersion = $bound.PSObject.Copy()
        $staleVersion.apkVersionCode = 1000010
        $malformedVersion = $bound.PSObject.Copy()
        $malformedVersion.apkVersionCode = 'not-a-version'
        $wrongRom = $bound.PSObject.Copy()
        $wrongRom.romSha256 = 'rom-b'

        (Test-DualDexReviewDecisionApplicable -Decision $bound -ReviewResult $review -ApkVersionCode 1000011) | Should Be $true
        (Test-DualDexReviewDecisionApplicable -Decision $legacy -ReviewResult $review -ApkVersionCode 1000011) | Should Be $false
        (Test-DualDexReviewDecisionApplicable -Decision $staleVersion -ReviewResult $review -ApkVersionCode 1000011) | Should Be $false
        (Test-DualDexReviewDecisionApplicable -Decision $malformedVersion -ReviewResult $review -ApkVersionCode 1000011) | Should Be $false
        (Test-DualDexReviewDecisionApplicable -Decision $wrongRom -ReviewResult $review -ApkVersionCode 1000011) | Should Be $false
    }

    It 'validates manifest SHA, extracted path, byte size, and actual content before reuse' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-manifest-integrity-" + [guid]::NewGuid().ToString('N'))
        $romPath = Join-Path $fixtureRoot 'sample.gba'
        [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
        try {
            [System.IO.File]::WriteAllBytes($romPath, [byte[]] @(1, 2, 3, 4))
            $sha = (Get-FileHash -LiteralPath $romPath -Algorithm SHA256).Hash.ToLowerInvariant()
            $entry = [pscustomobject]@{
                ArchiveRelativePath = 'sample.zip'
                EntryPath = 'sample.gba'
                Extension = '.gba'
                RomSha256 = $sha
                ExtractedPath = $romPath
                Bytes = 4
            }

            $validated = Get-DualDexValidatedManifestEntry -Entry $entry -Index 1
            $validated.RomSha256 | Should Be $sha

            $badFormat = $entry.PSObject.Copy()
            $badFormat.RomSha256 = 'not-a-sha'
            (Invoke-AndCaptureError { Get-DualDexValidatedManifestEntry -Entry $badFormat -Index 1 }) | Should Not BeNullOrEmpty

            $missing = $entry.PSObject.Copy()
            $missing.ExtractedPath = Join-Path $fixtureRoot 'missing.gba'
            (Invoke-AndCaptureError { Get-DualDexValidatedManifestEntry -Entry $missing -Index 1 }) | Should Not BeNullOrEmpty

            $badSize = $entry.PSObject.Copy()
            $badSize.Bytes = 5
            (Invoke-AndCaptureError { Get-DualDexValidatedManifestEntry -Entry $badSize -Index 1 }) | Should Not BeNullOrEmpty

            $badHash = $entry.PSObject.Copy()
            $badHash.RomSha256 = '0' * 64
            (Invoke-AndCaptureError { Get-DualDexValidatedManifestEntry -Entry $badHash -Index 1 }) | Should Not BeNullOrEmpty
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'rejects non-finite and out-of-range compatibility percentages' {
        (Get-DualDexCompatibilityPercent -Result ([pscustomobject]@{ compatibilityPercent = 0 })) | Should Be 0
        (Get-DualDexCompatibilityPercent -Result ([pscustomobject]@{ compatibilityPercent = 100 })) | Should Be 100
        (Invoke-AndCaptureError { Get-DualDexCompatibilityPercent -Result ([pscustomobject]@{ compatibilityPercent = [double]::NaN }) }) | Should Not BeNullOrEmpty
        (Invoke-AndCaptureError { Get-DualDexCompatibilityPercent -Result ([pscustomobject]@{ compatibilityPercent = [double]::PositiveInfinity }) }) | Should Not BeNullOrEmpty
        (Invoke-AndCaptureError { Get-DualDexCompatibilityPercent -Result ([pscustomobject]@{ compatibilityPercent = -0.01 }) }) | Should Not BeNullOrEmpty
        (Invoke-AndCaptureError { Get-DualDexCompatibilityPercent -Result ([pscustomobject]@{ compatibilityPercent = 100.01 }) }) | Should Not BeNullOrEmpty
        (Invoke-AndCaptureError { Get-DualDexCompatibilityPercent -Result ([pscustomobject]@{ compatibilityPercent = 'invalid' }) }) | Should Not BeNullOrEmpty
    }

    It 'requires the parsed report identity to match the manifest ROM SHA' {
        $sha = 'a' * 64
        (Assert-DualDexParserResultIdentity -Result ([pscustomobject]@{ result = [pscustomobject]@{ sha256 = $sha } }) -ExpectedSha256 $sha) | Should Be $sha
        (Invoke-AndCaptureError { Assert-DualDexParserResultIdentity -Result ([pscustomobject]@{ result = [pscustomobject]@{ sha256 = ('b' * 64) } }) -ExpectedSha256 $sha }) | Should Not BeNullOrEmpty
        (Invoke-AndCaptureError { Assert-DualDexParserResultIdentity -Result ([pscustomobject]@{ result = [pscustomobject]@{} }) -ExpectedSha256 $sha }) | Should Not BeNullOrEmpty
    }

    It 'drops cached decision fields until a current decision is reapplied to a still-partial result' {
        $sha = 'd' * 64
        $normalized = ConvertTo-DualDexNormalizedReviewResult `
            -Record ([pscustomobject]@{
                romSha256 = $sha
                apkVersionCode = 1000011
                compatibilityPercent = 70
                manualReviewRequired = $true
                decision = 'PARTIAL_ACCEPTED'
                decisionReason = 'stale cached value'
            }) `
            -ManifestEntry ([pscustomobject]@{
                ArchiveRelativePath = 'current.zip'
                EntryPath = 'current.gba'
                Extension = '.gba'
                RomSha256 = $sha
                Bytes = 4
            }) `
            -Index 1 `
            -ApkVersionCode 1000011

        $normalized.decision | Should BeNullOrEmpty
        $normalized.decisionReason | Should BeNullOrEmpty
    }

    It 'recognizes equal and ancestor reset paths without rejecting a safe sibling or descendant' {
        (Test-DualDexPathEqualOrAncestor -CandidateAncestor 'D:\Temp\corpus' -Path 'D:\Temp\corpus') | Should Be $true
        (Test-DualDexPathEqualOrAncestor -CandidateAncestor 'D:\Temp\corpus' -Path 'D:\Temp\corpus\source') | Should Be $true
        (Test-DualDexPathEqualOrAncestor -CandidateAncestor 'D:\Temp\corpus\work' -Path 'D:\Temp\corpus\source') | Should Be $false
        (Test-DualDexPathEqualOrAncestor -CandidateAncestor 'D:\Temp\corpus\source\work' -Path 'D:\Temp\corpus\source') | Should Be $false
    }

    It 'rejects a non-default MaximumIndex unless bounded review is enabled' {
        (Invoke-AndCaptureError { Assert-DualDexValidationOptions -ReviewIncomplete:$false -MaximumIndex 50 }) | Should Not BeNullOrEmpty
        (Invoke-AndCaptureError { Assert-DualDexValidationOptions -ReviewIncomplete:$true -MaximumIndex 50 }) | Should BeNullOrEmpty
        (Invoke-AndCaptureError { Assert-DualDexValidationOptions -ReviewIncomplete:$false -MaximumIndex 2147483647 }) | Should BeNullOrEmpty
        (Invoke-AndCaptureError { Assert-DualDexValidationOptions -ReviewIncomplete:$false -MaximumIndex 2147483647 -Rebaseline:$true }) | Should Not BeNullOrEmpty
        (Invoke-AndCaptureError { Assert-DualDexValidationOptions -ReviewIncomplete:$true -MaximumIndex 2147483647 -Rebaseline:$true }) | Should BeNullOrEmpty
    }

    It 'clears stale pending and legacy results before publishing current empty-corpus state' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-state-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            [System.IO.File]::WriteAllText($manifestPath, '[]', [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText(
                (Join-Path $reviewRoot 'pending-review.json'),
                '{"romSha256":"stale","apkVersionCode":1000010}',
                [System.Text.UTF8Encoding]::new($false)
            )
            [System.IO.File]::WriteAllText(
                (Join-Path $reviewRoot 'review-results.json'),
                '[{"romSha256":"legacy","dataCompatibility":"COMPLETE"}]',
                [System.Text.UTF8Encoding]::new($false)
            )
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'pending-parser-report.json'), '{"stale":true}', [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'pending-parser-report.md'), 'stale', [System.Text.UTF8Encoding]::new($false))

            & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            (Test-Path -LiteralPath (Join-Path $reviewRoot 'pending-review.json')) | Should Be $false
            (Test-Path -LiteralPath (Join-Path $reviewRoot 'pending-parser-report.json')) | Should Be $false
            (Test-Path -LiteralPath (Join-Path $reviewRoot 'pending-parser-report.md')) | Should Be $false
            @((Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)).Count | Should Be 0
            $state = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-state.json') -Raw | ConvertFrom-Json
            $complete = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-complete.json') -Raw | ConvertFrom-Json
            $state.apkVersionCode | Should Be 1000011
            $complete.apkVersionCode | Should Be 1000011
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'requires a positive APK version code' {
        $parameter = (Get-Command $reviewScriptPath).Parameters['ApkVersionCode']
        $mandatory = @($parameter.Attributes | Where-Object { $_ -is [System.Management.Automation.ParameterAttribute] }) | Select-Object -First 1
        $range = @($parameter.Attributes | Where-Object { $_ -is [System.Management.Automation.ValidateRangeAttribute] }) | Select-Object -First 1
        $maximumIndexParameter = (Get-Command $reviewScriptPath).Parameters['MaximumIndex']
        $maximumIndexRange = @($maximumIndexParameter.Attributes | Where-Object { $_ -is [System.Management.Automation.ValidateRangeAttribute] }) | Select-Object -First 1

        $mandatory.Mandatory | Should Be $true
        $range.MinRange | Should Be 1
        $maximumIndexRange.MinRange | Should Be 1
    }

    It 'rebaseline clears same-version rows and state before recomputing' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-rebaseline-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            [System.IO.File]::WriteAllText($manifestPath, '[]', [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText(
                (Join-Path $reviewRoot 'review-state.json'),
                '{"schemaVersion":3,"apkVersionCode":1000011,"completedRomSha256":["same-version"]}',
                [System.Text.UTF8Encoding]::new($false)
            )
            [System.IO.File]::WriteAllText(
                (Join-Path $reviewRoot 'review-results.json'),
                '[{"schemaVersion":3,"romSha256":"same-version","apkVersionCode":1000011,"compatibilityPercent":100,"manualReviewRequired":false}]',
                [System.Text.UTF8Encoding]::new($false)
            )

            & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -Rebaseline -SkipBuild | Out-Null

            @((Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)).Count | Should Be 0
            $state = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-state.json') -Raw | ConvertFrom-Json
            @($state.completedRomSha256).Count | Should Be 0
            $state.apkVersionCode | Should Be 1000011
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'ignores legacy and other-version decisions instead of validating them as current' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-stale-decisions-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        $decisionsPath = Join-Path $reviewRoot 'review-decisions.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            [System.IO.File]::WriteAllText($manifestPath, '[]', [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText(
                $decisionsPath,
                '[{"romSha256":"legacy","decision":"OBSOLETE"},{"romSha256":"old","apkVersionCode":1000010,"decision":"OBSOLETE"}]',
                [System.Text.UTF8Encoding]::new($false)
            )

            $reviewErrors = @()
            & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild -ErrorVariable +reviewErrors | Out-Null

            @($reviewErrors).Count | Should Be 0
            (Test-Path -LiteralPath (Join-Path $reviewRoot 'review-complete.json') -PathType Leaf) | Should Be $true
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'validates manifest integrity before cached reuse or marker and result mutation' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-cache-integrity-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $entry = [pscustomobject]@{
                ArchiveRelativePath = 'sample.zip'
                EntryPath = 'sample.gba'
                Extension = '.gba'
                RomSha256 = 'invalid-sha'
                ExtractedPath = (Join-Path $fixtureRoot 'missing.gba')
                Bytes = 4
            }
            Write-DualDexTestJson $manifestPath @($entry)
            $originalResults = '[{"romSha256":"invalid-sha","apkVersionCode":1000011,"compatibilityPercent":100,"manualReviewRequired":false}]'
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'review-results.json'), $originalResults, [System.Text.UTF8Encoding]::new($false))
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-state.json') ([ordered]@{
                apkVersionCode = 1000011
                completedRomSha256 = @('invalid-sha')
            })
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'pending-review.json'), 'do-not-mutate', [System.Text.UTF8Encoding]::new($false))

            $error = Invoke-AndCaptureError { & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild }

            $error | Should Not BeNullOrEmpty
            (Get-Content -LiteralPath (Join-Path $reviewRoot 'pending-review.json') -Raw) | Should Be 'do-not-mutate'
            (Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw) | Should Be $originalResults
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'rejects invalid cached compatibility before mutating current review artifacts' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-bad-percent-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $entry = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'sample.gba' -Payload ([byte[]] @(9, 8, 7, 6))
            Write-DualDexTestJson $manifestPath @($entry)
            $originalResults = ConvertTo-Json -Compress -InputObject @([ordered]@{
                romSha256 = $entry.RomSha256
                apkVersionCode = 1000011
                compatibilityPercent = 101
                manualReviewRequired = $false
            })
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'review-results.json'), $originalResults, [System.Text.UTF8Encoding]::new($false))
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-state.json') ([ordered]@{
                apkVersionCode = 1000011
                completedRomSha256 = @($entry.RomSha256)
            })
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'pending-review.json'), 'do-not-mutate', [System.Text.UTF8Encoding]::new($false))

            $error = Invoke-AndCaptureError { & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild }

            $error | Should Not BeNullOrEmpty
            (Get-Content -LiteralPath (Join-Path $reviewRoot 'pending-review.json') -Raw) | Should Be 'do-not-mutate'
            (Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw) | Should Be $originalResults
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'rejects duplicate current decisions before mutating review artifacts' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-duplicate-decisions-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $sha = 'a' * 64
            [System.IO.File]::WriteAllText($manifestPath, '[]', [System.Text.UTF8Encoding]::new($false))
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-decisions.json') @(
                [ordered]@{ romSha256 = $sha; apkVersionCode = 1000011; decision = 'PARTIAL_ACCEPTED'; reason = 'first' },
                [ordered]@{ romSha256 = $sha; apkVersionCode = 1000011; decision = 'PARTIAL_ACCEPTED'; reason = 'second' }
            )
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'pending-review.json'), 'do-not-mutate', [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'review-results.json'), '[]', [System.Text.UTF8Encoding]::new($false))

            $error = Invoke-AndCaptureError { & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild }

            $error | Should Not BeNullOrEmpty
            (Get-Content -LiteralPath (Join-Path $reviewRoot 'pending-review.json') -Raw) | Should Be 'do-not-mutate'
            (Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw) | Should Be '[]'
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'rejects a blank current decision reason while ignoring stale decisions' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-blank-decision-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            [System.IO.File]::WriteAllText($manifestPath, '[]', [System.Text.UTF8Encoding]::new($false))
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-decisions.json') @(
                [ordered]@{ romSha256 = ('b' * 64); apkVersionCode = 1000010; decision = 'OBSOLETE'; reason = '' },
                [ordered]@{ romSha256 = ('c' * 64); apkVersionCode = 1000011; decision = 'PARTIAL_ACCEPTED'; reason = '   ' }
            )
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'pending-review.json'), 'do-not-mutate', [System.Text.UTF8Encoding]::new($false))

            $error = Invoke-AndCaptureError { & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild }

            $error | Should Not BeNullOrEmpty
            (Get-Content -LiteralPath (Join-Path $reviewRoot 'pending-review.json') -Raw) | Should Be 'do-not-mutate'
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'does not count an obsolete accepted decision for a now-complete result' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-obsolete-decision-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $entry = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'sample.gba' -Payload ([byte[]] @(4, 3, 2, 1))
            Write-DualDexTestJson $manifestPath @($entry)
            $cachedResult = [pscustomobject][ordered]@{
                romSha256 = $entry.RomSha256
                apkVersionCode = 1000011
                compatibilityPercent = 100
                manualReviewRequired = $false
                dataCompatibility = 'COMPLETE'
                decision = 'PARTIAL_ACCEPTED'
                decisionReason = 'old result'
            }
            Set-DualDexTestObservationEnvelope -Record $cachedResult
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-results.json') @($cachedResult)
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-state.json') ([ordered]@{
                apkVersionCode = 1000011
                completedRomSha256 = @($entry.RomSha256)
                acceptedDesignExceptions = @($entry.RomSha256)
            })
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-decisions.json') @([ordered]@{
                romSha256 = $entry.RomSha256
                apkVersionCode = 1000011
                decision = 'PARTIAL_ACCEPTED'
                reason = 'formerly partial'
            })

            & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            $state = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-state.json') -Raw | ConvertFrom-Json
            $complete = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-complete.json') -Raw | ConvertFrom-Json
            $result = @(Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)[0]
            (@($state.completedRomSha256) -contains $entry.RomSha256) | Should Be $true
            @($state.acceptedDesignExceptions).Count | Should Be 0
            $complete.fullyCompatible | Should Be 1
            $complete.acceptedDesignExceptions | Should Be 0
            $result.decision | Should BeNullOrEmpty
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'limits the exact eligible denominator and retained rows to MaximumIndex' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-window-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $manifest = @(1..60 | ForEach-Object {
                $romPath = Join-Path $fixtureRoot ('rom-{0:D3}.gba' -f $_)
                [System.IO.File]::WriteAllBytes($romPath, [System.BitConverter]::GetBytes([int] $_))
                $sha = (Get-FileHash -LiteralPath $romPath -Algorithm SHA256).Hash.ToLowerInvariant()
                [pscustomobject]@{
                    ArchiveRelativePath = ('{0:D3}.zip' -f $_)
                    EntryPath = ('Pokemon Test {0:D3}.gba' -f $_)
                    Extension = '.gba'
                    RomSha256 = $sha
                    ExtractedPath = $romPath
                    Bytes = 4
                }
            })
            $results = @(1..60 | ForEach-Object {
                $manifestEntry = $manifest[$_ - 1]
                $cachedResult = [pscustomobject][ordered]@{
                    schemaVersion = 3
                    index = 999
                    archive = 'stale.zip'
                    entry = 'stale.gba'
                    bytes = 999
                    romSha256 = $manifestEntry.RomSha256
                    apkVersionCode = 1000011
                    compatibilityPercent = 100
                    manualReviewRequired = $false
                    dataCompatibility = 'COMPLETE'
                }
                Set-DualDexTestObservationEnvelope -Record $cachedResult
                $cachedResult
            })
            $completed = @($manifest.RomSha256)
            [System.IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText((Join-Path $reviewRoot 'review-results.json'), ($results | ConvertTo-Json -Depth 5), [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText(
                (Join-Path $reviewRoot 'review-state.json'),
                ([ordered]@{ schemaVersion = 3; apkVersionCode = 1000011; completedRomSha256 = $completed } | ConvertTo-Json -Depth 5),
                [System.Text.UTF8Encoding]::new($false)
            )

            $output = @(& $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -MaximumIndex 50 -SkipBuild)

            ($output -join "`n") | Should Match 'Eligible unique ROMs: 50'
            $retainedResults = @(Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)
            $retainedResults.Count | Should Be 50
            @($retainedResults | Where-Object romSha256 -eq $manifest[50].RomSha256).Count | Should Be 0
            $retainedResults[0].index | Should Be 1
            $retainedResults[0].archive | Should Be '001.zip'
            $retainedResults[0].entry | Should Be 'Pokemon Test 001.gba'
            $retainedResults[0].bytes | Should Be 4
            $retainedResults[49].index | Should Be 50
            $state = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-state.json') -Raw | ConvertFrom-Json
            $complete = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-complete.json') -Raw | ConvertFrom-Json
            $state.eligibleUniqueRoms | Should Be 50
            $state.discoveredUniqueRoms | Should Be 60
            $state.maximumIndex | Should Be 50
            $complete.eligibleUniqueRoms | Should Be 50
            $complete.discoveredUniqueRoms | Should Be 60
            $complete.maximumIndex | Should Be 50
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'preserves manifest order and rewrites cached manifest metadata' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-tie-order-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $first = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'first.gba' -Payload ([byte[]] @(1, 1, 1, 1)) -ArchiveRelativePath 'same.zip' -EntryPath 'same.gba'
            $second = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'second.gba' -Payload ([byte[]] @(2, 2, 2, 2)) -ArchiveRelativePath 'same.zip' -EntryPath 'same.gba'
            $manifest = @($first, $second | Sort-Object RomSha256 -Descending)
            Write-DualDexTestJson $manifestPath $manifest
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-results.json') @($manifest | ForEach-Object {
                $cachedResult = [pscustomobject][ordered]@{
                    index = 99
                    archive = 'old.zip'
                    entry = 'old.gba'
                    bytes = 99
                    romSha256 = $_.RomSha256
                    apkVersionCode = 1000011
                    compatibilityPercent = 100
                    manualReviewRequired = $false
                }
                Set-DualDexTestObservationEnvelope -Record $cachedResult
                $cachedResult
            })
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-state.json') ([ordered]@{
                apkVersionCode = 1000011
                completedRomSha256 = @($manifest.RomSha256)
            })

            & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            $results = @(Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)
            @($results.romSha256) -join ',' | Should Be (@($manifest.RomSha256) -join ',')
            @($results.index) -join ',' | Should Be '1,2'
            @($results.archive | Select-Object -Unique) -join ',' | Should Be 'same.zip'
            @($results.entry | Select-Object -Unique) -join ',' | Should Be 'same.gba'
            @($results.bytes | Select-Object -Unique) -join ',' | Should Be '4'
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 're-extracts a same-size corrupted cached payload from the authoritative archive on the second public-wrapper run' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-wrapper-extraction-integrity-" + [guid]::NewGuid().ToString('N'))
        $sourceRoot = Join-Path $fixtureRoot 'source'
        $workRoot = Join-Path $fixtureRoot 'work'
        $archivePath = Join-Path $sourceRoot 'sample.7z'
        $authoritativePath = Join-Path $sourceRoot 'authoritative.gba'
        $authoritativeSecondPath = Join-Path $sourceRoot 'authoritative-second.gbc'
        $fakeSevenZip = Join-Path $fixtureRoot 'fake-7z.ps1'
        [System.IO.Directory]::CreateDirectory($sourceRoot) | Out-Null
        try {
            [System.IO.File]::WriteAllBytes($archivePath, [byte[]] @(7, 7, 7, 7))
            [System.IO.File]::WriteAllBytes($authoritativePath, [System.Text.Encoding]::ASCII.GetBytes('ABCD'))
            [System.IO.File]::WriteAllBytes($authoritativeSecondPath, [System.Text.Encoding]::ASCII.GetBytes('EFGH'))
            $fakeScript = @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]] $FakeArgs)
$global:LASTEXITCODE = 0
$command = $FakeArgs[0]
$separator = [Array]::IndexOf($FakeArgs, '--')
$archive = if ($separator -ge 0) { $FakeArgs[$separator + 1] } else { $null }
if ($command -eq 'l') {
    $payload = Join-Path (Split-Path -Parent $archive) 'authoritative.gba'
    $secondPayload = Join-Path (Split-Path -Parent $archive) 'authoritative-second.gbc'
    '----------'
    'Path = game.gba'
    "Size = $((Get-Item -LiteralPath $payload).Length)"
    'Attributes = A'
    ''
    'Path = nested/second.gbc'
    "Size = $((Get-Item -LiteralPath $secondPayload).Length)"
    'Attributes = A'
    ''
    return
}
if ($command -eq 'x') {
    $outputArg = @($FakeArgs | Where-Object { $_.StartsWith('-o') }) | Select-Object -First 1
    $outputRoot = $outputArg.Substring(2)
    [System.IO.Directory]::CreateDirectory($outputRoot) | Out-Null
    [System.IO.Directory]::CreateDirectory((Join-Path $outputRoot 'nested')) | Out-Null
    [System.IO.File]::Copy(
        (Join-Path (Split-Path -Parent $archive) 'authoritative.gba'),
        (Join-Path $outputRoot 'game.gba'),
        $true
    )
    [System.IO.File]::Copy(
        (Join-Path (Split-Path -Parent $archive) 'authoritative-second.gbc'),
        (Join-Path $outputRoot 'nested\second.gbc'),
        $true
    )
    return
}
'Everything is Ok'
'@
            [System.IO.File]::WriteAllText($fakeSevenZip, $fakeScript, [System.Text.UTF8Encoding]::new($false))

            & $validationScriptPath `
                -SourceRoot $sourceRoot `
                -WorkRoot $workRoot `
                -ApkVersionCode 1000011 `
                -SevenZip $fakeSevenZip `
                -SkipBuild | Out-Null
            $firstManifest = @(Get-Content -LiteralPath (Join-Path $workRoot 'rom-manifest.json') -Raw | ConvertFrom-Json)
            $firstEntry = $firstManifest | Where-Object EntryPath -eq 'game.gba'
            $secondEntry = $firstManifest | Where-Object EntryPath -eq 'nested/second.gbc'
            $firstSha = [string] $firstEntry.RomSha256
            $secondSha = [string] $secondEntry.RomSha256
            $extractedPath = [string] $firstEntry.ExtractedPath
            [System.IO.File]::WriteAllBytes($extractedPath, [System.Text.Encoding]::ASCII.GetBytes('WXYZ'))

            & $validationScriptPath `
                -SourceRoot $sourceRoot `
                -WorkRoot $workRoot `
                -ApkVersionCode 1000011 `
                -SevenZip $fakeSevenZip `
                -SkipBuild | Out-Null

            $secondManifest = @(Get-Content -LiteralPath (Join-Path $workRoot 'rom-manifest.json') -Raw | ConvertFrom-Json)
            [System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($extractedPath)) | Should Be 'ABCD'
            ($secondManifest | Where-Object EntryPath -eq 'game.gba').RomSha256 | Should Be $firstSha
            ($secondManifest | Where-Object EntryPath -eq 'nested/second.gbc').RomSha256 | Should Be $secondSha
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'exposes and forwards the review identity and index window from the public validation wrapper' {
        $command = Get-Command $validationScriptPath
        $versionParameter = $command.Parameters['ApkVersionCode']
        $versionMandatory = @($versionParameter.Attributes | Where-Object { $_ -is [System.Management.Automation.ParameterAttribute] }) | Select-Object -First 1
        $versionRange = @($versionParameter.Attributes | Where-Object { $_ -is [System.Management.Automation.ValidateRangeAttribute] }) | Select-Object -First 1
        $maximumParameter = $command.Parameters['MaximumIndex']
        $maximumRange = @($maximumParameter.Attributes | Where-Object { $_ -is [System.Management.Automation.ValidateRangeAttribute] }) | Select-Object -First 1
        $rebaselineParameter = $command.Parameters['Rebaseline']
        $reviewInvocation = $validationScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.Extent.Text -match 'Invoke-DualDexCorpusReview\.ps1'
        }, $true) | Select-Object -First 1

        $versionMandatory.Mandatory | Should Be $true
        $versionRange.MinRange | Should Be 1
        $maximumRange.MinRange | Should Be 1
        $rebaselineParameter.ParameterType.FullName | Should Be 'System.Management.Automation.SwitchParameter'
        $reviewInvocation.Extent.Text | Should Match '-ApkVersionCode\s+\$ApkVersionCode'
        $reviewInvocation.Extent.Text | Should Match '-MaximumIndex\s+\$MaximumIndex'
        $reviewInvocation.Extent.Text | Should Match '-Rebaseline:\$Rebaseline'
    }

    It 'makes the bounded ReviewIncomplete branch terminal before the all-ROM parser fallback' {
        $reviewBranch = $validationScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.IfStatementAst] -and
                @($node.Clauses | Where-Object { $_.Item1.Extent.Text -eq '$ReviewIncomplete' }).Count -gt 0
        }, $true) | Select-Object -First 1
        $reviewClause = @($reviewBranch.Clauses | Where-Object { $_.Item1.Extent.Text -eq '$ReviewIncomplete' }) | Select-Object -First 1
        $lastStatement = @($reviewClause.Item2.Statements) | Select-Object -Last 1

        $reviewBranch | Should Not BeNullOrEmpty
        $lastStatement.GetType().FullName | Should Be 'System.Management.Automation.Language.ReturnStatementAst'
        $reviewBranch.Extent.EndOffset | Should BeLessThan ($validationScriptAst.Extent.Text.IndexOf("& `$parserCli `$romRoot '--json'"))
    }

    It 'wires reset and option guards before deletion and unbounded corpus work' {
        $resetGuard = $validationScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Test-DualDexPathEqualOrAncestor'
        }, $true) | Select-Object -First 1
        $optionGuard = $validationScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Assert-DualDexValidationOptions'
        }, $true) | Select-Object -First 1
        $resetDelete = $validationScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Remove-Item'
        }, $true) | Select-Object -First 1

        $resetGuard | Should Not BeNullOrEmpty
        $optionGuard | Should Not BeNullOrEmpty
        $resetGuard.Extent.StartOffset | Should BeLessThan $resetDelete.Extent.StartOffset
        $optionGuard.Extent.StartOffset | Should BeLessThan $resetDelete.Extent.StartOffset
    }

    It 'checks parsed report SHA in the live path and publishes JSON atomically' {
        $identityCall = $reviewScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and $node.GetCommandName() -eq 'Assert-DualDexParserResultIdentity'
        }, $true) | Select-Object -First 1
        $writeFunction = $reviewScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Write-JsonFile'
        }, $true) | Select-Object -First 1

        $identityCall | Should Not BeNullOrEmpty
        $identityCall.Extent.StartOffset | Should BeGreaterThan ($reviewScriptAst.Extent.Text.IndexOf('& $parserCli $romPath'))
        $writeFunction.Body.Extent.Text | Should Match 'GetRandomFileName'
        $writeFunction.Body.Extent.Text | Should Match '\[System\.IO\.File\]::Replace'
    }

    It 'publishes each terminal marker before the baseline commit so marker write failure cannot advance baseline' {
        $commitCalls = @($reviewScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.GetCommandName() -eq 'Commit-DualDexBaseline'
        }, $true) | Sort-Object { $_.Extent.StartOffset })
        $pendingWrite = $reviewScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.GetCommandName() -eq 'Write-JsonFile' -and
                $node.Extent.Text -match '^Write-JsonFile\s+\$pendingPath\b'
        }, $true) | Sort-Object { $_.Extent.StartOffset } | Select-Object -Last 1
        $completeWrite = $reviewScriptAst.FindAll({
            param($node)
            $node -is [System.Management.Automation.Language.CommandAst] -and
                $node.GetCommandName() -eq 'Write-JsonFile' -and
                $node.Extent.Text -match '^Write-JsonFile\s+\$completePath\b'
        }, $true) | Select-Object -First 1

        $commitCalls.Count | Should Be 2
        $pendingWrite | Should Not BeNullOrEmpty
        $completeWrite | Should Not BeNullOrEmpty
        $pendingWrite.Extent.StartOffset | Should BeLessThan $commitCalls[0].Extent.StartOffset
        $completeWrite.Extent.StartOffset | Should BeLessThan $commitCalls[1].Extent.StartOffset
    }

    It 'hashes every stable parser observation field and reports sorted exact changes' {
        Import-DualDexDifferentialReviewFunctions
        $before = New-DualDexTestReviewRow -Offset 4096 -Count 151 -Sample 'BULBASAUR'
        $after = New-DualDexTestReviewRow -Offset 8192 -Count 152 -Sample 'MISSINGNO'

        $beforeObservation = ConvertTo-DualDexStableObservation -Record $before
        $afterObservation = ConvertTo-DualDexStableObservation -Record $after
        $changes = @(Compare-DualDexObservation -Before $beforeObservation -After $afterObservation)

        (Get-DualDexObservationHash -Observation $beforeObservation) | Should Not Be (Get-DualDexObservationHash -Observation $afterObservation)
        (@($changes.path) -contains '/capabilities/SPECIES_CATALOG/offset') | Should Be $true
        (@($changes.path) -contains '/capabilities/SPECIES_CATALOG/count') | Should Be $true
        (@($changes.path) -contains '/matchedTableFirstRegisters/speciesPhysical/0') | Should Be $true
        (@($changes.path) -join "`n") | Should Be (@(Get-DualDexOrdinalSortedStrings -Value @($changes.path)) -join "`n")
        ($changes | Where-Object path -eq '/capabilities/SPECIES_CATALOG/offset').before | Should Be 4096
        ($changes | Where-Object path -eq '/capabilities/SPECIES_CATALOG/offset').after | Should Be 8192
    }

    It 'keeps schema eleven ruleset details out of the numeric catalog observation envelope' {
        $record = New-DualDexTestReviewRow -Offset 4096 -Count 151 -Sample 'BULBASAUR'
        $record.catalog['rulesetDetails'] = @(
            [pscustomobject]@{
                id = 'ruleset-00001234'
                label = 'Expanded 1'
                sourceOffset = 4660
                confidence = 0.875
                primary = $false
                levelUpSelector = [pscustomobject]@{
                    saveBlock1ByteOffset = 32
                    mask = 4
                    expectedValue = 4
                }
            }
        )

        $observation = ConvertTo-DualDexStableObservation -Record $record

        $observation.catalog.species | Should Be 151
        $observation.catalog.PSObject.Properties['rulesetDetails'] | Should BeNullOrEmpty
    }

    It 'ignores timing paths and generated filenames while stabilizing capability order' {
        Import-DualDexDifferentialReviewFunctions
        $before = New-DualDexTestReviewRow
        $after = New-DualDexTestReviewRow
        $after.durationMillis = 9999
        $after.verifiedAt = '2099-01-01T00:00:00Z'
        $after.parserReportJson = 'E:\Temp\other-report.json'
        $after.cachePath = 'E:\Temp\other-cache'
        $after.generatedFileName = 'other.sqlite'
        $after.reopenMillis = 999
        $after.capabilities = @($after.capabilities | Sort-Object capability -Descending)

        $beforeObservation = ConvertTo-DualDexStableObservation -Record $before
        $afterObservation = ConvertTo-DualDexStableObservation -Record $after

        (Get-DualDexObservationHash -Observation $beforeObservation) | Should Be (Get-DualDexObservationHash -Observation $afterObservation)
        @(Compare-DualDexObservation -Before $beforeObservation -After $afterObservation).Count | Should Be 0
        (ConvertTo-DualDexCanonicalJson -Value $beforeObservation) | Should Be (ConvertTo-DualDexCanonicalJson -Value $afterObservation)
    }

    It 'preserves null empty singleton and multi-element array identity in canonical JSON and hashes' {
        Import-DualDexDifferentialReviewFunctions
        $nullJson = ConvertTo-DualDexCanonicalJson -Value $null
        $emptyJson = ConvertTo-DualDexCanonicalJson -Value ([object[]] @())
        $singletonJson = ConvertTo-DualDexCanonicalJson -Value ([object[]] @('x'))
        $multipleJson = ConvertTo-DualDexCanonicalJson -Value ([object[]] @('x', 'y'))
        $nestedJson = ConvertTo-DualDexCanonicalJson -Value ([ordered]@{
            nullValue = $null
            empty = [object[]] @()
            singleton = [object[]] @('x')
        })

        $nullJson | Should Be 'null'
        $emptyJson | Should Be '[]'
        $singletonJson | Should Be '["x"]'
        $multipleJson | Should Be '["x","y"]'
        $nestedJson | Should Be '{"empty":[],"nullValue":null,"singleton":["x"]}'
        @(
            Get-DualDexObservationHash -Observation $null
            Get-DualDexObservationHash -Observation ([object[]] @())
            Get-DualDexObservationHash -Observation ([object[]] @('x'))
            Get-DualDexObservationHash -Observation ([object[]] @('x', 'y'))
        ) | Select-Object -Unique | Measure-Object | Select-Object -ExpandProperty Count | Should Be 4

        $nullToEmpty = @(Compare-DualDexObservation `
            -Before ([ordered]@{ value = $null }) `
            -After ([ordered]@{ value = [object[]] @() }))
        $emptyToSingleton = @(Compare-DualDexObservation `
            -Before ([ordered]@{ value = [object[]] @() }) `
            -After ([ordered]@{ value = [object[]] @('x') }))
        @($nullToEmpty.path) -join ',' | Should Be '/value'
        $nullToEmpty[0].beforeJson | Should Be 'null'
        $nullToEmpty[0].afterJson | Should Be '[]'
        @($emptyToSingleton.path) -join ',' | Should Be '/value,/value/0'
    }

    It 'uses escaped JSON Pointer paths without dotted-key collisions' {
        Import-DualDexDifferentialReviewFunctions
        $before = [ordered]@{
            a = [ordered]@{ 'b.c' = 1 }
            'a.b' = [ordered]@{ c = 10 }
            'a/b~c' = 100
        }
        $after = [ordered]@{
            a = [ordered]@{ 'b.c' = 2 }
            'a.b' = [ordered]@{ c = 20 }
            'a/b~c' = 200
        }

        $changes = @(Compare-DualDexObservation -Before $before -After $after)

        @($changes.path) -join ',' | Should Be '/a.b/c,/a/b.c,/a~1b~0c'
        ($changes | Where-Object path -eq '/a/b.c').after | Should Be 2
        ($changes | Where-Object path -eq '/a.b/c').after | Should Be 20
        ($changes | Where-Object path -eq '/a~1b~0c').after | Should Be 200
    }

    It 'fails closed for non-finite relevant observation numbers' {
        Import-DualDexDifferentialReviewFunctions
        $record = New-DualDexTestReviewRow -Confidence ([double]::NaN)

        $error = Invoke-AndCaptureError { ConvertTo-DualDexStableObservation -Record $record }

        $error | Should Not BeNullOrEmpty
        $error.Exception.Message | Should Match 'confidence.*finite'
    }

    It 'requires exact delta acknowledgement bindings and a nonblank reason' {
        Import-DualDexDifferentialReviewFunctions
        $delta = [pscustomobject]@{
            romSha256 = ('a' * 64)
            apkVersionCode = 1000011
            beforeHash = ('b' * 64)
            afterHash = ('c' * 64)
        }
        $matching = [pscustomobject]@{
            romSha256 = ('a' * 64)
            apkVersionCode = 1000011
            beforeHash = ('b' * 64)
            afterHash = ('c' * 64)
            reason = 'Reviewed exact parser drift'
        }
        $wrongHash = $matching.PSObject.Copy()
        $wrongHash.beforeHash = ('d' * 64)
        $staleVersion = $matching.PSObject.Copy()
        $staleVersion.apkVersionCode = 1000010
        $blankReason = $matching.PSObject.Copy()
        $blankReason.reason = '   '

        (Test-DualDexDeltaDecisionApplicable -Decision $matching -Delta $delta -ApkVersionCode 1000011) | Should Be $true
        (Test-DualDexDeltaDecisionApplicable -Decision $wrongHash -Delta $delta -ApkVersionCode 1000011) | Should Be $false
        (Test-DualDexDeltaDecisionApplicable -Decision $staleVersion -Delta $delta -ApkVersionCode 1000011) | Should Be $false
        (Test-DualDexDeltaDecisionApplicable -Decision $blankReason -Delta $delta -ApkVersionCode 1000011) | Should Be $false
    }

    It 'pauses on 100-to-100 offset count and sample drift despite a legacy compatibility decision' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-delta-pause-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot
            Write-DualDexTestJson (Join-Path $fixture.reviewRoot 'review-decisions.json') @([ordered]@{
                romSha256 = $fixture.entry.RomSha256
                apkVersionCode = 1000011
                decision = 'PARTIAL_ACCEPTED'
                reason = 'Legacy compatibility acceptance must not acknowledge drift'
            })

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            $pending = Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json
            $delta = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-deltas.json') -Raw | ConvertFrom-Json).deltas[0]
            $baseline = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-baseline.json') -Raw | ConvertFrom-Json).observations[0]
            $pending.reviewStatus | Should Be 'PARSER_DRIFT_REVIEW_REQUIRED'
            $pending.compatibilityPercent | Should Be 100
            (@($delta.changes.path) -contains '/capabilities/SPECIES_CATALOG/offset') | Should Be $true
            (@($delta.changes.path) -contains '/capabilities/SPECIES_CATALOG/count') | Should Be $true
            (@($delta.changes.path) -contains '/matchedTableFirstRegisters/speciesPhysical/0') | Should Be $true
            $baseline.observationHash | Should Be (New-DualDexTestBaselineEntry -ReviewRow $fixture.before).observationHash
            (Test-Path -LiteralPath (Join-Path $fixture.reviewRoot 'review-complete.json')) | Should Be $false
            (Test-Path -LiteralPath (Join-Path $fixture.reviewRoot 'review-deltas.md')) | Should Be $true
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'ignores a stale hash delta decision and keeps the baseline unchanged' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-stale-delta-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot
            $beforeEntry = New-DualDexTestBaselineEntry -ReviewRow $fixture.before
            $afterEntry = New-DualDexTestBaselineEntry -ReviewRow $fixture.after
            Write-DualDexTestJson (Join-Path $fixture.reviewRoot 'review-delta-decisions.json') @([ordered]@{
                romSha256 = $fixture.entry.RomSha256
                apkVersionCode = 1000011
                beforeHash = ('f' * 64)
                afterHash = $afterEntry.observationHash
                reason = 'This acknowledges a different before observation'
            })

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            (Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json).reviewStatus | Should Be 'PARSER_DRIFT_REVIEW_REQUIRED'
            @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-baseline.json') -Raw | ConvertFrom-Json).observations[0].observationHash | Should Be $beforeEntry.observationHash
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'advances the baseline only for a matching delta acknowledgement' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-accepted-delta-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot
            $beforeEntry = New-DualDexTestBaselineEntry -ReviewRow $fixture.before
            $afterEntry = New-DualDexTestBaselineEntry -ReviewRow $fixture.after
            Write-DualDexTestJson (Join-Path $fixture.reviewRoot 'review-delta-decisions.json') @([ordered]@{
                romSha256 = $fixture.entry.RomSha256
                apkVersionCode = 1000011
                beforeHash = $beforeEntry.observationHash
                afterHash = $afterEntry.observationHash
                reason = 'Reviewed the exact changed tables and samples'
            })

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            $baseline = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-baseline.json') -Raw | ConvertFrom-Json).observations[0]
            $baseline.observationHash | Should Be $afterEntry.observationHash
            (Test-Path -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json')) | Should Be $false
            (Test-Path -LiteralPath (Join-Path $fixture.reviewRoot 'review-complete.json')) | Should Be $true
            @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-deltas.json') -Raw | ConvertFrom-Json).deltas[0].acknowledged | Should Be $true
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'seeds the first observation without treating it as a delta' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-first-observation-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot -NoBaseline

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            $baseline = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-baseline.json') -Raw | ConvertFrom-Json).observations
            $baseline.Count | Should Be 1
            $baseline[0].observationHash | Should Be (New-DualDexTestBaselineEntry -ReviewRow $fixture.after).observationHash
            @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-deltas.json') -Raw | ConvertFrom-Json).deltas.Count | Should Be 0
            (Test-Path -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json')) | Should Be $false
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'preserves the reviewed baseline when current observation validation interrupts a pass' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-interrupted-delta-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot -AfterConfidence 'NaN'
            $baselinePath = Join-Path $fixture.reviewRoot 'review-baseline.json'
            $beforeBytes = [System.IO.File]::ReadAllBytes($baselinePath)

            $error = Invoke-AndCaptureError { & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild }

            $error | Should Not BeNullOrEmpty
            [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($baselinePath)) | Should Be ([Convert]::ToBase64String($beforeBytes))
            (Test-Path -LiteralPath (Join-Path $fixture.reviewRoot 'review-deltas.json')) | Should Be $false
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'compares a new APK pass against the prior ROM baseline without hashing the APK audit field' {
        Import-DualDexDifferentialReviewFunctions
        $sameBefore = New-DualDexTestReviewRow -ApkVersionCode 1000010
        $sameAfter = New-DualDexTestReviewRow -ApkVersionCode 1000011
        (Get-DualDexObservationHash -Observation (ConvertTo-DualDexStableObservation -Record $sameBefore)) |
            Should Be (Get-DualDexObservationHash -Observation (ConvertTo-DualDexStableObservation -Record $sameAfter))

        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-cross-apk-delta-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture `
                -Root $fixtureRoot `
                -BeforeApkVersionCode 1000010 `
                -AfterApkVersionCode 1000011

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            $pending = Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json
            $baseline = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-baseline.json') -Raw | ConvertFrom-Json).observations[0]
            $pending.reviewStatus | Should Be 'PARSER_DRIFT_REVIEW_REQUIRED'
            $pending.requiredDeltaDecisionBinding.apkVersionCode | Should Be 1000011
            $baseline.apkVersionCode | Should Be 1000010
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'keeps the whole baseline byte-identical when an earlier first seed is followed by later drift' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-pass-atomic-delta-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $firstEntry = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'first.gba' -Payload ([byte[]] @(1, 1, 1, 1)) -ArchiveRelativePath '001.zip'
            $secondEntry = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'second.gba' -Payload ([byte[]] @(2, 2, 2, 2)) -ArchiveRelativePath '002.zip'
            $firstCurrent = New-DualDexTestReviewRow -RomSha256 $firstEntry.RomSha256
            $secondBefore = New-DualDexTestReviewRow -RomSha256 $secondEntry.RomSha256 -Offset 4096 -Count 151 -Sample 'BULBASAUR'
            $secondCurrent = New-DualDexTestReviewRow -RomSha256 $secondEntry.RomSha256 -Offset 8192 -Count 152 -Sample 'MISSINGNO'
            Set-DualDexTestObservationEnvelope -Record $firstCurrent
            Set-DualDexTestObservationEnvelope -Record $secondCurrent
            Write-DualDexTestJson $manifestPath @($firstEntry, $secondEntry)
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-results.json') @($firstCurrent, $secondCurrent)
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-state.json') ([ordered]@{
                schemaVersion = 3
                apkVersionCode = 1000011
                completedRomSha256 = @($firstEntry.RomSha256, $secondEntry.RomSha256)
            })
            $baselinePath = Join-Path $reviewRoot 'review-baseline.json'
            Write-DualDexTestJson $baselinePath ([ordered]@{
                schemaVersion = 1
                observations = @(New-DualDexTestBaselineEntry -ReviewRow $secondBefore)
            })
            $beforeBytes = [System.IO.File]::ReadAllBytes($baselinePath)

            & $reviewScriptPath -RomManifest $manifestPath -WorkRoot $workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            (Get-Content -LiteralPath (Join-Path $reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json).reviewStatus |
                Should Be 'PARSER_DRIFT_REVIEW_REQUIRED'
            [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($baselinePath)) |
                Should Be ([Convert]::ToBase64String($beforeBytes))
            @(Get-Content -LiteralPath $baselinePath -Raw | ConvertFrom-Json).observations.Count | Should Be 1
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'commits a staged first observation when ordinary compatibility review pauses' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-ordinary-pause-seed-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot -NoBaseline
            Remove-Item -LiteralPath (Join-Path $fixture.reviewRoot 'review-results.json') -Force
            Remove-Item -LiteralPath (Join-Path $fixture.reviewRoot 'review-state.json') -Force

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            (Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json).reviewStatus |
                Should Be 'JUDGMENT_REQUIRED'
            $baseline = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-baseline.json') -Raw | ConvertFrom-Json).observations[0]
            $persisted = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)[0]
            $baseline.observationHash | Should Be (New-DualDexTestBaselineEntry -ReviewRow $persisted).observationHash
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'does not attribute a compatibility decision before a parser drift pause' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-delta-before-compat-decision-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot
            $fixture.after.compatibilityPercent = 99
            $fixture.after.manualReviewRequired = $true
            $fixture.after.dataCompatibility = 'PARTIAL'
            Set-DualDexTestObservationEnvelope -Record $fixture.after
            Write-DualDexTestJson (Join-Path $fixture.reviewRoot 'review-results.json') @($fixture.after)
            Write-DualDexTestJson (Join-Path $fixture.reviewRoot 'review-state.json') ([ordered]@{
                schemaVersion = 3
                apkVersionCode = 1000011
                completedRomSha256 = @()
            })
            Write-DualDexTestJson (Join-Path $fixture.reviewRoot 'review-decisions.json') @([ordered]@{
                romSha256 = $fixture.entry.RomSha256
                apkVersionCode = 1000011
                decision = 'PARTIAL_ACCEPTED'
                reason = 'Compatibility reviewed, parser drift not reviewed'
            })

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            (Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json).reviewStatus |
                Should Be 'PARSER_DRIFT_REVIEW_REQUIRED'
            $persisted = @(Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)[0]
            $persisted.decision | Should BeNullOrEmpty
            $persisted.decisionReason | Should BeNullOrEmpty
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'flattens a newly added capability object into deterministic leaf changes' {
        Import-DualDexDifferentialReviewFunctions
        $before = New-DualDexTestReviewRow
        $before.capabilities = @($before.capabilities | Where-Object capability -ne 'MOVE_CATALOG')
        $after = New-DualDexTestReviewRow

        $changes = @(Compare-DualDexObservation `
            -Before (ConvertTo-DualDexStableObservation -Record $before) `
            -After (ConvertTo-DualDexStableObservation -Record $after))

        (@($changes.path) -contains '/capabilities/MOVE_CATALOG') | Should Be $false
        (@($changes.path) -contains '/capabilities/MOVE_CATALOG/status') | Should Be $true
        (@($changes.path) -contains '/capabilities/MOVE_CATALOG/offset') | Should Be $true
        (@($changes.path) -contains '/capabilities/MOVE_CATALOG/reasons') | Should Be $true
        (@($changes.path) -join "`n") | Should Be (@(Get-DualDexOrdinalSortedStrings -Value @($changes.path)) -join "`n")
    }

    It 'reconciles a leftover completion marker against the still-uncommitted baseline on rerun' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-terminal-reconcile-" + [guid]::NewGuid().ToString('N'))
        try {
            $fixture = New-DualDexDifferentialFixture -Root $fixtureRoot
            $baselinePath = Join-Path $fixture.reviewRoot 'review-baseline.json'
            $beforeBytes = [System.IO.File]::ReadAllBytes($baselinePath)
            Write-DualDexTestJson (Join-Path $fixture.reviewRoot 'review-complete.json') ([ordered]@{
                schemaVersion = 3
                reviewStatus = 'COMPLETE'
                apkVersionCode = 1000011
            })

            & $reviewScriptPath -RomManifest $fixture.manifestPath -WorkRoot $fixture.workRoot -ApkVersionCode 1000011 -SkipBuild | Out-Null

            (Test-Path -LiteralPath (Join-Path $fixture.reviewRoot 'review-complete.json')) | Should Be $false
            (Get-Content -LiteralPath (Join-Path $fixture.reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json).reviewStatus |
                Should Be 'PARSER_DRIFT_REVIEW_REQUIRED'
            [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($baselinePath)) |
                Should Be ([Convert]::ToBase64String($beforeBytes))
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'does not reuse a fresh result that paused for drift and reparses changed output on rerun' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-fresh-drift-rerun-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        $fakeParser = Join-Path $fixtureRoot 'fake-parser.ps1'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $entry = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'sample.gba' -Payload ([byte[]] @(1, 2, 3, 4))
            $before = New-DualDexTestReviewRow -RomSha256 $entry.RomSha256 -Offset 4096
            Write-DualDexTestJson $manifestPath @($entry)
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-baseline.json') ([ordered]@{
                schemaVersion = 1
                observations = @(New-DualDexTestBaselineEntry -ReviewRow $before)
            })
            $fakeParserSource = @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]] $FakeArgs)
$romPath = $FakeArgs[0]
$jsonIndex = [Array]::IndexOf($FakeArgs, '--json')
$markdownIndex = [Array]::IndexOf($FakeArgs, '--markdown')
$jsonPath = $FakeArgs[$jsonIndex + 1]
$markdownPath = $FakeArgs[$markdownIndex + 1]
$countPath = Join-Path $PSScriptRoot 'fake-parser-count.txt'
$callCount = if (Test-Path -LiteralPath $countPath) { [int] (Get-Content -LiteralPath $countPath -Raw) + 1 } else { 1 }
[System.IO.File]::WriteAllText($countPath, [string] $callCount, [System.Text.UTF8Encoding]::new($false))
$offset = if ($callCount -eq 1) { 8192 } else { 12288 }
$sha = (Get-FileHash -LiteralPath $romPath -Algorithm SHA256).Hash.ToLowerInvariant()
$report = [ordered]@{
    results = @([ordered]@{
        result = [ordered]@{
            sha256 = $sha
            status = 'SELECTED'
            selectedFamily = 'EMERALD'
            selectedProfile = 'Fake profile'
            runnerUpMargin = 20
            capabilities = @([ordered]@{
                capability = 'SPECIES_CATALOG'
                compatible = $true
                status = 'AVAILABLE'
                offset = $offset
                count = 151
                recordSize = 28
                validRecords = 151
                totalRecords = 151
                coveredRecords = 151
                expectedRecords = 151
                reviewStatus = 'VALIDATED'
                validatorReviewRecommended = $false
                confidence = 1.0
                format = 'GEN3_BASE_STATS'
                reasons = @()
            })
        }
        compatibilityPercent = 100
        resolvedFeatureCount = 1
        expectedFeatureCount = 1
        manualReviewRequired = $false
        dataCompatibility = 'COMPLETE'
        catalog = [ordered]@{ species = 151 }
    })
}
[System.IO.File]::WriteAllText($jsonPath, ($report | ConvertTo-Json -Depth 12), [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($markdownPath, "fake parser pass $callCount", [System.Text.UTF8Encoding]::new($false))
$global:LASTEXITCODE = 0
'@
            [System.IO.File]::WriteAllText($fakeParser, $fakeParserSource, [System.Text.UTF8Encoding]::new($false))

            & $reviewScriptPath `
                -RomManifest $manifestPath `
                -WorkRoot $workRoot `
                -ApkVersionCode 1000011 `
                -ParserCliPath $fakeParser `
                -SkipBuild | Out-Null
            $firstPending = Get-Content -LiteralPath (Join-Path $reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json
            @((Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)).Count | Should Be 0

            & $reviewScriptPath `
                -RomManifest $manifestPath `
                -WorkRoot $workRoot `
                -ApkVersionCode 1000011 `
                -ParserCliPath $fakeParser `
                -SkipBuild | Out-Null
            $secondPending = Get-Content -LiteralPath (Join-Path $reviewRoot 'pending-review.json') -Raw | ConvertFrom-Json
            $secondDelta = @(Get-Content -LiteralPath (Join-Path $reviewRoot 'review-deltas.json') -Raw | ConvertFrom-Json).deltas[0]

            (Get-Content -LiteralPath (Join-Path $fixtureRoot 'fake-parser-count.txt') -Raw) | Should Be '2'
            $firstPending.afterHash | Should Not Be $secondPending.afterHash
            ($secondDelta.changes | Where-Object path -eq '/capabilities/SPECIES_CATALOG/offset').after | Should Be 12288
            @((Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)).Count | Should Be 0
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }

    It 'trusts only a current hash-validated cached observation envelope' {
        Import-DualDexDifferentialReviewFunctions
        Import-CorpusReviewFunction 'Get-DualDexValidatedCachedObservation'
        $legacy = New-DualDexTestReviewRow

        (Get-DualDexValidatedCachedObservation -Record $legacy -ExpectedSha256 $legacy.romSha256) |
            Should BeNullOrEmpty

        $current = $legacy.PSObject.Copy()
        $observation = ConvertTo-DualDexStableObservation -Record $current
        $hash = Get-DualDexObservationHash -Observation $observation
        $current | Add-Member -NotePropertyName observationSchemaVersion -NotePropertyValue 1
        $current | Add-Member -NotePropertyName stableObservation -NotePropertyValue $observation
        $current | Add-Member -NotePropertyName observationHash -NotePropertyValue $hash

        $validated = Get-DualDexValidatedCachedObservation `
            -Record $current `
            -ExpectedSha256 $current.romSha256
        (ConvertTo-DualDexCanonicalJson -Value $validated) |
            Should Be (ConvertTo-DualDexCanonicalJson -Value $observation)

        $tamperedObservation = $current.PSObject.Copy()
        $tamperedStable = ConvertTo-DualDexCanonicalJson -Value $observation | ConvertFrom-Json
        $tamperedStable.capabilities.SPECIES_CATALOG.offset = 999999
        $tamperedObservation.stableObservation = $tamperedStable
        (Invoke-AndCaptureError {
            Get-DualDexValidatedCachedObservation `
                -Record $tamperedObservation `
                -ExpectedSha256 $current.romSha256
        }) | Should Not BeNullOrEmpty

        $tamperedHash = $current.PSObject.Copy()
        $tamperedHash.observationHash = 'f' * 64
        (Invoke-AndCaptureError {
            Get-DualDexValidatedCachedObservation `
                -Record $tamperedHash `
                -ExpectedSha256 $current.romSha256
        }) | Should Not BeNullOrEmpty
    }

    It 'skips legacy baseline observations but rejects tampered current-schema entries' {
        Import-DualDexDifferentialReviewFunctions
        Import-CorpusReviewFunction 'ConvertTo-DualDexValidatedBaselineEntry'
        $current = [pscustomobject] (New-DualDexTestBaselineEntry -ReviewRow (New-DualDexTestReviewRow))

        $validated = ConvertTo-DualDexValidatedBaselineEntry -Entry $current -Index 1
        $validated.observationSchemaVersion | Should Be 1
        $validated.observationHash | Should Be $current.observationHash

        $legacy = $current.PSObject.Copy()
        $legacy.PSObject.Properties.Remove('observationSchemaVersion')
        (ConvertTo-DualDexValidatedBaselineEntry -Entry $legacy -Index 1) | Should BeNullOrEmpty

        $wrongSchema = $current.PSObject.Copy()
        $wrongSchema.observationSchemaVersion = 2
        (Invoke-AndCaptureError {
            ConvertTo-DualDexValidatedBaselineEntry -Entry $wrongSchema -Index 1
        }) | Should Not BeNullOrEmpty

        $malformedSchema = $current.PSObject.Copy()
        $malformedSchema.observationSchemaVersion = 'garbage'
        (Invoke-AndCaptureError {
            ConvertTo-DualDexValidatedBaselineEntry -Entry $malformedSchema -Index 1
        }) | Should Not BeNullOrEmpty

        $tampered = $current.PSObject.Copy()
        $tamperedObservation = ConvertTo-DualDexCanonicalJson -Value $current.observation | ConvertFrom-Json
        $tamperedObservation.capabilities.SPECIES_CATALOG.offset = 999999
        $tampered.observation = $tamperedObservation
        (Invoke-AndCaptureError {
            ConvertTo-DualDexValidatedBaselineEntry -Entry $tampered -Index 1
        }) | Should Not BeNullOrEmpty
    }

    It 'migrates a legacy result through a fresh first observation without fake drift and reuses the exact envelope' {
        Import-DualDexDifferentialReviewFunctions
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-corpus-legacy-observation-migration-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $reviewRoot = Join-Path $workRoot 'review'
        $manifestPath = Join-Path $fixtureRoot 'manifest.json'
        $fakeParser = Join-Path $fixtureRoot 'fake-parser.ps1'
        [System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
        try {
            $entry = New-DualDexTestRomEntry -Root $fixtureRoot -FileName 'sample.gba' -Payload ([byte[]] @(1, 2, 3, 4))
            Write-DualDexTestJson $manifestPath @($entry)
            # Shape from the pre-observation-envelope rollout: selection and
            # table data exist, but newer stable fields were never persisted.
            $legacyResult = [pscustomobject][ordered]@{
                schemaVersion = 3
                index = 1
                archive = 'sample.zip'
                entry = 'sample.gba'
                extension = '.gba'
                romSha256 = $entry.RomSha256
                bytes = 4
                apkVersionCode = 1000011
                compatibilityPercent = 100
                resolvedFeatureCount = 1
                expectedFeatureCount = 1
                manualReviewRequired = $false
                dataCompatibility = 'COMPLETE'
                selectionStatus = 'SELECTED'
                selectedFamily = 'EMERALD'
                capabilities = @([ordered]@{
                    capability = 'SPECIES_CATALOG'
                    status = 'AVAILABLE'
                    offset = 4096
                    count = 151
                    recordSize = 28
                    validRecords = 151
                    totalRecords = 151
                    reasons = @()
                })
                catalog = [ordered]@{ species = 151 }
                referenceErrors = @()
            }
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-results.json') @($legacyResult)
            $legacyObservation = ConvertTo-DualDexStableObservation -Record $legacyResult
            Write-DualDexTestJson (Join-Path $reviewRoot 'review-baseline.json') ([ordered]@{
                schemaVersion = 1
                observations = @([ordered]@{
                    romSha256 = $entry.RomSha256
                    apkVersionCode = 1000011
                    observationHash = Get-DualDexObservationHash -Observation $legacyObservation
                    observation = $legacyObservation
                })
            })
            $fakeParserSource = @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]] $FakeArgs)
$romPath = $FakeArgs[0]
$jsonIndex = [Array]::IndexOf($FakeArgs, '--json')
$markdownIndex = [Array]::IndexOf($FakeArgs, '--markdown')
$jsonPath = $FakeArgs[$jsonIndex + 1]
$markdownPath = $FakeArgs[$markdownIndex + 1]
$countPath = Join-Path $PSScriptRoot 'fake-parser-count.txt'
$callCount = if (Test-Path -LiteralPath $countPath) { [int] (Get-Content -LiteralPath $countPath -Raw) + 1 } else { 1 }
[System.IO.File]::WriteAllText($countPath, [string] $callCount, [System.Text.UTF8Encoding]::new($false))
$sha = (Get-FileHash -LiteralPath $romPath -Algorithm SHA256).Hash.ToLowerInvariant()
$report = [ordered]@{
    results = @([ordered]@{
        result = [ordered]@{
            sha256 = $sha
            status = 'SELECTED'
            selectedFamily = 'EMERALD'
            selectedProfile = 'Fake current profile'
            runnerUpMargin = 20
            capabilities = @([ordered]@{
                capability = 'SPECIES_CATALOG'
                compatible = $true
                status = 'AVAILABLE'
                offset = 4096
                count = 151
                recordSize = 28
                elementSize = 2
                validRecords = 151
                totalRecords = 151
                coveredRecords = 151
                expectedRecords = 151
                reviewStatus = 'VALIDATED'
                validatorReviewRecommended = $false
                confidence = 1.0
                format = 'GEN3_BASE_STATS'
                reasons = @()
            })
        }
        compatibilityPercent = 100
        resolvedFeatureCount = 1
        expectedFeatureCount = 1
        manualReviewRequired = $false
        dataCompatibility = 'COMPLETE'
        catalog = [ordered]@{ species = 151 }
        samples = [ordered]@{
            species = @('BULBASAUR')
            speciesByDex = @('BULBASAUR')
            moves = @('POUND')
            types = @()
            typeChart = @()
            evolutions = @()
            learnsets = @()
            eggMoves = @()
            machineMoves = @()
            tutorMoves = @()
            abilities = @()
            encounters = @()
            balls = @()
            referenceErrors = @()
        }
    })
}
[System.IO.File]::WriteAllText($jsonPath, ($report | ConvertTo-Json -Depth 20), [System.Text.UTF8Encoding]::new($false))
[System.IO.File]::WriteAllText($markdownPath, "fake parser pass $callCount", [System.Text.UTF8Encoding]::new($false))
$global:LASTEXITCODE = 0
'@
            [System.IO.File]::WriteAllText($fakeParser, $fakeParserSource, [System.Text.UTF8Encoding]::new($false))

            & $reviewScriptPath `
                -RomManifest $manifestPath `
                -WorkRoot $workRoot `
                -ApkVersionCode 1000011 `
                -ParserCliPath $fakeParser `
                -Rebaseline `
                -SkipBuild | Out-Null

            (Test-Path -LiteralPath (Join-Path $reviewRoot 'pending-review.json')) | Should Be $false
            $firstDeltas = Get-Content -LiteralPath (Join-Path $reviewRoot 'review-deltas.json') -Raw | ConvertFrom-Json
            @($firstDeltas.deltas).Count | Should Be 0
            $firstResult = @(Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)[0]
            $firstBaseline = @(Get-Content -LiteralPath (Join-Path $reviewRoot 'review-baseline.json') -Raw | ConvertFrom-Json).observations[0]
            $firstResult.observationSchemaVersion | Should Be 1
            $firstBaseline.observationSchemaVersion | Should Be 1
            $firstResult.observationHash | Should Be $firstBaseline.observationHash
            (Get-DualDexObservationHash -Observation $firstResult.stableObservation) | Should Be $firstResult.observationHash
            (Get-Content -LiteralPath (Join-Path $fixtureRoot 'fake-parser-count.txt') -Raw) | Should Be '1'

            & $reviewScriptPath `
                -RomManifest $manifestPath `
                -WorkRoot $workRoot `
                -ApkVersionCode 1000011 `
                -ParserCliPath $fakeParser `
                -SkipBuild | Out-Null
            (Get-Content -LiteralPath (Join-Path $fixtureRoot 'fake-parser-count.txt') -Raw) | Should Be '1'
            @((Get-Content -LiteralPath (Join-Path $reviewRoot 'review-deltas.json') -Raw | ConvertFrom-Json).deltas).Count | Should Be 0

            & $reviewScriptPath `
                -RomManifest $manifestPath `
                -WorkRoot $workRoot `
                -ApkVersionCode 1000011 `
                -ParserCliPath $fakeParser `
                -Rebaseline `
                -SkipBuild | Out-Null
            (Get-Content -LiteralPath (Join-Path $fixtureRoot 'fake-parser-count.txt') -Raw) | Should Be '2'
            (Test-Path -LiteralPath (Join-Path $reviewRoot 'pending-review.json')) | Should Be $false
            @((Get-Content -LiteralPath (Join-Path $reviewRoot 'review-deltas.json') -Raw | ConvertFrom-Json).deltas).Count | Should Be 0
            $secondResult = @(Get-Content -LiteralPath (Join-Path $reviewRoot 'review-results.json') -Raw | ConvertFrom-Json)[0]
            $secondResult.observationHash | Should Be $firstResult.observationHash
            (ConvertTo-DualDexCanonicalJson -Value $secondResult.stableObservation) |
                Should Be (ConvertTo-DualDexCanonicalJson -Value $firstResult.stableObservation)
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) { Remove-Item -LiteralPath $fixtureRoot -Recurse -Force }
        }
    }
}

Describe 'DualDex corpus evidence lineage' -Tags 'EvidenceLineage' {
    It 'binds parser CLI builds and reports to source and execution receipts' {
        foreach ($scriptAst in @($reviewScriptAst, $validationScriptAst)) {
            $source = $scriptAst.Extent.Text
            $source | Should Match 'git\s+-C\s+\$projectRoot\s+rev-parse\s+HEAD'
            $source | Should Match '-PdualdexSourceCommit=\$sourceCommit'
            $source | Should Match "'--execution-receipt'"
            $source | Should Match '''--source-commit''\s+\$sourceCommit'
        }
        $validationScriptAst.Extent.Text | Should Match 'status\s+--porcelain\s+--untracked-files=no'
    }
}
