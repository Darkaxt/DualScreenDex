function Test-DualDexCorpusNeedsReview {
    param(
        [Parameter(Mandatory = $true)]
        [double] $CompatibilityPercent,

        [Parameter(Mandatory = $true)]
        [bool] $ManualReviewRequired
    )

    return $CompatibilityPercent -lt 100.0 -or $ManualReviewRequired
}

function Test-DualDexCorpusRecordComplete {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Record
    )

    $percentProperty = $Record.PSObject.Properties['compatibilityPercent']
    if ($null -ne $percentProperty) {
        $manualProperty = $Record.PSObject.Properties['manualReviewRequired']
        $manualReviewRequired = $null -ne $manualProperty -and [bool] $manualProperty.Value
        return -not (Test-DualDexCorpusNeedsReview `
            -CompatibilityPercent ([double] $percentProperty.Value) `
            -ManualReviewRequired $manualReviewRequired)
    }

    return $Record.PSObject.Properties['dataCompatibility']?.Value -eq 'COMPLETE'
}

function Test-DualDexCorpusEntryInScope {
    param(
        [Parameter(Mandatory = $true)]
        [string] $EntryPath
    )

    return $EntryPath -notmatch '(?i)(Mystery Dungeon|Pinball|Puzzle Challenge|Trading Card Game|TCG)'
}

function Select-DualDexUniqueManifestEntries {
    param(
        [Parameter(Mandatory = $true)]
        [AllowEmptyCollection()]
        [object[]] $Manifest
    )

    $seenRomSha256 = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
    foreach ($entry in $Manifest) {
        $romSha256 = [string] $entry.PSObject.Properties['RomSha256']?.Value
        if ($seenRomSha256.Add($romSha256)) {
            Write-Output $entry
        }
    }
}

function Get-DualDexOrdinalSortedStrings {
    param([object[]] $Value)

    $strings = [string[]] @($Value | Where-Object { $null -ne $_ } | ForEach-Object { [string] $_ })
    [System.Array]::Sort($strings, [System.StringComparer]::Ordinal)
    return $strings
}

function Get-DualDexObservationPropertyValue {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Record,
        [Parameter(Mandatory = $true)]
        [string] $Name
    )

    if ($Record -is [System.Collections.IDictionary]) {
        return $(if ($Record.Contains($Name)) { $Record[$Name] } else { $null })
    }
    return $Record.PSObject.Properties[$Name]?.Value
}

function ConvertTo-DualDexObservationInteger {
    param(
        [object] $Value,
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if ($null -eq $Value) { return $null }
    $parsed = 0L
    if (-not [long]::TryParse(
        [string] $Value,
        [System.Globalization.NumberStyles]::Integer,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref] $parsed
    )) {
        throw "Observation field '$Path' must be an integer; found '$Value'"
    }
    return $parsed
}

