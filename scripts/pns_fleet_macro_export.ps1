# Milestone 18.4 — cross-device macro benchmark CSV from pulled fleet matrices + parity reports.
#
# Reads matrix JSON under hfr-runs/fleet_matrix_* and parity_report.json under parity_sweep_*,
# or explicit -MatrixPath / -ParityPath. Writes macro_benchmark.csv + summary markdown.

param(
    [string]$MatrixPath = "",
    [string]$ParityPath = "",
    [string]$OutDir = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_fleet_macro_export.ps1 — aggregate fleet matrix + parity artifacts to CSV

  -MatrixPath  explicit fleet_device_matrix.json (optional; newest hfr-runs scan if omitted)
  -ParityPath   explicit parity_report.json (optional)
  -OutDir       output folder (default hfr-runs/fleet_macro_export_<utc>)
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $OutDir = Join-Path $projRoot "hfr-runs\fleet_macro_export_$(Get-Date -Format yyyyMMdd_HHmmss)"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Resolve-LatestMatrix {
    if ($MatrixPath -and (Test-Path -LiteralPath $MatrixPath)) { return $MatrixPath }
    $dirs = Get-ChildItem -Path (Join-Path $projRoot "hfr-runs") -Directory -Filter "fleet_matrix_*" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    foreach ($d in $dirs) {
        $candidate = Join-Path $d.FullName "fleet_device_matrix.json"
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

function Resolve-LatestParity {
    if ($ParityPath -and (Test-Path -LiteralPath $ParityPath)) { return $ParityPath }
    $dirs = Get-ChildItem -Path (Join-Path $projRoot "hfr-runs") -Directory -Filter "parity_sweep_*" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending
    foreach ($d in $dirs) {
        $candidate = Join-Path $d.FullName "parity_report.json"
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

$matrixFile = Resolve-LatestMatrix
$parityFile = Resolve-LatestParity

if (-not $matrixFile) {
    Write-Error "No fleet_device_matrix.json found — run pns_fleet_matrix_scan.ps1 first or pass -MatrixPath"
    exit 1
}

$matrix = Get-Content -LiteralPath $matrixFile -Raw | ConvertFrom-Json
$parity = $null
if ($parityFile) {
    $parity = Get-Content -LiteralPath $parityFile -Raw | ConvertFrom-Json
}

$product = $matrix.product
$scan = $matrix.scanMeta
$encoder = $matrix.encoder
$catalogVersion = $matrix.catalogVersion

$rows = @()
$rows += [pscustomobject]@{
    timestampUtc = (Get-Date).ToUniversalTime().ToString("o")
    matrixPath = $matrixFile
    parityPath = $parityFile
    model = $product.model
    device = $product.device
    fingerprintPrefix = $scan.fingerprintSha256Prefix
    scanTier = $scan.scanTier
    catalogVersion = $catalogVersion
    cameraCount = @($matrix.cameras).Count
    parityMode = $parity.mode
    parityPass = $parity.pass
    parityCellCount = $parity.cellCount
    gapAdvertisedNotProven = $parity.gapAdvertisedNotProven
    encoderAv1 = $encoder.supportsAv1
    encoderHevcMain10 = $encoder.supportsMain10
    hfrMax1080 = ($matrix.cameras | ForEach-Object { $_.hfrMaxFpsAt1080 } | Measure-Object -Maximum).Maximum
}

$csvPath = Join-Path $OutDir "macro_benchmark.csv"
$rows | Export-Csv -LiteralPath $csvPath -NoTypeInformation -Encoding utf8

$md = @(
    "# Fleet macro benchmark export",
    "",
    "- **Matrix:** ``$matrixFile``",
    "- **Parity:** ``$(if ($parityFile) { $parityFile } else { 'none' })``",
    "- **CSV:** ``$csvPath``",
    "",
    "| Model | Scan tier | Cameras | Parity pass | Cells |",
    "|-------|-----------|---------|-------------|-------|",
    "| $($rows[0].model) | $($rows[0].scanTier) | $($rows[0].cameraCount) | $($rows[0].parityPass) | $($rows[0].parityCellCount) |",
    ""
)
$mdPath = Join-Path $OutDir "macro_benchmark.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "[fleet_macro_export] Wrote $csvPath"
exit 0
