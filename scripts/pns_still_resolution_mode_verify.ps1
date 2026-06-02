<#
.SYNOPSIS
  USB gate: verify binned vs max-resolution still mode affects effective stream size.

.DESCRIPTION
  Runs two composed-still smoke captures with:
    - pns_preview_still_resolution_mode=binned
    - pns_preview_still_resolution_mode=max_resolution

  Collects proof:
    - logcat (PNS.AdbValidation / PNS.CaptureStill / PNS.ChromeUx)
    - parsed `RAW ImageReader ...`, `JPEG ImageReader ...`, `dng save diag ... rawWxH=...`
    - composed-still save lines (`captureRawStill ... saved=`, `captureIndependentTonalStill ... saved=`)
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 75,
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [switch]$ExperimentalVendorSession
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
$outDir = Join-Path $repo "hfr-runs\still_resolution_mode_verify_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Clear-SafeModeState {
    & adb @adbPrefix shell run-as $pkg sh -c "rm -f files/noop shared_prefs/pns_experimental_safe_mode.xml" 2>$null | Out-Null
}

function Run-Scenario([string]$ScenarioId, [string]$ModeId, [string[]]$ExtraStartArgs, [bool]$ResetSafeModeBeforeStart = $true) {
    $scenarioDir = Join-Path $outDir $ScenarioId
    New-Item -ItemType Directory -Force -Path $scenarioDir | Out-Null
    $logPath = Join-Path $scenarioDir "logcat.txt"

    & adb @adbPrefix shell logcat -c 2>$null | Out-Null
    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
    if ($ResetSafeModeBeforeStart) {
        Clear-SafeModeState
    }
    Start-Sleep -Milliseconds 700

    $startArgs = @(
        "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_imaging_profile", "standard_pro",
        "--ez", "pns_preview_composed_still", "true",
        "--es", "pns_preview_still_resolution_mode", $ModeId
    )
    if ($ExtraStartArgs -and $ExtraStartArgs.Count -gt 0) {
        $startArgs += $ExtraStartArgs
    }
    & adb @adbPrefix @startArgs 2>&1 | Out-Null

    Write-Host "[still_res_verify] waiting ${WaitSec}s scenario=$ScenarioId mode=$ModeId..."
    Start-Sleep -Seconds $WaitSec

    & adb @adbPrefix exec-out logcat -d 2>$null |
        Out-File -LiteralPath $logPath -Encoding utf8

    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

    $lines = @(Get-Content -LiteralPath $logPath -ErrorAction SilentlyContinue)
    $hay = ($lines -join "`n")
    $diagLine = @($lines | Where-Object { $_ -match "dng save diag.*rawWxH=(\d+x\d+)" } | Select-Object -Last 1)
    $rawWxH = if ($diagLine) { ([regex]::Match($diagLine[0], "rawWxH=(\d+x\d+)")).Groups[1].Value } else { $null }
    $rawReaderLine = @($lines | Where-Object { $_ -match "RAW ImageReader (\d+x\d+) format=(\d+)" } | Select-Object -Last 1)
    $rawReaderWxH = if ($rawReaderLine) { ([regex]::Match($rawReaderLine[0], "RAW ImageReader (\d+x\d+) format=(\d+)")).Groups[1].Value } else { $null }
    $rawReaderFmt = if ($rawReaderLine) { ([regex]::Match($rawReaderLine[0], "RAW ImageReader (\d+x\d+) format=(\d+)")).Groups[2].Value } else { $null }
    $jpegReaderLine = @($lines | Where-Object { $_ -match "JPEG ImageReader (\d+x\d+)" } | Select-Object -Last 1)
    $jpegReaderWxH = if ($jpegReaderLine) { ([regex]::Match($jpegReaderLine[0], "JPEG ImageReader (\d+x\d+)")).Groups[1].Value } else { $null }
    $rawSupportLine = @($lines | Where-Object { $_ -match "maxResRawSupport larger=(true|false)" } | Select-Object -Last 1)
    $jpegSupportLine = @($lines | Where-Object { $_ -match "maxResJpegSupport larger=(true|false)" } | Select-Object -Last 1)
    $unlockLine = @($lines | Where-Object { $_ -match "maxResUnlock active=(true|false) applied=(true|false).*reason=([^\s]+)" } | Select-Object -Last 1)
    $unlockActive = if ($unlockLine) { ([regex]::Match($unlockLine[0], "active=(true|false)")).Groups[1].Value -eq "true" } else { $false }
    $unlockApplied = if ($unlockLine) { ([regex]::Match($unlockLine[0], "applied=(true|false)")).Groups[1].Value -eq "true" } else { $false }
    $unlockReason = if ($unlockLine) { ([regex]::Match($unlockLine[0], "reason=([^\s]+)")).Groups[1].Value } else { $null }
    $safeModeForced = ($lines | Where-Object { $_ -match "preview seeded experimental safeMode=true \(forced\)" }).Count -gt 0
    $safeModeIgnoredSeeds = ($lines | Where-Object { $_ -match "preview seeded experimental flags ignored \(safe mode active\)" }).Count -gt 0
    $seededExperimentalLine = @($lines | Where-Object { $_ -match "preview seeded experimental master=(true|false) maxRes=(true|false) vendorSession=(true|false)" } | Select-Object -Last 1)
    $seededExperimentalLine = if ($seededExperimentalLine) { $seededExperimentalLine[0] } else { $null }
    $safeModeOn = ($lines | Where-Object { $_ -match "safeMode=on" }).Count -gt 0
    $rawLargerSupported = if ($rawSupportLine) { ([regex]::Match($rawSupportLine[0], "larger=(true|false)")).Groups[1].Value -eq "true" } else { $false }
    $jpegLargerSupported = if ($jpegSupportLine) { ([regex]::Match($jpegSupportLine[0], "larger=(true|false)")).Groups[1].Value -eq "true" } else { $false }
    $seededMode = ($lines | Where-Object { $_ -match "stillResMode=$ModeId" }).Count -gt 0
    $smokeOk = ($lines | Where-Object { $_ -match "captureComposedStill composed_smoke ok=true" }).Count -gt 0
    $rawSaved = ($lines | Where-Object { $_ -match "captureRawStill composed_smoke ok=true saved=" }).Count -gt 0
    $tonalSaved = ($lines | Where-Object { $_ -match "captureIndependentTonalStill composed_smoke ok=true saved=" }).Count -gt 0
    $dngDiagHasIso = ($lines | Where-Object { $_ -match "dng save diag .* iso=\d+" }).Count -gt 0

    return [ordered]@{
        scenario = $ScenarioId
        mode = $ModeId
        rawWxH = $rawWxH
        rawReaderWxH = $rawReaderWxH
        rawReaderFmt = $rawReaderFmt
        jpegReaderWxH = $jpegReaderWxH
        rawLargerSupported = $rawLargerSupported
        jpegLargerSupported = $jpegLargerSupported
        seededMode = $seededMode
        composedSmokeOk = $smokeOk
        metadataDiagHasIso = $dngDiagHasIso
        rawSaved = $rawSaved
        tonalSaved = $tonalSaved
        unlockActive = $unlockActive
        unlockApplied = $unlockApplied
        unlockReason = $unlockReason
        seededExperimentalLine = $seededExperimentalLine
        safeModeForced = $safeModeForced
        safeModeIgnoredSeeds = $safeModeIgnoredSeeds
        safeModeOn = $safeModeOn
        logPath = $logPath
    }
}

