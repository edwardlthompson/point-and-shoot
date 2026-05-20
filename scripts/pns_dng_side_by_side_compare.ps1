<#
.SYNOPSIS
  Build side-by-side ProShot vs P&S DNG comparison report (by physical camera id).

.EXAMPLE
  .\scripts\pns_dng_side_by_side_compare.ps1 `
    -ProShotDir hfr-runs\proshot_reference_20260518_025813 `
    -PnsDir hfr-runs\aux_dng_capture_analyze_20260518_025101
#>
param(
    [string]$ProShotDir = "",
    [string]$PnsDir = "",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$pyTag = Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) "dng_tag_report.py"

function Find-LatestDir([string]$root, [string]$pattern) {
    Get-ChildItem (Join-Path $root "hfr-runs") -Directory -Filter $pattern |
        Sort-Object Name | Select-Object -Last 1
}

if ([string]::IsNullOrWhiteSpace($ProShotDir)) {
    $d = Find-LatestDir $projRoot "proshot_reference_*"
    if ($d) { $ProShotDir = $d.FullName }
}
if ([string]::IsNullOrWhiteSpace($PnsDir)) {
    $d = Find-LatestDir $projRoot "aux_dng_capture_analyze_*"
    if ($d) { $PnsDir = $d.FullName }
}

if (-not (Test-Path $ProShotDir)) { throw "ProShotDir not found: $ProShotDir" }
if (-not (Test-Path $PnsDir)) { throw "PnsDir not found: $PnsDir" }

# ProShot order from dumpsys: shot1=cam3, shot2=cam2, shot3=cam4 (user UW/wide/tele session 22:57)
$proshotByCam = @{
    "2" = Join-Path $ProShotDir "proshot_02.dng"
    "3" = Join-Path $ProShotDir "proshot_01.dng"
    "4" = Join-Path $ProShotDir "proshot_03.dng"
}

# P&S from manifest physicalId or filenames
$pnsManifest = Join-Path $PnsDir "manifest.json"
$pnsByCam = @{}
if (Test-Path $pnsManifest) {
    $m = Get-Content $pnsManifest -Raw | ConvertFrom-Json
    foreach ($c in $m.captures) {
        if ($c.physicalId -and $c.path) { $pnsByCam[$c.physicalId] = $c.path }
    }
}
if (-not $pnsByCam["2"]) { $pnsByCam["2"] = Join-Path $PnsDir "M23_wide.dng" }
if (-not $pnsByCam["3"]) { $pnsByCam["3"] = Join-Path $PnsDir "M14_uw.dng" }
if (-not $pnsByCam["4"]) { $pnsByCam["4"] = Join-Path $PnsDir "M73_tele.dng" }

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\dng_side_by_side_$ts"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$lines = [System.Collections.Generic.List[string]]::new()
$lines.Add("# DNG side-by-side: ProShot vs Point and Shoot")
$lines.Add("")
$lines.Add("| HAL cam | Lens | ProShot | P&S |")
$lines.Add("|---------|------|---------|-----|")
$labels = @{ "2" = "wide (M23)"; "3" = "UW (M14)"; "4" = "tele (M73)" }

foreach ($cam in @("3", "2", "4")) {
    $ps = $proshotByCam[$cam]
    $pn = $pnsByCam[$cam]
    $psLeaf = if ($ps -and (Test-Path $ps)) { Split-Path $ps -Leaf } else { "(missing)" }
    $pnLeaf = if ($pn -and (Test-Path $pn)) { Split-Path $pn -Leaf } else { "(missing)" }
    $lines.Add("| $cam | $($labels[$cam]) | ``$psLeaf`` | ``$pnLeaf`` |")
}

$lines.Add("")
$lines.Add("## Tag summary (physical camera order 3, 2, 4)")
$lines.Add("")
$lines.Add("### ProShot")
$lines.Add("``````")
if (Test-Path $pyTag) {
    $files = @("3", "2", "4") | ForEach-Object { $proshotByCam[$_] } | Where-Object { Test-Path $_ }
    & python $pyTag @($files) | ForEach-Object { $lines.Add($_) }
}
$lines.Add("``````")
$lines.Add("")
$lines.Add("### Point and Shoot")
$lines.Add("``````")
if (Test-Path $pyTag) {
    $files = @("3", "2", "4") | ForEach-Object { $pnsByCam[$_] } | Where-Object { Test-Path $_ }
    & python $pyTag @($files) | ForEach-Object { $lines.Add($_) }
}
$lines.Add("``````")
$lines.Add("")
$lines.Add("## Environment")
$lines.Add("- Low light / dark room: exposure and WB differ run-to-run; structural FM/WB gates are not color-calibration truth.")
$lines.Add("- P&S captures should use **Auto dial** (not Highlight/H) for metering parity with typical ProShot RAW.")
$lines.Add("")
$lines.Add("## Takeaways")
$lines.Add("- ProShot order: ``proshot_01`` = oldest of pull trio, ``03`` = newest (expect UW → wide → tele if shot in that order).")
$lines.Add("- Paired by HAL cam 3 / 2 / 4 (UW / wide / tele).")
$lines.Add("- Both apps often show FM1[0,0]=0.4375 in simple IFD0 parse; color difference is usually not TIFF FM rewrite.")
$lines.Add("- Compare visually in darktable/ACR using files copied to this folder.")

foreach ($cam in @("3", "2", "4")) {
    $ps = $proshotByCam[$cam]
    $pn = $pnsByCam[$cam]
    if ($ps -and (Test-Path $ps)) {
        Copy-Item $ps (Join-Path $OutDir "proshot_cam${cam}.dng") -Force
    }
    if ($pn -and (Test-Path $pn)) {
        Copy-Item $pn (Join-Path $OutDir "pns_cam${cam}.dng") -Force
    }
}

$report = Join-Path $OutDir "side_by_side_report.md"
$lines | Out-File -Encoding utf8 $report
Write-Host "[side_by_side] $report"
Write-Host "[side_by_side] copied DNGs -> $OutDir"
