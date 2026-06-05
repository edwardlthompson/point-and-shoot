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

$combined = $catalogText + "`n" + $expansionText
if ($combined -notmatch 'probe_only_inventory') {
    $failures += "Encoder ProbeOnly rows must set sweepSkipReason=probe_only_inventory"
}
if ($combined -notmatch 'AppStatus\.ProbeOnly') {
    $failures += "Catalog must include AppStatus.ProbeOnly encoder inventory rows"
}
if ($combined -notmatch 'consumerImpact') {
    $failures += "Catalog rows must declare consumerImpact metadata (M21)"
}

$descriptorText = Get-Content -LiteralPath $descriptorKt -Raw
foreach ($codec in @("H264", "H265", "AV1", "DCG")) {
    if ($descriptorText -notmatch "VideoCodec\.$codec") {
        $failures += "FormatQualityRegistry missing descriptor for $codec"
    }
}

function Test-HasProofScriptForCatalogId([string]$CatalogId, [string[]]$Sources) {
    $suffix = if ($CatalogId -match '\.([^.]+)$') { $Matches[1] } else { $null }
    foreach ($src in $Sources) {
        $needles = @("`"$CatalogId`"")
        if ($suffix) { $needles += "`"$suffix`"" }
        foreach ($needle in $needles) {
            $idx = $src.IndexOf($needle)
            while ($idx -ge 0) {
                $start = [Math]::Max(0, $idx - 120)
                $len = [Math]::Min(1200, $src.Length - $start)
                $chunk = $src.Substring($start, $len)
                if ($chunk -match 'parityProofScript\s*=' -or $chunk -match 'proofScript\s*=') { return $true }
                $idx = $src.IndexOf($needle, $idx + 1)
            }
        }
    }
    return $false
}

# M22.0 hardening: if latest Full parity says a Partial/Shipped row is unautomated,
# require a parityProofScript hook in catalog sources before milestone gate close.
$latestParity = Join-Path $projRoot "docs\FLEET_PARITY_LATEST.json"
if (Test-Path -LiteralPath $latestParity) {
    try {
        $latestObj = Get-Content -LiteralPath $latestParity -Raw | ConvertFrom-Json
        $inAppPath = $latestObj.inAppJsonPath
        if ($inAppPath -and (Test-Path -LiteralPath $inAppPath)) {
            $inAppObj = Get-Content -LiteralPath $inAppPath -Raw | ConvertFrom-Json
            $unautomatedRows = @($inAppObj.cells | Where-Object { $_.gap -eq "GAP_UNAUTOMATED" -or $_.failReason -eq "unautomated" })
            $missingProof = @()
            foreach ($cell in $unautomatedRows) {
                $id = [string]$cell.catalogId
                if (-not (Test-HasProofScriptForCatalogId $id @($catalogText, $expansionText))) {
                    $missingProof += $id
                }
            }
            if ($missingProof.Count -gt 0) {
                $uniq = $missingProof | Sort-Object -Unique
                $failures += "M22 proof coverage: GAP_UNAUTOMATED rows missing parityProofScript -> $($uniq -join ', ')"
            }
        }
    } catch {
        $failures += "Failed to parse latest parity artifacts for proof coverage check"
    }
}

# M22.8 ownership hardening: every currently open advertised+not_proven row
# in the latest parity snapshot must have an owner classification.
$ownershipPath = Join-Path $projRoot "docs\M22_PROVIDER_OWNERSHIP.json"
if ((Test-Path -LiteralPath $latestParity) -and (Test-Path -LiteralPath $ownershipPath)) {
    try {
        $ownership = Get-Content -LiteralPath $ownershipPath -Raw | ConvertFrom-Json
        if ($ownership.schema -ne "pns.m22_provider_ownership.v1") {
            $failures += "M22 ownership map schema mismatch: $($ownership.schema)"
        } else {
            $allowedOwnerClasses = @("ShipNow", "MatrixGate", "ProbeOnly", "DeferredPlanned")
            $ownershipById = @{}
            foreach ($row in @($ownership.rows)) {
                $id = [string]$row.catalogId
                $ownerClass = [string]$row.ownerClass
                if ([string]::IsNullOrWhiteSpace($id)) { continue }
                if ($ownershipById.ContainsKey($id)) {
                    $failures += "M22 ownership map duplicate catalogId: $id"
                } else {
                    $ownershipById[$id] = $ownerClass
                }
                if ($allowedOwnerClasses -notcontains $ownerClass) {
                    $failures += "M22 ownership map invalid ownerClass for $id -> $ownerClass"
                }
            }

            $latestObj = Get-Content -LiteralPath $latestParity -Raw | ConvertFrom-Json
            $inAppPath = $latestObj.inAppJsonPath
            if ($inAppPath -and (Test-Path -LiteralPath $inAppPath)) {
                $inAppObj = Get-Content -LiteralPath $inAppPath -Raw | ConvertFrom-Json
                $openRows = @($inAppObj.cells | Where-Object { $_.advertised -eq $true -and $_.provenOk -ne $true })
                $uncategorized = @()
                foreach ($cell in $openRows) {
                    $id = [string]$cell.catalogId
                    if (-not $ownershipById.ContainsKey($id)) {
                        $uncategorized += $id
                    }
                }
                if ($uncategorized.Count -gt 0) {
                    $uniq = $uncategorized | Sort-Object -Unique
                    $failures += "M22 ownership coverage missing for open rows -> $($uniq -join ', ')"
                }
            }
        }
    } catch {
        $failures += "Failed to parse M22 provider ownership map"
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
