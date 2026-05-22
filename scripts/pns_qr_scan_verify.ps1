#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Sprint 14.4 — verify preview QR dial mode on USB device.

.DESCRIPTION
  Cold-starts preview with photo-primary + pns_preview_dial=QR; asserts PNS.ChromeUx
  qrScanMode=active and YUV wantQr in session context. Optional decode line if a code
  is visible to the camera. Writes hfr-runs/qr_scan_verify_*.
#>
[CmdletBinding()]
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [int]$SettleSec = 38
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$outDir = Join-Path $projRoot ("hfr-runs/qr_scan_verify_{0:yyyyMMdd_HHmmss}" -f (Get-Date))
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
            @(& adb -s $adbSerial shell "logcat -d -t 80000 *:S PNS.ChromeUx:I PNS.QrScan:I PNS.PreviewSessionCtx:I" 2>&1)
        }
        else {
            @(adb shell "logcat -d -t 80000 *:S PNS.ChromeUx:I PNS.QrScan:I PNS.PreviewSessionCtx:I" 2>&1)
        }
        $sb = New-Object System.Text.StringBuilder
        foreach ($ln in $tail) { [void]$sb.AppendLine($ln) }
        [void]$sb.AppendLine("--- supplement: tag-filtered PNS.ChromeUx + PNS.QrScan + PNS.PreviewSessionCtx ---")
        foreach ($ln in $tagLines) { [void]$sb.AppendLine($ln) }
        $utf8 = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($OutPath, $sb.ToString(), $utf8)
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

$adbSerial = Read-PnsSerial
if ($adbSerial) { Write-Host "[qr_scan_verify] serial=$adbSerial" }

if (-not $SkipAssemble) {
    & (Join-Path $projRoot "scripts\pns_gradlew.ps1") :app:assembleDebug | Out-Host
}
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk"
}

if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk)
}
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")
Invoke-AdbIgnore @("shell", "logcat", "-G", "64M")

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
Invoke-AdbIgnore @("shell", "logcat", "-c")
Start-Sleep -Milliseconds 600

Invoke-Adb @(
    "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "true",
    "--es", "pns_preview_dial", "QR"
)
Write-Host "[qr_scan_verify] settle ${SettleSec}s..."
Start-Sleep -Seconds $SettleSec

$logPath = Join-Path $outDir "logcat_qr_scan.txt"
Save-LogcatTail $logPath

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)

$logText = [System.IO.File]::ReadAllText($logPath)
$qrModeOk = $logText -match 'PNS\.ChromeUx.*qrScanMode=active'
$wantYuvOk = $logText -match 'PNS\.PreviewSessionCtx.*dial=Qr.*wantYuv=true'
$decodeOk = $logText -match 'PNS\.QrScan.*decode ok'
$pass = $qrModeOk -and $wantYuvOk

$gate = [ordered]@{
    pass = $pass
    qrScanModeOk = $qrModeOk
    wantYuvOk = $wantYuvOk
    decodeOk = $decodeOk
    outDir = $outDir
    logcat = $logPath
}
$gate | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $outDir "gate.json") -Encoding utf8

Write-Host "[qr_scan_verify] qrScanMode=$qrModeOk wantYuv=$wantYuvOk decode=$decodeOk pass=$pass"
Write-Host "[qr_scan_verify] artifacts: $outDir"
if (-not $pass) { exit 1 }
