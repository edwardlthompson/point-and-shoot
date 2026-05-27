#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 14.12 — dual video gate (host JVM + design doc; optional USB record).

.PARAMETER HostOnly
  Default when no device: JVM tests only (no adb).

.PARAMETER SkipGradle
  Skip assembleDebug on USB path.

.PARAMETER RecordSec
  When > 0, run in-app video automation (`pns_preview_automation_in_app_video_sec`) and require `inAppVideoSaved ok=true`.
#>
param(
    [switch]$HostOnly,
    [switch]$SkipGradle,
    [string]$Serial = "",
    [int]$RecordSec = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$doc = Join-Path $repoRoot "docs\M14_12_DUAL_VIDEO.md"

Push-Location $repoRoot
try {
    if (-not (Test-Path -LiteralPath $doc)) {
        throw "Missing design doc: $doc"
    }
    Write-Host "[pns_dual_video] design doc OK"

    & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest `
        --tests "dev.pointandshoot.DualVideoRecordingControllerTest" `
        --tests "dev.pointandshoot.CaptureMediaFamilyTest"
    if ($LASTEXITCODE -ne 0) { throw "JVM tests failed" }

    if ($HostOnly) {
        Write-Host "[pns_dual_video] HOST_PASS (scaffold; USB record not run)"
        exit 0
    }

    $resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
    if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }

    $adbArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adbArgs += "-s", $Serial }

    $devices = & adb @adbArgs devices 2>&1 | Out-String
    if ($devices -notmatch "`tdevice") {
        Write-Host "[pns_dual_video] no USB device; HOST_PASS (JVM only)"
        exit 0
    }

    $apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not $SkipGradle) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }
    if (-not $SkipGradle -or (Test-Path -LiteralPath $apk)) {
        if (-not (Test-Path -LiteralPath $apk)) { throw "Missing $apk (run assembleDebug first)" }
        & adb @adbArgs install -r -t $apk | Out-Host
    }

    $pkg = "dev.pointandshoot"
    & adb @adbArgs shell am force-stop $pkg | Out-Null
    & adb @adbArgs logcat -c 2>$null | Out-Null

    $startExtras = @(
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_primary_photo", "false",
        "--es", "pns_preview_dial", "DUAL",
        "--ei", "pns_preview_video_fps", "30",
        "--es", "pns_preview_imaging_profile", "standard_pro"
    )
    if ($RecordSec -gt 0) {
        $startExtras += "--ei", "pns_preview_automation_in_app_video_sec", "$RecordSec"
    }
    & adb @adbArgs shell am start -n "$pkg/.MainActivity" @startExtras | Out-Null

    $waitSec = if ($RecordSec -gt 0) { [Math]::Max(90, $RecordSec + 75) } else { 15 }
    Start-Sleep -Seconds $waitSec
    $log = & adb @adbArgs logcat -d -s "PNS.DualVideo:I" "PNS.AdbValidation:I" "PNS.VideoEncode:I" "PNS.MCVideoRec:I" 2>&1 | Out-String

    $previewOk =
        $log -match "dualVideo=active=true" -or
        $log -match "dualFront session ready" -or
        $log -match "dualGlRecordArmed"
    if (-not $previewOk) {
        Write-Warning "[pns_dual_video] dual preview log missing"
    } else {
        Write-Host "[pns_dual_video] dual preview OK"
    }

    if ($RecordSec -gt 0) {
        if ($log -notmatch "inAppVideoSaved ok=true") {
            $log | Out-File -FilePath (Join-Path $repoRoot "hfr-runs\dual_video_verify_log_tail.txt") -Encoding utf8
            throw "[pns_dual_video] FAIL: no inAppVideoSaved ok=true (dual record ${RecordSec}s)"
        }
        if ($log -notmatch "dualGlRecordArmed") {
            Write-Warning "[pns_dual_video] dualGlRecordArmed not seen — file may be rear-only or empty"
        }
        Write-Host "[pns_dual_video] USB_PASS dual record ${RecordSec}s"
    } elseif ($previewOk) {
        Write-Host "[pns_dual_video] USB_PASS (preview only; use -RecordSec 5 for clip gate)"
    } else {
        throw "[pns_dual_video] FAIL preview path"
    }
    & adb @adbArgs shell am force-stop $pkg | Out-Null
    exit 0
} finally {
    Pop-Location
}
