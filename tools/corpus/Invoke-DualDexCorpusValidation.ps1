[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $SourceRoot,

    [Parameter(Mandatory = $true)]
    [string] $WorkRoot,

    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int] $ApkVersionCode,

    [string] $SevenZip,

    [switch] $Reset,

    [switch] $ReviewIncomplete,

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

$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$sourcePath = [System.IO.Path]::GetFullPath($SourceRoot)
$workPath = [System.IO.Path]::GetFullPath($WorkRoot)

function Test-DualDexPathEqualOrAncestor {
    param(
        [Parameter(Mandatory = $true)]
        [string] $CandidateAncestor,

        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    $separator = [System.IO.Path]::DirectorySeparatorChar
    $alternateSeparator = [System.IO.Path]::AltDirectorySeparatorChar
    $ancestorPath = [System.IO.Path]::GetFullPath($CandidateAncestor).TrimEnd($separator, $alternateSeparator)
    $targetPath = [System.IO.Path]::GetFullPath($Path).TrimEnd($separator, $alternateSeparator)
    return [string]::Equals($ancestorPath, $targetPath, [System.StringComparison]::OrdinalIgnoreCase) -or
        $targetPath.StartsWith($ancestorPath + $separator, [System.StringComparison]::OrdinalIgnoreCase)
}

function Assert-DualDexValidationOptions {
    param(
        [Parameter(Mandatory = $true)]
        [bool] $ReviewIncomplete,

        [Parameter(Mandatory = $true)]
        [int] $MaximumIndex,

        [bool] $Rebaseline = $false
    )

    if (-not $ReviewIncomplete -and $MaximumIndex -ne 2147483647) {
        throw '-MaximumIndex is supported only with -ReviewIncomplete; an unbounded report would violate the requested corpus window.'
    }
    if (-not $ReviewIncomplete -and $Rebaseline) {
        throw '-Rebaseline is supported only with -ReviewIncomplete.'
    }
}

Assert-DualDexValidationOptions `
    -ReviewIncomplete ([bool] $ReviewIncomplete) `
    -MaximumIndex $MaximumIndex `
    -Rebaseline ([bool] $Rebaseline)

if (-not (Test-Path -LiteralPath $sourcePath -PathType Container)) {
    throw "Corpus root does not exist: $sourcePath"
}

if ($workPath -eq [System.IO.Path]::GetPathRoot($workPath)) {
    throw "WorkRoot must not be a drive root: $workPath"
}

if ($Reset -and (Test-DualDexPathEqualOrAncestor -CandidateAncestor $workPath -Path $sourcePath)) {
    throw "Refusing to reset WorkRoot because it is equal to or contains SourceRoot: $workPath :: $sourcePath"
}

if (-not $SevenZip) {
    $resolvedSevenZip = Get-Command 7z.exe -ErrorAction SilentlyContinue
    if (-not $resolvedSevenZip) {
        throw '7z.exe was not found. Pass -SevenZip with the NanaZip or 7-Zip CLI path.'
    }
    $SevenZip = $resolvedSevenZip.Source
}
$sevenZipPath = [System.IO.Path]::GetFullPath($SevenZip)

$romRoot = Join-Path $workPath 'roms'
$cacheRoot = Join-Path $workPath 'catalog-cache'
$reportRoot = Join-Path $workPath 'report'

if ($Reset -and (Test-Path -LiteralPath $workPath)) {
    $resolvedWork = (Resolve-Path -LiteralPath $workPath).Path
    if ($resolvedWork -eq [System.IO.Path]::GetPathRoot($resolvedWork)) {
        throw "Refusing to reset drive root: $resolvedWork"
    }
    Remove-Item -LiteralPath $resolvedWork -Recurse -Force
}

[System.IO.Directory]::CreateDirectory($workPath) | Out-Null
[System.IO.Directory]::CreateDirectory($romRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($cacheRoot) | Out-Null
[System.IO.Directory]::CreateDirectory($reportRoot) | Out-Null

function Write-Stage([string] $message) {
    $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss'
    Write-Output "[$stamp] $message"
}

function Invoke-SevenZip([string[]] $Arguments) {
    $output = & $sevenZipPath @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "7-Zip failed with exit code $LASTEXITCODE`n$($output -join [Environment]::NewLine)"
    }
    return @($output)
}

