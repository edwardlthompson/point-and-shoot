#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.25** — wind noise filter (NS + AEC) logged during in-app recording.

.EXAMPLE
  .\scripts\pns_wind_noise_filter_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$RecordSec = 8,
    [int]$WaitSec = 10
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
$outDir = Join-Path $repo "hfr-runs\wind_noise_filter_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
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
    --ez pns_preview_wind_noise_filter true `
    --ei pns_preview_automation_in_app_video_sec $rec `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[wind_filter] waiting ${WaitSec}s for record + effects..."
Start-Sleep -Seconds $WaitSec

$remain = [Math]::Max(0, $rec + 12 - $WaitSec)
if ($remain -gt 0) {
    Start-Sleep -Seconds $remain
}

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.Audio:I PNS.MCVideoRec:I PNS.AdbValidation:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$windOn = $log -match "windFilter=on"
$nsAvail = $log -match "nsAvail="
$videoSaved = $log -match "inAppVideoSaved ok=true"
$pass = $windOn -and $nsAvail -and $videoSaved

$gate = [ordered]@{
    pass = $pass
    windFilterOn = [bool]$windOn
    nsAvailLogged = [bool]$nsAvail
    videoSaved = [bool]$videoSaved
    outDir = $outDir
}
$gate | ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

if (-not $pass) {
    Write-Host "FAIL wind noise filter gate — $outDir" -ForegroundColor Red
    exit 1
}
Write-Host "PASS wind noise filter gate — $outDir" -ForegroundColor Green
