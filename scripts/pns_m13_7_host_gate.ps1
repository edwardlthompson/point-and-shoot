#Requires -Version 5.1
<#
.SYNOPSIS
  Milestone 13.7 — host-only gate (toolchain + docs); documents H.7 human blocker.

.DESCRIPTION
  Does not claim Milestone 13 fully closed. Runs pns_verify_toolchain.ps1 -RunTests and
  pns_ai_features_verify.ps1 -HostOnly; writes gate.json with automated vs human rows.
#>
param(
    [switch]$SkipToolchain,
    [switch]$SkipAiHost
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$ts = Get-Date -Format "yyyyMMdd_HHmmss"
$gateDir = Join-Path $repoRoot "hfr-runs\m13_7_host_gate_$ts"
New-Item -ItemType Directory -Force -Path $gateDir | Out-Null

$gate = [ordered]@{
    schema = "m13_7_host_gate.v1"
    timestampUtc = (Get-Date).ToUniversalTime().ToString("o")
    gateDir = $gateDir
    milestone13FullyClosed = $false
    humanBlocker = "H.7"
    doc = "docs/M13_7_GATE.md"
    toolchainPass = $null
    aiFeaturesHostPass = $null
    automatedUsbEvidence = @(
        "hfr-runs/aux_dng_capture_analyze_20260519_235745/",
        "hfr-runs/m13_3f_gate_20260520_012341/",
        "hfr-runs/m13_8d_gate_20260520_020059/"
    )
    gateResult = "HOST_PREP_PASS"
}

if (-not $SkipToolchain) {
    Write-Host "[m13_7_host] pns_verify_toolchain.ps1 -RunTests..."
    & "$PSScriptRoot\pns_verify_toolchain.ps1" -RunTests
    $gate.toolchainPass = ($LASTEXITCODE -eq 0)
    if (-not $gate.toolchainPass) { $gate.gateResult = "FAIL" }
}

if (-not $SkipAiHost) {
    Write-Host "[m13_7_host] pns_ai_features_verify.ps1 -HostOnly..."
    & "$PSScriptRoot\pns_ai_features_verify.ps1" -HostOnly
    $gate.aiFeaturesHostPass = ($LASTEXITCODE -eq 0)
    if (-not $gate.aiFeaturesHostPass) { $gate.gateResult = "FAIL" }
}

$jsonPath = Join-Path $gateDir "gate.json"
$gate | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonPath
Write-Host "[m13_7_host] $($gate.gateResult) (M13.7 awaits H.7) -> $jsonPath"

if ($gate.gateResult -eq "FAIL") { exit 1 }
exit 0
