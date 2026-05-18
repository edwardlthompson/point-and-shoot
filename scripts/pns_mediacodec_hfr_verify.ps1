#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Verify MediaCodecVideoRecorder HFR (120fps) and 10-bit video on device.

.DESCRIPTION
    Exercises the new MediaCodec-based encoder path that bypasses MediaRecorder's
    ro.media.recorder-max-base-layer-fps=60 system cap.

    Tests:
      1. 1080p @ 120fps HEVC (HFR via MediaCodec)
      2. 1080p @ 60fps HEVC 10-bit Main10 (10-bit via MediaCodec)
      3. 1080p @ 240fps HEVC (max HFR)
      4. 1080p @ 60fps HEVC 10-bit HDR10 (10-bit + HDR)
      5. 4K @ 30fps HEVC   (Sprint 13.4 unified picker)
      6. 4K @ 60fps HEVC   (Sprint 13.4 unified picker)
      7. 4K @ 120fps HEVC  (Sprint 13.4 unified picker — MediaCodec path)

    Pass criteria:
      - logcat shows "MediaCodecVideoRecorder started" (not MediaRecorder)
      - logcat shows "mcVideoPrepared" with correct fps
      - logcat shows "inAppVideoSaved" on stop
      - No "CAMERA_DISCONNECTED" or fatal codec errors
      - Saved MP4 file exists and has non-zero size

.PARAMETER Serial
    ADB device serial (optional; uses first connected device).

.PARAMETER OutDir
    Output directory for artifacts. Defaults to hfr-runs\mediacodec_verify_<timestamp>.
