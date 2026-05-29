#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.18** — ZSL ring RGB histogram + "ZSL" badge with pre-capture buffer enabled.

  Patches HUD prefs (histogram + RGB + pre-capture buffer), cold-starts photo preview,
  waits for ZSL ring + histogram path, screencaps finder overlay.

.EXAMPLE
  .\scripts\pns_rgb_histogram_verify.ps1 -Serial b5214fc6
#>
param(
    [string]$Serial = "",
    [int]$SettleSec = 20,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

function Read-Serial {
    param([string]$S)
    if ($S) { return $S }
    $envFile = Join-Path $repo "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim() }
        }
    }
    throw "Set PNS_ADB_SERIAL or -Serial"
}

function Set-HudBoolPref {
    param([string]$Xml, [string]$Name, [bool]$Value)
    $v = if ($Value) { "true" } else { "false" }
    $pattern = "<boolean name=`"$Name`" value=`"[^`"]*`" />"
    $replacement = "<boolean name=`"$Name`" value=`"$v`" />"
    if ($Xml -match [regex]::Escape($Name)) {
        return ($Xml -replace $pattern, $replacement)
    }
    return ($Xml -replace "</map>", "    $replacement`r`n</map>")
}

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\rgb_histogram_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "=== PNS RGB / ZSL Histogram Verify (Sprint 15.18) ===" -ForegroundColor Cyan

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Null
    adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
}

$hudPath = "/data/data/$pkg/shared_prefs/pns_hud_settings.xml"
$hud = (adb -s $Serial shell "run-as $pkg cat $hudPath" 2>&1) -join "`n"
if ($hud -notmatch "<map>") { throw "missing pns_hud_settings.xml" }
$hud = Set-HudBoolPref $hud "show_histogram" $true
$hud = Set-HudBoolPref $hud "show_rgb_histogram" $true
$hud = Set-HudBoolPref $hud "pre_capture_buffer_enabled" $true
$tmpHud = [System.IO.Path]::GetTempFileName() + ".xml"
[System.IO.File]::WriteAllText($tmpHud, $hud, [System.Text.UTF8Encoding]::new($false))
adb -s $Serial push $tmpHud /data/local/tmp/pns_hud_hist.xml | Out-Null
adb -s $Serial shell "run-as $pkg cp /data/local/tmp/pns_hud_hist.xml $hudPath" | Out-Null
Remove-Item $tmpHud -Force -ErrorAction SilentlyContinue

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null
adb -s $Serial logcat -c 2>$null | Out-Null

adb -s $Serial shell am start -W -n "$pkg/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_primary_photo true `
    --es pns_preview_imaging_profile standard_pro `
    --ei pns_preview_camera_id 2 2>&1 | Out-Null

Write-Host "Waiting ${SettleSec}s for ZSL ring + histogram..."
Start-Sleep -Seconds $SettleSec

$capPath = Join-Path $outDir "preview_histogram_screencap.png"
& (Join-Path $repo "scripts\pns_device_screencap.ps1") -Serial $Serial -OutPath $capPath | Out-Host

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.Cam:I PNS.PreviewSessionCtx:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$logRaw = Get-Content $logPath -Raw

$preCapture = $logRaw -match "preCaptureBuffer enabled"
$zslRing = $logRaw -match "zsl still ring|zslStillRing|ringCapacity="
$noRgbFail = -not ($logRaw -match "zsl rgbHistogram reduce failed")
$wantYuv = $logRaw -match "wantYuv=true"
$screencapOk = Test-Path $capPath
$camErrors = $logRaw -match "CAMERA_DISCONNECTED|onError.*cameraId|FATAL EXCEPTION"

$overallPass = $preCapture -and $noRgbFail -and $screencapOk -and -not $camErrors

$result = [ordered]@{
    timestamp = (Get-Date -Format "yyyyMMdd_HHmmss")
    passed = $overallPass
    preCaptureBufferLog = [bool]$preCapture
    zslRingLog = [bool]$zslRing
    wantYuv = [bool]$wantYuv
    noZslRgbFail = [bool]$noRgbFail
    screencap = $capPath
    cameraErrors = [bool]$camErrors
}
$result | ConvertTo-Json | Set-Content (Join-Path $outDir "results.json") -Encoding UTF8

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

Write-Host ""
Write-Host "Gate results:"
Write-Host "  preCaptureBuffer log : $preCapture"
Write-Host "  ZSL ring activity    : $zslRing"
Write-Host "  wantYuv              : $wantYuv"
Write-Host "  No ZSL RGB fail      : $noRgbFail"
Write-Host "  Screencap saved      : $screencapOk"
Write-Host "  Camera errors        : $camErrors"
Write-Host ""

if ($overallPass) {
    Write-Host "GATE: PASS — screencap at $capPath (visual: RGB histogram + ZSL badge)" -ForegroundColor Green
} else {
    Write-Host "GATE: FAIL — $outDir" -ForegroundColor Red
    exit 1
}

Write-Host "Artifacts: $outDir"
