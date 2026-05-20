<#
.SYNOPSIS
  Refresh tests/fixtures/proshot_cph2655 from lens-matched ProShot DNGs.

.DESCRIPTION
  Preferred (13.3g-4): pass -FromForensicsDir from pns_proshot_live_forensics_* (files
  proshot_uw_3.dng, proshot_wide_2.dng, proshot_tele_4.dng at 15/23/73 mm).

  Fallback: newest 3 non-P&S DCIM DNGs via pns_proshot_dng_reference_pull (order not guaranteed).

.EXAMPLE
  .\scripts\pns_proshot_reference_sync.ps1 -FromForensicsDir hfr-runs\proshot_live_forensics_20260520_120000
  .\scripts\pns_proshot_reference_sync.ps1 -Serial 8bf09993
#>
param(
    [string]$Serial = "",
    [string]$FromForensicsDir = ""
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$fixtureDir = Join-Path $projRoot "tests\fixtures\proshot_cph2655"
New-Item -ItemType Directory -Force -Path $fixtureDir | Out-Null

$manifest = @{
    schema = "proshot_fixture_sync.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    fixtureDir = $fixtureDir
    source = ""
    slots = @()
}

if (-not [string]::IsNullOrWhiteSpace($FromForensicsDir)) {
    $src = (Resolve-Path -LiteralPath $FromForensicsDir).Path
    $map = @(
        @{ slot = "uw"; cam = "3"; src = "proshot_uw_3.dng"; dst = "proshot_uw_cam3.dng" }
        @{ slot = "wide"; cam = "2"; src = "proshot_wide_2.dng"; dst = "proshot_wide_cam2.dng" }
        @{ slot = "tele"; cam = "4"; src = "proshot_tele_4.dng"; dst = "proshot_tele_cam4.dng" }
    )
    foreach ($m in $map) {
        $from = Join-Path $src $m.src
        if (-not (Test-Path -LiteralPath $from)) {
            throw "Missing lens file $from (run pns_proshot_live_forensics.ps1 -TryUiAutomation first)"
        }
        $to = Join-Path $fixtureDir $m.dst
        Copy-Item -LiteralPath $from -Destination $to -Force
        $manifest.slots += @{ slot = $m.slot; cameraId = $m.cam; file = $m.dst; sourcePath = $from }
    }
    $manifest.source = $src
    $manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $fixtureDir "fixture_sync_manifest.json") -Encoding UTF8
    Write-Host "[proshot_sync] updated $fixtureDir from forensics $src" -ForegroundColor Green
    exit 0
}

$pullArgs = @("-PnsCompareDir", "__skip__")
if ($Serial) { $pullArgs += @("-Serial", $Serial) }
& (Join-Path $PSScriptRoot "pns_proshot_dng_reference_pull.ps1") @pullArgs | Out-Host
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$latest = Get-ChildItem (Join-Path $projRoot "hfr-runs") -Directory -Filter "proshot_reference_*" |
    Sort-Object Name | Select-Object -Last 1
if (-not $latest) { throw "No proshot_reference_* folder" }

Copy-Item (Join-Path $latest.FullName "proshot_01.dng") (Join-Path $fixtureDir "proshot_uw_cam3.dng") -Force
Copy-Item (Join-Path $latest.FullName "proshot_02.dng") (Join-Path $fixtureDir "proshot_wide_cam2.dng") -Force
Copy-Item (Join-Path $latest.FullName "proshot_03.dng") (Join-Path $fixtureDir "proshot_tele_cam4.dng") -Force

$manifest.source = $latest.FullName
$manifest.mode = "dcim_pull_order_fallback"
$manifest.warning = "Not lens-matched; prefer -FromForensicsDir after live forensics"
$manifest | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $fixtureDir "fixture_sync_manifest.json") -Encoding UTF8

Write-Host "[proshot_sync] updated $fixtureDir (DCIM pull fallback)" -ForegroundColor Yellow
Write-Host "[proshot_sync] source pull: $($latest.FullName)"
