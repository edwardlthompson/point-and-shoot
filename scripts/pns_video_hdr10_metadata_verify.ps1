# pns_video_hdr10_metadata_verify.ps1
# Sprint 13.4 / 13.5 — DCG session + HDR10 in-app video gate.
#
# Prerequisites: ffprobe on PATH (ffmpeg). USB device with DCIM write access.
#
# Usage:
#   .\scripts\pns_video_hdr10_metadata_verify.ps1 -Serial 8bf09993

param(
    [string]$Serial = "",
    [int]$RecordSec = 8,
    [int]$WaitSec = 30
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") {
            return $t.Substring($eq + 1).Trim()
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $projRoot "hfr-runs\hdr10_meta_verify_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$adb = "adb"
$adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }

function Invoke-AdbCmd { & $adb @adbArgs @args }

Write-Host "=== PNS HDR10 + DCG session verify (13.4) ===" -ForegroundColor Cyan
Write-Host "Artifacts: $outDir"

if (-not (Get-Command "ffprobe" -ErrorAction SilentlyContinue)) {
    Write-Warning "ffprobe not found on PATH. Skipping ffprobe analysis."
    $hasFfprobe = $false
} else {
    $hasFfprobe = $true
}

$devices = & $adb devices 2>&1 | Select-String "device$"
if (-not $devices) { Write-Error "No ADB device connected." }

$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    Write-Host "Building debug APK..."
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug" | Out-Host
}
if (Test-Path -LiteralPath $apk) {
    Write-Host "Installing $apk..."
    Invoke-AdbCmd install -r -t $apk 2>&1 | Out-Null
}

Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
Start-Sleep -Milliseconds 600
Invoke-AdbCmd logcat -c 2>$null
Invoke-AdbCmd shell "rm -f '/sdcard/DCIM/Point and Shoot/'*.mp4" 2>$null

Write-Host "Launching preview: DCG (pns_preview_video_dcg) @ 60fps, ${RecordSec}s record..."
Invoke-AdbCmd shell am start -W -n "dev.pointandshoot/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_primary_photo false `
    --ei pns_preview_automation_in_app_video_sec $RecordSec `
    --ei pns_preview_video_fps 60 `
    --ez pns_preview_video_dcg true 2>&1 | Out-Null

$totalWait = $RecordSec + $WaitSec
Write-Host "Waiting ${totalWait}s..."
Start-Sleep -Seconds $totalWait

$logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
$logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

$dcgSession = $logLines -match "dcgSessionTemplate=EnableHDRDCGMode"
$inAppVideoDcg = $logLines -match "inAppVideoFormat=DCG"
$videoSaved = $logLines -match "inAppVideoSaved"
$mcPathUsed = $logLines -match "mcVideoPrepared|MediaCodecVideoRecorder started"
$hdr10Config = $logLines -match "hdrProfile=4096|hdr-static-info=java\.nio|isHdr10=true"
$codecErrors = $logLines -match "codec error|CAMERA_DISCONNECTED"

Write-Host "  DCG session template : $dcgSession"
Write-Host "  inAppVideoFormat=DCG : $inAppVideoDcg"
Write-Host "  MediaCodec path      : $mcPathUsed"
Write-Host "  HDR10 config         : $hdr10Config"
Write-Host "  Video saved          : $videoSaved"
Write-Host "  Codec errors         : $codecErrors"

$ffprobePass = $false
$colorSpace = ""
$colorTransfer = ""
$maxCll = ""

