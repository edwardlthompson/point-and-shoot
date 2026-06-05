param(
    [string]$OutDir = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "pns_leaderboard_export_catalog.ps1 - exports catalog_taxonomy.json + glossary.json"
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$catalogKt = Join-Path $repoRoot "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalog.kt"
$expansionKt = Join-Path $repoRoot "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalogExpansion.kt"
if (-not $OutDir) { $OutDir = Join-Path $repoRoot "docs\leaderboard\data" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Get-QuotedStrings([string]$Fragment) {
    $values = @()
    $i = 0
    while ($i -lt $Fragment.Length) {
        if ($Fragment[$i] -ne '"') {
            $i++
            continue
        }
        $i++
        $sb = New-Object System.Text.StringBuilder
        while ($i -lt $Fragment.Length) {
            $ch = $Fragment[$i]
            if ($ch -eq '\') {
                if ($i + 1 -lt $Fragment.Length) {
                    [void]$sb.Append($Fragment[$i + 1])
                    $i += 2
                    continue
                }
            }
            if ($ch -eq '"') { break }
            [void]$sb.Append($ch)
            $i++
        }
        $values += $sb.ToString()
        $i++
    }
    return $values
}

function Parse-CatalogRows([string[]]$Sources) {
    $rows = @()
    $prefixes = @('CatalogRow(', 'add(row(')
    foreach ($src in $Sources) {
        if (-not (Test-Path -LiteralPath $src)) { continue }
        foreach ($line in Get-Content -LiteralPath $src) {
            $hit = $null
            foreach ($prefix in $prefixes) {
                $idx = $line.IndexOf($prefix)
                if ($idx -ge 0) {
                    $hit = $line.Substring($idx + $prefix.Length)
                    break
                }
            }
            if (-not $hit) { continue }
            $parts = Get-QuotedStrings $hit
            if ($parts.Count -ge 3) {
                $rows += [ordered]@{
                    id = $parts[0]
                    displayName = $parts[1]
                    category = $parts[2]
                }
            }
        }
    }
    $dedup = @{}
    foreach ($r in $rows) { $dedup[$r.id] = $r }
    return @($dedup.Values | Sort-Object id)
}

$rows = Parse-CatalogRows @($catalogKt, $expansionKt)

$catalogVersion = 3
if (Test-Path -LiteralPath $catalogKt) {
    $t = Get-Content -LiteralPath $catalogKt -Raw
    if ($t -match 'CATALOG_VERSION:\s*Int\s*=\s*(\d+)') { $catalogVersion = [int]$Matches[1] }
}

$catalog = [ordered]@{
    schema = 'pns.catalog_taxonomy.v1'
    catalogVersion = $catalogVersion
    updatedUtc = [DateTime]::UtcNow.ToString('o')
    rows = $rows
}
$catalog | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir 'catalog_taxonomy.json') -Encoding utf8

$glossary = [ordered]@{
    schema = 'pns.leaderboard_glossary.v1'
    terms = @(
        @{ id = 'hal_advertised'; term = 'HAL advertised'; definition = 'The device Camera2 HAL reports this capability as supported in its characteristics or stream maps.' }
        @{ id = 'proven'; term = 'Proven'; definition = 'Point and Shoot parity sweep successfully exercised this capability on a real device session.' }
        @{ id = 'sessionOk'; term = 'Session OK'; definition = 'A capture session could be created that includes this capability without immediate failure.' }
        @{ id = 'honesty'; term = 'Honesty %'; definition = 'Percentage of HAL-advertised catalog cells that were proven in the parity sweep.' }
        @{ id = 'restriction_index'; term = 'Restriction Index'; definition = 'OEM score for how much advertised hardware is gated from third-party Camera2 apps. Higher means more restrictive.' }
        @{ id = 'sensor_sum'; term = 'Sensor sum (mm2)'; definition = 'Combined physical sensor area of rear non-logical cameras from HAL or GSMArena.' }
        @{ id = 'camera2'; term = 'Camera2 API'; definition = 'Android standard camera API used by third-party apps, not the OEM proprietary camera app.' }
        @{ id = 'tested_api'; term = 'Tested API'; definition = 'Android SDK/API level running on the device during the parity sweep.' }
        @{ id = 'trust_maintainer'; term = 'Fleet tested'; definition = 'Maintainer USB sweep published from the project regression fleet.' }
        @{ id = 'trust_community'; term = 'Community verified'; definition = 'User-submitted Full parity sweep that passed automated validation.' }
        @{ id = 'advertised_spec'; term = 'Advertised (GSMArena)'; definition = 'Marketing spec-sheet data from GSMArena. Not verified by Point and Shoot Camera2 sweeps.' }
        @{ id = 'camera2_stock_tested'; term = 'Camera2 on stock (tested)'; definition = 'USB Full parity sweep on stock ROM. Separate from GSMArena advertised specs.' }
        @{ id = 'resolution_betrayal'; term = 'Resolution betrayal index'; definition = '0-100 score for rear cameras with higher MP on alternate HAL stream maps vs Camera2 default path.' }
        @{ id = 'parity_pts'; term = 'Parity points'; definition = 'Weighted sum of proven catalog features, resolutions, and capability gates. Primary capability rank.' }
    )
}
$glossary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir 'glossary.json') -Encoding utf8

Write-Host "[catalog_export] rows=$($rows.Count) out=$OutDir"
exit 0
