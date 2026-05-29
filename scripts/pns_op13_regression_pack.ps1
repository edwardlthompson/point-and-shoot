# Optional OP13 (CPH2655-class) regression lane — NOT a default Milestone 16 gate.
#
# Chains:
#   1. pns_fleet_matrix_scan.ps1 -LegacyOp13FleetPolicy (matrix with OP13 plugin)
#   2. pns_aux_dng_capture_analyze.ps1 (aux DNG + desktop open gate)
#   3. pns_proshot_parity_gate.ps1 (informational unless -RequireProshotParity)
#
# Use primary device CPH2583 for product gates; run this only when validating OP13 plugin / DNG parity.
#
# Artifacts: hfr-runs/op13_regression_pack_*/

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$RequireProshotParity,
    [switch]$HostOnly
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$scriptDir = $PSScriptRoot

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\op13_regression_pack_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$pack = [ordered]@{
    schema       = "pns.op13_regression_pack.v1"
    pass         = $false
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir       = $OutDir
    steps        = @()
}

function Add-Step([string]$Name, [bool]$Ok, [string]$Detail) {
    $script:pack.steps += [ordered]@{ name = $Name; ok = $Ok; detail = $Detail }
}

if ($HostOnly.IsPresent) {
    Write-Host "[op13_pack] -HostOnly: wrote stub pack JSON (no USB)"
    $pack.pass = $true
    $pack.skippedReason = "host_only"
    $pack | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "op13_regression_pack.json") -Encoding utf8
    exit 0
}

$matrixOut = Join-Path $OutDir "fleet_matrix"
$matrixArgs = @("-OutDir", $matrixOut, "-ScanTier", "quick", "-LegacyOp13FleetPolicy")
if ($Serial) { $matrixArgs += @("-Serial", $Serial) }
if ($SkipInstall.IsPresent) { $matrixArgs += "-SkipInstall" }
if ($AssembleDebug.IsPresent) { $matrixArgs += "-AssembleDebug" }

Write-Host "[op13_pack] fleet matrix (legacy OP13 policy)..."
& (Join-Path $scriptDir "pns_fleet_matrix_scan.ps1") @matrixArgs
$matrixOk = ($LASTEXITCODE -eq 0)
Add-Step "fleet_matrix_scan" $matrixOk "exit=$LASTEXITCODE dir=$matrixOut"

$dngArgs = @()
if ($Serial) { $dngArgs += @("-Serial", $Serial) }
if ($SkipInstall.IsPresent) { $dngArgs += "-SkipInstall" }
if ($AssembleDebug.IsPresent) { $dngArgs += "-SkipBuild" }

Write-Host "[op13_pack] aux DNG capture analyze..."
& (Join-Path $scriptDir "pns_aux_dng_capture_analyze.ps1") @dngArgs
$dngOk = ($LASTEXITCODE -eq 0)
Add-Step "aux_dng_capture_analyze" $dngOk "exit=$LASTEXITCODE"

$parityArgs = @()
if ($Serial) { $parityArgs += @("-Serial", $Serial) }
if ($RequireProshotParity.IsPresent) { $parityArgs += "-RequireProshotParity" }

Write-Host "[op13_pack] proshot parity gate (informational)..."
& (Join-Path $scriptDir "pns_proshot_parity_gate.ps1") @parityArgs
$parityOk = ($LASTEXITCODE -eq 0)
$parityRequired = $RequireProshotParity.IsPresent
Add-Step "proshot_parity_gate" $(if ($parityRequired) { $parityOk } else { $true }) "exit=$LASTEXITCODE required=$parityRequired"

$pack.pass = $matrixOk -and $dngOk -and ($(if ($parityRequired) { $parityOk } else { $true }))
$jsonPath = Join-Path $OutDir "op13_regression_pack.json"
$pack | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host "[op13_pack] Wrote $jsonPath pass=$($pack.pass)"

if (-not $pack.pass) { exit 1 }
exit 0
