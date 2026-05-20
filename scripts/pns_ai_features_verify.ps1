#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 13V.17 — host + USB gate (smile still, scene probe, bitrate scale).

.DESCRIPTION
  Host: JVM tests + assembleDebug.
  USB (when device online):
    - PNS.SceneHint sceneHintProbeComplete
    - PNS.VideoController videoBitrateScale 100% vs 125% (in-app video automation)
    - PNS.SmileStill smileSyntheticTrigger + captureRawStill (adb synthetic gate hook)

.PARAMETER HostOnly
  Skip USB device checks.

.PARAMETER Serial
  ADB serial (scripts/pns_adb_device.env when omitted).

.PARAMETER SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipAssemble
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$act = "dev.pointandshoot.MainActivity"
Push-Location $repoRoot
try {
    if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") {
        . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet
    }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if ($Serial -eq "" -and (Test-Path $envFile)) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim().Trim('"') }
        }
    }

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = Join-Path $repoRoot "hfr-runs\ai_features_verify_$ts"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $result = [ordered]@{
        schema = "pns_ai_features_verify.v1"
        timestampUtc = (Get-Date).ToUniversalTime().ToString("o")
        gateDir = $outDir
        serial = $Serial
        hostOnly = [bool]$HostOnly
        unitTestsPass = $false
        assemblePass = $false
        sceneProbeOk = $null
        bitrateScaleOk = $null
        bitrateBps100 = $null
        bitrateBps125 = $null
        smileSyntheticOk = $null
        gateResult = "FAIL"
    }

    Write-Host "[pns_ai_features] JVM tests..."
    & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest `
        --tests "dev.pointandshoot.SmileStillCapturePolicyTest" `
        --tests "dev.pointandshoot.SceneVendorHintProbeTest"
    if ($LASTEXITCODE -ne 0) { throw "unit tests failed" }
    $result.unitTestsPass = $true

    if (-not $SkipAssemble) {
        Write-Host "[pns_ai_features] assembleDebug..."
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
        $result.assemblePass = $true
    } else {
        $result.assemblePass = Test-Path (Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk")
    }

    if ($HostOnly) {
        $result.gateResult = if ($result.unitTestsPass -and $result.assemblePass) { "HOST_PASS" } else { "FAIL" }
        $jsonPath = Join-Path $outDir "gate.json"
        $result | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonPath
        Write-Host "[pns_ai_features] GATE: $($result.gateResult) -> $jsonPath"
        if ($result.gateResult -eq "FAIL") { exit 1 }
        exit 0
    }

    $devices = @( (Invoke-AdbCmd devices) 2>$null | Where-Object { $_ -match "^\S+\s+device$" } )
    if ($devices.Length -eq 0) {
        Write-Host "[pns_ai_features] No device — HOST_PASS only"
        $result.gateResult = if ($result.unitTestsPass -and $result.assemblePass) { "HOST_PASS" } else { "FAIL" }
        $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8
        if ($result.gateResult -eq "FAIL") { exit 1 }
        exit 0
    }

    $apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apk) {
        Invoke-AdbCmd install -r -t $apk | Out-Null
        Invoke-AdbCmd shell pm grant $pkg android.permission.CAMERA 2>$null
        Invoke-AdbCmd shell pm grant $pkg android.permission.RECORD_AUDIO 2>$null
    }

    function Start-Preview {
        param([string[]]$ExtraAmArgs, [int]$WaitSec = 12)
        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
        Invoke-AdbCmd logcat -c 2>$null | Out-Null
        $startArgs = @(
            "shell", "am", "start", "-n", "$pkg/$act",
            "--es", "pns_screen", "preview"
        ) + $ExtraAmArgs
        Invoke-AdbCmd @startArgs 2>&1 | Out-Null
        Start-Sleep -Seconds $WaitSec
    }

    function Get-BitrateFromLog {
        $lines = Invoke-AdbCmd logcat -d -s "PNS.VideoController" 2>&1
        $m = $lines | Select-String -Pattern "videoBitrateScale=(\d+)% actualBitrate=(\d+)" | Select-Object -Last 1
        if ($m) {
            return [int]$m.Matches[0].Groups[2].Value
        }
        return $null
    }

    Write-Host "[pns_ai_features] Scene probe (cold start)..."
    Start-Preview -WaitSec 10
    $sceneLog = Invoke-AdbCmd logcat -d -s "PNS.SceneHint" 2>&1
    $sceneLog | Set-Content (Join-Path $outDir "scene_hint_logcat.txt") -Encoding UTF8
    $result.sceneProbeOk = [bool]($sceneLog | Select-String "sceneHintProbeComplete")
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

    Write-Host "[pns_ai_features] Bitrate scale 100% vs 125% (in-app video)..."
    Start-Preview -ExtraAmArgs @(
        "--ez", "pns_preview_primary_photo", "false",
        "--ei", "pns_preview_automation_in_app_video_sec", "4",
        "--ei", "pns_preview_video_fps", "30",
        "--ei", "pns_preview_video_bitrate_scale", "100"
    ) -WaitSec 48
    $result.bitrateBps100 = Get-BitrateFromLog
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

    Start-Preview -ExtraAmArgs @(
        "--ez", "pns_preview_primary_photo", "false",
        "--ei", "pns_preview_automation_in_app_video_sec", "4",
        "--ei", "pns_preview_video_fps", "30",
        "--ei", "pns_preview_video_bitrate_scale", "125"
    ) -WaitSec 48
    $result.bitrateBps125 = Get-BitrateFromLog
    $bitrateLog = Invoke-AdbCmd logcat -d -s "PNS.VideoController" 2>&1
    $bitrateLog | Set-Content (Join-Path $outDir "video_bitrate_logcat.txt") -Encoding UTF8
    $result.bitrateScaleOk =
        ($null -ne $result.bitrateBps100) -and
        ($null -ne $result.bitrateBps125) -and
        ($result.bitrateBps125 -gt $result.bitrateBps100)
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

    Write-Host "[pns_ai_features] Smile still synthetic capture..."
    Start-Preview -ExtraAmArgs @(
        "--ez", "pns_preview_smile_still", "true",
        "--ez", "pns_preview_smile_still_synthetic", "true",
        "--es", "pns_preview_dial", "A"
    ) -WaitSec 8
    $hasSynthetic = $false
    $hasCapture = $false
    for ($i = 0; $i -lt 12; $i++) {
        Start-Sleep -Seconds 5
        $smileLog = Invoke-AdbCmd logcat -d 2>&1
        $hasSynthetic = [bool]($smileLog | Select-String "smileSyntheticTrigger")
        $hasCapture =
            [bool]($smileLog | Select-String "captureRawStill.*ok=true") -or
            [bool]($smileLog | Select-String "captureComposedStill.*ok=true") -or
            [bool]($smileLog | Select-String "dng save diag.*stillMode=")
        if ($hasSynthetic -and $hasCapture) { break }
    }
    $smileLog = Invoke-AdbCmd logcat -d 2>&1
    $smileLog | Select-String "PNS.SmileStill|PNS.CaptureStill|PNS.AdbValidation" | ForEach-Object { $_.Line } |
        Set-Content (Join-Path $outDir "smile_still_logcat.txt") -Encoding UTF8
    $result.smileSyntheticOk = $hasSynthetic -and $hasCapture
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

    $usbOk =
        $result.sceneProbeOk -and
        $result.bitrateScaleOk -and
        $result.smileSyntheticOk
    $result.gateResult = if ($result.unitTestsPass -and $result.assemblePass -and $usbOk) { "USB_PASS" } else { "FAIL" }

    $jsonPath = Join-Path $outDir "gate.json"
    $result | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonPath

    Write-Host "[pns_ai_features] sceneProbe=$($result.sceneProbeOk) bitrate=$($result.bitrateBps100)->$($result.bitrateBps125) ok=$($result.bitrateScaleOk) smile=$($result.smileSyntheticOk)"
    Write-Host "[pns_ai_features] GATE: $($result.gateResult) -> $jsonPath"

    if ($result.gateResult -eq "FAIL") { exit 1 }
    exit 0
} finally {
    Pop-Location
}
