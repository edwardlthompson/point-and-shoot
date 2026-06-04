#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint PO.2 gate: adaptive preview FPS + lifecycle pause/resume for long-running preview work.
#>
param(
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
        & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest --tests "dev.pointandshoot.PreviewAdaptiveFpsPolicyTest"
        if ($LASTEXITCODE -ne 0) { throw "PreviewAdaptiveFpsPolicyTest failed" }
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

    Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null | Out-Null
    Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.READ_MEDIA_IMAGES 2>$null | Out-Null
    Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.READ_MEDIA_VIDEO 2>$null | Out-Null

    $welcomeTmp = Join-Path $env:TEMP ("pns_welcome_flow_{0}.xml" -f [Guid]::NewGuid().ToString("N"))
    @'
<?xml version="1.0" encoding="utf-8" standalone="yes" ?>
<map>
  <int name="permission_onboarding_version" value="4" />
</map>
'@ | Set-Content -LiteralPath $welcomeTmp -Encoding UTF8
    try {
        Invoke-AdbCmd push $welcomeTmp /data/local/tmp/pns_welcome_flow.xml 2>$null | Out-Null
        Invoke-AdbCmd shell run-as dev.pointandshoot sh -c "mkdir -p shared_prefs && cp /data/local/tmp/pns_welcome_flow.xml shared_prefs/pns_welcome_flow.xml" 2>$null | Out-Null
    } catch {
        Write-Warning "[battery_life_test] welcome pref seed failed: $_"
    } finally {
        Remove-Item -LiteralPath $welcomeTmp -ErrorAction SilentlyContinue
    }

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = "hfr-runs\battery_life_test_$stamp"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Write-Host "=== PNS Battery Life Test (Sprint PO.2) ===" -ForegroundColor Cyan

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Start-Sleep -Milliseconds 600
    Invoke-AdbCmd logcat -c 2>$null

    Write-Host "Phase 1: adaptive FPS cap (simulated 15% battery, user 120 fps)..."
    Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
        --activity-clear-task `
        --es pns_screen preview `
        --ez pns_preview_primary_photo false `
        --ei pns_preview_video_fps 120 `
        --ei pns_preview_adaptive_battery_pct 15 2>&1 | Out-Null

    Start-Sleep -Seconds 8

    Write-Host "Phase 2: lifecycle pause (HOME) then resume..."
    Invoke-AdbCmd shell input keyevent KEYCODE_HOME 2>&1 | Out-Null
    Start-Sleep -Seconds 2
    Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview 2>&1 | Out-Null
    Start-Sleep -Seconds 4

    $logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
    $logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

    $adaptiveSeed = $logLines -match "preview adaptiveBatteryPctOverride=15"
    $adaptiveCap = $logLines -match "PNS\.PowerThermal:.*adaptiveFpsCap.*effective=60"
    $pauseLog = $logLines -match "longRunningPaused=true"
    $resumeLog = $logLines -match "longRunningPaused=false"
    $errorRegex = "CAMERA_DISCONNECTED|onError.*cameraId"
    $camErrorsObserved = $logLines -match $errorRegex
    $camErrorsBlocking = $false
    if ($camErrorsObserved) {
        $lineList = $logLines -split "`r?`n"
        $lastErr = -1
        for ($i = 0; $i -lt $lineList.Count; $i++) {
            if ($lineList[$i] -match $errorRegex) { $lastErr = $i }
        }
        $recovered = $false
        if ($lastErr -ge 0) {
            for ($j = $lastErr + 1; $j -lt $lineList.Count; $j++) {
                if ($lineList[$j] -match "status=Preview running") {
                    $recovered = $true
                    break
                }
            }
        }
        $camErrorsBlocking = -not $recovered
    }

    $overallPass = $adaptiveSeed -and $adaptiveCap -and $pauseLog -and $resumeLog -and -not $camErrorsBlocking

    $result = [ordered]@{
        timestamp    = $stamp
        passed       = $overallPass
        adaptiveSeed = [bool]$adaptiveSeed
        adaptiveCap  = [bool]$adaptiveCap
        pauseLog     = [bool]$pauseLog
        resumeLog    = [bool]$resumeLog
        cameraErrorsObserved = [bool]$camErrorsObserved
        cameraErrorsBlocking = [bool]$camErrorsBlocking
        artifactDir  = $outDir
        note         = "PO.2 gate validates policy + lifecycle wiring; not a 60-minute drain benchmark."
    }
    $result | ConvertTo-Json | Set-Content "$outDir\result.json" -Encoding UTF8

    if ($overallPass) {
        Write-Host "PASS — adaptive FPS + pause/resume logged" -ForegroundColor Green
    } else {
        Write-Host "FAIL — see $outDir\logcat.txt" -ForegroundColor Red
    }

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

    if (-not $overallPass) { exit 1 }
} finally {
    Pop-Location
}
