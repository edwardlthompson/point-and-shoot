#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.19** — enable bt_remote_shutter + media key → logcat shutterFired source=bt_media.

.EXAMPLE
  .\scripts\pns_bt_remote_shutter_verify.ps1 -Serial b5214fc6
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall
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
$outDir = Join-Path $projRoot "hfr-runs\bt_remote_shutter_verify_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipInstall -and (Test-Path $apk)) {
    Invoke-PnsAdb install -r -t $apk | Out-Null
}

$chromePath = "/data/data/$pkg/shared_prefs/pns_preview_chrome.xml"
$xml = (Invoke-PnsAdb shell "run-as $pkg cat $chromePath" 2>&1) -join "`n"
if ($xml -notmatch "bt_remote_shutter") { throw "chrome prefs missing bt_remote_shutter" }
$xml = $xml -replace '<boolean name="bt_remote_shutter" value="false"\s*/>', '<boolean name="bt_remote_shutter" value="true" />'
if ($xml -notmatch '<boolean name="bt_remote_shutter" value="true"') {
    throw "failed to enable bt_remote_shutter in chrome prefs"
}
$tmp = Join-Path $env:TEMP "pns_chrome_bt_$ts.xml"
[System.IO.File]::WriteAllText($tmp, $xml, [System.Text.UTF8Encoding]::new($false))
Invoke-PnsAdb push $tmp /data/local/tmp/pns_chrome_bt.xml | Out-Null
Invoke-PnsAdb shell "run-as $pkg cp /data/local/tmp/pns_chrome_bt.xml $chromePath" | Out-Null

Invoke-PnsAdb shell am force-stop $pkg | Out-Null
Start-Sleep -Milliseconds 500
Invoke-PnsAdb logcat -c | Out-Null
Invoke-PnsAdb shell am start -n "$pkg/.MainActivity" --es pns_screen preview --ez pns_preview_automation_bt_media_key true | Out-Null
Write-Host "waiting 14s for preview + automation media key..."
Start-Sleep -Seconds 14
Invoke-PnsAdb shell input keyevent 85 2>$null | Out-Null
Start-Sleep -Seconds 1
$log = (Invoke-PnsAdb logcat -d -t 2000 -s PNS.MediaSession:I PNS.AdbValidation:I 2>&1) -join "`n"
$log | Set-Content (Join-Path $outDir "logcat.txt") -Encoding UTF8
Invoke-PnsAdb shell am force-stop $pkg | Out-Null

$ok = $log -match "shutterFired source=bt_media"
$gate = @{ pass = [bool]$ok; timestamp = $ts; outDir = $outDir }
$gate | ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8
if (-not $ok) {
    Write-Host "FAIL: no shutterFired source=bt_media - $outDir" -ForegroundColor Red
    exit 1
}
Write-Host "PASS: bt remote shutter - $outDir" -ForegroundColor Green
exit 0
