#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.29** — Night dial NightScape multi-frame JPEG stack → AVIF.

.EXAMPLE
  .\scripts\pns_nightscape_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$WaitSec = 120
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

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\nightscape_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Null
    adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
}

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null
adb -s $Serial logcat -c 2>$null | Out-Null

adb -s $Serial shell am start -W -n "$pkg/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --es pns_preview_dial NIGHT `
    --es pns_preview_imaging_profile standard_pro `
    --ez pns_preview_composed_still true 2>&1 | Out-Null

Write-Host "[nightscape] waiting ${WaitSec}s for NightScape stack..."
Start-Sleep -Seconds $WaitSec

adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.NightScape:I PNS.AdbValidation:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$log = Get-Content $logPath -Raw

$stackBegin = $log -match "nightScape begin|stack begin frames="
$frameProgress = ([regex]::Matches($log, "frame=\d+/\d+")).Count -ge 2
$stackOk = $log -match "nightScape ok=true|stack ok=true"
$tonalSaved = $log -match "nightScape nightscape_smoke ok=true saved=content://"
$avifSaved = $log -match "\.avif|ext=avif.*saved=.*\.avif"
$pass = $stackBegin -and $frameProgress -and $stackOk -and $tonalSaved

$gate = [ordered]@{
    pass = $pass
    stackBegin = [bool]$stackBegin
    frameProgress = [bool]$frameProgress
    stackOk = [bool]$stackOk
    tonalSaved = [bool]$tonalSaved
    avifSaved = [bool]$avifSaved
    outDir = $outDir
}
$gate | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $outDir "gate.json")

if ($pass) {
    Write-Host "NIGHTSCAPE VERIFY: PASS ($outDir)"
    exit 0
}
Write-Host "NIGHTSCAPE VERIFY: FAIL ($outDir)"
exit 1
