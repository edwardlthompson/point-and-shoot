#Requires -Version 5.1
<#
.SYNOPSIS
  Milestone **15** host-only gate (no USB): toolchain + M15 scripts + unit tests.
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
    python "$PSScriptRoot\pns_colorchecker_de2000_gate.py"
    python "$PSScriptRoot\pns_dng_aesthetic_gate.py"
    & "$PSScriptRoot\pns_dng_rawpy_decode_gate.ps1"
    & "$PSScriptRoot\pns_hfr_color_compare_frames.ps1" -HostOnly
    & "$PSScriptRoot\pns_video_matrix_verify.ps1" -HostOnly
    & "$PSScriptRoot\pns_still_mode_compare_gate.ps1" -HostOnly
    & "$PSScriptRoot\pns_keystore_verify.ps1"
    & "$PSScriptRoot\pns_release_asset_check.ps1"
    & "$PSScriptRoot\pns_a11y_dump_gate.ps1" -HostOnly
    & "$PSScriptRoot\pns_eye_af_pixel_gate.ps1" -HostOnly
    Write-Host "MILESTONE 15 HOST GATE: PASS"
    exit 0
} finally {
    Pop-Location
}
