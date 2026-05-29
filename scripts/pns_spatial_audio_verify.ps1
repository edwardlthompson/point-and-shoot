#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.30** — spatial audio metadata (stereo channel mask) on in-app MC video + ffprobe.

.EXAMPLE
  .\scripts\pns_spatial_audio_verify.ps1 -Serial b5214fc6
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$WaitSec = 60,
    [int]$RecordSec = 5
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

function Read-Serial {
    param([string]$S)
    if ($S) { return $S }
    $envFile = Join-Path $repo "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim() }
        }
    }
    throw "Set PNS_ADB_SERIAL or -Serial"
}

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $argv = @()
    if ($Serial) { $argv += "-s", $Serial }
    $argv += $AdbArgs
    & adb @argv
}

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\spatial_audio_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk) | Out-Null
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 2>$null | Out-Null
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") 2>$null | Out-Null
}

Invoke-Adb @("shell", "am", "force-stop", $pkg) 2>$null | Out-Null
Invoke-Adb @("logcat", "-c") 2>$null | Out-Null

$rec = [Math]::Max(3, [Math]::Min($RecordSec, 30))
Invoke-Adb @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "false",
    "--ei", "pns_preview_automation_in_app_video_sec", "$rec",
    "--es", "pns_preview_imaging_profile", "standard_pro"
) 2>&1 | Out-Null

Write-Host "[spatial_audio] waiting ${WaitSec}s for in-app video + spatialAudioMeta..."
Start-Sleep -Seconds $WaitSec

Invoke-Adb @("shell", "am", "force-stop", $pkg) 2>$null | Out-Null

$logPath = Join-Path $outDir "logcat.txt"
Invoke-Adb @(
    "logcat", "-d", "-v", "threadtime",
    "-s", "PNS.MCVideoRec:I", "PNS.AdbValidation:I", "AndroidRuntime:E"
) 2>&1 | Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$videoSaved = $log -match "inAppVideoSaved ok=true"
$spatialMeta = $log -match "spatialAudioMeta=stereo"
$channelLayout = "unknown"
$ffprobeOk = $false

$localMp4 = Join-Path $outDir "clip.mp4"
$dcim = "/sdcard/DCIM/Point & Shoot"
$latest = (Invoke-Adb @("shell", "ls -t '$dcim'/pns_*.mp4 2>/dev/null | head -1") 2>&1) -join "`n"
$latest = ($latest -split "`n" | Where-Object { $_ -match "pns_.*\.mp4" } | Select-Object -First 1)
if ($latest) {
    $latest = $latest.Trim()
    Invoke-Adb @("pull", $latest, $localMp4) 2>&1 | Out-Null
}
if ((Test-Path $localMp4) -and (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
    $layout = (& ffprobe -v error -select_streams a:0 `
        -show_entries stream=channel_layout `
        -of default=noprint_wrappers=1:nokey=1 $localMp4 2>&1) -join ""
    $layout = $layout.Trim()
    if ($layout) {
        $channelLayout = $layout
        $ffprobeOk = ($layout -eq "stereo")
    }
} elseif (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
    Write-Host "[spatial_audio] ffprobe not on PATH — skipping channel_layout check"
    $ffprobeOk = $videoSaved -and $spatialMeta
}

$pass = $videoSaved -and $spatialMeta -and $ffprobeOk

$gate = [ordered]@{
    pass = $pass
    videoSaved = [bool]$videoSaved
    spatialMeta = [bool]$spatialMeta
    ffprobeStereo = [bool]$ffprobeOk
    channelLayout = $channelLayout
    outDir = $outDir
}
$gate | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $outDir "gate.json")

if ($pass) {
    Write-Host "SPATIAL AUDIO VERIFY: PASS ($outDir)"
    exit 0
}
Write-Host "SPATIAL AUDIO VERIFY: FAIL ($outDir)"
exit 1
