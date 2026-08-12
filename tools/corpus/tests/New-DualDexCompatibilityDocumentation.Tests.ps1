$policyPath = Join-Path $PSScriptRoot '..\CorpusReviewPolicy.ps1'
$generatorPath = Join-Path $PSScriptRoot '..\New-DualDexCompatibilityDocumentation.ps1'
. $policyPath

function Write-DualDexCompatibilityTestJson([string] $Path, [object] $Value) {
    $json = ConvertTo-Json -InputObject $Value -Depth 64
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-DualDexCompatibilityAndCaptureError([scriptblock] $Action) {
    try {
        & $Action | Out-Null
        return $null
    } catch {
        return $_
    }
}

function New-DualDexCompatibilityTestCapability {
    param(
        [string] $Name,
        [string] $Status = 'AVAILABLE',
        [int] $Count = 1,
        [string[]] $Reasons = @()
    )
    [pscustomobject][ordered]@{
        capability = $Name
        compatible = $Status -ne 'NOT_FOUND'
        confidence = $(if ($Status -eq 'AVAILABLE') { 1.0 } else { 0.5 })
        offset = 4096
        count = $Count
        recordSize = 4
        elementSize = $null
        reasons = @($Reasons)
        status = $Status
        validRecords = $Count
        totalRecords = $Count
        coveredRecords = $(if ($Status -eq 'PARTIAL') { $Count - 1 } else { $Count })
        expectedRecords = $Count
        incompleteRecords = $(if ($Status -eq 'PARTIAL') { 1 } else { 0 })
        reviewStatus = $(if ($Status -eq 'PARTIAL') { 'MANUAL_REVIEW' } else { 'NONE' })
        validatorReviewRecommended = $false
        format = 'STANDARD'
    }
}

function New-DualDexCompatibilityTestReportResult {
    param(
        [string] $Sha,
        [string] $Name,
        [string] $Family,
        [int] $Generation,
        [string] $Platform,
        [bool] $WithPartial = $false
    )
    $capabilities = @(
        New-DualDexCompatibilityTestCapability -Name 'SPECIES_CATALOG' -Count 151
        New-DualDexCompatibilityTestCapability -Name 'MOVE_CATALOG' -Count 354
        New-DualDexCompatibilityTestCapability -Name 'ABILITIES' -Count 77
        New-DualDexCompatibilityTestCapability -Name 'TYPE_PRESENTATION' -Count 18
        New-DualDexCompatibilityTestCapability -Name 'ABILITY_MECHANICS' -Count 4
        New-DualDexCompatibilityTestCapability -Name 'EGG_MOVES' -Count 12
        New-DualDexCompatibilityTestCapability -Name 'MACHINE_MOVES' -Count 13
        New-DualDexCompatibilityTestCapability -Name 'TUTOR_MOVES' -Count 14
        New-DualDexCompatibilityTestCapability -Name 'AREA_ENCOUNTERS' -Count 15
    )
    if ($WithPartial) {
        $capabilities += New-DualDexCompatibilityTestCapability `
            -Name 'EVOLUTIONS' -Status 'PARTIAL' -Count 3 -Reasons @('source table ends after two valid rows')
        $capabilities += New-DualDexCompatibilityTestCapability `
            -Name 'SPRITES' -Status 'PARTIAL' -Count 2
    }
    $tables = [pscustomobject][ordered]@{
        speciesNames = [pscustomobject][ordered]@{ offset = 4096; count = 152; recordSize = 11; stride = $null; elementSize = $null; variableLength = $false; valuesArePointers = $false; format = 'STANDARD'; bank = $null; banks = @(); pointerOffsets = @(); bankAdjustment = 0; bankRemap = [pscustomobject]@{} }
        baseStats = [pscustomobject][ordered]@{ offset = 8192; count = 152; recordSize = $(if ($Generation -eq 3) { 32 } else { 28 }); stride = $null; elementSize = $null; variableLength = $false; valuesArePointers = $false; format = 'STANDARD'; bank = $null; banks = @(); pointerOffsets = @(); bankAdjustment = 0; bankRemap = [pscustomobject]@{} }
        moveNames = [pscustomobject][ordered]@{ offset = 12288; count = 355; recordSize = 13; stride = $null; elementSize = $null; variableLength = $false; valuesArePointers = $false; format = 'STANDARD'; bank = $null; banks = @(); pointerOffsets = @(); bankAdjustment = 0; bankRemap = [pscustomobject]@{} }
        moveData = [pscustomobject][ordered]@{ offset = 16384; count = 355; recordSize = 16; stride = $null; elementSize = $null; variableLength = $false; valuesArePointers = $false; format = 'CFRU_MOVE_16'; bank = $null; banks = @(); pointerOffsets = @(); bankAdjustment = 0; bankRemap = [pscustomobject]@{} }
        typeChart = $null
        evolutions = $null
        learnsets = $null
        sprites = $null
        descriptions = $null
        abilities = $null
    }
    $layout = [pscustomobject][ordered]@{
        family = $Family
        generation = $Generation
        platform = $Platform
        speciesCount = 152
        moveCount = 355
        tables = $tables
        pokeemeraldExpansion = $null
        learnsetTables = @([pscustomobject]@{ id = 'primary'; offset = 20480; format = 'STANDARD' })
        learnsetSelector = [pscustomobject]@{ saveBlock1ByteOffset = 32; mask = 4; expectedValue = 4 }
    }
    $catalog = [pscustomobject][ordered]@{
        species = 151
        namedSpecies = 151
        speciesWithStats = 151
        speciesWithSprites = 151
        speciesWithDescriptions = 151
        evolutionEdges = 2
        learnsetEntries = 30
        learnsetRulesets = 1
        moves = 354
        movesWithDetails = 354
        movesWithDescriptions = 354
        eggMoveLinks = 12
        machineMoveLinks = 13
        tutorMoveLinks = 14
        types = 18
        typeMatchups = 108
        abilities = 77
        abilitiesWithDescriptions = 77
        abilitiesWithMechanics = 4
        captureBalls = 12
        encounterAreas = 15
        rulesetDetails = @([pscustomobject][ordered]@{
            id = 'ruleset-00005000'
            label = 'Expanded 1'
            sourceOffset = 20480
            confidence = 0.875
            primary = $false
            levelUpSelector = [pscustomobject][ordered]@{
                saveBlock1ByteOffset = 32
                mask = 4
                expectedValue = 4
            }
        })
    }
    [pscustomobject][ordered]@{
        displayName = $Name
        source = "fixtures/$Name"
        durationMillis = 12
        result = [pscustomobject][ordered]@{
            header = [pscustomobject]@{ platform = $Platform; title = $Name; gameCode = 'TEST'; revision = 1 }
            sha256 = $Sha
            crc32 = '1234abcd'
            size = 4
            status = 'SELECTED'
            selectedFamily = $Family
            selectedProfile = 'Structural profile'
            runnerUpMargin = 20.0
            probes = @(
                [pscustomobject]@{ family = 'RED_BLUE'; exactProfile = $false; resolvedLayout = $null },
                [pscustomobject]@{ family = $Family; exactProfile = $false; resolvedLayout = $layout }
            )
            capabilities = $capabilities
            diagnostics = @()
        }
        catalog = $catalog
        samples = $null
        persistence = [pscustomobject]@{ fileName = 'catalog.sqlite'; bytes = 2048; writeMillis = 3; reopenMillis = 2; sections = 8 }
        persistenceError = $null
        catalogError = $null
        dataCompatibility = $(if ($WithPartial) { 'PARTIAL' } else { 'COMPLETE' })
        compatibilityPercent = $(if ($WithPartial) { 90.0 } else { 100.0 })
        resolvedFeatureCount = $(if ($WithPartial) { 9 } else { 10 })
        expectedFeatureCount = 10
        manualReviewRequired = $WithPartial
    }
}

function New-DualDexCompatibilityTestReviewRow {
    param([object] $ReportResult, [object] $ManifestEntry, [int] $Index, [int] $ApkVersionCode, [string] $Decision)
    $capabilities = @($ReportResult.result.capabilities | ForEach-Object { [pscustomobject][ordered]@{
        capability = $_.capability; compatible = $_.compatible; status = $_.status; count = $_.count
        offset = $_.offset; recordSize = $_.recordSize; elementSize = $_.elementSize
        validRecords = $_.validRecords; totalRecords = $_.totalRecords
        coveredRecords = $_.coveredRecords; expectedRecords = $_.expectedRecords
        reviewStatus = $_.reviewStatus; validatorReviewRecommended = $_.validatorReviewRecommended
        confidence = $_.confidence; format = $_.format; reasons = @($_.reasons)
    } })
    $row = [pscustomobject][ordered]@{
        schemaVersion = 3
        index = $Index
        archive = $ManifestEntry.ArchiveRelativePath
        archiveSha256 = $ManifestEntry.ArchiveSha256
        entry = $ManifestEntry.EntryPath
        platformFolder = $ManifestEntry.PlatformFolder
        extension = $ManifestEntry.Extension
        romSha256 = $ReportResult.result.sha256
        bytes = $ReportResult.result.size
        apkVersionCode = $ApkVersionCode
        compatibilityPercent = $ReportResult.compatibilityPercent
        resolvedFeatureCount = $ReportResult.resolvedFeatureCount
        expectedFeatureCount = $ReportResult.expectedFeatureCount
        manualReviewRequired = $ReportResult.manualReviewRequired
        dataCompatibility = $ReportResult.dataCompatibility
        selectionStatus = $ReportResult.result.status
        selectedFamily = $ReportResult.result.selectedFamily
        selectedProfile = $ReportResult.result.selectedProfile
        runnerUpMargin = $ReportResult.result.runnerUpMargin
        missingStructures = @($ReportResult.result.capabilities | Where-Object status -eq 'NOT_FOUND' | ForEach-Object capability)
        matchedTableFirstRegisters = $null
        capabilities = $capabilities
        catalog = $ReportResult.catalog
        referenceErrors = @()
        catalogError = $null
        persistenceError = $null
        decision = $Decision
        decisionReason = $(if ($Decision) { 'Reviewed source-authored truncation' } else { $null })
    }
    $observation = ConvertTo-DualDexStableObservation -Record $row
    $row | Add-Member observationSchemaVersion 1
    $row | Add-Member observationHash (Get-DualDexObservationHash -Observation $observation)
    $row | Add-Member stableObservation $observation
    $row
}

function New-DualDexCompatibilityTestFixture {
    $root = Join-Path 'D:\Temp' ('dualdex-compat-doc-test-' + [guid]::NewGuid().ToString('N'))
    [System.IO.Directory]::CreateDirectory($root) | Out-Null
    $shaA = 'a' * 64
    $shaB = 'b' * 64
    $manifest = @(
        [pscustomobject]@{ ArchiveRelativePath = '003.zip'; ArchiveSha256 = ('c' * 64); EntryPath = 'Gen3 First.gba'; PlatformFolder = 'Game Boy Advance'; Extension = '.gba'; RomSha256 = $shaA; Bytes = 4 },
        [pscustomobject]@{ ArchiveRelativePath = '002.zip'; ArchiveSha256 = ('d' * 64); EntryPath = 'Gen2 Second.gbc'; PlatformFolder = 'Game Boy Color'; Extension = '.gbc'; RomSha256 = $shaB; Bytes = 4 },
        [pscustomobject]@{ ArchiveRelativePath = '000-duplicate.zip'; ArchiveSha256 = ('e' * 64); EntryPath = 'Duplicate.gba'; PlatformFolder = 'Game Boy Advance'; Extension = '.gba'; RomSha256 = $shaA.ToUpperInvariant(); Bytes = 4 }
    )
    $reportResults = @(
        New-DualDexCompatibilityTestReportResult -Sha $shaA -Name 'Gen3 First.gba' -Family 'FIRERED_LEAFGREEN' -Generation 3 -Platform 'GBA' -WithPartial $true
        New-DualDexCompatibilityTestReportResult -Sha $shaB -Name 'Gen2 Second.gbc' -Family 'CRYSTAL' -Generation 2 -Platform 'GBC'
    )
    $apkVersionCode = 1000011
    $review = @(
        New-DualDexCompatibilityTestReviewRow -ReportResult $reportResults[0] -ManifestEntry $manifest[0] -Index 1 -ApkVersionCode $apkVersionCode -Decision 'SOURCE_DATA_DAMAGED'
        New-DualDexCompatibilityTestReviewRow -ReportResult $reportResults[1] -ManifestEntry $manifest[1] -Index 2 -ApkVersionCode $apkVersionCode -Decision $null
    )
    $paths = [pscustomobject]@{
        root = $root
        manifest = Join-Path $root 'manifest.json'
        report = Join-Path $root 'report.json'
        review = Join-Path $root 'review-results.json'
        complete = Join-Path $root 'review-complete.json'
        json = Join-Path $root 'compatibility.json'
        markdown = Join-Path $root 'compatibility.md'
        apkVersionCode = $apkVersionCode
    }
    Write-DualDexCompatibilityTestJson $paths.manifest $manifest
    Write-DualDexCompatibilityTestJson $paths.report ([pscustomobject]@{ schemaVersion = 11; results = $reportResults })
    Write-DualDexCompatibilityTestJson $paths.review $review
    Write-DualDexCompatibilityTestJson $paths.complete ([pscustomobject]@{
        schemaVersion = 3; reviewStatus = 'COMPLETE'; apkVersionCode = $apkVersionCode
        uniqueRomsReviewed = 2; eligibleUniqueRoms = 2; discoveredUniqueRoms = 2; maximumIndex = 2
        verificationScope = [pscustomobject][ordered]@{
            strategy = 'final rerun plus reviewed non-impact evidence'
            finalRerunIndices = @(1)
            baseRunOnlyIndices = @(2)
        }
    })
    $paths
}

Describe 'DualDex compatibility/property documentation generator' {
    It 'joins reviewed schema eleven evidence and groups Generation then family then manifest identity' {
        $fixture = New-DualDexCompatibilityTestFixture
        try {
            & $generatorPath -RomManifest $fixture.manifest -RawReport $fixture.report `
                -ReviewResults $fixture.review -ReviewComplete $fixture.complete `
                -ApkVersionCode $fixture.apkVersionCode -ExpectedCount 2 `
                -JsonOutput $fixture.json -MarkdownOutput $fixture.markdown

            $document = Get-Content -LiteralPath $fixture.json -Raw | ConvertFrom-Json
            $document.schemaVersion | Should Be 1
            $document.reportSchemaVersion | Should Be 11
            $document.romCount | Should Be 2
            $document.verificationScope.strategy | Should Be 'final rerun plus reviewed non-impact evidence'
            @($document.verificationScope.finalRerunIndices) -join ',' | Should Be '1'
            @($document.verificationScope.baseRunOnlyIndices) -join ',' | Should Be '2'
            @($document.groups.generation) -join ',' | Should Be '2,3'
            $gen3 = @($document.groups | Where-Object generation -eq 3)[0].families[0].roms[0]
            $gen3.identity.manifestIndex | Should Be 1
            $gen3.identity.displayName | Should Be 'Gen3 First.gba'
            $gen3.active.species | Should Be 151
            ($gen3.engineLineage.markers -contains 'CFRU_OR_DPE_WIDENED_MOVE_ABI') | Should Be $true
            ($gen3.engineLineage.markers -contains 'BATTLE_ENGINE_BASE_STATS_ABI') | Should Be $true
            $gen3.rulesets.details[0].id | Should Be 'ruleset-00005000'
            $gen3.rulesets.details[0].PSObject.Properties['entriesBySpecies'] | Should BeNullOrEmpty
            $gen3.mechanics.abilityMechanics.materializedAbilityCount | Should Be 4
            $gen3.encounters.areas | Should Be 15
            $gen3.gaps[0].classification | Should Be 'SOURCE_DATA_DAMAGED'
            $gen3.gaps[0].sourceAuthored | Should Be $true
            $gen3.review.status | Should Be 'SOURCE_DATA_DAMAGED'
            $gen3.provenance.observationHash | Should Be (Get-Content -LiteralPath $fixture.review -Raw | ConvertFrom-Json | Select-Object -First 1).observationHash
            @($gen3.PSObject.Properties.Name) -join ',' | Should Be 'identity,routing,generation,platform,compatibility,active,engineLineage,tableAbis,expansionAbi,rulesets,mechanics,encounters,gaps,review,integrity,provenance'
            (Get-Content -LiteralPath $fixture.markdown -Raw) | Should Match '## Generation 3'
            (Get-Content -LiteralPath $fixture.markdown -Raw) | Should Match '### FIRERED_LEAFGREEN'
            (Get-Content -LiteralPath $fixture.markdown -Raw) | Should Match 'final rerun plus reviewed non-impact evidence'
            (Get-Content -LiteralPath $fixture.json -Raw) | Should Not Match "`r"
            (Get-Content -LiteralPath $fixture.markdown -Raw) | Should Not Match "`r"
            (Get-Content -LiteralPath $fixture.markdown -Raw) | Should Not Match '(?m)[ \t]+$'
            (Get-Content -LiteralPath $fixture.markdown -Raw) | Should Not Match "`n`n$"
            @(Get-ChildItem -LiteralPath $fixture.root -File | Where-Object Name -Like '*.tmp').Count | Should Be 0
        } finally {
            if ($fixture.root -like 'D:\Temp\dualdex-compat-doc-test-*' -and (Test-Path -LiteralPath $fixture.root)) {
                Remove-Item -LiteralPath $fixture.root -Recurse -Force
            }
        }
    }

    It 'rejects an observation mismatch before replacing either published document' {
        $fixture = New-DualDexCompatibilityTestFixture
        try {
            [System.IO.File]::WriteAllText($fixture.json, 'json-sentinel')
            [System.IO.File]::WriteAllText($fixture.markdown, 'markdown-sentinel')
            $review = @(Get-Content -LiteralPath $fixture.review -Raw | ConvertFrom-Json)
            $review[0].observationHash = '0' * 64
            Write-DualDexCompatibilityTestJson $fixture.review $review

            $error = Invoke-DualDexCompatibilityAndCaptureError { & $generatorPath -RomManifest $fixture.manifest -RawReport $fixture.report `
                    -ReviewResults $fixture.review -ReviewComplete $fixture.complete `
                    -ApkVersionCode $fixture.apkVersionCode -ExpectedCount 2 `
                    -JsonOutput $fixture.json -MarkdownOutput $fixture.markdown }

            $error | Should Not BeNullOrEmpty
            $error.Exception.Message | Should Match 'observation mismatch'
            (Get-Content -LiteralPath $fixture.json -Raw) | Should Be 'json-sentinel'
            (Get-Content -LiteralPath $fixture.markdown -Raw) | Should Be 'markdown-sentinel'
        } finally {
            if ($fixture.root -like 'D:\Temp\dualdex-compat-doc-test-*' -and (Test-Path -LiteralPath $fixture.root)) {
                Remove-Item -LiteralPath $fixture.root -Recurse -Force
            }
        }
    }

    It 'requires observation schema one before publishing reviewed evidence' {
        $fixture = New-DualDexCompatibilityTestFixture
        try {
            $review = @(Get-Content -LiteralPath $fixture.review -Raw | ConvertFrom-Json)
            $review[0].observationSchemaVersion = 2
            Write-DualDexCompatibilityTestJson $fixture.review $review

            $error = Invoke-DualDexCompatibilityAndCaptureError { & $generatorPath -RomManifest $fixture.manifest -RawReport $fixture.report `
                    -ReviewResults $fixture.review -ReviewComplete $fixture.complete `
                    -ApkVersionCode $fixture.apkVersionCode -ExpectedCount 2 `
                    -JsonOutput $fixture.json -MarkdownOutput $fixture.markdown }

            $error | Should Not BeNullOrEmpty
            $error.Exception.Message | Should Match 'observationSchemaVersion 1'
        } finally {
            if ($fixture.root -like 'D:\Temp\dualdex-compat-doc-test-*' -and (Test-Path -LiteralPath $fixture.root)) {
                Remove-Item -LiteralPath $fixture.root -Recurse -Force
            }
        }
    }

    It 'rejects tampered persisted stable observation even when the row and hash remain intact' {
        $fixture = New-DualDexCompatibilityTestFixture
        try {
            $review = @(Get-Content -LiteralPath $fixture.review -Raw | ConvertFrom-Json)
            $review[0].stableObservation.catalog.species = 999
            Write-DualDexCompatibilityTestJson $fixture.review $review

            $error = Invoke-DualDexCompatibilityAndCaptureError { & $generatorPath -RomManifest $fixture.manifest -RawReport $fixture.report `
                    -ReviewResults $fixture.review -ReviewComplete $fixture.complete `
                    -ApkVersionCode $fixture.apkVersionCode -ExpectedCount 2 `
                    -JsonOutput $fixture.json -MarkdownOutput $fixture.markdown }

            $error | Should Not BeNullOrEmpty
            $error.Exception.Message | Should Match 'persisted stable observation mismatch'
        } finally {
            if ($fixture.root -like 'D:\Temp\dualdex-compat-doc-test-*' -and (Test-Path -LiteralPath $fixture.root)) {
                Remove-Item -LiteralPath $fixture.root -Recurse -Force
            }
        }
    }

    It 'quarantines retained selected-family catalog and ABI data for every non-selected outcome' {
        $fixture = New-DualDexCompatibilityTestFixture
        try {
            $manifest = @(Get-Content -LiteralPath $fixture.manifest -Raw | ConvertFrom-Json)
            $report = Get-Content -LiteralPath $fixture.report -Raw | ConvertFrom-Json
            $report.results[0].result.status = 'AMBIGUOUS'
            Write-DualDexCompatibilityTestJson $fixture.report $report

            $review = @(Get-Content -LiteralPath $fixture.review -Raw | ConvertFrom-Json)
            $review[0] = New-DualDexCompatibilityTestReviewRow `
                -ReportResult $report.results[0] -ManifestEntry $manifest[0] -Index 1 `
                -ApkVersionCode $fixture.apkVersionCode -Decision 'SOURCE_DATA_DAMAGED'
            Write-DualDexCompatibilityTestJson $fixture.review $review

            & $generatorPath -RomManifest $fixture.manifest -RawReport $fixture.report `
                -ReviewResults $fixture.review -ReviewComplete $fixture.complete `
                -ApkVersionCode $fixture.apkVersionCode -ExpectedCount 2 `
                -JsonOutput $fixture.json -MarkdownOutput $fixture.markdown

            $document = Get-Content -LiteralPath $fixture.json -Raw | ConvertFrom-Json
            $unresolvedGroup = @($document.groups | Where-Object generation -eq 'Unresolved')[0]
            $unresolvedGroup.families.Count | Should Be 1
            $unresolvedGroup.families[0].family | Should Be 'Unresolved'
            $rom = $unresolvedGroup.families[0].roms[0]
            $rom.routing.status | Should Be 'AMBIGUOUS'
            $rom.routing.family | Should Be 'Unresolved'
            $rom.routing.profile | Should BeNullOrEmpty
            $rom.generation | Should BeNullOrEmpty
            $rom.platform | Should BeNullOrEmpty
            $rom.active.species | Should BeNullOrEmpty
            $rom.active.moves | Should BeNullOrEmpty
            $rom.active.abilities | Should BeNullOrEmpty
            $rom.active.types | Should BeNullOrEmpty
            $rom.active.countEvidence | Should BeNullOrEmpty
            $rom.engineLineage.baseFamily | Should Be 'Unresolved'
            $rom.engineLineage.structuralAncestor | Should BeNullOrEmpty
            $rom.tableAbis | Should BeNullOrEmpty
            $rom.expansionAbi | Should BeNullOrEmpty
            $rom.rulesets | Should BeNullOrEmpty
        } finally {
            if ($fixture.root -like 'D:\Temp\dualdex-compat-doc-test-*' -and (Test-Path -LiteralPath $fixture.root)) {
                Remove-Item -LiteralPath $fixture.root -Recurse -Force
            }
        }
    }

    It 'rejects duplicate raw identities and an incomplete review marker' {
        $fixture = New-DualDexCompatibilityTestFixture
        try {
            $report = Get-Content -LiteralPath $fixture.report -Raw | ConvertFrom-Json
            $report.results = @($report.results[0], $report.results[0])
            Write-DualDexCompatibilityTestJson $fixture.report $report
            $complete = Get-Content -LiteralPath $fixture.complete -Raw | ConvertFrom-Json
            $complete.reviewStatus = 'REVIEW_INCOMPLETE'
            Write-DualDexCompatibilityTestJson $fixture.complete $complete

            $error = Invoke-DualDexCompatibilityAndCaptureError { & $generatorPath -RomManifest $fixture.manifest -RawReport $fixture.report `
                    -ReviewResults $fixture.review -ReviewComplete $fixture.complete `
                    -ApkVersionCode $fixture.apkVersionCode -ExpectedCount 2 `
                    -JsonOutput $fixture.json -MarkdownOutput $fixture.markdown }

            $error | Should Not BeNullOrEmpty
            $error.Exception.Message | Should Match 'reviewStatus COMPLETE|duplicate ROM identity'
        } finally {
            if ($fixture.root -like 'D:\Temp\dualdex-compat-doc-test-*' -and (Test-Path -LiteralPath $fixture.root)) {
                Remove-Item -LiteralPath $fixture.root -Recurse -Force
            }
        }
    }

    It 'projects only relative path size and digest from the parser artifact manifest' {
        $fixture = New-DualDexCompatibilityTestFixture
        $artifacts = Join-Path $fixture.root 'parser-artifacts.json'
        try {
            Write-DualDexCompatibilityTestJson $artifacts @(
                [pscustomobject]@{
                    relativePath = 'lib/parser-cli.jar'
                    bytes = 1234
                    sha256 = 'f' * 64
                    absolutePath = 'H:\Private\parser-cli.jar'
                }
            )

            & $generatorPath -RomManifest $fixture.manifest -RawReport $fixture.report `
                -ReviewResults $fixture.review -ReviewComplete $fixture.complete `
                -ApkVersionCode $fixture.apkVersionCode -ExpectedCount 2 `
                -ParserArtifacts $artifacts -JsonOutput $fixture.json -MarkdownOutput $fixture.markdown

            $json = Get-Content -LiteralPath $fixture.json -Raw
            $document = $json | ConvertFrom-Json
            $projected = $document.groups[0].families[0].roms[0].provenance.parserArtifacts[0]
            @($projected.PSObject.Properties.Name) -join ',' | Should Be 'relativePath,bytes,sha256'
            $projected.relativePath | Should Be 'lib/parser-cli.jar'
            $json | Should Not Match 'H:\\Private'
        } finally {
            if ($fixture.root -like 'D:\Temp\dualdex-compat-doc-test-*' -and (Test-Path -LiteralPath $fixture.root)) {
                Remove-Item -LiteralPath $fixture.root -Recurse -Force
            }
        }
    }
}

Describe 'DualDex release documentation and registry handoff contract' {
    $repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\..'))
    $releaseWorkflow = Join-Path $repoRoot '.github\workflows\release.yml'
    $compatibilityJson = Join-Path $repoRoot 'reports\dualdex-parser-compatibility.json'
    $propertyJson = Join-Path $repoRoot 'reports\dualdex-rom-properties.json'
    $postReleaseChecklist = Join-Path $repoRoot 'release\POST_RELEASE_CHECKLIST.md'

    It 'ships both raw compatibility and grouped property documents in both formats' {
        $workflow = Get-Content -LiteralPath $releaseWorkflow -Raw
        foreach ($asset in @(
            'dualdex-parser-compatibility.json',
            'dualdex-parser-compatibility.md',
            'dualdex-rom-properties.json',
            'dualdex-rom-properties.md'
        )) {
            ([regex]::Matches($workflow, [regex]::Escape($asset))).Count | Should BeGreaterThan 2
        }

        $compatibility = Get-Content -LiteralPath $compatibilityJson -Raw | ConvertFrom-Json
        $properties = Get-Content -LiteralPath $propertyJson -Raw | ConvertFrom-Json
        $compatibility.schemaVersion | Should Be 11
        @($compatibility.results).Count | Should Be 50
        $properties.schemaVersion | Should Be 1
        $properties.romCount | Should Be 50
    }

    It 'keeps the physical Thor verification before the existing GAFT registry update' {
        Test-Path -LiteralPath $postReleaseChecklist -PathType Leaf | Should Be $true
        $checklist = Get-Content -LiteralPath $postReleaseChecklist -Raw
        $thor = $checklist.IndexOf('validate-signed-candidate.ps1', [System.StringComparison]::Ordinal)
        $gaft = $checklist.IndexOf('https://github.com/andreyvelsk/GAFT', [System.StringComparison]::Ordinal)
        $thor | Should BeGreaterThan -1
        $gaft | Should BeGreaterThan $thor
        $checklist | Should Match 'content/<slug>/index\.md'
        $checklist | Should Match 'open the upstream pull request'
    }
}
