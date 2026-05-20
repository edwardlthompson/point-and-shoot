<#
.SYNOPSIS
  Sprint 13.3g-2 — openability gate on a capture-analyze folder + optional ACR sign-off record.

.DESCRIPTION
  - Runs dng_desktop_open_gate.py (P&S M14/M23/M73 triplet with wide-cal leak check)
  - Verifies logcat openability diag lines when *_logcat.txt present
  - -RecordAcrPass: writes acr_signoff.json after human ACR/Lightroom 3/3 (Milestone H)

.EXAMPLE
  .\scripts\pns_m13_3g2_gate.ps1 -Dir hfr-runs\aux_dng_capture_analyze_20260519_235745
  .\scripts\pns_m13_3g2_gate.ps1 -Dir ... -RecordAcrPass -AcrNote "ACR 16.5 all three open, color not judged"
#>
param(
    [string]$Dir = "",
    [string]$Serial = "",
    [switch]$RecordAcrPass,
    [string]$AcrNote = ""
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($Dir)) {
    $latest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "aux_dng_capture_analyze_*" |
        Sort-Object Name | Select-Object -Last 1
    if (-not $latest) { throw "No aux_dng_capture_analyze_* folder; pass -Dir" }
    $Dir = $latest.FullName
}
$Dir = (Resolve-Path -LiteralPath $Dir).Path

Write-Host "=== M13.3g-2 openability gate ===" -ForegroundColor Cyan
Write-Host "Artifact: $Dir"

$gatePs1 = Join-Path $PSScriptRoot "pns_dng_desktop_open_gate.ps1"
& powershell -NoProfile -ExecutionPolicy Bypass -File $gatePs1 -Dir $Dir
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$logcatOk = $true
$diagNeedles = @(
    @{ file = "M14_uw_logcat.txt"; cam = "3" },
    @{ file = "M23_wide_logcat.txt"; cam = "2" },
    @{ file = "M73_tele_logcat.txt"; cam = "4" }
)
foreach ($slot in $diagNeedles) {
    $lp = Join-Path $Dir $slot.file
    if (-not (Test-Path -LiteralPath $lp)) { continue }
    $text = Get-Content -LiteralPath $lp -Raw
    if ($text -notmatch "dng openability diag cam=$($slot.cam) reconcile=false wideCal=false") {
        Write-Host "WARN: missing openability diag for cam $($slot.cam) in $($slot.file)" -ForegroundColor Yellow
        $logcatOk = $false
    }
    if ($text -match "leaf still: skip stopRepeating") {
        Write-Host "FAIL: skip stopRepeating in $($slot.file)" -ForegroundColor Red
        exit 1
    }
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$result = @{
    schema = "m13_3g2_gate.v1"
    timestampUtc = $ts
    artifactDir = $Dir
    openabilityGate = "PASS"
    logcatOpenabilityDiag = $logcatOk
    serial = $Serial
}
$result | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $Dir "m13_3g2_gate.json") -Encoding UTF8
if (-not (Test-Path (Join-Path $Dir "openability_gate.json"))) {
    @{
        schema = "openability_gate.v1"
        timestampUtc = $ts
        gate = "PASS"
        serial = $Serial
        paths = @{
            uw = (Join-Path $Dir "M14_uw.dng")
            wide = (Join-Path $Dir "M23_wide.dng")
            tele = (Join-Path $Dir "M73_tele.dng")
        }
        checks = @("dng_tiff_integrity", "rawpy_decode", "asn_sanity", "wide_cal_leak")
        backfilledBy = "pns_m13_3g2_gate.ps1"
    } | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $Dir "openability_gate.json") -Encoding UTF8
}

if ($RecordAcrPass) {
    $acr = @{
        schema = "acr_signoff.v1"
        timestampUtc = $ts
        artifactDir = $Dir
        gate = "PASS"
        slots = @("M14_uw", "M23_wide", "M73_tele")
        note = if ($AcrNote) { $AcrNote } else { "Human verified ACR/Lightroom open 3/3 (Milestone H / 13.3g-2)" }
    }
    $acr | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $Dir "acr_signoff.json") -Encoding UTF8
    @"
# ACR sign-off recorded ($ts)

$($acr.note)

Files: M14_uw.dng, M23_wide.dng, M73_tele.dng
"@ | Set-Content (Join-Path $Dir "ACR_HUMAN_VERIFY.md") -Encoding UTF8
    Write-Host "ACR sign-off written: $(Join-Path $Dir 'acr_signoff.json')" -ForegroundColor Green
}

Write-Host "M13.3g-2 GATE: PASS (automated)" -ForegroundColor Green
if (-not $RecordAcrPass) {
    Write-Host "Human: open the three DNGs in ACR, then re-run with -RecordAcrPass" -ForegroundColor Yellow
}
exit 0