if ($videoSaved -and $hasFfprobe) {
    Write-Host "Pulling video file from device..."
    $pullDir = "$outDir\pulled"
    New-Item -ItemType Directory -Force -Path $pullDir | Out-Null
    $dcimPath = "/storage/emulated/0/DCIM/Point & Shoot"
    $lsOut = (Invoke-AdbCmd shell "ls '$dcimPath/'" 2>&1) | Where-Object { $_ -match "pns_.*\.mp4" } | Select-Object -Last 1
    $newestFile = if ($lsOut) { ([string]$lsOut).Trim() } else { $null }
    if ($newestFile) {
        $realPath = "$dcimPath/$newestFile"
        Write-Host "  Pulling: $realPath"
        Invoke-AdbCmd pull $realPath "$pullDir/$newestFile" 2>&1 | Out-Null
    }
    $mp4Files = Get-ChildItem "$pullDir" -Recurse -Filter "*.mp4" | Sort-Object LastWriteTime -Descending
    if ($mp4Files.Count -gt 0) {
        $mp4 = $mp4Files[0].FullName
        Write-Host "Analysing: $($mp4Files[0].Name)"
        $ffprobeJson = & ffprobe -v quiet -print_format json -show_streams -read_intervals "%+#1" $mp4 2>&1
        $ffprobeJson | Set-Content "$outDir\ffprobe.json" -Encoding UTF8
        $parsed = $ffprobeJson | ConvertFrom-Json -ErrorAction SilentlyContinue
        if ($parsed) {
            $videoStream = $parsed.streams | Where-Object { $_.codec_type -eq "video" } | Select-Object -First 1
            if ($videoStream) {
                $colorSpace = $videoStream.color_space
                $colorTransfer = $videoStream.color_transfer
                Write-Host "  color_space    : $colorSpace"
                Write-Host "  color_transfer : $colorTransfer"
                $sideData = $videoStream.side_data_list
                if ($sideData) {
                    $cll = $sideData | Where-Object { $_.side_data_type -match "Content light level" }
                    if ($cll) {
                        $maxCll = $cll.max_content
                        Write-Host "  MaxCLL         : $maxCll nits"
                    }
                }
                $ffprobePass = ($colorSpace -match "bt2020") -and
                    ($colorTransfer -eq "smpte2084") -and
                    ($maxCll -ne "" -and [int]$maxCll -gt 0)
            }
        }
    }
} elseif (-not $hasFfprobe) {
    Write-Warning "Skipping ffprobe check (not installed)"
    $ffprobePass = $videoSaved
}

$overallPass = $dcgSession -and $inAppVideoDcg -and $videoSaved -and $mcPathUsed -and -not $codecErrors -and $ffprobePass
$result = [ordered]@{
    schema = "hdr10_dcg_verify.v1"
    sprint = "13.4"
    timestamp = $stamp
    passed = $overallPass
    dcgSessionTemplate = [bool]$dcgSession
    inAppVideoFormatDcg = [bool]$inAppVideoDcg
    videoSaved = [bool]$videoSaved
    mcPathUsed = [bool]$mcPathUsed
    hdr10Config = [bool]$hdr10Config
    codecErrors = [bool]$codecErrors
    ffprobePass = $ffprobePass
    colorSpace = $colorSpace
    colorTransfer = $colorTransfer
    maxCll = $maxCll
}
$result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

if ($overallPass) {
    Write-Host "GATE: PASS" -ForegroundColor Green
} else {
    Write-Host "GATE: FAIL" -ForegroundColor Red
    if (-not $dcgSession) { Write-Host "  FAIL: missing dcgSessionTemplate log" -ForegroundColor Red }
    if (-not $inAppVideoDcg) { Write-Host "  FAIL: missing inAppVideoFormat=DCG" -ForegroundColor Red }
    if (-not $videoSaved) { Write-Host "  FAIL: video not saved" -ForegroundColor Red }
    if ($codecErrors) { Write-Host "  FAIL: codec errors" -ForegroundColor Red }
    if (-not $mcPathUsed) { Write-Host "  FAIL: MediaCodec path not used" -ForegroundColor Red }
    if (-not $ffprobePass) { Write-Host "  FAIL: ffprobe HDR10 metadata" -ForegroundColor Red }
}

Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

if (-not $overallPass) { exit 1 }
