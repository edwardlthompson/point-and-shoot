# Check for new upstream agent-project-bootstrap releases on GitHub.
#
# Usage:
#   .\scripts\pns_check_template_updates.ps1
#   .\scripts\pns_check_template_updates.ps1 -Verbose

param(
  [switch]$Verbose
)

$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ConfigPath = Join-Path $Root ".template-update.json"
$VersionPath = Join-Path $Root ".template-version"

if (-not (Test-Path $ConfigPath)) {
  Write-Host "No .template-update.json found; skipping update check"
  exit 0
}

$config = Get-Content $ConfigPath -Raw | ConvertFrom-Json
$upstream = if ($config.upstream) { $config.upstream } else { "edwardlthompson/agent-project-bootstrap" }
$interval = if ($config.check_interval) { $config.check_interval } else { "weekly" }

$validIntervals = @("off", "daily", "weekly", "monthly", "on_session")
if ($interval -notin $validIntervals) {
  Write-Host "Warning: invalid check_interval '$interval'; defaulting to weekly"
  $interval = "weekly"
}

if ($interval -eq "off") {
  if ($Verbose) { Write-Host "Update check disabled (interval: off)" }
  exit 0
}

if ($config.last_checked -and $interval -ne "on_session") {
  try {
    $last = [DateTime]::Parse($config.last_checked)
    $days = switch ($interval) {
      "daily" { 1 }
      "weekly" { 7 }
      "monthly" { 30 }
      "on_session" { 0 }
      default { 7 }
    }
    if ((Get-Date).ToUniversalTime().Subtract($last).TotalDays -lt $days) {
      if ($Verbose) { Write-Host "Skipped: interval throttle ($interval)" }
      exit 0
    }
  } catch {
    if ($Verbose) { Write-Host "Warning: could not parse last_checked; checking anyway" }
  }
}

try {
  $response = Invoke-RestMethod -Uri "https://api.github.com/repos/$upstream/releases/latest" -ErrorAction Stop
} catch {
  Write-Host "Warning: could not reach GitHub API for $upstream"
  exit 0
}

$latest = $response.tag_name -replace '^v', ''
$current = (Get-Content $VersionPath -Raw).Trim()
$config.last_checked = (Get-Date).ToUniversalTime().ToString("o")
$utf8 = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($ConfigPath, ($config | ConvertTo-Json -Depth 5), $utf8)

function Compare-Version($a, $b) {
  $aParts = @($a.Split('.') | ForEach-Object { [int]$_ })
  $bParts = @($b.Split('.') | ForEach-Object { [int]$_ })
  for ($i = 0; $i -lt [Math]::Max($aParts.Count, $bParts.Count); $i++) {
    $av = if ($i -lt $aParts.Count) { $aParts[$i] } else { 0 }
    $bv = if ($i -lt $bParts.Count) { $bParts[$i] } else { 0 }
    if ($av -gt $bv) { return 1 }
    if ($av -lt $bv) { return -1 }
  }
  return 0
}

if ((Compare-Version $latest $current) -gt 0) {
  Write-Host "========================================="
  Write-Host " NEW TEMPLATE VERSION AVAILABLE"
  Write-Host " Current:  $current"
  Write-Host " Latest:   $latest"
  Write-Host " Release:  $($response.html_url)"
  Write-Host " See docs/UPGRADING_FROM_TEMPLATE.md"
  Write-Host "========================================="
} elseif ($Verbose) {
  Write-Host "Template is up to date ($current)"
}

exit 0
