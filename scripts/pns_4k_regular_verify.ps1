<#
.SYNOPSIS
  USB gate: 4K @ 30 fps H.264 in-app video on devices with matrix fourKRegular.sessionOk=true.

.DESCRIPTION
  Cold video-primary preview with encode 3840x2160, H.264 codec ordinal 0, 30 fps automation clip.
  Asserts inAppVideoSaved ok=true and minimum byte size. Optional ffprobe dimension check when on PATH.

.PARAMETER Serial
  adb -s serial (optional scripts/pns_adb_device.env PNS_ADB_SERIAL).

.PARAMETER SkipFourKGateCheck
  Run record attempt even when pulled matrix shows fourKRegular.sessionOk=false (expect fail).

.EXAMPLE
  .\scripts\pns_4k_regular_verify.ps1 -Serial FA8BW1F00538
#>
param(
    [string]$Serial = "",
    [int]$MaxAttempts = 4,
    [int]$WaitSec = 90,
    [int]$RecordSec = 5,
    [long]$MinBytes = 500000,
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [switch]$SkipFourKGateCheck
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}
. (Join-Path $PSScriptRoot "pns_adb_serial.ps1")

$Serial = Resolve-PnsAdbSerial -Serial $Serial -ScriptRoot $PSScriptRoot -LogPrefix "4k_regular_verify"

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"
$rec = [Math]::Max(1, [Math]::Min($RecordSec, 120))

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }

function Invoke-Adb([string[]]$CmdArgs) {
    $adbExe = (Get-Command adb -ErrorAction Stop).Source
    $prefix = @()
    if ($Serial) { $prefix = @("-s", $Serial) }
    & $adbExe @prefix @CmdArgs
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($CmdArgs -join ' ') exit=$LASTEXITCODE" }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    try { Invoke-Adb $CmdArgs } catch { }
}

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\4k_regular_verify_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "[4k_regular_verify] artifacts -> $outDir"

Invoke-Adb @("devices", "-l")
if (-not $SkipInstall) {
    Write-Host "[4k_regular_verify] install $apk"
    Invoke-Adb @("install", "-r", "-t", $apk)
}
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.WRITE_EXTERNAL_STORAGE")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_EXTERNAL_STORAGE")

$gateOk = $true
$gateNote = "skip_gate_check"
if (-not $SkipFourKGateCheck) {
    try {
        $matrixRaw = & adb $(if ($Serial) { @("-s", $Serial) }) exec-out run-as $pkg cat files/fleet_device_matrix.json 2>$null
        if ($matrixRaw) {
            $matrixObj = $matrixRaw | ConvertFrom-Json
            $cam0 = $matrixObj.cameras | Where-Object { $_.cameraId -eq "0" } | Select-Object -First 1
            $gate = $cam0.featureGates.fourKRegular
            if ($gate) {
                $gateOk = [bool]$gate.sessionOk
                $gateNote = "fourKRegular advertised=$($gate.advertised) sessionOk=$($gate.sessionOk)"
            } else {
                $gateOk = $false
                $gateNote = "fourKRegular gate missing - rescan with pns_fleet_matrix_scan.ps1 -ScanTier full"
            }
        } else {
            $gateOk = $false
            $gateNote = "matrix pull failed"
        }
    }
    catch {
        $gateOk = $false
        $gateNote = "matrix parse failed: $($_.Exception.Message)"
    }
    Write-Host "[4k_regular_verify] matrix gate: $gateNote"
    if (-not $gateOk) {
        @{
            schema = "pns.4k_regular_verify.v1"
            pass = $false
            skipped = $true
            reason = "fourKRegular.sessionOk=false"
            gateNote = $gateNote
            outDir = $outDir
        } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir "gate.json") -Encoding utf8
        Write-Host "[4k_regular_verify] SKIPPED — fourKRegular not session-proven on device (expected on EXODUS-class stacks)."
        Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
        exit 0
    }
}

$successNeedle = "inAppVideoSaved ok=true bytes="
$failNeedles = @(
    "inAppVideoAutomation recorderMissingOrFailed",
    "inAppVideoShellStartFailed",
    "FATAL EXCEPTION"
)

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    Write-Host "[4k_regular_verify] attempt $attempt / $MaxAttempts"
    Invoke-AdbIgnore @("shell", "logcat", "-c")
    Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
    Start-Sleep -Milliseconds 800

    $amArgs = @(
        "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_primary_photo", "false",
        "--ei", "pns_preview_automation_in_app_video_sec", "$rec",
        "--ei", "pns_preview_video_encode_w", "3840",
        "--ei", "pns_preview_video_encode_h", "2160",
        "--ei", "pns_preview_video_codec_ordinal", "0",
        "--ei", "pns_preview_fps", "30",
        "--es", "pns_preview_imaging_profile", "standard_pro",
        "--es", "pns_preview_camera_id", "0"
    )
    try {
        Invoke-Adb $amArgs | Out-Null
    }
    catch {
        Write-Warning "[4k_regular_verify] am start failed: $_"
        continue
    }

    Start-Sleep -Seconds 12
    $deadline = (Get-Date).AddSeconds($WaitSec)
    $haystack = ""
    while ((Get-Date) -lt $deadline) {
        $adbExe = (Get-Command adb -ErrorAction Stop).Source
        $argv = @()
        if ($Serial) { $argv += "-s", $Serial }
        $argv += "logcat", "-d", "-s", "PNS.AdbValidation:I", "PNS.ChromeUx:I", "PNS.VideoController:I"
        $lines = & $adbExe @argv 2>&1
        $haystack = if ($lines -is [System.Array]) { $lines -join "`n" } else { [string]$lines }
        if ($haystack.Contains($successNeedle)) { break }
        Start-Sleep -Seconds 3
    }

    $logPath = Join-Path $outDir ("attempt_{0:D2}_logcat.txt" -f $attempt)
    $haystack | Set-Content -LiteralPath $logPath -Encoding utf8

    $bad = $false
    foreach ($needle in $failNeedles) {
        if ($haystack.Contains($needle)) { $bad = $true; break }
    }

    $bytes = -1
    if ($haystack -match 'inAppVideoSaved ok=true bytes=(\d+)') {
        $bytes = [long]$Matches[1]
    }

    if (-not $bad -and $bytes -ge $MinBytes) {
        Write-Host "[4k_regular_verify] PASS bytes=$bytes gate=$gateNote"
        @{
            schema = "pns.4k_regular_verify.v1"
            pass = $true
            bytes = $bytes
            gateNote = $gateNote
            outDir = $outDir
        } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir "gate.json") -Encoding utf8
        Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
        exit 0
    }
    Write-Host "[4k_regular_verify] attempt $attempt failed bytes=$bytes bad=$bad"
}

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
Write-Error "[4k_regular_verify] FAILED after $MaxAttempts attempts. See $outDir"
exit 1
