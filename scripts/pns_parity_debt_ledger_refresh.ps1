param(
    [string]$RunsRoot = "",
    [string]$OutJsonPath = "",
    [string]$OutMarkdownPath = "",
    [string]$OwnershipPath = "",
    [string]$ManifestPath = "",
    [int]$HalHonestDeviceThreshold = 2,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_parity_debt_ledger_refresh.ps1

Builds a deduplicated parity debt ledger (schema v2) from in-app parity sweep reports.

Options:
  -RunsRoot                  Root with parity artifacts (default: <repo>/hfr-runs)
  -OutJsonPath               Output JSON path (default: <repo>/docs/FLEET_PARITY_DEBT_LEDGER.json)
  -OutMarkdownPath           Output Markdown path (default: <repo>/docs/FLEET_PARITY_DEBT_LEDGER.md)
  -OwnershipPath             Ownership map (default: <repo>/docs/M22_PROVIDER_OWNERSHIP.json)
  -ManifestPath              Proof manifest (default: <repo>/scripts/parity_proof_manifest.json)
  -HalHonestDeviceThreshold  Devices with not_advertised before HalHonestLimit (default: 2)
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $RunsRoot) { $RunsRoot = Join-Path $repoRoot "hfr-runs" }
if (-not $OutJsonPath) { $OutJsonPath = Join-Path $repoRoot "docs\FLEET_PARITY_DEBT_LEDGER.json" }
if (-not $OutMarkdownPath) { $OutMarkdownPath = Join-Path $repoRoot "docs\FLEET_PARITY_DEBT_LEDGER.md" }
if (-not $OwnershipPath) { $OwnershipPath = Join-Path $repoRoot "docs\M22_PROVIDER_OWNERSHIP.json" }
if (-not $ManifestPath) { $ManifestPath = Join-Path $repoRoot "scripts\parity_proof_manifest.json" }

if (-not (Test-Path -LiteralPath $RunsRoot)) {
    throw "Runs root not found: $RunsRoot"
}

function Load-JsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Get-CatalogMetaMap {
    $catalogKt = Join-Path $repoRoot "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalog.kt"
    $expansionKt = Join-Path $repoRoot "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalogExpansion.kt"
    $map = @{}
    foreach ($path in @($catalogKt, $expansionKt)) {
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $text = Get-Content -LiteralPath $path -Raw
        [regex]::Matches($text, 'CatalogRow\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"') | ForEach-Object {
            $id = $_.Groups[1].Value
            $display = $_.Groups[2].Value
            if (-not $map.ContainsKey($id)) {
                $map[$id] = [ordered]@{
                    catalogId = $id
                    displayName = $display
                    appStatus = "Shipped"
                    humanOnly = $false
                    buildPlanSprint = $null
                    closureEffort = $null
                    parityProofScript = $null
                }
            } else {
                $map[$id].displayName = $display
            }
        }
        [regex]::Matches($text, 'CatalogRow\s*\(\s*"([^"]+)"[^)]*\)') | ForEach-Object {
            $block = $_.Value
            if ($block -notmatch 'CatalogRow\s*\(\s*"([^"]+)"') { return }
            $id = $Matches[1]
            if (-not $map.ContainsKey($id)) {
                $map[$id] = [ordered]@{
                    catalogId = $id
                    displayName = $id
                    appStatus = "Shipped"
                    humanOnly = $false
                    buildPlanSprint = $null
                    closureEffort = $null
                    parityProofScript = $null
                }
            }
            $entry = $map[$id]
            if ($block -match 'appStatus\s*=\s*AppStatus\.(\w+)') { $entry.appStatus = $Matches[1] }
            if ($block -match 'humanOnly\s*=\s*true') { $entry.humanOnly = $true }
            if ($block -match 'buildPlanSprint\s*=\s*"([^"]+)"') { $entry.buildPlanSprint = $Matches[1] }
            if ($block -match 'closureEffort\s*=\s*"([^"]+)"') { $entry.closureEffort = $Matches[1] }
            if ($block -match 'parityProofScript\s*=\s*"([^"]+)"') { $entry.parityProofScript = $Matches[1] }
        }
    }
    return $map
}

