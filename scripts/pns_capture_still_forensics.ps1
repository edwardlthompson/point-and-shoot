<#
.SYNOPSIS
  Reproduce sequential RAW stills on a USB device and pull logcat for DNG/save failures.

.DESCRIPTION
  Optionally installs **app-debug.apk**, cold-starts preview with **`pns_preview_dial=H`** (forces
  preview below 120 fps so the RAW ImageReader session matches **pns_adb_preview_validate** style runs)
  and **`pns_preview_raw_count`**, waits, then writes **logcat_raw_still_forensics.txt** (pid-scoped dump
  plus ring tail). Inspect **`PNS.CaptureStill`**, **`PNS.StillBoundary`**, **`PNS.AdbValidation`**, **`PNS.Dng`** via
  **adb logcat** or extend this script.

.PARAMETER Serial
  adb **-s** serial. Omit to use **scripts/pns_adb_device.env** (**PNS_ADB_SERIAL**).

.PARAMETER RawCount
  **--ei pns_preview_raw_count** (default **6**).

.PARAMETER WaitSec
  Scenario wall wait. **0** = max(95, 40 + RawCount * 20).

.PARAMETER SkipInstall
  Skip **adb install** when the debug APK is already on device.

.PARAMETER Fast
  Pass **`--ez pns_preview_raw_still_fast true`** so the app uses shorter ADB RAW settle/poll (dev smoke).
  When **-WaitSec** is omitted, uses a shorter default wait than the full forensics run.

.EXAMPLE
  .\scripts\pns_capture_still_forensics.ps1
  .\scripts\pns_capture_still_forensics.ps1 -RawCount 3 -WaitSec 100 -SkipInstall
  .\scripts\pns_capture_still_forensics.ps1 -Fast -RawCount 1 -WaitSec 40 -SkipInstall
#>
param(
    [string]$Serial = "",
    [int]$RawCount = 6,
    [int]$WaitSec = 0,
    [switch]$SkipInstall,
    [switch]$Fast
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) {
            continue
        }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) {
            continue
        }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($k -eq "PNS_ADB_SERIAL") {
            return $v
        }
    }
    return $null
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs
    }
    else {
        & adb @CmdArgs
    }
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE"
    }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs 2>$null
    }
    else {
        & adb @CmdArgs 2>$null
    }
}

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host ('[capture_still_forensics] PNS_ADB_SERIAL from pns_adb_device.env -> ' + $Serial)
    }
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk - run .\scripts\pns_gradlew.ps1 :app:assembleDebug"
}

Invoke-Adb @("devices", "-l")

if (-not $SkipInstall) {
    Write-Host ('[capture_still_forensics] install ' + $apk)
    Invoke-Adb @("install", "-r", $apk)
}

Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_IMAGES")
Invoke-AdbIgnore @("shell", "logcat", "-G", "32M")

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\capture_still_forensics_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if ($WaitSec -le 0) {
    if ($Fast) {
        $WaitSec = [Math]::Max(38, 22 + $RawCount * 12)
    }
    else {
        $WaitSec = [Math]::Max(95, 40 + $RawCount * 20)
    }
}

$fastTag = if ($Fast) { " fast=1" } else { "" }
Write-Host ('[capture_still_forensics] raw_still_x' + $RawCount + ' dial=H wait=' + $WaitSec + 's' + $fastTag + ' -> ' + $outDir)

Invoke-Adb @("logcat", "-c")
# Best-effort: some transports return non-zero when the app is already stopped or the shell flakes.
Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
Start-Sleep -Milliseconds 600
if ($Fast) {
    $amArgs = @(
        "shell", "am", "start", "-n", "${pkg}/.MainActivity",
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_dial", "H",
        "--ei", "pns_preview_raw_count", "$RawCount",
        "--ez", "pns_preview_raw_still_fast", "true"
    )
}
else {
    $amArgs = @(
        "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_dial", "H",
        "--ei", "pns_preview_raw_count", "$RawCount"
    )
}
Invoke-Adb @amArgs
Start-Sleep -Seconds $WaitSec

$logPath = Join-Path $outDir "logcat_raw_still_forensics.txt"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "SilentlyContinue"
$fallbackTail = if ($Fast) { 8000 } else { 120000 }
$tagTail = if ($Fast) { 8000 } else { 80000 }
$chosenPid = $null
for ($try = 0; $try -lt 25; $try++) {
    if ($try -gt 0) {
        Start-Sleep -Milliseconds 200
    }
    $pidOfOut = if ($Serial) {
        (& adb -s $Serial shell pidof $pkg 2>&1 | Out-String)
    }
    else {
        (& adb shell pidof $pkg 2>&1 | Out-String)
    }
    $pidTokens = @( ($pidOfOut.Trim() -split '\s+') | Where-Object { $_ -match '^\d+$' } )
    $tryPid = $null
    foreach ($t in $pidTokens) {
        if ($null -eq $tryPid -or [int64]$t -gt [int64]$tryPid) {
            $tryPid = $t
        }
    }
    if ($tryPid -match '^\d+$') {
        $chosenPid = $tryPid
        break
    }
}

