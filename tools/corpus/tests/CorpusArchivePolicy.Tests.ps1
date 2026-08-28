$validationScriptPath = Join-Path $PSScriptRoot '..\Invoke-DualDexCorpusValidation.ps1'
$tokens = $null
$parseErrors = $null
$validationScriptAst = [System.Management.Automation.Language.Parser]::ParseFile(
    $validationScriptPath,
    [ref] $tokens,
    [ref] $parseErrors
)
if ($parseErrors.Count -gt 0) {
    throw "Corpus validation script has parse errors: $($parseErrors.Message -join '; ')"
}

function Import-CorpusArchiveFunction([string] $Name) {
    $definition = $validationScriptAst.FindAll({
        param($node)
        $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $Name
    }, $true) | Select-Object -First 1
    if ($null -eq $definition) {
        throw "Corpus validation function '$Name' was not found"
    }
    Invoke-Expression "function global:$Name $($definition.Body.Extent.Text)"
}

function Invoke-AndCaptureArchiveError([scriptblock] $Action) {
    try {
        & $Action | Out-Null
        return $null
    } catch {
        return $_
    }
}

function New-DualDexSevenZipListing {
    param(
        [int] $EntryCount,
        [long] $EntrySize = 16,
        [bool] $Solid = $false
    )

    $lines = [System.Collections.Generic.List[string]]::new()
    $lines.Add('Solid = ' + $(if ($Solid) { '+' } else { '-' }))
    $lines.Add('----------')
    for ($index = 0; $index -lt $EntryCount; $index++) {
        $lines.Add(('Path = game-{0:D4}.gba' -f $index))
        $lines.Add("Size = $EntrySize")
        $lines.Add('Attributes = A')
        $lines.Add('')
    }
    return @($lines)
}

foreach ($name in @(
    'ConvertFrom-DualDexSevenZipListing',
    'Initialize-DualDexBoundedProcessType',
    'Remove-DualDexDirectoryBounded',
    'Get-DualDexDirectoryUsage',
    'Invoke-DualDexBoundedProcess',
    'Invoke-SevenZip',
    'Install-DualDexArchivePayloads'
)) {
    Import-CorpusArchiveFunction $name
}

