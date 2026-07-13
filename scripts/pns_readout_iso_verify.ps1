# Locks readout ISO via pns_preview_readout_iso and checks logcat for applied exposure.
param(
    [string]$Serial,
    [int]$Iso = 400,
    [int]$WaitSec = 25,
    [switch]$SkipInstall,
    [switch]$SkipAssemble
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

$envFile = Join-Path $repo "scripts\pns_adb_device.env"
if (-not $Serial -and (Test-Path $envFile)) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim() }
    }
}
if (-not $Serial) { throw "Set PNS_ADB_SERIAL in scripts/pns_adb_device.env or pass -Serial" }

$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\readout_iso_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Host
}

adb -s $Serial logcat -c | Out-Null
adb -s $Serial shell am force-stop $pkg | Out-Null
adb -s $Serial shell am start -W -n "$pkg/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --es pns_preview_camera_id 2 `
    --ei pns_preview_readout_iso $Iso | Out-Host

Start-Sleep -Seconds $WaitSec
$logPath = Join-Path $outDir "logcat_readout_iso.txt"
adb -s $Serial exec-out logcat -d -v brief 2>$null |
    Select-String -Pattern "PNS\.(Cam|ChromeUx|AdbValidation|Preview)" |
    ForEach-Object { $_.Line } |
    Out-File -Encoding utf8 $logPath

adb -s $Serial shell am force-stop $pkg | Out-Null

$text = Get-Content $logPath -Raw
if ($null -eq $text) { $text = "" }
$applied = $text -match "readoutAeApplied coupling=LOCKED_ISO_AUTO_SS"
$probe = $text -match "readoutIsoProbe=locked iso=$Iso"
$isoInReq = $text -match "readoutAeApplied[^\n]*iso=$Iso"

$ok = $applied -and $probe -and $isoInReq
$summary = @{
    ok = $ok
    iso = $Iso
    readoutAeApplied = $applied
    readoutIsoProbe = $probe
    isoInAppliedLine = $isoInReq
    logcat = $logPath
} | ConvertTo-Json
$summary | Out-File (Join-Path $outDir "verify.json") -Encoding utf8
Write-Host $summary
if (-not $ok) { exit 1 }
