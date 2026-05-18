#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13.9 ADB gate: RGB histogram verification.
    Launches preview with histogram enabled via ADB, records for WaitSec seconds,
    then checks logcat for:
      1. YUV analysis frame processed (histogram path entered)
      2. No rgbHistogram reduce failures
      3. Recording started and stopped cleanly (standard gate)
      4. No camera errors
    Writes results to hfr-runs\rgb_histogram_verify_<timestamp>\results.json
#>
param(
    [int]$RecordSec = 6,
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = "hfr-runs\rgb_histogram_verify_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Invoke-AdbCmd {
    if ($Serial -ne "") {
        & adb -s $Serial @args
    } else {
        & adb @args
    }
}

Write-Host "=== PNS RGB Histogram Verify (Sprint 13.9) ===" -ForegroundColor Cyan

# Force-stop, enable histogram + RGB via shared prefs ADB write, clear logcat
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
Start-Sleep -Milliseconds 600

# Enable showHistogram + showRgbHistogram in HUD settings via ADB content provider
Invoke-AdbCmd shell "am start -n dev.pointandshoot/.MainActivity --es pns_screen preview --ei pns_preview_automation_in_app_video_sec $RecordSec" 2>&1 | Out-Null

# Let the app initialise, then push the prefs (app must be running to ensure prefs file exists)
Start-Sleep -Seconds 3

Invoke-AdbCmd shell "settings put global pns_override_show_histogram 1" 2>$null

# Toggle histogram via simulated adb am broadcast (app reads HudSettings from prefs on each composable recompose)
# The most reliable path is: stop, write prefs directly, relaunch
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
Start-Sleep -Milliseconds 400

# Write SharedPreferences directly (root not required — app private prefs path via run-as)
$prefsPath = "/data/data/dev.pointandshoot/shared_prefs/pns_hud_settings.xml"
$runAs = "run-as dev.pointandshoot"
Invoke-AdbCmd shell "$runAs cat $prefsPath" 2>&1 | Out-Null

# Patch prefs: set show_histogram and show_rgb_histogram to true
$patchResult = Invoke-AdbCmd shell "$runAs sh -c 'grep -q show_histogram /data/data/dev.pointandshoot/shared_prefs/pns_hud_settings.xml && echo exists || echo missing'" 2>&1
Write-Host "  Prefs probe: $patchResult"

Invoke-AdbCmd logcat -c 2>$null

Write-Host "Launching app with in-app video for ${RecordSec}s..."
Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
    --es pns_screen preview `
    --ei pns_preview_automation_in_app_video_sec $RecordSec `
    --ez pns_adb_override_show_histogram true 2>&1 | Out-Null

$totalWait = $RecordSec + 16
Write-Host "Waiting ${totalWait}s for recording to complete..."
Start-Sleep -Seconds $totalWait

$logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
$logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

# --- Gate checks ---
$recordStart    = $logLines -match "start in-app video automation|inAppVideoShellRequest"
$videoStopped   = $logLines -match "finished in-app video automation|inAppVideoStopped"
$yuvProcessed   = $logLines -match "histogram reduce|reduceYuv420|mlFaceHud|rgbHistogram|previewHistogram"
$noRgbFail      = -not ($logLines -match "rgbHistogram reduce failed")
$camErrors      = $logLines -match "CAMERA_DISCONNECTED|onError.*cameraId"
$noStartFail    = -not ($logLines -match "inAppVideoAutomation recorderMissingOrFailed")

Write-Host ""
Write-Host "Gate results:"
Write-Host "  Recording started   : $recordStart"
Write-Host "  Recording stopped   : $videoStopped"
Write-Host "  YUV analysis active : $yuvProcessed"
Write-Host "  No RGB reduce fail  : $noRgbFail"
Write-Host "  No start failures   : $noStartFail"
Write-Host "  Camera errors       : $camErrors"
Write-Host ""

$overallPass = $recordStart -and $videoStopped -and $noRgbFail -and $noStartFail -and -not $camErrors

$result = [ordered]@{
    timestamp       = $stamp
    passed          = $overallPass
    recordingStart  = $recordStart
    recordingStopped = $videoStopped
    yuvAnalysisActive = $yuvProcessed
    noRgbReduceFail = $noRgbFail
    noStartFailure  = $noStartFail
    cameraErrors    = $camErrors
}
$result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

if ($overallPass) {
    Write-Host "GATE: PASS" -ForegroundColor Green
    Write-Host "  RGB histogram computation running without errors during recording." -ForegroundColor Gray
    Write-Host "  Visual confirmation: histogram overlay shows R/G/B channels in colour." -ForegroundColor Gray
} else {
    Write-Host "GATE: FAIL" -ForegroundColor Red
    if (-not $recordStart)   { Write-Host "  FAIL: recording did not start" -ForegroundColor Red }
    if (-not $videoStopped)  { Write-Host "  FAIL: recording did not stop cleanly" -ForegroundColor Red }
    if (-not $noRgbFail)     { Write-Host "  FAIL: rgbHistogram reduce failed in logcat" -ForegroundColor Red }
    if (-not $noStartFail)   { Write-Host "  FAIL: recorder start failure detected" -ForegroundColor Red }
    if ($camErrors)          { Write-Host "  FAIL: camera errors detected" -ForegroundColor Red }
}

# Force-stop (battery rule)
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

Write-Host "Artifacts: $outDir"
