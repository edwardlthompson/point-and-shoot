<#
.SYNOPSIS
  Milestone 13.3f — daylight USB gates (capture, openability, ReferenceCam parity, optional session).

.DESCRIPTION
  1. pns_capture_pipeline_verify.ps1 -Fast
  2. pns_aux_dng_capture_analyze.ps1 -PreviewDial A -NoFast
  3. pns_m13_3g2_gate.ps1 on capture folder (openability)
  4. dng_referenceapp_parity_gate.py vs tests/fixtures/referenceapp_legacy_sku/
  5. Optional: pns_referenceapp_live_forensics + reference sync (-RefreshProshotRefs)
  6. Optional: pns_dng_referenceapp_pns_session.ps1 (-RunProshotSession)
  7. Writes m13_3f_gate.json + ACR_HUMAN_VERIFY.md (color checklist)

  Exit 0 when pipeline + capture 3/3 + openability PASS (parity may FAIL — documented).
  Exit 1 on capture/openability/pipeline failure, or when -RequireParityPass and parity FAIL.

.EXAMPLE
  .\scripts\pns_m13_3f_gate.ps1 -Serial <serial>
  .\scripts\pns_m13_3f_gate.ps1 -RecordAcrPass -AcrColorAcceptable -AcrNote "ACR 16.5 opens 3/3; UW/tele cast vs ReferenceCam"
#>
param(
    [string]$Serial = "",
    [string]$Dir = "",
    [switch]$SkipPipelineVerify,
    [switch]$SkipCapture,
    [string]$CaptureDir = "",
    [switch]$RefreshProshotRefs,
    [switch]$RunProshotSession,
    [switch]$RequireParityPass,
    [switch]$RecordAcrPass,
    [switch]$AcrColorAcceptable,
    [string]$AcrNote = ""
)

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

$projRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "pns_resolve_referenceapp_fixture_dir.ps1")
$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$gateDir = Join-Path $projRoot "hfr-runs\m13_3f_gate_$ts"
New-Item -ItemType Directory -Force -Path $gateDir | Out-Null

$result = [ordered]@{
    schema = "m13_3f_gate.v1"
    timestampUtc = $ts
    gateDir = $gateDir
    serial = $Serial
    pipelineVerifyPass = $null
    captureAnalyzePass = $null
    openabilityPass = $null
    parityPass = $null
    referenceappSessionDir = $null
    captureDir = $CaptureDir
    fixtureDir = (Resolve-PnsReferenceAppFixtureDir -ProjectRoot $projRoot -RequireExists)
}

Write-Host "=== M13.3f daylight gate ($ts) ===" -ForegroundColor Cyan
Write-Host "Artifacts: $gateDir"

if ($RecordAcrPass -and -not [string]::IsNullOrWhiteSpace($Dir)) {
    $targetDir = (Resolve-Path -LiteralPath $Dir).Path
    $acr = @{
        schema = "acr_signoff_13_3f.v1"
        timestampUtc = $ts
        artifactDir = $targetDir
        openability = "PASS"
        colorAcceptable = [bool]$AcrColorAcceptable
        slots = @("M14_uw", "M23_wide", "M73_tele")
        note = if ($AcrNote) { $AcrNote } else { "Human ACR review (13.3f)" }
    }
    $acr | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $targetDir "acr_signoff_13_3f.json") -Encoding UTF8
    Write-Host "ACR sign-off: $(Join-Path $targetDir 'acr_signoff_13_3f.json')" -ForegroundColor Green
    exit 0
}

