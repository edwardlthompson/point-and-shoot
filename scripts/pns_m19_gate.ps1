# Milestone 19 — one-shot gate: M19 JVM tests + catalog + fleet regression tier 2.
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
pns_m19_gate.ps1 — Milestone 19 gate

  Host: M19 JVM tests + pns_capability_catalog_gate.ps1
  USB (when device online): pns_fleet_regression_pack.ps1 -Tier 2

  -HostOnly   skip USB regression pack even if device present
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$OutDir = Join-Path $projRoot "hfr-runs\m19_gate_$utc"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$results = [ordered]@{
    schema = "pns.m19_gate.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode) {
    $results.steps += [ordered]@{ name = $Name; exitCode = $ExitCode; pass = ($ExitCode -eq 0) }
}

$m19Tests = @(
    "ColorQualityIndexTest",
    "DualIsoVideoMergerTest",
    "ProResProbeTest",
    "AnamorphicVideoMetadataTest"
)
foreach ($t in $m19Tests) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:testDebugUnitTest" "--tests" "dev.pointandshoot.$t"
    Add-Step "unit_$t" $LASTEXITCODE
}

& (Join-Path $PSScriptRoot "pns_capability_catalog_gate.ps1") -HostOnly
Add-Step "catalog_gate" $LASTEXITCODE

if (-not $HostOnly) {
    $common = @("-Tier", "2")
    if ($Serial) { $common += @("-Serial", $Serial) }
    if ($SkipInstall) { $common += "-SkipInstall" }
    if ($AssembleDebug) { $common += "-AssembleDebug" }
    & (Join-Path $PSScriptRoot "pns_fleet_regression_pack.ps1") @common
    Add-Step "fleet_regression_pack_tier2" $LASTEXITCODE
} else {
    $results.steps += [ordered]@{ name = "fleet_regression_pack_tier2"; exitCode = 0; pass = $true; skipped = "HostOnly" }
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$reportPath = Join-Path $OutDir "m19_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "[m19_gate] pass=$($results.pass) -> $reportPath"
if (-not $results.pass) { exit 1 }
exit 0
