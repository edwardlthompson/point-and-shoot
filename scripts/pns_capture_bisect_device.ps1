<#
.SYNOPSIS
  Apply cumulative capture bisect steps (see **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**), **assembleDebug**,
  and run **`pns_capture_pipeline_verify.ps1`** on USB for each step.

.DESCRIPTION
  1. Snapshot **`PreviewEngineScreen.kt`** + **`RawCaptureSupport.kt`** (restore at end unless **`-NoRestore`**).
  2. For **step = 1 .. UpToStep**, restore sources from snapshot, apply transforms **1..step** (best-effort
     no-ops when already applied), write files, **`pns_gradlew.ps1 :app:assembleDebug`**, then
     **`pns_capture_pipeline_verify.ps1 -BisectStep <n> -NoHistoryAppend`** (optional **`-Fast`** / tight **`-MaxAttempts`**).
  3. Writes **`hfr-runs/capture_bisect_device_<UTC>/report.md`**, **`results.json`**, per-step **`step_N/`** copies of gate output.

  **Transforms:** **1** = omit **`PreviewStabilization`** on RAW + bracket stills; **2** = default RAW tier
  **RAW12 → RAW_SENSOR → RAW10**; **3** = imaging profile **`remember`** without **`runCatching`** / singleton-touch (keep **`SideEffect`** → **`setImagingProfileForStreams`**);
  **4** = REGULAR session **`streamHints=false`** only;
  **5** = omit **`PreviewPostRawSensitivity`** on those still builders; **6** = no-op (HDR preview pref off by default).

.PARAMETER UpToStep
  Last bisect doc step to run (**1**..**6**, default **5**). Step **6** runs verify only (no extra patch beyond step **5**).

.PARAMETER FromStep
  First step to run (default **1**). Cumulative transforms still start at **1** for each run (e.g. **FromStep 3**
  skips device runs for steps 1-2 but still applies **T1+T2+T3** when testing step 3).

.PARAMETER Serial
  Forwarded to **`pns_capture_pipeline_verify.ps1`**.

.PARAMETER Fast
  Forwarded to **`pns_capture_pipeline_verify.ps1`**.

.PARAMETER WaitSec
  Forwarded (default **58**).

.PARAMETER MaxAttempts
  Forwarded to **`pns_capture_pipeline_verify.ps1`** (default **2** for shorter bisect loops).

.PARAMETER SkipGradle
  Skip **assembleDebug** (still runs verify; use pre-built APK).

.PARAMETER DryRun
  Print transforms only; do not write sources or run adb.

.PARAMETER NoRestore
  Leave **`PreviewEngineScreen.kt`** / **`RawCaptureSupport.kt`** in the last patched state (not recommended).

.PARAMETER WriteDocHistory
  If set, omit **`-NoHistoryAppend`** on **`pns_capture_pipeline_verify`** so **`docs/CAPTURE_PIPELINE_VERIFY_*`**
  updates (default: bisect run does **not** flood docs).

.EXAMPLE
  . .\scripts\pns_resolve_adb.ps1 -PrependToPath -Quiet
  .\scripts\pns_capture_bisect_device.ps1 -UpToStep 3 -Fast -MaxAttempts 2