function Test-DebtFailReason([string]$FailReason) {
    if ([string]::IsNullOrWhiteSpace($FailReason)) { return $false }
    if ($FailReason -in @("not_proven", "unautomated", "session_failed", "advertised_not_surfaced", "delivery_mismatch", "surfaced_not_advertised")) {
        return $true
    }
    if ($FailReason -like "4k120_truth_*") { return $true }
    if ($FailReason -like "skip:*") { return $false }
    return $false
}

function Get-GapClassFromCell($Cell) {
    if ($Cell.gap) { return [string]$Cell.gap }
    if ($Cell.failReason -eq "unautomated") { return "GAP_UNAUTOMATED" }
    if ($Cell.failReason -like "4k120_truth_*") { return "GAP_DELIVERY_MISMATCH" }
    if ($Cell.failReason -eq "advertised_not_surfaced") { return "GAP_ADVERTISED_NOT_SURFACED" }
    if ($Cell.failReason -eq "delivery_mismatch") { return "GAP_DELIVERY_MISMATCH" }
    if ($Cell.advertised -eq $true -and $Cell.provenOk -ne $true) { return "GAP_ADVERTISED_NOT_PROVEN" }
    if ($Cell.failReason -eq "session_failed") { return "GAP_ADVERTISED_NOT_PROVEN" }
    return "GAP_ADVERTISED_NOT_PROVEN"
}

function Get-ClosurePlanPriority([string]$GapClass) {
    switch ($GapClass) {
        "GAP_REGRESSION_SINCE_BASELINE" { return 0 }
        "GAP_DELIVERY_MISMATCH" { return 1 }
        "GAP_ADVERTISED_NOT_PROVEN" { return 2 }
        "GAP_CONFLICT_RISK" { return 3 }
        "GAP_ADVERTISED_NOT_SURFACED" { return 4 }
        "GAP_UNAUTOMATED" { return 5 }
        "GAP_PROVEN_NOT_ADVERTISED" { return 6 }
        "GAP_SURFACED_NOT_ADVERTISED" { return 7 }
        "GAP_FLEET_PLUGIN_CANDIDATE" { return 8 }
        "GAP_FLAKE_SUSPECT" { return 9 }
        "GAP_HUMAN_ONLY" { return 10 }
        "GAP_PROBE_INVENTORY" { return 11 }
        "GAP_PLANNED" { return 12 }
        default { return 13 }
    }
}

function Resolve-WorkType($Cell, $CatalogMeta, [int]$NotAdvertisedDeviceCount, [int]$HalThreshold) {
    $catalogId = [string]$Cell.catalogId
    $failReason = [string]$Cell.failReason
    $gapClass = Get-GapClassFromCell $Cell
    $meta = if ($CatalogMeta -and $CatalogMeta.ContainsKey($catalogId)) { $CatalogMeta[$catalogId] } else { $null }
    $humanOnly = $meta -and $meta.humanOnly -eq $true
    $appStatus = if ($meta) { [string]$meta.appStatus } else { "Shipped" }

    if ($humanOnly) { return "HumanOnly" }
    if ($failReason -eq "not_advertised" -or ($Cell.advertised -eq $false -and $failReason -ne "not_proven")) {
        if ($NotAdvertisedDeviceCount -ge $HalThreshold -or $appStatus -eq "ProbeOnly") {
            return "HalHonestLimit"
        }
    }
    if ($gapClass -eq "GAP_DELIVERY_MISMATCH" -or $failReason -like "4k120_truth_*" -or $failReason -eq "delivery_mismatch") {
        return "DeliveryHonesty"
    }
    if ($failReason -eq "unautomated" -or $gapClass -eq "GAP_UNAUTOMATED") {
        return "AutomationProof"
    }
    if ($failReason -eq "session_failed" -and $Cell.sessionOk -eq $false) {
        return "MatrixGate"
    }
    if ($failReason -in @("not_proven", "advertised_not_surfaced") -or $gapClass -eq "GAP_ADVERTISED_NOT_SURFACED") {
        if ($Cell.sessionOk -eq $true -or $Cell.advertised -eq $true) {
            return "AppFeature"
        }
    }
    if ($failReason -eq "not_advertised") { return "HalHonestLimit" }
    return "AppFeature"
}

