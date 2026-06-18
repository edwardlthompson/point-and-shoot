#Requires -Version 5.1
<#
.SYNOPSIS
  Automated GitHub Pages smoke — workflow status + HTTP 200 on leaderboard URLs.
.EXAMPLE
  .\scripts\pns_github_pages_smoke.ps1
#>
param(
    [string]$Repo = "edwardlthompson/point-and-shoot",
    [string]$Workflow = "leaderboard-pages.yml",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $OutDir = Join-Path $root "hfr-runs\github_pages_smoke_$(Get-Date -Format yyyyMMdd_HHmmss)"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$failures = @()
$urls = @(
    "https://edwardlthompson.github.io/point-and-shoot/leaderboard/",
    "https://edwardlthompson.github.io/point-and-shoot/leaderboard/data/site.json"
)

$runJson = gh run list --repo $Repo --workflow $Workflow --limit 1 --json conclusion,status,createdAt,displayTitle,url 2>&1
if ($LASTEXITCODE -ne 0) { $failures += "gh run list failed: $runJson" }
else {
    $run = ($runJson | ConvertFrom-Json)[0]
    if ($run.conclusion -ne "success") { $failures += "latest workflow conclusion=$($run.conclusion)" }
}

foreach ($url in $urls) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 20
        if ($r.StatusCode -ne 200) { $failures += "$url status=$($r.StatusCode)" }
    } catch {
        $failures += "$url error: $_"
    }
}

$report = @{
    schema = "pns.github_pages_smoke.v1"
    repo = $Repo
    workflow = $Workflow
    run = if ($runJson) { ($runJson | ConvertFrom-Json)[0] } else { $null }
    urls = $urls
    pass = ($failures.Count -eq 0)
    failures = $failures
}
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "github_pages_smoke.json") -Encoding utf8

if ($failures.Count -gt 0) {
    Write-Host "GITHUB PAGES SMOKE: FAIL ($($failures.Count) issue(s)) out=$OutDir"
    $failures | ForEach-Object { Write-Host "  - $_" }
    exit 1
}
Write-Host "GITHUB PAGES SMOKE: PASS out=$OutDir"
exit 0
