#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13V.10 gate: manual-focus (M dial) + GLES focus peaking during in-app video record.
.DESCRIPTION
    Cold-starts preview in video-primary mode with M dial and Red peaking, records via
    pns_preview_automation_in_app_video_sec, then greps logcat for PNS.FocusPeaking + recorder lines.
#>
param(
    [int]$RecordSec = 6,
    [string]$Serial = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") {
        . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet
    }

    if (-not $SkipAssemble) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }

    $apk = "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "Missing $apk" }

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    if (-not $SkipInstall) {
        Invoke-AdbCmd install -r -t $apk | Out-Null
    }

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = "hfr-runs\focus_peaking_verify_$stamp"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Write-Host "=== PNS Focus Peaking Verify (Sprint 13V.10) ===" -ForegroundColor Cyan

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Start-Sleep -Milliseconds 600
    Invoke-AdbCmd logcat -c 2>$null

    Write-Host "Launch: video-primary, M dial, manual focus, Red peaking, ${RecordSec}s record..."
    Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview `
        --ez pns_preview_primary_photo false `
        --es pns_preview_dial M `
        --es pns_preview_focus_mode manual `
        --es pns_preview_focus_peaking Red `
        --ei pns_preview_automation_in_app_video_sec $RecordSec 2>&1 | Out-Null

    $totalWait = $RecordSec + 16
    Write-Host "Waiting ${totalWait}s..."
    Start-Sleep -Seconds $totalWait

    $logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
    $logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

    $peakingDiag = $logLines -match "PNS\.FocusPeaking:.*manualFocus active=true"
    $focusModeLog = $logLines -match "PNS\.ChromeUx.*focusMode=.*manual"
    $recordingStart = $logLines -match "start in-app video automation"
    $recordingDone = $logLines -match "finished in-app video automation"
    $recorderPresent = $logLines -match "inAppVideoPrepared|MediaRecorder started"
    $camErrors = $logLines -match "CAMERA_DISCONNECTED|onError.*cameraId"
    $seedPeaking = $logLines -match "preview seeded focusPeakingColor=Red"

    $overallPass = $peakingDiag -and $focusModeLog -and $recordingStart -and $recordingDone -and $recorderPresent -and $seedPeaking -and -not $camErrors

    $result = [ordered]@{
        timestamp       = $stamp
        passed          = $overallPass
        focusModeLog    = [bool]$focusModeLog
        focusPeakingDiag = [bool]$peakingDiag
        recordingStart  = [bool]$recordingStart
        recordingDone   = [bool]$recordingDone
        recorderPresent = [bool]$recorderPresent
        seedPeaking     = [bool]$seedPeaking
        cameraErrors    = [bool]$camErrors
    }
    $result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

    if ($overallPass) {
        Write-Host "GATE: PASS" -ForegroundColor Green
    } else {
        Write-Host "GATE: FAIL" -ForegroundColor Red
        if (-not $focusModeLog) { Write-Host "  FAIL: missing PNS.ChromeUx focusMode=manual" -ForegroundColor Red }
        if (-not $peakingDiag) { Write-Host "  FAIL: missing PNS.FocusPeaking manualFocus diag" -ForegroundColor Red }
        if (-not $recordingStart) { Write-Host "  FAIL: recording did not start" -ForegroundColor Red }
        if (-not $recordingDone) { Write-Host "  FAIL: recording did not finish" -ForegroundColor Red }
        if (-not $recorderPresent) { Write-Host "  FAIL: MediaRecorder not prepared" -ForegroundColor Red }
        if (-not $seedPeaking) { Write-Host "  FAIL: focus peaking color not seeded" -ForegroundColor Red }
        if ($camErrors) { Write-Host "  FAIL: camera errors in logcat" -ForegroundColor Red }
    }

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Write-Host "Artifacts: $outDir"
    if (-not $overallPass) { exit 1 }
} finally {
    Pop-Location
}
