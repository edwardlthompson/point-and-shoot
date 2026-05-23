#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint VF — host gate: JVM smoke, codecs (H.264/H.265/AV1), HFR 120/240/480, A/V ffprobe.

.DESCRIPTION
    Runs **`pns_mediacodec_hfr_verify.ps1 -GateProfile vf -RequireFfprobeAv`** (H.264 @ 60 MediaRecorder,
    H.265 @ 60, HEVC HFR 1080p @ 120/240/480) with pulled MP4s and **ffprobe** audio+video checks.
    Also runs format probe (AV1), stabilization @ 60, and JVM unit tests.

.PARAMETER Serial
.PARAMETER SkipAssemble
    Skip Gradle for child scripts (use existing debug APK).
.PARAMETER SkipAv1Record
    Probe AV1 only (no record attempt) when device lacks encoder.
.PARAMETER SkipHfrMatrix
    Skip HFR/codec matrix (host-only / no device).
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipAv1Record,
    [switch]$SkipHfrMatrix
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot

$utc = Get-Date -Format "yyyyMMdd_HHmmss"
$gateDir = Join-Path $projRoot "hfr-runs\video_quality_gate_$utc"
New-Item -ItemType Directory -Force -Path $gateDir | Out-Null

Write-Host "[video_quality_gate] JVM unit tests..."
& (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:testDebugUnitTest" "--tests" "dev.pointandshoot.VideoEffectsProcessorTest" "--tests" "dev.pointandshoot.PreviewStabilizationTest"
if ($LASTEXITCODE -ne 0) { throw "unit tests failed" }

if (-not $SkipHfrMatrix) {
    if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
        throw "ffprobe required on PATH for HFR/codec gate (audio+video stream checks)"
    }
    Write-Host "[video_quality_gate] HFR + H.264/H.265 matrix (120/240/480 + ffprobe A/V)..."
    $hfrParams = @{
        GateProfile = "vf"
        RequireFfprobeAv = $true
        OutDir = Join-Path $gateDir "mediacodec_vf"
    }
    if ($Serial -ne "") { $hfrParams.Serial = $Serial }
    if ($SkipAssemble) { $hfrParams.SkipAssemble = $true }
    & (Join-Path $PSScriptRoot "pns_mediacodec_hfr_verify.ps1") @hfrParams
    if ($LASTEXITCODE -ne 0) { throw "mediacodec_hfr_verify (vf profile) failed" }
}

Write-Host "[video_quality_gate] format probe (AV1 + cap probe)..."
$fmtParams = @{ WaitSec = 55 }
if ($Serial -ne "") { $fmtParams.Serial = $Serial }
if ($SkipAssemble) { $fmtParams.SkipAssemble = $true }
if (-not $SkipAv1Record) { $fmtParams.RunAv1Record = $true }
& (Join-Path $PSScriptRoot "pns_video_format_test.ps1") @fmtParams
if ($LASTEXITCODE -ne 0) { throw "video_format_test failed" }

Write-Host "[video_quality_gate] stabilization @ 60fps..."
$stabParams = @{}
if ($Serial -ne "") { $stabParams.Serial = $Serial }
if ($SkipAssemble) { $stabParams.SkipAssemble = $true }
& (Join-Path $PSScriptRoot "pns_video_stabilization_test.ps1") @stabParams
if ($LASTEXITCODE -ne 0) { throw "video_stabilization_test failed" }

$pkg = "dev.pointandshoot"
if ($Serial -ne "") {
    & adb -s $Serial shell am force-stop $pkg | Out-Null
} else {
    & adb shell am force-stop $pkg | Out-Null
}

@{
    timestampUtc = (Get-Date).ToUniversalTime().ToString("o")
    gate = "VF"
    hfrMatrix = (-not $SkipHfrMatrix)
    result = "PASS"
    outDir = $gateDir
} | ConvertTo-Json | Set-Content (Join-Path $gateDir "gate.json")

Write-Host "VF VIDEO GATE PASS -> $gateDir"
exit 0
