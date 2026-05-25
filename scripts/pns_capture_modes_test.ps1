<#
.SYNOPSIS
  Sprint **CC.1** — burst composed stills + pre-capture ring log smoke on USB device.

.EXAMPLE
  .\scripts\pns_capture_modes_test.ps1 -BurstCount 3 -BurstIntervalMs 400
#>
param(
    [string]$Serial = "",
    [int]$BurstCount = 3,
    [int]$BurstIntervalMs = 400,
    [int]$WaitSec = 90,
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
$outDir = Join-Path $projRoot "hfr-runs\capture_modes_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_capture_modes.txt"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ei pns_preview_burst_count $BurstCount `
    --ei pns_preview_burst_interval_ms $BurstIntervalMs `
    --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null

Write-Host "[capture_modes_test] waiting ${WaitSec}s for burst n=$BurstCount intervalMs=$BurstIntervalMs..."
Start-Sleep -Seconds $WaitSec
& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.CaptureStill:I" "PNS.ZslRing:I" "PNS.ChromeUx:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$burstBegin = $hay -match "captureBurst begin count=$BurstCount"
$burstOk = [regex]::Matches($hay, "captureBurst burst \d+/$BurstCount ok=true").Count -ge $BurstCount
$finished = $hay -match "finished burst automation n=$BurstCount"

$pass = $burstBegin -and $burstOk -and $finished
Write-Host "CAPTURE_MODES_TEST: burstBegin=$burstBegin burstOk=$burstOk finished=$finished PASS=$pass"
Write-Host "Artifacts: $outDir"
if (-not $pass) { exit 1 }
exit 0
