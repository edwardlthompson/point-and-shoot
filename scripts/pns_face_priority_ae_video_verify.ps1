#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.22** — in-app video + eye AF overlay; assert logcat aeSub=true.

.EXAMPLE
  .\scripts\pns_face_priority_ae_video_verify.ps1 -Serial b5214fc6 -SkipInstall
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [int]$RecordSec = 8
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") { . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet }

$adbExe = (Get-Command adb -ErrorAction Stop).Source
function Invoke-PnsAdb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    if ($Serial) { & $adbExe -s $Serial @AdbArgs } else { & $adbExe @AdbArgs }
}

$pkg = "dev.pointandshoot"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $projRoot "hfr-runs\face_priority_ae_video_verify_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipInstall -and (Test-Path $apk)) {
    Invoke-PnsAdb install -r -t $apk | Out-Null
}

$hudPath = "/data/data/$pkg/shared_prefs/pns_hud_settings.xml"
$hud = (Invoke-PnsAdb shell "run-as $pkg cat $hudPath" 2>&1) -join "`n"
if ($hud -notmatch "show_eye_af_overlay") { throw "missing show_eye_af_overlay in hud prefs" }
$hud = $hud -replace '<boolean name="show_eye_af_overlay" value="false"', '<boolean name="show_eye_af_overlay" value="true"'
$tmpHud = Join-Path $env:TEMP "pns_hud_face_$ts.xml"
[System.IO.File]::WriteAllText($tmpHud, $hud, [System.Text.UTF8Encoding]::new($false))
Invoke-PnsAdb push $tmpHud /data/local/tmp/pns_hud_face.xml | Out-Null
Invoke-PnsAdb shell "run-as $pkg cp /data/local/tmp/pns_hud_face.xml $hudPath" | Out-Null

Invoke-PnsAdb shell am force-stop $pkg | Out-Null
Invoke-PnsAdb logcat -c | Out-Null
Invoke-PnsAdb shell am start -n "$pkg/.MainActivity" `
    --es pns_screen preview `
    --ez pns_preview_primary_photo false `
    --ez pns_preview_eye_af_overlay true `
    --ei pns_preview_automation_in_app_video_sec $RecordSec `
    --ei pns_preview_video_fps 30 | Out-Null

$wait = $RecordSec + 20
Write-Host "waiting ${wait}s for video + face metering..."
Start-Sleep -Seconds $wait

$log = (Invoke-PnsAdb logcat -d -t 4000 -s PNS.FaceMeter:I PNS.AdbValidation:I 2>&1) -join "`n"
$log | Set-Content (Join-Path $outDir "logcat.txt") -Encoding UTF8
Invoke-PnsAdb shell am force-stop $pkg | Out-Null

$saved = $log -match "inAppVideoSaved ok=true"
$aeSub = $log -match "aeSub=true"
$pass = $saved -and $aeSub
@{ pass = [bool]$pass; saved = [bool]$saved; aeSub = [bool]$aeSub; outDir = $outDir } |
    ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8

if (-not $pass) {
    Write-Host "FAIL face priority AE video - saved=$saved aeSub=$aeSub - $outDir" -ForegroundColor Red
    exit 1
}
Write-Host "PASS face priority AE video - $outDir" -ForegroundColor Green
exit 0
