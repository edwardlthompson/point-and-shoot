<#
.SYNOPSIS
  Targeted USB probe for experimental max-resolution unlock evidence.

.DESCRIPTION
  Runs one cold preview automation pass with experimental max-res toggles enabled and collects:
    - logcat proof lines (PNS.AdbValidation / PNS.MaxResUnlock / reader + save diagnostics)
    - capture pipeline diagnostics file (MAX_RES_* events)

  Intended for invasive CPH2583 unlock experiments after root overlays/module changes.
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 95,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") { return $t.Substring($eq + 1).Trim() }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$adbPrefix = @()
if ($Serial) { $adbPrefix = @("-s", $Serial) }

$repo = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    & adb @adbPrefix install -r -t $apk 2>&1 | Out-Null
}

& adb @adbPrefix shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
& adb @adbPrefix shell pm grant $pkg android.permission.READ_MEDIA_IMAGES 2>$null | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $repo "hfr-runs\max_res_unlock_probe_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat.txt"
$diagPath = Join-Path $outDir "PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt"
$diagLastPath = Join-Path $outDir "PNS_CAPTURE_PIPELINE_DIAGNOSTICS_LAST.txt"
$summaryPath = Join-Path $outDir "max_res_unlock_probe_summary.json"
$mdPath = Join-Path $outDir "max_res_unlock_probe_summary.md"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

# Clear safe mode + prior diagnostics using root (this lane is explicitly root-gated).
& adb @adbPrefix shell su -c "rm -f /data/user/0/$pkg/shared_prefs/pns_experimental_safe_mode.xml" 2>$null | Out-Null
& adb @adbPrefix shell su -c "rm -f /data/user/0/$pkg/files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt" 2>$null | Out-Null
& adb @adbPrefix shell su -c "rm -f /data/user/0/$pkg/files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS_LAST.txt" 2>$null | Out-Null

$startArgs = @(
    "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--es", "pns_preview_imaging_profile", "standard_pro",
    "--es", "pns_preview_still_resolution_mode", "max_resolution",
    "--es", "pns_preview_dial", "H",
    "--ei", "pns_preview_raw_count", "1",
    "--ez", "pns_preview_raw_still_fast", "true",
    "--ez", "pns_preview_experimental_master", "true",
    "--ez", "pns_preview_experimental_max_res_unlock", "true",
    "--ez", "pns_preview_experimental_vendor_session", "true",
    "--ez", "pns_preview_force_safe_mode", "false"
)
& adb @adbPrefix @startArgs 2>&1 | Out-Null

Write-Host "[max_res_unlock_probe] waiting ${WaitSec}s..."
Start-Sleep -Seconds $WaitSec

& adb @adbPrefix exec-out logcat -d 2>$null | Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell run-as $pkg cat files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt 2>$null |
    Out-File -LiteralPath $diagPath -Encoding utf8
& adb @adbPrefix shell run-as $pkg cat files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS_LAST.txt 2>$null |
    Out-File -LiteralPath $diagLastPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$lines = @(Get-Content -LiteralPath $logPath -ErrorAction SilentlyContinue)
$diagLines = @()
if (Test-Path -LiteralPath $diagPath) { $diagLines += @(Get-Content -LiteralPath $diagPath -ErrorAction SilentlyContinue) }
if (Test-Path -LiteralPath $diagLastPath) { $diagLines += @(Get-Content -LiteralPath $diagLastPath -ErrorAction SilentlyContinue) }

function Last-Line([string]$pattern, [string[]]$source) {
    $hit = @($source | Where-Object { $_ -match $pattern } | Select-Object -Last 1)
    if ($hit) { return $hit[0] }
    return $null
}

$seedLine = Last-Line "preview (preseed|seeded) experimental master=(true|false) maxRes=(true|false) vendorSession=(true|false)" $lines
$stillResSeedLine = Last-Line "preview seeded stillResolutionMode=(\S+) \(adb\)" $lines
$unlockLine = Last-Line "maxResUnlock active=(true|false) applied=(true|false).*reason=([^\s]+)" $lines
$reqKeyLine = Last-Line "maxResUnlock requestVendorKeyApplied name=com\.oplus\.QCFARemosaicType" $lines
$reqKeyAbsentLine = Last-Line "maxResUnlock requestVendorKeyAbsent name=com\.oplus\.QCFARemosaicType" $lines
$sessionKeyLine = Last-Line "maxResUnlock sessionVendorTemplateApplied cam=" $lines
$rawReaderLine = Last-Line "RAW ImageReader (\d+x\d+) format=(\d+)" $lines
$jpegReaderLine = Last-Line "JPEG ImageReader (\d+x\d+)" $lines
$dngDiagLine = Last-Line "dng save diag .*rawWxH=(\d+x\d+)" $lines
$rawSavedLine = Last-Line "captureRawStill .*ok=true saved=" $lines

