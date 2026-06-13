# Canonical legacy device (OP13 / legacy SKU-class) regression lane — thin wrapper.
#
# Implementation: scripts/pns_op13_regression_pack.ps1
# Use primary device CPH2583 for product gates; run this only for optional legacy plugin / DNG parity.

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$RequireProshotParity,
    [switch]$HostOnly
)

$ErrorActionPreference = "Stop"
$impl = Join-Path $PSScriptRoot "pns_op13_regression_pack.ps1"
if (-not (Test-Path -LiteralPath $impl)) {
    Write-Error "Missing implementation script: $impl"
    exit 1
}

$forward = @{}
if ($Serial) { $forward.Serial = $Serial }
if ($OutDir) { $forward.OutDir = $OutDir }
if ($SkipInstall.IsPresent) { $forward.SkipInstall = $true }
if ($AssembleDebug.IsPresent) { $forward.AssembleDebug = $true }
if ($RequireProshotParity.IsPresent) { $forward.RequireProshotParity = $true }
if ($HostOnly.IsPresent) { $forward.HostOnly = $true }

& $impl @forward
exit $LASTEXITCODE
