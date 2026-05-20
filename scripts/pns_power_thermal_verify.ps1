#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13V.12 gate: power + thermal HUD on high-drain video preview.
#>
param(
    [int]$VideoFps = 120,
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
    $outDir = "hfr-runs\power_thermal_verify_$stamp"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Write-Host "=== PNS Power + Thermal Verify (Sprint 13V.12) ===" -ForegroundColor Cyan

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Start-Sleep -Milliseconds 600
    Invoke-AdbCmd logcat -c 2>$null

    Write-Host "Launch: video-primary fps=$VideoFps force_power_thermal..."
    Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview `
        --ez pns_preview_primary_photo false `
        --ei pns_preview_video_fps $VideoFps `
        --ez pns_preview_force_power_thermal true 2>&1 | Out-Null

    $waitSec = 12
    Write-Host "Waiting ${waitSec}s for power HUD samples..."
    Start-Sleep -Seconds $waitSec

    $logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
    $logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

    $forceSeed = $logLines -match "preview forcePowerThermalOverlay=true"
    $powerThermal = $logLines -match "PNS\.PowerThermal:.*battery=\d+.*highDrain=true"
    $camErrors = $logLines -match "CAMERA_DISCONNECTED|onError.*cameraId"

    $overallPass = $forceSeed -and $powerThermal -and -not $camErrors

    $result = [ordered]@{
        timestamp     = $stamp
        passed        = $overallPass
        videoFps      = $VideoFps
        forceSeed     = [bool]$forceSeed
        powerThermal  = [bool]$powerThermal
        cameraErrors  = [bool]$camErrors
        artifactDir   = $outDir
    }
    $result | ConvertTo-Json | Set-Content "$outDir\result.json" -Encoding UTF8

    if ($overallPass) {
        Write-Host "PASS — power/thermal HUD logged" -ForegroundColor Green
    } else {
        Write-Host "FAIL — see $outDir\logcat.txt" -ForegroundColor Red
    }

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

    if (-not $overallPass) { exit 1 }
} finally {
    Pop-Location
}
