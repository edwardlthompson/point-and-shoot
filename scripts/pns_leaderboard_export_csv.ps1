param(
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "pns_leaderboard_common.ps1")

if (-not $OutDir) { $OutDir = Join-Path $repoRoot "docs\leaderboard\data" }
$devicesDir = Join-Path $OutDir "devices"
$csvPath = Join-Path $OutDir "leaderboard.csv"

if (-not (Test-Path -LiteralPath $devicesDir)) {
    Write-Warning "No devices dir: $devicesDir"
    exit 0
}

$rows = @()
foreach ($f in Get-ChildItem -LiteralPath $devicesDir -Filter "*.json") {
    try {
        $p = Get-Content -LiteralPath $f.FullName -Raw | ConvertFrom-Json
    } catch { continue }
    $rows += [ordered]@{
        rank = if ($p.scores.total.rank) { $p.scores.total.rank } else { "" }
        slug = $p.slug
        marketingName = $p.identity.marketingName
        manufacturer = $p.identity.manufacturer
        model = $p.identity.model
        parityPts = $p.scores.total.score
        parityPct = $p.scores.total.percent
        honestyPct = $p.disparity.honestyPercent
        resolutionBetrayalIndex = if ($p.resolutionBetrayal) { $p.resolutionBetrayal.index } else { "" }
        fullMpBreakthrough = if ($p.camera2FullMpBreakthrough) { $p.camera2FullMpBreakthrough.proven } else { $false }
        maxProvenCamera2Mp = if ($p.camera2FullMpBreakthrough) { $p.camera2FullMpBreakthrough.maxMpPerSensor } else { "" }
        formatPickerHonestyScore = $p.formatPickerHonestyScore
        sensorSumMm2 = $p.sensors.sensorSumMm2
        testedApi = $p.meta.testedApiLevel
        romFlavor = $p.software.romFlavor
        buildDisplay = $p.software.buildDisplay
        msrpUsd = if ($p.value) { $p.value.msrpUsd } else { $p.identity.msrpUsd }
        parityPerUsd = if ($p.value) { $p.value.parityPerUsd } else { $null }
        shipBlockerCount = if ($p.oemLossSummary) { $p.oemLossSummary.shipBlockerCount } else { 0 }
        trustTier = $p.meta.trustTier
        lastSweepMode = $p.meta.lastSweepMode
        gsmarenaUrl = $p.identity.gsmarenaUrl
    }
}

$headers = @(
    "rank", "slug", "marketingName", "manufacturer", "model", "parityPts", "parityPct", "honestyPct",
    "resolutionBetrayalIndex", "fullMpBreakthrough", "maxProvenCamera2Mp", "formatPickerHonestyScore", "sensorSumMm2", "testedApi", "romFlavor",
    "buildDisplay", "msrpUsd", "parityPerUsd", "shipBlockerCount", "trustTier", "lastSweepMode", "gsmarenaUrl"
)
$lines = @($headers -join ",")
foreach ($r in @($rows | Sort-Object { [int]$_.rank })) {
    $vals = @()
    foreach ($h in $headers) {
        $v = $r.$h
        if ($null -eq $v) { $v = "" }
        $s = [string]$v
        if ($s -match '[,"\r\n]') { $s = '"' + ($s -replace '"', '""') + '"' }
        $vals += $s
    }
    $lines += ($vals -join ",")
}
$lines -join "`n" | Set-Content -LiteralPath $csvPath -Encoding utf8
Write-Host "[leaderboard_csv] rows=$($rows.Count) -> $csvPath"
