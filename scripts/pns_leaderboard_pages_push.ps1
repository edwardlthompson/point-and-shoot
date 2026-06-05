# Publish leaderboard data (optional) and push GitHub Pages files to origin/main.
param(
    [switch]$SkipPublish,
    [switch]$MergeSubmissions,
    [string]$CommitMessage = "leaderboard: refresh GitHub Pages site",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

if ($Help) {
    Write-Host @"
pns_leaderboard_pages_push.ps1 - push docs/leaderboard to GitHub (triggers Leaderboard Pages workflow)

  -SkipPublish       Do not run pns_leaderboard_site_publish.ps1 first
  -MergeSubmissions  Pass -MergeSubmissions to publish script
  -CommitMessage     Git commit subject line (default: leaderboard: refresh GitHub Pages site)

Example:
  .\scripts\pns_leaderboard_pages_push.ps1
  .\scripts\pns_leaderboard_pages_push.ps1 -SkipPublish -CommitMessage "leaderboard: fix typo"
"@
    exit 0
}

if (-not $SkipPublish) {
    $pubArgs = @("-SkipGsmarenaScrape")
    if ($MergeSubmissions) { $pubArgs += "-MergeSubmissions" }
    & (Join-Path $PSScriptRoot "pns_leaderboard_site_publish.ps1") @pubArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    & (Join-Path $PSScriptRoot "pns_leaderboard_export_catalog.ps1")
}

$paths = @(
    "docs/leaderboard/",
    "docs/index.html",
    "docs/.nojekyll",
    ".github/workflows/leaderboard-pages.yml"
)

git add @paths
$staged = git diff --cached --name-only
if (-not $staged) {
    Write-Host "[leaderboard_pages_push] nothing to commit - site files unchanged on disk."
    exit 0
}

git commit -m $CommitMessage
git push origin main
Write-Host "[leaderboard_pages_push] pushed - watch Actions > Leaderboard Pages for deploy."
Write-Host "[leaderboard_pages_push] live URL: https://edwardlthompson.github.io/point-and-shoot/leaderboard/"