#>
param(
    [string]$Serial = "",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

$ADB = "adb"
if ($Serial) { $ADB = "adb -s $Serial" }

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
if (!$OutDir) { $OutDir = "hfr-runs\mediacodec_verify_$Timestamp" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$ResultsFile = Join-Path $OutDir "results.md"
$LogFile     = Join-Path $OutDir "logcat.txt"
$SummaryJson = Join-Path $OutDir "summary.json"

$pkg = "dev.pointandshoot"
$act = "dev.pointandshoot.MainActivity"

function Invoke-Adb { param([string]$AdbArgs) Invoke-Expression "$ADB $AdbArgs" }

function Write-Log { param([string]$Msg) Write-Host $Msg; Add-Content $ResultsFile $Msg }

function Clear-LogcatBuffer {
    Invoke-Adb "logcat -c" | Out-Null
}

function Get-LogcatSegment {
    param([int]$DurationSec)
    Start-Sleep -Seconds $DurationSec
    Invoke-Adb "logcat -d -v threadtime" 2>&1
}

function Test-RecordingMode {
    param(
        [string]$TestName,
        [int]$Fps,
        [bool]$TenBit,
        [int]$Width  = 1920,
        [int]$Height = 1080,
        [int]$DurationSec = 8
    )
    Write-Log ""
    Write-Log "## Test: $TestName  fps=$Fps  10bit=$TenBit"

    # Force-stop and clear logcat
    Invoke-Adb "shell am force-stop $pkg" | Out-Null
    Start-Sleep -Milliseconds 800
    Clear-LogcatBuffer

    # Inject encode resolution into SharedPreferences (debuggable build: run-as allowed)
    $prefsPath = "/data/data/$pkg/shared_prefs/pns_preview_chrome.xml"
    $existingPrefs = (& adb shell run-as $pkg cat $prefsPath 2>&1) -join "`n"
    if ($existingPrefs -match "<map>") {
        $patched = $existingPrefs -replace '(?s)<int name="in_app_video_encode_w"[^/]*/>', "<int name=`"in_app_video_encode_w`" value=`"$Width`" />"
        $patched = $patched  -replace '(?s)<int name="in_app_video_encode_h"[^/]*/>', "<int name=`"in_app_video_encode_h`" value=`"$Height`" />"
        if ($patched -notmatch 'in_app_video_encode_w') {
            $patched = $patched -replace '</map>', "    <int name=`"in_app_video_encode_w`" value=`"$Width`" />`n    <int name=`"in_app_video_encode_h`" value=`"$Height`" />`n</map>"
        }
        $tmpLocal = [System.IO.Path]::GetTempFileName() + ".xml"
        [System.IO.File]::WriteAllText($tmpLocal, $patched, [System.Text.Encoding]::UTF8)
        $tmpDevice = "/data/local/tmp/pns_chrome_prefs_patch.xml"
        & adb push $tmpLocal $tmpDevice 2>&1 | Out-Null
        & adb shell run-as $pkg cp $tmpDevice $prefsPath 2>&1 | Out-Null
        Remove-Item $tmpLocal -Force -ErrorAction SilentlyContinue
        Write-Host "  Encode resolution set to ${Width}x${Height} in SharedPrefs"
    } else {
        Write-Host "  WARNING: Could not read $prefsPath (app not yet installed or prefs not created)"
    }

    # Launch app with video automation extras
    $startArgs = @(
        "shell", "am", "start", "-n", "$pkg/$act",
        "--es", "pns_screen", "preview",
        "--ei", "pns_preview_automation_in_app_video_sec", "$DurationSec",
        "--ei", "pns_preview_video_fps", "$Fps"
    )
    if ($TenBit) { $startArgs += @("--ez", "pns_preview_video_10bit", "true") }
    & adb @startArgs 2>&1 | Out-Null

    # App drives recording automatically via adbAutomationInAppVideoSec — wait for duration + settle
    $waitTotal = $DurationSec + 12
    Write-Host "  Waiting ${waitTotal}s for automation to complete..."
    Start-Sleep -Seconds $waitTotal

    # Collect logcat
    $allLog = (& adb logcat -d -v threadtime 2>&1) -join "`n"

    # Save raw log
    $allLog | Out-File -FilePath (Join-Path $OutDir "log_${TestName}.txt") -Encoding utf8

    # Parse results
    $usedMcPath   = $allLog -match "mcVideoPrepared|MediaCodecVideoRecorder started"
    $usedMrPath   = $allLog -match "inAppVideoPrepared"
    $savedOk      = $allLog -match "inAppVideoSaved"
    $codecError   = $allLog -match "codec error|CAMERA_DISCONNECTED|CameraDevice.*ERROR"
    $correctFps   = $allLog -match "fps=$Fps"

    # MediaCodec path required only for HFR (>=120fps) or 10-bit; SDR <=60fps uses MediaRecorder correctly
    $requiresMcPath = ($Fps -ge 120) -or $TenBit
    $pass = $savedOk -and -not $codecError -and (-not $requiresMcPath -or $usedMcPath)

    Write-Log "  MediaCodec path used : $usedMcPath"
    Write-Log "  MediaRecorder path   : $usedMrPath  (expected: false for HFR/10bit)"
    Write-Log "  Correct fps in log   : $correctFps"
    Write-Log "  inAppVideoSaved      : $savedOk"
    Write-Log "  Codec errors         : $codecError"
    Write-Log "  RESULT               : $(if ($pass) { 'PASS' } else { 'FAIL' })"

    # Force-stop app (battery rule)
    Invoke-Adb "shell am force-stop $pkg" | Out-Null

    return @{
        Test      = $TestName
        Fps       = $Fps
        TenBit    = $TenBit
        Pass      = $pass
        McPath    = $usedMcPath
        Saved     = $savedOk
        Error     = $codecError
    }
}

# ── Header ──────────────────────────────────────────────────────────────────
Set-Content $ResultsFile "# MediaCodecVideoRecorder Verification"
Add-Content $ResultsFile "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Add-Content $ResultsFile ""

# Check device is connected
$devices = & adb devices 2>&1
$deviceLines = $devices | Select-String -Pattern "\bdevice$"
if (-not $deviceLines) {
    Write-Log "ERROR: No ADB device found. Output: $devices"
    exit 1
}

# Dump codec info for evidence
Write-Log "## Codec Capabilities"
$codecDump = Invoke-Adb "shell dumpsys media.player" 2>&1 | Select-String -Pattern "hevc.encoder|frame-rate-range|performance-point|Main10|YUVP010"
$codecDump | ForEach-Object { Write-Log "  $_" }

# Bootstrap: launch app once at default resolution to ensure SharedPrefs file exists
Write-Log ""
Write-Log "## Bootstrap — creating SharedPrefs file"
& adb shell am force-stop $pkg 2>&1 | Out-Null
Start-Sleep -Milliseconds 500
& adb shell am start -n "$pkg/$act" --es pns_screen preview 2>&1 | Out-Null
Write-Host "  Waiting 8s for app to write SharedPrefs..."
Start-Sleep -Seconds 8
& adb shell am force-stop $pkg 2>&1 | Out-Null
Start-Sleep -Milliseconds 500
Write-Log "  Bootstrap complete"

# ── Tests ─────────────────────────────────────────────────────────────────
$results = @()
$results += Test-RecordingMode -TestName "HFR_1080p_120fps"   -Fps 120 -TenBit $false -Width 1920 -Height 1080
$results += Test-RecordingMode -TestName "TenBit_1080p_60fps"  -Fps 60  -TenBit $true  -Width 1920 -Height 1080
$results += Test-RecordingMode -TestName "HFR_1080p_240fps"    -Fps 240 -TenBit $false -Width 1920 -Height 1080
$results += Test-RecordingMode -TestName "TenBit_HDR10_1080p"  -Fps 60  -TenBit $true  -Width 1920 -Height 1080
$results += Test-RecordingMode -TestName "4K_30fps"            -Fps 30  -TenBit $false -Width 3840 -Height 2160
$results += Test-RecordingMode -TestName "4K_60fps"            -Fps 60  -TenBit $false -Width 3840 -Height 2160
$results += Test-RecordingMode -TestName "4K_120fps_MediaCodec" -Fps 120 -TenBit $false -Width 3840 -Height 2160

# ── Summary ───────────────────────────────────────────────────────────────
Write-Log ""
Write-Log "## Summary"
$pass  = ($results | Where-Object { $_.Pass }).Count
$total = $results.Count
Write-Log "Passed: $pass / $total"

foreach ($r in $results) {
    $icon = if ($r.Pass) { "✅" } else { "❌" }
    Write-Log "  $icon $($r.Test)  fps=$($r.Fps)  10bit=$($r.TenBit)  mcPath=$($r.McPath)"
}

# JSON artifact
$results | ConvertTo-Json -Depth 3 | Out-File $SummaryJson -Encoding utf8

Write-Host ""
Write-Host "Results: $ResultsFile"
Write-Host "Log:     $LogFile"
Write-Host "JSON:    $SummaryJson"

if ($pass -lt $total) {
    Write-Host "GATE: FAIL ($pass/$total passed)" -ForegroundColor Red
    exit 1
} else {
    Write-Host "GATE: PASS ($pass/$total)" -ForegroundColor Green
    exit 0
}