if ($RefreshProshotRefs) {
    Write-Host "[13.3f] ReferenceCam live forensics (15/23/73 mm)..." -ForegroundColor Cyan
    $foreArgs = @{ TryUiAutomation = $true }
    if ($Serial) { $foreArgs["Serial"] = $Serial }
    & (Join-Path $PSScriptRoot "pns_referenceapp_live_forensics.ps1") @foreArgs
    if ($LASTEXITCODE -ne 0) { throw "referenceapp_live_forensics failed" }
    $foreLatest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "referenceapp_live_forensics_*" |
        Sort-Object Name | Select-Object -Last 1
    if ($foreLatest) {
        & (Join-Path $PSScriptRoot "pns_referenceapp_reference_sync.ps1") -FromForensicsDir $foreLatest.FullName
        if ($LASTEXITCODE -ne 0) { throw "referenceapp_reference_sync failed" }
    }
}

if (-not $SkipPipelineVerify) {
    Write-Host "[13.3f] capture pipeline verify (-Fast)..." -ForegroundColor Cyan
    $pvArgs = @{ Fast = $true; MaxAttempts = 2; NoHistoryAppend = $true }
    if ($Serial) { $pvArgs["Serial"] = $Serial }
    & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1") @pvArgs
    $result.pipelineVerifyPass = ($LASTEXITCODE -eq 0)
    if (-not $result.pipelineVerifyPass) {
        $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $gateDir "m13_3f_gate.json") -Encoding UTF8
        throw "pipeline verify FAIL"
    }
}

if (-not $SkipCapture) {
    Write-Host "[13.3f] aux DNG capture analyze (dial A, -NoFast)..." -ForegroundColor Cyan
    $capArgs = @{
        PreviewDial = "A"
        NoFast = $true
        OutDir = (Join-Path $gateDir "pns_capture")
    }
    if ($Serial) { $capArgs["Serial"] = $Serial }
    & (Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1") @capArgs
    $result.captureAnalyzePass = ($LASTEXITCODE -eq 0)
    $result.captureDir = $capArgs.OutDir
    if (-not $result.captureAnalyzePass) {
        $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $gateDir "m13_3f_gate.json") -Encoding UTF8
        throw "capture analyze FAIL"
    }
} elseif ([string]::IsNullOrWhiteSpace($result.captureDir)) {
    $latest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "aux_dng_capture_analyze_*" |
        Sort-Object Name | Select-Object -Last 1
    if ($latest) { $result.captureDir = $latest.FullName }
    else { throw "No capture dir; omit -SkipCapture or pass -CaptureDir" }
}

Write-Host "[13.3f] openability gate (13.3g-2)..." -ForegroundColor Cyan
$g2Args = @{ Dir = $result.captureDir }
if ($Serial) { $g2Args["Serial"] = $Serial }
& (Join-Path $PSScriptRoot "pns_m13_3g2_gate.ps1") @g2Args
$result.openabilityPass = ($LASTEXITCODE -eq 0)
if (-not $result.openabilityPass) {
    $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $gateDir "m13_3f_gate.json") -Encoding UTF8
    throw "openability FAIL"
}