Describe 'DualDex bounded 7-Zip corpus policy' {
    It 'rejects solid archives and the 1025th entry before extraction or integrity testing' {
        $solidError = Invoke-AndCaptureArchiveError {
            ConvertFrom-DualDexSevenZipListing `
                -Listing (New-DualDexSevenZipListing -EntryCount 1 -Solid $true) `
                -ArchivePath 'solid.7z' `
                -MaximumEntries 1024 `
                -MaximumMemberBytes 32 `
                -MaximumAggregateBytes 1024
        }
        $entryError = Invoke-AndCaptureArchiveError {
            ConvertFrom-DualDexSevenZipListing `
                -Listing (New-DualDexSevenZipListing -EntryCount 1025) `
                -ArchivePath 'many.7z' `
                -MaximumEntries 1024 `
                -MaximumMemberBytes 32 `
                -MaximumAggregateBytes 65536
        }

        $solidError | Should Not BeNullOrEmpty
        $solidError.Exception.Message | Should Match 'solid archive'
        $entryError | Should Not BeNullOrEmpty
        $entryError.Exception.Message | Should Match 'entry limit.*1024'
    }

    It 'rejects member and aggregate expansion limits while preserving valid listing order' {
        $memberError = Invoke-AndCaptureArchiveError {
            ConvertFrom-DualDexSevenZipListing `
                -Listing (New-DualDexSevenZipListing -EntryCount 1 -EntrySize 33) `
                -ArchivePath 'member.7z' `
                -MaximumEntries 4 `
                -MaximumMemberBytes 32 `
                -MaximumAggregateBytes 64
        }
        $aggregateError = Invoke-AndCaptureArchiveError {
            ConvertFrom-DualDexSevenZipListing `
                -Listing (New-DualDexSevenZipListing -EntryCount 2 -EntrySize 24) `
                -ArchivePath 'aggregate.7z' `
                -MaximumEntries 4 `
                -MaximumMemberBytes 32 `
                -MaximumAggregateBytes 40
        }
        $valid = ConvertFrom-DualDexSevenZipListing `
            -Listing (New-DualDexSevenZipListing -EntryCount 2 -EntrySize 16) `
            -ArchivePath 'valid.7z' `
            -MaximumEntries 4 `
            -MaximumMemberBytes 32 `
            -MaximumAggregateBytes 40

        $memberError.Exception.Message | Should Match 'member.*32'
        $aggregateError.Exception.Message | Should Match 'aggregate.*40'
        @($valid.Entries.Path) -join ',' | Should Be 'game-0000.gba,game-0001.gba'
        $valid.AggregateBytes | Should Be 32
    }

    It 'caps captured process output without retaining the complete stream' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-7z-output-" + [guid]::NewGuid().ToString('N'))
        $fakeSevenZip = Join-Path $fixtureRoot 'fake-output.ps1'
        [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
        try {
            [System.IO.File]::WriteAllText(
                $fakeSevenZip,
                "1..100 | ForEach-Object { 'x' * 64 }",
                [System.Text.UTF8Encoding]::new($false)
            )

            $error = Invoke-AndCaptureArchiveError {
                Invoke-DualDexBoundedProcess `
                    -FilePath $fakeSevenZip `
                    -Arguments @() `
                    -TimeoutSeconds 5 `
                    -MaximumOutputBytes 256
            }

            $error | Should Not BeNullOrEmpty
            $error.Exception.Message | Should Match 'output limit.*256'
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'preserves exact arguments through the PowerShell test-process adapter' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-7z-arguments-" + [guid]::NewGuid().ToString('N'))
        $fakeSevenZip = Join-Path $fixtureRoot 'fake-arguments.ps1'
        $capturePath = Join-Path $fixtureRoot 'arguments.txt'
        [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
        try {
            $fakeSource = @'
param([Parameter(ValueFromRemainingArguments = $true)][string[]] $FakeArgs)
[System.IO.File]::WriteAllLines($FakeArgs[0], [string[]] $FakeArgs[1..($FakeArgs.Count - 1)])
'@
            [System.IO.File]::WriteAllText($fakeSevenZip, $fakeSource, [System.Text.UTF8Encoding]::new($false))

            $result = Invoke-DualDexBoundedProcess `
                -FilePath $fakeSevenZip `
                -Arguments @($capturePath, 'x', '-oD:\Temp\stage root', '--') `
                -TimeoutSeconds 5 `
                -MaximumOutputBytes 4096

            $result.ExitCode | Should Be 0
            @(Get-Content -LiteralPath $capturePath) -join '|' | Should Be 'x|-oD:\Temp\stage root|--'
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'reports late termination after a fixed grace period instead of waiting without a bound' {
        Initialize-DualDexBoundedProcessType
        $process = Start-Process `
            -FilePath (Get-Process -Id $PID).Path `
            -ArgumentList @('-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 30') `
            -PassThru
        try {
            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            $error = Invoke-AndCaptureArchiveError {
                [DualDexBoundedProcessRunner]::WaitForExitOrThrow($process, 50)
            }
            $stopwatch.Stop()

            $error.Exception.Message | Should Match 'did not terminate.*50'
            $stopwatch.ElapsedMilliseconds | Should BeLessThan 2000
            $validationScriptAst.Extent.Text | Should Not Match '\.WaitForExit\(\)'
        } finally {
            if (-not $process.HasExited) {
                Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
                $process.WaitForExit(5000) | Out-Null
            }
            $process.Dispose()
        }
    }

    It 'waits for the owned job to report zero active descendants' {
        $validationScriptAst.Extent.Text | Should Match 'QueryInformationJobObject'
        $validationScriptAst.Extent.Text | Should Match 'ActiveProcesses'
        $validationScriptAst.Extent.Text | Should Match 'WaitForEmptyOrThrow'
    }

    It 'bounds staging cleanup when a handle never releases' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-7z-cleanup-bound-" + [guid]::NewGuid().ToString('N'))
        $lockedPath = Join-Path $fixtureRoot 'locked.gba'
        [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
        $stream = [System.IO.File]::Open(
            $lockedPath,
            [System.IO.FileMode]::Create,
            [System.IO.FileAccess]::ReadWrite,
            [System.IO.FileShare]::None
        )
        try {
            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            $error = Invoke-AndCaptureArchiveError {
                Import-CorpusArchiveFunction 'Remove-DualDexDirectoryBounded'
                Remove-DualDexDirectoryBounded `
                    -Path $fixtureRoot `
                    -TimeoutMilliseconds 50 `
                    -RetryDelayMilliseconds 10
            }
            $stopwatch.Stop()

            $error.Exception.Message | Should Match 'cleanup.*50'
            $stopwatch.ElapsedMilliseconds | Should BeLessThan 2000
        } finally {
            $stream.Dispose()
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'kills a timed out process tree rather than leaving a child behind' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-7z-timeout-" + [guid]::NewGuid().ToString('N'))
        $fakeSevenZip = Join-Path $fixtureRoot 'fake-timeout.ps1'
        $childPidPath = Join-Path $fixtureRoot 'child.pid'
        [System.IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
        try {
            $fakeSource = @'
param([string] $ChildPidPath)
$hostPath = (Get-Process -Id $PID).Path
$child = Start-Process -FilePath $hostPath -ArgumentList @(
    '-NoProfile', '-NonInteractive', '-Command', 'Start-Sleep -Seconds 30'
) -PassThru
[System.IO.File]::WriteAllText($ChildPidPath, [string] $child.Id)
Start-Sleep -Seconds 30
'@
            [System.IO.File]::WriteAllText($fakeSevenZip, $fakeSource, [System.Text.UTF8Encoding]::new($false))

            $error = Invoke-AndCaptureArchiveError {
                Invoke-DualDexBoundedProcess `
                    -FilePath $fakeSevenZip `
                    -Arguments @($childPidPath) `
                    -TimeoutSeconds 1 `
                    -MaximumOutputBytes 4096
            }

            $error.Exception.Message | Should Match 'timed out'
            (Test-Path -LiteralPath $childPidPath -PathType Leaf) | Should Be $true
            $childPid = [int] (Get-Content -LiteralPath $childPidPath -Raw)
            $deadline = [DateTime]::UtcNow.AddSeconds(3)
            while ((Get-Process -Id $childPid -ErrorAction SilentlyContinue) -and [DateTime]::UtcNow -lt $deadline) {
                Start-Sleep -Milliseconds 50
            }
            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should BeNullOrEmpty
        } finally {
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'closes the owned job when an extractor parent exits and removes its live child and staging' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-7z-parent-exit-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $archiveOutput = Join-Path $workRoot 'roms\archive'
        $fakeSevenZip = Join-Path $fixtureRoot 'fake-parent-exit.ps1'
        $childScript = Join-Path $fixtureRoot 'fake-child.ps1'
        $childPidPath = Join-Path $fixtureRoot 'child.pid'
        $archivePath = Join-Path $fixtureRoot 'sample.7z'
        $childPid = $null
        [System.IO.Directory]::CreateDirectory($archiveOutput) | Out-Null
        [System.IO.File]::WriteAllBytes($archivePath, [byte[]] @(1, 2, 3, 4))
        try {
            $childSource = @'
param([string] $OutputRoot)
$lockedPath = Join-Path $OutputRoot 'lock.tmp'
$stream = [System.IO.File]::Open(
    $lockedPath,
    [System.IO.FileMode]::Create,
    [System.IO.FileAccess]::ReadWrite,
    [System.IO.FileShare]::None
)
try {
    $stream.WriteByte(1)
    $stream.Flush()
    Start-Sleep -Seconds 30
} finally {
    $stream.Dispose()
}
'@
            $parentSource = @'
$outputArgument = @($args | Where-Object { $_.StartsWith('-o') }) | Select-Object -First 1
if ($null -eq $outputArgument) { throw 'fake 7-Zip received no output argument' }
$outputRoot = $outputArgument.Substring(2)
$hostPath = (Get-Process -Id $PID).Path
$child = Start-Process -FilePath $hostPath -ArgumentList @(
    '-NoProfile', '-NonInteractive', '-File',
    (Join-Path $PSScriptRoot 'fake-child.ps1'), $outputRoot
) -PassThru
[System.IO.File]::WriteAllText((Join-Path $PSScriptRoot 'child.pid'), [string] $child.Id)
$lockedPath = Join-Path $outputRoot 'lock.tmp'
$deadline = [DateTime]::UtcNow.AddSeconds(3)
while (-not (Test-Path -LiteralPath $lockedPath -PathType Leaf) -and [DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Milliseconds 20
}
if (-not (Test-Path -LiteralPath $lockedPath -PathType Leaf)) { throw 'child did not stage locked file' }
'@
            [System.IO.File]::WriteAllText($childScript, $childSource, [System.Text.UTF8Encoding]::new($false))
            [System.IO.File]::WriteAllText($fakeSevenZip, $parentSource, [System.Text.UTF8Encoding]::new($false))
            $global:sevenZipPath = $fakeSevenZip
            $global:workPath = $workRoot
            $entry = [pscustomobject]@{ Path = 'game.gba'; Size = 16L; Attributes = 'A' }

            $error = Invoke-AndCaptureArchiveError {
                Install-DualDexArchivePayloads `
                    -ArchivePath $archivePath `
                    -ArchiveSha256 ('b' * 64) `
                    -ArchiveOutput $archiveOutput `
                    -RomEntries @($entry) `
                    -MaximumStagingBytes 128 `
                    -MaximumSevenZipOutputBytes 4096 `
                    -SevenZipTimeoutSeconds 5
            }

            $error.Exception.Message | Should Match 'did not extract expected ROM payload'
            (Test-Path -LiteralPath $childPidPath -PathType Leaf) | Should Be $true
            $childPid = [int] (Get-Content -LiteralPath $childPidPath -Raw)
            (Get-Process -Id $childPid -ErrorAction SilentlyContinue) | Should BeNullOrEmpty
            $stagingParent = Join-Path $workRoot 'extraction-staging'
            if (Test-Path -LiteralPath $stagingParent -PathType Container) {
                @(Get-ChildItem -LiteralPath $stagingParent -Force).Count | Should Be 0
            }
        } finally {
            if ($null -ne $childPid) {
                $child = Get-Process -Id $childPid -ErrorAction SilentlyContinue
                if ($null -ne $child) {
                    Stop-Process -Id $childPid -Force -ErrorAction SilentlyContinue
                    $child.WaitForExit(5000) | Out-Null
                }
            }
            Remove-Variable sevenZipPath -Scope Global -ErrorAction SilentlyContinue
            Remove-Variable workPath -Scope Global -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }

    It 'kills over-limit extraction and cleans its staging directory' {
        $fixtureRoot = Join-Path 'D:\Temp' ("dualdex-7z-staging-" + [guid]::NewGuid().ToString('N'))
        $workRoot = Join-Path $fixtureRoot 'work'
        $archiveOutput = Join-Path $workRoot 'roms\archive'
        $fakeSevenZip = Join-Path $fixtureRoot 'fake-staging.ps1'
        $archivePath = Join-Path $fixtureRoot 'sample.7z'
        [System.IO.Directory]::CreateDirectory($archiveOutput) | Out-Null
        [System.IO.File]::WriteAllBytes($archivePath, [byte[]] @(1, 2, 3, 4))
        try {
            $fakeSource = @'
$outputArgument = @($args | Where-Object { $_.StartsWith('-o') }) | Select-Object -First 1
if ($null -eq $outputArgument) { throw 'fake 7-Zip received no output argument' }
$outputRoot = $outputArgument.Substring(2)
[System.IO.Directory]::CreateDirectory($outputRoot) | Out-Null
[System.IO.File]::WriteAllBytes((Join-Path $outputRoot 'game.gba'), [byte[]]::new(4096))
Start-Sleep -Seconds 30
'@
            [System.IO.File]::WriteAllText($fakeSevenZip, $fakeSource, [System.Text.UTF8Encoding]::new($false))
            $global:sevenZipPath = $fakeSevenZip
            $global:workPath = $workRoot
            $entry = [pscustomobject]@{ Path = 'game.gba'; Size = 16L; Attributes = 'A' }

            $error = Invoke-AndCaptureArchiveError {
                Install-DualDexArchivePayloads `
                    -ArchivePath $archivePath `
                    -ArchiveSha256 ('a' * 64) `
                    -ArchiveOutput $archiveOutput `
                    -RomEntries @($entry) `
                    -MaximumStagingBytes 128 `
                    -MaximumSevenZipOutputBytes 4096 `
                    -SevenZipTimeoutSeconds 5
            }

            $error.Exception.Message | Should Match 'staging limit.*128'
            $stagingParent = Join-Path $workRoot 'extraction-staging'
            if (Test-Path -LiteralPath $stagingParent -PathType Container) {
                @(Get-ChildItem -LiteralPath $stagingParent -Force).Count | Should Be 0
            }
        } finally {
            Remove-Variable sevenZipPath -Scope Global -ErrorAction SilentlyContinue
            Remove-Variable workPath -Scope Global -ErrorAction SilentlyContinue
            if (Test-Path -LiteralPath $fixtureRoot) {
                Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
            }
        }
    }
}
