#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Sprint 14.2 — verify PreviewTopStatusBar logs on USB device.

.DESCRIPTION
  Cold-starts preview in video mode, runs in-app video automation (record clip),
  dumps logcat for PNS.ChromeUx statusBar= / readoutMode=video / audioMeters=true.
  Writes hfr-runs/video_status_bar_verify_*.
#>
[CmdletBinding()]
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [int]$AutomationVideoSec = 3,
    [int]$SettleSec = 38
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$outDir = Join-Path $projRoot ("hfr-runs/video_status_bar_verify_{0:yyyyMMdd_HHmmss}" -f (Get-Date))
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }

function Read-PnsSerial {
    if ($Serial) { return $Serial }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim() }
        }
    }
    return $null
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($adbSerial) { & adb -s $adbSerial @CmdArgs }
    else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($adbSerial) { & adb -s $adbSerial @CmdArgs 2>$null }
    else { & adb @CmdArgs 2>$null }
}

function Save-LogcatTail([string]$OutPath) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $mixedTail = 250000
        $tail = if ($adbSerial) { @(& adb -s $adbSerial shell "logcat -d -t $mixedTail" 2>&1) }
        else { @(adb shell "logcat -d -t $mixedTail" 2>&1) }
        $tagLines = if ($adbSerial) {
            @(& adb -s $adbSerial shell "logcat -d -t 80000 *:S PNS.ChromeUx:I" 2>&1)
        }
        else {
            @(adb shell "logcat -d -t 80000 *:S PNS.ChromeUx:I" 2>&1)
        }
        $sb = New-Object System.Text.StringBuilder
        foreach ($ln in $tail) { [void]$sb.AppendLine($ln) }
        [void]$sb.AppendLine("--- supplement: tag-filtered PNS.ChromeUx ---")
        foreach ($ln in $tagLines) { [void]$sb.AppendLine($ln) }
        $utf8 = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($OutPath, $sb.ToString(), $utf8)
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

$adbSerial = Read-PnsSerial
if ($adbSerial) { Write-Host "[video_status_bar_verify] serial=$adbSerial" }

if (-not $SkipAssemble -and (-not (Test-Path -LiteralPath $apk) -or -not $SkipInstall)) {
    & (Join-Path $projRoot "scripts\pns_gradlew.ps1") :app:assembleDebug | Out-Host
}
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk"
}

if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk)
}
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO")
Invoke-AdbIgnore @("shell", "logcat", "-G", "64M")

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
Invoke-AdbIgnore @("shell", "logcat", "-c")
Start-Sleep -Milliseconds 600

Invoke-Adb @(
    "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "false",
    "--ei", "pns_preview_automation_in_app_video_sec", "$AutomationVideoSec"
)
Write-Host "[video_status_bar_verify] settle ${SettleSec}s (preview + automation clip)..."
Start-Sleep -Seconds $SettleSec

$logPath = Join-Path $outDir "logcat_chrome_status_bar.txt"
Save-LogcatTail $logPath

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)

$logText = [System.IO.File]::ReadAllText($logPath)
$statusBarOk = $logText -match 'PNS\.ChromeUx.*statusBar=visible'
$readoutVideoOk = $logText -match 'PNS\.ChromeUx.*readoutMode=video'
$audioMetersOk = $logText -match 'PNS\.ChromeUx.*audioMeters=true'
$pass = $statusBarOk -and $readoutVideoOk -and $audioMetersOk

$gate = [ordered]@{
    pass = $pass
    statusBarOk = $statusBarOk
    readoutVideoOk = $readoutVideoOk
    audioMetersOk = $audioMetersOk
    settleSec = $SettleSec
    automationVideoSec = $AutomationVideoSec
    outDir = $outDir
    logcat = $logPath
}
$gate | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $outDir "gate.json") -Encoding utf8

Write-Host "[video_status_bar_verify] statusBar=$statusBarOk readoutVideo=$readoutVideoOk audioMeters=$audioMetersOk pass=$pass"
Write-Host "[video_status_bar_verify] artifacts: $outDir"
if (-not $pass) { exit 1 }
