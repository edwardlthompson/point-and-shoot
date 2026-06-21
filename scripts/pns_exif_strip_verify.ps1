#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 29.1 — EXIF privacy strip host + optional USB gate.

.DESCRIPTION
    Host: JpegExifPrivacyStripTest + assembleDebug.
    USB (default when device online): cold preview with strip seed + one RAW still;
    asserts PNS.AdbValidation exifStrip ok=true and optional pns_aux_dng_capture_analyze DNG integrity.

.PARAMETER Serial
.PARAMETER SkipInstall
.PARAMETER SkipAssemble
.PARAMETER HostOnly
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [switch]$HostOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
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

    $outDir = Join-Path $repoRoot "hfr-runs\exif_strip_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    function Write-Step { param([string]$msg) Write-Host "[pns_exif_strip_verify] $msg" }

    Write-Step "JpegExifPrivacyStripTest..."
    & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest --tests "dev.pointandshoot.JpegExifPrivacyStripTest"
    if ($LASTEXITCODE -ne 0) { Write-Step "HOST FAIL: unit test"; exit 1 }

    if (-not $SkipAssemble) {
        Write-Step "assembleDebug..."
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { exit 1 }
    }

    if ($HostOnly) {
        $artifact = [ordered]@{
            schema = "pns.exif_strip_verify.v1"
            timestamp = (Get-Date -Format "o")
            gateResult = "HOST_PASS"
            hostOnly = $true
        }
        $jsonOut = Join-Path $outDir "gate.json"
        $artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut
        Write-Step "HOST_PASS -> $jsonOut"
        exit 0
    }

    $adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }
    $devices = & adb @adbArgs devices 2>&1 | Where-Object { $_ -match "\tdevice$" }
    if (-not $devices) {
        Write-Step "SKIP USB: no device (host test PASS)"
        exit 0
    }

    if (-not $SkipInstall) {
        $apk = Get-ChildItem "$repoRoot\app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $apk) { throw "APK missing" }
        & adb @adbArgs install -r -t $apk.FullName
        if ($LASTEXITCODE -ne 0) { exit 1 }
        & adb @adbArgs shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null | Out-Null
    }

    & adb @adbArgs logcat -c 2>$null
    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null
  & adb @adbArgs shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview `
        --ez pns_preview_strip_exif true `
        --ei pns_preview_raw_count 1 2>&1 | Out-Null

    Write-Step "Waiting 55s for RAW still + exif strip..."
    Start-Sleep -Seconds 55

    $rawLog = & adb @adbArgs logcat -d -s "PNS.AdbValidation" 2>&1
    $logPath = Join-Path $outDir "logcat_adbvalidation.txt"
    $rawLog | Set-Content -Encoding UTF8 $logPath

    $stripOk = $rawLog | Where-Object { $_ -match "exifStrip ok=true" }
    $dngSkipOk = $rawLog | Where-Object { $_ -match "exifStrip dngMetadataSkipped ok=true" }
    $captureOk = $rawLog | Where-Object { $_ -match "captureRawStill 1/1 ok=true" }

    $stripProofOk = ($null -ne $stripOk) -or ($null -ne $dngSkipOk)
    $usbPass = $stripProofOk -and ($null -ne $captureOk)
    $gateResult = if ($usbPass) { "PASS" } else { "FAIL" }

    $artifact = [ordered]@{
        schema = "pns.exif_strip_verify.v1"
        timestamp = (Get-Date -Format "o")
        device = if ($Serial) { $Serial } else { "default" }
        gateResult = $gateResult
        stripLogOk = ($null -ne $stripOk)
        captureOk = ($null -ne $captureOk)
        outDir = $outDir
    }
    $jsonOut = Join-Path $outDir "gate.json"
    $artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut
    Write-Step "USB gateResult=$gateResult -> $jsonOut"

    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null

    if ($usbPass) {
        Write-Step "USB PASS (run pns_aux_dng_capture_analyze.ps1 separately for DNG lane)"
    }

    if ($gateResult -eq "FAIL") { exit 1 }
    exit 0
}
finally {
    Pop-Location
}
