<#
.SYNOPSIS
  Scripted RAW still at M14 / M23 / M73, pull each DNG, run structural_verify + logcat summary.

.DESCRIPTION
  Native tele reference is M73 (not M150). Writes hfr-runs/aux_dng_capture_analyze_<utc>/.

.EXAMPLE
  .\scripts\pns_aux_dng_capture_analyze.ps1 -Serial 8bf09993
  .\scripts\pns_aux_dng_capture_analyze.ps1 -SkipBuild -WaitSec 52
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 52,
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$NoFast,
    # ADB dial: H=Highlight (legacy automation), A/Auto for ProShot parity (no YUV highlight metering)
    [string]$PreviewDial = "A",
    [string]$FocalMmSlots = "",
    [string]$OutDir = "",
    [string[]]$ExtraAmArgs = @(),
    # Sprint 13.3f — hard-fail when color parity vs tests/fixtures/proshot_cph2655 is required.
    # Default off: 13.3g only requires capture 3/3 + dng_desktop_open_gate.py PASS.
    [switch]$RequireProshotParity
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

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
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") {
            return $t.Substring($eq + 1).Trim()
        }
    }
    return $null
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

function Invoke-AdbOut([string[]]$CmdArgs) {
    if ($Serial) { return (& adb -s $Serial @CmdArgs 2>&1 | Out-String) }
    return (& adb @CmdArgs 2>&1 | Out-String)
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir =
    if (-not [string]::IsNullOrWhiteSpace($OutDir)) {
        $OutDir
    } else {
        Join-Path $projRoot "hfr-runs\aux_dng_capture_analyze_$ts"
    }
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
if ($ExtraAmArgs.Count -gt 0) {
    Write-Host "[capture_analyze] bisect extras: $($ExtraAmArgs -join ' ')"
}

Write-Host ""
Write-Host "=== PNS aux DNG capture + analyze (M14 / M23 / M73) ===" -ForegroundColor Cyan
Write-Host "Output: $outDir"
if ($Serial) { Write-Host "Serial: $Serial" }
Write-Host ""

if (-not $SkipBuild) {
    $gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
    Write-Host "[capture_analyze] assembleDebug..."
    if (Test-Path $gw) { & $gw ":app:assembleDebug" }
    else { & "$projRoot\gradlew.bat" ":app:assembleDebug" "--no-daemon" }
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}

if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }

if (-not $SkipInstall) {
    Write-Host "[capture_analyze] install APK..."
    Invoke-Adb @("install", "-r", "-t", $apk)
}

Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") | Out-Null
Invoke-Adb @("shell", "logcat", "-G", "64M") | Out-Null

