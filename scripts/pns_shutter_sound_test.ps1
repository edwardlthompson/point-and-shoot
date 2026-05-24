<#
.SYNOPSIS
  Sprint **AS.2** — cold preview + composed still; assert shutterSound ok=true in logcat.

.EXAMPLE
  .\scripts\pns_shutter_sound_test.ps1 -ShutterPack digital
#>
param(
    [string]$Serial = "",
    [string]$ShutterPack = "digital",
    [int]$WaitSec = 70,
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

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\shutter_sound_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_shutter.txt"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_composed_still true `
    --es pns_preview_shutter_sound_pack $ShutterPack `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[shutter_sound_test] waiting ${WaitSec}s for composed still + shutterSound..."
Start-Sleep -Seconds $WaitSec
& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.ShutterSound:I" "PNS.CaptureStill:I" 2>$null | Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$shutterOk = $hay -match "shutterSound ok=true pack=$ShutterPack"
$stillOk = $hay -match "composedStill" -or $hay -match "captureComposedStill" -or $hay -match "CaptureStill"

@{
    shutterSoundOk = $shutterOk
    stillActivityOk = $stillOk
    pack = $ShutterPack
    logPath = $logPath
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir "shutter_sound_test.json") -Encoding utf8

Write-Host "[shutter_sound_test] artifacts -> $outDir"
if (-not $shutterOk) {
    Write-Host "[shutter_sound_test] FAIL — expected shutterSound ok=true pack=$ShutterPack"
    exit 1
}
Write-Host "[shutter_sound_test] PASS"
exit 0
