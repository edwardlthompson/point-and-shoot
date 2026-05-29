#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.27** — intervalometer time-lapse video (H.264 MP4 in MediaStore).

.EXAMPLE
  .\scripts\pns_timelapse_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$IntervalSec = 2,
    [int]$WaitSec = 18
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
$outDir = Join-Path $repo "hfr-runs\timelapse_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Null
    adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
}

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null
adb -s $Serial logcat -c 2>$null | Out-Null

$interval = [Math]::Max(1, [Math]::Min($IntervalSec, 10))
adb -s $Serial shell am start -W -n "$pkg/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_primary_photo true `
    --es pns_preview_timelapse_mode video `
    --ez pns_preview_timelapse_running true `
    --ei pns_preview_timelapse_interval_sec $interval `
    --ei pns_preview_timelapse_auto_stop_sec 12 `
    --ez pns_preview_jpeg_companion true `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[timelapse] waiting ${WaitSec}s for frames + auto-stop finalize..."
Start-Sleep -Seconds $WaitSec
Start-Sleep -Seconds 4

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.TimeLapse:I PNS.AdbValidation:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$modeActive = $log -match "intervalometer active mode=Video"
$frameEncoded = $log -match "timelapse frame="
$timelapseSaved = $log -match "timelapseVideoSaved ok=true|finish ok uri="
$pass = $modeActive -and $frameEncoded -and $timelapseSaved

$gate = [ordered]@{
    pass = $pass
    modeActive = [bool]$modeActive
    frameEncoded = [bool]$frameEncoded
    timelapseSaved = [bool]$timelapseSaved
    intervalSec = $interval
    outDir = $outDir
}
$gate | ConvertTo-Json | Out-File -Encoding utf8 (Join-Path $outDir "gate.json")
Write-Host ($gate | ConvertTo-Json -Compress)
if (-not $pass) { exit 1 }
Write-Host "PASS timelapse verify -> $outDir"