function ConvertTo-DualDexObservationNumber {
    param(
        [object] $Value,
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if ($null -eq $Value) { return $null }
    $parsed = 0.0
    if (-not [double]::TryParse(
        [string] $Value,
        [System.Globalization.NumberStyles]::Float,
        [System.Globalization.CultureInfo]::InvariantCulture,
        [ref] $parsed
    ) -or [double]::IsNaN($parsed) -or [double]::IsInfinity($parsed)) {
        throw "Observation field '$Path' must be finite; found '$Value'"
    }
    return $parsed
}

function ConvertTo-DualDexObservationBoolean {
    param(
        [object] $Value,
        [Parameter(Mandatory = $true)]
        [string] $Path
    )

    if ($null -eq $Value) { return $null }
    if ($Value -is [bool]) { return [bool] $Value }
    $parsed = $false
    if (-not [bool]::TryParse([string] $Value, [ref] $parsed)) {
        throw "Observation field '$Path' must be a Boolean; found '$Value'"
    }
    return $parsed
}

function ConvertTo-DualDexStableGenericValue {
    param(
        [object] $Value,
        [string] $Path = '$'
    )

    if ($null -eq $Value) { return $null }
    if ($Value -is [string] -or $Value -is [char] -or $Value -is [bool]) { return $Value }
    if ($Value -is [byte] -or $Value -is [sbyte] -or
        $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or
        $Value -is [int64] -or $Value -is [uint64] -or
        $Value -is [decimal]) {
        return $Value
    }
    if ($Value -is [single] -or $Value -is [double]) {
        return ConvertTo-DualDexObservationNumber -Value $Value -Path $Path
    }
    # PowerShell may decorate an array with a PSObject type name after it is
    # assigned to another PSObject. Prefer its CLR IEnumerable identity over
    # that adapter identity; dictionaries remain object-shaped JSON values.
    if ($Value -is [System.Collections.IDictionary] -or
        ($Value -is [pscustomobject] -and -not ($Value -is [System.Collections.IEnumerable]))) {
        $names = if ($Value -is [System.Collections.IDictionary]) {
            @($Value.Keys)
        } else {
            @($Value.PSObject.Properties | ForEach-Object { $_.Name })
        }
        $ordered = [ordered]@{}
        foreach ($name in (Get-DualDexOrdinalSortedStrings -Value $names)) {
            # A function return travels through the PowerShell pipeline, where
            # an empty array becomes no output and a singleton array becomes
            # its only item. Read the property directly so collection identity
            # survives before the recursive conversion starts.
            if ($Value -is [System.Collections.IDictionary]) {
                $child = $Value[$name]
            } else {
                $child = $Value.PSObject.Properties[$name].Value
            }
            $ordered[$name] = ConvertTo-DualDexStableGenericValue -Value $child -Path "$Path.$name"
        }
        return [pscustomobject] $ordered
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        $convertedItems = [System.Collections.Generic.List[object]]::new()
        foreach ($item in $Value) {
            $convertedItem = ConvertTo-DualDexStableGenericValue -Value $item -Path "$Path[]"
            $convertedItems.Add([object] $convertedItem)
        }
        $converted = [object[]] $convertedItems.ToArray()
        Write-Output -NoEnumerate $converted
        return
    }
    throw "Observation field '$Path' has unsupported type '$($Value.GetType().FullName)'"
}

function ConvertTo-DualDexStableObservation {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Record
    )

    $sha = [string] (Get-DualDexObservationPropertyValue -Record $Record -Name 'romSha256')
    if ($sha -notmatch '^[0-9a-fA-F]{64}$') {
        throw "Observation ROM SHA-256 is invalid: '$sha'"
    }
    $apkVersionCode = ConvertTo-DualDexObservationInteger `
        -Value (Get-DualDexObservationPropertyValue -Record $Record -Name 'apkVersionCode') `
        -Path 'apkVersionCode'
    if ($null -eq $apkVersionCode -or $apkVersionCode -lt 1) {
        throw "Observation apkVersionCode must be positive; found '$apkVersionCode'"
    }

    $capabilityMap = [ordered]@{}
    $capabilities = @(Get-DualDexObservationPropertyValue -Record $Record -Name 'capabilities' | Where-Object { $null -ne $_ })
    $capabilityNames = @($capabilities | ForEach-Object {
        [string] (Get-DualDexObservationPropertyValue -Record $_ -Name 'capability')
    })
    if (@($capabilityNames | Where-Object { [string]::IsNullOrWhiteSpace($_) }).Count -gt 0) {
        throw 'Observation capability names must be non-blank'
    }
    if (@($capabilityNames | Group-Object | Where-Object Count -gt 1).Count -gt 0) {
        throw 'Observation capability names must be unique'
    }
    foreach ($name in (Get-DualDexOrdinalSortedStrings -Value $capabilityNames)) {
        $capability = @($capabilities | Where-Object {
            [string]::Equals(
                [string] (Get-DualDexObservationPropertyValue -Record $_ -Name 'capability'),
                $name,
                [System.StringComparison]::Ordinal
            )
        })[0]
        $reasons = @(Get-DualDexObservationPropertyValue -Record $capability -Name 'reasons' | Where-Object { $null -ne $_ })
        $capabilityMap[$name] = [pscustomobject][ordered]@{
            compatible = ConvertTo-DualDexObservationBoolean -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'compatible') -Path "capabilities.$name.compatible"
            status = Get-DualDexObservationPropertyValue -Record $capability -Name 'status'
            reviewStatus = Get-DualDexObservationPropertyValue -Record $capability -Name 'reviewStatus'
            validatorReviewRecommended = ConvertTo-DualDexObservationBoolean -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'validatorReviewRecommended') -Path "capabilities.$name.validatorReviewRecommended"
            validRecords = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'validRecords') -Path "capabilities.$name.validRecords"
            totalRecords = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'totalRecords') -Path "capabilities.$name.totalRecords"
            coveredRecords = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'coveredRecords') -Path "capabilities.$name.coveredRecords"
            expectedRecords = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'expectedRecords') -Path "capabilities.$name.expectedRecords"
            offset = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'offset') -Path "capabilities.$name.offset"
            count = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'count') -Path "capabilities.$name.count"
            recordSize = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'recordSize') -Path "capabilities.$name.recordSize"
            elementSize = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'elementSize') -Path "capabilities.$name.elementSize"
            format = Get-DualDexObservationPropertyValue -Record $capability -Name 'format'
            confidence = ConvertTo-DualDexObservationNumber -Value (Get-DualDexObservationPropertyValue -Record $capability -Name 'confidence') -Path "capabilities.$name.confidence"
            reasons = @(Get-DualDexOrdinalSortedStrings -Value $reasons)
        }
    }

    $catalogMap = [ordered]@{}
    $catalog = Get-DualDexObservationPropertyValue -Record $Record -Name 'catalog'
    if ($null -ne $catalog) {
        $catalogNames = if ($catalog -is [System.Collections.IDictionary]) {
            @($catalog.Keys)
        } else {
            @($catalog.PSObject.Properties | ForEach-Object { $_.Name })
        }
        # Report schema 11 adds structural ruleset details beside these numeric
        # aggregate metrics. Keep observation schema 1 stable: ruleset details
        # are release-document evidence, not an integer catalog count.
        foreach ($name in (Get-DualDexOrdinalSortedStrings -Value @($catalogNames | Where-Object { $_ -ne 'rulesetDetails' }))) {
            $catalogMap[$name] = ConvertTo-DualDexObservationInteger `
                -Value (Get-DualDexObservationPropertyValue -Record $catalog -Name $name) `
                -Path "catalog.$name"
        }
    }

    $referenceErrors = @(Get-DualDexObservationPropertyValue -Record $Record -Name 'referenceErrors' | Where-Object { $null -ne $_ })
    return [pscustomobject][ordered]@{
        romSha256 = $sha.ToLowerInvariant()
        # APK version is baseline audit metadata, not parser behavior. Excluding
        # it lets a new APK pass compare against the prior observation.
        family = Get-DualDexObservationPropertyValue -Record $Record -Name 'selectedFamily'
        profile = Get-DualDexObservationPropertyValue -Record $Record -Name 'selectedProfile'
        status = Get-DualDexObservationPropertyValue -Record $Record -Name 'selectionStatus'
        margin = ConvertTo-DualDexObservationNumber -Value (Get-DualDexObservationPropertyValue -Record $Record -Name 'runnerUpMargin') -Path 'runnerUpMargin'
        compatibilityPercent = ConvertTo-DualDexObservationNumber -Value (Get-DualDexObservationPropertyValue -Record $Record -Name 'compatibilityPercent') -Path 'compatibilityPercent'
        resolvedFeatureCount = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $Record -Name 'resolvedFeatureCount') -Path 'resolvedFeatureCount'
        expectedFeatureCount = ConvertTo-DualDexObservationInteger -Value (Get-DualDexObservationPropertyValue -Record $Record -Name 'expectedFeatureCount') -Path 'expectedFeatureCount'
        manualReviewRequired = ConvertTo-DualDexObservationBoolean -Value (Get-DualDexObservationPropertyValue -Record $Record -Name 'manualReviewRequired') -Path 'manualReviewRequired'
        dataCompatibility = Get-DualDexObservationPropertyValue -Record $Record -Name 'dataCompatibility'
        capabilities = [pscustomobject] $capabilityMap
        catalog = [pscustomobject] $catalogMap
        matchedTableFirstRegisters = ConvertTo-DualDexStableGenericValue `
            -Value (Get-DualDexObservationPropertyValue -Record $Record -Name 'matchedTableFirstRegisters') `
            -Path 'matchedTableFirstRegisters'
        referenceErrors = @(Get-DualDexOrdinalSortedStrings -Value $referenceErrors)
        catalogError = Get-DualDexObservationPropertyValue -Record $Record -Name 'catalogError'
        persistenceError = Get-DualDexObservationPropertyValue -Record $Record -Name 'persistenceError'
    }
}

