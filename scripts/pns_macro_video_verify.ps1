#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.31** — macro video mode (video tray + MACRO dial, EIS, UW, macroVideo log).

.EXAMPLE
  .\scripts\pns_macro_video_verify.ps1 -Serial b5214fc6
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$WaitSec = 60,
    [int]$RecordSec = 5
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

function Read-Serial {
    param([string]$S)
    if ($S) { return $S }
    $envFile = Join-Path $repo "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim() }
        }
    }
    throw "Set PNS_ADB_SERIAL or -Serial"
}

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $argv = @()
    if ($Serial) { $argv += "-s", $Serial }
    $argv += $AdbArgs
    & adb @argv
}

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\macro_video_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk) | Out-Null
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 2>$null | Out-Null
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") 2>$null | Out-Null
}

Invoke-Adb @("shell", "am", "force-stop", $pkg) 2>$null | Out-Null
Invoke-Adb @("logcat", "-c") 2>$null | Out-Null

$rec = [Math]::Max(3, [Math]::Min($RecordSec, 30))
Invoke-Adb @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "false",
    "--es", "pns_preview_dial", "MACRO",
    "--ei", "pns_preview_automation_in_app_video_sec", "$rec",
    "--es", "pns_preview_imaging_profile", "standard_pro"
) 2>&1 | Out-Null

Write-Host "[macro_video] waiting ${WaitSec}s for macro video session + in-app record..."
Start-Sleep -Seconds $WaitSec

Invoke-Adb @("shell", "am", "force-stop", $pkg) 2>$null | Out-Null

$logPath = Join-Path $outDir "logcat.txt"
Invoke-Adb @(
    "logcat", "-d", "-v", "threadtime",
    "-s", "PNS.ChromeUx:I", "PNS.AdbValidation:I", "PNS.MCVideoRec:I", "AndroidRuntime:E"
) 2>&1 | Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$macroVideo = $log -match "macroVideo=true"
$inAppOk = $log -match "inAppVideoSaved ok=true"
$uwSwitch = $log -match "macroMode autoSwitchUW"

$results = [ordered]@{
    macroVideoLog = [bool]$macroVideo
    inAppVideoSaved = [bool]$inAppOk
    macroUwSwitch = [bool]$uwSwitch
    outDir = $outDir
    serial = $Serial
}
$results | ConvertTo-Json | Set-Content (Join-Path $outDir "results.json") -Encoding utf8

Write-Host ""
Write-Host "=== Macro video verify (15.31) ===" -ForegroundColor Cyan
Write-Host "  macroVideo=true     : $(if ($macroVideo) { 'PASS' } else { 'FAIL' })"
Write-Host "  inAppVideoSaved     : $(if ($inAppOk) { 'PASS' } else { 'FAIL' })"
Write-Host "  macroMode autoSwitchUW: $(if ($uwSwitch) { 'PASS' } else { 'WARN (device-dependent)' })"
Write-Host "  artifacts: $outDir"

$pass = $macroVideo -and $inAppOk
if (-not $pass) { exit 1 }
exit 0
