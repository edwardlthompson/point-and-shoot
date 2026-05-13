<#
.SYNOPSIS
  **`assembleDebug`** + USB **`pns_capture_pipeline_verify.ps1`** after restoring bisect-reverted capture code.

.DESCRIPTION
  Use after changing **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** capture code so merges cannot ship without a green
  **`PNS.AdbValidation`** **`captureRawStill 1/1 ok=true saved=`** needle on hardware.

  **May 2026:** Re-applying **every** §1–§5 “Milestone shipping” hunk at once failed this gate on **CPH2655**; the **max verified**
  combo on **`8bf09993`** restores **§1** + **§5** while keeping **§4a** and **§2** at bisect values — see **`docs/REVERTED_FEATURES_RESTORE_LIST.md`** §8.
  Still run this script after **any** capture touch so regressions cannot merge silently.

  Forwards **`-Serial`**, **`-MaxAttempts`**, **`-WaitSec`**, **`-Fast`**, **`-SweepCameraIds`**, **`-SkipAssemble`**, **`-SkipInstall`**
  to **`pns_capture_pipeline_verify.ps1`** (which wraps **`pns_photo_capture_verify.ps1`**).

.EXAMPLE
  . .\scripts\pns_resolve_adb.ps1 -PrependToPath -Quiet
  .\scripts\pns_capture_restore_verified.ps1 -Fast -WaitSec 75 -MaxAttempts 2

.EXAMPLE
  .\scripts\pns_capture_restore_verified.ps1 -SkipAssemble -Fast -MaxAttempts 1 -WaitSec 75
#>
param(
    [string]$Serial = "",
    [int]$MaxAttempts = 2,
    [int]$WaitSec = 75,
    [switch]$Fast,
    [switch]$SweepCameraIds,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
$gate = Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1"
if (-not (Test-Path -LiteralPath $gate)) { throw "Missing $gate" }

if (-not $SkipAssemble) {
    Write-Host "[capture_restore_verified] assembleDebug..."
    & $gw ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
}

$argList = [System.Collections.Generic.List[string]]::new()
$argList.Add("-NoProfile")
$argList.Add("-ExecutionPolicy")
$argList.Add("Bypass")
$argList.Add("-File")
$argList.Add($gate)
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $argList.Add("-Serial")
    $argList.Add($Serial)
}
$argList.Add("-MaxAttempts")
$argList.Add("$MaxAttempts")
$argList.Add("-WaitSec")
$argList.Add("$WaitSec")
if ($Fast) { $argList.Add("-Fast") }
if ($SweepCameraIds) { $argList.Add("-SweepCameraIds") }
if ($SkipAssemble) { $argList.Add("-SkipAssemble") }
if ($SkipInstall) { $argList.Add("-SkipInstall") }
$argList.Add("-BisectStep")
$argList.Add("restore-verified")
$argList.Add("-Notes")
$argList.Add("pns_capture_restore_verified.ps1 post-restore gate")

Write-Host "[capture_restore_verified] running pns_capture_pipeline_verify.ps1 ..."
$p = Start-Process -FilePath "powershell.exe" -ArgumentList $argList -NoNewWindow -PassThru -Wait:$true
$code = 0
try {
    if ($null -ne $p.ExitCode) { $code = [int]$p.ExitCode }
}
catch {
    $code = 1
}
if ($code -ne 0) {
    throw "pns_capture_pipeline_verify.ps1 failed exit=$code (USB scripted RAW still gate)"
}
Write-Host "[capture_restore_verified] OK (see hfr-runs/photo_capture_verify_* and docs/CAPTURE_PIPELINE_VERIFY_LATEST.json)"
