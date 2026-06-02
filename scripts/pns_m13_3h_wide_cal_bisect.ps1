<#
.SYNOPSIS
  Milestone 13.3h — USB bisect H1–H3 (wide-cal / ASN / exposure latch on legacy device aux DNG).

.DESCRIPTION
  Patches LegacyFleetPolicy.kt per step, assembleDebug, pns_aux_dng_capture_analyze.ps1,
  records openability + logcat needles, restores policy. Runbook: docs/M13_3H_WIDE_CAL_BISECT.md

.PARAMETER Steps
  Comma-separated subset: H1,H2,H3 (default all).

.PARAMETER Serial
  ADB serial (else scripts/pns_adb_device.env).

.PARAMETER SkipGradle
  Skip assembleDebug (use installed APK — only if policy already patched).

.EXAMPLE
  .\scripts\pns_m13_3h_wide_cal_bisect.ps1
#>
param(
    [string]$Steps = "H1,H2,H3",
    [string]$Serial = "",
    [switch]$SkipGradle,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

$projRoot = Split-Path -Parent $PSScriptRoot
$policyPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\fleet\LegacyFleetPolicy.kt"
if (-not (Test-Path -LiteralPath $policyPath)) { throw "Missing $policyPath" }

$stepList = $Steps -split "," | ForEach-Object { $_.Trim().ToUpperInvariant() } | Where-Object { $_ }
$stepDefs = @{
    H1 = @{
        wideCal = $true
        legacyAsn = $false
        note = "useWideLeafCalibrationForAuxDng=true (wide CM/FM on aux RAW)"
    }
    H2 = @{
        wideCal = $true
        legacyAsn = $true
        note = "H1 + useLegacyAsnReconcileOnly=true (wide-cal reconcile path still primary)"
    }
    H3 = @{
        wideCal = $true
        legacyAsn = $true
        note = "H2 + exposure latch (proShotLatchManualExposureOnStill when wideCal; tele 2.5x exp scale)"
    }
}

function Set-PolicyFlags {
    param($wideCal, $legacyAsn)
    $wcKt = if ($wideCal) { "true" } else { "false" }
    $asnKt = if ($legacyAsn) { "true" } else { "false" }
    $text = Get-Content -LiteralPath $policyPath -Raw -Encoding UTF8
    $text = $text -replace 'fun useWideLeafCalibrationForAuxDng\(\): Boolean = \w+', "fun useWideLeafCalibrationForAuxDng(): Boolean = $wcKt"
    $text = $text -replace 'fun useLegacyAsnReconcileOnly\(\): Boolean = \w+', "fun useLegacyAsnReconcileOnly(): Boolean = $asnKt"
    Set-Content -LiteralPath $policyPath -Value $text -Encoding UTF8 -NoNewline
}

function Restore-Policy {
    Set-PolicyFlags -wideCal $false -legacyAsn $false
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\m13_3h_wide_cal_bisect_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$snapshot = Join-Path $outDir "LegacyFleetPolicy.kt.baseline"
Copy-Item -LiteralPath $policyPath -Destination $snapshot -Force

$results = @()
try {
    foreach ($step in $stepList) {
        if (-not $stepDefs.ContainsKey($step)) {
            Write-Warning "Unknown step $step — skip"
            continue
        }
        $def = $stepDefs[$step]
        Write-Host "=== 13.3h $step — $($def.note) ===" -ForegroundColor Cyan
        if ($DryRun) { continue }

        Restore-Policy
        Set-PolicyFlags -wideCal $def.wideCal -legacyAsn $def.legacyAsn

        if (-not $SkipGradle) {
            & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
            if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed for $step" }
        }

        $analyzeArgs = @{
            PreviewDial = "A"
            NoFast = $true
            OutDir = (Join-Path $outDir "step_$step")
        }
        if ($Serial) { $analyzeArgs["Serial"] = $Serial }

        $analyzeScript = Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1"
        & $analyzeScript @analyzeArgs
        $analyzeExit = $LASTEXITCODE

        $stepDir = Join-Path $outDir "step_$step"
        $openJson = Join-Path $stepDir "openability_gate.json"
        $openPass = $false
        if (Test-Path -LiteralPath $openJson) {
            $oj = Get-Content $openJson -Raw | ConvertFrom-Json
            $openPass = [bool]$oj.pass
        }

        $logNeedles = @{
            wideCalReconcile = $false
            legacyAsn = $false
            exposureLatch = $false
        }
        $logcatFiles = @(Get-ChildItem -Path $stepDir -Filter "*_logcat.txt" -ErrorAction SilentlyContinue)
        if ($logcatFiles.Count -gt 0) {
            $lc = ($logcatFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
            $logNeedles.wideCalReconcile = $lc -match "wide-cal reconcile"
            $logNeedles.legacyAsn = $lc -match "Op13 ASN|asn-only|AsShotNeutral"
            $logNeedles.exposureLatch = $lc -match "ProShotExposureLatch|exposure.?latch|adjustProShotExposure"
        }

        $row = [ordered]@{
            step = $step
            note = $def.note
            analyzeExit = $analyzeExit
            openGatePass = $openPass
            wideCal = $def.wideCal
            legacyAsn = $def.legacyAsn
            logNeedles = $logNeedles
            artifactDir = $stepDir
        }
        $results += $row
        $row | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outDir "step_${step}_result.json") -Encoding UTF8
    }
}
finally {
    if (-not $DryRun) {
        Copy-Item -LiteralPath $snapshot -Destination $policyPath -Force
        Write-Host "Restored LegacyFleetPolicy.kt from baseline." -ForegroundColor Green
    }
}

$report = @"
# M13.3h wide-cal bisect ($ts UTC)

Device: $(if ($Serial) { $Serial } else { 'env/default' })
Runbook: docs/M13_3H_WIDE_CAL_BISECT.md

| Step | Open gate | Analyze exit | wide-cal log | Notes |
|------|-----------|--------------|--------------|-------|
"@
foreach ($r in $results) {
    $og = if ($r.openGatePass) { "PASS" } else { "FAIL" }
    $wc = if ($r.logNeedles.wideCalReconcile) { "yes" } else { "no" }
    $report += "| $($r.step) | $og | $($r.analyzeExit) | $wc | $($r.note) |`n"
}
$report += @"

**Shipped default after bisect:** all flags restored to 13.3g (wideCal=false).

Human ACR 3/3 required per step before promoting any lock — see ACR_HUMAN_VERIFY.md in each step_* folder.

"@

$reportPath = Join-Path $outDir "report.md"
$report | Set-Content $reportPath -Encoding UTF8
$results | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir "results.json") -Encoding UTF8
Write-Host "Wrote $reportPath" -ForegroundColor Green

# Battery rule
$adbStop = @("shell", "am", "force-stop", "dev.pointandshoot")
if ($Serial) { $adbStop = @("-s", $Serial) + $adbStop }
& adb @adbStop 2>$null | Out-Null

if ($results | Where-Object { -not $_.openGatePass -or $_.analyzeExit -ne 0 }) {
    exit 1
}
exit 0