function Test-ActionableWorkType([string]$WorkType) {
    return $WorkType -in @("AppFeature", "AutomationProof", "DeliveryHonesty", "MatrixGate")
}

$ownership = @{}
$ownershipObj = Load-JsonFile $OwnershipPath
if ($ownershipObj -and $ownershipObj.rows) {
    foreach ($row in @($ownershipObj.rows)) {
        if ($row.catalogId) { $ownership[[string]$row.catalogId] = $row }
    }
}

$proofRows = @{}
$manifestObj = Load-JsonFile $ManifestPath
if ($manifestObj -and $manifestObj.rows) {
    foreach ($row in @($manifestObj.rows)) {
        $catalogId = [string]$row.catalogId
        if ($catalogId) { $proofRows[$catalogId] = $row }
        foreach ($child in @($row.alsoProves)) {
            if ($child) { $proofRows[[string]$child] = $row }
        }
    }
}

$catalogMeta = Get-CatalogMetaMap
$reports = Get-ChildItem -LiteralPath $RunsRoot -Filter "in_app_parity_report.json" -Recurse -File -ErrorAction SilentlyContinue
$debtByKey = @{}
$notAdvertisedByCatalog = @{}

foreach ($inAppFile in $reports) {
    $inApp = Load-JsonFile $inAppFile.FullName
    if (-not $inApp -or -not $inApp.cells) { continue }
    $sweepDir = Split-Path -Parent $inAppFile.FullName
    $reportPath = Join-Path $sweepDir "parity_report.json"
    $report = Load-JsonFile $reportPath
    $mode = if ($report -and $report.mode) { [string]$report.mode } else { "Unknown" }
    $serial = if ($report -and $report.serial) { [string]$report.serial } else { "" }
    $deviceKey = if ($report -and $report.serial) { [string]$report.serial } else { "unknown-device" }
    $timestamp = if ($report -and $report.timestampUtc) { [DateTime]$report.timestampUtc } else { $inAppFile.LastWriteTimeUtc }

    foreach ($cell in @($inApp.cells)) {
        if ($cell.provenOk -eq $true) { continue }
        $failReason = [string]$cell.failReason
        if (-not (Test-DebtFailReason $failReason)) {
            if ($cell.advertised -eq $false -and $failReason -eq "not_advertised") {
                # track for HalHonestLimit threshold
            } else {
                continue
            }
        }
        $catalogId = [string]$cell.catalogId
        if ([string]::IsNullOrWhiteSpace($catalogId)) { continue }

        if ($failReason -eq "not_advertised" -or ($cell.advertised -eq $false -and -not $failReason)) {
            if (-not $notAdvertisedByCatalog.ContainsKey($catalogId)) { $notAdvertisedByCatalog[$catalogId] = @{} }
            $notAdvertisedByCatalog[$catalogId][$deviceKey] = $true
            if (-not (Test-DebtFailReason $failReason)) { continue }
        }

        $gapClass = Get-GapClassFromCell $cell
        $key = "$catalogId|$failReason|$gapClass"
        if (-not $debtByKey.ContainsKey($key)) {
            $meta = if ($catalogMeta.ContainsKey($catalogId)) { $catalogMeta[$catalogId] } else { $null }
            $debtByKey[$key] = [ordered]@{
                catalogId = $catalogId
                displayName = if ($meta) { [string]$meta.displayName } else { $catalogId }
                failReason = $failReason
                gapClass = $gapClass
                closurePriority = Get-ClosurePlanPriority $gapClass
                workType = $null
                actionable = $false
                recurrence = 0
                firstSeenUtc = $timestamp.ToString("o")
                lastSeenUtc = $timestamp.ToString("o")
                modes = @{}
                devices = @{}
                sampleArtifacts = @()
                ownerClass = $null
                providerDomain = $null
                closureLane = $null
                proofScript = $null
                matrixGate = $null
                buildPlanSprint = if ($meta) { $meta.buildPlanSprint } else { $null }
                closureEffort = if ($meta) { $meta.closureEffort } else { $null }
                parityProofScript = if ($meta) { $meta.parityProofScript } else { $null }
                appStatus = if ($meta) { [string]$meta.appStatus } else { "Shipped" }
                humanOnly = if ($meta) { [bool]$meta.humanOnly } else { $false }
                sessionOk = $null
                advertisedOk = $null
                provenOk = $false
            }
        }
        $row = $debtByKey[$key]
        $row.recurrence = [int]$row.recurrence + 1
        if ($cell.sessionOk -eq $true) { $row.sessionOk = $true }
        if ($cell.advertised -eq $true) { $row.advertisedOk = $true }
        if ($timestamp -lt ([DateTime]$row.firstSeenUtc)) { $row.firstSeenUtc = $timestamp.ToString("o") }
        if ($timestamp -gt ([DateTime]$row.lastSeenUtc)) { $row.lastSeenUtc = $timestamp.ToString("o") }
        if (-not $row.modes.ContainsKey($mode)) { $row.modes[$mode] = 0 }
        $row.modes[$mode] = [int]$row.modes[$mode] + 1
        if (-not $row.devices.ContainsKey($deviceKey)) {
            $row.devices[$deviceKey] = [ordered]@{
                serial = $serial
                count = 0
            }
        }
        $row.devices[$deviceKey].count = [int]$row.devices[$deviceKey].count + 1
        if (@($row.sampleArtifacts).Count -lt 5) {
            $row.sampleArtifacts += [ordered]@{
                sweepDir = $sweepDir
                mode = $mode
                timestampUtc = $timestamp.ToString("o")
            }
        }
    }
}

