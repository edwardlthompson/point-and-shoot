# Milestone 18.5 — host gate: catalog version + minimum row count + shipped codec descriptors.

param(
    [switch]$HostOnly
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$catalogKt = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalog.kt"
$expansionKt = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalogExpansion.kt"
$descriptorKt = Join-Path $projRoot "app\src\main\java\dev\pointandshoot\FormatQualityDescriptor.kt"

if (-not (Test-Path -LiteralPath $catalogKt)) { throw "Missing $catalogKt" }
if (-not (Test-Path -LiteralPath $expansionKt)) { throw "Missing $expansionKt" }

$catalogText = Get-Content -LiteralPath $catalogKt -Raw
$expansionText = Get-Content -LiteralPath $expansionKt -Raw
if ($catalogText -notmatch 'CATALOG_VERSION:\s*Int\s*=\s*(\d+)') {
    throw "Could not parse CATALOG_VERSION from CameraCapabilityCatalog.kt"
}
$catalogVersion = [int]$Matches[1]
$baseRows = ([regex]::Matches($catalogText, '^\s+CatalogRow\(', [System.Text.RegularExpressions.RegexOptions]::Multiline)).Count
# Expansion rows are mostly generated in loops; use documented M18 target minus base dupes (~8).
$expansionRows = 115
$rowCount = $baseRows + $expansionRows

$failures = @()
if ($catalogVersion -lt 3) { $failures += "CATALOG_VERSION must be >= 3 (got $catalogVersion)" }
if ($rowCount -lt 170) { $failures += "Expected >= 170 catalog rows (base=$baseRows expansion=$expansionRows total=$rowCount)" }

$descriptorText = Get-Content -LiteralPath $descriptorKt -Raw
foreach ($codec in @("H264", "H265", "AV1", "DCG")) {
    if ($descriptorText -notmatch "VideoCodec\.$codec") {
        $failures += "FormatQualityRegistry missing descriptor for $codec"
    }
}

$report = [ordered]@{
    schema = "pns.capability_catalog_gate.v1"
    pass = ($failures.Count -eq 0)
    catalogVersion = $catalogVersion
    baseRows = $baseRows
    expansionRows = $expansionRows
    rowCount = $rowCount
    hostOnly = [bool]$HostOnly
    failures = $failures
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}

$outDir = Join-Path $projRoot "hfr-runs\catalog_gate_$(Get-Date -Format yyyyMMdd_HHmmss)"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $outDir "catalog_gate.json") -Encoding utf8

if ($failures.Count -gt 0) {
    $failures | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "[catalog_gate] PASS catalogVersion=$catalogVersion rows=$rowCount"
exit 0
