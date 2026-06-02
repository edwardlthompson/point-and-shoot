# Milestone 22 — run parity proof manifest scripts and emit parity_proof_results.json.
#
# Used by pns_fleet_parity_sweep.ps1 -IncludeProofPack (Full mode).

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [string]$ManifestPath = "",
    [string]$MatrixJsonPath = "",
    [string]$DeliveryMismatchPath = "",
    [switch]$SkipInstall,
    [switch]$HostOnly,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_parity_proof_pack.ps1 — M22 proof pack runner

  -OutDir           artifact folder (required unless -HostOnly)
  -ManifestPath      defaults to scripts/parity_proof_manifest.json
  -MatrixJsonPath    fleet matrix for matrixGate skips
  -DeliveryMismatchPath  delivery_mismatch.json for video.delivery_honesty
  -SkipInstall       pass through to child verify scripts
  -HostOnly          validate manifest schema only (no device)
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
if (-not $ManifestPath) { $ManifestPath = Join-Path $PSScriptRoot "parity_proof_manifest.json" }
if (-not (Test-Path -LiteralPath $ManifestPath)) { throw "Missing manifest $ManifestPath" }

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
if ($manifest.schema -ne "parity_proof_manifest.v1") { throw "Unexpected manifest schema $($manifest.schema)" }

if ($HostOnly) {
    $count = @($manifest.rows).Count
    $ids = @()
    foreach ($row in @($manifest.rows)) {
        $ids += @($row.catalogId)
        if ($row.alsoProves) { $ids += @($row.alsoProves) }
    }
    $idCount = @($ids | Sort-Object -Unique).Count
    Write-Host "[proof_pack] HostOnly manifest rows=$count ids=$idCount schema=ok"
    if ($idCount -lt 20) { exit 1 }
    exit 0
}

$resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }

function Read-PnsSerial {
    param([string]$S)
    if ($S) { return $S }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            if ($line -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim().Trim('"') }
        }
    }
    return ""
}