$rows = @($debtByKey.Values)
foreach ($row in $rows) {
    $notAdvCount = 0
    if ($notAdvertisedByCatalog.ContainsKey($row.catalogId)) {
        $notAdvCount = @($notAdvertisedByCatalog[$row.catalogId].Keys).Count
    }
    $pseudoCell = [pscustomobject]@{
        catalogId = $row.catalogId
        failReason = $row.failReason
        gap = $row.gapClass
        sessionOk = $row.sessionOk
        advertised = $row.advertisedOk
        provenOk = $false
    }
    $workType = Resolve-WorkType $pseudoCell $catalogMeta $notAdvCount $HalHonestDeviceThreshold
    $row.workType = $workType
    $row.actionable = Test-ActionableWorkType $workType

    if ($ownership.ContainsKey($row.catalogId)) {
        $o = $ownership[$row.catalogId]
        $row.ownerClass = [string]$o.ownerClass
        $row.providerDomain = [string]$o.providerDomain
        $row.closureLane = [string]$o.closureLane
    } else {
        $row.ownerClass = "UNASSIGNED"
    }
    if ($proofRows.ContainsKey($row.catalogId)) {
        $p = $proofRows[$row.catalogId]
        if (-not $row.proofScript) { $row.proofScript = [string]$p.script }
        $row.matrixGate = [string]$p.matrixGate
    }
    if (-not $row.proofScript -and $row.parityProofScript) {
        $row.proofScript = [string]$row.parityProofScript
    }
}