Write-Host "[13.3f] ReferenceCam parity vs fixtures..." -ForegroundColor Cyan
$parityJson = Join-Path $result.captureDir "referenceapp_parity_gate.json"
& python (Join-Path $PSScriptRoot "dng_referenceapp_parity_gate.py") $result.captureDir `
    --referencecam-dir $result.fixtureDir --json-out $parityJson
$result.parityPass = ($LASTEXITCODE -eq 0)
Copy-Item -LiteralPath $parityJson -Destination (Join-Path $gateDir "referenceapp_parity_gate.json") -Force -ErrorAction SilentlyContinue

if ($RunProshotSession) {
    Write-Host "[13.3f] ReferenceCam + P&S session (side-by-side)..." -ForegroundColor Cyan
    $sessArgs = @{
        PreviewDial = "A"
        Notes = "M13.3f gate $ts — daylight; dial A not H"
    }
    if ($Serial) { $sessArgs["Serial"] = $Serial }
    if ($RefreshProshotRefs) { $sessArgs["SkipProshotPull"] = $true }
    & (Join-Path $PSScriptRoot "pns_dng_referenceapp_pns_session.ps1") @sessArgs
    if ($LASTEXITCODE -eq 0) {
        $sessLatest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "dng_referenceapp_pns_session_*" |
            Sort-Object Name | Select-Object -Last 1
        if ($sessLatest) { $result.referenceappSessionDir = $sessLatest.FullName }
    }
}

$acrTemplate = @"
# M13.3f — human ACR / color sign-off ($ts UTC)

**Device:** $($Serial)
**P&S captures:** ``$($result.captureDir)``
**ReferenceCam fixtures:** ``$($result.fixtureDir)``

## Automated (this run)

| Gate | Result |
|------|--------|
| Pipeline verify | $(if ($result.pipelineVerifyPass) { 'PASS' } elseif ($null -eq $result.pipelineVerifyPass) { 'skip' } else { 'FAIL' }) |
| Capture 3/3 + integrity | $(if ($result.captureAnalyzePass) { 'PASS' } else { 'FAIL' }) |
| Openability (13.3g) | $(if ($result.openabilityPass) { 'PASS' } else { 'FAIL' }) |
| ReferenceCam parity (rawpy) | $(if ($result.parityPass) { 'PASS' } else { 'FAIL' }) |

## Human checklist (required for Milestone H color close)

1. Open **M14_uw.dng**, **M23_wide.dng**, **M73_tele.dng** in **Adobe Camera Raw** or Lightroom.
2. Confirm all three **open** without error (openability).
3. Compare **UW** and **tele** color vs ReferenceCam reference (same scene, daylight).
4. Record: acceptable **yes/no** per slot; note cast (green/magenta/luminance).

After review:
``````powershell
.\scripts\pns_m13_3f_gate.ps1 -Dir "$($result.captureDir)" -RecordAcrPass [-AcrColorAcceptable] -AcrNote "your note"
``````

"@

$acrPath = Join-Path $result.captureDir "ACR_HUMAN_VERIFY.md"
$acrTemplate | Set-Content $acrPath -Encoding UTF8
Copy-Item $acrPath (Join-Path $gateDir "ACR_HUMAN_VERIFY.md") -Force

if ($RecordAcrPass) {
    $targetDir = if (-not [string]::IsNullOrWhiteSpace($Dir)) { (Resolve-Path $Dir).Path } else { $result.captureDir }
    $acr = @{
        schema = "acr_signoff_13_3f.v1"
        timestampUtc = $ts
        artifactDir = $targetDir
        openability = "PASS"
        colorAcceptable = [bool]$AcrColorAcceptable
        slots = @("M14_uw", "M23_wide", "M73_tele")
        note = if ($AcrNote) { $AcrNote } else { "Human ACR review (13.3f)" }
    }
    $acr | ConvertTo-Json -Depth 4 | Set-Content (Join-Path $targetDir "acr_signoff_13_3f.json") -Encoding UTF8
}

$result | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $gateDir "m13_3f_gate.json") -Encoding UTF8

$adbStop = @("shell", "am", "force-stop", "dev.pointandshoot")
if ($Serial) { $adbStop = @("-s", $Serial) + $adbStop }
& adb @adbStop 2>$null | Out-Null

Write-Host ""
Write-Host "=== M13.3f summary ===" -ForegroundColor Cyan
Write-Host "  Pipeline:  $($result.pipelineVerifyPass)"
Write-Host "  Capture:   $($result.captureAnalyzePass)"
Write-Host "  Open gate: $($result.openabilityPass)"
Write-Host "  Parity:    $($result.parityPass)"
Write-Host "  ACR doc:   $acrPath"

if ($RequireParityPass -and -not $result.parityPass) {
    Write-Host "FAIL: -RequireParityPass and parity gate did not pass" -ForegroundColor Red
    exit 1
}
if (-not $result.openabilityPass -or -not $result.captureAnalyzePass) {
    exit 1
}
Write-Host "M13.3f automated gates: PASS (parity may still FAIL — see ACR checklist)" -ForegroundColor Green
exit 0
