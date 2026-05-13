<#
.SYNOPSIS
  Automated bisect: which single “post-commit” change breaks scripted RAW still capture?

.DESCRIPTION
  1) Snapshot Kotlin sources (in memory).
  2) Run **scripts/pns_photo_capture_verify.ps1** on the **current** tree (baseline).
  3) For each suspect variant, restore snapshot, apply **one** mechanical change, **assembleDebug**,
     run photo verify (**-MaxAttempts 2 -Fast** by default), record pass/fail, restore snapshot.
  4) Write **hfr-runs/raw_regression_bisect_*/results.json** + **report.md** with conclusions.

  Suspects (from RAW regression investigation):
  - **wrong_default_raw_tier_order** — [RawStreamPreference.Default] uses RAW12 → RAW10 → RAW_SENSOR
    (Milestone 10.1 ordering) instead of current bisect default RAW12 → RAW_SENSOR → RAW10.
  - **desired_fps_default_120** — [DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC] = 120 so [canCaptureRawStill]
    can stay false until UI sync.
  - **want_yuv_omit_under_scripted_raw** — H-dial YUV omits when [automationSuppressFacePipeline] (ADB raw/bracket).

.PARAMETER Serial
  Forwarded to **pns_photo_capture_verify.ps1** (**adb -s**). Omit for **pns_adb_device.env**.

.PARAMETER MaxAttempts
  Per-variant photo verify attempts (default **2**).

.PARAMETER WaitSec
  Seconds to wait after **am start** (default **58**).

.PARAMETER SkipBaseline
  Skip step 2 (use when baseline already verified manually).

.PARAMETER Variants
  Comma list: **all** (default), **wrong_default_raw_tier_order**, **desired_fps_default_120**,
  **want_yuv_omit_under_scripted_raw**, or **baseline**.

.EXAMPLE
  . .\scripts\pns_resolve_adb.ps1 -PrependToPath -Quiet
  .\scripts\pns_raw_regression_bisect.ps1
