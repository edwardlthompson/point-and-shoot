# Fleet / CI orchestration: host toolchain + Milestone 9 chrome gate + Milestone 7 failure-matrix smoke +
# optional Milestone 9 ChromeUxPack preview scenario (when an authorized adb device is present).
#
# - Always runs `pns_verify_toolchain.ps1 -RunTests` unless `-SkipVerifyToolchain`.
# - Runs `pns_chrome_ux_gate.ps1 -SkipHost` (device-only chrome UX checks; assumes verify already ran).
# - Runs `pns_failure_matrix_smoke.ps1` unless `-SkipFailureMatrix`.
# - When at least one authorized device is connected: runs `pns_adb_preview_validate.ps1 -ChromeUxPack` unless `-SkipChromeUxPack`.
#
# Optional `-TryAdbRoot`: after TCP `adb connect` (when serial is ip:port), runs `adb root` best-effort
# (userdebug / rooted fleet); may restart adbd — waits briefly before device scripts.
#
# Serial: use `-Serial` or `scripts/pns_adb_device.env` (`PNS_ADB_SERIAL`). Wi‑Fi form connects automatically.
#
# Exit code: non-zero if any invoked script fails.

param(
    [string]$Serial = "",
    [switch]$SkipVerifyToolchain,
    [switch]$SkipInstall,
    [switch]$SkipGradle,
    [switch]$TryAdbRoot,
    [switch]$SkipFailureMatrix,
    [switch]$SkipChromeUxPack
)

$ErrorActionPreference = "Stop"

$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\automation_smoke_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

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
        Write-Host "[automation_smoke] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

if ($Serial -match '^\d+\.\d+\.\d+\.\d+:\d+$') {
    Write-Host "[automation_smoke] adb connect $Serial (TCP/IP)"
    Invoke-AdbIgnore @("connect", $Serial)
}

if ($TryAdbRoot.IsPresent) {
    Write-Host "[automation_smoke] TryAdbRoot: adb root (best-effort)"
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
    Write-Host "[automation_smoke] -SkipVerifyToolchain"
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
    Write-Host "[automation_smoke] -SkipFailureMatrix"
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
        Write-Host "[automation_smoke] No authorized adb device — skipping pns_adb_preview_validate -ChromeUxPack"
    }
    else {
        Write-Host "[automation_smoke] -SkipChromeUxPack"
    }
    Step-Set "adbPreviewChromeUxPack" $true
}

$summary["pass"] = -not $failed
$jsonPath = Join-Path $outDir "automation_smoke.json"
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host ""
Write-Host "[automation_smoke] Wrote $jsonPath pass=$($summary.pass)"

if ($failed) {
    exit 1
}
exit 0
