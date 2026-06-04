#Requires -Version 5.1
<#
.SYNOPSIS
  Milestone **H** host gate (no USB): agent-runnable H.1 / H.4 scripts + fixture DNG gates.

.EXAMPLE
  .\scripts\pns_milestone_h_host_gate.ps1
  .\scripts\pns_milestone_h_host_gate.ps1 -SkipGradle
#>
param([switch]$SkipGradle)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (-not $SkipGradle) {
        & "$PSScriptRoot\pns_verify_toolchain.ps1" -RunTests
        if ($LASTEXITCODE -ne 0) { throw "toolchain failed" }
    }
    python "$PSScriptRoot\pns_passport_ce_values.py"
    if ($LASTEXITCODE -ne 0) { throw "passport_ce_values failed" }
    python "$PSScriptRoot\pns_colorchecker_de2000_gate.py"
    if ($LASTEXITCODE -ne 0) { throw "colorchecker gate failed" }
    $self = Join-Path $root "hfr-runs\aesthetic_selftest_h1"
    if (-not (Test-Path (Join-Path $self "M14_uw.dng"))) {
        New-Item -ItemType Directory -Force -Path $self | Out-Null
        Copy-Item "tests\fixtures\referenceapp_legacy_sku\referenceapp_uw_cam3.dng" (Join-Path $self "M14_uw.dng")
        Copy-Item "tests\fixtures\referenceapp_legacy_sku\referenceapp_wide_cam2.dng" (Join-Path $self "M23_wide.dng")
        Copy-Item "tests\fixtures\referenceapp_legacy_sku\referenceapp_tele_cam4.dng" (Join-Path $self "M73_tele.dng")
    }
    python "$PSScriptRoot\pns_dng_aesthetic_gate.py" --ps-dir $self
    if ($LASTEXITCODE -ne 0) { throw "dng_aesthetic_gate failed" }
    & "$PSScriptRoot\pns_fixture_dng_gates.ps1"
    if ($LASTEXITCODE -ne 0) { throw "fixture_dng_gates failed" }
    & "$PSScriptRoot\pns_dng_rawpy_decode_gate.ps1"
    if ($LASTEXITCODE -ne 0) { throw "dng_rawpy_decode_gate failed" }
    & "$PSScriptRoot\pns_keystore_verify.ps1"
    if ($LASTEXITCODE -ne 0) { throw "keystore_verify failed" }
    & "$PSScriptRoot\pns_release_asset_check.ps1"
    if ($LASTEXITCODE -ne 0) { throw "release_asset_check failed" }
    & "$PSScriptRoot\pns_gitlab_setup.ps1" -Verify
    if ($LASTEXITCODE -ne 0) { throw "gitlab_verify failed" }
    Write-Host "MILESTONE H HOST GATE: PASS"
    exit 0
} finally {
    Pop-Location
}
