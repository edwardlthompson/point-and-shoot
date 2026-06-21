# Highlight (H dial) metering gate: cold photo-primary preview with dial H.
param(
    [string]$Serial,
    [int]$WaitSec = 50,
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
$outDir = Join-Path $repo "hfr-runs\highlight_meter_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Host
}

adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
adb -s $Serial logcat -c | Out-Null
adb -s $Serial shell am force-stop $pkg | Out-Null
$amArgs = @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "true",
    "--es", "pns_preview_dial", "H"
)
adb -s $Serial @amArgs | Out-Host

Start-Sleep -Seconds $WaitSec
$logPath = Join-Path $outDir "logcat_highlight_meter.txt"
adb -s $Serial logcat -d -s PNS.Cam:I PNS.ChromeUx:I PNS.HighlightAe:I PNS.AdbValidation:I | Out-File -Encoding utf8 $logPath

adb -s $Serial shell am force-stop $pkg | Out-Null

$text = Get-Content $logPath -Raw
$dialH = $text -match "dial=H"
$wantYuv = $text -match "wantYuv=true"
$yuvAttached = $text -match "yuvAttached=true"
$highlightMeter = $text -match "highlightMeter ev="
$aeComp = $text -match "highlightMeter[^\n]*aeComp="
$vendorExtraOnly = $text -match "PNS\.HighlightAe:.*path=vendor_extra"
$softwarePathOk = -not $vendorExtraOnly

$ok = $dialH -and $wantYuv -and $yuvAttached -and $highlightMeter -and $aeComp -and $softwarePathOk
$summary = @{
    ok = $ok
    dialH = $dialH
    wantYuv = $wantYuv
    yuvAttached = $yuvAttached
    highlightMeter = $highlightMeter
    aeCompLogged = $aeComp
    softwarePathOk = $softwarePathOk
    vendorExtraPath = $vendorExtraOnly
    logcat = $logPath
} | ConvertTo-Json
$summary | Out-File (Join-Path $outDir "verify.json") -Encoding utf8
Write-Host $summary
if (-not $ok) { exit 1 }