$sb = New-Object System.Text.StringBuilder
if ($chosenPid -match '^\d+$') {
    if (-not $Fast) {
        Write-Host ('[capture_still_forensics] logcat -d --pid=' + $chosenPid)
        $pidLines = if ($Serial) {
            @(& adb -s $Serial logcat -d --pid=$chosenPid 2>&1)
        }
        else {
            @(adb logcat -d --pid=$chosenPid 2>&1)
        }
        foreach ($ln in $pidLines) {
            [void]$sb.AppendLine($ln)
        }
        [void]$sb.AppendLine("--- supplement: logcat -d -t $fallbackTail ---")
    }
    else {
        Write-Host ('[capture_still_forensics] skip pid log (-Fast); pid=' + $chosenPid)
        [void]$sb.AppendLine("--- pid log skipped (-Fast); pid=$chosenPid ---")
    }
}
$tailLines = if ($Serial) {
    @(& adb -s $Serial logcat -d -t $fallbackTail 2>&1)
}
else {
    @(adb logcat -d -t $fallbackTail 2>&1)
}
foreach ($ln in $tailLines) {
    [void]$sb.AppendLine($ln)
}
# Host-side filter (device `adb shell logcat` can stall on some OEM builds).
[void]$sb.AppendLine("--- supplement: host tag-filtered ---")
$hostTagArgs = @(
    "logcat", "-d", "-t", "$tagTail", "*:S",
    "PNS.AdbValidation:I", "PNS.CaptureStill:W", "PNS.StillBoundary:I", "PNS.Preview:I", "PNS.Cam:I",
    "PNS.Reader:W", "PNS.Dng:D", "PNS.Storage:D", "AndroidRuntime:E"
)
$tagLines = if ($Serial) {
    @(& adb -s $Serial @hostTagArgs 2>&1)
}
else {
    @(adb @hostTagArgs 2>&1)
}
foreach ($ln in $tagLines) {
    [void]$sb.AppendLine($ln)
}

[System.IO.File]::WriteAllText($logPath, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
$ErrorActionPreference = $prevEap

Write-Host ('[capture_still_forensics] wrote ' + $logPath)

$summaryPath = Join-Path $outDir "failure_lines.txt"
$patterns = @(
    "PNS.CaptureStill",
    "PNS.StillBoundary",
    "captureRawStill",
    "PNS.AdbValidation",
    "Unsupported image format",
    "No RAW buffer",
    "AndroidRuntime",
    "FATAL EXCEPTION"
)
$sumSb = New-Object System.Text.StringBuilder
foreach ($p in $patterns) {
    [void]$sumSb.AppendLine("==== $p ====")
    Select-String -LiteralPath $logPath -Pattern $p -SimpleMatch -ErrorAction SilentlyContinue |
        ForEach-Object { [void]$sumSb.AppendLine($_.Line) }
    [void]$sumSb.AppendLine("")
}
[System.IO.File]::WriteAllText($summaryPath, $sumSb.ToString(), [System.Text.UTF8Encoding]::new($false))
Write-Host ('[capture_still_forensics] wrote ' + $summaryPath)

$saveFails = @(Select-String -LiteralPath $logPath -Pattern "captureRawStill save ok=false" -SimpleMatch -ErrorAction SilentlyContinue)
$okHits = @(Select-String -LiteralPath $logPath -Pattern "captureRawStill " -SimpleMatch -ErrorAction SilentlyContinue | Where-Object { $_.Line -match "ok=true" })
$jsonPath = Join-Path $outDir "capture_still_forensics.json"
$json = [ordered]@{
    schema                        = "pns.capture_still_forensics.v1"
    generatedAtUtc                = [DateTime]::UtcNow.ToString("o")
    outDir                        = $outDir
    rawCount                      = $RawCount
    waitSec                       = $WaitSec
    logArtifact                   = "logcat_raw_still_forensics.txt"
    summaryArtifact               = "failure_lines.txt"
    captureStillSaveFailureCount  = $saveFails.Count
    adbValidationOkTrueApprox     = $okHits.Count
}
$json | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host ('[capture_still_forensics] wrote ' + $jsonPath + ' saveFailures=' + $saveFails.Count + ' okTrue~=' + $okHits.Count)
Write-Host '[capture_still_forensics] OK'
