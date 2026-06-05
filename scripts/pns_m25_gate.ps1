# Milestone 25 — Camera2 leaderboard host gate (+ optional USB refresh).
param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipPublish,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_m25_gate.ps1 — Milestone 25 leaderboard gate

  Host: JVM tests + leaderboard publish + host smoke
  USB (optional): pns_fleet_matrix_scan.ps1 -ScanTier full when device online

  -HostOnly     skip USB matrix rescan
  -SkipPublish  skip site publish (smoke existing data only)
"@
    exit 0
}

$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$OutDir = Join-Path $projRoot "hfr-runs\m25_gate_$utc"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$results = [ordered]@{
    schema = "pns.m25_gate.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode, [string]$Skipped = "") {
    $row = [ordered]@{ name = $Name; exitCode = $ExitCode; pass = ($ExitCode -eq 0) }
    if ($Skipped) { $row.skipped = $Skipped }
    $results.steps += $row
}

$m25Tests = @(
    "ResolutionBetrayalTest",
    "LeaderboardReadinessTest",
    "LeaderboardDeviceSlugTest",
    "LeaderboardRomReportTest"
)
foreach ($t in $m25Tests) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:testDebugUnitTest" "--tests" "dev.pointandshoot.fleet.$t"
    Add-Step "unit_$t" $LASTEXITCODE
}

if (-not $SkipPublish) {
    & (Join-Path $PSScriptRoot "pns_leaderboard_site_publish.ps1") -SkipGsmarenaScrape
    Add-Step "leaderboard_publish" $LASTEXITCODE
}

& (Join-Path $PSScriptRoot "pns_leaderboard_host_smoke.ps1") -OutDir $OutDir
Add-Step "leaderboard_host_smoke" $LASTEXITCODE

if (-not $HostOnly) {
    $matrixParams = @{ ScanTier = "full" }
    if ($Serial) { $matrixParams.Serial = $Serial }
    & (Join-Path $PSScriptRoot "pns_fleet_matrix_scan.ps1") @matrixParams
    Add-Step "fleet_matrix_scan_full" $LASTEXITCODE
} else {
    Add-Step "fleet_matrix_scan_full" 0 "HostOnly"
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$reportPath = Join-Path $OutDir "m25_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "[m25_gate] pass=$($results.pass) -> $reportPath"
if (-not $results.pass) { exit 1 }
exit 0