$allSlots = @(
    @{ mm = "14"; label = "uw";   local = "M14_uw.dng" },
    @{ mm = "23"; label = "wide"; local = "M23_wide.dng" },
    @{ mm = "73"; label = "tele"; local = "M73_tele.dng" }
)
$slots = $allSlots
if (-not [string]::IsNullOrWhiteSpace($FocalMmSlots)) {
    $want = @($FocalMmSlots -split "[,\s]+" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
    $slots = @($allSlots | Where-Object { $want -contains $_.mm })
    if ($slots.Count -eq 0) { throw "FocalMmSlots matched no slot: $FocalMmSlots" }
}

$captured = @()
$report = [System.Collections.Generic.List[string]]::new()
$report.Add("Aux DNG capture + analyze - $ts")
$report.Add("Preview dial (ADB): $PreviewDial")
$report.Add("Focal slots: M14 (UW), M23 (wide ref), M73 (native tele)")
$report.Add("=" * 72)

foreach ($slot in $slots) {
    $mm = $slot.mm
    $label = $slot.label
    $localName = $slot.local
    Write-Host ""
    Write-Host "[capture_analyze] === M${mm} ($label) ===" -ForegroundColor Cyan

    Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
    Start-Sleep -Milliseconds 900
    Invoke-Adb @("shell", "logcat", "-c") | Out-Null

    $amArgs = @(
        "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_dial", $PreviewDial,
        "--ei", "pns_preview_raw_count", "1",
        "--es", "pns_preview_imaging_profile", "standard_pro",
        "--es", "pns_preview_camera_id", "0",
        "--es", "pns_preview_focal_mm_slot", $mm,
        "--ez", "pns_preview_jpeg_companion", "false"
    )
    if (-not $NoFast) {
        $amArgs += @("--ez", "pns_preview_raw_still_fast", "true")
    }
    if ($ExtraAmArgs.Count -gt 0) {
        $amArgs += $ExtraAmArgs
    }
    Invoke-Adb $amArgs | Out-Host

    $epochOut = (Invoke-AdbOut @("shell", "date", "+%s")).Trim()
    $captureEpoch = [int]$epochOut - 2

    Write-Host "[capture_analyze] waiting ${WaitSec}s..."
    Start-Sleep -Seconds $WaitSec

    $logFile = Join-Path $outDir "M${mm}_${label}_logcat.txt"
    $pidStr = (Invoke-AdbOut @("shell", "pidof", "-s", $pkg)).Trim()
    if ($pidStr -match "^\d+$") {
        Invoke-Adb @("shell", "logcat", "-d", "-v", "threadtime", "--pid", $pidStr, "-t", "50000") |
            Out-File -Encoding utf8 $logFile
    } else {
        Invoke-Adb @("shell", "logcat", "-d", "-v", "threadtime", "-t", "20000") |
            Out-File -Encoding utf8 $logFile
    }

    $physId = $null
    $focalLine = Get-Content $logFile -ErrorAction SilentlyContinue |
        Select-String -Pattern "focalSlotTap=.*cameraIdAfter=(\d+)" |
        Select-Object -Last 1
    if ($focalLine -and $focalLine.Matches.Count -gt 0) {
        $physId = $focalLine.Matches[0].Groups[1].Value
    }

    $diag = Get-Content $logFile -ErrorAction SilentlyContinue |
        Select-String -Pattern "dng save diag|dng colorPatch|captureRawStill|focalSlotTap=" |
        ForEach-Object { $_.Line.Trim() }
    $report.Add("")
    $report.Add("--- M${mm} ($label) ---")
    if ($diag) {
        foreach ($line in @($diag)) { $report.Add([string]$line) }
    } else {
        $report.Add("(no capture/dng lines in logcat)")
    }

    $remoteDcim = "/sdcard/DCIM/Point & Shoot"
    $picked = $null
    $savedLine = Get-Content $logFile -ErrorAction SilentlyContinue |
        Select-String -Pattern "captureRawStill 1/1 ok=true saved=([^\s]+\.dng)" |
        Select-Object -Last 1
    if ($savedLine -and $savedLine.Matches.Count -gt 0) {
        $baseName = $savedLine.Matches[0].Groups[1].Value.Trim()
        $picked = "$remoteDcim/$baseName"
        Write-Host "[capture_analyze] logcat picked $baseName"
    }
    if (-not $picked) {
        $findOut = if ($Serial) {
            & adb -s $Serial shell "find '$remoteDcim' -name '*.dng' 2>/dev/null"
        } else {
            & adb shell "find '$remoteDcim' -name '*.dng' 2>/dev/null"
        }
        $allDngs = ($findOut -split "`n") | ForEach-Object { $_.Trim() } | Where-Object { $_ -match "\.dng$" }
        $newDngs = @()
        foreach ($d in $allDngs) {
            if ($Serial) {
                $statOut = (& adb -s $Serial shell "stat -c %Y '$d' 2>/dev/null" 2>&1 | Out-String).Trim()
            } else {
                $statOut = (& adb shell "stat -c %Y '$d' 2>/dev/null" 2>&1 | Out-String).Trim()
            }
            if ($statOut -match "^\d+$" -and [int]$statOut -ge $captureEpoch) {
                $newDngs += $d
            }
        }
        $picked = ($newDngs | Sort-Object | Select-Object -Last 1)
    }
    $localDng = Join-Path $outDir $localName
    if (-not $picked) {
        Write-Host "[capture_analyze] WARNING: no new DNG for M${mm}" -ForegroundColor Yellow
        $captured += [pscustomobject]@{ mm = $mm; label = $label; path = $null; physicalId = $physId }
        continue
    }
    Write-Host "[capture_analyze] pull $picked -> $localName"
    Invoke-Adb @("pull", $picked, $localDng)
    if (Test-Path $localDng) {
        $mb = [int]((Get-Item $localDng).Length / 1MB)
        Write-Host "[capture_analyze] saved $localName ($mb MB)"
        $captured += [pscustomobject]@{
            mm = $mm; label = $label; path = $localDng; remote = $picked; physicalId = $physId
        }
    } else {
        $captured += [pscustomobject]@{ mm = $mm; label = $label; path = $null; physicalId = $physId }
    }
}

Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Write-Host "[capture_analyze] app force-stopped"

function Get-CapturePathByPhysicalId([string]$id) {
    ($captured | Where-Object { $_.physicalId -eq $id -and $_.path } | Select-Object -First 1).path
}

# CPH2655 leaf ids: cam2=wide, cam3=UW, cam4=tele (match focal slot labels, not structural_verify labels)
$uwPath = Get-CapturePathByPhysicalId "3"
$widePath = Get-CapturePathByPhysicalId "2"
$telePath = Get-CapturePathByPhysicalId "4"
if (-not $uwPath) { $uwPath = ($captured | Where-Object { $_.label -eq "uw" } | Select-Object -First 1).path }
if (-not $widePath) { $widePath = ($captured | Where-Object { $_.label -eq "wide" } | Select-Object -First 1).path }
if (-not $telePath) { $telePath = ($captured | Where-Object { $_.label -eq "tele" } | Select-Object -First 1).path }

$report.Add("")
$report.Add("=== Pulled files ===")
foreach ($c in $captured) {
    $report.Add("  M$($c.mm) $($c.label) phys=$($c.physicalId): $($c.path)")
}

$reportPath = Join-Path $outDir "capture_report.txt"
$report | Out-File -Encoding utf8 $reportPath

$manifest = @{
    timestampUtc = $ts
    focalSlots = @("14", "23", "73")
    captures = $captured
    reportPath = $reportPath
}
$manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outDir "manifest.json") -Encoding UTF8

