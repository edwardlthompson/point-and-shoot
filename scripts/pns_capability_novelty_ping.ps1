<#
.SYNOPSIS
  Detect newly discovered fleet capabilities/vendor keys and optionally post a webhook.
  FOSS-safe defaults: local report only, no network unless -WebhookUrl is provided.

.EXAMPLE
  .\scripts\pns_capability_novelty_ping.ps1
  .\scripts\pns_capability_novelty_ping.ps1 -UpdateBaseline
  .\scripts\pns_capability_novelty_ping.ps1 -WebhookUrl "https://example.com/pns-capability-ingest"
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [string]$BaselinePath = "",
    [switch]$UpdateBaseline,
    [switch]$SkipMatrixRefresh,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$SkipProbeExport,
    [string]$WebhookUrl = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_capability_novelty_ping.ps1

Detects newly observed capability IDs, feature-gate keys, and vendor-ish key names.
Writes local reports by default. Network egress is opt-in via -WebhookUrl.

Usage:
  .\scripts\pns_capability_novelty_ping.ps1
  .\scripts\pns_capability_novelty_ping.ps1 -UpdateBaseline
  .\scripts\pns_capability_novelty_ping.ps1 -WebhookUrl <https endpoint>
"@
    exit 0
}

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$matrixScan = Join-Path $PSScriptRoot "pns_fleet_matrix_scan.ps1"

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\capability_novelty_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not $BaselinePath) {
    $BaselinePath = Join-Path $projRoot "docs\FLEET_CAPABILITY_NOVELTY_BASELINE.json"
}
$discoveryLedgerPath = Join-Path $projRoot "docs\CAPABILITY_DISCOVERY_LEDGER.jsonl"

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") {
            return $t.Substring($eq + 1).Trim()
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) { $Serial = $fromEnv }
}

function Invoke-Adb([string[]]$Args) {
    if ($Serial) { & adb -s $Serial @Args } else { & adb @Args }
    if ($LASTEXITCODE -ne 0) { throw "adb $($Args -join ' ') failed exit=$LASTEXITCODE" }
}

function Test-AdbAuthorizedDevice {
    foreach ($line in @(adb devices 2>&1)) {
        if ($line -match '\tdevice$') { return $true }
    }
    return $false
}

function Add-Unique([System.Collections.Generic.HashSet[string]]$Set, [string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return }
    [void]$Set.Add($Value.Trim())
}

function Parse-MatrixNovelty([string]$MatrixPath) {
    $catalogSet = New-Object 'System.Collections.Generic.HashSet[string]'
    $featureGateSet = New-Object 'System.Collections.Generic.HashSet[string]'
    $cameraIdSet = New-Object 'System.Collections.Generic.HashSet[string]'
    $scanMeta = $null
    if (-not (Test-Path -LiteralPath $MatrixPath)) {
        return [ordered]@{
            catalogIds = @()
            featureGateKeys = @()
            cameraIds = @()
            scanMeta = $null
        }
    }
    $obj = Get-Content -LiteralPath $MatrixPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($obj.PSObject.Properties.Name -contains "scanMeta") { $scanMeta = $obj.scanMeta }

    foreach ($row in @($obj.capabilityCatalog)) {
        if ($null -ne $row.id) { Add-Unique $catalogSet "$($row.id)" }
    }
    foreach ($cam in @($obj.cameras)) {
        if ($null -ne $cam.cameraId) { Add-Unique $cameraIdSet "$($cam.cameraId)" }
        if ($cam.PSObject.Properties.Name -contains "featureGates" -and $null -ne $cam.featureGates) {
            foreach ($p in $cam.featureGates.PSObject.Properties) {
                Add-Unique $featureGateSet "$($p.Name)"
            }
        }
    }
    return [ordered]@{
        catalogIds = @($catalogSet | Sort-Object)
        featureGateKeys = @($featureGateSet | Sort-Object)
        cameraIds = @($cameraIdSet | Sort-Object)
        scanMeta = $scanMeta
    }
}

function Parse-VendorKeysFromProbe([string]$ProbePath) {
    $set = New-Object 'System.Collections.Generic.HashSet[string]'
    if (-not (Test-Path -LiteralPath $ProbePath)) { return @() }
    $text = Get-Content -LiteralPath $ProbePath -Raw -Encoding UTF8
    $rx = [regex]'(?im)\b(?:vendor|qti|qcom|oplus|oneplus|oppo|mediatek|mtk|exynos|sony|samsung|xiaomi|com|org)\.[A-Za-z0-9_.-]+\b'
    foreach ($m in $rx.Matches($text)) {
        Add-Unique $set $m.Value
    }
    return @($set | Sort-Object)
}