$baselineBinned = Run-Scenario -ScenarioId "baseline_stock_binned" -ModeId "binned" -ExtraStartArgs @(
    "--ez", "pns_preview_experimental_master", "false",
    "--ez", "pns_preview_experimental_max_res_unlock", "false",
    "--ez", "pns_preview_experimental_vendor_session", "false",
    "--ez", "pns_preview_force_safe_mode", "false"
) -ResetSafeModeBeforeStart $true
$baselineMaxRes = Run-Scenario -ScenarioId "baseline_stock_max_resolution" -ModeId "max_resolution" -ExtraStartArgs @(
    "--ez", "pns_preview_experimental_master", "false",
    "--ez", "pns_preview_experimental_max_res_unlock", "false",
    "--ez", "pns_preview_experimental_vendor_session", "false",
    "--ez", "pns_preview_force_safe_mode", "false"
) -ResetSafeModeBeforeStart $true
$experimentalBinned = Run-Scenario -ScenarioId "experimental_enabled_binned" -ModeId "binned" -ExtraStartArgs @(
    "--ez", "pns_preview_experimental_master", "true",
    "--ez", "pns_preview_experimental_max_res_unlock", "true",
    "--ez", "pns_preview_experimental_vendor_session", $(if ($ExperimentalVendorSession.IsPresent) { "true" } else { "false" }),
    "--ez", "pns_preview_force_safe_mode", "false"
) -ResetSafeModeBeforeStart $true
$experimentalMaxRes = Run-Scenario -ScenarioId "experimental_enabled_max_resolution" -ModeId "max_resolution" -ExtraStartArgs @(
    "--ez", "pns_preview_experimental_master", "true",
    "--ez", "pns_preview_experimental_max_res_unlock", "true",
    "--ez", "pns_preview_experimental_vendor_session", $(if ($ExperimentalVendorSession.IsPresent) { "true" } else { "false" }),
    "--ez", "pns_preview_force_safe_mode", "false"
) -ResetSafeModeBeforeStart $true
$safeModeForced = Run-Scenario -ScenarioId "safe_mode_forced_max_resolution" -ModeId "max_resolution" -ExtraStartArgs @(
    "--ez", "pns_preview_experimental_master", "true",
    "--ez", "pns_preview_experimental_max_res_unlock", "true",
    "--ez", "pns_preview_experimental_vendor_session", $(if ($ExperimentalVendorSession.IsPresent) { "true" } else { "false" }),
    "--ez", "pns_preview_force_safe_mode", "true"
) -ResetSafeModeBeforeStart $true

