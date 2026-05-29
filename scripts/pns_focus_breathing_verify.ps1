#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.28** — focus breathing compensation during M-dial tele manual-focus rack.

.EXAMPLE
  .\scripts\pns_focus_breathing_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$RackSec = 10,
    [int]$WaitSec = 20
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
$outDir = Join-Path $repo "hfr-runs\focus_breathing_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
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

$rack = [Math]::Max(4, [Math]::Min($RackSec, 30))
adb -s $Serial shell am start -W -n "$pkg/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --es pns_preview_dial M `
    --es pns_preview_focal_mm_slot 73 `
    --ez pns_preview_focus_breathing_comp true `
    --ei pns_preview_automation_focus_rack_sec $rack `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[focus_breathing] waiting ${WaitSec}s for tele M-dial rack..."
Start-Sleep -Seconds $WaitSec

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.FocusBreathing:I PNS.AdbValidation:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$seedOn = $log -match "focusBreathingComp=true|focusBreathingAutomation begin"
$rackDone = $log -match "focusBreathingAutomation (begin|rackSec=)"
$breathingLog = $log -match "PNS\.FocusBreathing.*breathing"
$pass = $seedOn -and $rackDone -and $breathingLog

$gate = [ordered]@{
    pass = $pass
    seedOn = [bool]$seedOn
    rackDone = [bool]$rackDone
    breathingLog = [bool]$breathingLog
    rackSec = $rack
    outDir = $outDir
}
$gate | ConvertTo-Json | Out-File -Encoding utf8 (Join-Path $outDir "gate.json")
Write-Host ($gate | ConvertTo-Json -Compress)
if (-not $pass) { exit 1 }
Write-Host "PASS focus breathing verify -> $outDir"
