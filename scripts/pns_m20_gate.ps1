# Milestone 20 — one-shot gate: M20 JVM tests + dual record + catalog + fleet regression tier 2.
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
pns_m20_gate.ps1 — Milestone 20 gate

  Host: M20 JVM tests + pns_capability_catalog_gate.ps1
  USB (when device online):
    pns_dual_video_verify.ps1 -RecordSec 5
    pns_pip_preview_verify.ps1
    pns_multicam_melt_verify.ps1
    pns_fleet_regression_pack.ps1 -Tier 2

  -HostOnly   skip USB steps even if device present
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$OutDir = Join-Path $projRoot "hfr-runs\m20_gate_$utc"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$results = [ordered]@{
    schema = "pns.m20_gate.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode) {
    $results.steps += [ordered]@{ name = $Name; exitCode = $ExitCode; pass = ($ExitCode -eq 0) }
}

$m20Tests = @(
    "DualVideoRecordingControllerTest",
    "DualVideoHalConcurrencyTest",
    "MulticamMeltThermalPolicyTest",
    "MulticamMeltRecordingControllerTest",
    "DeviceFeatureGatesTest"
)
foreach ($t in $m20Tests) {
    $pkg = if ($t -eq "DeviceFeatureGatesTest") { "dev.pointandshoot.fleet.$t" } else { "dev.pointandshoot.$t" }
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:testDebugUnitTest" "--tests" $pkg
    Add-Step "unit_$t" $LASTEXITCODE
}

& (Join-Path $PSScriptRoot "pns_capability_catalog_gate.ps1") -HostOnly
Add-Step "catalog_gate" $LASTEXITCODE

if (-not $HostOnly) {
    & adb $(if ($Serial) { @("-s", $Serial) }) shell am force-stop dev.pointandshoot 2>$null
    Start-Sleep -Seconds 2

    $dualArgs = @{ RecordSec = 5 }
    if ($Serial) { $dualArgs.Serial = $Serial }
    if ($SkipInstall) { $dualArgs.SkipGradle = $true }
    & (Join-Path $PSScriptRoot "pns_dual_video_verify.ps1") @dualArgs
    Add-Step "dual_video_record_5s" $LASTEXITCODE

    $usbCommon = @{}
    if ($Serial) { $usbCommon.Serial = $Serial }
    if ($SkipInstall) { $usbCommon.SkipInstall = $true }
    if ($AssembleDebug) { $usbCommon.AssembleDebug = $true }

    & (Join-Path $PSScriptRoot "pns_pip_preview_verify.ps1") @usbCommon
    Add-Step "pip_preview_verify" $LASTEXITCODE

    & (Join-Path $PSScriptRoot "pns_multicam_melt_verify.ps1") @usbCommon
    Add-Step "multicam_melt_verify" $LASTEXITCODE

    $regArgs = @("-Tier", "2")
    if ($Serial) { $regArgs += @("-Serial", $Serial) }
    if ($SkipInstall) { $regArgs += "-SkipInstall" }
    if ($AssembleDebug) { $regArgs += "-AssembleDebug" }
    & (Join-Path $PSScriptRoot "pns_fleet_regression_pack.ps1") @regArgs
    Add-Step "fleet_regression_pack_tier2" $LASTEXITCODE

    & adb $(if ($Serial) { @("-s", $Serial) }) shell am force-stop dev.pointandshoot 2>$null
} else {
    foreach ($name in @("dual_video_record_5s", "pip_preview_verify", "multicam_melt_verify", "fleet_regression_pack_tier2")) {
        $results.steps += [ordered]@{ name = $name; exitCode = 0; pass = $true; skipped = "HostOnly" }
    }
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$reportPath = Join-Path $OutDir "m20_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "[m20_gate] pass=$($results.pass) -> $reportPath"
if (-not $results.pass) { exit 1 }
exit 0
