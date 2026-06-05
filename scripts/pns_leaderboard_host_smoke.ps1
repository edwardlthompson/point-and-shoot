# Host smoke for docs/leaderboard/data artifacts (no browser).
param(
    [string]$DataDir = "",
    [string]$OutDir = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host "pns_leaderboard_host_smoke.ps1 - validates leaderboard JSON + CSV/RSS on disk"
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $DataDir) { $DataDir = Join-Path $repoRoot "docs\leaderboard\data" }
if (-not $OutDir) { $OutDir = Join-Path $repoRoot "hfr-runs\leaderboard_host_smoke_$(Get-Date -Format yyyyMMdd_HHmmss)" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$failures = @()

function Fail([string]$Msg) { $script:failures += $Msg }

$sitePath = Join-Path $DataDir "site.json"
if (-not (Test-Path -LiteralPath $sitePath)) { Fail "missing site.json" }
else {
    $site = Get-Content -LiteralPath $sitePath -Raw | ConvertFrom-Json
    if ([int]$site.deviceCount -lt 1) { Fail 'site.deviceCount is zero' }
    foreach ($slug in @($site.deviceSlugs)) {
        if (-not $slug) { Fail "null device slug in site.json"; continue }
        $devPath = Join-Path $DataDir "devices\$slug.json"
        if (-not (Test-Path -LiteralPath $devPath)) { Fail "missing device json for slug=$slug" }
        else {
            $dev = Get-Content -LiteralPath $devPath -Raw | ConvertFrom-Json
            if (-not $dev.resolutionBetrayal) { Fail "$slug missing resolutionBetrayal" }
            if (-not $dev.oemLossSummary) { Fail "$slug missing oemLossSummary" }
            if (-not $dev.measurementContext) { Fail "$slug missing measurementContext" }
        }
    }
}

$groupsPath = Join-Path $DataDir "product_groups.json"
if (-not (Test-Path -LiteralPath $groupsPath)) { Fail "missing product_groups.json" }
else {
    $groups = Get-Content -LiteralPath $groupsPath -Raw | ConvertFrom-Json
    foreach ($g in @($groups.groups)) {
        if ($null -eq $g.advertisedSpec -and $g.testedVariants.Count -lt 1) {
            Fail "group $($g.groupId) has no tested variants or advertisedSpec"
        }
    }
}

foreach ($aux in @("leaderboard.csv", "feed.xml", "glossary.json", "catalog_taxonomy.json")) {
    if (-not (Test-Path -LiteralPath (Join-Path $DataDir $aux))) { Fail "missing $aux" }
}

$report = [ordered]@{
    schema = "pns.leaderboard_host_smoke.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    dataDir = $DataDir
    pass = ($failures.Count -eq 0)
    failures = $failures
}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutDir "leaderboard_host_smoke.json") -Encoding utf8

if ($failures.Count -gt 0) {
    Write-Host "[leaderboard_host_smoke] FAIL: $($failures.Count) issue(s)"
    $failures | ForEach-Object { Write-Host "  - $_" }
    exit 1
}
Write-Host "[leaderboard_host_smoke] PASS out=$OutDir"
exit 0
