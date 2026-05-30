# Milestone 18 — one-shot gate: host catalog + toolchain tests + tiered regression pack.
#
# USB steps run when an authorized device is online; otherwise host-only steps still execute.

param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_m18_gate.ps1 — Milestone 18 gate

  Host: pns_capability_catalog_gate.ps1 + pns_verify_toolchain.ps1 -RunTests
  USB (when device online): pns_fleet_regression_pack.ps1 -Tier all

  -HostOnly   skip USB regression pack even if device present
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$OutDir = Join-Path $projRoot "hfr-runs\m18_gate_$utc"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$results = [ordered]@{
    schema = "pns.m18_gate.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode) {
    $results.steps += [ordered]@{ name = $Name; exitCode = $ExitCode; pass = ($ExitCode -eq 0) }
}

& (Join-Path $PSScriptRoot "pns_capability_catalog_gate.ps1") -HostOnly
Add-Step "catalog_gate" $LASTEXITCODE

& (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -RunTests
Add-Step "toolchain_run_tests" $LASTEXITCODE

if (-not $HostOnly) {
    $common = @("-Tier", "all")
    if ($Serial) { $common += @("-Serial", $Serial) }
    if ($SkipInstall) { $common += "-SkipInstall" }
    if ($AssembleDebug) { $common += "-AssembleDebug" }
    & (Join-Path $PSScriptRoot "pns_fleet_regression_pack.ps1") @common
    Add-Step "fleet_regression_pack_all" $LASTEXITCODE
} else {
    $results.steps += [ordered]@{ name = "fleet_regression_pack_all"; exitCode = 0; pass = $true; skipped = "HostOnly" }
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$reportPath = Join-Path $OutDir "m18_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "[m18_gate] pass=$($results.pass) -> $reportPath"
if (-not $results.pass) { exit 1 }
exit 0
