<#
.SYNOPSIS
  Task Scheduler-friendly monthly wrapper for capability novelty + upstream Camera2 ping.

.DESCRIPTION
  Runs:
    1) pns_capability_novelty_ping.ps1 (device-aware)
    2) pns_camera2_upstream_ping.ps1 (host-only)

  Then writes one consolidated report:
    - monthly_capability_report.json
    - monthly_capability_report.md

  No network egress by default. Webhook posting remains opt-in and is only forwarded to
  pns_capability_novelty_ping.ps1 when -WebhookUrl is provided.

.EXAMPLE
  .\scripts\pns_monthly_capability_report.ps1

.EXAMPLE
  .\scripts\pns_monthly_capability_report.ps1 -WebhookUrl "https://example.com/pns/ingest"

.EXAMPLE
  .\scripts\pns_monthly_capability_report.ps1 -UpdateBaselines
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [string]$CapabilityBaselinePath = "",
    [string]$UpstreamBaselinePath = "",
    [switch]$UpdateBaselines,
    [string]$WebhookUrl = "",
    [switch]$SkipMatrixRefresh,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$SkipProbeExport,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_monthly_capability_report.ps1

Runs both monthly novelty checks and drops one consolidated report.

Usage:
  .\scripts\pns_monthly_capability_report.ps1
  .\scripts\pns_monthly_capability_report.ps1 -WebhookUrl <https endpoint>
  .\scripts\pns_monthly_capability_report.ps1 -UpdateBaselines
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\monthly_capability_report_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not $CapabilityBaselinePath) {
    $CapabilityBaselinePath = Join-Path $projRoot "docs\FLEET_CAPABILITY_NOVELTY_BASELINE.json"
}
if (-not $UpstreamBaselinePath) {
    $UpstreamBaselinePath = Join-Path $projRoot "docs\CAMERA2_UPSTREAM_BASELINE.json"
}

$capScript = Join-Path $PSScriptRoot "pns_capability_novelty_ping.ps1"
$upstreamScript = Join-Path $PSScriptRoot "pns_camera2_upstream_ping.ps1"
if (-not (Test-Path -LiteralPath $capScript)) { throw "Missing script: $capScript" }
if (-not (Test-Path -LiteralPath $upstreamScript)) { throw "Missing script: $upstreamScript" }

$capOutDir = Join-Path $OutDir "capability_novelty"
$upstreamOutDir = Join-Path $OutDir "camera2_upstream"
New-Item -ItemType Directory -Force -Path $capOutDir | Out-Null
New-Item -ItemType Directory -Force -Path $upstreamOutDir | Out-Null

function Invoke-StepSafely(
    [string]$Name,
    [string]$ScriptPath,
    [hashtable]$Args
) {
    $status = [ordered]@{
        name = $Name
        success = $false
        exitCode = $null
        error = $null
    }
    try {
        & $ScriptPath @Args
        $status.exitCode = $LASTEXITCODE
        $status.success = ($LASTEXITCODE -eq 0)
    } catch {
        $status.exitCode = if ($null -ne $LASTEXITCODE) { $LASTEXITCODE } else { 1 }
        $status.error = $_.Exception.Message
        $status.success = $false
    }
    return $status
}

$capArgs = @{
    OutDir = $capOutDir
    BaselinePath = $CapabilityBaselinePath
}
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $capArgs.Serial = $Serial }
if ($UpdateBaselines) { $capArgs.UpdateBaseline = $true }
if ($SkipMatrixRefresh) { $capArgs.SkipMatrixRefresh = $true }
if ($SkipInstall) { $capArgs.SkipInstall = $true }
if ($AssembleDebug) { $capArgs.AssembleDebug = $true }
if ($SkipProbeExport) { $capArgs.SkipProbeExport = $true }
if (-not [string]::IsNullOrWhiteSpace($WebhookUrl)) { $capArgs.WebhookUrl = $WebhookUrl }

$upstreamArgs = @{
    OutDir = $upstreamOutDir
    BaselinePath = $UpstreamBaselinePath
}
if ($UpdateBaselines) { $upstreamArgs.UpdateBaseline = $true }

$capStep = Invoke-StepSafely -Name "capability_novelty" -ScriptPath $capScript -Args $capArgs
$upstreamStep = Invoke-StepSafely -Name "camera2_upstream" -ScriptPath $upstreamScript -Args $upstreamArgs

$capSummaryPath = Join-Path $capOutDir "capability_novelty_summary.json"
$capReportPath = Join-Path $capOutDir "capability_novelty_report.json"
$upstreamReportPath = Join-Path $upstreamOutDir "camera2_upstream_ping_report.json"

