<#
.SYNOPSIS
  Capture P&S M14/M23/M73 RAW stills, pull DNGs, enforce ReferenceCam parity (integrity + color/luminance).

.EXAMPLE
  .\scripts\pns_referenceapp_parity_gate.ps1 -Serial <serial>
  .\scripts\pns_referenceapp_parity_gate.ps1 -SkipCapture -PnsDir hfr-runs\aux_dng_capture_analyze_*
#>
param(
    [string]$Serial = "",
    [switch]$SkipCapture,
    [string]$PnsDir = "",
    [string]$ReferenceAppFixtureDir = "",
    [double]$MaxGreenDelta = 0.08,
    [double]$MaxLumDelta = 0.12
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "pns_resolve_referenceapp_fixture_dir.ps1")
$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\referenceapp_parity_gate_$ts"

if (-not $SkipCapture) {
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null
    $capParams = @{
        PreviewDial = "A"
        WaitSec     = 62
        OutDir      = $outDir
    }
    if ($Serial) { $capParams["Serial"] = $Serial }
    $capParams["RequireProshotParity"] = $true
    & (Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1") @capParams
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    $PnsDir = $outDir
} elseif ([string]::IsNullOrWhiteSpace($PnsDir)) {
    $d = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory |
        Where-Object { $_.Name -match "aux_dng_capture_analyze_|referenceapp_parity_gate_|dng_matrix_bisect_" } |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $d) { throw "No capture dir; pass -PnsDir" }
    $PnsDir = $d.FullName
}

$refDir = if ($ReferenceAppFixtureDir) {
    $ReferenceAppFixtureDir
} else {
    Resolve-PnsReferenceAppFixtureDir -ProjectRoot $projRoot -RequireExists
}

$py = Join-Path $PSScriptRoot "dng_referenceapp_parity_gate.py"
$jsonOut = Join-Path $PnsDir "referenceapp_parity_gate.json"
& python $py $PnsDir --referencecam-dir $refDir `
    --max-green-delta $MaxGreenDelta --max-lum-delta $MaxLumDelta `
    --json-out $jsonOut
$exit = $LASTEXITCODE

if ($Serial) { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null }
else { & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null }

if ($exit -ne 0) {
    Write-Host "FAIL: ReferenceCam parity gate (see $jsonOut)" -ForegroundColor Red
    exit 1
}
Write-Host "PASS: ReferenceCam parity gate" -ForegroundColor Green
exit 0