$Serial = Read-PnsSerial $Serial
if (-not $OutDir) {
    $OutDir = Join-Path $projRoot "hfr-runs\parity_proof_pack_$(Get-Date -Format yyyyMMdd_HHmmss)"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$matrixObj = $null
if ($MatrixJsonPath -and (Test-Path -LiteralPath $MatrixJsonPath)) {
    $matrixObj = Get-Content -LiteralPath $MatrixJsonPath -Raw | ConvertFrom-Json
}

function Test-MatrixGate([string]$GatePath) {
    if (-not $GatePath -or -not $matrixObj) { return $true }
    if ($GatePath.StartsWith("cameraAny.")) {
        $sub = $GatePath.Substring("cameraAny.".Length)
        foreach ($cam in @($matrixObj.cameras)) {
            $parts = $sub -split '\.'
            $cur = $cam
            foreach ($p in $parts) {
                if ($null -eq $cur) { $cur = $null; break }
                if ($cur -is [System.Collections.IList]) {
                    if ($p -match '^\d+$') {
                        $idx = [int]$p
                        if ($idx -lt $cur.Count) {
                            $cur = $cur[$idx]
                        } else {
                            $cur = $null
                            break
                        }
                    } else {
                        $cur = $null
                        break
                    }
                } elseif ($cur.PSObject.Properties.Name -contains $p) {
                    $cur = $cur.$p
                } else {
                    $cur = $null
                    break
                }
            }
            if ($cur -eq $true -or $cur -eq "true") { return $true }
            if ($cur -is [int] -and $cur -gt 0) { return $true }
        }
        return $false
    }
    $parts = $GatePath -split '\.'
    $cur = $matrixObj
    foreach ($p in $parts) {
        if ($null -eq $cur) { return $false }
        if ($cur.PSObject.Properties.Name -contains $p) {
            $cur = $cur.$p
        } else {
            return $false
        }
    }
    if ($cur -eq $true -or $cur -eq "true") { return $true }
    if ($cur -is [int] -and $cur -gt 0) { return $true }
    return $false
}

$deliveryObj = $null
if ($DeliveryMismatchPath -and (Test-Path -LiteralPath $DeliveryMismatchPath)) {
    $deliveryObj = Get-Content -LiteralPath $DeliveryMismatchPath -Raw | ConvertFrom-Json
}

$scriptCache = @{}
$results = @()
$seenIds = @{}

foreach ($row in @($manifest.rows)) {
    $ids = @($row.catalogId)
    if ($row.alsoProves) { $ids += @($row.alsoProves) }

    if ($row.source -eq "delivery_mismatch") {
        $pass = $false
        $reason = "delivery_mismatch_missing"
        if ($deliveryObj -and $deliveryObj.probe) {
            $pass = ($deliveryObj.probe.matchOk -eq $true)
            $reason = if ($pass) { $null } else { $deliveryObj.probe.mismatchReason }
        }
        foreach ($id in $ids) {
            if ($seenIds.ContainsKey($id)) { continue }
            $seenIds[$id] = $true
            $results += [ordered]@{
                catalogId = $id
                pass = [bool]$pass
                source = "delivery_mismatch"
                skippedReason = if (-not $DeliveryMismatchPath) { "requires_IncludeRecord" } else { $null }
                failReason = $reason
                timestampUtc = [DateTime]::UtcNow.ToString("o")
            }
        }
        continue
    }

    if ($row.matrixGate -and -not (Test-MatrixGate $row.matrixGate)) {
        foreach ($id in $ids) {
            if ($seenIds.ContainsKey($id)) { continue }
            $seenIds[$id] = $true
            $results += [ordered]@{
                catalogId = $id
                pass = $true
                skippedReason = "matrix_gate:$($row.matrixGate)"
                script = $row.script
                timestampUtc = [DateTime]::UtcNow.ToString("o")
            }
        }
        continue
    }

    $scriptName = $row.script
    if (-not $scriptName) { continue }
    $scriptPath = Join-Path $PSScriptRoot $scriptName
    if (-not (Test-Path -LiteralPath $scriptPath)) {
        foreach ($id in $ids) {
            if ($seenIds.ContainsKey($id)) { continue }
            $seenIds[$id] = $true
            $results += [ordered]@{
                catalogId = $id
                pass = $false
                failReason = "script_missing:$scriptName"
                script = $scriptName
                timestampUtc = [DateTime]::UtcNow.ToString("o")
            }
        }
        continue
    }

    $cacheKey = $scriptName + "|" + ($row.scriptArgs | ConvertTo-Json -Compress)
    if (-not $scriptCache.ContainsKey($cacheKey)) {
        Write-Host "[proof_pack] running $scriptName ..."
        $childArgs = @{ OutDir = (Join-Path $OutDir ("proof_" + ($scriptName -replace '\.ps1$',''))) }
        if ($Serial) { $childArgs.Serial = $Serial }
        if ($SkipInstall) { $childArgs.SkipInstall = $true; $childArgs.SkipAssemble = $true }
        if ($row.scriptArgs) {
            $row.scriptArgs.PSObject.Properties | ForEach-Object { $childArgs[$_.Name] = $_.Value }
        }
        & $scriptPath @childArgs
        $scriptCache[$cacheKey] = [ordered]@{
            exitCode = $LASTEXITCODE
            pass = ($LASTEXITCODE -eq 0)
        }
    }
    $run = $scriptCache[$cacheKey]
    foreach ($id in $ids) {
        if ($seenIds.ContainsKey($id)) { continue }
        $seenIds[$id] = $true
        $results += [ordered]@{
            catalogId = $id
            pass = [bool]$run.pass
            script = $scriptName
            exitCode = $run.exitCode
            timestampUtc = [DateTime]::UtcNow.ToString("o")
        }
    }
}

$passCount = @($results | Where-Object { $_.pass -eq $true }).Count
$skipCount = @($results | Where-Object { $_.skippedReason }).Count
$report = [ordered]@{
    schema = "parity_proof_results.v1"
    pass = ($passCount + $skipCount) -ge @($results).Count
    manifestPath = $ManifestPath
    rowCount = @($results).Count
    passCount = $passCount
    skipCount = $skipCount
    failCount = @($results | Where-Object { -not $_.pass -and -not $_.skippedReason }).Count
    rows = $results
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
}

$outJson = Join-Path $OutDir "parity_proof_results.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $outJson -Encoding utf8
Write-Host "[proof_pack] rows=$($report.rowCount) pass=$passCount skip=$skipCount fail=$($report.failCount) -> $outJson"

if ($report.failCount -gt 0) { exit 1 }
exit 0
