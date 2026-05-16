<#.SYNOPSIS
  Fleet probe entry point (cross-cutting roadmap). Chains existing probe helpers; extend as needed.
  See AGENTS.md for `pns_ae_highlight_probe_adb.ps1`, `pns_face_meter_probe.ps1`, etc.
#>
param(
  [switch] $SkipGradle,
  [string] $Serial
)

$ErrorActionPreference = "Stop"
$envArgs = @()
if ($Serial) { $envArgs += "-Serial"; $envArgs += $Serial }

Write-Host "pns_probe_device: running milestone-style host checks (no-op smoke)."
& "$PSScriptRoot\pns_root_capability_adb.ps1" @envArgs -OutDir (Join-Path $PSScriptRoot "..\hfr-runs\probe_device_latest") | Out-Null
Write-Host "pns_probe_device: done (artifacts under hfr-runs\probe_device_latest)."
