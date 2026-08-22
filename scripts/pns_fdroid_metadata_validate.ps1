# Point & Shoot — F-Droid metadata + en-US store asset gate (host-only).
# Milestone T Sprint T.10. Wired into pns_prerelease_gate.ps1 (T.12 full orchestrator).
#
# Usage:
#   .\scripts\pns_fdroid_metadata_validate.ps1
#   .\scripts\pns_fdroid_metadata_validate.ps1 -ProjectRoot C:\path\to\repo

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$metaYml = Join-Path $ProjectRoot "metadata\metadata.yml"
$gradlePath = Join-Path $ProjectRoot "app\build.gradle.kts"
$enUs = Join-Path $ProjectRoot "metadata\en-US"
$failures = New-Object System.Collections.Generic.List[string]

function Add-Fail {
  param([string]$Message)
  $failures.Add("FAIL: $Message")
}

foreach ($required in @($metaYml, $gradlePath)) {
  if (-not (Test-Path -LiteralPath $required)) {
    Add-Fail "missing required file '$required'"
  }
}

if ($failures.Count -gt 0) {
  foreach ($f in $failures) { Write-Host $f }
  exit 1
}

$yaml = [System.IO.File]::ReadAllText($metaYml)
$gradle = [System.IO.File]::ReadAllText($gradlePath)

if ($yaml -match '<your-username>') {
  Add-Fail "metadata.yml still contains placeholder '<your-username>'"
}

$expectedRepo = "https://github.com/edwardlthompson/point-and-shoot"
if ($yaml -notmatch [regex]::Escape("SourceCode: $expectedRepo")) {
  Add-Fail "metadata.yml SourceCode must be $expectedRepo"
}
if ($yaml -notmatch [regex]::Escape("IssueTracker: $expectedRepo/issues")) {
  Add-Fail "metadata.yml IssueTracker must be $expectedRepo/issues"
}
if ($yaml -notmatch 'License:\s*Apache-2\.0') {
  Add-Fail "metadata.yml License must be Apache-2.0"
}
if ($yaml -notmatch '(?m)^Builds:\s*$') {
  Add-Fail "metadata.yml missing Builds: block"
}
if ($yaml -notmatch 'gradle:\s*\n\s+-\s+yes') {
  Add-Fail "metadata.yml Builds must enable gradle (gradle: yes)"
}

if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') {
  Add-Fail "could not parse versionCode from app/build.gradle.kts"
} else {
  $gradleCode = [int]$Matches[1]
}
if ($gradle -notmatch 'versionName\s*=\s*"([^"]+)"') {
  Add-Fail "could not parse versionName from app/build.gradle.kts"
} else {
  $gradleName = $Matches[1]
}

if ($yaml -notmatch "versionCode:\s*$gradleCode\b") {
  Add-Fail "metadata.yml Builds versionCode must match app ($gradleCode)"
}
if ($yaml -notmatch [regex]::Escape("versionName: '$gradleName'")) {
  Add-Fail "metadata.yml Builds versionName must match app ('$gradleName')"
}
if ($yaml -notmatch "(?m)^CurrentVersion: $([regex]::Escape($gradleName))\s*$") {
  Add-Fail "metadata.yml CurrentVersion must match app ($gradleName)"
}
if ($yaml -notmatch "(?m)^CurrentVersionCode: $gradleCode\s*$") {
  Add-Fail "metadata.yml CurrentVersionCode must match app ($gradleCode)"
}
if ($yaml -match '(?m)^\s+# publishedApkSha256: (\S+)\s*$') {
  $pubSha = $Matches[1]
  if ($pubSha -notmatch '^[a-f0-9]{64}$') {
    Add-Fail "publishedApkSha256 must be 64 lowercase hex"
  }
}

$textFiles = @{
  "title.txt" = (Join-Path $enUs "title.txt")
  "short_description.txt" = (Join-Path $enUs "short_description.txt")
  "full_description.txt" = (Join-Path $enUs "full_description.txt")
}
foreach ($entry in $textFiles.GetEnumerator()) {
  $path = $entry.Value
  if (-not (Test-Path -LiteralPath $path)) {
    Add-Fail "missing metadata/en-US/$($entry.Key)"
    continue
  }
  $content = ([System.IO.File]::ReadAllText($path)).Trim()
  if ([string]::IsNullOrWhiteSpace($content)) {
    Add-Fail "metadata/en-US/$($entry.Key) is empty"
  }
}

$shortPath = Join-Path $enUs "short_description.txt"
if (Test-Path -LiteralPath $shortPath) {
  $shortLen = ([System.IO.File]::ReadAllText($shortPath)).Trim().Length
  if ($shortLen -gt 80) {
    Add-Fail "short_description.txt length $shortLen exceeds F-Droid limit 80"
  }
}

$changelogPath = Join-Path $enUs "changelogs\$gradleCode.txt"
if (-not (Test-Path -LiteralPath $changelogPath)) {
  Add-Fail "missing metadata/en-US/changelogs/$gradleCode.txt (sync with versionCode)"
} else {
  $cl = ([System.IO.File]::ReadAllText($changelogPath)).Trim()
  if ($cl.Length -lt 20) {
    Add-Fail "changelogs/$gradleCode.txt too short (need excerpt from CHANGELOG)"
  }
}

function Test-ScreenshotDir {
  param(
    [string]$RelativeDir,
    [string]$Label
  )
  $dir = Join-Path $ProjectRoot $RelativeDir
  if (-not (Test-Path -LiteralPath $dir)) {
    Add-Fail "missing directory $RelativeDir"
    return
  }
  $pngs = Get-ChildItem -LiteralPath $dir -Filter "*.png" -File -ErrorAction SilentlyContinue
  if ($null -eq $pngs -or $pngs.Count -lt 1) {
    Add-Fail "$Label requires at least one PNG under $RelativeDir"
  }
}

Test-ScreenshotDir -RelativeDir "metadata\en-US\images\phoneScreenshots" -Label "phoneScreenshots"
Test-ScreenshotDir -RelativeDir "metadata\en-US\images\sevenInchScreenshots" -Label "sevenInchScreenshots"

if ($failures.Count -gt 0) {
  Write-Host "FDROID METADATA GATE: FAIL ($($failures.Count) issue(s))"
  foreach ($f in $failures) { Write-Host $f }
  exit 1
}

Write-Host "FDROID METADATA GATE: PASS (metadata.yml + en-US store assets; versionCode=$gradleCode)"
exit 0
