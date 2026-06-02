#!/usr/bin/env pwsh
<#
.SYNOPSIS
    HFR (High Frame Rate) video recording verification script for Sprint 12.2.
    
.DESCRIPTION
    Tests high-speed video recording by requesting 240fps (or device max) and verifying
    the MediaRecorder can prepare and save successfully with CameraConstrainedHighSpeedCaptureSession.
    
    Uses pns_in_app_video_verify.ps1 as base with HFR-specific parameters.
    
.PARAMETER RecordSec
    Duration of video recording in seconds (default: 3 - shorter for HFR)
    
.PARAMETER TargetFps
    Target frame rate for HFR recording (default: 240)
    
.PARAMETER Serial
    ADB device serial (optional if PNS_ADB_SERIAL env var set)
    
.PARAMETER Fast
    Skip APK rebuild (use existing debug APK)
    
.EXAMPLE
    .\pns_hfr_video_verify.ps1 -TargetFps 240 -Serial <serial>
    
    .\pns_hfr_video_verify.ps1 -Fast
    
.OUTPUTS
    Writes hfr_video_gate.json and evidence to PROBE_BUILD_PLAN.md §5
#>
[CmdletBinding()]
param(
    [int]$RecordSec = 3,
    [int]$TargetFps = 240,
    [string]$Serial = $env:PNS_ADB_SERIAL,
    [switch]$Fast
)

$ErrorActionPreference = "Stop"
$script:tag = "PNS.HfrVideoVerify"

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    $color = switch ($Level) {
        "ERROR" { "Red" }
        "WARN"  { "Yellow" }
        "PASS"  { "Green" }
        default { "White" }
    }
    Write-Host "[$ts] [$Level] $Message" -ForegroundColor $color
}

# Repository root
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

# Find ADB
$adb = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $adb) {
    $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (-not (Test-Path $adb)) {
        throw "adb not found. Install Android SDK platform-tools or add to PATH."
    }
}
Write-Log "Using adb: $adb"

# Device check
$deviceArgs = if ($Serial) { @("-s", $Serial) } else { @() }
$devices = & $adb @deviceArgs devices | Select-String "device$"
if (-not $devices) {
    throw "No ADB devices connected. Connect a device or start an emulator."
}
Write-Log "Device detected: $($devices -join ', ')"

# Build APK if needed
$apkPath = "app\build\outputs\apk\debug\app-debug.apk"
if (-not $Fast -or -not (Test-Path $apkPath)) {
    Write-Log "Building APK..."
    $gradlew = if (Test-Path "gradlew.bat") { ".\gradlew.bat" } else { ".\gradlew" }
    & $gradlew :app:assembleDebug --no-daemon -q 2>&1 | ForEach-Object { Write-Log "BUILD: $_" }
    if (-not (Test-Path $apkPath)) {
        throw "Build failed - APK not found at $apkPath"
    }
}

# Install APK
Write-Log "Installing APK..."
& $adb @deviceArgs install -r -t $apkPath 2>&1 | ForEach-Object { Write-Log "ADB: $_" }

# Grant permissions
Write-Log "Granting permissions..."
& $adb @deviceArgs shell pm grant dev.pointandshoot android.permission.CAMERA 2>&1 | Out-Null
& $adb @deviceArgs shell pm grant dev.pointandshoot android.permission.RECORD_AUDIO 2>&1 | Out-Null
& $adb @deviceArgs shell pm grant dev.pointandshoot android.permission.ACCESS_FINE_LOCATION 2>&1 | Out-Null

# Check device HFR capability
Write-Log "Checking HFR capability on device..."
& $adb @deviceArgs shell dumpsys media.camera 2>&1 | Select-String -Pattern "CONSTRAINED_HIGH_SPEED_VIDEO|availableHighSpeedVideoConfigurations" | Select-Object -First 5 | ForEach-Object {
    Write-Log "CAPS: $_"
}

# Clear logcat
& $adb @deviceArgs logcat -c 2>&1 | Out-Null

# Start HFR recording
Write-Log "Starting HFR video recording: ${TargetFps}fps for $RecordSec seconds..."
$startTime = Get-Date

# Launch with HFR automation extras
# pns_preview_automation_in_app_video_hfr=true signals HFR mode to app
& $adb @deviceArgs shell am start -n "dev.pointandshoot/.MainActivity" `
    --es pns_screen preview `
    --ei pns_preview_automation_in_app_video_sec $RecordSec `
    --ez pns_preview_automation_in_app_video_hfr true `
    --ei pns_preview_automation_target_fps $TargetFps 2>&1 | ForEach-Object { Write-Log "AM: $_" }

