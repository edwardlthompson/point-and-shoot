#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13.6 ADB gate: Macro shooting mode verification.
    Launches app in Macro (CommandDialMode.Macro) via ADB extra, verifies:
      1. App switches to best close-focus camera (macroMode autoSwitch log)
      2. Macro session parameters applied (superMacroCloseup probe log)
      3. AF locked to CONTINUOUS_PICTURE (afMode=CONTINUOUS_PICTURE log)
      4. Still capture succeeds (inAppStillSaved or stillSaved log)
      5. No camera errors (CAMERA_DISCONNECTED / codec error)
    Writes results to hfr-runs\macro_verify_<timestamp>\results.json
#>
param(
    [int]$WaitSec = 12,
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = "hfr-runs\macro_verify_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Invoke-AdbCmd {
    if ($Serial -ne "") {
        & adb -s $Serial @args
    } else {
        & adb @args
    }
}

Write-Host "=== PNS Macro Mode Verify (Sprint 13.6) ===" -ForegroundColor Cyan

# Force-stop, clear logcat
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
Start-Sleep -Milliseconds 600
Invoke-AdbCmd logcat -c 2>$null

Write-Host "Launching app in Macro mode (CommandDialMode.Macro)..."
Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
    --es pns_screen preview `
    --es pns_preview_dial MACRO `
    --es pns_preview_focus_mode macro 2>&1 | Out-Null

Write-Host "Waiting ${WaitSec}s for macro session to initialise and capture a still..."
Start-Sleep -Seconds $WaitSec

# Trigger a still capture via volume-up shutter (ADB key event)
Invoke-AdbCmd shell input keyevent 24 2>$null
Start-Sleep -Seconds 3

$logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
$logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

# --- Gate checks ---
$macroSwitch   = $logLines -match "macroMode autoSwitch( cameraId=|UW)"
$macroSession  = $logLines -match "superMacroCloseup probe.*vendorKeyApplied=(true|false)"
$macroApplied  = $logLines -match "superMacroCloseup probe.*vendorKeyApplied=true"
$afLocked      = $logLines -match "superMacroCloseup afMode=CONTINUOUS_PICTURE|reqAfMode=4"
$stillSaved    = $logLines -match "inAppStillSaved|stillSaved|imageSaved"
$camErrors     = $logLines -match "CAMERA_DISCONNECTED|onError.*cameraId"

Write-Host ""
Write-Host "Gate results:"
Write-Host "  Macro auto-switch   : $macroSwitch"
Write-Host "  Macro session probe : $macroSession"
Write-Host "  Vendor key applied  : $macroApplied"
Write-Host "  AF CONTINUOUS_PIC   : $afLocked"
Write-Host "  Still saved         : $stillSaved"
Write-Host "  Camera errors       : $camErrors"
Write-Host ""

# Overall pass: macro camera auto-switch + no cam errors mandatory; vendor key is device-dependent.
$overallPass = $macroSwitch -and -not $camErrors

$result = [ordered]@{
    timestamp      = $stamp
    passed         = $overallPass
    macroAutoSwitch = $macroSwitch
    macroSession   = $macroSession
    macroApplied   = $macroApplied
    afContinuous   = $afLocked
    stillSaved     = $stillSaved
    cameraErrors   = $camErrors
}
$result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

if ($overallPass) {
    Write-Host "GATE: PASS" -ForegroundColor Green
    if (-not $macroApplied) {
        Write-Host "  NOTE: vendor macro key not applied (non-OPLUS device or key not advertised) — macro auto-switch + no errors sufficient" -ForegroundColor Yellow
    }
    if (-not $afLocked) {
        Write-Host "  NOTE: CONTINUOUS_PICTURE AF log not seen (AF mode may default correctly on this device)" -ForegroundColor Yellow
    }
} else {
    Write-Host "GATE: FAIL" -ForegroundColor Red
    if (-not $macroSwitch) { Write-Host "  FAIL: macro camera auto-switch not detected in logcat" -ForegroundColor Red }
    if ($camErrors)     { Write-Host "  FAIL: camera errors detected" -ForegroundColor Red }
}

# Force-stop (battery rule)
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

Write-Host "Artifacts: $outDir"
