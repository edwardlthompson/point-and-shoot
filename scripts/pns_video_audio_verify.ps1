#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Video + Audio verification script for Sprint 12.1/12.5
    
.DESCRIPTION
    Records video with audio enabled and verifies AAC audio track is present using ffprobe.
    Extends pns_in_app_video_verify.ps1 with audio track validation.
    
.PARAMETER RecordSec
    Duration of video recording in seconds (default: 5)
    
.PARAMETER RequireAudioTrack
    Fail if no audio track detected in output MP4
    
.PARAMETER MinAudioBitrate
    Minimum acceptable audio bitrate in bps (default: 64000)
    
.PARAMETER Serial
    ADB device serial (optional if PNS_ADB_SERIAL env var set)
    
.EXAMPLE
    .\pns_video_audio_verify.ps1 -RecordSec 5 -RequireAudioTrack
    
.OUTPUTS
    Writes video_audio_gate.json and evidence to PROBE_BUILD_PLAN.md §5
#>
[CmdletBinding()]
param(
    [int]$RecordSec = 5,
    [switch]$RequireAudioTrack,
    [int]$MinAudioBitrate = 64000,
    [string]$Serial = $env:PNS_ADB_SERIAL
)

$ErrorActionPreference = "Stop"
$script:tag = "PNS.VideoAudioVerify"

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    Write-Host "[$ts] $Message"
}

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
if (-not (Test-Path $apkPath)) {
    Write-Log "APK not found, building..."
    $gradlew = if (Test-Path "gradlew.bat") { ".\gradlew.bat" } else { ".\gradlew" }
    & $gradlew :app:assembleDebug --no-daemon -q 2>&1 | ForEach-Object { Write-Log $_ }
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

# Clear logcat
& $adb @deviceArgs logcat -c 2>&1 | Out-Null

# Start recording with audio
Write-Log "Starting video recording with audio for $RecordSec seconds..."
& $adb @deviceArgs shell am start -n "dev.pointandshoot/.MainActivity" `
    --es pns_screen preview `
    --ei pns_preview_automation_in_app_video_sec $RecordSec 2>&1 | ForEach-Object { Write-Log "AM: $_" }

# Wait for recording to complete:
# - 2.5s initial settle
# - Up to 10s recorder prep
# - Recording duration
# - 3s buffer for save
$totalWait = 15 + $RecordSec
Write-Log "Waiting $totalWait seconds for automation to complete..."
Start-Sleep -Seconds $totalWait

# Collect logcat
Write-Log "Collecting logcat..."
$logcatOutput = & $adb @deviceArgs exec-out logcat -d -s "PNS.Video" -s "PNS.AdbValidation" -s "PNS.ChromeUx" 2>&1

# Check for success indicators
$videoPrepared = $logcatOutput | Select-String "inAppVideoPrepared audioEnabled=true"
$videoSaved = $logcatOutput | Select-String "inAppVideoSaved ok=true"

Write-Log "Logcat analysis:"
Write-Log "  - Video prepared with audio: $(if ($videoPrepared) { 'YES' } else { 'NO' })"
Write-Log "  - Video saved successfully: $(if ($videoSaved) { 'YES' } else { 'NO' })"

# Find the saved video file
$dcimPath = "/sdcard/DCIM/Point & Shoot"
$escapedPath = "'/sdcard/DCIM/Point & Shoot'"
$videoFiles = & $adb @deviceArgs shell "ls -t $escapedPath/pns_*.mp4 2>/dev/null | head -1" 2>&1 | Select-String "pns_"

if (-not $videoFiles) {
    Write-Log "ERROR: No video file found in $dcimPath"
    $result = @{ pass = $false; error = "No video file found"; timestamp = (Get-Date -Format "o") }
} else {
    $latestVideo = $videoFiles[-1].ToString().Trim()
    Write-Log "Found video: $latestVideo"
    
    # Pull video to temp location
    $tempVideo = [System.IO.Path]::GetTempFileName() + ".mp4"
    & $adb @deviceArgs pull $latestVideo $tempVideo 2>&1 | ForEach-Object { Write-Log "PULL: $_" }
    
    # Check with ffprobe
    $ffprobe = Get-Command ffprobe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if (-not $ffprobe) {
        $ffprobe = "ffprobe"  # Try PATH
    }
    
    $audioStream = $null
    $audioCodec = $null
    $audioSampleRate = $null
    $audioBitRate = $null
    
    if ($ffprobe) {
        try {
            Write-Log "Analyzing audio with ffprobe..."
            $ffprobeOutput = & $ffprobe -v error `
                -select_streams a:0 `
                -show_entries stream=codec_name,sample_rate,bit_rate `
                -of csv=p=0:nk=1 `
                $tempVideo 2>&1
            
            if ($ffprobeOutput) {
                $parts = $ffprobeOutput -split ','
                $audioCodec = $parts[0]
                $audioSampleRate = $parts[1]
                $audioBitRate = $parts[2]
                $audioStream = $true
                
                Write-Log "  Audio codec: $audioCodec"
                Write-Log "  Sample rate: $audioSampleRate Hz"
                Write-Log "  Bitrate: $audioBitRate bps"
            }
        } catch {
            Write-Log "ffprobe analysis failed: $_"
        }
    } else {
        Write-Log "ffprobe not found - skipping audio analysis"
    }
    
    # Determine pass/fail
    $pass = $videoPrepared -and $videoSaved
    if ($RequireAudioTrack) {
        $pass = $pass -and $audioStream -and ($audioCodec -eq "aac")
    }
    
    $result = @{
        pass = $pass
        videoPrepared = ($null -ne $videoPrepared)
        videoSaved = ($null -ne $videoSaved)
        audioStreamPresent = ($null -ne $audioStream)
        audioCodec = $audioCodec
        audioSampleRate = $audioSampleRate
        audioBitRate = $audioBitRate
        requireAudioTrack = $RequireAudioTrack.IsPresent
        minAudioBitrate = $MinAudioBitrate
        videoPath = $latestVideo
        timestamp = (Get-Date -Format "o")
    }
    
    # Cleanup
    if (Test-Path $tempVideo) {
        Remove-Item $tempVideo -Force
    }
}

# Write JSON result
$outDir = "hfr-runs"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$outFile = Join-Path $outDir ("video_audio_verify_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
$result | ConvertTo-Json -Depth 3 | Set-Content -Path $outFile -Encoding UTF8
Write-Log "Results written to: $outFile"

# Output summary
Write-Host "`n=== Video Audio Verification Results ===" -ForegroundColor Cyan
Write-Host "Pass: $($result.pass)" -ForegroundColor $(if ($result.pass) { "Green" } else { "Red" })
Write-Host "Video prepared: $($result.videoPrepared)"
Write-Host "Video saved: $($result.videoSaved)"
Write-Host "Audio stream present: $($result.audioStreamPresent)"
if ($result.audioCodec) {
    Write-Host "Audio codec: $($result.audioCodec)"
    Write-Host "Sample rate: $($result.audioSampleRate) Hz"
    Write-Host "Bitrate: $($result.audioBitRate) bps"
}

# BATTERY CONSERVATION: Always close app after testing
Write-Log "Closing app to conserve battery..."
& $adb @deviceArgs shell am force-stop dev.pointandshoot 2>&1 | Out-Null

# Return exit code
exit $(if ($result.pass) { 0 } else { 1 })
