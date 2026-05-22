<#
.SYNOPSIS
  Locked readout ISO + one composed still (DNG + tonal JPEG/AVIF); pull DCIM; luminance parity report.

  Uses the same path as the tray shutter: [captureComposedStill] (not sequential RAW-only).

.EXAMPLE
  .\scripts\pns_readout_jpeg_dng_parity.ps1 -Serial 8bf09993 -Iso 400 -WaitSec 70
#>
param(
    [string]$Serial = "",
    [int]$Iso = 400,
    [int]$WaitSec = 70,
    [string]$Dial = "A",
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

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\readout_jpeg_dng_parity_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
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
$am = @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--es", "pns_preview_dial", $Dial,
    "--ei", "pns_preview_readout_iso", "$Iso",
    "--ez", "pns_preview_composed_still", "true",
    "--ez", "pns_preview_jpeg_companion", "true",
    "--es", "pns_preview_imaging_profile", "standard_pro"
)
adb -s $Serial @am | Out-Host

Write-Host "[parity] waiting ${WaitSec}s for chase settle + composed DNG+tonal still..."
Start-Sleep -Seconds $WaitSec

$logPath = Join-Path $outDir "logcat_parity.txt"
adb -s $Serial logcat -d -s PNS.ChromeUx:I PNS.CaptureStill:I PNS.AdbValidation:I PNS.Dng:I 2>&1 |
    Out-File -Encoding utf8 $logPath

$pullDir = Join-Path $outDir "dcim_pull"
New-Item -ItemType Directory -Force -Path $pullDir | Out-Null
& (Join-Path $repo "scripts\pns_pull_dcim_captures.ps1") -Serial $Serial -OutDir $pullDir

adb -s $Serial shell am force-stop $pkg | Out-Null

$py = Join-Path $repo "scripts\readout_jpeg_dng_luminance_compare.py"
$jsonOut = Join-Path $outDir "luminance_parity.json"
if (Test-Path $py) {
    python $py --dir $pullDir --logcat $logPath --json-out $jsonOut 2>&1 |
        Tee-Object -FilePath (Join-Path $outDir "luminance_report.txt")
}

$composedOk = (Get-Content $logPath -Raw) -match "composed_smoke ok=true"
@{
    composedStillOk = $composedOk
    iso = $Iso
    dial = $Dial
    outDir = $outDir
    luminanceJson = $jsonOut
} | ConvertTo-Json | Out-File (Join-Path $outDir "gate.json") -Encoding utf8

if (-not $composedOk) {
    Write-Host "[parity] FAIL: composed_smoke ok=true not in logcat" -ForegroundColor Red
    exit 1
}
Write-Host "[parity] artifacts: $outDir" -ForegroundColor Green
