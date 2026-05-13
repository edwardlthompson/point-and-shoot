# BUILD_PLAN Milestone 3 (hardware mapping) — host gate + optional device smoke (USB adb).
# **ADB client match:** use the same **adb.exe** build as your USB stack (Android Studio / SDK
# **platform-tools**). Mixing an older **adb** on PATH with a newer one restarts the daemon and
# can briefly drop `adb devices` during scripted runs.
# Milestone 3 checklist in BUILD_PLAN.md is already [x]; this script re-verifies the M3 gate on demand:
#   - Unit tests: SensorCropGeometry, CropPlan, DNG default crop ratios, BackCameraRoleResolver clustering,
#     pickCameraIdFromM23Resolve
#   - Optional: assembleDebug + sideload + logcat grep for PNS.ChromeUx seedOk (M23 wide seed)
#
# Prerequisites: JDK for Gradle; optional scripts/pns_adb_device.env with PNS_ADB_SERIAL.
# Root is not required (device smoke uses normal preview automation only).

param(
    [string]$Serial = "",
    [switch]$SkipGradle,
    [switch]$RunDeviceSmoke
)

$ErrorActionPreference = "Stop"

$gradlewPs1 = Join-Path $PSScriptRoot "pns_gradlew.ps1"

Write-Host "[milestone3_gate] unit tests (M3 mapping gate)"
$testArgs = @(
    ":app:testDebugUnitTest",
    "--tests", "dev.pointandshoot.SensorCropGeometryTest",
    "--tests", "dev.pointandshoot.DngDefaultUserCropRatiosTest",
    "--tests", "dev.pointandshoot.CropPlanTest",
    "--tests", "dev.pointandshoot.BackCameraRoleResolverTest"
)
if (-not $SkipGradle.IsPresent) {
    & $gradlewPs1 @testArgs
    if ($LASTEXITCODE -ne 0) { throw "Milestone 3 unit tests failed exit=$LASTEXITCODE" }
}
else {
    Write-Host "[milestone3_gate] -SkipGradle: skipping unit tests"
}

if (-not $RunDeviceSmoke.IsPresent) {
    Write-Host "[milestone3_gate] done (host only). Pass -RunDeviceSmoke for sideload + seedOk log grep."
    exit 0
}

$sideload = Join-Path $PSScriptRoot "pns_sideload_and_launch.ps1"
if (-not (Test-Path -LiteralPath $sideload)) { throw "missing $sideload" }

Write-Host "[milestone3_gate] device smoke: sideload + preview cold start"
$sl = @{
    LaunchScreen = "preview"
    ColdStart    = $true
}
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $sl.Serial = $Serial
}
if (-not $SkipGradle.IsPresent) {
    # Unit tests already compiled debug sources; avoid a second full assembleDebug.
    $sl.SkipBuild = $true
}

& $sideload @sl
if (-not $?) { throw "pns_sideload_and_launch.ps1 failed" }

# Match pns_chrome_ux_gate.ps1: preview + readout need time before logcat (cold start is fast-returning).
Start-Sleep -Seconds 22

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet 2>$null
}

Write-Host "[milestone3_gate] adb logcat -d -s PNS.ChromeUx:I (seedOk slot=M23)"
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $tail = & adb -s $Serial logcat -d -s "PNS.ChromeUx:I" 2>$null
}
else {
    $tail = & adb logcat -d -s "PNS.ChromeUx:I" 2>$null
}
$hit = $tail | Select-String -Pattern "seedOk slot=M23"
if (-not $hit) {
    throw "Milestone 3 device smoke: no PNS.ChromeUx seedOk slot=M23 in logcat (cold preview start)."
}
Write-Host "[milestone3_gate] seedOk line: $($hit.Line)"
Write-Host "[milestone3_gate] RESULT: PASSED (host + device smoke)"