$orderedRows = @(
    $rows |
        Sort-Object @{ Expression = { if ($_.actionable) { 0 } else { 1 } }; Descending = $false },
        @{ Expression = { [int]$_.closurePriority }; Descending = $false },
        @{ Expression = { [int]$_.recurrence }; Descending = $true },
        @{ Expression = { $_.catalogId }; Descending = $false }
)

$byFailReason = [ordered]@{}
$byWorkType = [ordered]@{}
$byOwnerClass = [ordered]@{}
foreach ($row in $orderedRows) {
    $fr = [string]$row.failReason
    if (-not $byFailReason.Contains($fr)) { $byFailReason[$fr] = 0 }
    $byFailReason[$fr] = [int]$byFailReason[$fr] + 1
    $wt = [string]$row.workType
    if (-not $byWorkType.Contains($wt)) { $byWorkType[$wt] = 0 }
    $byWorkType[$wt] = [int]$byWorkType[$wt] + 1
    $owner = [string]$row.ownerClass
    if (-not $byOwnerClass.Contains($owner)) { $byOwnerClass[$owner] = 0 }
    $byOwnerClass[$owner] = [int]$byOwnerClass[$owner] + 1
}

$actionableCount = @($orderedRows | Where-Object { $_.actionable -eq $true }).Count

$ledger = [ordered]@{
    schema = "pns.fleet_parity_debt_ledger.v2"
    generatedUtc = [DateTime]::UtcNow.ToString("o")
    runsRoot = $RunsRoot
    reportCount = @($reports).Count
    rowCount = @($orderedRows).Count
    actionableRowCount = $actionableCount
    summary = [ordered]@{
        byFailReason = $byFailReason
        byWorkType = $byWorkType
        byOwnerClass = $byOwnerClass
    }
    rows = $orderedRows
}

$jsonDir = Split-Path -Parent $OutJsonPath
$mdDir = Split-Path -Parent $OutMarkdownPath
if ($jsonDir -and -not (Test-Path -LiteralPath $jsonDir)) { New-Item -ItemType Directory -Force -Path $jsonDir | Out-Null }
if ($mdDir -and -not (Test-Path -LiteralPath $mdDir)) { New-Item -ItemType Directory -Force -Path $mdDir | Out-Null }
$ledger | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutJsonPath -Encoding utf8

$md = @(
    "# Fleet parity debt ledger",
    "",
    "- Generated: $($ledger.generatedUtc)",
    "- Schema: $($ledger.schema)",
    "- In-app reports scanned: $($ledger.reportCount)",
    "- Debt rows: $($ledger.rowCount) (actionable: $($ledger.actionableRowCount))",
    ""
)

foreach ($wt in @("AppFeature", "AutomationProof", "DeliveryHonesty", "MatrixGate", "HalHonestLimit", "HumanOnly")) {
    $subset = @($orderedRows | Where-Object { $_.workType -eq $wt })
    if ($subset.Count -eq 0) { continue }
    $md += "## $wt ($($subset.Count))"
    $md += ""
    foreach ($row in @($subset | Select-Object -First 20)) {
        $owner = if ($row.ownerClass) { $row.ownerClass } else { "UNASSIGNED" }
        $proof = if ($row.proofScript) { $row.proofScript } else { "-" }
        $sprint = if ($row.buildPlanSprint) { $row.buildPlanSprint } else { "-" }
        $md += "- **$($row.catalogId)** gap=$($row.gapClass) fail=$($row.failReason) recurrence=$($row.recurrence) owner=$owner sprint=$sprint proof=$proof"
    }
    $md += ""
}

if (@($orderedRows).Count -eq 0) {
    $md += "- No debt rows found."
}

$md | Set-Content -LiteralPath $OutMarkdownPath -Encoding utf8

Write-Host "[parity_debt_ledger] reports=$($ledger.reportCount) rows=$($ledger.rowCount) actionable=$actionableCount"
Write-Host "[parity_debt_ledger] json=$OutJsonPath"
Write-Host "[parity_debt_ledger] md=$OutMarkdownPath"
exit 0
