# pns_raw_video_verify.ps1
# Sprint 13.6 — scripted RAW video (.mcraw) record + pull gate.
#
# Usage:
#   .\scripts\pns_raw_video_verify.ps1 -Serial <serial>

param(
    [string]$Serial = "",
    [int]$RecordSec = 5,
    [int]$WaitSec = 35,
    [string]$CameraId = "2",
    [int]$MinBytes = 65536,
    [int]$MinFrames = 1,
    [switch]$SkipInstall,
    [switch]$PullMcraw
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") {
            return $t.Substring($eq + 1).Trim()
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $projRoot "hfr-runs\raw_video_verify_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$adb = "adb"
$adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }
function Invoke-AdbCmd { & $adb @adbArgs @args }

Write-Host "=== PNS RAW video verify (13.6) ===" -ForegroundColor Cyan
Write-Host "Artifacts: $outDir"

$devices = & $adb devices 2>&1 | Select-String "device$"
if (-not $devices) { Write-Error "No ADB device connected." }

$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    Write-Host "Building debug APK..."
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug" | Out-Host
}
if (-not $SkipInstall -and (Test-Path -LiteralPath $apk)) {
    Write-Host "Installing $apk..."
    Invoke-AdbCmd install -r -t $apk 2>&1 | Out-Null
}

Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
Start-Sleep -Milliseconds 600
Invoke-AdbCmd logcat -c 2>$null

Write-Host "Launching preview: RAW video ${RecordSec}s (camera id=$CameraId)..."
Invoke-AdbCmd shell am start -W -n "dev.pointandshoot/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_primary_photo false `
    --ei pns_preview_video_raw_sec $RecordSec `
    --es pns_preview_imaging_profile standard_pro `
    --es pns_preview_camera_id $CameraId `
    --ei pns_preview_video_fps 30 2>&1 | Out-Null

$totalWait = $RecordSec + $WaitSec
Write-Host "Waiting ${totalWait}s..."
Start-Sleep -Seconds $totalWait

$logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
$logLines | Set-Content "$outDir\logcat.txt" -Encoding UTF8

$rawStart = $logLines -match "rawVideoStart"
$rawSaved = $logLines -match "rawVideoSaved ok=true"
$framesMatch = [regex]::Match($logLines, "rawVideoSaved ok=true frames=(\d+)")
$frameCount = if ($framesMatch.Success) { [int]$framesMatch.Groups[1].Value } else { 0 }
$bytesMatch = [regex]::Match($logLines, "rawVideoSaved ok=true frames=\d+ bytes=(\d+)")
$fileBytes = if ($bytesMatch.Success) { [long]$bytesMatch.Groups[1].Value } else { 0 }
$noMcRecorder = -not ($logLines -match "inAppVideoSaved ok=true")
$codecErrors = $logLines -match "CAMERA_DISCONNECTED|rawVideo start failed"

Write-Host "  rawVideoStart       : $rawStart"
Write-Host "  rawVideoSaved       : $rawSaved"
Write-Host "  frames              : $frameCount"
Write-Host "  bytes (log)         : $fileBytes"
Write-Host "  no encoded inAppVideoSaved : $noMcRecorder"

$magicOk = $false
$pulledBytes = 0L
$pulledPath = ""
if ($rawSaved) {
    $dcimPath = "/storage/emulated/0/DCIM/Point & Shoot"
    $lsOut = (Invoke-AdbCmd shell "ls '$dcimPath/'" 2>&1) | Where-Object { $_ -match "\.mcraw" } | Select-Object -Last 1
    $newestFile = if ($lsOut) { ([string]$lsOut).Trim() } else { $null }
    if ($newestFile) {
        $realPath = "$dcimPath/$newestFile"
        $hexHead = (Invoke-AdbCmd shell "xxd -l 8 -p '$realPath' 2>/dev/null" 2>&1) -join "" -replace '\s', ''
        $magicOk = $hexHead.StartsWith("504e4d5241575631")
        Write-Host "  device mcraw        : $newestFile magicHex=$hexHead magicOk=$magicOk"
        if ($PullMcraw) {
            $pullDir = "$outDir\pulled"
            New-Item -ItemType Directory -Force -Path $pullDir | Out-Null
            Write-Host "  pulling full file (may be multi-GB)..."
            Invoke-AdbCmd pull $realPath "$pullDir\$newestFile" 2>&1 | Out-Null
            $local = Join-Path $pullDir $newestFile
            if (Test-Path -LiteralPath $local) {
                $pulledBytes = (Get-Item -LiteralPath $local).Length
                $pulledPath = $local
            }
        }
    }
}
$sizeForGate = [Math]::Max($fileBytes, $pulledBytes)

$overallPass = $rawStart -and $rawSaved -and ($frameCount -ge $MinFrames) -and
    ($sizeForGate -ge $MinBytes) -and $magicOk -and -not $codecErrors

$result = [ordered]@{
    schema = "raw_video_verify.v1"
    sprint = "13.6"
    timestamp = $stamp
    passed = $overallPass
    rawVideoStart = [bool]$rawStart
    rawVideoSaved = [bool]$rawSaved
    frameCount = $frameCount
    logBytes = $fileBytes
    pulledBytes = $pulledBytes
    magicOk = $magicOk
    pulledPath = $pulledPath
    codecErrors = [bool]$codecErrors
}
$result | ConvertTo-Json | Set-Content "$outDir\results.json" -Encoding UTF8

if ($overallPass) {
    Write-Host "GATE: PASS" -ForegroundColor Green
} else {
    Write-Host "GATE: FAIL" -ForegroundColor Red
    exit 1
}

Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
