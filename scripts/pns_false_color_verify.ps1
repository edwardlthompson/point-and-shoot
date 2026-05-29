#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.21** — false color overlay on preview (HUD false_color mode) + screencap.

.EXAMPLE
  .\scripts\pns_false_color_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$SettleSec = 15
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

function Set-HudStringPref {
    param([string]$Xml, [string]$Name, [string]$Value)
    $pattern = "<string name=`"$Name`">[^<]*</string>"
    $replacement = "<string name=`"$Name`">$Value</string>"
    if ($Xml -match $Name) { return ($Xml -replace $pattern, $replacement) }
    return ($Xml -replace "</map>", "    $replacement`r`n</map>")
}

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\false_color_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

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
$hud = Set-HudStringPref $hud "false_color_mode" "false_color"
$tmpHud = [System.IO.Path]::GetTempFileName() + ".xml"
[System.IO.File]::WriteAllText($tmpHud, $hud, [System.Text.UTF8Encoding]::new($false))
adb -s $Serial push $tmpHud /data/local/tmp/pns_hud_fc.xml | Out-Null
adb -s $Serial shell "run-as $pkg cp /data/local/tmp/pns_hud_fc.xml $hudPath" | Out-Null
Remove-Item $tmpHud -Force -ErrorAction SilentlyContinue

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null
adb -s $Serial logcat -c 2>$null | Out-Null

adb -s $Serial shell am start -W -n "$pkg/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_primary_photo true `
    --es pns_preview_imaging_profile standard_pro `
    --ei pns_preview_camera_id 2 2>&1 | Out-Null

Write-Host "[false_color] settle ${SettleSec}s..."
Start-Sleep -Seconds $SettleSec

$capPath = Join-Path $outDir "false_color_preview.png"
& (Join-Path $repo "scripts\pns_device_screencap.ps1") -Serial $Serial -OutPath $capPath | Out-Host

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.PreviewSessionCtx:I PNS.Cam:I PNS.ChromeUx:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$wantYuv = $log -match "wantYuv=true"
$falseColorSession = $log -match "falseColor|false_color|falseColorEnabled"
$screencapOk = Test-Path $capPath
$pass = $wantYuv -and $screencapOk

$gate = [ordered]@{
    pass = $pass
    wantYuv = [bool]$wantYuv
    falseColorLog = [bool]$falseColorSession
    screencap = $capPath
    outDir = $outDir
}
$gate | ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

if (-not $pass) {
    Write-Host "FAIL false color gate — $outDir" -ForegroundColor Red
    exit 1
}
Write-Host "PASS false color gate — $outDir" -ForegroundColor Green
