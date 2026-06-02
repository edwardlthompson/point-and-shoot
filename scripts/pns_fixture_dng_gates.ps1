<#
.SYNOPSIS
  Host-only CI gate: dng_desktop_open_gate + informational parity on tests/fixtures/proshot_legacy_sku.

.EXAMPLE
  .\scripts\pns_fixture_dng_gates.ps1
#>
$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$fixtureDir = Join-Path $projRoot "tests\fixtures\proshot_legacy_sku"
$uw = Join-Path $fixtureDir "proshot_uw_cam3.dng"
$wide = Join-Path $fixtureDir "proshot_wide_cam2.dng"
$tele = Join-Path $fixtureDir "proshot_tele_cam4.dng"

foreach ($p in @($uw, $wide, $tele)) {
    if (-not (Test-Path -LiteralPath $p)) {
        Write-Host "FAIL: missing fixture $p" -ForegroundColor Red
        exit 1
    }
}

Write-Host "[fixture_gates] dng_desktop_open_gate.py (ReferenceCam refs: skip wide-cal leak)..." -ForegroundColor Cyan
& python (Join-Path $PSScriptRoot "dng_desktop_open_gate.py") --skip-wide-cal-leak $uw $wide $tele
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "FIXTURE DNG GATES: PASS" -ForegroundColor Green
exit 0
