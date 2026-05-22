#!/usr/bin/env pwsh
<#
.SYNOPSIS
  Sprint PO.1 — profile preview memory during scripted capture on USB device.

.DESCRIPTION
  One cold preview session: optional scripted RAW still, grep PNS.MemoryProfiler / PNS.Bitmap,
  dumpsys meminfo. Writes hfr-runs/memory_profiler_*.
#>
[CmdletBinding()]
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [int]$SettleSec = 8,
    [int]$RawPollSec = 55
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$outDir = Join-Path $projRoot ("hfr-runs/memory_profiler_{0:yyyyMMdd_HHmmss}" -f (Get-Date))
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

function Save-Logcat([string]$OutPath) {
    $tagLines = if ($adbSerial) {
        @(& adb -s $adbSerial shell "logcat -d -t 120000 *:S PNS.MemoryProfiler:I PNS.MemoryProfiler:D PNS.Bitmap:D PNS.GalleryIndex:I PNS.CaptureStill:I PNS.AdbValidation:I" 2>&1)
    } else {
        @(adb shell "logcat -d -t 120000 *:S PNS.MemoryProfiler:I PNS.MemoryProfiler:D PNS.Bitmap:D PNS.GalleryIndex:I PNS.CaptureStill:I PNS.AdbValidation:I" 2>&1)
    }
    $tagLines | Out-File -FilePath $OutPath -Encoding utf8
    return ($tagLines -join "`n")
}

$adbSerial = Read-PnsSerial
if ($adbSerial) { Write-Host "[memory_profiler] serial=$adbSerial" }

if (-not $SkipAssemble) {
    & (Join-Path $projRoot "scripts\pns_gradlew.ps1") :app:assembleDebug | Out-Host
}
if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk)
}

Invoke-Adb @("shell", "am", "force-stop", $pkg)
Invoke-Adb @("logcat", "-c")
Invoke-Adb @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "true",
    "--ei", "pns_preview_raw_count", "1",
    "--ez", "pns_preview_raw_still_fast", "true"
)
Start-Sleep -Seconds $SettleSec

$deadline = (Get-Date).AddSeconds($RawPollSec)
$captureOk = $false
while ((Get-Date) -lt $deadline) {
    $tail = if ($adbSerial) {
        & adb -s $adbSerial shell "logcat -d -t 4000 *:S PNS.AdbValidation:I" 2>&1
    } else {
        adb shell "logcat -d -t 4000 *:S PNS.AdbValidation:I" 2>&1
    }
    if ($tail -match 'captureRawStill 1/1 ok=true') {
        $captureOk = $true
        break
    }
    Start-Sleep -Seconds 2
}

Start-Sleep -Seconds 2
$logPath = Join-Path $outDir "logcat_memory_profiler.txt"
$logText = Save-Logcat $logPath

$meminfoPath = Join-Path $outDir "dumpsys_meminfo.txt"
if ($adbSerial) { & adb -s $adbSerial shell "dumpsys meminfo $pkg" 2>&1 | Out-File $meminfoPath -Encoding utf8 }
else { adb shell "dumpsys meminfo $pkg" 2>&1 | Out-File $meminfoPath -Encoding utf8 }

$profilerOk = $logText -match 'PNS\.MemoryProfiler'
$profilerStartOk = $logText -match 'preview_session_start|Starting memory profiling'
$profilerStopOk = $logText -match 'preview_session_stop|Memory profiling completed'
$leakOk = $logText -match 'PNS\.Bitmap.*leakCheck component=PreviewEngine ok'
$criticalOk = -not ($logText -match 'Critical memory pressure')
$pass = $profilerOk -and $captureOk -and $criticalOk -and ($profilerStartOk -or $profilerStopOk)

$gate = @{
    pass = $pass
    profilerAny = $profilerOk
    profilerStartOk = $profilerStartOk
    profilerStopOk = $profilerStopOk
    bitmapLeakCheckOk = $leakOk
    noCriticalPressure = $criticalOk
    captureRawOk = $captureOk
    serial = $adbSerial
    artifacts = $outDir
} | ConvertTo-Json
$gate | Out-File (Join-Path $outDir "memory_profiler_gate.json") -Encoding utf8

Invoke-Adb @("shell", "am", "force-stop", $pkg)
Write-Host "[memory_profiler] pass=$pass profiler=$profilerOk capture=$captureOk critical=$criticalOk leak=$leakOk"
Write-Host "[memory_profiler] artifacts: $outDir"
if (-not $pass) { exit 1 }
