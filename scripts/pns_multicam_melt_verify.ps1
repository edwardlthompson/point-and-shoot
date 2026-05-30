#Requires -Version 5.1
<#
.SYNOPSIS
  Milestone 20.2 — Multicam Melt scaffold USB smoke (grep multicamMelt=arm).
#>
param(
    [switch]$HostOnly,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ($HostOnly) {
    Write-Host "[pns_multicam_melt] HOST_PASS (skipped)"
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }

$adbArgs = @()
if ($Serial) { $adbArgs += "-s", $Serial }

$devices = & adb @adbArgs devices 2>&1 | Out-String
if ($devices -notmatch "`tdevice") {
    Write-Host "[pns_multicam_melt] no USB device; HOST_PASS"
    exit 0
}

$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if ($AssembleDebug -or -not (Test-Path -LiteralPath $apk)) {
    & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not $SkipInstall) {
    & adb @adbArgs install -r -t $apk | Out-Null
}

$pkg = "dev.pointandshoot"
& adb @adbArgs shell am force-stop $pkg | Out-Null
& adb @adbArgs logcat -c 2>$null | Out-Null

& adb @adbArgs shell am start -n "$pkg/.MainActivity" `
    --es pns_screen preview `
    --ez pns_preview_primary_photo false `
    --ez pns_preview_multicam_melt true | Out-Null

Start-Sleep -Seconds 15
$log = & adb @adbArgs logcat -d -s "PNS.MulticamMelt:I" 2>&1 | Out-String

if ($log -notmatch "multicamMelt=arm") {
    $log | Out-File -FilePath (Join-Path $repoRoot "hfr-runs\multicam_melt_verify_log.txt") -Encoding utf8
    throw "[pns_multicam_melt] FAIL: multicamMelt=arm not seen"
}

Write-Host "[pns_multicam_melt] USB_PASS"
& adb @adbArgs shell am force-stop $pkg | Out-Null
exit 0
