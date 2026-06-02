<#
.SYNOPSIS
  Milestone 13.8d — still-mode benchmark (Standard / ZSL / HDR) + pipeline regression + optional ReferenceCam session.

.DESCRIPTION
  1. pns_capture_pipeline_verify.ps1 -Fast with pns_preview_still_mode=standard
  2. pns_still_mode_benchmark.ps1 -Mode all
  3. Optional: pns_dng_proshot_pns_session.ps1 -PnsStillModes standard,zsl,hdr
  4. Writes m13_8d_gate.json + STILL_MODE_COMPARE.md (human ACR checklist)

.EXAMPLE
  .\scripts\pns_m13_8d_gate.ps1 -Serial <serial>
  .\scripts\pns_m13_8d_gate.ps1 -SkipProshotSession
#>
param(
    [string]$Serial = "",
    [string]$Dir = "",
    [switch]$SkipPipelineVerify,
    [switch]$SkipBenchmark,
    [switch]$SkipProshotSession,
    [switch]$PullMotionCamReference,
    [switch]$RecordHumanPass,
    [string]$ColorNote = "",
    [string]$Notes = "Daylight 13.8d — Standard vs ZSL vs HDR vs ReferenceCam (subjective color in ACR)."
)

if ($RecordHumanPass) {
    if ([string]::IsNullOrWhiteSpace($Dir)) { throw "-Dir required with -RecordHumanPass" }
    $sign = @{
        schema = "still_mode_compare_human.v1"
        timestampUtc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
        artifactDir = (Resolve-Path -LiteralPath $Dir).Path
        colorNote = $ColorNote
        acceptable = $true
    }
    $sign | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $Dir "still_mode_human_signoff.json") -Encoding UTF8
    Write-Host "Wrote $(Join-Path $Dir 'still_mode_human_signoff.json')" -ForegroundColor Green
    exit 0
}

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }

$projRoot = Split-Path -Parent $PSScriptRoot
$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$gateDir = Join-Path $projRoot "hfr-runs\m13_8d_gate_$ts"
New-Item -ItemType Directory -Force -Path $gateDir | Out-Null

$result = [ordered]@{
    schema = "m13_8d_gate.v1"
    timestampUtc = $ts
    gateDir = $gateDir
    serial = $Serial
    pipelineVerifyPass = $null
    benchmarkDir = $null
    benchmarkPass = $null
    proshotSessionDir = $null
    notes = $Notes
}

Write-Host "=== M13.8d still-mode gate ($ts) ===" -ForegroundColor Cyan
Write-Host "Artifacts: $gateDir"

if (-not $SkipPipelineVerify) {
    Write-Host "[13.8d] pipeline verify (stillMode=standard)..." -ForegroundColor Cyan
    $pvArgs = @{
        Fast = $true
        MaxAttempts = 2
        NoHistoryAppend = $true
        PreviewStillMode = "standard"
    }
    if ($Serial) { $pvArgs["Serial"] = $Serial }
    & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1") @pvArgs
    $result.pipelineVerifyPass = ($LASTEXITCODE -eq 0)
    if (-not $result.pipelineVerifyPass) {
        $result | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $gateDir "m13_8d_gate.json") -Encoding UTF8
        throw "pipeline verify FAIL (standard mode)"
    }
}

if (-not $SkipBenchmark) {
    Write-Host "[13.8d] still mode benchmark (all)..." -ForegroundColor Cyan
    $benchDir = Join-Path $gateDir "still_mode_bench"
    $benchArgs = @{
        Mode = "all"
        Repeats = 1
        OutDir = $benchDir
    }
    if ($Serial) { $benchArgs["Serial"] = $Serial }
    & (Join-Path $PSScriptRoot "pns_still_mode_benchmark.ps1") @benchArgs
    $result.benchmarkDir = $benchDir
    $benchJson = Join-Path $benchDir "results.json"
    $benchPass = $true
    if (Test-Path $benchJson) {
        $bj = Get-Content $benchJson -Raw | ConvertFrom-Json
        foreach ($mode in @("standard", "zsl", "hdr")) {
            $runs = $bj.byMode.$mode
            if (-not $runs) { $benchPass = $false; continue }
            foreach ($run in $runs) {
                if (-not $run.capturePass) { $benchPass = $false }
            }
        }
    } else {
        $benchPass = $false
    }
    $result.benchmarkPass = $benchPass
    if (-not $benchPass) {
        $result | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $gateDir "m13_8d_gate.json") -Encoding UTF8
        throw "still mode benchmark FAIL"
    }
}

if (-not $SkipProshotSession) {
    Write-Host "[13.8d] ReferenceCam + three-way P&S session..." -ForegroundColor Cyan
    $sessArgs = @{
        PnsStillModes = "standard,zsl,hdr"
        Notes = $Notes
        SkipProShotPull = $false
    }
    if ($Serial) { $sessArgs["Serial"] = $Serial }
    if ($PullMotionCamReference) { $sessArgs["PullMotionCamReference"] = $true }
    & (Join-Path $PSScriptRoot "pns_dng_proshot_pns_session.ps1") @sessArgs
    if ($LASTEXITCODE -ne 0) { Write-Warning "[13.8d] referencecam session exit=$LASTEXITCODE" }
    $sessLatest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "dng_proshot_pns_session_*" |
        Sort-Object Name | Select-Object -Last 1
    if ($sessLatest) { $result.proshotSessionDir = $sessLatest.FullName }
}

$humanPath = Join-Path $gateDir "STILL_MODE_COMPARE.md"
$human = @"
# 13.8d — Standard vs ZSL vs HDR vs ReferenceCam (human)

**Gate:** ``$ts``  
**Device serial:** ``$(if ($Serial) { $Serial } else { "from pns_adb_device.env" })``

## Automated (this run)

| Check | Result |
|-------|--------|
| Pipeline verify (`stillMode=standard`) | $($result.pipelineVerifyPass) |
| Benchmark all modes | $($result.benchmarkPass) |
| ReferenceCam session | $(if ($result.proshotSessionDir) { $result.proshotSessionDir } else { "skipped" }) |

## Human — Adobe Camera Raw / Lightroom (daylight)

Open **wide (M23)** for each source; note exposure match, color cast vs ReferenceCam, shadow/highlight preference.

| Source | M14 UW | M23 wide | M73 tele | Notes |
|--------|--------|----------|----------|-------|
| ReferenceCam reference | | | | |
| P&S Standard | | | | |
| P&S ZSL | | | | |
| P&S HDR (use **hdr2of3** or merge 3) | | | | |
| MotionCam (optional) | | | | |

**Sign-off:** When acceptable, run:

``````powershell
.\scripts\pns_m13_8d_gate.ps1 -RecordHumanPass -Dir "$gateDir" -ColorNote "your note"
``````

Artifacts: ``$($result.benchmarkDir)`` / ``report.md``; session ``$($result.proshotSessionDir)``.
"@
$human | Set-Content $humanPath -Encoding UTF8

$result | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $gateDir "m13_8d_gate.json") -Encoding UTF8

if ($Serial) { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null }
else { & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null }

Write-Host ""
Write-Host "=== M13.8d gate PASS ===" -ForegroundColor Green
Write-Host "JSON: $(Join-Path $gateDir 'm13_8d_gate.json')"
Write-Host "Human checklist: $humanPath"