function Get-ModeDelta($binnedResult, $maxResult) {
    $rawDiagSwitched = ($null -ne $binnedResult.rawWxH -and $null -ne $maxResult.rawWxH -and $binnedResult.rawWxH -ne $maxResult.rawWxH)
    $rawReaderSwitched = ($null -ne $binnedResult.rawReaderWxH -and $null -ne $maxResult.rawReaderWxH -and $binnedResult.rawReaderWxH -ne $maxResult.rawReaderWxH)
    $jpegReaderSwitched = ($null -ne $binnedResult.jpegReaderWxH -and $null -ne $maxResult.jpegReaderWxH -and $binnedResult.jpegReaderWxH -ne $maxResult.jpegReaderWxH)
    $sizeSwitched = ($rawDiagSwitched -or $rawReaderSwitched -or $jpegReaderSwitched)
    $largerSupportedOnDevice = ($maxResult.rawLargerSupported -or $maxResult.jpegLargerSupported)
    return [ordered]@{
        rawDiagSwitched = $rawDiagSwitched
        rawReaderSwitched = $rawReaderSwitched
        jpegReaderSwitched = $jpegReaderSwitched
        sizeSwitched = $sizeSwitched
        largerSupportedOnDevice = $largerSupportedOnDevice
    }
}

$baselineDelta = Get-ModeDelta $baselineBinned $baselineMaxRes
$experimentalDelta = Get-ModeDelta $experimentalBinned $experimentalMaxRes
$unlockProducedDelta = ($experimentalDelta.sizeSwitched -and -not $baselineDelta.sizeSwitched)
$safeModeFailClosed = $safeModeForced.safeModeOn -and -not $safeModeForced.unlockApplied
$explicitNoUnlockEvidence =
    (-not $experimentalBinned.unlockApplied) -and
    (-not $experimentalMaxRes.unlockApplied) -and
    ($experimentalBinned.unlockReason -in @("root_not_granted", "device_not_cph2583", "master_toggle_off", "safe_mode_active", "still_mode_not_max", "setprop_failed", "setprop_not_sticky")) -and
    ($experimentalMaxRes.unlockReason -in @("root_not_granted", "device_not_cph2583", "master_toggle_off", "safe_mode_active", "still_mode_not_max", "setprop_failed", "setprop_not_sticky"))

$scenarioPassBase =
    $baselineBinned.seededMode -and
    $baselineMaxRes.seededMode -and
    $baselineBinned.composedSmokeOk -and
    $baselineMaxRes.composedSmokeOk -and
    $baselineBinned.metadataDiagHasIso -and
    $baselineMaxRes.metadataDiagHasIso -and
    $baselineBinned.rawSaved -and
    $baselineMaxRes.rawSaved -and
    $baselineBinned.tonalSaved -and
    $baselineMaxRes.tonalSaved -and
    ((-not $baselineDelta.largerSupportedOnDevice) -or $baselineDelta.sizeSwitched)

$scenarioPassExperimental =
    $experimentalBinned.seededMode -and
    $experimentalMaxRes.seededMode -and
    $experimentalBinned.composedSmokeOk -and
    $experimentalMaxRes.composedSmokeOk -and
    $experimentalBinned.metadataDiagHasIso -and
    $experimentalMaxRes.metadataDiagHasIso -and
    $experimentalBinned.rawSaved -and
    $experimentalMaxRes.rawSaved -and
    $experimentalBinned.tonalSaved -and
    $experimentalMaxRes.tonalSaved

$pass =
    $scenarioPassBase -and
    $scenarioPassExperimental -and
    $safeModeFailClosed -and
    ($unlockProducedDelta -or $explicitNoUnlockEvidence)