function ConvertTo-DualDexCanonicalJson {
    param(
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [object] $Value
    )

    $stable = ConvertTo-DualDexStableGenericValue -Value $Value
    return ConvertTo-Json -InputObject $stable -Depth 64 -Compress
}

function Get-DualDexObservationHash {
    param(
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [object] $Observation
    )

    $json = ConvertTo-DualDexCanonicalJson -Value $Observation
    $bytes = [System.Text.UTF8Encoding]::new($false).GetBytes($json)
    $sha256 = [System.Security.Cryptography.SHA256]::Create()
    try {
        return ([System.BitConverter]::ToString($sha256.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $sha256.Dispose()
    }
}

function Compare-DualDexObservation {
    param(
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [object] $Before,
        [Parameter(Mandatory = $true)]
        [AllowNull()]
        [object] $After
    )

    $beforeDocument = [System.Text.Json.JsonDocument]::Parse((ConvertTo-DualDexCanonicalJson -Value $Before))
    $afterDocument = [System.Text.Json.JsonDocument]::Parse((ConvertTo-DualDexCanonicalJson -Value $After))
    $beforeLeaves = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)
    $afterLeaves = [System.Collections.Generic.Dictionary[string, string]]::new([System.StringComparer]::Ordinal)

    function ConvertTo-DualDexJsonPointerSegment {
        param([Parameter(Mandatory = $true)][string] $Value)

        return $Value.Replace('~', '~0').Replace('/', '~1')
    }

    function Add-DualDexObservationLeaves {
        param(
            [System.Text.Json.JsonElement] $Element,
            [string] $Path,
            [System.Collections.Generic.Dictionary[string, string]] $Leaves
        )

        if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Object) {
            $properties = [System.Collections.Generic.Dictionary[string, System.Text.Json.JsonElement]]::new([System.StringComparer]::Ordinal)
            foreach ($property in $Element.EnumerateObject()) { $properties[$property.Name] = $property.Value.Clone() }
            if ($properties.Count -eq 0) {
                $Leaves[$Path] = '{}'
                return
            }
            foreach ($name in (Get-DualDexOrdinalSortedStrings -Value @($properties.Keys))) {
                $childPath = "$Path/$(ConvertTo-DualDexJsonPointerSegment -Value $name)"
                Add-DualDexObservationLeaves -Element $properties[$name] -Path $childPath -Leaves $Leaves
            }
            return
        }
        if ($Element.ValueKind -eq [System.Text.Json.JsonValueKind]::Array) {
            $values = @($Element.EnumerateArray() | ForEach-Object { $_.Clone() })
            if ($values.Count -eq 0) {
                $Leaves[$Path] = '[]'
                return
            }
            for ($index = 0; $index -lt $values.Count; $index++) {
                Add-DualDexObservationLeaves -Element $values[$index] -Path "$Path/$index" -Leaves $Leaves
            }
            return
        }
        $Leaves[$Path] = $Element.GetRawText()
    }

    function ConvertFrom-DualDexDiffJsonValue {
        param(
            [string] $Json,
            [bool] $Exists
        )

        if (-not $Exists) { return '<missing>' }
        if ($Json -eq '[]') { return [pscustomobject][ordered]@{ emptyContainer = 'array' } }
        if ($Json -eq '{}') { return [pscustomobject][ordered]@{ emptyContainer = 'object' } }
        return $Json | ConvertFrom-Json
    }

    try {
        Add-DualDexObservationLeaves -Element $beforeDocument.RootElement -Path '' -Leaves $beforeLeaves
        Add-DualDexObservationLeaves -Element $afterDocument.RootElement -Path '' -Leaves $afterLeaves
        $allPaths = @($beforeLeaves.Keys) + @($afterLeaves.Keys)
        $orderedPaths = Get-DualDexOrdinalSortedStrings -Value @($allPaths | Select-Object -Unique)
        $changes = [System.Collections.Generic.List[object]]::new()
        foreach ($path in $orderedPaths) {
            $beforeExists = $beforeLeaves.ContainsKey($path)
            $afterExists = $afterLeaves.ContainsKey($path)
            $beforeJson = $(if ($beforeExists) { $beforeLeaves[$path] } else { $null })
            $afterJson = $(if ($afterExists) { $afterLeaves[$path] } else { $null })
            if ($beforeExists -and $afterExists -and
                [string]::Equals($beforeJson, $afterJson, [System.StringComparison]::Ordinal)) {
                continue
            }
            $changes.Add([pscustomobject][ordered]@{
                path = $path
                before = ConvertFrom-DualDexDiffJsonValue -Json $beforeJson -Exists $beforeExists
                after = ConvertFrom-DualDexDiffJsonValue -Json $afterJson -Exists $afterExists
                beforeJson = $beforeJson
                afterJson = $afterJson
            })
        }
        return @($changes)
    } finally {
        $beforeDocument.Dispose()
        $afterDocument.Dispose()
    }
}

