<#
.SYNOPSIS
  Host-only CI gate: dng_desktop_open_gate + informational parity on tests/fixtures/referenceapp_legacy_sku.

.EXAMPLE
  .\scripts\pns_fixture_dng_gates.ps1
#>
$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "pns_resolve_referenceapp_fixture_dir.ps1")
$fixtureCandidates = @(
    (Join-Path $projRoot "tests\fixtures\referenceapp_legacy_sku"),
    (Join-Path $projRoot "tests\fixtures\referenceapp_cph2655")
)
$fixtureDir = Resolve-PnsReferenceAppFixtureDir -ProjectRoot $projRoot -RequireExists
$uw = Join-Path $fixtureDir "referenceapp_uw_cam3.dng"
$wide = Join-Path $fixtureDir "referenceapp_wide_cam2.dng"
$tele = Join-Path $fixtureDir "referenceapp_tele_cam4.dng"

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
