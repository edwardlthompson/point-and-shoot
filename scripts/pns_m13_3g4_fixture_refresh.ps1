<#
.SYNOPSIS
  Sprint 13.3g-4 — ReferenceCam 15/23/73 mm captures → tests/fixtures/referenceapp_legacy_sku → parity gate.

.EXAMPLE
  .\scripts\pns_m13_3g4_fixture_refresh.ps1 -Serial <serial>
  .\scripts\pns_m13_3g4_fixture_refresh.ps1 -SkipForensics -ForensicsDir hfr-runs\referenceapp_live_forensics_* -SkipPnsCapture
#>
param(
    [string]$Serial = "",
    [string]$ForensicsDir = "",
    [switch]$SkipForensics,
    [switch]$SkipPnsCapture,
    [string]$PnsDir = "",
    [int]$PerLensSec = 18
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$fixtureDir = Join-Path $projRoot "tests\fixtures\referenceapp_legacy_sku"

if (-not $SkipForensics) {
    Write-Host "=== 13.3g-4 ReferenceCam live forensics (15/23/73 mm) ===" -ForegroundColor Cyan
    $foreArgs = @(
        "-File", (Join-Path $PSScriptRoot "pns_referenceapp_live_forensics.ps1"),
        "-TryUiAutomation",
        "-PerLensSec", "$PerLensSec"
    )
    if ($Serial) { $foreArgs += @("-Serial", $Serial) }
    & powershell -NoProfile -ExecutionPolicy Bypass @foreArgs
    if ($LASTEXITCODE -ne 0) { throw "pns_referenceapp_live_forensics failed" }
    $latest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "referenceapp_live_forensics_*" |
        Sort-Object Name | Select-Object -Last 1
    if (-not $latest) { throw "No referenceapp_live_forensics_* output" }
    $ForensicsDir = $latest.FullName
} elseif (-not [string]::IsNullOrWhiteSpace($ForensicsDir)) {
    if ($ForensicsDir -match "\*") {
        $ForensicsDir = (Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "referenceapp_live_forensics_*" |
            Sort-Object Name | Select-Object -Last 1).FullName
    } else {
        $ForensicsDir = (Resolve-Path -LiteralPath $ForensicsDir).Path
    }
}

if ([string]::IsNullOrWhiteSpace($ForensicsDir)) { throw "ForensicsDir required when -SkipForensics" }

foreach ($name in @("referenceapp_uw_3.dng", "referenceapp_wide_2.dng", "referenceapp_tele_4.dng")) {
    if (-not (Test-Path (Join-Path $ForensicsDir $name))) {
        throw "Missing $name under $ForensicsDir"
    }
}

Write-Host ""
Write-Host "=== Sync fixtures ===" -ForegroundColor Cyan
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "pns_referenceapp_reference_sync.ps1") `
    -FromForensicsDir $ForensicsDir
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host ""
Write-Host "=== Fixture openability (integrity + ASN; skip wide-cal leak on ReferenceCam refs) ===" -ForegroundColor Cyan
$uw = Join-Path $fixtureDir "referenceapp_uw_cam3.dng"
$wide = Join-Path $fixtureDir "referenceapp_wide_cam2.dng"
$tele = Join-Path $fixtureDir "referenceapp_tele_cam4.dng"
# ReferenceCam on legacy SKU may ship identical CM2[0,0] on UW+wide (1.4337); leak check targets P&S wide-cal patch only.
& python (Join-Path $PSScriptRoot "dng_desktop_open_gate.py") --skip-wide-cal-leak $uw $wide $tele
if ($LASTEXITCODE -ne 0) {
    Write-Host "FAIL: fixture open gate (integrity / ASN)" -ForegroundColor Red
    exit 1
}

if (-not $SkipPnsCapture) {
    Write-Host ""
    Write-Host "=== P&S capture + RequireProshotParity ===" -ForegroundColor Cyan
    $parityArgs = @("-File", (Join-Path $PSScriptRoot "pns_referenceapp_parity_gate.ps1"))
    if ($Serial) { $parityArgs += @("-Serial", $Serial) }
    & powershell -NoProfile -ExecutionPolicy Bypass @parityArgs
    exit $LASTEXITCODE
}

if ([string]::IsNullOrWhiteSpace($PnsDir)) {
    $PnsDir = (Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "aux_dng_capture_analyze_*" |
        Sort-Object Name | Select-Object -Last 1).FullName
}
if (-not $PnsDir) { throw "No PnsDir; pass -PnsDir or run without -SkipPnsCapture" }

Write-Host ""
Write-Host "=== Parity gate (existing P&S captures) ===" -ForegroundColor Cyan
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "pns_referenceapp_parity_gate.ps1") `
    -SkipCapture -PnsDir $PnsDir
exit $LASTEXITCODE
