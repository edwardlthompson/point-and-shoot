# Automated DNG color matrix diagnostic for UW / wide / tele cameras.
# Fires one RAW still on each focal slot, greps PNS.Dng + PNS.CaptureStill,
# and writes a human-readable report + raw logcat to hfr-runs/dng_color_matrix_<ts>/.
#
# Usage:
#   .\scripts\pns_dng_color_matrix_probe.ps1
#   .\scripts\pns_dng_color_matrix_probe.ps1 -Serial 8bf09993 -SkipInstall
param(
    [string]$Serial    = "8bf09993",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$scriptDir  = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot   = Split-Path -Parent $scriptDir
$apk        = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg        = "dev.pointandshoot"

$resolve = Join-Path $scriptDir "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

# ── output dir ──────────────────────────────────────────────────────────────
$ts  = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$dir = Join-Path $projRoot "hfr-runs\dng_color_matrix_$ts"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
Write-Host "[dng_color_probe] artifacts -> $dir"

# ── install ──────────────────────────────────────────────────────────────────
if (-not $SkipInstall) {
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "APK not found: $apk  — run .\gradlew.bat :app:assembleDebug first"
    }
    Write-Host "[dng_color_probe] installing APK..."
    adb -s $Serial install -r -t $apk | Out-Host
}
adb -s $Serial shell pm grant $pkg android.permission.CAMERA  2>$null | Out-Null
adb -s $Serial shell pm grant $pkg android.permission.WRITE_EXTERNAL_STORAGE 2>$null | Out-Null
adb -s $Serial shell logcat -G 64M 2>$null | Out-Null

# ── focal slots to probe ─────────────────────────────────────────────────────
# mm label maps to the focal-mm-slot values the app understands.
# NOTE: session IDs confirmed from logcat — slot 14 routes session=2 (UW),
#       slot 23 routes session=3 (Wide reference), slot 73 routes session=4 (native tele).
$slots = @(
    @{ mm = "14";  label = "UW_session2"   },
    @{ mm = "23";  label = "Wide_session3" },
    @{ mm = "73";  label = "Tele_session4" }
)

$report = [System.Collections.Generic.List[string]]::new()
$report.Add("DNG Color Matrix Probe — $ts")
$report.Add("Device: $Serial")
$report.Add("="*72)

foreach ($slot in $slots) {
    $mm    = $slot.mm
    $label = $slot.label
    Write-Host ""
    Write-Host "[dng_color_probe] === $label (${mm}mm) ===" -ForegroundColor Cyan

    # stop + clear log
    adb -s $Serial shell am force-stop $pkg | Out-Null
    Start-Sleep -Milliseconds 800
    adb -s $Serial shell logcat -c | Out-Null

    # launch and trigger one RAW still
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

    Write-Host "[dng_color_probe] waiting 55 s for capture + save..."
    Start-Sleep -Seconds 55

    # collect logcat
    $pidStr = ([string](adb -s $Serial shell pidof -s $pkg)).Trim()
    $rawLog = Join-Path $dir "${label}_logcat.txt"
    if ($pidStr -match "^\d+$") {
        adb -s $Serial shell logcat -d -v threadtime --pid $pidStr | Out-File -Encoding utf8 $rawLog
    } else {
        "no PID for package $pkg" | Out-File -Encoding utf8 $rawLog
        Write-Warning "[dng_color_probe] $label — app not running after 40 s"
    }

    # extract the lines we care about
    $dngDiag      = Select-String -Path $rawLog -Pattern "dng color diag"
    $captureDiag  = Select-String -Path $rawLog -Pattern "dng save diag"
    $fmPatchDiag  = Select-String -Path $rawLog -Pattern "FM lookup|FM patch"

    $report.Add("")
    $report.Add("── $label (${mm}mm) ──")
    if ($dngDiag) {
        $report.Add("[COLOR MATRICES]")
        $dngDiag | ForEach-Object { $report.Add("  " + $_.Line) }
    } else {
        $report.Add("[COLOR MATRICES]  ** NOT FOUND — capture may not have completed **")
    }
    if ($captureDiag) {
        $report.Add("[CAPTURE DIAG]")
        $captureDiag | ForEach-Object { $report.Add("  " + $_.Line) }
    }
    if ($fmPatchDiag) {
        $report.Add("[FM PATCH]")
        $fmPatchDiag | ForEach-Object { $report.Add("  " + $_.Line) }
    }

    # pretty-print to console
    if ($dngDiag) {
        Write-Host "[dng_color_probe] $label color diag:" -ForegroundColor Green
        $dngDiag | ForEach-Object { Write-Host "  " $_.Line }
    } else {
        Write-Host "[dng_color_probe] $label — dng color diag NOT found in log" -ForegroundColor Yellow
    }
}

# ── force-stop (battery rule) ────────────────────────────────────────────────
adb -s $Serial shell am force-stop $pkg | Out-Null
Write-Host ""
Write-Host "[dng_color_probe] app force-stopped (battery rule)" -ForegroundColor DarkGray

# ── write report ─────────────────────────────────────────────────────────────
$report.Add("")
$report.Add("="*72)
$report.Add("Interpretation guide:")
$report.Add("  il1/il2   : ReferenceIlluminant (17=D65/Daylight, 21=D65, 23=D50)")
$report.Add("  cm1/cm2   : ColorMatrix (sensor->XYZ). Diagonal ~1,identity=no calib.")
$report.Add("  fm1/fm2   : ForwardMatrix (XYZ->sRGB). null = not provided by HAL.")
$report.Add("  blackLevel: SENSOR_BLACK_LEVEL_PATTERN (4 values, one per Bayer quad)")
$report.Add("  whiteLevel: SENSOR_INFO_WHITE_LEVEL")
$report.Add("")
$report.Add("Green/dark cast symptoms:")
$report.Add("  - cm1/cm2 all zeros or identity -> HAL not calibrating that camera")
$report.Add("  - il1/il2 null or wrong illuminant -> wrong white-point at decode")
$report.Add("  - blackLevel much higher than wide -> clipped shadows -> dark DNG")
$report.Add("  - whiteLevel much lower than wide -> compressed highlights")

$reportPath = Join-Path $dir "dng_color_matrix_report.txt"
$report | Out-File -Encoding utf8 $reportPath
Write-Host ""
Write-Host "[dng_color_probe] report: $reportPath" -ForegroundColor Green
Write-Host "[dng_color_probe] done."
