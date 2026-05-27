#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.2** — H.264 vs 8-bit HEVC color: codec gate + mean Cb/Cr frame compare.

.DESCRIPTION
  1. Runs [pns_video_codec_color_compare.ps1] (USB record + colorVui=bt709 + ffprobe).
  2. Decodes ~10 frames per clip with ffmpeg; asserts mean U/V (Cb/Cr) delta < 8.

.PARAMETER HostOnly
  Skip USB; exit 0 (CI / host gate).

.PARAMETER Serial
  adb serial (optional; uses scripts/pns_adb_device.env).
#>
param(
    [switch]$HostOnly,
    [string]$Serial = "",
    [int]$RecordSec = 10,
    [double]$MaxCbCrDelta = 8.0
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
. "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet
Push-Location $root
try {
    if ($HostOnly) {
        Write-Host "HFR COLOR COMPARE: SKIP (HostOnly)"
        exit 0
    }
    if (-not (Get-Command ffmpeg -ErrorAction SilentlyContinue)) {
        Write-Host "HFR COLOR COMPARE: SKIP (ffmpeg not on PATH)"
        exit 0
    }
    if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
        Write-Host "HFR COLOR COMPARE: SKIP (python not on PATH)"
        exit 0
    }

    $gateArgs = @{}
    if ($Serial -ne "") { $gateArgs.Serial = $Serial }
    & "$PSScriptRoot\pns_video_codec_color_compare.ps1" @gateArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    $latest = Get-ChildItem -Path "hfr-runs" -Directory -Filter "video_codec_color_compare_*" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latest) { throw "no video_codec_color_compare_* artifact dir" }

    $gateJson = Join-Path $latest.FullName "gate.json"
    if (-not (Test-Path $gateJson)) { throw "missing gate.json in $($latest.FullName)" }
    $gate = Get-Content $gateJson -Raw | ConvertFrom-Json

    $h264 = $gate.h264.mp4
    $hevc = $gate.hevc60.mp4
    if (-not $hevc) { $hevc = $gate.hevc30.mp4 }
    if (-not $h264 -or -not $hevc) { throw "gate.json missing h264/hevc mp4 paths" }
    if (-not (Test-Path $h264)) { throw "missing H.264 mp4: $h264" }
    if (-not (Test-Path $hevc)) { throw "missing HEVC mp4: $hevc" }

    Write-Host ""
    Write-Host "=== YCbCr frame compare (python) ==="
    python "$PSScriptRoot\video_codec_yuv_compare.py" $h264 $hevc --max-cb-cr-delta $MaxCbCrDelta
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host "HFR COLOR COMPARE: PASS"
    exit 0
} finally {
    Pop-Location
}