function Read-ArchiveEntries([string] $archivePath) {
    $listing = Invoke-SevenZip @('l', '-slt', '-sccUTF-8', '--', $archivePath)
    $separator = [Array]::IndexOf($listing, '----------')
    if ($separator -lt 0) {
        throw "7-Zip listing has no entry separator: $archivePath"
    }

    $entries = [System.Collections.Generic.List[object]]::new()
    $fields = @{}
    foreach ($line in @($listing | Select-Object -Skip ($separator + 1)) + '') {
        if ([string]::IsNullOrWhiteSpace($line)) {
            if ($fields.Count -gt 0) {
                $entryPath = [string] $fields['Path']
                if ($entryPath) {
                    $entries.Add([pscustomobject]@{
                        Path = $entryPath
                        Size = if ($fields.ContainsKey('Size')) { [long] $fields['Size'] } else { 0L }
                        Attributes = if ($fields.ContainsKey('Attributes')) { [string] $fields['Attributes'] } else { '' }
                    })
                }
                $fields = @{}
            }
            continue
        }

        $split = $line.IndexOf(' = ')
        if ($split -gt 0) {
            $fields[$line.Substring(0, $split)] = $line.Substring($split + 3)
        }
    }
    return $entries
}

function Assert-SafeEntryPath([string] $entryPath, [string] $archivePath) {
    if ([System.IO.Path]::IsPathRooted($entryPath)) {
        throw "Archive contains a rooted entry path: $archivePath :: $entryPath"
    }
    $segments = $entryPath -split '[\\/]'
    if ($segments -contains '..' -or $segments -contains '') {
        throw "Archive contains an unsafe entry path: $archivePath :: $entryPath"
    }
}

