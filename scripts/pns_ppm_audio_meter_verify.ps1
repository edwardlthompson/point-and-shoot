#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.20** — segmented PPM meters visible during in-app video recording.

.EXAMPLE
  .\scripts\pns_ppm_audio_meter_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$RecordSec = 8,
    [int]$ScreencapDelaySec = 5
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

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\ppm_audio_meter_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Null
    adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
    adb -s $Serial shell pm grant $pkg android.permission.RECORD_AUDIO 2>$null | Out-Null
}

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null
adb -s $Serial logcat -c 2>$null | Out-Null

$rec = [Math]::Max(3, [Math]::Min($RecordSec, 30))
adb -s $Serial shell am start -W -n "$pkg/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_primary_photo false `
    --ei pns_preview_automation_in_app_video_sec $rec `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[ppm_meter] waiting ${ScreencapDelaySec}s then screencap (recording)..."
Start-Sleep -Seconds $ScreencapDelaySec
$capPath = Join-Path $outDir "ppm_meters_recording.png"
& (Join-Path $repo "scripts\pns_device_screencap.ps1") -Serial $Serial -OutPath $capPath | Out-Host

$remain = [Math]::Max(0, $rec + 12 - $ScreencapDelaySec)
if ($remain -gt 0) {
    Write-Host "[ppm_meter] waiting ${remain}s for clip finalize..."
    Start-Sleep -Seconds $remain
}

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.ChromeUx:I PNS.AdbValidation:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$audioMeters = $log -match "audioMeters=true"
$recording = $log -match "readoutMode=video|recording=true"
$videoSaved = $log -match "inAppVideoSaved ok=true"
$screencapOk = Test-Path $capPath
$pass = $audioMeters -and $screencapOk -and ($recording -or $videoSaved)

$gate = [ordered]@{
    pass = $pass
    audioMeters = [bool]$audioMeters
    recordingLog = [bool]$recording
    videoSaved = [bool]$videoSaved
    screencap = $capPath
    outDir = $outDir
}
$gate | ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

if (-not $pass) {
    Write-Host "FAIL ppm meter gate — $outDir" -ForegroundColor Red
    exit 1
}
Write-Host "PASS ppm meter gate — $outDir" -ForegroundColor Green
