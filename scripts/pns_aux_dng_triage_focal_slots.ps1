# One-off triage: M14 / M23 / M73 scripted RAW still + logcat + DCIM pull (native tele, not M150 crop).
# Do not wire into CI; run manually: .\scripts\pns_aux_dng_triage_focal_slots.ps1 -Serial 8bf09993
param(
    [string]$Serial = "8bf09993"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$dir = Join-Path $projRoot "hfr-runs\aux_dng_triage_$ts"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Write-Host "[aux_dng_triage] artifacts -> $dir"

if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk (run assembleDebug first)" }

adb -s $Serial install -r -t $apk | Out-Host
adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
adb -s $Serial shell logcat -G 64M 2>$null | Out-Null

$slots = @(
    @{ mm = "14";  n = "M14_uw" },
    @{ mm = "23";  n = "M23_wide" },
    @{ mm = "73";  n = "M73_tele" }
)

foreach ($slot in $slots) {
    adb -s $Serial shell am force-stop $pkg | Out-Null
    Start-Sleep -Milliseconds 900
    adb -s $Serial shell logcat -c | Out-Null
    $mm = $slot.mm
    $label = $slot.n
    Write-Host "[aux_dng_triage] focal slot $mm ($label)..."
    adb -s $Serial shell am start -W -n "${pkg}/.MainActivity" `
        --activity-clear-task `
        --es pns_screen preview `
        --es pns_preview_dial H `
        --ei pns_preview_raw_count 1 `
        --es pns_preview_imaging_profile standard_pro `
        --es pns_preview_camera_id 0 `
        --es pns_preview_focal_mm_slot $mm `
        --ez pns_preview_raw_still_fast true `
        --ez pns_preview_jpeg_companion false | Out-Host
    Start-Sleep -Seconds 52
    $pidStr = ([string](adb -s $Serial shell pidof -s $pkg)).Trim()
    $outLog = Join-Path $dir "${label}_logcat.txt"
    if ($pidStr -match "^\d+$") {
        adb -s $Serial shell logcat -d -v threadtime --pid $pidStr -t 50000 | Out-File -Encoding utf8 $outLog
    }
    else {
        "no pid for package" | Out-File -Encoding utf8 $outLog
    }
}

& (Join-Path $PSScriptRoot "pns_pull_dcim_captures.ps1") -Serial $Serial -OutDir (Join-Path $dir "dcim_pull")
Write-Host "[aux_dng_triage] done."
