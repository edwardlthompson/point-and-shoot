# Milestone 21 — one-shot gate: parity honesty JVM tests + catalog gate + golden sweep + USB Quick/Full.
#
# USB steps run when an authorized device is online; otherwise host-only steps still execute.

param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipInstall,
    [switch]$SkipMatrixRefresh,
    [switch]$AssembleDebug,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_m21_gate.ps1 — Milestone 21 gate

  Host:
    Fleet parity JVM tests (Sweep, LogcatParser, ChromeLint, GoldenSweep, DeliveryProbe)
    pns_capability_catalog_gate.ps1
    pns_fleet_parity_sweep.ps1 -HostOnlyFixture

  USB (when device online):
    pns_fleet_parity_sweep.ps1 -Mode Quick [-SkipMatrixRefresh]
    pns_fleet_parity_sweep.ps1 -Mode Full (fresh install)

  -HostOnly   skip USB steps even if device present
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$OutDir = Join-Path $projRoot "hfr-runs\m21_gate_$utc"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$results = [ordered]@{
    schema = "pns.m21_gate.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode, [string]$Skipped = "") {
    $step = [ordered]@{ name = $Name; exitCode = $ExitCode; pass = ($ExitCode -eq 0) }
    if ($Skipped) { $step.skipped = $Skipped }
    $results.steps += $step
}

$m21Tests = @(
    "FleetParitySweepTest",
    "FleetParityLogcatParserTest",
    "FleetParityChromeLintTest",
    "FleetParityGoldenSweepTest",
    "FleetDeliveryProbeTest",
    "FleetDeviceMatrixTest",
    "FleetDeviceMatrixGoldenTest",
    "FleetParityEncoderCrossCheckTest"
)
foreach ($t in $m21Tests) {
    $pkg = "dev.pointandshoot.fleet.$t"
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:testDebugUnitTest" "--tests" $pkg 2>&1 | Out-Null
    Add-Step "unit_$t" $LASTEXITCODE
}

& (Join-Path $PSScriptRoot "pns_capability_catalog_gate.ps1") -HostOnly
Add-Step "catalog_gate" $LASTEXITCODE

& (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") -HostOnlyFixture
Add-Step "parity_logcat_fixture" $LASTEXITCODE

if (-not $HostOnly) {
    $resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
    if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }

    if ($AssembleDebug) {
        & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
        Add-Step "assemble_debug" $LASTEXITCODE
    }

    $quickArgs = @{
        Mode = "Quick"
        OutDir = (Join-Path $OutDir "parity_quick")
    }
    if ($Serial) { $quickArgs.Serial = $Serial }
    if ($SkipInstall) { $quickArgs.SkipInstall = $true }
    if ($SkipMatrixRefresh) { $quickArgs.SkipMatrixRefresh = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") @quickArgs
    Add-Step "parity_quick_usb" $LASTEXITCODE

    $fullArgs = @{
        Mode = "Full"
        OutDir = (Join-Path $OutDir "parity_full")
    }
    if ($Serial) { $fullArgs.Serial = $Serial }
    if ($SkipMatrixRefresh) { $fullArgs.SkipMatrixRefresh = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") @fullArgs
    Add-Step "parity_full_usb" $LASTEXITCODE

    $recordArgs = @{
        Mode = "Full"
        IncludeRecord = $true
        OutDir = (Join-Path $OutDir "parity_full_record")
    }
    if ($Serial) { $recordArgs.Serial = $Serial }
    if ($SkipInstall) { $recordArgs.SkipInstall = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") @recordArgs
    Add-Step "parity_full_include_record" $LASTEXITCODE

    if ($Serial) {
        & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null
    } else {
        & adb shell am force-stop dev.pointandshoot 2>$null
    }
} else {
    foreach ($name in @("parity_quick_usb", "parity_full_usb")) {
        $results.steps += [ordered]@{ name = $name; exitCode = 0; pass = $true; skipped = "HostOnly" }
    }
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$reportPath = Join-Path $OutDir "m21_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "[m21_gate] pass=$($results.pass) -> $reportPath"
if (-not $results.pass) { exit 1 }
exit 0
