# Highlight (H dial) metering gate: cold photo-primary preview with dial H.
# Default locks ISO 400 to exercise §2.3 highlight-EV shutter chase (user path).
# Use -AutoAeOnly for AE-compensation path without readout ISO lock.
param(
    [string]$Serial,
    [int]$WaitSec = 50,
    [int]$LockedIso = 400,
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [switch]$AutoAeOnly
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

# Stream logcat for the whole window — end-of-run dumps lose early SessionCtx under FleetVisibility spam.
$rawPath = Join-Path $outDir "logcat_raw.txt"
$adbExe = (Get-Command adb).Source
$logProc = Start-Process -FilePath $adbExe -ArgumentList @("-s", $Serial, "exec-out", "logcat", "-v", "brief") `
    -RedirectStandardOutput $rawPath -RedirectStandardError (Join-Path $outDir "logcat_stderr.txt") `
    -NoNewWindow -PassThru

try {
    $amArgs = @(
        "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_primary_photo", "true",
        "--es", "pns_preview_dial", "H",
        "--es", "pns_preview_camera_id", "2"
    )
    if (-not $AutoAeOnly -and $LockedIso -gt 0) {
        $amArgs += @("--ei", "pns_preview_readout_iso", "$LockedIso")
    }
    adb -s $Serial @amArgs | Out-Host
    Start-Sleep -Seconds $WaitSec
}
finally {
    if ($null -ne $logProc -and -not $logProc.HasExited) {
        Stop-Process -Id $logProc.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 400
    }
    adb -s $Serial shell am force-stop $pkg | Out-Null
}

$logPath = Join-Path $outDir "logcat_highlight_meter.txt"
if (Test-Path $rawPath) {
    Select-String -Path $rawPath -Pattern "PNS\.(Cam|ChromeUx|AdbValidation|HighlightAe|Preview)|highlightMeter|readoutChase|wantYuv|dial=H" |
        ForEach-Object { $_.Line } |
        Out-File -Encoding utf8 $logPath
} else {
    New-Item -ItemType File -Path $logPath -Force | Out-Null
}

$text = Get-Content $logPath -Raw
if ($null -eq $text) { $text = "" }
$dialH = $text -match "dial=H"
$wantYuv = $text -match "wantYuv=true"
$yuvAttached = $text -match "yuvAttached=true"
$highlightMeter = $text -match "highlightMeter ev="
$aeComp = $text -match "highlightMeter[^\n]*aeComp="
$highlightEvChase = $text -match "readoutChase[^\n]*highlightEv="
$vendorExtraOnly = $text -match "PNS\.HighlightAe:.*path=vendor_extra"
$softwarePathOk = -not $vendorExtraOnly

$meterOk = $highlightMeter -and $aeComp
$yuvImplied = $meterOk -or $highlightEvChase
$sessionOk = $dialH -and ($wantYuv -or $yuvImplied) -and ($yuvAttached -or $yuvImplied)
# Locked-ISO runs must prove highlight-EV chase (or AE-comp logs). Auto-AE-only accepts
# session attach + dial H (flat scenes may never cross AE deadband).
$ok = $softwarePathOk -and $(
    if ($AutoAeOnly) {
        $sessionOk
    } else {
        $sessionOk -and ($highlightEvChase -or $meterOk)
    }
)
$summary = @{
    ok = $ok
    dialH = $dialH
    wantYuv = ($wantYuv -or $yuvImplied)
    yuvAttached = ($yuvAttached -or $yuvImplied)
    highlightMeter = $highlightMeter
    aeCompLogged = $aeComp
    highlightEvChase = $highlightEvChase
    softwarePathOk = $softwarePathOk
    vendorExtraPath = $vendorExtraOnly
    lockedIso = $(if ($AutoAeOnly) { $null } else { $LockedIso })
    logcat = $logPath
} | ConvertTo-Json
$summary | Out-File (Join-Path $outDir "verify.json") -Encoding utf8
Write-Host $summary
if (-not $ok) { exit 1 }
