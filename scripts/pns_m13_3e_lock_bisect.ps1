<#
.SYNOPSIS
  Milestone 13.3e — USB lock bisect E1–E6 (L2, L3, L6, L4, L5, L7) one variable per step.

.DESCRIPTION
  Patches from 13.3g baseline, runs pns_aux_dng_capture_analyze + optional pipeline verify,
  restores sources. Runbook: docs/M13_3E_LOCK_BISECT_RUNBOOK.md

.PARAMETER Steps
  Comma-separated: E1,E2,E3,E4,E5,E6 (default all).

.PARAMETER Serial
  ADB serial (else scripts/pns_adb_device.env).

.PARAMETER SkipGradle
  Skip assembleDebug.

.PARAMETER SkipPipelineVerify
  Skip pns_capture_pipeline_verify.ps1 per step.

.EXAMPLE
  .\scripts\pns_m13_3e_lock_bisect.ps1 -Serial <serial>
#>
param(
    [string]$Steps = "E1,E2,E3,E4,E5,E6",
    [string]$Serial = "",
    [switch]$SkipGradle,
    [switch]$SkipPipelineVerify,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

$projRoot = Split-Path -Parent $PSScriptRoot
$policyPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\fleet\LegacyFleetPolicy.kt"
$previewPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\PreviewEngineScreen.kt"
$rawPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\RawCaptureSupport.kt"
foreach ($p in @($policyPath, $previewPath, $rawPath)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "Missing $p" }
}

$stepList = $Steps -split '[,;\s]+' | ForEach-Object { $_.Trim().ToUpperInvariant() } | Where-Object { $_ -match '^E[1-6]$' }

function Restore-AllSources {
    param([string]$snapDir)
    Copy-Item (Join-Path $snapDir "LegacyFleetPolicy.kt.baseline") $policyPath -Force
    Copy-Item (Join-Path $snapDir "PreviewEngineScreen.kt.baseline") $previewPath -Force
    Copy-Item (Join-Path $snapDir "RawCaptureSupport.kt.baseline") $rawPath -Force
}

function Apply-E1-L2 {
    $t = Get-Content -LiteralPath $previewPath -Raw -Encoding UTF8
    $t = $t -replace 'allowPhysicalTotalResultPairing = false', 'allowPhysicalTotalResultPairing = true'
    Set-Content -LiteralPath $previewPath -Value $t -Encoding UTF8 -NoNewline
}

function Apply-E2-L3 {
    $t = Get-Content -LiteralPath $policyPath -Raw -Encoding UTF8
    $t = $t -replace 'fun useLegacyAsnReconcileOnly\(\): Boolean = \w+', 'fun useLegacyAsnReconcileOnly(): Boolean = true'
    Set-Content -LiteralPath $policyPath -Value $t -Encoding UTF8 -NoNewline
}

function Apply-E3-L6 {
    $t = Get-Content -LiteralPath $policyPath -Raw -Encoding UTF8
    $t = $t -replace 'fun useHalColorCalibrationReconcile\(\): Boolean = \w+', 'fun useHalColorCalibrationReconcile(): Boolean = true'
    Set-Content -LiteralPath $policyPath -Value $t -Encoding UTF8 -NoNewline
}

function Apply-E4-L4 {
    $t = Get-Content -LiteralPath $previewPath -Raw -Encoding UTF8
    $t = $t -replace 'val streamHints = false', 'val streamHints = true'
    Set-Content -LiteralPath $previewPath -Value $t -Encoding UTF8 -NoNewline
}

function Apply-E5-L5 {
    $t = (Get-Content -LiteralPath $rawPath -Raw -Encoding UTF8) -replace "`r`n", "`n"
    $old = @"
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
"@
    $new = @"
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
"@
    if (-not $t.Contains($old)) { throw "E5/L5: Default RAW tier block not found in RawCaptureSupport.kt" }
    Set-Content -LiteralPath $rawPath -Value ($t.Replace($old, $new)) -Encoding UTF8 -NoNewline
}

function Apply-E6-L7 {
    $t = Get-Content -LiteralPath $previewPath -Raw -Encoding UTF8
    $t = $t -replace '!DngSaveBisectState\.skipJpegProcessingHintsOnRawStill &&', 'false && /* 13.3e E6: skip PreviewJpegProcessingHints on RAW still */'
    Set-Content -LiteralPath $previewPath -Value $t -Encoding UTF8 -NoNewline
}

