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

$maximumArchiveEntries = 1024
$maximumArchiveMemberBytes = 32L * 1024 * 1024
$maximumArchiveAggregateBytes = 16L * 1024 * 1024 * 1024
$maximumExtractionStagingBytes = $maximumArchiveAggregateBytes
$maximumSevenZipOutputBytes = 1L * 1024 * 1024
$sevenZipTimeoutSeconds = 120

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

function Initialize-DualDexBoundedProcessType {
    if ('DualDexBoundedProcessRunner' -as [type]) {
        return
    }

    Add-Type -TypeDefinition @'
using System;
using System.ComponentModel;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

public sealed class DualDexBoundedProcessResult
{
    public int ExitCode { get; set; }
    public string[] Output { get; set; }
    public bool TimedOut { get; set; }
    public bool OutputLimitExceeded { get; set; }
    public bool StagingLimitExceeded { get; set; }
}

internal sealed class DualDexBoundedOutputCapture
{
    private readonly object gate = new object();
    private readonly long maximumBytes;
    private readonly MemoryStream retained = new MemoryStream();
    private bool limitExceeded;

    public DualDexBoundedOutputCapture(long maximumBytes)
    {
        this.maximumBytes = maximumBytes;
    }

    public bool LimitExceeded
    {
        get
        {
            lock (gate) return limitExceeded;
        }
    }

    public void Drain(Stream stream)
    {
        var chunk = new byte[4096];
        try
        {
            while (true)
            {
                var count = stream.Read(chunk, 0, chunk.Length);
                if (count <= 0) return;
                lock (gate)
                {
                    var remaining = maximumBytes - retained.Length;
                    if (count > remaining)
                    {
                        if (remaining > 0) retained.Write(chunk, 0, (int)remaining);
                        limitExceeded = true;
                        return;
                    }
                    retained.Write(chunk, 0, count);
                }
            }
        }
        catch (IOException)
        {
        }
        catch (ObjectDisposedException)
        {
        }
    }

    public string[] GetLines()
    {
        lock (gate)
        {
            if (retained.Length == 0) return new string[0];
            var text = Encoding.UTF8.GetString(retained.ToArray());
            return text.Split(new[] { "\r\n", "\n", "\r" }, StringSplitOptions.None);
        }
    }
}

internal sealed class DualDexProcessJob : IDisposable
{
    private const uint BasicAccountingInformationClass = 1;
    private const uint ExtendedLimitInformationClass = 9;
    private const uint JobObjectLimitKillOnJobClose = 0x00002000;
    private IntPtr handle;

    public DualDexProcessJob()
    {
        handle = CreateJobObject(IntPtr.Zero, null);
        if (handle == IntPtr.Zero)
        {
            throw new Win32Exception(Marshal.GetLastWin32Error(), "could not create process job");
        }

        var limits = new JobObjectExtendedLimitInformation();
        limits.BasicLimitInformation.LimitFlags = JobObjectLimitKillOnJobClose;
        var size = Marshal.SizeOf(typeof(JobObjectExtendedLimitInformation));
        var buffer = Marshal.AllocHGlobal(size);
        try
        {
            Marshal.StructureToPtr(limits, buffer, false);
            if (!SetInformationJobObject(handle, ExtendedLimitInformationClass, buffer, (uint)size))
            {
                throw new Win32Exception(Marshal.GetLastWin32Error(), "could not configure kill-on-close process job");
            }
        }
        catch
        {
            Dispose();
            throw;
        }
        finally
        {
            Marshal.FreeHGlobal(buffer);
        }
    }

    public void Assign(Process process)
    {
        if (process == null) throw new ArgumentNullException("process");
        if (handle == IntPtr.Zero) throw new ObjectDisposedException("DualDexProcessJob");
        if (!AssignProcessToJobObject(handle, process.Handle))
        {
            throw new Win32Exception(Marshal.GetLastWin32Error(), "could not assign extractor to owned process job");
        }
    }

    public void Terminate()
    {
        if (handle == IntPtr.Zero) return;
        if (!TerminateJobObject(handle, 1))
        {
            throw new Win32Exception(Marshal.GetLastWin32Error(), "could not terminate owned process job");
        }
    }

    public void WaitForEmptyOrThrow(int graceMilliseconds)
    {
        if (graceMilliseconds < 1) throw new ArgumentOutOfRangeException("graceMilliseconds");
        if (handle == IntPtr.Zero) throw new ObjectDisposedException("DualDexProcessJob");

        var size = Marshal.SizeOf(typeof(JobObjectBasicAccountingInformation));
        var buffer = Marshal.AllocHGlobal(size);
        var stopwatch = Stopwatch.StartNew();
        try
        {
            while (true)
            {
                uint returnedLength;
                if (!QueryInformationJobObject(
                    handle,
                    BasicAccountingInformationClass,
                    buffer,
                    (uint)size,
                    out returnedLength))
                {
                    throw new Win32Exception(
                        Marshal.GetLastWin32Error(),
                        "could not query owned process job accounting");
                }
                var accounting = (JobObjectBasicAccountingInformation)Marshal.PtrToStructure(
                    buffer,
                    typeof(JobObjectBasicAccountingInformation));
                if (accounting.ActiveProcesses == 0) return;

                var remainingMilliseconds = graceMilliseconds - stopwatch.ElapsedMilliseconds;
                if (remainingMilliseconds <= 0)
                {
                    throw new InvalidOperationException(
                        "owned process job did not reach zero active processes within " +
                        graceMilliseconds + " ms");
                }
                Thread.Sleep((int)Math.Min(10, remainingMilliseconds));
            }
        }
        finally
        {
            Marshal.FreeHGlobal(buffer);
        }
    }

    public void Dispose()
    {
        var ownedHandle = Interlocked.Exchange(ref handle, IntPtr.Zero);
        if (ownedHandle != IntPtr.Zero && !CloseHandle(ownedHandle))
        {
            throw new Win32Exception(Marshal.GetLastWin32Error(), "could not close owned process job");
        }
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JobObjectBasicAccountingInformation
    {
        public long TotalUserTime;
        public long TotalKernelTime;
        public long ThisPeriodTotalUserTime;
        public long ThisPeriodTotalKernelTime;
        public uint TotalPageFaultCount;
        public uint TotalProcesses;
        public uint ActiveProcesses;
        public uint TotalTerminatedProcesses;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct IoCounters
    {
        public ulong ReadOperationCount;
        public ulong WriteOperationCount;
        public ulong OtherOperationCount;
        public ulong ReadTransferCount;
        public ulong WriteTransferCount;
        public ulong OtherTransferCount;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JobObjectBasicLimitInformation
    {
        public long PerProcessUserTimeLimit;
        public long PerJobUserTimeLimit;
        public uint LimitFlags;
        public UIntPtr MinimumWorkingSetSize;
        public UIntPtr MaximumWorkingSetSize;
        public uint ActiveProcessLimit;
        public UIntPtr Affinity;
        public uint PriorityClass;
        public uint SchedulingClass;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct JobObjectExtendedLimitInformation
    {
        public JobObjectBasicLimitInformation BasicLimitInformation;
        public IoCounters IoInfo;
        public UIntPtr ProcessMemoryLimit;
        public UIntPtr JobMemoryLimit;
        public UIntPtr PeakProcessMemoryUsed;
        public UIntPtr PeakJobMemoryUsed;
    }

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr CreateJobObject(IntPtr securityAttributes, string name);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool SetInformationJobObject(
        IntPtr job,
        uint informationClass,
        IntPtr information,
        uint informationLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool QueryInformationJobObject(
        IntPtr job,
        uint informationClass,
        IntPtr information,
        uint informationLength,
        out uint returnLength);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool TerminateJobObject(IntPtr job, uint exitCode);

    [DllImport("kernel32.dll", SetLastError = true)]
    private static extern bool CloseHandle(IntPtr handle);
}

public static class DualDexBoundedProcessRunner
{
    private const int TerminationGraceMilliseconds = 5000;
    private const int StreamCloseGraceMilliseconds = 5000;

    public static DualDexBoundedProcessResult Run(
        string filePath,
        string[] arguments,
        int timeoutSeconds,
        long maximumOutputBytes,
        string stagingRoot,
        long maximumStagingBytes)
    {
        var startInfo = new ProcessStartInfo
        {
            FileName = filePath,
            Arguments = BuildArguments(arguments),
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true
        };
        var process = new Process { StartInfo = startInfo };
        var job = new DualDexProcessJob();
        var output = new DualDexBoundedOutputCapture(maximumOutputBytes);
        Thread outputThread = null;
        Thread errorThread = null;
        var timedOut = false;
        var stagingExceeded = false;
        var started = false;
        var jobClosed = false;
        var jobTerminationRequested = false;
        try
        {
            if (!process.Start()) throw new InvalidOperationException("process did not start");
            started = true;
            job.Assign(process);
            outputThread = new Thread(delegate() { output.Drain(process.StandardOutput.BaseStream); });
            errorThread = new Thread(delegate() { output.Drain(process.StandardError.BaseStream); });
            outputThread.IsBackground = true;
            errorThread.IsBackground = true;
            outputThread.Start();
            errorThread.Start();

            var deadline = Stopwatch.StartNew();
            while (!process.WaitForExit(50))
            {
                if (output.LimitExceeded) break;
                if (deadline.Elapsed >= TimeSpan.FromSeconds(timeoutSeconds))
                {
                    timedOut = true;
                    break;
                }
                if (!string.IsNullOrEmpty(stagingRoot) && Directory.Exists(stagingRoot))
                {
                    long stagedBytes = 0;
                    foreach (var path in Directory.EnumerateFiles(stagingRoot, "*", SearchOption.AllDirectories))
                    {
                        var length = new FileInfo(path).Length;
                        if (length > maximumStagingBytes - stagedBytes)
                        {
                            stagingExceeded = true;
                            break;
                        }
                        stagedBytes += length;
                    }
                    if (stagingExceeded) break;
                }
            }

            if (output.LimitExceeded || timedOut || stagingExceeded)
            {
                job.Terminate();
                jobTerminationRequested = true;
            }
            WaitForExitOrThrow(process, TerminationGraceMilliseconds);
            var exitCode = process.ExitCode;

            if (!jobTerminationRequested)
            {
                job.Terminate();
                jobTerminationRequested = true;
            }
            job.WaitForEmptyOrThrow(TerminationGraceMilliseconds);
            job.Dispose();
            jobClosed = true;
            JoinThreadOrThrow(outputThread, StreamCloseGraceMilliseconds, "standard output");
            JoinThreadOrThrow(errorThread, StreamCloseGraceMilliseconds, "standard error");

            return new DualDexBoundedProcessResult
            {
                ExitCode = exitCode,
                Output = output.GetLines(),
                TimedOut = timedOut,
                OutputLimitExceeded = output.LimitExceeded,
                StagingLimitExceeded = stagingExceeded
            };
        }
        finally
        {
            Exception cleanupFailure = null;
            if (!jobClosed)
            {
                if (started && !jobTerminationRequested)
                {
                    try
                    {
                        job.Terminate();
                        jobTerminationRequested = true;
                    }
                    catch (Exception error)
                    {
                        cleanupFailure = error;
                    }
                }
                if (started)
                {
                    try
                    {
                        job.WaitForEmptyOrThrow(TerminationGraceMilliseconds);
                    }
                    catch (Exception error)
                    {
                        if (cleanupFailure == null) cleanupFailure = error;
                    }
                }
                try
                {
                    job.Dispose();
                    jobClosed = true;
                }
                catch (Exception error)
                {
                    if (cleanupFailure == null) cleanupFailure = error;
                }
            }
            if (started && !process.HasExited)
            {
                try
                {
                    WaitForExitOrThrow(process, TerminationGraceMilliseconds);
                }
                catch (Exception error)
                {
                    if (cleanupFailure == null) cleanupFailure = error;
                }
            }
            try
            {
                JoinThreadOrThrow(outputThread, StreamCloseGraceMilliseconds, "standard output");
                JoinThreadOrThrow(errorThread, StreamCloseGraceMilliseconds, "standard error");
            }
            catch (Exception error)
            {
                if (cleanupFailure == null) cleanupFailure = error;
            }
            process.Dispose();
            if (cleanupFailure != null)
            {
                throw new InvalidOperationException("bounded process cleanup failed", cleanupFailure);
            }
        }
    }

    public static void WaitForExitOrThrow(Process process, int graceMilliseconds)
    {
        if (process == null) throw new ArgumentNullException("process");
        if (graceMilliseconds < 1) throw new ArgumentOutOfRangeException("graceMilliseconds");
        if (!process.WaitForExit(graceMilliseconds))
        {
            throw new InvalidOperationException(
                "process did not terminate within " + graceMilliseconds + " ms");
        }
    }

    private static void JoinThreadOrThrow(Thread thread, int graceMilliseconds, string streamName)
    {
        if (thread != null && thread.IsAlive && !thread.Join(graceMilliseconds))
        {
            throw new InvalidOperationException(
                "process " + streamName + " stream did not close within " + graceMilliseconds + " ms");
        }
    }

    private static string BuildArguments(string[] arguments)
    {
        var commandLine = new StringBuilder();
        foreach (var argument in arguments)
        {
            if (commandLine.Length > 0) commandLine.Append(' ');
            commandLine.Append(QuoteArgument(argument ?? string.Empty));
        }
        return commandLine.ToString();
    }

    private static string QuoteArgument(string argument)
    {
        var requiresQuotes = argument.Length == 0;
        for (var index = 0; index < argument.Length && !requiresQuotes; index++)
        {
            requiresQuotes = char.IsWhiteSpace(argument[index]) || argument[index] == '"';
        }
        if (!requiresQuotes) return argument;

        var quoted = new StringBuilder();
        quoted.Append('"');
        var backslashes = 0;
        foreach (var character in argument)
        {
            if (character == '\\')
            {
                backslashes++;
            }
            else if (character == '"')
            {
                quoted.Append('\\', backslashes * 2 + 1);
                quoted.Append('"');
                backslashes = 0;
            }
            else
            {
                quoted.Append('\\', backslashes);
                quoted.Append(character);
                backslashes = 0;
            }
        }
        quoted.Append('\\', backslashes * 2);
        quoted.Append('"');
        return quoted.ToString();
    }
}
'@
}

function Remove-DualDexDirectoryBounded {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [ValidateRange(1, 2147483647)]
        [int] $TimeoutMilliseconds = 5000,

        [ValidateRange(1, 2147483647)]
        [int] $RetryDelayMilliseconds = 25
    )

    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $lastFailure = $null
    while ([System.IO.Directory]::Exists($fullPath)) {
        try {
            [System.IO.Directory]::Delete($fullPath, $true)
            return
        } catch [System.IO.IOException] {
            $lastFailure = $_.Exception
        } catch [System.UnauthorizedAccessException] {
            $lastFailure = $_.Exception
        }

        $remainingMilliseconds = $TimeoutMilliseconds - $stopwatch.ElapsedMilliseconds
        if ($remainingMilliseconds -le 0) {
            $message = "7-Zip staging cleanup did not complete within $TimeoutMilliseconds ms: $fullPath"
            throw [System.InvalidOperationException]::new($message, $lastFailure)
        }
        Start-Sleep -Milliseconds ([Math]::Min($RetryDelayMilliseconds, $remainingMilliseconds))
    }
}

function Get-DualDexDirectoryUsage {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Path,

        [Parameter(Mandatory = $true)]
        [long] $MaximumBytes
    )

    $bytes = 0L
    $files = 0
    if (Test-Path -LiteralPath $Path -PathType Container) {
        foreach ($file in [System.IO.Directory]::EnumerateFiles($Path, '*', [System.IO.SearchOption]::AllDirectories)) {
            $length = (Get-Item -LiteralPath $file).Length
            if ($bytes -gt $MaximumBytes - $length) {
                throw "7-Zip staging limit exceeded ($($bytes + $length) > $MaximumBytes bytes)"
            }
            $bytes += $length
            $files++
        }
    }
    return [pscustomobject]@{ Bytes = $bytes; Files = $files }
}

function Invoke-DualDexBoundedProcess {
    param(
        [Parameter(Mandatory = $true)]
        [string] $FilePath,

        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [string[]] $Arguments,

        [Parameter(Mandatory = $true)]
        [ValidateRange(1, 2147483647)]
        [int] $TimeoutSeconds,

        [Parameter(Mandatory = $true)]
        [ValidateRange(1, 9223372036854775807)]
        [long] $MaximumOutputBytes,

        [string] $StagingRoot,

        [ValidateRange(1, 9223372036854775807)]
        [long] $MaximumStagingBytes = 1
    )

    Initialize-DualDexBoundedProcessType
    $effectivePath = [System.IO.Path]::GetFullPath($FilePath)
    $effectiveArguments = [System.Collections.Generic.List[string]]::new()
    if ([System.IO.Path]::GetExtension($effectivePath) -eq '.ps1') {
        $quotedScriptPath = "'" + $effectivePath.Replace("'", "''") + "'"
        $scriptInvocation = "& $quotedScriptPath"
        foreach ($argument in $Arguments) {
            $quotedArgument = "'" + ([string] $argument).Replace("'", "''") + "'"
            $scriptInvocation += " $quotedArgument"
        }
        $hostPath = (Get-Process -Id $PID).Path
        $effectiveArguments.Add('-NoProfile')
        $effectiveArguments.Add('-NonInteractive')
        $effectiveArguments.Add('-Command')
        $effectiveArguments.Add($scriptInvocation)
        $effectivePath = $hostPath
    } else {
        foreach ($argument in $Arguments) {
            $effectiveArguments.Add([string] $argument)
        }
    }

    $result = [DualDexBoundedProcessRunner]::Run(
        $effectivePath,
        @($effectiveArguments),
        $TimeoutSeconds,
        $MaximumOutputBytes,
        $StagingRoot,
        $MaximumStagingBytes
    )
    if ($result.OutputLimitExceeded) {
        throw "7-Zip output limit exceeded ($MaximumOutputBytes bytes)"
    }
    if ($result.StagingLimitExceeded) {
        throw "7-Zip staging limit exceeded ($MaximumStagingBytes bytes)"
    }
    if ($result.TimedOut) {
        throw "7-Zip timed out after $TimeoutSeconds seconds"
    }
    return $result
}

function Invoke-SevenZip {
    param(
        [Parameter(Mandatory = $true)]
        [string[]] $Arguments,

        [int] $TimeoutSeconds = $sevenZipTimeoutSeconds,

        [long] $MaximumOutputBytes = $maximumSevenZipOutputBytes,

        [string] $StagingRoot,

        [long] $MaximumStagingBytes = $maximumExtractionStagingBytes
    )

    $result = Invoke-DualDexBoundedProcess `
        -FilePath $sevenZipPath `
        -Arguments $Arguments `
        -TimeoutSeconds $TimeoutSeconds `
        -MaximumOutputBytes $MaximumOutputBytes `
        -StagingRoot $StagingRoot `
        -MaximumStagingBytes $MaximumStagingBytes
    if ($result.ExitCode -ne 0) {
        throw "7-Zip failed with exit code $($result.ExitCode)`n$($result.Output -join [Environment]::NewLine)"
    }
    return @($result.Output)
}

function ConvertFrom-DualDexSevenZipListing {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyString()]
        [string[]] $Listing,

        [Parameter(Mandatory = $true)]
        [string] $ArchivePath,

        [Parameter(Mandatory = $true)]
        [int] $MaximumEntries,

        [Parameter(Mandatory = $true)]
        [long] $MaximumMemberBytes,

        [Parameter(Mandatory = $true)]
        [long] $MaximumAggregateBytes
    )

    if (@($Listing | Where-Object { $_ -eq 'Solid = +' }).Count -gt 0) {
        throw "7-Zip solid archive is not supported by the bounded corpus policy: $ArchivePath"
    }
    $separator = [Array]::IndexOf($Listing, '----------')
    if ($separator -lt 0) {
        throw "7-Zip listing has no entry separator: $ArchivePath"
    }

    $entries = [System.Collections.Generic.List[object]]::new()
    $aggregateBytes = 0L
    $fields = @{}
    foreach ($line in @($Listing | Select-Object -Skip ($separator + 1)) + '') {
        if ([string]::IsNullOrWhiteSpace($line)) {
            if ($fields.Count -gt 0) {
                $entryPath = [string] $fields['Path']
                if ($entryPath) {
                    if ($entries.Count -eq $MaximumEntries) {
                        throw "7-Zip archive entry limit exceeded ($($entries.Count + 1) > $MaximumEntries): $ArchivePath"
                    }
                    $size = 0L
                    if ($fields.ContainsKey('Size') -and
                        -not [long]::TryParse([string] $fields['Size'], [ref] $size)) {
                        throw "7-Zip archive member has an invalid size: $ArchivePath :: $entryPath"
                    }
                    if ($size -lt 0 -or $size -gt $MaximumMemberBytes) {
                        throw "7-Zip archive member exceeds $MaximumMemberBytes bytes: $ArchivePath :: $entryPath"
                    }
                    if ($aggregateBytes -gt $MaximumAggregateBytes - $size) {
                        throw "7-Zip archive aggregate exceeds $MaximumAggregateBytes bytes: $ArchivePath"
                    }
                    $aggregateBytes += $size
                    $entries.Add([pscustomobject]@{
                        Path = $entryPath
                        Size = $size
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
    return [pscustomobject]@{
        Entries = @($entries)
        AggregateBytes = $aggregateBytes
    }
}

function Read-ArchiveEntries([string] $archivePath) {
    $listing = Invoke-SevenZip -Arguments @('l', '-slt', '-sccUTF-8', '--', $archivePath)
    return ConvertFrom-DualDexSevenZipListing `
        -Listing $listing `
        -ArchivePath $archivePath `
        -MaximumEntries $maximumArchiveEntries `
        -MaximumMemberBytes $maximumArchiveMemberBytes `
        -MaximumAggregateBytes $maximumArchiveAggregateBytes
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
        [object[]] $RomEntries,

        [long] $MaximumStagingBytes = $maximumExtractionStagingBytes,

        [long] $MaximumSevenZipOutputBytes = $maximumSevenZipOutputBytes,

        [int] $SevenZipTimeoutSeconds = $sevenZipTimeoutSeconds
    )

    $stagingParent = Join-Path $workPath 'extraction-staging'
    [System.IO.Directory]::CreateDirectory($stagingParent) | Out-Null
    $stagingRoot = Join-Path $stagingParent ([System.IO.Path]::GetRandomFileName())
    [System.IO.Directory]::CreateDirectory($stagingRoot) | Out-Null
    try {
        $extractArguments = @('x', '-y', '-sccUTF-8', ('-o' + $stagingRoot), '--', $ArchivePath) + @($RomEntries.Path)
        Invoke-SevenZip `
            -Arguments $extractArguments `
            -TimeoutSeconds $SevenZipTimeoutSeconds `
            -MaximumOutputBytes $MaximumSevenZipOutputBytes `
            -StagingRoot $stagingRoot `
            -MaximumStagingBytes $MaximumStagingBytes | Out-Null
        $stagingUsage = Get-DualDexDirectoryUsage -Path $stagingRoot -MaximumBytes $MaximumStagingBytes
        if ($stagingUsage.Files -gt $RomEntries.Count) {
            throw "7-Zip staged unexpected files ($($stagingUsage.Files) > $($RomEntries.Count))"
        }
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
        Remove-DualDexDirectoryBounded -Path $stagingRoot
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

    $archiveSha = (Get-FileHash -LiteralPath $archive.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $archiveListing = Read-ArchiveEntries $archive.FullName
    $entries = @($archiveListing.Entries)
    $romEntries = @($entries | Where-Object { [System.IO.Path]::GetExtension($_.Path).ToLowerInvariant() -in '.gb', '.gbc', '.gba' })
    foreach ($entry in $entries) {
        Assert-SafeEntryPath $entry.Path $archive.FullName
    }
    Invoke-SevenZip -Arguments @('t', '-sccUTF-8', '--', $archive.FullName) | Out-Null

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
$sourceCommit = (& git -C $projectRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $sourceCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'Could not resolve the parser source commit.'
}
$trackedChanges = @(& git -C $projectRoot status --porcelain --untracked-files=no)
if ($LASTEXITCODE -ne 0 -or $trackedChanges.Count -ne 0) {
    throw 'Corpus evidence requires a clean tracked source tree.'
}
if (-not $SkipBuild) {
    Write-Stage 'Building parser CLI distribution'
    & $gradle '--project-dir' $projectRoot ':parser-cli:installDist' "-PdualdexSourceCommit=$sourceCommit" '--console=plain'
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle parser CLI build failed with exit code $LASTEXITCODE"
    }
}

$parserCli = Join-Path $projectRoot 'parser-cli\build\install\parser-cli\bin\parser-cli.bat'
$reportJson = Join-Path $reportRoot 'compatibility.json'
$reportMarkdown = Join-Path $reportRoot 'compatibility.md'
$executionReceipt = Join-Path $reportRoot 'compatibility-execution.json'
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
& $parserCli $romRoot '--json' $reportJson '--markdown' $reportMarkdown `
    '--execution-receipt' $executionReceipt '--source-commit' $sourceCommit `
    '--cache-dir' $cacheRoot '--all-roms'
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
