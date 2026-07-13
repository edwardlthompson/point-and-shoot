<#
.SYNOPSIS
  Fleet DNG exposure bisect — cold preview + scripted RAW still with matrix ADB extras.

.EXAMPLE
  .\scripts\pns_dng_fleet_exposure_bisect.ps1 -Cell E03 -FocalMmSlot 14 -Serial 8bf09993
#>
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet("E01", "E02", "E03", "E04", "E05", "E08", "E09", "E10", "E11", "PS01", "baseline")]
    [string]$Cell,
    [int]$FocalMmSlot = 14,
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$WaitSec = 70
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

if (-not $Serial) {
    $envFile = Join-Path $repo "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim() }
        }
    }
}
if (-not $Serial) {
    $devs = @(adb devices | Select-String "`tdevice$" | ForEach-Object { ($_ -split "\s+")[0] })
    if ($devs.Count -eq 1) { $Serial = $devs[0] }
}
if (-not $Serial) { throw "Pass -Serial or set PNS_ADB_SERIAL" }

$cellExtra = @{
    baseline = ""
    E01      = ""
    E02      = ""
    E03      = "--ez pns_preview_dng_skip_ae_lock true"
    E04      = "--ei pns_preview_dng_after_stop_debounce_ms 420"
    E05      = "--ez pns_preview_dng_skip_ae_regions true"
    E08      = "--ei pns_preview_dng_ae_comp_steps 1"
    E09      = "--ez pns_preview_dng_precapture_still_template true"
    E10      = "--ez pns_preview_jpeg_companion false"
    E11      = "--ez pns_preview_dng_skip_still_iq true"
    PS01     = "--ez pns_preview_dng_proshot_pipeline true"
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $repo "hfr-runs\dng_fleet_exposure_${Cell}_${stamp}"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not $SkipInstall) {
    if (-not (Test-Path $apk)) { throw "Missing $apk" }
    adb -s $Serial install -r -t $apk | Out-Host
}

$pkg = "dev.pointandshoot"
adb -s $Serial shell "pm unstop --user 0 $pkg 2>/dev/null; pm enable --user 0 $pkg 2>/dev/null"
adb -s $Serial shell "am force-stop $pkg"
Start-Sleep -Seconds 1
adb -s $Serial logcat -c

# Single string — Windows PS corrupts Start-Process / adb shell with string[] (see photo_capture_verify).
$extra = $cellExtra[$Cell]
$am =
    "am start -W -n ${pkg}/.MainActivity --activity-clear-task " +
    "--es pns_screen preview --es pns_preview_dial Auto " +
    "--ei pns_preview_raw_count 1 --es pns_preview_focal_mm_slot $FocalMmSlot " +
    "--ez pns_preview_primary_photo true"
if ($extra) { $am = "$am $extra" }

Write-Host "Cell=$Cell"
Write-Host "am: $am"
adb -s $Serial shell $am | Out-Host

$deadline = (Get-Date).AddSeconds($WaitSec)
$ok = $false
$logPath = Join-Path $outDir "logcat.txt"
$needle = "captureRawStill 1/1 ok=true saved="
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 4
    adb -s $Serial exec-out "logcat -d" | Out-File -FilePath $logPath -Encoding utf8
    $text = Get-Content $logPath -Raw -ErrorAction SilentlyContinue
    if ($text -and $text.Contains($needle)) {
        $ok = $true
        break
    }
    if ($text -match "captureRawStill 1/1 ok=false") { break }
}

# Pull newest P&S DNG newer than run start when possible
$remoteList = adb -s $Serial shell "ls -t '/sdcard/DCIM/Point & Shoot/'*.dng 2>/dev/null | head -3"
$remote = ($remoteList | Select-Object -First 1).ToString().Trim()
if ($remote -and $ok) {
    adb -s $Serial pull $remote (Join-Path $outDir "pns_${Cell}.dng") | Out-Host
}

# Grep needles into summary
$summaryPath = Join-Path $outDir "needles.txt"
if (Test-Path $logPath) {
    Select-String -Path $logPath -Pattern "focalMmSlot=|aePrecapture|proshotPipeline|ProShotPipeline|rawStillDualTarget|dng save path=|dng save diag|bisect |skipStillIq|aeCompSteps=|captureRawStill" |
        Select-Object -Last 40 |
        ForEach-Object { $_.Line } |
        Set-Content $summaryPath -Encoding utf8
}

adb -s $Serial shell "am force-stop $pkg; am force-stop com.riseupgames.proshot2"

$gate = [ordered]@{
    cell      = $Cell
    serial    = $Serial
    focalMm   = $FocalMmSlot
    captureOk = $ok
    outDir    = $outDir
    remoteDng = $remote
}
$gate | ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding utf8
Write-Host ($gate | ConvertTo-Json)
if (-not $ok) { exit 1 }
exit 0