function Test-DualDexDeltaDecisionApplicable {
    param(
        [Parameter(Mandatory = $true)]
        [object] $Decision,
        [Parameter(Mandatory = $true)]
        [object] $Delta,
        [Parameter(Mandatory = $true)]
        [int] $ApkVersionCode
    )

    $decisionVersion = 0L
    return [long]::TryParse([string] (Get-DualDexObservationPropertyValue -Record $Decision -Name 'apkVersionCode'), [ref] $decisionVersion) -and
        $decisionVersion -eq [long] $ApkVersionCode -and
        [string]::Equals([string] (Get-DualDexObservationPropertyValue -Record $Decision -Name 'romSha256'), [string] (Get-DualDexObservationPropertyValue -Record $Delta -Name 'romSha256'), [System.StringComparison]::OrdinalIgnoreCase) -and
        [string]::Equals([string] (Get-DualDexObservationPropertyValue -Record $Decision -Name 'beforeHash'), [string] (Get-DualDexObservationPropertyValue -Record $Delta -Name 'beforeHash'), [System.StringComparison]::OrdinalIgnoreCase) -and
        [string]::Equals([string] (Get-DualDexObservationPropertyValue -Record $Decision -Name 'afterHash'), [string] (Get-DualDexObservationPropertyValue -Record $Delta -Name 'afterHash'), [System.StringComparison]::OrdinalIgnoreCase) -and
        -not [string]::IsNullOrWhiteSpace([string] (Get-DualDexObservationPropertyValue -Record $Decision -Name 'reason'))
}
