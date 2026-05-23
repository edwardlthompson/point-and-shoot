#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint VF.1 — MediaCodec capability probe + optional AV1 in-app record smoke.

.DESCRIPTION
    Cold-starts preview, greps **PNS.VideoCapProbe** for **av1=** and HEVC perf points.
    When **av1=true** and **-RunAv1Record**, runs a short AV1 clip via
    **pns_preview_video_av1** + **pns_preview_automation_in_app_video_sec**.

.PARAMETER Serial
    ADB serial (optional; uses scripts/pns_adb_device.env).

.PARAMETER SkipAssemble / SkipInstall
.PARAMETER RunAv1Record
    Record ~5s AV1 clip when hardware encoder is advertised (default: probe-only).
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [switch]$RunAv1Record,
    [int]$RecordSec = 5,
    [int]$WaitSec = 55
)

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

$envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
if ($Serial -eq "" -and (Test-Path $envFile)) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim().Trim('"') }
    }
}

$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"

function Invoke-AdbCmd {
    param([Parameter(Mandatory = $true)][string[]]$Cmd)
    if ($Serial -ne "") { & adb -s $Serial @Cmd } else { & adb @Cmd }
}

function Get-Logcat([string[]]$Tags) {
    $cmd = @("logcat", "-d", "-v", "brief") + $Tags
    Invoke-AdbCmd $cmd
}

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path $apk)) { throw "Missing $apk" }
if (-not $SkipInstall) {
    Invoke-AdbCmd @("install", "-r", "-t", $apk) | Out-Null
    Invoke-AdbCmd @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 2>$null | Out-Null
    Invoke-AdbCmd @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") 2>$null | Out-Null
}

$utc = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $projRoot "hfr-runs\video_format_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$previewStart = @(
    "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "false",
    "--es", "pns_preview_imaging_profile", "standard_pro"
)

Invoke-AdbCmd @("shell", "am", "force-stop", $pkg) | Out-Null
Invoke-AdbCmd @("logcat", "-c") | Out-Null
Invoke-AdbCmd $previewStart | Out-Null

Start-Sleep -Seconds $WaitSec
$log = Get-Logcat @("-s", "PNS.VideoCapProbe:I", "PNS.MCVideoRec:I", "PNS.ChromeUx:I", "PNS.AdbValidation:I")
$log | Set-Content (Join-Path $outDir "logcat_video_format.txt")

$capLine = ($log | Select-String "capProbeResult").Line | Select-Object -Last 1
$av1Line = ($log | Select-String "av1Encoders=").Line | Select-Object -Last 1
$supportsAv1 = $false
if ($capLine -match "av1=true") { $supportsAv1 = $true }
if ($av1Line) { $supportsAv1 = $true }

$av1RecordOk = $null
if ($RunAv1Record -and $supportsAv1) {
    Invoke-AdbCmd @("shell", "am", "force-stop", $pkg) | Out-Null
    Invoke-AdbCmd @("logcat", "-c") | Out-Null
    $rec = [Math]::Max(1, [Math]::Min($RecordSec, 30))
    $av1Start = $previewStart + @(
        "--ez", "pns_preview_video_av1", "true",
        "--ei", "pns_preview_video_encode_w", "1920",
        "--ei", "pns_preview_video_encode_h", "1080",
        "--ei", "pns_preview_video_fps", "60",
        "--ei", "pns_preview_automation_in_app_video_sec", "$rec"
    )
    Invoke-AdbCmd $av1Start | Out-Null
    Start-Sleep -Seconds ($rec + 25)
    $log2 = Get-Logcat @("-s", "PNS.MCVideoRec:I", "PNS.AdbValidation:I", "PNS.ChromeUx:I")
    $log2 | Add-Content (Join-Path $outDir "logcat_av1_record.txt")
    $av1RecordOk =
        (($log2 | Select-String "mime=video/av01").Count -gt 0) -and
        (($log2 | Select-String "inAppVideoSaved ok=true").Count -gt 0)
} elseif ($RunAv1Record -and -not $supportsAv1) {
    Write-Host "[video_format_test] AV1 record skipped — no HW encoder"
}

$summary = [ordered]@{
    timestampUtc = (Get-Date).ToUniversalTime().ToString("o")
    supportsAv1 = $supportsAv1
    capProbeLine = [string]$capLine
    av1EncodersLine = [string]$av1Line
    av1RecordOk = $av1RecordOk
    outDir = $outDir
}
$summary | ConvertTo-Json | Set-Content (Join-Path $outDir "summary.json")

Invoke-AdbCmd @("shell", "am", "force-stop", $pkg) | Out-Null

Write-Host "[video_format_test] supportsAv1=$supportsAv1 outDir=$outDir"
if (-not $capLine) {
    Write-Error "VF.1 FAIL: missing PNS.VideoCapProbe capProbeResult"
    exit 1
}
if ($RunAv1Record -and $supportsAv1 -and $av1RecordOk -ne $true) {
    Write-Error "VF.1 FAIL: AV1 record smoke failed"
    exit 1
}
Write-Host "VF.1 PASS"
exit 0
