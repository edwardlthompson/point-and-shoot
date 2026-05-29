#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.11** — video @ 30 fps with 180° shutter angle; assert readoutManual ssNs=33333333 in logcat.
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 14
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (Test-Path (Join-Path $PSScriptRoot "pns_resolve_adb.ps1")) {
    . (Join-Path $PSScriptRoot "pns_resolve_adb.ps1") -PrependToPath -Quiet
}

$adb = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adb = @("-s", $Serial) }

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
    --ez pns_preview_primary_photo false `
    --ei pns_preview_video_fps 30 `
    --es pns_preview_video_shutter_angle Angle180 | Out-Null

Start-Sleep -Seconds $WaitSec
$log = & adb @adb logcat -d -s "PNS.Cam:I" "PNS.AdbValidation:I" 2>&1 | Out-String
$ok =
    ($log -match "readoutManual videoShutterAngle=Angle180") -and
    ($log -match "readoutManual.*ssNs=1666666[67]") -and
    ($log -match "preview seeded videoShutterAngle=Angle180 fps=30")
if (-not $ok) {
    Write-Host "SHUTTER ANGLE VERIFY: FAIL"
    Write-Host $log
    & adb @adb shell am force-stop $pkg | Out-Null
    exit 1
}
Write-Host "SHUTTER ANGLE VERIFY: PASS Angle180 ssNs~16666667 @30fps (half frame)"
& adb @adb shell am force-stop $pkg | Out-Null
exit 0