$summary = [ordered]@{
    schema = "pns.still_resolution_mode_verify.v2"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    serial = if ($Serial) { $Serial } else { "default" }
    pass = $pass
    checks = [ordered]@{
        baseline = [ordered]@{
            binned = $baselineBinned
            maxResolution = $baselineMaxRes
            deltas = $baselineDelta
            pass = $scenarioPassBase
        }
        experimentalEnabled = [ordered]@{
            binned = $experimentalBinned
            maxResolution = $experimentalMaxRes
            deltas = $experimentalDelta
            pass = $scenarioPassExperimental
            unlockProducedDelta = $unlockProducedDelta
            explicitNoUnlockEvidence = $explicitNoUnlockEvidence
        }
        safeModeForced = [ordered]@{
            run = $safeModeForced
            failClosed = $safeModeFailClosed
        }
    }
    rollback = [ordered]@{
        shouldRollback = (-not ($unlockProducedDelta -or $explicitNoUnlockEvidence)) -or (-not $safeModeFailClosed)
        reasons = @(
            $(if (-not $unlockProducedDelta -and -not $explicitNoUnlockEvidence) { "no_stream_delta_vs_stock" }),
            $(if ($explicitNoUnlockEvidence) { "explicit_no_unlock_evidence_recorded" }),
            $(if (-not $safeModeFailClosed) { "safe_mode_fail_closed_broken" })
        ) | Where-Object { $_ }
    }
    artifacts = [ordered]@{
        outDir = $outDir
    }
}

$summaryPath = Join-Path $outDir "still_resolution_mode_verify_summary.json"
$summary | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $summaryPath -Encoding utf8

$md = @(
    "# Still resolution mode verify",
    "",
    "- **PASS:** $pass",
    "",
    "## Baseline stock",
    "- **Binned rawWxH:** $($baselineBinned.rawWxH)",
    "- **Max rawWxH:** $($baselineMaxRes.rawWxH)",
    "- **Binned RAW ImageReader:** $($baselineBinned.rawReaderWxH) fmt=$($baselineBinned.rawReaderFmt)",
    "- **Max RAW ImageReader:** $($baselineMaxRes.rawReaderWxH) fmt=$($baselineMaxRes.rawReaderFmt)",
    "- **Binned JPEG ImageReader:** $($baselineBinned.jpegReaderWxH)",
    "- **Max JPEG ImageReader:** $($baselineMaxRes.jpegReaderWxH)",
    "- **RAW diag switched:** $($baselineDelta.rawDiagSwitched)",
    "- **RAW reader switched:** $($baselineDelta.rawReaderSwitched)",
    "- **JPEG reader switched:** $($baselineDelta.jpegReaderSwitched)",
    "- **Larger stream supported on device:** $($baselineDelta.largerSupportedOnDevice)",
    "- **Size switched:** $($baselineDelta.sizeSwitched)",
    "",
    "## Experimental enabled",
    "- **Seed line:** $($experimentalMaxRes.seededExperimentalLine)",
    "- **Unlock active/applied/reason:** $($experimentalMaxRes.unlockActive)/$($experimentalMaxRes.unlockApplied)/$($experimentalMaxRes.unlockReason)",
    "- **Binned rawWxH:** $($experimentalBinned.rawWxH)",
    "- **Max rawWxH:** $($experimentalMaxRes.rawWxH)",
    "- **Size switched:** $($experimentalDelta.sizeSwitched)",
    "- **Unlock produced delta vs stock:** $unlockProducedDelta",
    "- **Explicit no-unlock evidence:** $explicitNoUnlockEvidence",
    "",
    "## Safe mode forced",
    "- **Safe mode forced line seen:** $($safeModeForced.safeModeForced)",
    "- **Safe mode active line seen:** $($safeModeForced.safeModeOn)",
    "- **Unlock applied while forced safe mode:** $($safeModeForced.unlockApplied)",
    "- **Fail-closed check:** $safeModeFailClosed",
    "",
    "## Rollback decision",
    "- **Should rollback experimental lane:** $($summary.rollback.shouldRollback)",
    "- **Reasons:** $([string]::Join(', ', @($summary.rollback.reasons)))",
    "",
    "Summary JSON: $summaryPath",
    "Artifacts root: $outDir"
)
$mdPath = Join-Path $outDir "still_resolution_mode_verify_summary.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "STILL_RESOLUTION_MODE_VERIFY: pass=$pass baselineSwitch=$($baselineDelta.sizeSwitched) experimentalSwitch=$($experimentalDelta.sizeSwitched) safeModeFailClosed=$safeModeFailClosed"
Write-Host "Artifacts: $outDir"
if (-not $pass) { exit 1 }
exit 0

