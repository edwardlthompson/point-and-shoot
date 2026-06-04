# Variable-aperture readout gate: cold preview, cycle F chip via pns_preview_aperture_cycles, grep logcat.
# Primary proof device: Sony Xperia PRO-I main camera (cameraId=2, f/2.0 <-> f/4.0).
param(
    [string]$Serial,
    [string]$CameraId = "2",
    [int]$Cycles = 2,
    [int]$WaitSec = 28,
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
$outDir = Join-Path $repo "hfr-runs\aperture_readout_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
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
$amArgs = @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--es", "pns_preview_camera_id", $CameraId,
    "--ei", "pns_preview_aperture_cycles", [string]$Cycles
)
adb -s $Serial @amArgs | Out-Host

Start-Sleep -Seconds $WaitSec
$logPath = Join-Path $outDir "logcat_aperture.txt"
adb -s $Serial logcat -d -s PNS.ChromeUx:I PNS.AdbValidation:I | Out-File -Encoding utf8 $logPath

adb -s $Serial shell am force-stop $pkg | Out-Null

$text = Get-Content $logPath -Raw
$cycleMatches = [regex]::Matches($text, "apertureCycle cameraId=$CameraId ")
$init =
    $text -match "apertureInit cameraId=$CameraId[^\n]*variable=true" -or
    $cycleMatches.Count -ge 1
$cycleOpen = $text -match "apertureCycle cameraId=$CameraId[^\n]*f/2\.0 -> f/4\.0"
$cycleStop = $text -match "apertureCycle cameraId=$CameraId[^\n]*f/4\.0 -> f/2\.0"
$adbOk = $text -match "PNS\.AdbValidation:.*apertureCycle ok=true"
$automation = $text -match "apertureAutomation=cycles=$Cycles"

$ok = $init -and ($cycleMatches.Count -ge $Cycles) -and $cycleOpen -and $cycleStop -and $adbOk -and $automation
$summary = @{
    ok = $ok
    cameraId = $CameraId
    cycles = $Cycles
    apertureCycleLineCount = $cycleMatches.Count
    apertureInitVariable = $init
    cycleF2toF4 = $cycleOpen
    cycleF4toF2 = $cycleStop
    adbValidationOk = $adbOk
    apertureAutomation = $automation
    logcat = $logPath
} | ConvertTo-Json
$summary | Out-File (Join-Path $outDir "verify.json") -Encoding utf8
Write-Host $summary
if (-not $ok) { exit 1 }