$capSummary = $null
$capReport = $null
$upstreamReport = $null

if (Test-Path -LiteralPath $capSummaryPath) {
    $capSummary = Get-Content -LiteralPath $capSummaryPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
if (Test-Path -LiteralPath $capReportPath) {
    $capReport = Get-Content -LiteralPath $capReportPath -Raw -Encoding UTF8 | ConvertFrom-Json
}
if (Test-Path -LiteralPath $upstreamReportPath) {
    $upstreamReport = Get-Content -LiteralPath $upstreamReportPath -Raw -Encoding UTF8 | ConvertFrom-Json
}

$overallPass = $capStep.success -and $upstreamStep.success

$consolidated = [ordered]@{
    schema = "pns.monthly_capability_report.v1"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    pass = $overallPass
    outDir = $OutDir
    steps = @($capStep, $upstreamStep)
    capabilityNovelty = [ordered]@{
        summaryPath = if (Test-Path -LiteralPath $capSummaryPath) { $capSummaryPath } else { $null }
        reportPath = if (Test-Path -LiteralPath $capReportPath) { $capReportPath } else { $null }
        hasNewDiscoveries = if ($null -ne $capSummary) { [bool]$capSummary.hasNewDiscoveries } else { $null }
        webhookPosted = if ($null -ne $capSummary) { [bool]$capSummary.webhookPosted } else { $null }
        discoveryLedgerPath = if ($null -ne $capSummary) { $capSummary.discoveryLedgerPath } else { $null }
        discoveryLedgerAppended = if ($null -ne $capSummary) { [bool]$capSummary.discoveryLedgerAppended } else { $null }
    }
    camera2Upstream = [ordered]@{
        reportPath = if (Test-Path -LiteralPath $upstreamReportPath) { $upstreamReportPath } else { $null }
        changedCount = if ($null -ne $upstreamReport) { [int]$upstreamReport.changedCount } else { $null }
        fetchErrorCount = if ($null -ne $upstreamReport) { [int]$upstreamReport.fetchErrorCount } else { $null }
    }
    effectiveOptions = [ordered]@{
        serial = if (-not [string]::IsNullOrWhiteSpace($Serial)) { $Serial } else { "auto" }
        updateBaselines = [bool]$UpdateBaselines
        webhookEnabled = -not [string]::IsNullOrWhiteSpace($WebhookUrl)
        skipMatrixRefresh = [bool]$SkipMatrixRefresh
        skipInstall = [bool]$SkipInstall
        assembleDebug = [bool]$AssembleDebug
        skipProbeExport = [bool]$SkipProbeExport
    }
}

$jsonOut = Join-Path $OutDir "monthly_capability_report.json"
$consolidated | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonOut -Encoding utf8

$md = @(
    "# Monthly capability report",
    "",
    "- **Pass:** $overallPass",
    "- **Generated (UTC):** $($consolidated.generatedAtUtc)",
    "- **Capability novelty step:** success=$($capStep.success) exit=$($capStep.exitCode)",
    "- **Upstream ping step:** success=$($upstreamStep.success) exit=$($upstreamStep.exitCode)",
    "",
    "## Capability novelty",
    "- **Has new discoveries:** $($consolidated.capabilityNovelty.hasNewDiscoveries)",
    "- **Webhook posted:** $($consolidated.capabilityNovelty.webhookPosted)",
    "- **Discovery ledger appended:** $($consolidated.capabilityNovelty.discoveryLedgerAppended)",
    "- **Discovery ledger path:** $($consolidated.capabilityNovelty.discoveryLedgerPath)",
    "- **Report:** $($consolidated.capabilityNovelty.reportPath)",
    "",
    "## Camera2 upstream",
    "- **Changed count:** $($consolidated.camera2Upstream.changedCount)",
    "- **Fetch errors:** $($consolidated.camera2Upstream.fetchErrorCount)",
    "- **Report:** $($consolidated.camera2Upstream.reportPath)",
    ""
)
if (-not [string]::IsNullOrWhiteSpace($capStep.error)) {
    $md += "## Capability novelty error"
    $md += "- $($capStep.error)"
    $md += ""
}
if (-not [string]::IsNullOrWhiteSpace($upstreamStep.error)) {
    $md += "## Upstream ping error"
    $md += "- $($upstreamStep.error)"
    $md += ""
}
$mdOut = Join-Path $OutDir "monthly_capability_report.md"
$md | Set-Content -LiteralPath $mdOut -Encoding utf8

Write-Host "[monthly_cap] done pass=$overallPass report=$jsonOut"
if ($overallPass) {
    exit 0
}
exit 1

