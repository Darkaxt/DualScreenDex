[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $RomManifest,

    [Parameter(Mandatory = $true)]
    [string] $WorkRoot,

    [string] $DecisionsPath,

    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$utf8 = [System.Text.UTF8Encoding]::new($false)
[Console]::InputEncoding = $utf8
[Console]::OutputEncoding = $utf8
$OutputEncoding = $utf8

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
$cacheRoot = Join-Path $workPath 'catalog-cache'
$statePath = Join-Path $reviewRoot 'review-state.json'
$pendingPath = Join-Path $reviewRoot 'pending-review.json'
$pendingReportJson = Join-Path $reviewRoot 'pending-parser-report.json'
$pendingReportMarkdown = Join-Path $reviewRoot 'pending-parser-report.md'
$completePath = Join-Path $reviewRoot 'review-complete.json'
$resultsPath = Join-Path $reviewRoot 'review-results.json'
if (-not $DecisionsPath) {
    $DecisionsPath = Join-Path $reviewRoot 'review-decisions.json'
}
$decisionsFullPath = [System.IO.Path]::GetFullPath($DecisionsPath)
[System.IO.Directory]::CreateDirectory($reviewRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($cacheRoot) | Out-Null

function Write-JsonFile([string] $path, [object] $value) {
    $json = $value | ConvertTo-Json -Depth 32
    [System.IO.File]::WriteAllText($path, $json + [Environment]::NewLine, $utf8)
}

if (-not $SkipBuild) {
    & (Join-Path $projectRoot 'gradlew.bat') '--project-dir' $projectRoot ':parser-cli:installDist' '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle parser CLI build failed with exit code $LASTEXITCODE"
    }
}

$parserCli = Join-Path $projectRoot 'parser-cli\build\install\parser-cli\bin\parser-cli.bat'
if (-not (Test-Path -LiteralPath $parserCli -PathType Leaf)) {
    throw "Parser CLI distribution does not exist: $parserCli"
}

$manifest = @(Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json)
$eligible = @($manifest | Where-Object {
    $_.EntryPath -notmatch '(?i)(Mystery Dungeon|Pinball|Puzzle Challenge|Trading Card Game)' -and
    $_.Extension -in '.gb', '.gbc', '.gba'
})
$unique = @($eligible | Group-Object RomSha256 | ForEach-Object { $_.Group | Select-Object -First 1 } | Sort-Object ArchiveRelativePath, EntryPath)

$completed = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
if (Test-Path -LiteralPath $statePath -PathType Leaf) {
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    @($state.completedRomSha256) | ForEach-Object { if ($_) { [void] $completed.Add([string] $_) } }
}

$decisions = @()
if (Test-Path -LiteralPath $decisionsFullPath -PathType Leaf) {
    $decisions = @(Get-Content -LiteralPath $decisionsFullPath -Raw | ConvertFrom-Json)
}
$accepted = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($decision in $decisions) {
    if ($decision.decision -notin 'DESIGN_INCOMPATIBLE', 'SOURCE_DATA_DAMAGED', 'EXCLUDED_BY_SCOPE') {
        throw "Unsupported review decision '$($decision.decision)' for $($decision.romSha256)"
    }
    if (-not $decision.reason) {
        throw "Review decision for $($decision.romSha256) requires a reason"
    }
    [void] $accepted.Add([string] $decision.romSha256)
}

$reviewResults = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::OrdinalIgnoreCase)
if (Test-Path -LiteralPath $resultsPath -PathType Leaf) {
    @(Get-Content -LiteralPath $resultsPath -Raw | ConvertFrom-Json) | ForEach-Object {
        if ($_.romSha256) { $reviewResults[[string] $_.romSha256] = $_ }
    }
}

function Save-ReviewResults {
    Write-JsonFile $resultsPath @($reviewResults.Values | Sort-Object index, entry)
}

