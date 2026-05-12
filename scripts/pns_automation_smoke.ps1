# Fleet / CI orchestration: host toolchain + Milestone 9 chrome gate + Milestone 7 failure-matrix smoke +
# optional Milestone 9 ChromeUxPack preview scenario (when an authorized adb device is present).
#
# - Always runs `pns_verify_toolchain.ps1 -RunTests` unless `-SkipVerifyToolchain`.
# - Runs `pns_chrome_ux_gate.ps1 -SkipHost` (device-only chrome UX checks; assumes verify already ran).
# - Runs `pns_failure_matrix_smoke.ps1` unless `-SkipFailureMatrix`.
# - When at least one authorized device is connected: runs `pns_adb_preview_validate.ps1 -ChromeUxPack` unless `-SkipChromeUxPack`.
# - Optional `-RunAeHighlightProbe`: runs `pns_ae_highlight_probe_adb.ps1` (debuggable APK; probe markdown + optional root JSON).
#
# Optional `-TryAdbRoot`: runs `adb root` best-effort
# (userdebug / rooted fleet); may restart adbd  -  waits briefly before device scripts.
#
# Optional full `pns_adb_preview_validate.ps1` (capture-heavy; not ChromeUxPack) when device present.
# Optional `-RequireMediaStoreDcim` (requires `-RunFullAdbPreviewValidate`): fail if mediastore_probe.json dcimHasPnsCapture is false.
#
# Optional `-AppendSection5` (+ `-ProbePlan`): after a fully passing smoke (no prior step failure), runs `pns_probe_append_section5.ps1`
# for gate JSON under this run's `outDir` when artifacts exist: `failure_matrix_smoke.json`, full-validate `super_macro_gate.json` / `mediastore_probe.json`,
# and `chrome_ux_gate.json` only when an authorized adb device was present (host-only chrome pass does not append PROBE_BUILD_PLAN section 5).
# `mediastore_probe.json`: uses `-PassOnly` only when `dcimHasPnsCapture` is true so empty-DCIM runs still append an audit row.
#
# Serial: use `-Serial` or `scripts/pns_adb_device.env` (`PNS_ADB_SERIAL`, USB serial from adb devices).
#
# Exit code: non-zero if any invoked script fails.

param(
    [string]$Serial = "",
    [switch]$SkipVerifyToolchain,
    [switch]$SkipInstall,
    [switch]$SkipGradle,
    [switch]$TryAdbRoot,
    [switch]$SkipFailureMatrix,
    [switch]$SkipChromeUxPack,
    [switch]$RunFullAdbPreviewValidate,
    [switch]$RequireMediaStoreDcim,
    [switch]$RunAeHighlightProbe,
    [switch]$AppendSection5,
    [string]$ProbePlan = ""
)

$ErrorActionPreference = "Stop"

if ($RequireMediaStoreDcim.IsPresent -and -not $RunFullAdbPreviewValidate.IsPresent) {
    throw "-RequireMediaStoreDcim requires -RunFullAdbPreviewValidate (full adb preview run writes mediastore_probe.json)."
}

$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\automation_smoke_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($k -eq "PNS_ADB_SERIAL") {
            return $v
        }
    }
    return $null
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs 2>$null
    }
    else {
        & adb @CmdArgs 2>$null
    }
}

