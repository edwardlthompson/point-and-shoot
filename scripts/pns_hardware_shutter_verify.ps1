<#
.SYNOPSIS
  Cold preview + synthetic KEYCODE_CAMERA automation; assert PNS.HardwareKey shutterFired.

.EXAMPLE
  .\scripts\pns_hardware_shutter_verify.ps1 -Fast
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 70,
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [switch]$Fast,
    [switch]$AssertFocusConfirm
)

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") { return $t.Substring($eq + 1).Trim() }
    }
    return $null
}

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"
if ($Fast) { $WaitSec = [Math]::Min($WaitSec, 45) }

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}
$adbPrefix = @()
if ($Serial) { $adbPrefix = @("-s", $Serial) }

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    & adb @adbPrefix install -r -t $apk 2>&1 | Out-Null
}
& adb @adbPrefix shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\hardware_shutter_verify_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_hardware_shutter.txt"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_automation_hardware_key true `
    --ez pns_preview_composed_still true `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[hardware_shutter_verify] waiting ${WaitSec}s..."
Start-Sleep -Seconds $WaitSec

if ($AssertFocusConfirm) {
    Write-Host "[hardware_shutter_verify] injecting KEYCODE_FOCUS for focus confirm..."
    & adb @adbPrefix shell input keyevent 80 2>&1 | Out-Null
    Start-Sleep -Seconds 3
}

& adb @adbPrefix exec-out logcat -d -s "PNS.HardwareKey:I" "PNS.AdbValidation:I" "PNS.CaptureStill:I" "PNS.ShutterSound:I" 2>$null | Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$shutterOk = $hay -match "shutterFired source=camera_key"
$automationOk = $hay -match "hardwareKeyAutomation"
$stillOk = $hay -match "composedStill" -or $hay -match "captureComposedStill"
$focusConfirmOk = (-not $AssertFocusConfirm) -or ($hay -match "focusConfirm ok=true" -or $hay -match "focusConfirmBeep fired=true")

Write-Host "artifactDir=$outDir shutterOk=$shutterOk automationOk=$automationOk stillOk=$stillOk focusConfirmOk=$focusConfirmOk"
if (-not $shutterOk) { exit 1 }
if ($AssertFocusConfirm -and -not $focusConfirmOk) { exit 1 }
exit 0
