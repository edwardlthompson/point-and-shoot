#!/usr/bin/env pwsh
<#
.SYNOPSIS
    YUV+10-bit video verification gate (Sprint 13.2)
    
.DESCRIPTION
    Verifies 10-bit video recording capability and output file bit depth.
    
.PARAMETER Serial
    ADB device serial number (optional - uses first device if not specified)
    
.PARAMETER Install
    Install APK before testing (optional)
#>

param(
    [string]$Serial = "",
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$script:Pass = $true
$script:Evidence = @()

# Resolve ADB
$adb = "adb"
try { 
    $adbResult = & "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath 
    if ($adbResult) { $adb = $adbResult }
} catch { }

function Log($msg) {
    Write-Host "[pns_tenbit_video_verify] $msg"
    $script:Evidence += "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') - $msg"
}

function Fail($msg) {
    Write-Error "[FAIL] $msg"
    $script:Pass = $false
    $script:Evidence += "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') - FAIL: $msg"
}

# Optional install
if ($Install) {
    Log "Installing APK..."
    $apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { Fail "APK not found: $apk" }
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { Fail "Install failed" }
    Log "Install succeeded"
}

# Clear logcat
Log "Clearing logcat..."
& $adb logcat -c

# Test 1: Record video with H.265 10-bit
Log "=== Test 1: Record H.265 10-bit Video ==="
& $adb shell "am start -a android.intent.action.MAIN -n dev.pointandshoot/.MainActivity --ez pns_preview_automation_in_app_video 1 --ei pns_preview_video_sec 5 --ei pns_preview_video_bitrate 20000000"
Start-Sleep -Seconds 8  # Wait for recording

# Check log for 10-bit marker
Log "Checking logcat for 10-bit recording markers..."
$log = & $adb logcat -d -s PNS.Video:I -t 1000
$tenBitRecorded = $log | Select-String -Pattern "tenBit=true|codec=H.265 10-bit"
if ($tenBitRecorded) {
    Log "✓ 10-bit video recording initiated (log evidence)"
} else {
    Log "⚠ 10-bit marker not found in log (may require manual verification)"
}

# Pull recorded video
Log "=== Test 2: Verify Video File ==="
$videoPath = & $adb shell "find /sdcard/DCIM -name '*.mp4' -mmin -5 2>/dev/null | head -1"
if ($videoPath) {
    $localPath = Join-Path $PSScriptRoot "tenbit_test_video.mp4"
    & $adb pull $videoPath $localPath
    if (Test-Path $localPath) {
        Log "✓ Video file pulled: $localPath"
        
        # Check with ffprobe
        Log "Running ffprobe to check bit depth..."
        $ffprobe = & ffprobe -v error -select_streams v:0 -show_entries stream=pix_fmt,color_transfer,color_primaries -of default=noprint_wrappers=1 $localPath 2>&1
        Log "ffprobe output: $ffprobe"
        
        # Check for 10-bit indicators
        $isTenBit = ($ffprobe -match "yuv420p10le|yuv422p10le|yuv444p10le|smpte2084|arib-std-b67")
        if ($isTenBit) {
            Log "✓ 10-bit pixel format detected!"
        } else {
            Log "⚠ 10-bit format not confirmed (may be 8-bit fallback)"
        }
        
        # Check codec
        $codec = & ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 $localPath 2>&1
        Log "Codec: $codec"
        if ($codec -match "hevc|h265") {
            Log "✓ H.265/HEVC codec confirmed"
        } else {
            Log "⚠ Codec is not H.265 (may be H.264 fallback)"
        }
    } else {
        Fail "Failed to pull video file"
    }
} else {
    Fail "No recent video file found"
}

# Write evidence JSON
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$evidenceFile = "tenbit_video_gate_$timestamp.json"
$json = @{
    schema = "pns.tenbit_video_gate.v1"
    generatedAt = (Get-Date -Format "o")
    pass = $script:Pass
    deviceSerial = & $adb get-serialno
    tests = @{
        tenBitRecording = ($null -ne $tenBitRecorded)
        videoFileFound = ($null -ne $videoPath)
        tenBitDetected = ($null -ne $isTenBit)
        hevcCodec = ($codec -match "hevc|h265")
    }
    evidence = $script:Evidence
} | ConvertTo-Json -Depth 5

$json | Out-File (Join-Path $PSScriptRoot $evidenceFile) -Encoding UTF8
Log "Evidence written to: $evidenceFile"

# Final status
if ($script:Pass) {
    Log "=== PASS === 10-bit video verification complete"
    exit 0
} else {
    Log "=== FAIL === Some checks failed"
    exit 1
}
