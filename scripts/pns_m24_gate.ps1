param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipInstall,
    [switch]$SkipMatrixRefresh,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_m24_gate.ps1 — Milestone 24 4K120 gate chain

HostOnly:
  1) pns_verify_toolchain.ps1 -RunTests

USB chain:
  1) pns_video_capability_probe.ps1
  2) pns_4k120_verify.ps1
  3) pns_4k120_endurance.ps1
  4) pns_fleet_parity_sweep.ps1 -Mode Full
  5) pns_verify_toolchain.ps1 -RunTests
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $repoRoot "hfr-runs\m24_gate_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$results = [ordered]@{
    schema = "pns.m24_gate.v2"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $outDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode, [string]$Note = "", [hashtable]$Extra = @{}) {
    $step = [ordered]@{
        name = $Name
        exitCode = $ExitCode
        pass = ($ExitCode -eq 0)
    }
    if ($Note) { $step.note = $Note }
    foreach ($k in $Extra.Keys) {
        $step[$k] = $Extra[$k]
    }
    $results.steps += $step
}

if ($HostOnly) {
    & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -RunTests
    Add-Step "toolchain_verify" $LASTEXITCODE
} else {
    & (Join-Path $PSScriptRoot "pns_usb_gate_mutex.ps1") -Serial $Serial -GateName "m24_gate"
    try {
    $capOutDir = Join-Path $outDir "video_capability_probe"
    $capArgs = @{ OutDir = $capOutDir }
    if ($Serial) { $capArgs.Serial = $Serial }
    if ($SkipInstall) { $capArgs.SkipInstall = $true }
    & (Join-Path $PSScriptRoot "pns_video_capability_probe.ps1") @capArgs
    $capExit = $LASTEXITCODE
    $capProbePath = Join-Path $capOutDir "probe.json"
    $capabilityClass = "unknown"
    if (Test-Path -LiteralPath $capProbePath) {
        try {
            $capObj = Get-Content -LiteralPath $capProbePath -Raw | ConvertFrom-Json
            if ($capObj.capabilityClass) { $capabilityClass = [string]$capObj.capabilityClass }
        } catch { }
    }
    Add-Step "video_capability_probe" $capExit "" @{
        artifactDir = $capOutDir
        probePath = $capProbePath
        capabilityClass = $capabilityClass
    }

    $strictOutDir = Join-Path $outDir "strict_4k120"
    $strictSummary = Join-Path $strictOutDir "strict_4k120_summary.json"
    $strictExit = 0
    $strictTruth = "not_run"
    if ($capabilityClass -eq "S0") {
        Add-Step "strict_4k120" 0 "skipped: capability class S0 (no 4k120 encoder path)" @{
            skipped = $true
            capabilityClass = $capabilityClass
            artifactDir = $strictOutDir
        }
    } else {
        $strictArgs = @{ OutDir = $strictOutDir }
        if ($Serial) { $strictArgs.Serial = $Serial }
        if ($SkipInstall) { $strictArgs.SkipInstall = $true; $strictArgs.SkipAssemble = $true }
        & (Join-Path $PSScriptRoot "pns_4k120_verify.ps1") @strictArgs
        $strictExit = $LASTEXITCODE
        if (Test-Path -LiteralPath $strictSummary) {
            try {
                $strictObj = Get-Content -LiteralPath $strictSummary -Raw | ConvertFrom-Json
                if ($strictObj.finalTruthClass) { $strictTruth = [string]$strictObj.finalTruthClass }
            } catch { }
        }
        $env:PNS_4K120_TRUTH_SUMMARY = $strictSummary
        Add-Step "strict_4k120" $strictExit "" @{
            capabilityClass = $capabilityClass
            truthClass = $strictTruth
            artifactDir = $strictOutDir
            summaryPath = $strictSummary
        }
    }

    if ($capabilityClass -in @("S0", "S1")) {
        Add-Step "endurance_4k120" 0 "skipped: capability class $capabilityClass" @{
            skipped = $true
            capabilityClass = $capabilityClass
            artifactDir = (Join-Path $outDir "endurance")
        }
    } else {
        $enduranceOutDir = Join-Path $outDir "endurance"
        $enduranceArgs = @{
            OutDir = $enduranceOutDir
            StartSec = 30
            StepSec = 15
            MaxSec = 180
        }
        if ($Serial) { $enduranceArgs.Serial = $Serial }
        if ($SkipInstall) { $enduranceArgs.SkipInstall = $true; $enduranceArgs.SkipAssemble = $true }
        & (Join-Path $PSScriptRoot "pns_4k120_endurance.ps1") @enduranceArgs
        Add-Step "endurance_4k120" $LASTEXITCODE "" @{
            capabilityClass = $capabilityClass
            artifactDir = $enduranceOutDir
            reportPath = (Join-Path $enduranceOutDir "endurance_report.json")
        }
    }

    $parityArgs = @{
        Mode = "Full"
        OutDir = (Join-Path $outDir "parity_full")
    }
    if ($Serial) { $parityArgs.Serial = $Serial }
    if ($SkipInstall) { $parityArgs.SkipInstall = $true }
    if ($SkipMatrixRefresh) { $parityArgs.SkipMatrixRefresh = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") @parityArgs
    Add-Step "parity_full" $LASTEXITCODE "" @{
        capabilityClass = $capabilityClass
        artifactDir = (Join-Path $outDir "parity_full")
    }

    & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -RunTests
    Add-Step "toolchain_verify" $LASTEXITCODE
    } finally {
        & (Join-Path $PSScriptRoot "pns_usb_gate_mutex.ps1") -Serial $Serial -GateName "m24_gate" -Release
    }
}

if (-not $HostOnly) {
    if ($Serial) { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null }
    else { & adb shell am force-stop dev.pointandshoot 2>$null }
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$report = Join-Path $outDir "m24_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $report -Encoding utf8
Write-Host "[m24_gate] pass=$($results.pass) -> $report"
if (-not $results.pass) { exit 1 }
exit 0
