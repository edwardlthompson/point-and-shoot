# Optional legacy device (legacy SKU-class) regression lane — NOT a default Milestone 16 gate.
#
# Chains:
#   1. pns_fleet_matrix_scan.ps1 -LegacyOp13FleetPolicy (matrix with legacy device plugin)
#   2. pns_aux_dng_capture_analyze.ps1 (aux DNG + desktop open gate)
#   3. pns_proshot_parity_gate.ps1 (informational unless -RequireProshotParity)
#
# Use primary device CPH2583 for product gates; run this only when validating legacy device plugin / DNG parity.
#
# Artifacts: hfr-runs/legacy_regression_pack_*/

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
    $OutDir = Join-Path $projRoot "hfr-runs\legacy_regression_pack_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$pack = [ordered]@{
    schema       = "pns.legacy_regression_pack.v1"
    pass         = $false
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir       = $OutDir
    steps        = @()
}

function Add-Step([string]$Name, [bool]$Ok, [string]$Detail) {
    $script:pack.steps += [ordered]@{ name = $Name; ok = $Ok; detail = $Detail }
}

if ($HostOnly.IsPresent) {
    Write-Host "[legacy_pack] -HostOnly: wrote stub pack JSON (no USB)"
    $pack.pass = $true
    $pack.skippedReason = "host_only"
    $pack | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "legacy_regression_pack.json") -Encoding utf8
    exit 0
}

$resolveAdb = Join-Path $scriptDir "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }

$legacySerial = $Serial
if ([string]::IsNullOrWhiteSpace($legacySerial)) {
    $envFile = Join-Path $scriptDir "pns_adb_device.env"
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -match '^PNS_ADB_SERIAL=(.+)$') { $legacySerial = $Matches[1].Trim() }
        }
    }
}
$deviceModel = $null
if ($legacySerial) {
    $deviceModel = (& adb -s $legacySerial shell getprop ro.product.model 2>$null).Trim()
}
$isOp13Device = ($deviceModel -match 'LegacySku|Legacy device')

$matrixOut = Join-Path $OutDir "fleet_matrix"
Write-Host "[legacy_pack] fleet matrix (legacy legacy device policy)..."
$scanParams = @{
    OutDir = $matrixOut
    ScanTier = 'quick'
    LegacyOp13FleetPolicy = $true
    SkipInstall = $SkipInstall.IsPresent
}
if ($Serial) { $scanParams.Serial = $Serial }
if ($AssembleDebug.IsPresent) { $scanParams.AssembleDebug = $true }
& (Join-Path $scriptDir "pns_fleet_matrix_scan.ps1") @scanParams
$matrixOk = ($LASTEXITCODE -eq 0)
Add-Step "fleet_matrix_scan" $matrixOk "exit=$LASTEXITCODE dir=$matrixOut"

$dngOk = $true
$parityOk = $true
$parityRequired = $RequireProshotParity.IsPresent
if ($isOp13Device) {
    $dngArgs = @()
    if ($Serial) { $dngArgs += @("-Serial", $Serial) }
    if ($SkipInstall.IsPresent) { $dngArgs += "-SkipInstall" }
    if ($AssembleDebug.IsPresent) { $dngArgs += "-SkipBuild" }

    Write-Host "[legacy_pack] aux DNG capture analyze..."
    & (Join-Path $scriptDir "pns_aux_dng_capture_analyze.ps1") @dngArgs
    $dngOk = ($LASTEXITCODE -eq 0)
    Add-Step "aux_dng_capture_analyze" $dngOk "exit=$LASTEXITCODE"

    $parityArgs = @()
    if ($Serial) { $parityArgs += @("-Serial", $Serial) }
    if ($RequireProshotParity.IsPresent) { $parityArgs += "-RequireProshotParity" }

    Write-Host "[legacy_pack] referencecam parity gate (informational)..."
    & (Join-Path $scriptDir "pns_proshot_parity_gate.ps1") @parityArgs
    $parityOk = ($LASTEXITCODE -eq 0)
    Add-Step "proshot_parity_gate" $(if ($parityRequired) { $parityOk } else { $true }) "exit=$LASTEXITCODE required=$parityRequired"
} else {
    Write-Host "[legacy_pack] legacy DNG/ReferenceCam steps skipped (not legacy device): model=$deviceModel"
    Add-Step "aux_dng_capture_analyze" $true "skipped_not_legacy model=$deviceModel"
    Add-Step "proshot_parity_gate" $true "skipped_not_legacy model=$deviceModel"
}

$pack.pass = $matrixOk -and $dngOk -and ($(if ($parityRequired) { $parityOk } else { $true }))

# M21.6 — optional PiP + Multicam melt when legacy device serial online
if ($isOp13Device) {
    Write-Host "[legacy_pack] M21.6 concurrency smoke (PiP + Multicam melt) on $deviceModel..."
    $usbCommon = @{ SkipInstall = $true }
    if ($legacySerial) { $usbCommon.Serial = $legacySerial }
    & (Join-Path $scriptDir "pns_pip_preview_verify.ps1") @usbCommon
    Add-Step "pip_preview_verify" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
    & (Join-Path $scriptDir "pns_multicam_melt_verify.ps1") @usbCommon
    Add-Step "multicam_melt_verify" ($LASTEXITCODE -eq 0) "exit=$LASTEXITCODE"
    $pack.pass = $pack.pass -and ($pack.steps | Where-Object { $_.name -in @('pip_preview_verify','multicam_melt_verify') } | ForEach-Object { $_.ok } | Where-Object { -not $_ }).Count -eq 0
} else {
    Write-Host "[legacy_pack] M21.6 concurrency skipped (not legacy device): model=$deviceModel"
    Add-Step "pip_preview_verify" $true "skipped_not_legacy model=$deviceModel"
    Add-Step "multicam_melt_verify" $true "skipped_not_legacy model=$deviceModel"
}
$jsonPath = Join-Path $OutDir "legacy_regression_pack.json"
$pack | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host "[legacy_pack] Wrote $jsonPath pass=$($pack.pass)"

if (-not $pack.pass) { exit 1 }
exit 0
