param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipInstall,
    [switch]$SkipMatrixRefresh,
    [switch]$SkipCloseoutApply,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_m23_gate.ps1 — Milestone 23 closeout chain + automated archive cleanup

USB chain (default):
  1) pns_fleet_matrix_scan.ps1 -ScanTier quick
  2) pns_capture_pipeline_verify.ps1
  3) pns_chrome_ux_gate.ps1 -FocalMmSlot 150
  4) pns_fleet_parity_sweep.ps1 -Mode Quick
  5) pns_verify_toolchain.ps1 -RunTests
  6) pns_milestone_closeout.ps1 -ConfigPath scripts/milestones/m23.closeout.json [-Apply]

HostOnly:
  - pns_verify_toolchain.ps1 -RunTests
  - pns_milestone_closeout.ps1 dry-run only
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $repoRoot "hfr-runs\m23_gate_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$results = [ordered]@{
    schema = "pns.m23_gate.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $outDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode, [string]$Note = "") {
    $step = [ordered]@{
        name = $Name
        exitCode = $ExitCode
        pass = ($ExitCode -eq 0)
    }
    if ($Note) { $step.note = $Note }
    $results.steps += $step
}

if ($HostOnly) {
    & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -RunTests
    Add-Step "toolchain_verify" $LASTEXITCODE
} else {
    $matrixArgs = @{
        ScanTier = "quick"
        OutDir = (Join-Path $outDir "matrix_quick")
    }
    if ($Serial) { $matrixArgs.Serial = $Serial }
    if ($SkipInstall) { $matrixArgs.SkipInstall = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_matrix_scan.ps1") @matrixArgs
    Add-Step "matrix_quick" $LASTEXITCODE

    $captureArgs = @{
        OutDir = (Join-Path $outDir "capture_pipeline")
    }
    if ($Serial) { $captureArgs.Serial = $Serial }
    & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1") @captureArgs
    Add-Step "capture_pipeline" $LASTEXITCODE

    $chromeArgs = @{
        FocalMmSlot = 150
        OutDir = (Join-Path $outDir "chrome_150")
        SkipHost = $true
        SkipGradle = $true
    }
    if ($Serial) { $chromeArgs.Serial = $Serial }
    & (Join-Path $PSScriptRoot "pns_chrome_ux_gate.ps1") @chromeArgs
    Add-Step "chrome_150" $LASTEXITCODE

    $parityArgs = @{
        Mode = "Quick"
        OutDir = (Join-Path $outDir "parity_quick")
    }
    if ($Serial) { $parityArgs.Serial = $Serial }
    if ($SkipInstall) { $parityArgs.SkipInstall = $true }
    if ($SkipMatrixRefresh) { $parityArgs.SkipMatrixRefresh = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") @parityArgs
    Add-Step "parity_quick" $LASTEXITCODE

    & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -RunTests
    Add-Step "toolchain_verify" $LASTEXITCODE
}

$closeoutArgs = @{
    ConfigPath = (Join-Path $PSScriptRoot "milestones\m23.closeout.json")
}
if (-not $SkipCloseoutApply -and -not $HostOnly) {
    $closeoutArgs.Apply = $true
}
& (Join-Path $PSScriptRoot "pns_milestone_closeout.ps1") @closeoutArgs
$closeoutMode = if ($closeoutArgs.ContainsKey("Apply")) { "apply" } else { "dry-run" }
Add-Step "milestone_closeout" $LASTEXITCODE $closeoutMode

if (-not $HostOnly) {
    if ($Serial) {
        & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null
    } else {
        & adb shell am force-stop dev.pointandshoot 2>$null
    }
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$report = Join-Path $outDir "m23_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $report -Encoding utf8
Write-Host "[m23_gate] pass=$($results.pass) -> $report"
if (-not $results.pass) { exit 1 }
exit 0