#>
param(
    [string]$Serial = "",
    [int]$MaxAttempts = 2,
    [int]$WaitSec = 58,
    [switch]$SkipBaseline,
    [string]$Variants = "all"
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$rawPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\RawCaptureSupport.kt"
$previewPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\PreviewEngineScreen.kt"

foreach ($p in @($rawPath, $previewPath)) {
    if (-not (Test-Path -LiteralPath $p)) {
        throw "Missing source file: $p"
    }
}

$snapshot = @{}
$snapshot[$rawPath] = [System.IO.File]::ReadAllText($rawPath, [System.Text.UTF8Encoding]::new($false))
$snapshot[$previewPath] = [System.IO.File]::ReadAllText($previewPath, [System.Text.UTF8Encoding]::new($false))

function Restore-AllSnapshot {
    foreach ($kv in $snapshot.GetEnumerator()) {
        [System.IO.File]::WriteAllText($kv.Key, $kv.Value, [System.Text.UTF8Encoding]::new($false))
    }
}

function Write-VariantPatch([string]$variant) {
    Restore-AllSnapshot
    $raw = $snapshot[$rawPath]
    $pr = $snapshot[$previewPath]
    switch ($variant) {
        "wrong_default_raw_tier_order" {
            $blockFrom = @"
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
            RawStreamPreference.RawSensorFirst ->
"@
            $blockTo = @"
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
            RawStreamPreference.RawSensorFirst ->
"@
            $bf = $blockFrom -replace "`r`n", "`n"
            $bt = $blockTo -replace "`r`n", "`n"
            $rawN = $raw -replace "`r`n", "`n"
            if (-not $rawN.Contains($bf)) {
                throw "Patch mismatch: wrong_default_raw_tier_order (Default+RawSensorFirst anchor not found)"
            }
            $rawN = $rawN.Replace($bf, $bt)
            $rawOut = $rawN -replace "`n", [Environment]::NewLine
            [System.IO.File]::WriteAllText($rawPath, $rawOut, [System.Text.UTF8Encoding]::new($false))
        }
        "desired_fps_default_120" {
            $old = "private const val DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC = 60"
            $new = "private const val DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC = 120"
            if (-not $pr.Contains($old)) {
                throw "Patch mismatch: desired_fps_default_120 (const line not found)"
            }
            $pr = $pr.Replace($old, $new)
            [System.IO.File]::WriteAllText($previewPath, $pr, [System.Text.UTF8Encoding]::new($false))
        }
        "want_yuv_omit_under_scripted_raw" {
            $from = "(commandDialMode == CommandDialMode.H && desiredFps < 120) ||"
            $to = "(commandDialMode == CommandDialMode.H && desiredFps < 120 && !automationSuppressFacePipeline) ||"
            if (-not $pr.Contains($from)) {
                throw "Patch mismatch: want_yuv_omit_under_scripted_raw (H+dial line not found)"
            }
            if ($pr.Contains($to)) {
                throw "Patch mismatch: want_yuv already gated (revert tree before bisect?)"
            }
            $pr = $pr.Replace($from, $to)
            [System.IO.File]::WriteAllText($previewPath, $pr, [System.Text.UTF8Encoding]::new($false))
        }
        default { throw "Unknown variant: $variant" }
    }
}

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\raw_regression_bisect_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$allowed = @("all", "baseline", "wrong_default_raw_tier_order", "desired_fps_default_120", "want_yuv_omit_under_scripted_raw")
$want = @()
if ($Variants -eq "all") {
    $want = @("baseline", "wrong_default_raw_tier_order", "desired_fps_default_120", "want_yuv_omit_under_scripted_raw")
}
else {
    $want = $Variants.Split(",", [System.StringSplitOptions]::RemoveEmptyEntries) | ForEach-Object { $_.Trim() }
    foreach ($w in $want) {
        if ($allowed -notcontains $w) {
            throw "Unknown variant '$w'. Allowed: $($allowed -join ', ')"
        }
    }
}

$results = New-Object System.Collections.Generic.List[object]

function Run-PhotoVerify {
    $verify = Join-Path $PSScriptRoot "pns_photo_capture_verify.ps1"
    # Child script ends with Write-Error + exit 1; run out-of-process so the bisect script always completes.
    if ($Serial) {
        $argList = @(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $verify,
            "-Serial", $Serial,
            "-MaxAttempts", "$MaxAttempts",
            "-WaitSec", "$WaitSec",
            "-Fast"
        )
    }
    else {
        $argList = @(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $verify,
            "-MaxAttempts", "$MaxAttempts",
            "-WaitSec", "$WaitSec",
            "-Fast"
        )
    }
    $p = Start-Process -FilePath "powershell.exe" -ArgumentList $argList -Wait -PassThru -NoNewWindow
    $ec = $p.ExitCode
    if ($null -eq $ec) { return 1 }
    return [int]$ec
}

try {
    foreach ($v in $want) {
        if ($v -eq "baseline") {
            if ($SkipBaseline) {
                Write-Host "[raw_regression_bisect] skip baseline (--SkipBaseline)"
                continue
            }
            Restore-AllSnapshot
            Write-Host "`n[raw_regression_bisect] === VARIANT: baseline (clean snapshot) ==="
            $code = Run-PhotoVerify
            $results.Add([pscustomobject]@{
                variant = "baseline"
                pass    = ($code -eq 0)
                exitCode = $code
                note    = $null
            })
            continue
        }

        Write-Host "`n[raw_regression_bisect] === VARIANT: $v ==="
        Write-VariantPatch $v
        $gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
        Write-Host "[raw_regression_bisect] assembleDebug..."
        & $gw ":app:assembleDebug"
        if ($LASTEXITCODE -ne 0) {
            $results.Add([pscustomobject]@{ variant = $v; pass = $false; exitCode = -1; note = "assembleDebug failed" })
            Restore-AllSnapshot
            continue
        }
        $code = Run-PhotoVerify
        $results.Add([pscustomobject]@{
            variant = $v
            pass    = ($code -eq 0)
            exitCode = $code
            note    = $null
        })
        Restore-AllSnapshot
    }
}
finally {
    Restore-AllSnapshot
}

$jsonPath = Join-Path $outDir "results.json"
@($results) | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding utf8

$baselineRow = $results | Where-Object { $_.variant -eq "baseline" } | Select-Object -First 1
$baselinePass = $null -ne $baselineRow -and $baselineRow.pass
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add("# RAW regression bisect")
$lines.Add("")
$lines.Add("Artifacts: ``$outDir``")
$lines.Add("")
$lines.Add("| Variant | Pass | Exit |")
$lines.Add("|---------|------|------|")
foreach ($r in $results) {
    $p = if ($r.pass) { "yes" } else { "no" }
    $lines.Add("| ``$($r.variant)`` | $p | $($r.exitCode) |")
}
$lines.Add("")
$lines.Add("## Interpretation")
if (-not $SkipBaseline) {
    if ($baselinePass) {
        $lines.Add("- **Baseline (reverted tree):** scripted RAW still **verified** on device.")
    }
    else {
        $lines.Add("- **Baseline failed:** device/network/build issue - fix **not** proven on this run. Check ``photo_capture_verify_*`` under ``hfr-runs``.")
        $lines.Add("- **Bisect note:** Per-variant runs may still execute, but **pass/fail vs baseline is inconclusive** until `pns_photo_capture_verify.ps1` succeeds on this USB device (or use a reference phone).")
    }
}
$broken = @($results | Where-Object { $_.variant -ne "baseline" -and -not $_.pass -and $_.note -ne "assembleDebug failed" })
$ok = @($results | Where-Object { $_.variant -ne "baseline" -and $_.pass })
if ($broken.Count -gt 0) {
    $names = ($broken | ForEach-Object { $_.variant }) -join ", "
    $lines.Add("- **Regressions reproduced:** $names - each **breaks** scripted RAW vs baseline on this run.")
}
if ($ok.Count -gt 0) {
    $names = ($ok | ForEach-Object { $_.variant }) -join ", "
    $lines.Add("- **Variants that still passed:** $names - not the regression on this device (or baseline flaky).")
}
if (-not $SkipBaseline -and $baselinePass -and $broken.Count -gt 0) {
    $lines.Add("")
    $lines.Add("## Mystery (isolated)")
    $lines.Add("Each failing variant is a single change **away** from the known-good baseline. Re-apply only with mitigations listed under **Safe shipping guidance**.")
}
$lines.Add("")
$lines.Add("## Safe shipping guidance")
$lines.Add("- **wrong_default_raw_tier_order:** Keep **Default** = RAW12 -> RAW10 -> RAW_SENSOR. Use **RawStreamPreference.RawSensorFirst** only for ADB/matrix on specific HALs.")
$lines.Add("- **desired_fps_default_120:** Keep **DESIRED_FPS_DEFAULT_BEFORE_UI_SYNC = 60** (or equivalent) so **canCaptureRawStill** is not blocked before first **setDesired**.")
$lines.Add("- **want_yuv_omit_under_scripted_raw:** If M6 needs fewer surfaces, prefer an explicit flag (e.g. intent extra) instead of overloading **automationSuppressFacePipeline**, so in-app H + YUV stays unchanged.")

$report = Join-Path $outDir "report.md"
$lines -join "`n" | Set-Content -LiteralPath $report -Encoding utf8

Write-Host "`n[raw_regression_bisect] Wrote $jsonPath"
Write-Host "[raw_regression_bisect] Wrote $report"
$exitCode = 0
if (-not $SkipBaseline -and -not $baselinePass) {
    Write-Warning "[raw_regression_bisect] baseline failed - exit 1"
    $exitCode = 1
}
exit $exitCode