function Test-AdbAuthorizedDevice {
    $lines = @(adb devices 2>&1)
    foreach ($line in $lines) {
        if ($line -match '\tdevice$') {
            return $true
        }
    }
    return $false
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[automation_smoke] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

if ($TryAdbRoot.IsPresent) {
    Write-Host "`[automation_smoke] TryAdbRoot: adb root (best-effort)"
    if ($Serial) {
        adb -s $Serial root 2>$null | Out-Null
    }
    else {
        adb root 2>$null | Out-Null
    }
    Start-Sleep -Seconds 2
}

$failed = $false
$summary = [ordered]@{
    schema         = "pns.automation_smoke.v1"
    generatedAtUtc = [DateTime]::UtcNow.ToString("o")
    outDir         = $outDir
    serial         = $(if ($Serial) { $Serial } else { "default" })
    tryAdbRoot     = $TryAdbRoot.IsPresent
    steps          = [ordered]@{}
}

function Step-Set([string]$Name, [bool]$Ok) {
    $summary.steps[$Name] = [ordered]@{ ok = $Ok }
    if (-not $Ok) { $script:failed = $true }
}

if (-not $SkipVerifyToolchain.IsPresent) {
    Write-Host ""
    Write-Host "========== [automation_smoke] pns_verify_toolchain.ps1 -RunTests =========="
    $verify = Join-Path $PSScriptRoot "pns_verify_toolchain.ps1"
    & $verify -ProjectRoot $projRoot -RunTests
    Step-Set "verifyToolchain" ($LASTEXITCODE -eq 0)
}
else {
    Write-Host "`[automation_smoke] -SkipVerifyToolchain"
    Step-Set "verifyToolchain" $true
}

Write-Host ""
Write-Host "========== [automation_smoke] pns_chrome_ux_gate.ps1 -SkipHost =========="
$chrome = Join-Path $PSScriptRoot "pns_chrome_ux_gate.ps1"
$chromeArgs = @{
    SkipHost = $true
    OutDir   = (Join-Path $outDir "chrome_ux_gate")
}
if ($Serial) { $chromeArgs["Serial"] = $Serial }
if ($SkipInstall.IsPresent) { $chromeArgs["SkipInstall"] = $true }
if ($SkipGradle.IsPresent) { $chromeArgs["SkipGradle"] = $true }
& $chrome @chromeArgs
Step-Set "chromeUxGate" ($LASTEXITCODE -eq 0)

if (-not $SkipFailureMatrix.IsPresent) {
    Write-Host ""
    Write-Host "========== [automation_smoke] pns_failure_matrix_smoke.ps1 =========="
    $fm = Join-Path $PSScriptRoot "pns_failure_matrix_smoke.ps1"
    $fmArgs = @{ OutDir = (Join-Path $outDir "failure_matrix_smoke") }
    if ($Serial) { $fmArgs["Serial"] = $Serial }
    if ($SkipInstall.IsPresent) { $fmArgs["SkipInstall"] = $true }
    & $fm @fmArgs
    Step-Set "failureMatrixSmoke" ($LASTEXITCODE -eq 0)
}
else {
    Write-Host "`[automation_smoke] -SkipFailureMatrix"
    Step-Set "failureMatrixSmoke" $true
}

$adbOk = Test-AdbAuthorizedDevice
$summary["adbAuthorizedDevice"] = $adbOk

if ($adbOk -and -not $SkipChromeUxPack.IsPresent) {
    Write-Host ""
    Write-Host "========== [automation_smoke] pns_adb_preview_validate.ps1 -ChromeUxPack =========="
    $adv = Join-Path $PSScriptRoot "pns_adb_preview_validate.ps1"
    $advArgs = @{
        ChromeUxPack = $true
        OutDir       = (Join-Path $outDir "adb_preview_chrome_ux")
    }
    if ($Serial) { $advArgs["Serial"] = $Serial }
    if ($SkipInstall.IsPresent) { $advArgs["SkipInstall"] = $true }
    & $adv @advArgs
    Step-Set "adbPreviewChromeUxPack" ($LASTEXITCODE -eq 0)
}
else {
    if (-not $adbOk) {
        Write-Host "`[automation_smoke] No authorized adb device  -  skipping pns_adb_preview_validate -ChromeUxPack"
    }
    else {
        Write-Host "`[automation_smoke] -SkipChromeUxPack"
    }
    Step-Set "adbPreviewChromeUxPack" $true
}

if ($adbOk -and $RunAeHighlightProbe.IsPresent) {
    Write-Host ""
    Write-Host "========== [automation_smoke] pns_ae_highlight_probe_adb.ps1 =========="
    $ae = Join-Path $PSScriptRoot "pns_ae_highlight_probe_adb.ps1"
    $aeDir = Join-Path $outDir "ae_highlight_probe"
    New-Item -ItemType Directory -Force -Path $aeDir | Out-Null
    $aeArgs = @{ OutDir = $aeDir; WaitSec = 10; PullAttempts = 8 }
    if ($Serial) { $aeArgs["Serial"] = $Serial }
    if ($SkipInstall.IsPresent) { $aeArgs["SkipInstall"] = $true }
    & $ae @aeArgs
    Step-Set "aeHighlightProbe" ($LASTEXITCODE -eq 0)
}
elseif ($RunAeHighlightProbe.IsPresent -and -not $adbOk) {
    Write-Host "`[automation_smoke] No authorized adb device  -  skipping -RunAeHighlightProbe"
    Step-Set "aeHighlightProbe" $true
}
else {
    Step-Set "aeHighlightProbe" $true
}

if ($adbOk -and $RunFullAdbPreviewValidate.IsPresent) {
    Write-Host ""
    Write-Host "========== [automation_smoke] pns_adb_preview_validate.ps1 (full capture suite) =========="
    $advFull = Join-Path $PSScriptRoot "pns_adb_preview_validate.ps1"
    $advFullDir = Join-Path $outDir "adb_preview_full_validate"
    $advFullArgs = @{ OutDir = $advFullDir }
    if ($Serial) { $advFullArgs["Serial"] = $Serial }
    if ($SkipInstall.IsPresent) { $advFullArgs["SkipInstall"] = $true }
    & $advFull @advFullArgs
    $advOk = ($LASTEXITCODE -eq 0)
    Step-Set "adbPreviewFullValidate" $advOk

    if ($RequireMediaStoreDcim.IsPresent) {
        $probePath = Join-Path $advFullDir "mediastore_probe.json"
        $msOk = $false
        if (Test-Path -LiteralPath $probePath) {
            try {
                $pj = ([System.IO.File]::ReadAllText($probePath) | ConvertFrom-Json)
                $msOk = [bool]$pj.dcimHasPnsCapture
            }
            catch {
                $msOk = $false
            }
        }
        Step-Set "mediaStoreDcimProbe" $msOk
        if (-not $msOk) {
            Write-Host "`[automation_smoke] FAIL: mediastore_probe.dcimHasPnsCapture is not true (see $probePath)"
        }
    }
    else {
        Step-Set "mediaStoreDcimProbe" $true
    }
}
else {
    if ($RunFullAdbPreviewValidate.IsPresent -and -not $adbOk) {
        Write-Host "`[automation_smoke] No authorized adb device  -  skipping -RunFullAdbPreviewValidate"
    }
    Step-Set "adbPreviewFullValidate" $true
    Step-Set "mediaStoreDcimProbe" $true
}

if ($AppendSection5.IsPresent) {
    if ($failed) {
        Write-Host "`[automation_smoke] -AppendSection5 skipped (smoke already has failures)"
        $summary["appendSection5"] = [ordered]@{ ran = $false; reason = "smoke_failed" }
    }
    else {
        Write-Host ""
        Write-Host "========== [automation_smoke] pns_probe_append_section5.ps1 (PassOnly except mediastore when dcim empty) =========="
        $append = Join-Path $PSScriptRoot "pns_probe_append_section5.ps1"
        $appendInvokes = New-Object System.Collections.Generic.List[hashtable]

        function Invoke-AutomationAppend([string]$Label, [string]$StepKey, [string]$RelJsonPath, [bool]$AppendPassOnly = $true) {
            if (-not ($script:summary.steps.Contains($StepKey))) {
                return
            }
            $stepOk = [bool]$script:summary.steps[$StepKey].ok
            if (-not $stepOk) {
                return
            }
            if ($Label -eq "chrome_ux_gate" -and -not $adbOk) {
                Write-Host "`[automation_smoke] AppendSection5 skip chrome_ux_gate  -  no authorized adb device (host-only pass does not append PROBE_BUILD_PLAN section 5)"
                return
            }
            $jp = Join-Path $script:outDir $RelJsonPath
            if (-not (Test-Path -LiteralPath $jp)) {
                Write-Host "`[automation_smoke] AppendSection5 skip $Label  -  missing $jp"
                return
            }
            Write-Host "`[automation_smoke] AppendSection5 $Label <- $jp"
            $invokeArgs = @{ GateJson = $jp }
            if ($AppendPassOnly) {
                $invokeArgs["PassOnly"] = $true
            }
            if ($ProbePlan) {
                $invokeArgs["ProbePlan"] = $ProbePlan
            }
            & $append @invokeArgs
            $ex = $LASTEXITCODE
            [void]$appendInvokes.Add(@{ label = $Label; json = $RelJsonPath; exitCode = $ex })
            if ($ex -ne 0) {
                $script:failed = $true
                Write-Host "`[automation_smoke] FAIL: probe_append_section5 exit=$ex for $jp"
            }
        }

        Invoke-AutomationAppend "chrome_ux_gate" "chromeUxGate" "chrome_ux_gate\chrome_ux_gate.json"
        Invoke-AutomationAppend "failure_matrix_smoke" "failureMatrixSmoke" "failure_matrix_smoke\failure_matrix_smoke.json"
        Invoke-AutomationAppend "super_macro_gate" "adbPreviewFullValidate" "adb_preview_full_validate\super_macro_gate.json"
        # mediastore: `pns_probe_append_section5 -PassOnly` skips append when dcimHasPnsCapture=false; still record empty DCIM in §5.
        $msRel = "adb_preview_full_validate\mediastore_probe.json"
        $msJp = Join-Path $outDir $msRel
        $msPassOnly = $true
        if (Test-Path -LiteralPath $msJp) {
            try {
                $msj = [System.IO.File]::ReadAllText($msJp) | ConvertFrom-Json
                if ($null -ne $msj.dcimHasPnsCapture) {
                    $msPassOnly = [bool]$msj.dcimHasPnsCapture
                }
            }
            catch {
                $msPassOnly = $true
            }
        }
        Invoke-AutomationAppend "mediastore_probe" "adbPreviewFullValidate" $msRel $msPassOnly

        $summary["appendSection5"] = [ordered]@{
            ran     = $true
            invokes = @($appendInvokes.ToArray())
        }
    }
}
else {
    $summary["appendSection5"] = [ordered]@{ ran = $false; reason = "not_requested" }
}

$summary["pass"] = -not $failed
$jsonPath = Join-Path $outDir "automation_smoke.json"
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host ""
Write-Host "`[automation_smoke] Wrote $jsonPath pass=$($summary.pass)"

if ($failed) {
    exit 1
}
exit 0