function Read-Baseline([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return [ordered]@{
            schema = "pns.capability_novelty_baseline.v1"
            updatedAtUtc = $null
            catalogIds = @()
            featureGateKeys = @()
            vendorKeyNames = @()
            cameraIds = @()
        }
    }
    return Get-Content -LiteralPath $Path -Raw -Encoding UTF8 | ConvertFrom-Json
}

function New-Delta([object[]]$Current, [object[]]$Baseline) {
    $baseSet = New-Object 'System.Collections.Generic.HashSet[string]'
    foreach ($x in @($Baseline)) { Add-Unique $baseSet "$x" }
    $out = New-Object System.Collections.Generic.List[string]
    foreach ($x in @($Current)) {
        $s = "$x"
        if (-not $baseSet.Contains($s)) { [void]$out.Add($s) }
    }
    return @($out | Sort-Object)
}

if (-not (Test-AdbAuthorizedDevice)) {
    $stub = [ordered]@{
        schema = "pns.capability_novelty_report.v1"
        pass = $false
        skippedReason = "no_authorized_device"
        timestampUtc = [DateTime]::UtcNow.ToString("o")
        outDir = $OutDir
    }
    $stub | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutDir "capability_novelty_report.json") -Encoding utf8
    Write-Warning "[cap_novelty] No authorized adb device."
    exit 1
}

$matrixDir = Join-Path $OutDir "matrix"
New-Item -ItemType Directory -Force -Path $matrixDir | Out-Null
$matrixPath = Join-Path $matrixDir "fleet_device_matrix.json"

if (-not $SkipMatrixRefresh) {
    if (-not (Test-Path -LiteralPath $matrixScan)) { throw "Missing $matrixScan" }
    $scanArgs = @{ OutDir = $matrixDir; ScanTier = "quick" }
    if ($Serial) { $scanArgs.Serial = $Serial }
    if ($SkipInstall) { $scanArgs.SkipInstall = $true }
    if ($AssembleDebug) { $scanArgs.AssembleDebug = $true }
    & $matrixScan @scanArgs
    if ($LASTEXITCODE -ne 0) { throw "pns_fleet_matrix_scan failed" }
}

if (-not (Test-Path -LiteralPath $matrixPath)) {
    Write-Host "[cap_novelty] matrix missing after scan; pulling run-as copy"
    Invoke-Adb @("exec-out", "run-as", $pkg, "cat", "files/fleet_device_matrix.json") | Set-Content -LiteralPath $matrixPath -Encoding utf8
}

$probePath = Join-Path $OutDir "PROBE_EXPORT_LATEST.md"
$probePulled = $false
if (-not $SkipProbeExport) {
    try {
        Invoke-Adb @("exec-out", "run-as", $pkg, "cat", "files/PROBE_EXPORT_LATEST.md") | Set-Content -LiteralPath $probePath -Encoding utf8
        $probePulled = (Test-Path -LiteralPath $probePath) -and ((Get-Item -LiteralPath $probePath).Length -gt 0)
    } catch {
        Write-Warning "[cap_novelty] probe export pull failed: $($_.Exception.Message)"
    }
}

$current = Parse-MatrixNovelty $matrixPath
$vendorKeys = Parse-VendorKeysFromProbe $probePath
$baseline = Read-Baseline $BaselinePath

$newCatalog = New-Delta $current.catalogIds $baseline.catalogIds
$newFeatureGates = New-Delta $current.featureGateKeys $baseline.featureGateKeys
$newVendor = New-Delta $vendorKeys $baseline.vendorKeyNames
$newCameraIds = New-Delta $current.cameraIds $baseline.cameraIds

$hasNew = ($newCatalog.Count + $newFeatureGates.Count + $newVendor.Count + $newCameraIds.Count) -gt 0

$report = [ordered]@{
    schema = "pns.capability_novelty_report.v1"
    pass = $true
    hasNewDiscoveries = $hasNew
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    serial = if ($Serial) { $Serial } else { "default" }
    matrixPath = $matrixPath
    probePath = if ($probePulled) { $probePath } else { $null }
    current = [ordered]@{
        catalogCount = @($current.catalogIds).Count
        featureGateCount = @($current.featureGateKeys).Count
        vendorKeyCount = @($vendorKeys).Count
        cameraIdCount = @($current.cameraIds).Count
        scanMeta = $current.scanMeta
    }
    newlyDiscovered = [ordered]@{
        catalogIds = $newCatalog
        featureGateKeys = $newFeatureGates
        vendorKeyNames = $newVendor
        cameraIds = $newCameraIds
    }
}

