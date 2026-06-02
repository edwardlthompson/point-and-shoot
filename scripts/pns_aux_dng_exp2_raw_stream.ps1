# Experiment 2: logical camera 0, scripted RAW still, compare raw_stream picks (Plan: aux DNG triage).
param(
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$dir = Join-Path $projRoot "hfr-runs\aux_dng_exp2_rawstream_$ts"
New-Item -ItemType Directory -Force -Path $dir | Out-Null

foreach ($rawStream in @("raw_sensor_only", "raw12_only")) {
    adb -s $Serial shell am force-stop $pkg | Out-Null
    Start-Sleep -Milliseconds 800
    adb -s $Serial shell logcat -c | Out-Null
    Write-Host "[exp2] raw_stream=$rawStream ..."
    adb -s $Serial shell am start -W -n "${pkg}/.MainActivity" `
        --activity-clear-task `
        --es pns_screen preview `
        --es pns_preview_dial H `
        --ei pns_preview_raw_count 1 `
        --es pns_preview_imaging_profile standard_pro `
        --es pns_preview_camera_id 0 `
        --es pns_preview_raw_stream $rawStream `
        --ez pns_preview_raw_still_fast true | Out-Null
    Start-Sleep -Seconds 50
    $pidStr = ([string](adb -s $Serial shell pidof -s $pkg)).Trim()
    $out = Join-Path $dir "log_${rawStream}.txt"
    if ($pidStr -match "^\d+$") {
        adb -s $Serial shell logcat -d -v threadtime --pid $pidStr -t 30000 | Out-File -Encoding utf8 $out
    }
    else {
        "no pid" | Out-File -Encoding utf8 $out
    }
}

Write-Host "[exp2] artifacts -> $dir"
