<#
.SYNOPSIS
  Host gate: dng_desktop_open_gate.py (Sprint 13.3g) — integrity + ASN + wide-cal leak.

.EXAMPLE
  .\scripts\pns_dng_desktop_open_gate.ps1 hfr-runs\aux_dng_capture_analyze_*\M14_uw.dng ...
  .\scripts\pns_dng_desktop_open_gate.ps1 -Dir hfr-runs\aux_dng_capture_analyze_20260519_155213
#>
param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$DngPaths = @(),
    [string]$Dir = ""
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$py = Join-Path $PSScriptRoot "dng_desktop_open_gate.py"

if ($Dir) {
    $d = Resolve-Path -LiteralPath $Dir
    $uw = Join-Path $d "M14_uw.dng"
    $wide = Join-Path $d "M23_wide.dng"
    $tele = Join-Path $d "M73_tele.dng"
    if (-not (Test-Path -LiteralPath $uw)) { throw "Missing $uw" }
    & python $py $uw $wide $(if (Test-Path -LiteralPath $tele) { $tele })
} elseif ($DngPaths.Count -ge 1) {
    & python $py @DngPaths
} else {
    Write-Host "usage: -Dir <capture_analyze_folder> OR pass .dng paths" -ForegroundColor Yellow
    exit 2
}
exit $LASTEXITCODE