for ($index = 0; $index -lt $unique.Count; $index++) {
    $item = $unique[$index]
    $sha = [string] $item.RomSha256
    $existingResult = if ($reviewResults.ContainsKey($sha)) { $reviewResults[$sha] } else { $null }
    $existingComplete = $null -ne $existingResult -and $existingResult.dataCompatibility -eq 'COMPLETE'
    if (($completed.Contains($sha) -and $existingComplete) -or ($accepted.Contains($sha) -and $null -ne $existingResult)) {
        continue
    }
    $romPath = [System.IO.Path]::GetFullPath([string] $item.ExtractedPath)
    if (-not (Test-Path -LiteralPath $romPath -PathType Leaf)) {
        throw "Extracted ROM is missing: $romPath"
    }
    $actual = Get-Item -LiteralPath $romPath
    if ($actual.Length -ne [long] $item.Bytes) {
        throw "Extracted ROM size changed: $romPath"
    }

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
    $missing = @($result.result.capabilities | Where-Object status -eq 'NOT_FOUND' | ForEach-Object capability)
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
    $decision = @($decisions | Where-Object { $_.romSha256 -eq $sha }) | Select-Object -First 1
    $decisionValue = if ($null -eq $decision) { $null } else { $decision.decision }
    $decisionReason = if ($null -eq $decision) { $null } else { $decision.reason }
    $reviewResults[$sha] = [ordered]@{
        schemaVersion = 1
        index = $index + 1
        archive = $item.ArchiveRelativePath
        entry = $item.EntryPath
        romSha256 = $sha
        bytes = [long] $item.Bytes
        dataCompatibility = $result.dataCompatibility
        selectionStatus = $result.result.status
        selectedFamily = $selectedFamily
        missingStructures = $missing
        matchedTableFirstRegisters = $firstRegisters
        capabilities = @($result.result.capabilities | ForEach-Object { [ordered]@{
            capability = $_.capability
            status = $_.status
            count = $_.PSObject.Properties['count']?.Value
            offset = $_.PSObject.Properties['offset']?.Value
            recordSize = $_.PSObject.Properties['recordSize']?.Value
            reasons = @($_.reasons)
        } })
        catalog = $result.PSObject.Properties['catalog']?.Value
        referenceErrors = $referenceErrors
        catalogError = $catalogError
        persistenceError = $persistenceError
        decision = $decisionValue
        decisionReason = $decisionReason
        verifiedAt = (Get-Date).ToString('o')
    }
    Save-ReviewResults

    if ($result.dataCompatibility -eq 'COMPLETE') {
        [void] $completed.Add($sha)
        Write-JsonFile $statePath ([ordered]@{
            schemaVersion = 1
            completedRomSha256 = @($completed | Sort-Object)
            acceptedDesignExceptions = @($accepted | Sort-Object)
            updatedAt = (Get-Date).ToString('o')
        })
        continue
    }
    if ($accepted.Contains($sha)) {
        continue
    }

    if ($completed.Remove($sha)) {
        Write-JsonFile $statePath ([ordered]@{
            schemaVersion = 1
            completedRomSha256 = @($completed | Sort-Object)
            acceptedDesignExceptions = @($accepted | Sort-Object)
            updatedAt = (Get-Date).ToString('o')
        })
    }

    Write-JsonFile $pendingPath ([ordered]@{
        schemaVersion = 1
        reviewStatus = 'JUDGMENT_REQUIRED'
        index = $index + 1
        totalUniqueRoms = $unique.Count
        archive = $item.ArchiveRelativePath
        entry = $item.EntryPath
        romSha256 = $sha
        dataCompatibility = $result.dataCompatibility
        missingStructures = $missing
        matchedTableFirstRegisters = $firstRegisters
        referenceErrors = $referenceErrors
        catalogError = $catalogError
        persistenceError = $persistenceError
        parserReportJson = $pendingReportJson
        parserReportMarkdown = $pendingReportMarkdown
        decisionsFile = $decisionsFullPath
        nextActions = @(
            'Fix a generally derivable parser layout and rerun this script',
            'Or add DESIGN_INCOMPATIBLE / SOURCE_DATA_DAMAGED / EXCLUDED_BY_SCOPE with a reason to the decisions file'
        )
    })
    Write-Output "Review paused: $pendingPath"
    Write-Output "Missing structures: $($missing -join ', ')"
    if ($null -ne $firstRegisters) {
        Write-Output "First physical species registers: $(@($firstRegisters.speciesPhysical) -join ' | ')"
        Write-Output "First Dex-ordered species registers: $(@($firstRegisters.speciesDexOrdered) -join ' | ')"
        Write-Output "First move registers: $(@($firstRegisters.moves) -join ' | ')"
        Write-Output "First tutor registers: $(@($firstRegisters.tutorMoves) -join ' | ')"
    }
    return
}

if (Test-Path -LiteralPath $pendingPath -PathType Leaf) {
    Remove-Item -LiteralPath $pendingPath -Force
}
Write-JsonFile $completePath ([ordered]@{
    schemaVersion = 1
    reviewStatus = 'COMPLETE'
    uniqueRomsReviewed = $unique.Count
    fullyCompatible = $completed.Count
    acceptedDesignExceptions = $accepted.Count
    completedAt = (Get-Date).ToString('o')
})
Write-Output "Review complete: $completePath"
