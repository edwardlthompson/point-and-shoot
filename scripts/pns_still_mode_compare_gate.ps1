#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.3** — still mode luminance compare gate (USB) or host SKIP.
#>
param(
    [switch]$HostOnly,
    [string]$Serial
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if ($HostOnly) {
        Write-Host "STILL MODE COMPARE: SKIP (HostOnly)"
        exit 0
    }
    if (Test-Path "$PSScriptRoot\pns_readout_jpeg_dng_parity.ps1") {
        & "$PSScriptRoot\pns_readout_jpeg_dng_parity.ps1" -Serial $Serial
        exit $LASTEXITCODE
    }
    Write-Host "STILL MODE COMPARE: SKIP (pns_readout_jpeg_dng_parity.ps1 missing)"
    exit 0
} finally {
    Pop-Location
}
