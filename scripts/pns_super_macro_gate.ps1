<#
.SYNOPSIS
  Automated Sprint 5.3 Super Macro vendor gate (BUILD_PLAN MIXED)  -  thin wrapper.

.DESCRIPTION
  Runs scripts/pns_adb_preview_validate.ps1 with -SuperMacroOnly: installs APK (unless -SkipInstall),
  launches preview on ultra-wide with pns_preview_super_macro_probe, captures logcat, writes
  super_macro_gate.json / super_macro_gate.txt under -OutDir.

.PARAMETER RequireSuperMacroPass
  Exit non-zero when vendorKeyApplied=true does not appear in PNS.AdbValidation log lines.

.EXAMPLE
  .\scripts\pns_super_macro_gate.ps1 -Serial <serial>
  .\scripts\pns_super_macro_gate.ps1 -UltraWideCameraId 3 -RequireSuperMacroPass
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [string]$OutDir = "",
    [string]$UltraWideCameraId = "3",
    [switch]$RequireSuperMacroPass
)

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot
$forward = @{
    SuperMacroOnly         = $true
    SkipInstall            = $SkipInstall
    UltraWideCameraId      = $UltraWideCameraId
    RequireSuperMacroPass  = $RequireSuperMacroPass
}
if ($Serial) { $forward.Serial = $Serial }
if ($OutDir) { $forward.OutDir = $OutDir }
& (Join-Path $here "pns_adb_preview_validate.ps1") @forward
