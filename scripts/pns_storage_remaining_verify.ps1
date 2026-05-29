#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13V.13 gate: storage-remaining overlay + estimate math spot-check.
#>
param(
    [long]$StorageAvailableBytes = 600000000,
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
    $outDir = "hfr-runs\storage_remaining_verify_$stamp"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Write-Host "=== PNS Storage Remaining Verify (Sprint 13V.13) ===" -ForegroundColor Cyan

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Start-Sleep -Milliseconds 600
    Invoke-AdbCmd logcat -c 2>$null

    Write-Host "Launch: video-primary record + avail=$StorageAvailableBytes fps=$VideoFps..."
    $recSec = 6
    Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
        --activity-clear-task `
        --es pns_screen preview `
        --ez pns_preview_primary_photo false `
        --ei pns_preview_video_fps $VideoFps `
        --ei pns_preview_automation_in_app_video_sec $recSec `
        --el pns_preview_storage_available_bytes $StorageAvailableBytes 2>&1 | Out-Null

    $waitSec = [Math]::Max(8, $recSec + 4)
    Write-Host "Waiting ${waitSec}s..."
    Start-Sleep -Seconds $waitSec

    $logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
    $logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

    $seed = $logLines -match "storageAvailableBytesOverride=$StorageAvailableBytes"
    $storageLog = [regex]::Match(
        $logLines,
        'PNS\.StorageRemain:.*minutes=([\d.]+).*bytesPerSec=(\d+).*avail=(\d+)'
    )
    $camErrors = $logLines -match "CAMERA_DISCONNECTED|onError.*cameraId"

    $mathOk = $false
    $minutes = $null
    $bytesPerSec = $null
    $avail = $null
    if ($storageLog.Success) {
        $minutes = [double]$storageLog.Groups[1].Value
        $bytesPerSec = [long]$storageLog.Groups[2].Value
        $avail = [long]$storageLog.Groups[3].Value
        if ($bytesPerSec -gt 0) {
            $expected = $avail / $bytesPerSec / 60.0
            $mathOk = [math]::Abs($minutes - $expected) -lt 1.0
        }
    }

    $overallPass = $seed -and $storageLog.Success -and $mathOk -and -not $camErrors

    $result = [ordered]@{
        timestamp            = $stamp
        passed               = $overallPass
        storageAvailableBytes = $StorageAvailableBytes
        seed                 = [bool]$seed
        storageLog           = $storageLog.Success
        mathOk               = $mathOk
        minutes              = $minutes
        bytesPerSec          = $bytesPerSec
        avail                = $avail
        cameraErrors         = [bool]$camErrors
        artifactDir          = $outDir
    }
    $result | ConvertTo-Json | Set-Content "$outDir\result.json" -Encoding UTF8

    if ($overallPass) {
        Write-Host "PASS — storage estimate logged (minutes=$minutes mathOk=$mathOk)" -ForegroundColor Green
    } else {
        Write-Host "FAIL — see $outDir\logcat.txt" -ForegroundColor Red
    }

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

    if (-not $overallPass) { exit 1 }
} finally {
    Pop-Location
}
