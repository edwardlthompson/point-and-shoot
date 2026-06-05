param(
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not $OutDir) { $OutDir = Join-Path $repoRoot "docs\leaderboard\data" }
$feedPath = Join-Path $OutDir "feed.json"
$devicesDir = Join-Path $OutDir "devices"
$rssPath = Join-Path $OutDir "feed.xml"
$siteUrl = "https://edwardlthompson.github.io/point-and-shoot/leaderboard/"

$feed = $null
if (Test-Path -LiteralPath $feedPath) {
    try { $feed = Get-Content -LiteralPath $feedPath -Raw | ConvertFrom-Json } catch { }
}

$updated = if ($feed -and $feed.updatedUtc) { [string]$feed.updatedUtc } else { [DateTime]::UtcNow.ToString("o") }
$items = @()
if ($feed -and $feed.items) { $items = @($feed.items) }

$xml = @"
<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>Point &amp; Shoot Camera Parity Leaderboard</title>
    <link>$siteUrl</link>
    <description>Tested Camera2 capability rankings for Android phones</description>
    <lastBuildDate>$updated</lastBuildDate>
"@

foreach ($item in $items | Select-Object -First 25) {
    $slug = [string]$item.slug
    $name = [string]$item.marketingName
    $ts = [string]$item.timestampUtc
    $link = "${siteUrl}#/device/$slug"
    $xml += @"

    <item>
      <title>$([System.Security.SecurityElement]::Escape($name))</title>
      <link>$link</link>
      <guid isPermaLink="true">$link</guid>
      <pubDate>$ts</pubDate>
      <description>Device added to parity leaderboard: $([System.Security.SecurityElement]::Escape($name))</description>
    </item>
"@
}

$xml += @"

  </channel>
</rss>
"@
$xml | Set-Content -LiteralPath $rssPath -Encoding utf8
Write-Host "[leaderboard_rss] items=$($items.Count) -> $rssPath"