#>
param(
    [int]$UpToStep = 5,
    [int]$FromStep = 1,
    [string]$Serial = "",
    [switch]$Fast,
    [int]$WaitSec = 58,
    [int]$MaxAttempts = 2,
    [switch]$SkipGradle,
    [switch]$DryRun,
    [switch]$NoRestore,
    [switch]$WriteDocHistory
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$previewPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\PreviewEngineScreen.kt"
$rawPath = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\RawCaptureSupport.kt"
foreach ($p in @($previewPath, $rawPath)) {
    if (-not (Test-Path -LiteralPath $p)) { throw "Missing $p" }
}

function Normalize-Lf([string]$s) {
    if ($null -eq $s) { return "" }
    return ($s -replace "`r`n", "`n")
}

function Denormalize-PlatformNewlines([string]$s) {
    if ($null -eq $s) { return "" }
    return $s -replace "`n", [Environment]::NewLine
}

function Write-Utf8NoBom([string]$path, [string]$content) {
    [System.IO.File]::WriteAllText($path, $content, [System.Text.UTF8Encoding]::new($false))
}

function Apply-T1_RemoveStillStabilization([string]$preview) {
    $p = $preview
    $capFrom = @"
                PreviewAeAntibanding.applyToRequest(this, chars)
                PreviewStabilization.applyToRequest(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    previewFpsRange = null,
                    manualSensor = manualSensorStill,
                    isStillCapture = true,
                )
                PreviewPostRawSensitivity.applyIfCompatible(
"@
    $capTo = @"
                PreviewAeAntibanding.applyToRequest(this, chars)
                // Bisect #1: still TEMPLATE_STILL_CAPTURE omits PreviewStabilization (restore: docs/REVERTED_FEATURES_RESTORE_LIST.md §1).
                PreviewPostRawSensitivity.applyIfCompatible(
"@
    $brkFrom = @"
                    PreviewAeAntibanding.applyToRequest(this, chars)
                    PreviewStabilization.applyToRequest(
                        this,
                        chars,
                        readHudCapturePrefs(),
                        previewFpsRange = null,
                        manualSensor = manualSensorBracket,
                        isStillCapture = true,
                    )
                    PreviewPostRawSensitivity.applyIfCompatible(
"@
    $brkTo = @"
                    PreviewAeAntibanding.applyToRequest(this, chars)
                    // Bisect #1: bracket still omits PreviewStabilization (restore: docs/REVERTED_FEATURES_RESTORE_LIST.md §1).
                    PreviewPostRawSensitivity.applyIfCompatible(
"@
    $cf = ($capFrom -replace "`r`n", "`n")
    $ct = ($capTo -replace "`r`n", "`n")
    $bf = ($brkFrom -replace "`r`n", "`n")
    $bt = ($brkTo -replace "`r`n", "`n")
    if ($p.Contains($cf)) { $p = $p.Replace($cf, $ct) }
    if ($p.Contains($bf)) { $p = $p.Replace($bf, $bt) }
    return $p
}

function Apply-T2_RawSensorBeforeRaw10([string]$raw) {
    $r = $raw
    $from = @"
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
            RawStreamPreference.RawSensorFirst ->
"@
    $to = @"
            RawStreamPreference.Default ->
                largest(raw12)?.let { ImageFormat.RAW12 to it }
                    ?: largest(rawSensor)?.let { ImageFormat.RAW_SENSOR to it }
                    ?: largest(raw10)?.let { ImageFormat.RAW10 to it }
            RawStreamPreference.RawSensorFirst ->
"@
    $ff = ($from -replace "`r`n", "`n")
    $tt = ($to -replace "`r`n", "`n")
    if ($r.Contains($ff)) { $r = $r.Replace($ff, $tt) }
    return $r
}

function Apply-T3_ImagingProfileSimpleRemember([string]$preview) {
    $p = $preview
    # Single-quoted here-string so Kotlin `` ` `` in comments are not interpreted by PowerShell.
    $fromLegacy = @'
    var imagingProfile by remember(adbInitialImagingProfile) {
        // JVM: sealed `data object` singleton fields can be observed null during early companion init;
        // touch both before prefs / intent paths return an [ImagingProfile] (see [EncoderRoute.downgradedProfiles]).
        listOf(ImagingProfile.StandardPro, ImagingProfile.UltraMax)
        mutableStateOf(
            runCatching {
                val r = adbInitialImagingProfile ?: HudSettings.loadImagingProfile(context)
                // Touch [.id] so a null / half-built singleton fails here instead of in SideEffect → controller.
                r.id
                r
            }.getOrElse { ImagingProfile.StandardPro },
        )
    }
'@
    $to = @"
    var imagingProfile by remember(adbInitialImagingProfile) {
        // Bisect #3: drop Milestone 9 runCatching / singleton-touch hardening only; keep [SideEffect] sync below.
        mutableStateOf(adbInitialImagingProfile ?: HudSettings.loadImagingProfile(context))
    }
"@
    $fnL = ($fromLegacy -replace "`r`n", "`n")
    $tn = ($to -replace "`r`n", "`n")
    if ($p.Contains($fnL)) { $p = $p.Replace($fnL, $tn) }
    return $p
}

function Apply-T4_StreamHintsOff([string]$preview) {
    $p = $preview
    $from = @"
            val streamHints = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            captureSessionAsyncConfigurePending = true
"@
    $to = @"
            // Bisect #4a: omit API 33+ OutputConfiguration.setStreamUseCase tags on REGULAR session
            // (restore: docs/REVERTED_FEATURES_RESTORE_LIST.md §4).
            val streamHints = false
            captureSessionAsyncConfigurePending = true
"@
    $fn = ($from -replace "`r`n", "`n")
    $tn = ($to -replace "`r`n", "`n")
    if ($p.Contains($fn)) { $p = $p.Replace($fn, $tn) }
    return $p
}

function Apply-T5_RemovePostRawOnStillBuilders([string]$preview) {
    $p = $preview
    $cap = @"
                PreviewPostRawSensitivity.applyIfCompatible(
                    this,
                    chars,
                    readHudCapturePrefs(),
                    manualIsoOverride,
                    manualExposureNsOverride,
                )
"@
    $brk = @"
                    PreviewPostRawSensitivity.applyIfCompatible(
                        this,
                        chars,
                        readHudCapturePrefs(),
                        manualIsoOverride,
                        manualExposureNsOverride,
                    )
"@
    $cn = ($cap -replace "`r`n", "`n")
    $bn = ($brk -replace "`r`n", "`n")
    if ($p.Contains($cn)) { $p = $p.Replace($cn, "`n") }
    if ($p.Contains($bn)) { $p = $p.Replace($bn, "`n") }
    return $p
}

function Invoke-Transforms([string]$previewLf, [string]$rawLf, [int]$upToStep) {
    $p = $previewLf
    $r = $rawLf
    $applied = [System.Collections.Generic.List[string]]::new()
    if ($upToStep -ge 1) {
        $p0 = $p
        $p = Apply-T1_RemoveStillStabilization $p
        if ($p -ne $p0) { [void]$applied.Add("T1_still_stabilization_removed") }
    }
    if ($upToStep -ge 2) {
        $r0 = $r
        $r = Apply-T2_RawSensorBeforeRaw10 $r
        if ($r -ne $r0) { [void]$applied.Add("T2_raw_sensor_before_raw10") }
    }
    if ($upToStep -ge 3) {
        $p0 = $p
        $p = Apply-T3_ImagingProfileSimpleRemember $p
        if ($p -ne $p0) { [void]$applied.Add("T3_imaging_profile_simple_remember") }
    }
    if ($upToStep -ge 4) {
        $p0 = $p
        $p = Apply-T4_StreamHintsOff $p
        if ($p -ne $p0) { [void]$applied.Add("T4_stream_hints_off") }
    }
    if ($upToStep -ge 5) {
        $p0 = $p
        $p = Apply-T5_RemovePostRawOnStillBuilders $p
        if ($p -ne $p0) { [void]$applied.Add("T5_post_raw_sensitivity_removed") }
    }
    return [pscustomobject]@{ PreviewLf = $p; RawLf = $r; Applied = $applied }
}

$snapPreview = [System.IO.File]::ReadAllText($previewPath, [System.Text.UTF8Encoding]::new($false))
$snapRaw = [System.IO.File]::ReadAllText($rawPath, [System.Text.UTF8Encoding]::new($false))
$snapPreviewLf = Normalize-Lf $snapPreview
$snapRawLf = Normalize-Lf $snapRaw

$stamp = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outRoot = Join-Path $projRoot "hfr-runs\capture_bisect_device_$stamp"
New-Item -ItemType Directory -Force -Path $outRoot | Out-Null

$gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
$gate = Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1"
if (-not (Test-Path -LiteralPath $gate)) { throw "Missing $gate" }

$rows = [System.Collections.Generic.List[hashtable]]::new()
$lines = [System.Collections.Generic.List[string]]::new()
[void]$lines.Add("# Capture bisect device run")
[void]$lines.Add("")
[void]$lines.Add("| Step | Applied transforms | assemble | verify exit |")
[void]$lines.Add("|------|--------------------|----------|-------------|")

$exitAll = 0

for ($step = [Math]::Max(1, $FromStep); $step -le [Math]::Max(1, [Math]::Min(6, $UpToStep)); $step++) {
    $note = ""
    if ($step -eq 6) { $note = " (no extra patch beyond step 5; HDR preview off by default)" }

    $tr = Invoke-Transforms $snapPreviewLf $snapRawLf $step
    $appliedStr = if ($tr.Applied.Count -gt 0) { ($tr.Applied -join ", ") } else { "(no-op / already patched)" }

    if ($DryRun) {
        Write-Host "[capture_bisect_device] DRY step=$step applied=$appliedStr$note"
        [void]$lines.Add("| $step | $appliedStr | SKIP | SKIP |")
        [void]$rows.Add(@{ step = $step; applied = $appliedStr; assemble = "dry"; verifyExit = $null; note = $note.Trim() })
        continue
    }

    $stepDir = Join-Path $outRoot ("step_{0:D2}" -f $step)
    New-Item -ItemType Directory -Force -Path $stepDir | Out-Null
    Write-Utf8NoBom $previewPath (Denormalize-PlatformNewlines $tr.PreviewLf)
    Write-Utf8NoBom $rawPath (Denormalize-PlatformNewlines $tr.RawLf)

    $assembleOk = "skipped"
    if (-not $SkipGradle) {
        Write-Host "[capture_bisect_device] step=$step assembleDebug..."
        & $gw ":app:assembleDebug"
        if ($LASTEXITCODE -ne 0) {
            $assembleOk = "FAIL"
            [void]$lines.Add("| $step | $appliedStr | FAIL | - |")
            $rows.Add(@{ step = $step; applied = $appliedStr; assemble = "fail"; verifyExit = $null })
            $exitAll = 1
            break
        }
        $assembleOk = "ok"
    }

    $gateArgs = [System.Collections.Generic.List[string]]::new()
    $gateArgs.Add("-NoProfile")
    $gateArgs.Add("-ExecutionPolicy")
    $gateArgs.Add("Bypass")
    $gateArgs.Add("-File")
    $gateArgs.Add($gate)
    if (-not [string]::IsNullOrWhiteSpace($Serial)) {
        $gateArgs.Add("-Serial")
        $gateArgs.Add($Serial)
    }
    $gateArgs.Add("-MaxAttempts")
    $gateArgs.Add("$MaxAttempts")
    $gateArgs.Add("-WaitSec")
    $gateArgs.Add("$WaitSec")
    if ($Fast) { $gateArgs.Add("-Fast") }
    $gateArgs.Add("-SkipAssemble")
    $gateArgs.Add("-BisectStep")
    $gateArgs.Add("cumulative-$step")
    if (-not $WriteDocHistory) {
        $gateArgs.Add("-NoHistoryAppend")
    }

    Write-Host "[capture_bisect_device] step=$step pns_capture_pipeline_verify..."
    $gp = Start-Process -FilePath "powershell.exe" -ArgumentList $gateArgs -Wait -PassThru -NoNewWindow
    $v = 1
    try { if ($null -ne $gp.ExitCode) { $v = [int]$gp.ExitCode } } catch { $v = 1 }
    if ($v -ne 0) { $exitAll = 1 }

    [void]$lines.Add("| $step | $appliedStr | $assembleOk | $v |")

    $rows.Add(@{
        step       = $step
        applied    = $appliedStr
        assemble   = $assembleOk
        verifyExit = $v
        note       = $note.Trim()
    })

    # Copy latest gate json into step dir if any
    $gates = Get-ChildItem -LiteralPath (Join-Path $projRoot "hfr-runs") -Directory -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "capture_pipeline_gate_*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if ($null -ne $gates) {
        $gj = Join-Path $gates.FullName "gate.json"
        if (Test-Path -LiteralPath $gj) {
            Copy-Item -LiteralPath $gj -Destination (Join-Path $stepDir "gate.json") -Force
        }
    }
}

if (-not $DryRun -and -not $NoRestore) {
    Write-Host "[capture_bisect_device] restoring snapshot sources..."
    Write-Utf8NoBom $previewPath $snapPreview
    Write-Utf8NoBom $rawPath $snapRaw
}
elseif (-not $DryRun) {
    Write-Warning "[capture_bisect_device] -NoRestore: sources left in last patched state."
}

$results = [ordered]@{
    schema   = "pns.capture_bisect_device.v1"
    utc      = [DateTime]::UtcNow.ToString("o")
    upToStep = $UpToStep
    fromStep = $FromStep
    steps    = @($rows)
}
$resultsPath = Join-Path $outRoot "results.json"
($results | ConvertTo-Json -Depth 8) | Set-Content -LiteralPath $resultsPath -Encoding utf8

$reportPath = Join-Path $outRoot "report.md"
$lines.Add("")
$lines.Add("Artifacts: ``$outRoot``")
$lines.Add("")
$lines.Add("Restore checklist: ``docs/REVERTED_FEATURES_RESTORE_LIST.md``")
$lines | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "[capture_bisect_device] wrote $reportPath exitAll=$exitAll"
exit $exitAll
