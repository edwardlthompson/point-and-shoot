<#
.SYNOPSIS
  Sprint **AS.1** — cold preview + scripted in-app video with hi-fi audio; assert videoAudioProfile in logcat.

.EXAMPLE
  .\scripts\pns_audio_quality_test.ps1
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 55,
    [int]$RecordSec = 4,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
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
& adb @adbPrefix shell pm grant $pkg android.permission.RECORD_AUDIO 2>$null | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\audio_quality_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_audio.txt"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

$rec = [Math]::Max(1, [Math]::Min($RecordSec, 120))
& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_primary_photo false `
    --ei pns_preview_automation_in_app_video_sec $rec `
    --ez pns_preview_audio_hifi true `
    --ez pns_preview_audio_wind true `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[audio_quality_test] waiting ${WaitSec}s..."
Start-Sleep -Seconds $WaitSec
& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.Audio:I" "VideoRecordingController:I" 2>$null | Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$profileOk = ($hay -match "videoAudioProfile.*hiFi=true") -or ($hay -match "audioCapturePrepare.*hiFi=true")
$rateOk = ($hay -match "sampleRate=96000") -or ($hay -match "sampleRate=48000")
$videoOk = $hay -match "inAppVideoSaved ok=true"

@{
    audioProfileOk = ($profileOk -and $rateOk)
    inAppVideoOk = $videoOk
    logPath = $logPath
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir "audio_quality_test.json") -Encoding utf8

Write-Host "[audio_quality_test] artifacts -> $outDir"
if (-not $profileOk -or -not $rateOk -or -not $videoOk) {
    Write-Host "[audio_quality_test] FAIL profileOk=$profileOk rateOk=$rateOk videoOk=$videoOk"
    exit 1
}
Write-Host "[audio_quality_test] PASS"
exit 0
