#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13V.11 gate: GLES video LUT on preview during in-app video record.
#>
param(
    [string]$VideoLut = "PnsCinematic",
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
    $outDir = "hfr-runs\video_lut_preview_verify_$stamp"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Write-Host "=== PNS Video LUT Preview Verify (Sprint 13V.11) ===" -ForegroundColor Cyan

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Start-Sleep -Milliseconds 600
    Invoke-AdbCmd logcat -c 2>$null

    Write-Host "Launch: video-primary, video_lut=$VideoLut, record ${RecordSec}s..."
    Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview `
        --ez pns_preview_primary_photo false `
        --es pns_preview_video_lut $VideoLut `
        --ei pns_preview_automation_in_app_video_sec $RecordSec 2>&1 | Out-Null

    $totalWait = $RecordSec + 16
    Write-Host "Waiting ${totalWait}s..."
    Start-Sleep -Seconds $totalWait

    $logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
    $logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

    $lutSeed = $logLines -match "preview seeded videoLut=$VideoLut"
    $lutPreviewIdle = $logLines -match "PNS\.LutPreview:.*previewLut=$VideoLut.*videoPrimary=true.*lutEnabled=true"
    $lutPreviewRecording = $logLines -match "PNS\.LutPreview:.*previewLut=$VideoLut.*recording=true.*lutEnabled=true"
    $recordingStart = $logLines -match "start in-app video automation"
    $recordingDone = $logLines -match "finished in-app video automation"
    $camErrors = $logLines -match "CAMERA_DISCONNECTED|onError.*cameraId"

    $overallPass = $lutSeed -and $lutPreviewIdle -and $lutPreviewRecording -and $recordingStart -and $recordingDone -and -not $camErrors

    $result = [ordered]@{
        timestamp            = $stamp
        passed               = $overallPass
        videoLut             = $VideoLut
        lutSeed              = [bool]$lutSeed
        lutPreviewIdle       = [bool]$lutPreviewIdle
        lutPreviewRecording  = [bool]$lutPreviewRecording
        recordingStart       = [bool]$recordingStart
        recordingDone        = [bool]$recordingDone
        cameraErrors         = [bool]$camErrors
    }
    $result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

    if ($overallPass) {
        Write-Host "GATE: PASS" -ForegroundColor Green
    } else {
        Write-Host "GATE: FAIL" -ForegroundColor Red
        if (-not $lutSeed) { Write-Host "  FAIL: videoLut seed missing" -ForegroundColor Red }
        if (-not $lutPreviewIdle) { Write-Host "  FAIL: idle video-primary LUT log missing" -ForegroundColor Red }
        if (-not $lutPreviewRecording) { Write-Host "  FAIL: recording LUT log missing" -ForegroundColor Red }
        if (-not $recordingStart) { Write-Host "  FAIL: recording did not start" -ForegroundColor Red }
        if (-not $recordingDone) { Write-Host "  FAIL: recording did not finish" -ForegroundColor Red }
        if ($camErrors) { Write-Host "  FAIL: camera errors" -ForegroundColor Red }
    }

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Write-Host "Artifacts: $outDir"
    if (-not $overallPass) { exit 1 }
} finally {
    Pop-Location
}
