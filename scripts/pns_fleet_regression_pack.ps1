# Milestone 18.4/18.5 — tiered fleet regression pack (matrix + parity Delta).

param(
    [string]$Serial = "",
    [ValidateSet("1", "2", "all")]
    [string]$Tier = "all",
    [ValidateSet("Delta", "Full")]
    [string]$ParityMode = "Delta",
    [switch]$IncludeProofPack,
    [switch]$SkipInstall,
    [switch]$AssembleDebug
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$OutDir = Join-Path $projRoot "hfr-runs\fleet_regression_pack_$utc"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$results = [ordered]@{
    schema = "pns.fleet_regression_pack.v1"
    tier = $Tier
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode) {
    $results.steps += [ordered]@{ name = $Name; exitCode = $ExitCode; pass = ($ExitCode -eq 0) }
}

function New-UsbArgs {
    $args = @{}
    if ($Serial) { $args.Serial = $Serial }
    if ($SkipInstall) { $args.SkipInstall = $true }
    if ($AssembleDebug) { $args.AssembleDebug = $true }
    return $args
}

if ($Tier -eq "1" -or $Tier -eq "all") {
    $matrixOut = Join-Path $OutDir "tier1_matrix"
    $matrixArgs = New-UsbArgs
    $matrixArgs.OutDir = $matrixOut
    $matrixArgs.ScanTier = "full"
    & (Join-Path $PSScriptRoot "pns_fleet_matrix_scan.ps1") @matrixArgs
    Add-Step "tier1_matrix_scan" $LASTEXITCODE
}

if ($Tier -eq "2" -or $Tier -eq "all") {
    $parityOut = Join-Path $OutDir "tier2_parity"
    $parityArgs = New-UsbArgs
    $parityArgs.OutDir = $parityOut
    $parityArgs.Mode = $ParityMode
    if ($IncludeProofPack) { $parityArgs.IncludeProofPack = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") @parityArgs
    Add-Step "tier2_parity_delta" $LASTEXITCODE
}

if ($Tier -eq "all") {
    & (Join-Path $PSScriptRoot "pns_capability_catalog_gate.ps1") -HostOnly
    Add-Step "catalog_gate" $LASTEXITCODE
}

if ($Tier -eq "2" -or $Tier -eq "all") {
    & (Join-Path $PSScriptRoot "pns_parity_debt_ledger_refresh.ps1") -RunsRoot (Join-Path $projRoot "hfr-runs")
    & (Join-Path $PSScriptRoot "pns_parity_build_plan_intake.ps1")
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$reportPath = Join-Path $OutDir "fleet_regression_pack.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

$historyPath = Join-Path $projRoot "docs\FLEET_REGRESSION_PACK_HISTORY.jsonl"
Add-Content -LiteralPath $historyPath -Value ($results | ConvertTo-Json -Compress -Depth 6)

Write-Host "[fleet_regression_pack] pass=$($results.pass) -> $reportPath"
if (-not $results.pass) { exit 1 }
exit 0
