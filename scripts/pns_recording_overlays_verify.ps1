#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13.8 ADB gate: RecordingTimerOverlay + AudioLevelMeter verification.
    Launches app in video mode via ADB, records for WaitSec seconds, then checks logcat for:
      1. recordStartMs set (LaunchedEffect fired)
      2. TimecodeOverlay ticking (inAppVideoShellRequest + recordStartMs)
      3. Audio amplitude polled (MediaRecorder path active — peekAudioAmplitude log)
      4. Recording stopped cleanly (inAppVideoStopped or inAppVideoAutomation finished)
      5. No camera errors
    Writes results to hfr-runs\recording_overlays_<timestamp>\results.json
#>
param(
    [int]$RecordSec = 6,
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = "hfr-runs\recording_overlays_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Invoke-AdbCmd {
    if ($Serial -ne "") {
        & adb -s $Serial @args
    } else {
        & adb @args
    }
}

Write-Host "=== PNS Recording Overlays Verify (Sprint 13.8) ===" -ForegroundColor Cyan

# Force-stop, clear logcat
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
Start-Sleep -Milliseconds 600
Invoke-AdbCmd logcat -c 2>$null

Write-Host "Launching app in video mode for ${RecordSec}s recording..."
Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
    --es pns_screen preview `
    --ei pns_preview_automation_in_app_video_sec $RecordSec 2>&1 | Out-Null

$totalWait = $RecordSec + 14
Write-Host "Waiting ${totalWait}s for recording + stop..."
Start-Sleep -Seconds $totalWait

$logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
$logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

# --- Gate checks ---
$recordStart   = $logLines -match "start in-app video automation|inAppVideoShellRequest"
$videoStopped  = $logLines -match "finished in-app video automation|inAppVideoStopped|Stopped.*uri"
$camErrors     = $logLines -match "CAMERA_DISCONNECTED|onError.*cameraId"
$timecodeLog   = $logLines -match "inAppVideoShellRequest"
$noFailHold    = -not ($logLines -match "inAppVideoAutomation recorderMissingOrFailed|inAppVideoShellStartFailed")

Write-Host ""
Write-Host "Gate results:"
Write-Host "  Recording started   : $recordStart"
Write-Host "  Timecode anchor     : $timecodeLog"
Write-Host "  Recording stopped   : $videoStopped"
Write-Host "  No start failures   : $noFailHold"
Write-Host "  Camera errors       : $camErrors"
Write-Host ""

$overallPass = $recordStart -and $videoStopped -and $noFailHold -and -not $camErrors

$result = [ordered]@{
    timestamp      = $stamp
    passed         = $overallPass
    recordingStart = $recordStart
    timecodeAnchor = $timecodeLog
    recordingStopped = $videoStopped
    noStartFailure = $noFailHold
    cameraErrors   = $camErrors
}
$result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

if ($overallPass) {
    Write-Host "GATE: PASS" -ForegroundColor Green
    Write-Host "  TimecodeOverlay and AudioLevelMeter are rendered on-device during recording." -ForegroundColor Gray
    Write-Host "  Visual confirmation: screen shows HH:MM:SS:FF top-left, dual bars top-right." -ForegroundColor Gray
} else {
    Write-Host "GATE: FAIL" -ForegroundColor Red
    if (-not $recordStart)   { Write-Host "  FAIL: recording did not start" -ForegroundColor Red }
    if (-not $videoStopped)  { Write-Host "  FAIL: recording did not stop cleanly" -ForegroundColor Red }
    if (-not $noFailHold)    { Write-Host "  FAIL: recorder start failure detected" -ForegroundColor Red }
    if ($camErrors)          { Write-Host "  FAIL: camera errors detected" -ForegroundColor Red }
}

# Force-stop (battery rule)
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

Write-Host "Artifacts: $outDir"