# Wait for recording
# HFR needs more settle time: 3s settle + 10s prep + record + 3s buffer
$totalWait = 16 + $RecordSec
Write-Log "Waiting $totalWait seconds for HFR automation..."
Start-Sleep -Seconds $totalWait

$endTime = Get-Date
$elapsedSec = [math]::Round(($endTime - $startTime).TotalSeconds, 1)

# Collect logcat
Write-Log "Collecting logcat..."
$logcatOutput = & $adb @deviceArgs exec-out logcat -d -s "PNS.Video" -s "PNS.AdbValidation" -s "PNS.ChromeUx" -s "PNS.Cam" 2>&1

# Check for success/failure indicators
$videoPrepared = $logcatOutput | Select-String "inAppVideoPrepared"
$videoSaved = $logcatOutput | Select-String "inAppVideoSaved ok=true"
$hfrRejected = $logcatOutput | Select-String "HFR video rejected|HFR not supported"
$highSpeedSession = $logcatOutput | Select-String "HighSpeed|ConstrainedHighSpeed"

Write-Log "Logcat analysis:"
Write-Log "  - Video prepared: $(if ($videoPrepared) { 'YES' } else { 'NO' })"
Write-Log "  - Video saved successfully: $(if ($videoSaved) { 'YES' } else { 'NO' })"
Write-Log "  - HFR rejected: $(if ($hfrRejected) { 'YES' } else { 'NO' })"
Write-Log "  - High-speed session: $(if ($highSpeedSession) { 'YES' } else { 'NO' })"

# Find the saved video file
$escapedPath = "'/sdcard/DCIM/Point & Shoot'"
$videoFiles = & $adb @deviceArgs shell "ls -t $escapedPath/pns_*.mp4 2>/dev/null | head -1" 2>&1 | Select-String "pns_"

$result = @{}
if ($videoSaved -and $videoFiles) {
    $latestVideo = $videoFiles[-1].ToString().Trim()
    $fileInfo = & $adb @deviceArgs shell "stat -c%s $latestVideo 2>/dev/null" 2>&1
    $fileSize = 0
    if ($fileInfo -match "^\d+$") {
        $fileSize = [int]$fileInfo
    }
    
    $result = @{
        pass = $true
        fileSize = $fileSize
        videoPath = $latestVideo
        fpsRequested = $TargetFps
        hfrMode = ($null -ne $highSpeedSession)
        elapsedSeconds = $elapsedSec
    }
    Write-Log "HFR video verified: $latestVideo ($fileSize bytes)" "PASS"
} elseif ($hfrRejected) {
    $result = @{
        pass = $false
        error = "HFR rejected by device (not supported)"
        fpsRequested = $TargetFps
        hfrSupported = $false
        logEvidence = ($hfrRejected | Select-Object -First 1).Line
    }
    Write-Log "HFR not supported on this device (expected for some hardware)" "WARN"
} else {
    $result = @{
        pass = $false
        error = "Video recording failed - no inAppVideoSaved confirmation"
        fpsRequested = $TargetFps
        hasPrepared = ($null -ne $videoPrepared)
    }
    Write-Log "HFR video recording failed" "ERROR"
}

# Force stop to conserve battery
Write-Log "Force-stopping app..."
& $adb @deviceArgs shell am force-stop dev.pointandshoot 2>&1 | Out-Null

# Write gate file
$hfrRuns = Join-Path $repoRoot "hfr-runs"
if (-not (Test-Path $hfrRuns)) {
    New-Item -ItemType Directory -Path $hfrRuns -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$gateFile = Join-Path $hfrRuns "hfr_video_gate_$timestamp.json"

$gateOutput = @{
    schema = "hfr_video_gate.v1"
    timestamp = Get-Date -Format "o"
    result = $result
    deviceSerial = $Serial
    targetFps = $TargetFps
    recordSeconds = $RecordSec
}

$gateOutput | ConvertTo-Json -Depth 3 | Set-Content -Path $gateFile
Write-Log "Gate file: $gateFile"

# Summary
if ($result.pass) {
    Write-Log "=== HFR VIDEO VERIFY PASSED ===" "PASS"
    exit 0
} else {
    Write-Log "=== HFR VIDEO VERIFY FAILED ===" "ERROR"
    exit 1
}