if (-not ($uwPath -and $widePath -and $telePath)) {
    Write-Host ""
    Write-Host "FAIL: missing one or more DNG pulls." -ForegroundColor Red
    Write-Host "  uw (cam2):   $uwPath"
    Write-Host "  wide (cam3): $widePath"
    Write-Host "  tele (cam4): $telePath"
    exit 1
}

Write-Host ""
Write-Host "[capture_analyze] dng_tiff_integrity_check.py..." -ForegroundColor Cyan
$integrityPy = Join-Path $PSScriptRoot "dng_tiff_integrity_check.py"
if (Test-Path -LiteralPath $integrityPy) {
    & python $integrityPy $uwPath $widePath $telePath 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: DNG TIFF integrity check failed (files may not open in Lightroom)." -ForegroundColor Red
        exit 1
    }
}

Write-Host ""
Write-Host "[capture_analyze] dng_desktop_open_gate.py (13.3g mandatory)..." -ForegroundColor Cyan
$openGatePy = Join-Path $PSScriptRoot "dng_desktop_open_gate.py"
$openGatePass = $false
$openGateJson = Join-Path $outDir "openability_gate.json"
if (Test-Path -LiteralPath $openGatePy) {
    $openGateOut = & python $openGatePy $uwPath $widePath $telePath 2>&1 | Out-String
    Write-Host $openGateOut
    if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: DNG desktop open gate (ACR loadability / ASN / wide-cal leak)." -ForegroundColor Red
        @{
            schema = "openability_gate.v1"
            timestampUtc = $ts
            gate = "FAIL"
            paths = @{ uw = $uwPath; wide = $widePath; tele = $telePath }
            log = $openGateOut.Trim()
        } | ConvertTo-Json -Depth 4 | Set-Content $openGateJson -Encoding UTF8
        exit 1
    }
    $openGatePass = $true
    @{
        schema = "openability_gate.v1"
        timestampUtc = $ts
        gate = "PASS"
        serial = $Serial
        paths = @{ uw = $uwPath; wide = $widePath; tele = $telePath }
        checks = @("dng_tiff_integrity", "rawpy_decode", "asn_sanity", "wide_cal_leak")
    } | ConvertTo-Json -Depth 4 | Set-Content $openGateJson -Encoding UTF8
}

Write-Host ""
Write-Host "[capture_analyze] structural_verify.py (informational; argv order is legacy cam2/3/4 labels)..." -ForegroundColor Cyan
$py = Join-Path $PSScriptRoot "structural_verify.py"
$pyText = & python $py $uwPath $widePath $telePath 2>&1 | Out-String
Write-Host $pyText
$pyExit = $LASTEXITCODE
$fmOk = $pyText -match "FM patch applied:\s+ALL PASS"
$wbOk = $pyText -match "Tele WB accuracy:\s+PASS"

$gate = @{
    timestampUtc = $ts
    uw = $uwPath
    wide = $widePath
    tele = $telePath
    fmPatch = if ($fmOk) { "PASS" } else { "FAIL" }
    teleWbAccuracy = if ($wbOk) { "PASS" } else { "FAIL" }
    structuralGate = if ($pyExit -eq 0) { "PASS" } else { "FAIL" }
}
$gate | ConvertTo-Json | Set-Content (Join-Path $outDir "gate_result.json") -Encoding UTF8

Write-Host ""
Write-Host "[capture_analyze] dng_proshot_parity_gate.py (Sprint 13.3f; informational unless -RequireProshotParity)..." -ForegroundColor Cyan
$parityPy = Join-Path $PSScriptRoot "dng_proshot_parity_gate.py"
$parityJson = Join-Path $outDir "proshot_parity_gate.json"
$refFixture = Join-Path $projRoot "tests\fixtures\proshot_cph2655"
$parityPass = $true
if (Test-Path $parityPy) {
    & python $parityPy $outDir --proshot-dir $refFixture --json-out $parityJson 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) {
        $parityPass = $false
        if ($RequireProshotParity) {
            Write-Host "FAIL: ProShot parity gate (color/luminance/integrity vs tests/fixtures/proshot_cph2655)" -ForegroundColor Red
            Write-Host "Report: $reportPath"
            exit 1
        }
        Write-Host "WARN: ProShot parity gate FAIL (expected until Sprint 13.3f fixture refresh)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Report: $reportPath"
if ($parityPass) {
    Write-Host "=== CAPTURE + OPENABILITY + PROSHOT PARITY: PASS ===" -ForegroundColor Green
} else {
    Write-Host "=== CAPTURE + OPENABILITY: PASS (parity deferred to 13.3f) ===" -ForegroundColor Green
}
exit 0