$diagEventSensor = Last-Line "MAX_RES_SENSOR_MODE" $diagLines
$diagEventReq = Last-Line "MAX_RES_VENDOR_REQ" $diagLines
$diagEventRawReader = Last-Line "MAX_RES_RAW_READER" $diagLines
$diagEventJpegReader = Last-Line "MAX_RES_JPEG_READER" $diagLines

$unlockApplied = $false
$unlockReason = $null
if ($unlockLine) {
    $unlockApplied = ([regex]::Match($unlockLine, "applied=(true|false)").Groups[1].Value -eq "true")
    $unlockReason = ([regex]::Match($unlockLine, "reason=([^\s]+)").Groups[1].Value)
}

$rawReaderWxH = if ($rawReaderLine) { ([regex]::Match($rawReaderLine, "RAW ImageReader (\d+x\d+) format=(\d+)")).Groups[1].Value } else { $null }
$rawReaderFmt = if ($rawReaderLine) { ([regex]::Match($rawReaderLine, "RAW ImageReader (\d+x\d+) format=(\d+)")).Groups[2].Value } else { $null }
$jpegReaderWxH = if ($jpegReaderLine) { ([regex]::Match($jpegReaderLine, "JPEG ImageReader (\d+x\d+)")).Groups[1].Value } else { $null }
$rawWxH = if ($dngDiagLine) { ([regex]::Match($dngDiagLine, "rawWxH=(\d+x\d+)")).Groups[1].Value } else { $null }

$pass =
    ($null -ne $seedLine) -and
    ($null -ne $stillResSeedLine) -and
    ($null -ne $unlockLine) -and
    ($null -ne $reqKeyLine -or $null -ne $reqKeyAbsentLine -or $null -ne $diagEventReq) -and
    ($null -ne $rawReaderLine -or $null -ne $diagEventRawReader) -and
    ($null -ne $dngDiagLine -or $null -ne $rawSavedLine)

$summary = [ordered]@{
    schema = "pns.max_res_unlock_probe.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    serial = if ($Serial) { $Serial } else { "default" }
    pass = $pass
    evidence = [ordered]@{
        seedLine = $seedLine
        stillResolutionSeedLine = $stillResSeedLine
        unlockLine = $unlockLine
        unlockApplied = $unlockApplied
        unlockReason = $unlockReason
        sessionVendorTemplateLine = $sessionKeyLine
        requestVendorKeyLine = $reqKeyLine
        requestVendorKeyAbsentLine = $reqKeyAbsentLine
        rawReaderLine = $rawReaderLine
        jpegReaderLine = $jpegReaderLine
        dngDiagLine = $dngDiagLine
        rawSavedLine = $rawSavedLine
        diagEventSensor = $diagEventSensor
        diagEventReq = $diagEventReq
        diagEventRawReader = $diagEventRawReader
        diagEventJpegReader = $diagEventJpegReader
    }
    parsed = [ordered]@{
        rawWxH = $rawWxH
        rawReaderWxH = $rawReaderWxH
        rawReaderFmt = $rawReaderFmt
        jpegReaderWxH = $jpegReaderWxH
    }
    artifacts = [ordered]@{
        outDir = $outDir
        logPath = $logPath
        diagPath = $diagPath
        diagLastPath = $diagLastPath
    }
}

$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8

$md = @(
    "# Max Resolution Unlock Probe",
    "",
    "- **PASS:** $pass",
    "- **Unlock applied:** $unlockApplied",
    "- **Unlock reason:** $unlockReason",
    "- **RAW reader:** $rawReaderWxH fmt=$rawReaderFmt",
    "- **JPEG reader:** $jpegReaderWxH",
    "- **DNG diag rawWxH:** $rawWxH",
    "",
    "## Needles",
    "- seed: $seedLine",
    "- unlock: $unlockLine",
    "- session key: $sessionKeyLine",
    "- request key: $reqKeyLine",
    "- diag MAX_RES_SENSOR_MODE: $diagEventSensor",
    "- diag MAX_RES_VENDOR_REQ: $diagEventReq",
    "- diag MAX_RES_RAW_READER: $diagEventRawReader",
    "- diag MAX_RES_JPEG_READER: $diagEventJpegReader",
    "",
    "Summary JSON: $summaryPath",
    "Artifacts root: $outDir"
)
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "MAX_RES_UNLOCK_PROBE: pass=$pass unlockApplied=$unlockApplied rawReader=$rawReaderWxH rawWxH=$rawWxH"
Write-Host "Artifacts: $outDir"
if (-not $pass) { exit 1 }
exit 0

