<#
.SYNOPSIS
  Run DNG pipeline matrix bisect steps on USB device; score with dng_color_metric.py.

.EXAMPLE
  .\scripts\pns_dng_matrix_bisect.ps1 -Serial 8bf09993
  .\scripts\pns_dng_matrix_bisect.ps1 -Steps E1,E2,E5 -SkipBuild
#>
param(
    [string]$Serial = "",
    [string[]]$Steps = @("E19_hal_cal"),
    [switch]$SkipBuild,
    [int]$WaitSec = 52
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$auxScript = Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1"
$metricPy = Join-Path $PSScriptRoot "dng_color_metric.py"

$stepDefs = @{
    baseline       = @{ label = "baseline (MotionCam shipped)"; am = @() }
    E1             = @{ label = "skip StillCaptureMetadata"; am = @("--ez", "pns_preview_dng_skip_still_metadata", "true") }
    E1_50708       = @{ label = "E1 + skip UniqueCameraModel 50708"; am = @(
        "--ez", "pns_preview_dng_skip_still_metadata", "true",
        "--ez", "pns_preview_dng_skip_unique_camera_model", "true"
    ) }
    E2             = @{ label = "ProShot backend (RAW order + IQ)"; am = @("--es", "pns_preview_still_dng_backend", "framework_proshot") }
    E2_reconcile   = @{ label = "ProShot + leaf ASN reconcile"; am = @(
        "--es", "pns_preview_still_dng_backend", "framework_proshot",
        "--ez", "pns_preview_dng_force_leaf_reconcile", "true"
    ) }
    E2_skipmeta    = @{ label = "ProShot + skip metadata"; am = @(
        "--es", "pns_preview_still_dng_backend", "framework_proshot",
        "--ez", "pns_preview_dng_skip_still_metadata", "true"
    ) }
    E3_skipjpeg    = @{ label = "skip JPEG hints on RAW still"; am = @("--ez", "pns_preview_dng_skip_jpeg_hints_still", "true") }
    E7_reconcile_mc = @{ label = "MotionCam + force leaf reconcile"; am = @(
        "--ez", "pns_preview_dng_force_leaf_reconcile", "true"
    ) }
    E11_gains_asn = @{ label = "shipped: PROSHOT + gains-first ASN (no Bayer)"; am = @() }
    E12_no_reconcile = @{ label = "PROSHOT + force reconcile OFF (ProShot parity)"; am = @(
        "--es", "pns_preview_still_dng_backend", "framework_proshot",
        "--ez", "pns_preview_dng_force_leaf_reconcile", "false"
    ) }
    E13_minimal = @{ label = "minimal DngCreator (no meta/50708/desc/reconcile)"; am = @(
        "--es", "pns_preview_still_dng_backend", "framework_proshot",
        "--ez", "pns_preview_dng_skip_still_metadata", "true",
        "--ez", "pns_preview_dng_skip_unique_camera_model", "true",
        "--ez", "pns_preview_dng_skip_software_desc", "true",
        "--ez", "pns_preview_dng_force_leaf_reconcile", "false"
    ) }
    E14_bayer_asn = @{ label = "PROSHOT + Bayer ASN (legacy bad path)"; am = @(
        "--es", "pns_preview_still_dng_backend", "framework_proshot",
        "--ez", "pns_preview_dng_force_leaf_reconcile", "true",
        "--ez", "pns_preview_dng_force_bayer_asn", "true"
    ) }
    E15_split_asn = @{ label = "shipped: tele Bayer ASN + UW HAL WB gains + wide skip"; am = @() }
    E16_capture_gains = @{ label = "still-request WB gains + post ASN"; am = @() }
    E17_bayer_both = @{ label = "Bayer ASN uw+tele + still gains + wide skip"; am = @() }
    E18_ship_combo = @{ label = "tele Bayer + uw gains ASN + skip leaf metadata + still gains"; am = @() }
    E19_hal_cal = @{ label = "shipped: HAL cal CM/FM/NCP + still gains + skip metadata/50708"; am = @() }
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outRoot = Join-Path $projRoot "hfr-runs\dng_matrix_bisect_$ts"
New-Item -ItemType Directory -Force -Path $outRoot | Out-Null

$results = @()

foreach ($step in $Steps) {
    if (-not $stepDefs.ContainsKey($step)) {
        throw "Unknown step: $step (known: $($stepDefs.Keys -join ', '))"
    }
    $def = $stepDefs[$step]
    Write-Host ""
    Write-Host "=== STEP $step : $($def.label) ===" -ForegroundColor Cyan

    $stepDir = Join-Path $outRoot $step
    New-Item -ItemType Directory -Force -Path $stepDir | Out-Null

    $env:PNS_MATRIX_BISECT_AM = ($def.am -join "`0")
    # Run capture into stepDir by copying latest aux run — invoke aux with custom out via env hack:
    # Extend aux script: -OutDir parameter
    $auxParams = @{
        PreviewDial = "A"
        WaitSec     = $WaitSec
        OutDir      = $stepDir
        ExtraAmArgs = [string[]]$def.am
    }
    if ($Serial) { $auxParams["Serial"] = $Serial }
    if ($SkipBuild) { $auxParams["SkipBuild"] = $true }

    & $auxScript @auxParams
    if ($LASTEXITCODE -ne 0) {
        $results += [pscustomobject]@{ step = $step; label = $def.label; capture = "FAIL"; metric = "SKIP"; parity = "SKIP" }
        continue
    }

    $parityJson = Join-Path $stepDir "proshot_parity_gate.json"
    $parityStatus = if ((Test-Path $parityJson) -and ((Get-Content $parityJson -Raw | ConvertFrom-Json).gate -eq "PASS")) { "PASS" } else { "FAIL" }

    $uw = Join-Path $stepDir "M14_uw.dng"
    $wide = Join-Path $stepDir "M23_wide.dng"
    $tele = Join-Path $stepDir "M73_tele.dng"
    if (-not ((Test-Path $uw) -and (Test-Path $wide) -and (Test-Path $tele))) {
        $results += [pscustomobject]@{ step = $step; label = $def.label; capture = "PARTIAL"; metric = "SKIP"; parity = "SKIP" }
        continue
    }

    $metricJson = Join-Path $stepDir "color_metric.json"
    & python $metricPy --json $uw $wide $tele | Out-File -Encoding utf8 $metricJson
    $metricExit = $LASTEXITCODE
    $parsed = Get-Content $metricJson -Raw | ConvertFrom-Json
    $uwD = $parsed.slots.uw.render_green_delta_vs_wide
    $teleD = $parsed.slots.tele.render_green_delta_vs_wide
    $results += [pscustomobject]@{
        step = $step
        label = $def.label
        capture = "OK"
        metric = if ($metricExit -eq 0) { "PASS" } else { "FAIL" }
        parity = $parityStatus
        uw_delta = $uwD
        tele_delta = $teleD
    }
    Write-Host "  uw_delta=$uwD tele_delta=$teleD metric=$(if ($metricExit -eq 0) { 'PASS' } else { 'FAIL' })" -ForegroundColor $(if ($metricExit -eq 0) { 'Green' } else { 'Yellow' })
}

$reportMd = Join-Path $outRoot "report.md"
$lines = @(
    "# DNG matrix bisect $ts",
    "",
    "| Step | Label | Capture | Metric | Parity | uw_delta | tele_delta |",
    "|------|-------|---------|--------|--------|----------|------------|"
)
foreach ($r in $results) {
    $lines += "| $($r.step) | $($r.label) | $($r.capture) | $($r.metric) | $($r.parity) | $($r.uw_delta) | $($r.tele_delta) |"
}
$lines += ""
$best = $results | Where-Object { $_.metric -eq "PASS" } | Sort-Object { [math]::Abs($_.uw_delta) + [math]::Abs($_.tele_delta) } | Select-Object -First 1
if ($best) {
    $lines += "**Best metric PASS:** $($best.step) — $($best.label)"
} else {
    $lines += "**No step passed color_metric_gate** — inspect render_green deltas; lowest |delta| is the next ship candidate."
    $near = $results | Where-Object { $_.capture -eq "OK" } | Sort-Object { [math]::Abs($_.uw_delta) + [math]::Abs($_.tele_delta) } | Select-Object -First 1
    if ($near) { $lines += "**Closest:** $($near.step) uw=$($near.uw_delta) tele=$($near.tele_delta)" }
}
$lines | Out-File -Encoding utf8 $reportMd

Write-Host ""
Write-Host "Report: $reportMd" -ForegroundColor Cyan
& adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null
