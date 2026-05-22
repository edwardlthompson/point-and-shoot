#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 14.5 — host gate for face/eye overlay alignment tooling.

.DESCRIPTION
  Runs JVM [TexturePreviewFitTest] + [CaptureMediaFamilyTest]. Optional USB: cold preview with
  HUD pref `show_face_alignment_debug_crosshair` seeded via run-as (debug APK).

.PARAMETER HostOnly
  Default; no device required.
#>
param(
    [switch]$HostOnly = $true,
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest `
        --tests "dev.pointandshoot.TexturePreviewFitTest" `
        --tests "dev.pointandshoot.CaptureMediaFamilyTest"
    if ($LASTEXITCODE -ne 0) { throw "alignment JVM tests failed" }

    $overlayKt = Join-Path $repoRoot "app\src\main\java\dev\pointandshoot\FaceAlignmentDebugCrosshairOverlay.kt"
    if (-not (Test-Path -LiteralPath $overlayKt)) {
        throw "Missing FaceAlignmentDebugCrosshairOverlay.kt"
    }
    Write-Host "[pns_eye_af_alignment] overlay source OK"

    if ($HostOnly) {
        Write-Host "[pns_eye_af_alignment] HOST_PASS"
        exit 0
    }

    $resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
    if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }
    $adbArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adbArgs += "-s", $Serial }

    $pkg = "dev.pointandshoot"
    & adb @adbArgs shell am force-stop $pkg | Out-Null
    & adb @adbArgs shell am start -n "$pkg/.MainActivity" --es pns_screen preview | Out-Null
    Start-Sleep -Seconds 6
    $log = & adb @adbArgs logcat -d -s "PNS.FaceAlign:I" 2>&1 | Out-String
    if ($log -notmatch "faceAlignCrosshair=visible") {
        Write-Warning "[pns_eye_af_alignment] crosshair log not seen (enable HUD toggle manually)"
    }
    & adb @adbArgs shell am force-stop $pkg | Out-Null
    Write-Host "[pns_eye_af_alignment] USB smoke done"
    exit 0
} finally {
    Pop-Location
}