function Write-DualDexAtomicJson([string] $Path, [object] $Value) {
    $directory = [System.IO.Path]::GetDirectoryName($Path)
    $temporaryPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    $backupPath = Join-Path $directory ([System.IO.Path]::GetRandomFileName())
    try {
        $json = ConvertTo-Json -InputObject $Value -Depth 8
        [System.IO.File]::WriteAllText($temporaryPath, $json + [Environment]::NewLine, $utf8)
        if (Test-Path -LiteralPath $Path -PathType Leaf) {
            [System.IO.File]::Replace($temporaryPath, $Path, $backupPath)
            [System.IO.File]::Delete($backupPath)
        } else {
            [System.IO.File]::Move($temporaryPath, $Path)
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

function Test-DualDexArchiveExtractionCache {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ArchiveOutput,

        [Parameter(Mandatory = $true)]
        [string] $ArchiveSha256,

        [Parameter(Mandatory = $true)]
        [object[]] $RomEntries
    )

    $provenancePath = Join-Path $ArchiveOutput '.dualdex-extraction.json'
    if (-not (Test-Path -LiteralPath $provenancePath -PathType Leaf)) {
        return $false
    }
    try {
        $provenance = Get-Content -LiteralPath $provenancePath -Raw | ConvertFrom-Json
        if ($provenance.schemaVersion -ne 1 -or
            -not [string]::Equals([string] $provenance.archiveSha256, $ArchiveSha256, [System.StringComparison]::OrdinalIgnoreCase) -or
            @($provenance.entries).Count -ne $RomEntries.Count) {
            return $false
        }
        $provenanceByPath = [System.Collections.Generic.Dictionary[string, object]]::new([System.StringComparer]::Ordinal)
        foreach ($record in @($provenance.entries)) {
            $recordPath = [string] $record.path
            if ([string]::IsNullOrWhiteSpace($recordPath) -or $provenanceByPath.ContainsKey($recordPath)) {
                return $false
            }
            $provenanceByPath[$recordPath] = $record
        }
        $outputPrefix = [System.IO.Path]::GetFullPath($ArchiveOutput + [System.IO.Path]::DirectorySeparatorChar)
        foreach ($entry in $RomEntries) {
            if (-not $provenanceByPath.ContainsKey([string] $entry.Path)) {
                return $false
            }
            $record = $provenanceByPath[[string] $entry.Path]
            $recordSha = [string] $record.sha256
            if ([long] $record.size -ne [long] $entry.Size -or $recordSha -notmatch '^[0-9a-fA-F]{64}$') {
                return $false
            }
            $relativePayload = $entry.Path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
            $payloadPath = [System.IO.Path]::GetFullPath((Join-Path $ArchiveOutput $relativePayload))
            if (-not $payloadPath.StartsWith($outputPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
                -not (Test-Path -LiteralPath $payloadPath -PathType Leaf)) {
                return $false
            }
            $payload = Get-Item -LiteralPath $payloadPath
            if ($payload.Length -ne [long] $entry.Size) {
                return $false
            }
            $actualSha = (Get-FileHash -LiteralPath $payloadPath -Algorithm SHA256).Hash
            if (-not [string]::Equals($actualSha, $recordSha, [System.StringComparison]::OrdinalIgnoreCase)) {
                return $false
            }
        }
        return $true
    } catch {
        return $false
    }
}

function Install-DualDexArchivePayloads {
    param(
        [Parameter(Mandatory = $true)]
        [string] $ArchivePath,

        [Parameter(Mandatory = $true)]
        [string] $ArchiveSha256,

        [Parameter(Mandatory = $true)]
        [string] $ArchiveOutput,

        [Parameter(Mandatory = $true)]
        [object[]] $RomEntries
    )

    $stagingParent = Join-Path $workPath 'extraction-staging'
    [System.IO.Directory]::CreateDirectory($stagingParent) | Out-Null
    $stagingRoot = Join-Path $stagingParent ([System.IO.Path]::GetRandomFileName())
    [System.IO.Directory]::CreateDirectory($stagingRoot) | Out-Null
    try {
        $extractArguments = @('x', '-y', '-sccUTF-8', ('-o' + $stagingRoot), '--', $ArchivePath) + @($RomEntries.Path)
        Invoke-SevenZip $extractArguments | Out-Null
        $stagingPrefix = [System.IO.Path]::GetFullPath($stagingRoot + [System.IO.Path]::DirectorySeparatorChar)
        $outputPrefix = [System.IO.Path]::GetFullPath($ArchiveOutput + [System.IO.Path]::DirectorySeparatorChar)
        $provenanceEntries = [System.Collections.Generic.List[object]]::new()
        foreach ($entry in $RomEntries) {
            $relativePayload = $entry.Path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
            $stagedPath = [System.IO.Path]::GetFullPath((Join-Path $stagingRoot $relativePayload))
            $payloadPath = [System.IO.Path]::GetFullPath((Join-Path $ArchiveOutput $relativePayload))
            if (-not $stagedPath.StartsWith($stagingPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
                -not $payloadPath.StartsWith($outputPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                throw "Extracted payload escaped its archive directory: $($entry.Path)"
            }
            if (-not (Test-Path -LiteralPath $stagedPath -PathType Leaf)) {
                throw "Archive did not extract expected ROM payload: $ArchivePath :: $($entry.Path)"
            }
            $stagedPayload = Get-Item -LiteralPath $stagedPath
            if ($stagedPayload.Length -ne [long] $entry.Size) {
                throw "Archive ROM size mismatch after staged extraction: $ArchivePath :: $($entry.Path)"
            }
            $payloadSha = (Get-FileHash -LiteralPath $stagedPath -Algorithm SHA256).Hash.ToLowerInvariant()
            $payloadDirectory = [System.IO.Path]::GetDirectoryName($payloadPath)
            [System.IO.Directory]::CreateDirectory($payloadDirectory) | Out-Null
            $temporaryPayload = Join-Path $payloadDirectory ([System.IO.Path]::GetRandomFileName())
            $backupPayload = Join-Path $payloadDirectory ([System.IO.Path]::GetRandomFileName())
            try {
                [System.IO.File]::Copy($stagedPath, $temporaryPayload, $true)
                if (Test-Path -LiteralPath $payloadPath -PathType Leaf) {
                    [System.IO.File]::Replace($temporaryPayload, $payloadPath, $backupPayload)
                    [System.IO.File]::Delete($backupPayload)
                } else {
                    [System.IO.File]::Move($temporaryPayload, $payloadPath)
                }
            } finally {
                if (Test-Path -LiteralPath $temporaryPayload -PathType Leaf) {
                    [System.IO.File]::Delete($temporaryPayload)
                }
                if (Test-Path -LiteralPath $backupPayload -PathType Leaf) {
                    [System.IO.File]::Delete($backupPayload)
                }
            }
            $provenanceEntries.Add([ordered]@{
                path = [string] $entry.Path
                size = [long] $entry.Size
                sha256 = $payloadSha
            })
        }
        Write-DualDexAtomicJson `
            -Path (Join-Path $ArchiveOutput '.dualdex-extraction.json') `
            -Value ([ordered]@{
                schemaVersion = 1
                archiveSha256 = $ArchiveSha256
                entries = @($provenanceEntries)
            })
    } finally {
        if (Test-Path -LiteralPath $stagingRoot -PathType Container) {
            [System.IO.Directory]::Delete($stagingRoot, $true)
        }
    }
}

$archives = @(Get-ChildItem -LiteralPath $sourcePath -Recurse -File -Filter '*.7z' | Sort-Object FullName)
if ($archives.Count -eq 0) {
    throw "No .7z archives found under $sourcePath"
}

Write-Stage "Integrity testing $($archives.Count) archives"
$archiveRows = [System.Collections.Generic.List[object]]::new()
$payloadRows = [System.Collections.Generic.List[object]]::new()

for ($archiveIndex = 0; $archiveIndex -lt $archives.Count; $archiveIndex++) {
    $archive = $archives[$archiveIndex]
    $relativeArchive = [System.IO.Path]::GetRelativePath($sourcePath, $archive.FullName)
    Write-Stage "Archive $($archiveIndex + 1)/$($archives.Count): $relativeArchive"
    Invoke-SevenZip @('t', '-sccUTF-8', '--', $archive.FullName) | Out-Null

    $archiveSha = (Get-FileHash -LiteralPath $archive.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $entries = @(Read-ArchiveEntries $archive.FullName)
    $romEntries = @($entries | Where-Object { [System.IO.Path]::GetExtension($_.Path).ToLowerInvariant() -in '.gb', '.gbc', '.gba' })
    foreach ($entry in $entries) {
        Assert-SafeEntryPath $entry.Path $archive.FullName
    }

    $archiveRows.Add([pscustomobject]@{
        RelativePath = $relativeArchive
        Bytes = $archive.Length
        Sha256 = $archiveSha
        Entries = $entries.Count
        RomEntries = $romEntries.Count
    })

    if ($romEntries.Count -eq 0) {
        continue
    }

    $archiveOutput = Join-Path $romRoot ('{0:D4}-{1}' -f ($archiveIndex + 1), $archiveSha.Substring(0, 12))
    [System.IO.Directory]::CreateDirectory($archiveOutput) | Out-Null
    $outputPrefix = [System.IO.Path]::GetFullPath($archiveOutput + [System.IO.Path]::DirectorySeparatorChar)
    if (-not (Test-DualDexArchiveExtractionCache `
        -ArchiveOutput $archiveOutput `
        -ArchiveSha256 $archiveSha `
        -RomEntries $romEntries)) {
        Install-DualDexArchivePayloads `
            -ArchivePath $archive.FullName `
            -ArchiveSha256 $archiveSha `
            -ArchiveOutput $archiveOutput `
            -RomEntries $romEntries
    }

    foreach ($entry in $romEntries) {
        $relativePayload = $entry.Path.Replace('/', [System.IO.Path]::DirectorySeparatorChar)
        $payloadPath = [System.IO.Path]::GetFullPath((Join-Path $archiveOutput $relativePayload))
        if (-not $payloadPath.StartsWith($outputPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "Extracted payload escaped its archive directory: $payloadPath"
        }
        if (-not (Test-Path -LiteralPath $payloadPath -PathType Leaf)) {
            throw "Expected ROM payload was not extracted: $relativeArchive :: $($entry.Path)"
        }

        $payload = Get-Item -LiteralPath $payloadPath
        if ($payload.Length -ne $entry.Size) {
            throw "Extracted ROM size mismatch: $relativeArchive :: $($entry.Path)"
        }
        $payloadRows.Add([pscustomobject]@{
            ArchiveRelativePath = $relativeArchive
            ArchiveSha256 = $archiveSha
            EntryPath = $entry.Path
            PlatformFolder = $relativeArchive.Split([System.IO.Path]::DirectorySeparatorChar)[0]
            Extension = $payload.Extension.ToLowerInvariant()
            Bytes = $payload.Length
            RomSha256 = (Get-FileHash -LiteralPath $payloadPath -Algorithm SHA256).Hash.ToLowerInvariant()
            ExtractedPath = $payloadPath
        })
    }
}

$archiveCsv = Join-Path $workPath 'archive-manifest.csv'
$archiveJson = Join-Path $workPath 'archive-manifest.json'
$romCsv = Join-Path $workPath 'rom-manifest.csv'
$romJson = Join-Path $workPath 'rom-manifest.json'
$archiveRows | Export-Csv -LiteralPath $archiveCsv -NoTypeInformation -Encoding utf8
$archiveRows | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $archiveJson -Encoding utf8
$payloadRows | Export-Csv -LiteralPath $romCsv -NoTypeInformation -Encoding utf8
$payloadRows | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $romJson -Encoding utf8

$uniqueRomCount = @($payloadRows.RomSha256 | Sort-Object -Unique).Count
Write-Stage "Extracted $($payloadRows.Count) ROM payloads with $uniqueRomCount unique SHA-256 hashes"

$gradle = Join-Path $projectRoot 'gradlew.bat'
if (-not $SkipBuild) {
    Write-Stage 'Building parser CLI distribution'
    & $gradle '--project-dir' $projectRoot ':parser-cli:installDist' '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle parser CLI build failed with exit code $LASTEXITCODE"
    }
}

$parserCli = Join-Path $projectRoot 'parser-cli\build\install\parser-cli\bin\parser-cli.bat'
$reportJson = Join-Path $reportRoot 'compatibility.json'
$reportMarkdown = Join-Path $reportRoot 'compatibility.md'
if ($ReviewIncomplete) {
    & (Join-Path $PSScriptRoot 'Invoke-DualDexCorpusReview.ps1') `
        -RomManifest $romJson `
        -WorkRoot $workPath `
        -ApkVersionCode $ApkVersionCode `
        -MaximumIndex $MaximumIndex `
        -Rebaseline:$Rebaseline `
        -SkipBuild
    if ($LASTEXITCODE -ne 0) {
        throw "Review-gated parser validation failed with exit code $LASTEXITCODE"
    }
    $pendingReview = Join-Path $workPath 'review\pending-review.json'
    if (Test-Path -LiteralPath $pendingReview -PathType Leaf) {
        Write-Stage "Stopped for parser judgment: $pendingReview"
        return
    }
    # A review run owns its bounded denominator and reports. Never fall through
    # to the uncapped all-ROM parser invocation below.
    return
}
Write-Stage "Parsing $($payloadRows.Count) ROM payloads"
& $parserCli $romRoot '--json' $reportJson '--markdown' $reportMarkdown '--cache-dir' $cacheRoot '--all-roms'
if ($LASTEXITCODE -ne 0) {
    throw "Parser CLI failed with exit code $LASTEXITCODE"
}

$completion = [pscustomobject]@{
    completedAt = (Get-Date).ToString('o')
    archives = $archives.Count
    payloads = $payloadRows.Count
    uniqueRomHashes = $uniqueRomCount
    reportJson = $reportJson
    reportMarkdown = $reportMarkdown
}
$completion | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $workPath 'completed.json') -Encoding utf8
Write-Stage "Completed: $reportMarkdown"
