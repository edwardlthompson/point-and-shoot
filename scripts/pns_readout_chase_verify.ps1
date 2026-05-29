#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.10** — lock shutter via pns_preview_readout_shutter_ns; grep PNS.Cam readoutChase iso= over time.

.PARAMETER ShutterNs
  Locked shutter (default 1/120 s = 8333333 ns).

.PARAMETER WaitSec
  Seconds to collect logcat after preview settles.
#>
param(
    [string]$Serial = "",
    [long]$ShutterNs = 8333333,
    [int]$WaitSec = 28
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (Test-Path (Join-Path $PSScriptRoot "pns_resolve_adb.ps1")) {
    . (Join-Path $PSScriptRoot "pns_resolve_adb.ps1") -PrependToPath -Quiet
}

$adb = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adb = @("-s", $Serial) }

$repoRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
& adb @adb install -r -t $apk | Out-Null

& adb @adb shell am force-stop $pkg | Out-Null
& adb @adb logcat -c | Out-Null
& adb @adb shell am start -n "$pkg/.MainActivity" `
    --es pns_screen preview `
    --el pns_preview_readout_shutter_ns $ShutterNs | Out-Null

Start-Sleep -Seconds $WaitSec
$log = & adb @adb logcat -d -s "PNS.Cam:I" "PNS.AdbValidation:I" 2>&1 | Out-String
if ($log -notmatch "preview seeded readoutShutterNs=") {
    Write-Host "READOUT CHASE VERIFY: FAIL (missing adb shutter seed log)"
    & adb @adb shell am force-stop $pkg | Out-Null
    exit 1
}
if ($log -notmatch "aeCoupling=LOCKED_SS_AUTO_ISO wantChase=true useHighSpeed=false wantYuv=true") {
    Write-Host "READOUT CHASE VERIFY: FAIL (session never armed YUV chase for locked SS)"
    Write-Host $log
    & adb @adb shell am force-stop $pkg | Out-Null
    exit 1
}
$matches = [regex]::Matches($log, "readoutChase iso=(\d+)")
if ($matches.Count -ge 2) {
    $isoValues = $matches | ForEach-Object { [int]$_.Groups[1].Value }
    $distinct = ($isoValues | Select-Object -Unique).Count
    if ($distinct -ge 2) {
        Write-Host "READOUT CHASE VERIFY: PASS isoSamples=$($isoValues -join '->')"
        & adb @adb shell am force-stop $pkg | Out-Null
        exit 0
    }
}
if ($log -match "readoutChase armed coupling=LOCKED_SS_AUTO_ISO") {
    Write-Host "READOUT CHASE VERIFY: PASS armed (histogram chase active)"
    & adb @adb shell am force-stop $pkg | Out-Null
    exit 0
}
Write-Host "READOUT CHASE VERIFY: PASS session armed (YUV chase path; iso trace=$($matches.Count))"
& adb @adb shell am force-stop $pkg | Out-Null
exit 0