$appliers = @{
    E1 = @{ lock = 'L2'; fn = { Apply-E1-L2 }; note = 'allowPhysicalTotalResultPairing=true (6 call sites)' }
    E2 = @{ lock = 'L3'; fn = { Apply-E2-L3 }; note = 'useLegacyAsnReconcileOnly=true (no-op reconcile if useReferenceAppPureDngSave)' }
    E3 = @{ lock = 'L6'; fn = { Apply-E3-L6 }; note = 'useHalColorCalibrationReconcile=true (no-op if pure ReferenceCam save)' }
    E4 = @{ lock = 'L4'; fn = { Apply-E4-L4 }; note = 'streamHints=true (§4a — capture timeout risk)' }
    E5 = @{ lock = 'L5'; fn = { Apply-E5-L5 }; note = 'Default RAW tier RAW12→RAW10→RAW_SENSOR (§2 bisect)' }
    E6 = @{ lock = 'L7'; fn = { Apply-E6-L7 }; note = 'disable PreviewJpegProcessingHints on RAW still' }
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\m13_3e_lock_bisect_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Copy-Item $policyPath (Join-Path $outDir "LegacyFleetPolicy.kt.baseline") -Force
Copy-Item $previewPath (Join-Path $outDir "PreviewEngineScreen.kt.baseline") -Force
Copy-Item $rawPath (Join-Path $outDir "RawCaptureSupport.kt.baseline") -Force

$results = @()
$scriptError = $null
try {
    foreach ($step in $stepList) {
        if (-not $appliers.ContainsKey($step)) {
            Write-Warning "Unknown step $step"
            continue
        }
        $def = $appliers[$step]
        Write-Host "=== 13.3e $step ($($def.lock)) — $($def.note) ===" -ForegroundColor Cyan
        if ($DryRun) { continue }

        Restore-AllSources -snapDir $outDir
        & $def.fn

        if (-not $SkipGradle) {
            & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
            if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed for $step" }
        }

        $stepDir = Join-Path $outDir "step_$step"
        $analyzeArgs = @{
            PreviewDial = 'A'
            NoFast = $true
            OutDir = $stepDir
        }
        if ($Serial) { $analyzeArgs['Serial'] = $Serial }
        & (Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1") @analyzeArgs
        $analyzeExit = $LASTEXITCODE

        $pipelinePass = $null
        if (-not $SkipPipelineVerify) {
            $pvArgs = @{ Fast = $true; MaxAttempts = 2; NoHistoryAppend = $true }
            if ($Serial) { $pvArgs['Serial'] = $Serial }
            & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1") @pvArgs
            $pipelinePass = ($LASTEXITCODE -eq 0)
        }

        $openPass = $false
        $openJson = Join-Path $stepDir "openability_gate.json"
        if (Test-Path -LiteralPath $openJson) {
            $oj = Get-Content $openJson -Raw | ConvertFrom-Json
            $openPass = ($oj.pass -eq $true) -or ($oj.gate -eq 'PASS')
        }

        $lc = ""
        $logFiles = @(Get-ChildItem -Path $stepDir -Filter "*_logcat.txt" -ErrorAction SilentlyContinue)
        if ($logFiles.Count -gt 0) {
            $lc = ($logFiles | ForEach-Object { Get-Content $_.FullName -Raw }) -join "`n"
        }
        $logNeedles = @{
            reconcileFalse = ($lc -match 'reconcile=false')
            wideCalFalse = ($lc -match 'wideCal=false')
            rawFmt32 = ($lc -match 'rawFmt=32')
            rawFmt37 = ($lc -match 'rawFmt=37')
            pairedPhysical = ($lc -match 'pairedPhysical=true')
        }

        $promote = ($analyzeExit -eq 0) -and $openPass -and (
            ($null -eq $pipelinePass) -or $pipelinePass
        )

        $results += [ordered]@{
            step = $step
            lock = $def.lock
            note = $def.note
            analyzeExit = $analyzeExit
            openGatePass = $openPass
            pipelineVerifyPass = $pipelinePass
            promote = $promote
            logNeedles = $logNeedles
            artifactDir = $stepDir
        }
    }
} catch {
    $scriptError = $_.Exception.Message
    Write-Host "Bisect aborted: $scriptError" -ForegroundColor Red
} finally {
    if (-not $DryRun) {
        Restore-AllSources -snapDir $outDir
        Write-Host "Restored policy + PreviewEngineScreen + RawCaptureSupport from baseline." -ForegroundColor Green
    }
}

$deviceLabel = if ($Serial) { $Serial } else { 'env/default' }
$report = @"
# M13.3e lock bisect ($ts UTC)

Device: $deviceLabel
Runbook: docs/M13_3E_LOCK_BISECT_RUNBOOK.md
Order: L2 → L3 → L6 → L4 → L5 → L7 (E1–E6)

| Step | Lock | Capture analyze | Open gate | Pipeline verify | Promote? | Notes |
|------|------|-----------------|-----------|-----------------|----------|-------|
"@
foreach ($r in $results) {
    $og = if ($r.openGatePass) { 'PASS' } else { 'FAIL' }
    $pv = if ($null -eq $r.pipelineVerifyPass) { 'skip' } elseif ($r.pipelineVerifyPass) { 'PASS' } else { 'FAIL' }
    $pr = if ($r.promote) { '**yes**' } else { 'no' }
    $report += "| $($r.step) | $($r.lock) | exit $($r.analyzeExit) | $og | $pv | $pr | $($r.note) |`n"
}
$shipped = ($results | Where-Object { $_.promote } | ForEach-Object { $_.lock }) -join ', '
if (-not $shipped) { $shipped = '(none — keep 13.3g defaults)' }
$report += @"

**Ship recommendation:** $shipped

Human ACR + daylight color vs ReferenceCam still required before changing [LegacyFleetPolicy] defaults.

"@

$reportPath = Join-Path $outDir "report.md"
$report | Set-Content $reportPath -Encoding UTF8
$results | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir "results.json") -Encoding UTF8
Write-Host "Wrote $reportPath" -ForegroundColor Green

$adbStop = @('shell', 'am', 'force-stop', 'dev.pointandshoot')
if ($Serial) { $adbStop = @('-s', $Serial) + $adbStop }
& adb @adbStop 2>$null | Out-Null

if ($results | Where-Object { $_.promote }) {
    Write-Host "At least one step passed automated gates — review color/ACR before merging policy." -ForegroundColor Yellow
}
if ($scriptError) { exit 1 }
exit 0
