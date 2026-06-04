# Point & Shoot — CHANGELOG coverage gate (host-only).
# Ensures CHANGELOG.md documents the latest shipped release and required milestone
# mentions stay in sync with app/build.gradle.kts versionCode.
#
# Manifest: scripts/changelog_coverage.v1.json (update in the same commit as CHANGELOG).
# Wired into: pns_verify_toolchain.ps1 (every host gate).
#
# Usage:
#   .\scripts\pns_changelog_gate.ps1
#   .\scripts\pns_changelog_gate.ps1 -ProjectRoot C:\path\to\repo

param(
  [string]$ProjectRoot = "",
  [string]$CoveragePath = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

if ([string]::IsNullOrWhiteSpace($CoveragePath)) {
  $CoveragePath = Join-Path $PSScriptRoot "changelog_coverage.v1.json"
}

$changelogPath = Join-Path $ProjectRoot "CHANGELOG.md"
$gradlePath = Join-Path $ProjectRoot "app\build.gradle.kts"

$failures = New-Object System.Collections.Generic.List[string]

if (-not (Test-Path -LiteralPath $CoveragePath)) {
  Write-Error "Missing coverage manifest: $CoveragePath"
}

if (-not (Test-Path -LiteralPath $changelogPath)) {
  Write-Error "Missing CHANGELOG.md at $changelogPath"
}

if (-not (Test-Path -LiteralPath $gradlePath)) {
  Write-Error "Missing app/build.gradle.kts at $gradlePath"
}

$coverageRaw = [System.IO.File]::ReadAllText($CoveragePath)
$coverage = $coverageRaw | ConvertFrom-Json
$changelog = [System.IO.File]::ReadAllText($changelogPath)
$gradle = [System.IO.File]::ReadAllText($gradlePath)

$tag = [string]$coverage.latestRelease.tag
$date = [string]$coverage.latestRelease.date
$expectedVersionCode = [int]$coverage.latestRelease.versionCode

if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') {
  $failures.Add("FAIL: could not parse versionCode from app/build.gradle.kts")
} else {
  $actualVersionCode = [int]$Matches[1]
  if ($actualVersionCode -gt $expectedVersionCode) {
    $failures.Add(
      "FAIL: app versionCode=$actualVersionCode exceeds changelog_coverage latestRelease.versionCode=$expectedVersionCode - cut a CHANGELOG release and bump scripts/changelog_coverage.v1.json"
    )
  } elseif ($actualVersionCode -lt $expectedVersionCode) {
    $failures.Add(
      "FAIL: app versionCode=$actualVersionCode is behind changelog_coverage latestRelease.versionCode=$expectedVersionCode - lower coverage versionCode or bump app version"
    )
  }
}

$releaseHeader = "## [$tag] - $date"
if ($changelog -notmatch [regex]::Escape($releaseHeader)) {
  $failures.Add("FAIL: CHANGELOG.md missing release header: $releaseHeader")
}

foreach ($entry in @($coverage.requiredMentions)) {
  $id = [string]$entry.id
  $pattern = [string]$entry.pattern
  if ([string]::IsNullOrWhiteSpace($pattern)) {
    $failures.Add("FAIL: requiredMentions entry '$id' has empty pattern")
    continue
  }
  if ($changelog -notmatch [regex]::Escape($pattern)) {
    $failures.Add("FAIL: CHANGELOG.md missing required mention '$id' (pattern: $pattern)")
  }
}

if ($failures.Count -gt 0) {
  foreach ($line in $failures) {
    Write-Host $line
  }
  Write-Host "HINT: update CHANGELOG.md and scripts/changelog_coverage.v1.json in the same commit when shipping milestones or bumping versionCode."
  exit 1
}

Write-Host "OK: CHANGELOG coverage ($tag, versionCode=$expectedVersionCode, $($coverage.requiredMentions.Count) required mention(s))"
exit 0
