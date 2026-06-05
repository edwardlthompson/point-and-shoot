<#
.SYNOPSIS
  Verify MONO-mode still capture with tiered success signals.

.DESCRIPTION
  Wraps pns_photo_capture_verify.ps1 in MONO mode and accepts either:
    - captureIndependentTonalStill composed_smoke ok=true saved=
    - MONO_FALLBACK_SNAPSHOT_SAVED
#>
param(
    [string]$Serial = "",
    [int]$MaxAttempts = 20,
    [int]$WaitSec = 55,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$scriptPath = Join-Path $PSScriptRoot "pns_photo_capture_verify.ps1"
if (-not (Test-Path -LiteralPath $scriptPath)) {
    throw "Missing script: $scriptPath"
}

Write-Host "[mono_capture_verify] invoking pns_photo_capture_verify in MONO mode..."
if ($Serial) {
    & $scriptPath `
        -Serial $Serial `
        -Dial "MONO" `
        -MonoMode `
        -MaxAttempts $MaxAttempts `
        -WaitSec $WaitSec `
        -SkipAssemble:$SkipAssemble `
        -SkipInstall:$SkipInstall
}
else {
    & $scriptPath `
        -Dial "MONO" `
        -MonoMode `
        -MaxAttempts $MaxAttempts `
        -WaitSec $WaitSec `
        -SkipAssemble:$SkipAssemble `
        -SkipInstall:$SkipInstall
}
$exitCode = $LASTEXITCODE
Write-Host "[mono_capture_verify] exit=$exitCode"
exit $exitCode

