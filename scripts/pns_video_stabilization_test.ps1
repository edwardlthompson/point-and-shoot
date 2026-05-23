#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint VF.2 — verify OIS/EIS hybrid logs on preview video path.

.PARAMETER Serial
.PARAMETER SkipAssemble / SkipInstall
.PARAMETER WaitSec
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$WaitSec = 35
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

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path $apk)) { throw "Missing $apk" }
if (-not $SkipInstall) {
    Invoke-AdbCmd @("install", "-r", "-t", $apk) | Out-Null
    Invoke-AdbCmd @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 2>$null | Out-Null
}

$utc = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $projRoot "hfr-runs\video_stabilization_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Invoke-AdbCmd @("shell", "am", "force-stop", $pkg) | Out-Null
Invoke-AdbCmd @("logcat", "-c") | Out-Null
Invoke-AdbCmd @(
    "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "false",
    "--ez", "pns_preview_video_stabilization", "true",
    "--ei", "pns_preview_video_fps", "60",
    "--es", "pns_preview_imaging_profile", "standard_pro"
) | Out-Null

Start-Sleep -Seconds $WaitSec
$log = Invoke-AdbCmd @("logcat", "-d", "-v", "brief", "-s", "PNS.VideoEffects:I", "PNS.Stabilization:W")
$log | Set-Content (Join-Path $outDir "logcat_stabilization.txt")

$stabLine = ($log | Select-String "videoStabilization").Line | Select-Object -Last 1
$oisOn = $stabLine -match "oisOn=true"
$eisAdvertised = $stabLine -match "eisAdvertised=true"
$pass = ($null -ne $stabLine) -and ($oisOn -or $eisAdvertised)

@{
    timestampUtc = (Get-Date).ToUniversalTime().ToString("o")
    stabilizationLine = [string]$stabLine
    oisOn = $oisOn
    eisAdvertised = $eisAdvertised
    pass = $pass
    outDir = $outDir
} | ConvertTo-Json | Set-Content (Join-Path $outDir "summary.json")

Invoke-AdbCmd @("shell", "am", "force-stop", $pkg) | Out-Null

if (-not $pass) {
    Write-Error "VF.2 FAIL: missing PNS.VideoEffects stabilization line (ois/eis)"
    exit 1
}
Write-Host "VF.2 PASS: $stabLine"
exit 0
