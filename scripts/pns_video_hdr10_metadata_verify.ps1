# pns_video_hdr10_metadata_verify.ps1
# Sprint 13.5 (extended) gate: record a 10-bit HDR10 video via ADB automation, pull the
# file, run ffprobe, and assert correct HDR10 metadata (color space, transfer, MaxCLL/MaxFALL).
#
# Prerequisites: ffprobe must be on PATH (part of ffmpeg installation).
#
# Usage:
#   .\scripts\pns_video_hdr10_metadata_verify.ps1
#   .\scripts\pns_video_hdr10_metadata_verify.ps1 -Serial "R5CX123456"

param(
    [string]$Serial = "",
    [int]$RecordSec = 8,
    [int]$WaitSec = 30
)

$ErrorActionPreference = "Stop"
$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = "hfr-runs\hdr10_meta_verify_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$adb = "adb"
$adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }

function Invoke-AdbCmd { & $adb @adbArgs @args }

Write-Host "=== PNS HDR10 Metadata Verify (Sprint 13.5 extended) ===" -ForegroundColor Cyan

# Check ffprobe
if (-not (Get-Command "ffprobe" -ErrorAction SilentlyContinue)) {
    Write-Warning "ffprobe not found on PATH. Skipping ffprobe analysis."
    $hasFfprobe = $false
} else {
    $hasFfprobe = $true
}

# Check device
$devices = & $adb devices 2>&1 | Select-String "device$"
if (-not $devices) { Write-Error "No ADB device connected." }

# Force-stop, clear logcat, and remove old DCIM clips before test
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
Start-Sleep -Milliseconds 600
Invoke-AdbCmd logcat -c 2>$null
Invoke-AdbCmd shell "rm -f '/sdcard/DCIM/Point and Shoot/'*.mp4" 2>$null

Write-Host "Launching app with DCG (HEVC Main10HDR10 + isHdr10 SEI) @ 60fps for ${RecordSec}s..."
Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
    --es pns_screen preview `
    --ei pns_preview_automation_in_app_video_sec $RecordSec `
    --ei pns_preview_video_fps 60 `
    --ez pns_preview_video_dcg true 2>&1 | Out-Null

$totalWait = $RecordSec + $WaitSec
Write-Host "Waiting ${totalWait}s for recording to complete..."
Start-Sleep -Seconds $totalWait

$logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
$logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

$videoSaved  = $logLines -match "inAppVideoSaved"
$mcPathUsed  = $logLines -match "mcVideoPrepared|MediaCodecVideoRecorder started"
$hdr10Config = $logLines -match "hdrProfile=4096|hdr-static-info=java\.nio|isHdr10=true"
$codecErrors = $logLines -match "codec error|CAMERA_DISCONNECTED"

Write-Host "  MediaCodec path : $mcPathUsed"
Write-Host "  HDR10 config    : $hdr10Config"
Write-Host "  Video saved     : $videoSaved"
Write-Host "  Codec errors    : $codecErrors"

$ffprobePass = $false
$colorSpace = ""
$colorTransfer = ""
$colorRange = ""
$maxCll = ""
$maxFall = ""

if ($videoSaved -and $hasFfprobe) {
    Write-Host "Pulling video file from device..."
    $pullDir = "$outDir\pulled"
    New-Item -ItemType Directory -Force -Path $pullDir | Out-Null
    # Get newest PNS mp4 from DCIM via ls (sorted ascending; take last entry)
    $dcimPath = "/storage/emulated/0/DCIM/Point & Shoot"
    $lsOut = (Invoke-AdbCmd shell "ls '$dcimPath/'" 2>&1) | Where-Object { $_ -match "pns_.*\.mp4" } | Select-Object -Last 1
    $newestFile = if ($lsOut) { ([string]$lsOut).Trim() } else { $null }
    if ($newestFile) {
        $realPath = "$dcimPath/$newestFile"
        Write-Host "  Pulling: $realPath"
        Invoke-AdbCmd pull $realPath "$pullDir/$newestFile" 2>&1 | Out-Null
    } else {
        Write-Host "  WARNING: No PNS mp4 found in DCIM"
    }
    $mp4Files = Get-ChildItem "$pullDir" -Recurse -Filter "*.mp4" | Sort-Object LastWriteTime -Descending
    if ($mp4Files.Count -gt 0) {
        $mp4 = $mp4Files[0].FullName
        Write-Host "Analysing: $($mp4Files[0].Name)"
        $ffprobeJson = & ffprobe -v quiet -print_format json -show_streams -show_frames -read_intervals "%+#1" $mp4 2>&1
        $ffprobeJson | Set-Content "$outDir\ffprobe.json" -Encoding UTF8
        $parsed = $ffprobeJson | ConvertFrom-Json -ErrorAction SilentlyContinue
        if ($parsed) {
            $videoStream = $parsed.streams | Where-Object { $_.codec_type -eq "video" } | Select-Object -First 1
            if ($videoStream) {
                $colorSpace = $videoStream.color_space
                $colorTransfer = $videoStream.color_transfer
                $colorRange = $videoStream.color_range
                Write-Host "  color_space    : $colorSpace"
                Write-Host "  color_transfer : $colorTransfer"
                Write-Host "  color_range    : $colorRange"
                $sideData = $videoStream.side_data_list
                if ($sideData) {
                    $cll = $sideData | Where-Object { $_.side_data_type -match "Content light level" }
                    if ($cll) {
                        $maxCll = $cll.max_content
                        $maxFall = $cll.max_average
                        Write-Host "  MaxCLL         : $maxCll nits"
                        Write-Host "  MaxFALL        : $maxFall nits"
                    }
                }
                $ffprobePass = ($colorSpace -match "bt2020") -and
                               ($colorTransfer -eq "smpte2084") -and
                               ($maxCll -ne "" -and [int]$maxCll -gt 0)
            }
        }
    } else {
        Write-Warning "No MP4 files found in pulled DCIM"
    }
} elseif (-not $hasFfprobe) {
    Write-Warning "Skipping ffprobe check (not installed)"
    $ffprobePass = $videoSaved
}

Write-Host ""
$overallPass = $videoSaved -and $mcPathUsed -and -not $codecErrors -and $ffprobePass
$result = [ordered]@{
    timestamp     = $stamp
    passed        = $overallPass
    videoSaved    = $videoSaved
    mcPathUsed    = $mcPathUsed
    hdr10Config   = $hdr10Config
    codecErrors   = $codecErrors
    ffprobePass   = $ffprobePass
    colorSpace    = $colorSpace
    colorTransfer = $colorTransfer
    colorRange    = $colorRange
    maxCll        = $maxCll
    maxFall       = $maxFall
}
$result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

if ($overallPass) {
    Write-Host "GATE: PASS" -ForegroundColor Green
} else {
    Write-Host "GATE: FAIL" -ForegroundColor Red
    if (-not $videoSaved) { Write-Host "  FAIL: video not saved" -ForegroundColor Red }
    if ($codecErrors) { Write-Host "  FAIL: codec errors" -ForegroundColor Red }
    if (-not $mcPathUsed)   { Write-Host "  FAIL: MediaCodec path not used" -ForegroundColor Red }
    if (-not $ffprobePass) { Write-Host "  FAIL: ffprobe HDR10 metadata missing/wrong (need bt2020 + smpte2084 + MaxCLL>0)" -ForegroundColor Red }
}

Write-Host "Artifacts: $outDir"

# Force-stop app
Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

if (-not $overallPass) { exit 1 }
