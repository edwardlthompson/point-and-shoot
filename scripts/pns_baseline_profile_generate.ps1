#Requires -Version 5.1
<#
.SYNOPSIS
  Runs Macrobenchmark baseline-profile generation for :app (USB device required).

.DESCRIPTION
  - Optionally disables window/transition/animator scales (reduces Macrobenchmark launch-detection flakes).
  - Invokes `.\scripts\pns_gradlew.ps1 :app:generateBaselineProfile`.
  - Artifacts land under `app\src\release\generated\baselineProfiles\` when using AGP + baseline-profile plugin 1.4+.

.PARAMETER SkipAnimationTweaks
  Do not run `adb shell settings put global ..._animation_scale 0`.

.PARAMETER GradleArgs
  Extra args forwarded to Gradle after the task name.
#>
param(
    [switch]$SkipAnimationTweaks,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$root = (Resolve-Path (Join-Path $here "..")).Path
$pnsGradlew = Join-Path $here "pns_gradlew.ps1"

$resolve = Join-Path $here "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

if (-not $SkipAnimationTweaks) {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) {
        adb shell settings put global window_animation_scale 0 2>$null
        adb shell settings put global transition_animation_scale 0 2>$null
        adb shell settings put global animator_duration_scale 0 2>$null
    }
}

Push-Location $root
try {
    & $pnsGradlew @(":app:generateBaselineProfile") @GradleArgs
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
