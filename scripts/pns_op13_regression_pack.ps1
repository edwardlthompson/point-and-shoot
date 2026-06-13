# Optional legacy device (legacy SKU-class) regression lane — NOT a default Milestone 16 gate.
#
# Chains:
#   1. pns_fleet_matrix_scan.ps1 -LegacyOp13FleetPolicy (matrix with legacy device plugin)
#   2. pns_aux_dng_capture_analyze.ps1 (aux DNG + desktop open gate)
#   3. pns_referenceapp_parity_gate.ps1 (informational unless -RequireProshotParity)
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
. (Join-Path $scriptDir "pns_adb_serial.ps1")

function Test-PnsLegacyOp13Device {
    param(
        [string]$Model = "",
        [string]$AdbDevicesLine = ""
    )
    if ($Model -match '(?i)LegacySku|Legacy\s*device|CPH265[35]|OP5D55L1') { return $true }
    if ($AdbDevicesLine -match '(?i)model:CPH265[35]|model:OP5D55L1|device:OP5D55L1|product:OP5D55L1') {
        return $true
    }
    return $false
}

function Resolve-PnsLegacyDeviceModel {
    param([string]$DeviceSerial)
    if ([string]::IsNullOrWhiteSpace($DeviceSerial)) { return $null }
    $model = (& adb -s $DeviceSerial shell getprop ro.product.model 2>$null).Trim()
    if (Test-PnsLegacyOp13Device -Model $model) { return $model }
    $devicesLine = (& adb devices -l 2>$null) |
        Where-Object { $_ -match "^$([regex]::Escape($DeviceSerial))\s" } |
        Select-Object -First 1
    if ($devicesLine -and (Test-PnsLegacyOp13Device -AdbDevicesLine $devicesLine)) {
        if ($devicesLine -match 'model:(\S+)') { return $Matches[1] }
        if ($devicesLine -match 'device:(\S+)') { return $Matches[1] }
    }
    return $model
}

$legacySerial = Resolve-PnsAdbSerial -Serial $Serial -ScriptRoot $scriptDir -LogPrefix "legacy_pack"
$deviceModel = Resolve-PnsLegacyDeviceModel -DeviceSerial $legacySerial
$isOp13Device = Test-PnsLegacyOp13Device -Model $deviceModel
if ($legacySerial) {
    Write-Host "[legacy_pack] device serial=$legacySerial model=$deviceModel legacyLane=$isOp13Device"
}

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
    $dngParams = @{}
    if ($Serial) { $dngParams.Serial = $Serial }
    if ($SkipInstall.IsPresent) { $dngParams.SkipInstall = $true }
    if ($AssembleDebug.IsPresent) { $dngParams.SkipBuild = $true }

    Write-Host "[legacy_pack] aux DNG capture analyze..."
    & (Join-Path $scriptDir "pns_aux_dng_capture_analyze.ps1") @dngParams
    $dngOk = ($LASTEXITCODE -eq 0)
    Add-Step "aux_dng_capture_analyze" $dngOk "exit=$LASTEXITCODE"

    $parityParams = @{}
    if ($Serial) { $parityParams.Serial = $Serial }
    if ($RequireProshotParity.IsPresent) { $parityParams.RequireProshotParity = $true }

    Write-Host "[legacy_pack] referencecam parity gate (informational)..."
    & (Join-Path $scriptDir "pns_referenceapp_parity_gate.ps1") @parityParams
    $parityOk = ($LASTEXITCODE -eq 0)
    Add-Step "referenceapp_parity_gate" $(if ($parityRequired) { $parityOk } else { $true }) "exit=$LASTEXITCODE required=$parityRequired"
} else {
    Write-Host "[legacy_pack] legacy DNG/ReferenceCam steps skipped (not legacy device): model=$deviceModel"
    Add-Step "aux_dng_capture_analyze" $true "skipped_not_legacy model=$deviceModel"
    Add-Step "referenceapp_parity_gate" $true "skipped_not_legacy model=$deviceModel"
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
