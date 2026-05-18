# pns_video_capability_probe.ps1
# Sprint 13.15 gate: launch app, wait for MediaCodecCapabilityProbe to log its results,
# assert 4K@120fps performance-point is present and parse the capability matrix.
#
# Usage:
#   .\scripts\pns_video_capability_probe.ps1
#   .\scripts\pns_video_capability_probe.ps1 -Serial "R5CX123456"
#
# Artifacts: hfr-runs/video_cap_probe_<stamp>/probe.json

param(
    [string]$Serial = "",
    [int]$WaitSec = 10
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = "hfr-runs\video_cap_probe_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$adb = "adb"
$adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }

function Adb { & $adb @adbArgs @args }

Write-Host "=== PNS Video Capability Probe (Sprint 13.15) ===" -ForegroundColor Cyan

# Check device
$devices = & $adb devices 2>&1 | Select-String "device$"
if (-not $devices) {
    Write-Error "No ADB device connected."
}

# Clear logcat
Adb logcat -c 2>$null

# Launch app
Write-Host "Launching app..."
Adb shell am start -n "dev.pointandshoot/.MainActivity" `
    --es pns_screen preview 2>&1 | Out-Null

Write-Host "Waiting ${WaitSec}s for probe to complete..."
Start-Sleep -Seconds $WaitSec

# Pull logcat
$logLines = Adb exec-out logcat -d -s "PNS.VideoCapProbe" 2>&1
$logFile = "$outDir\logcat_probe.txt"
$logLines | Set-Content -Path $logFile -Encoding UTF8

Write-Host ""
Write-Host "=== Probe Log ===" -ForegroundColor Yellow
$logLines | Write-Host

# Parse results
$summaryLine = $logLines | Where-Object { $_ -match "capProbeResult" } | Select-Object -Last 1
$perfPoints = $logLines | Where-Object { $_ -match "perfPoint " }
$encoders = $logLines | Where-Object { $_ -match "^.*encoder name=" }

$has4k120 = ($perfPoints | Where-Object { $_ -match "3840x2160@120fps|4096x2160@120fps" }).Count -gt 0
$has1080p120 = ($perfPoints | Where-Object { $_ -match "1920x1080@120fps" }).Count -gt 0
$has1080p240 = ($perfPoints | Where-Object { $_ -match "1920x1080@240fps" }).Count -gt 0
$hasMain10 = $summaryLine -match "main10=True"
$hasHdr10 = $summaryLine -match "hdr10=True"
$hasYuvP010 = $summaryLine -match "yuvp010=True"

Write-Host ""
Write-Host "=== Results ===" -ForegroundColor Cyan
Write-Host "  1080p@120fps perf-point : $has1080p120"
Write-Host "  1080p@240fps perf-point : $has1080p240"
Write-Host "  4K@120fps perf-point    : $has4k120"
Write-Host "  Main10 profile          : $hasMain10"
Write-Host "  HDR10 profile           : $hasHdr10"
Write-Host "  YUVP010 color format    : $hasYuvP010"
Write-Host "  Performance points found: $($perfPoints.Count)"
Write-Host "  Encoders found          : $($encoders.Count)"

$passed = $has1080p120 -and $has4k120 -and $hasMain10 -and $hasYuvP010

$result = [ordered]@{
    timestamp = $stamp
    passed = $passed
    has1080p120 = $has1080p120
    has1080p240 = $has1080p240
    has4k120 = $has4k120
    hasMain10 = $hasMain10
    hasHdr10 = $hasHdr10
    hasYuvP010 = $hasYuvP010
    perfPointCount = $perfPoints.Count
    encoderCount = $encoders.Count
    summaryLine = $summaryLine
    logFile = $logFile
}
$result | ConvertTo-Json | Set-Content "$outDir\probe.json" -Encoding UTF8

Write-Host ""
if ($passed) {
    Write-Host "GATE: PASS" -ForegroundColor Green
} else {
    Write-Host "GATE: FAIL" -ForegroundColor Red
    if (-not $has1080p120) { Write-Host "  MISSING: 1080p@120fps performance-point" -ForegroundColor Red }
    if (-not $has4k120) { Write-Host "  MISSING: 4K@120fps performance-point" -ForegroundColor Red }
    if (-not $hasMain10) { Write-Host "  MISSING: HEVC Main10 profile" -ForegroundColor Red }
    if (-not $hasYuvP010) { Write-Host "  MISSING: YUVP010 color format" -ForegroundColor Red }
}

Write-Host "Artifacts: $outDir"

# Force-stop app (battery rule)
Adb shell am force-stop dev.pointandshoot 2>$null

if (-not $passed) { exit 1 }
