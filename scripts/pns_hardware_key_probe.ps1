<#
.SYNOPSIS
  Interactive hardware key probe — cold start engineering screen, pull HARDWARE_KEY_PROBE_LATEST.json.

.EXAMPLE
  .\scripts\pns_hardware_key_probe.ps1 -WaitSec 120
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 0,
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [switch]$RescanMatrix,
    [switch]$Manual
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

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\hardware_key_probe_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_hardware_key_probe.txt"

$autoProbe = -not $Manual
if ($WaitSec -le 0) {
    $WaitSec = if ($autoProbe) { 12 } else { 90 }
}

if ($autoProbe) {
    Write-Host "[hardware_key_probe] Auto probe: synthesize KEYCODE_CAMERA and save JSON (${WaitSec}s wait)."
} else {
    Write-Host "[hardware_key_probe] Press dedicated + shortcut hardware buttons on device, then tap Save probe JSON in app."
    Write-Host "[hardware_key_probe] Waiting ${WaitSec}s..."
}

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600
$startArgs = @(
    "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "hardwarekeyprobe"
)
if ($autoProbe) {
    $startArgs += @("--ez", "pns_auto_hardware_key_probe", "true")
}
& adb @adbPrefix @startArgs 2>&1 | Out-Null

Start-Sleep -Seconds $WaitSec
& adb @adbPrefix exec-out logcat -d -s "PNS.HardwareKeyProbe:I" "PNS.SWEEP_SIGNAL:I" 2>$null | Out-File -LiteralPath $logPath -Encoding utf8

$jsonLocal = Join-Path $outDir "HARDWARE_KEY_PROBE_LATEST.json"
$pullOk = $false
try {
    & adb @adbPrefix exec-out run-as $pkg cat files/HARDWARE_KEY_PROBE_LATEST.json 2>$null | Out-File -LiteralPath $jsonLocal -Encoding utf8
    if ((Test-Path -LiteralPath $jsonLocal) -and ((Get-Item $jsonLocal).Length -gt 20)) { $pullOk = $true }
} catch {
    $pullOk = $false
}

$probeDone = (Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue) -match "HARDWARE_KEY_PROBE_DONE"
$hasCameraKey = $false
if ($pullOk) {
    $raw = Get-Content -LiteralPath $jsonLocal -Raw
    $hasCameraKey = $raw -match '"keyCode"\s*:\s*27' -or $raw -match '"cameraKeyConfirmed"\s*:\s*true'
}

if ($RescanMatrix -and $pullOk) {
    & (Join-Path $PSScriptRoot "pns_fleet_matrix_scan.ps1") -ScanTier quick -SkipGradle -SkipInstall -Serial $Serial
}

& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

Write-Host "artifactDir=$outDir pullOk=$pullOk probeDone=$probeDone hasCameraKey=$hasCameraKey"
if (-not $pullOk) { exit 1 }
if (-not $hasCameraKey) {
    Write-Warning "No KEYCODE_CAMERA (27) in probe JSON — press dedicated camera key and Save before timeout."
    exit 1
}
exit 0