$jsonPath = Join-Path $OutDir "capability_novelty_report.json"
$report | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$md = @(
    "# Capability novelty report",
    "",
    "- **New discoveries:** $hasNew",
    "- **Catalog IDs:** +$($newCatalog.Count)",
    "- **Feature gates:** +$($newFeatureGates.Count)",
    "- **Vendor keys:** +$($newVendor.Count)",
    "- **Camera IDs:** +$($newCameraIds.Count)",
    ""
)
if ($newCatalog.Count -gt 0) {
    $md += "## New catalog IDs"
    $md += @($newCatalog | ForEach-Object { "- $_" })
    $md += ""
}
if ($newFeatureGates.Count -gt 0) {
    $md += "## New feature-gate keys"
    $md += @($newFeatureGates | ForEach-Object { "- $_" })
    $md += ""
}
if ($newVendor.Count -gt 0) {
    $md += "## New vendor-ish keys"
    $md += @($newVendor | ForEach-Object { "- $_" })
    $md += ""
}
if ($newCameraIds.Count -gt 0) {
    $md += "## New camera IDs"
    $md += @($newCameraIds | ForEach-Object { "- $_" })
    $md += ""
}
$mdPath = Join-Path $OutDir "capability_novelty_report.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

$ledgerAppended = $false
$ledgerEntryPath = $null
if ($hasNew) {
    $ledgerEntry = [ordered]@{
        schema = "pns.capability_discovery_ledger_entry.v1"
        loggedAtUtc = [DateTime]::UtcNow.ToString("o")
        sourceReport = $jsonPath
        serial = if ($Serial) { $Serial } else { "default" }
        appSurfaced = [ordered]@{
            catalogIds = $newCatalog
            featureGateKeys = $newFeatureGates
            cameraIds = $newCameraIds
        }
        needsBuildTriage = [ordered]@{
            vendorKeyNames = $newVendor
        }
    }
    if (-not (Test-Path -LiteralPath $discoveryLedgerPath)) {
        New-Item -ItemType File -Force -Path $discoveryLedgerPath | Out-Null
    }
    Add-Content -LiteralPath $discoveryLedgerPath -Value ($ledgerEntry | ConvertTo-Json -Depth 10 -Compress)
    $ledgerAppended = $true
    $ledgerEntryPath = $discoveryLedgerPath
}

if ($UpdateBaseline -or -not (Test-Path -LiteralPath $BaselinePath)) {
    $baselineOut = [ordered]@{
        schema = "pns.capability_novelty_baseline.v1"
        updatedAtUtc = [DateTime]::UtcNow.ToString("o")
        catalogIds = @($current.catalogIds)
        featureGateKeys = @($current.featureGateKeys)
        vendorKeyNames = @($vendorKeys)
        cameraIds = @($current.cameraIds)
    }
    $baselineOut | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $BaselinePath -Encoding utf8
    Write-Host "[cap_novelty] baseline updated -> $BaselinePath"
}

$webhookPosted = $false
$webhookError = $null
if (-not [string]::IsNullOrWhiteSpace($WebhookUrl) -and $hasNew) {
    try {
        $payload = [ordered]@{
            schema = "pns.capability_novelty_webhook.v1"
            generatedAtUtc = [DateTime]::UtcNow.ToString("o")
            hasNewDiscoveries = $hasNew
            newlyDiscovered = $report.newlyDiscovered
            scanMeta = $current.scanMeta
        }
        $body = $payload | ConvertTo-Json -Depth 10
        Invoke-RestMethod -Method Post -Uri $WebhookUrl -ContentType "application/json" -Body $body | Out-Null
        $webhookPosted = $true
        Write-Host "[cap_novelty] webhook posted -> $WebhookUrl"
    } catch {
        $webhookError = $_.Exception.Message
        Write-Warning "[cap_novelty] webhook post failed: $webhookError"
    }
} elseif (-not [string]::IsNullOrWhiteSpace($WebhookUrl) -and -not $hasNew) {
    Write-Host "[cap_novelty] no new discoveries; webhook skipped"
}

$final = [ordered]@{
    schema = "pns.capability_novelty_summary.v1"
    pass = $true
    hasNewDiscoveries = $hasNew
    reportPath = $jsonPath
    markdownPath = $mdPath
    baselinePath = $BaselinePath
    discoveryLedgerPath = $discoveryLedgerPath
    discoveryLedgerAppended = $ledgerAppended
    discoveryLedgerEntryPath = $ledgerEntryPath
    webhookPosted = $webhookPosted
    webhookError = $webhookError
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}
$final | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutDir "capability_novelty_summary.json") -Encoding utf8

Write-Host "[cap_novelty] done hasNew=$hasNew report=$jsonPath"
exit 0
