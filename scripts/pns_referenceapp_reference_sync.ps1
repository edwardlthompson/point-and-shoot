<#
.SYNOPSIS
  Refresh tests/fixtures/referenceapp_legacy_sku from lens-matched ReferenceCam DNGs.

.DESCRIPTION
  Preferred (13.3g-4): pass -FromForensicsDir from pns_referenceapp_live_forensics_* (files
  referenceapp_uw_3.dng, referenceapp_wide_2.dng, referenceapp_tele_4.dng at 15/23/73 mm).

  Fallback: newest 3 non-P&S DCIM DNGs via pns_referenceapp_dng_reference_pull (order not guaranteed).

.EXAMPLE
  .\scripts\pns_referenceapp_reference_sync.ps1 -FromForensicsDir hfr-runs\referenceapp_live_forensics_20260520_120000
  .\scripts\pns_referenceapp_reference_sync.ps1 -Serial <serial>
  .\scripts\pns_referenceapp_reference_sync.ps1 -FromForensicsDir hfr-runs\referenceapp_live_forensics_* -FixtureProfile Cph2655 -Serial 8bf09993
#>
param(
    [string]$Serial = "",
    [string]$FromForensicsDir = "",
    [ValidateSet("LegacySku", "Cph2655")]
    [string]$FixtureProfile = "LegacySku"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$fixtureDir = if ($FixtureProfile -eq "Cph2655") {
    Join-Path $projRoot "tests\fixtures\referenceapp_cph2655"
} else {
    Join-Path $projRoot "tests\fixtures\referenceapp_legacy_sku"
}
New-Item -ItemType Directory -Force -Path $fixtureDir | Out-Null

function Write-Cph2655Manifest {
    param(
        [string]$FixtureRoot,
        [string]$ForensicsDir,
        [string]$DeviceSerial
    )
    $model = "CPH2655"
    if ($DeviceSerial) {
        $m = (& adb -s $DeviceSerial shell getprop ro.product.model 2>$null).Trim()
        if ($m) { $model = $m }
    }
    $timeline = Join-Path $ForensicsDir "session_timeline.txt"
    $remotes = @{ uw = ""; wide = ""; tele = "" }
    if (Test-Path -LiteralPath $timeline) {
        foreach ($line in Get-Content -LiteralPath $timeline) {
            if ($line -match '^dng=(/sdcard/[^\s]+)\s+local=.*referenceapp_uw_') { $remotes.uw = $Matches[1] }
            if ($line -match '^dng=(/sdcard/[^\s]+)\s+local=.*referenceapp_wide_') { $remotes.wide = $Matches[1] }
            if ($line -match '^dng=(/sdcard/[^\s]+)\s+local=.*referenceapp_tele_') { $remotes.tele = $Matches[1] }
        }
    }
    $manifest = [ordered]@{
        device            = $model
        serial            = $DeviceSerial
        capturedUtcApprox = [DateTime]::UtcNow.ToString("yyyy-MM-dd'T'HH:mm'Z'")
        shotOrder         = @("ultrawide", "wide", "tele")
        files             = [ordered]@{
            uw   = [ordered]@{ path = "referenceapp_uw_cam3.dng"; halCameraId = "3"; focalSlotMm = 14; remote = $remotes.uw }
            wide = [ordered]@{ path = "referenceapp_wide_cam2.dng"; halCameraId = "2"; focalSlotMm = 23; remote = $remotes.wide }
            tele = [ordered]@{ path = "referenceapp_tele_cam4.dng"; halCameraId = "4"; focalSlotMm = 73; remote = $remotes.tele }
        }
        usage             = "Gate P&S aux DNG captures with scripts/dng_referenceapp_parity_gate.py or scripts/pns_referenceapp_parity_gate.ps1"
        syncSource        = $ForensicsDir
    }
    $manifest | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $FixtureRoot "manifest.json") -Encoding UTF8
}

$manifest = @{
    schema = "referenceapp_fixture_sync.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    fixtureDir = $fixtureDir
    source = ""
    slots = @()
}

if (-not [string]::IsNullOrWhiteSpace($FromForensicsDir)) {
    $src = (Resolve-Path -LiteralPath $FromForensicsDir).Path
    $map = @(
        @{ slot = "uw"; cam = "3"; src = "referenceapp_uw_3.dng"; dst = "referenceapp_uw_cam3.dng" }
        @{ slot = "wide"; cam = "2"; src = "referenceapp_wide_2.dng"; dst = "referenceapp_wide_cam2.dng" }
        @{ slot = "tele"; cam = "4"; src = "referenceapp_tele_4.dng"; dst = "referenceapp_tele_cam4.dng" }
    )
    foreach ($m in $map) {
        $from = Join-Path $src $m.src
        if (-not (Test-Path -LiteralPath $from)) {
            throw "Missing lens file $from (run pns_referenceapp_live_forensics.ps1 -TryUiAutomation first)"
        }
        $to = Join-Path $fixtureDir $m.dst
        Copy-Item -LiteralPath $from -Destination $to -Force
        $manifest.slots += @{ slot = $m.slot; cameraId = $m.cam; file = $m.dst; sourcePath = $from }
    }
    $manifest.source = $src
    $manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $fixtureDir "fixture_sync_manifest.json") -Encoding UTF8
    if ($FixtureProfile -eq "Cph2655") {
        Write-Cph2655Manifest -FixtureRoot $fixtureDir -ForensicsDir $src -DeviceSerial $Serial
    }
    Write-Host "[referenceapp_sync] updated $fixtureDir from forensics $src" -ForegroundColor Green
    exit 0
}

$pullArgs = @("-PnsCompareDir", "__skip__")
if ($Serial) { $pullArgs += @("-Serial", $Serial) }
& (Join-Path $PSScriptRoot "pns_referenceapp_dng_reference_pull.ps1") @pullArgs | Out-Host
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$latest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "referenceapp_reference_*" |
    Sort-Object Name | Select-Object -Last 1
if (-not $latest) { throw "No referenceapp_reference_* folder" }

Copy-Item (Join-Path $latest.FullName "referenceapp_01.dng") (Join-Path $fixtureDir "referenceapp_uw_cam3.dng") -Force
Copy-Item (Join-Path $latest.FullName "referenceapp_02.dng") (Join-Path $fixtureDir "referenceapp_wide_cam2.dng") -Force
Copy-Item (Join-Path $latest.FullName "referenceapp_03.dng") (Join-Path $fixtureDir "referenceapp_tele_cam4.dng") -Force

$manifest.source = $latest.FullName
$manifest.mode = "dcim_pull_order_fallback"
$manifest.warning = "Not lens-matched; prefer -FromForensicsDir after live forensics"
$manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $fixtureDir "fixture_sync_manifest.json") -Encoding UTF8

Write-Host "[referenceapp_sync] updated $fixtureDir (DCIM pull fallback)" -ForegroundColor Yellow
Write-Host "[referenceapp_sync] source pull: $($latest.FullName)"
