#!/usr/bin/env pwsh
<#
.SYNOPSIS
    GitHub Release automation script for Sprint 12.6
    
.DESCRIPTION
    Creates GitHub releases, uploads APK/AAB artifacts, generates release notes from CHANGELOG.md.
    Uses gh CLI or GitHub REST API.
    
.PARAMETER Tag
    Git tag for release (e.g., "v1.0.0")
    
.PARAMETER ReleaseNotesPath
    Path to release notes/CHANGELOG file (default: CHANGELOG.md)
    
.PARAMETER Draft
    Create as draft release
    
.PARAMETER Prerelease
    Mark as pre-release
    
.PARAMETER ArtifactsDir
    Directory containing APK/AAB files (default: app/build/outputs)
    
.EXAMPLE
    .\pns_release_automation.ps1 -Tag "v1.0.0" -Draft
    
.OUTPUTS
    Writes release_automation.json with release details
#>
[CmdletBinding(SupportsShouldProcess=$true)]
param(
    [Parameter(Mandatory=$true)]
    [string]$Tag,
    
    [string]$ReleaseNotesPath = "CHANGELOG.md",
    [switch]$Draft,
    [switch]$Prerelease,
    [string]$ArtifactsDir = "app\build\outputs"
)

$ErrorActionPreference = "Stop"
$script:logTag = "PNS.ReleaseAutomation"

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    Write-Host "[$ts] $Message"
}

# Find repository
$repo = $null
try {
    $remoteUrl = & git remote get-url origin 2>&1
    if ($remoteUrl -match "github\.com[:/](?<owner>[^/]+)/(?<repo>[^/\.]+)") {
        $repo = "$($matches.owner)/$($matches.repo)"
        Write-Log "Repository: $repo"
    }
} catch {
    Write-Log "Could not infer repo from git remote"
}

if (-not $repo) {
    throw "Repository not detected. Ensure you're in a git repository with GitHub remote."
}

# Check for gh CLI
$gh = Get-Command gh -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $gh) {
    throw "gh CLI not found. Install from https://cli.github.com/"
}

# Verify authentication
Write-Log "Checking GitHub authentication..."
try {
    $authStatus = & $gh auth status 2>&1
    Write-Log "Authenticated: OK"
} catch {
    throw "Not authenticated with GitHub. Run: gh auth login"
}

# Read or generate release notes
$releaseNotes = ""
$changelogSection = ""

if (Test-Path $ReleaseNotesPath) {
    Write-Log "Reading release notes from: $ReleaseNotesPath"
    $changelog = Get-Content -Path $ReleaseNotesPath -Raw
    
    # Try to find section for this tag
    # Look for patterns like:
    # ## [v1.0.0] or ## v1.0.0 or ## 1.0.0
    $cleanTag = $Tag -replace '^v', ''
    $patterns = @(
        "## \[$Tag\].*?(?=## \[|$)",
        "## $Tag.*?(?=## |$)",
        "## \[$cleanTag\].*?(?=## \[|$)",
        "## $cleanTag.*?(?=## |$)"
    )
    
    foreach ($pattern in $patterns) {
        if ($changelog -match $pattern) {
            $changelogSection = $matches[0].Trim()
            break
        }
    }
    
    if ($changelogSection) {
        # Remove the header line
        $lines = $changelogSection -split "`n"
        $releaseNotes = ($lines | Select-Object -Skip 1) -join "`n"
        $releaseNotes = $releaseNotes.Trim()
        Write-Log "Found changelog section for $Tag"
    } else {
        # Use full changelog or generate generic notes
        Write-Log "No specific section found for $Tag, using generic notes"
        $releaseNotes = "Release $Tag`n`nSee CHANGELOG.md for full details."
    }
} else {
    Write-Log "CHANGELOG.md not found, using generic release notes"
    $releaseNotes = "Release $Tag"
}

Write-Log "Release notes: $($releaseNotes.Length) chars"

# Find artifacts
$artifactPaths = @()
$searchDirs = @(
    "$ArtifactsDir\apk\release\*.apk",
    "$ArtifactsDir\apk\debug\*.apk",
    "$ArtifactsDir\bundle\release\*.aab",
    "$ArtifactsDir\bundle\debug\*.aab"
)

foreach ($pattern in $searchDirs) {
    $found = Get-ChildItem -Path $pattern -ErrorAction SilentlyContinue
    if ($found) {
        $artifactPaths += $found
    }
}

if (-not $artifactPaths) {
    Write-Log "WARNING: No artifacts found in $ArtifactsDir"
    Write-Log "Run: .\gradlew :app:assembleRelease :app:bundleRelease"
} else {
    Write-Log "Found $($artifactPaths.Count) artifact(s):"
    $artifactPaths | ForEach-Object { Write-Log "  - $($_.Name)" }
}

# Check if tag exists
Write-Log "Checking tag: $Tag"
try {
    $existingTag = & git rev-parse $Tag 2>&1
    Write-Log "Tag exists: $Tag"
} catch {
    Write-Log "Tag not found locally: $Tag"
    Write-Log "Will create tag on current HEAD"
}

# Prepare release creation
$ghArgs = @(
    "release", "create", $Tag
    "--repo=$repo"
    "--title=$Tag"
)

if ($Draft) {
    $ghArgs += "--draft"
}

if ($Prerelease) {
    $ghArgs += "--prerelease"
}

if ($releaseNotes) {
    # Write to temp file for gh CLI
    $notesFile = [System.IO.Path]::GetTempFileName()
    $releaseNotes | Set-Content -Path $notesFile -Encoding UTF8 -NoNewline
    $ghArgs += "--notes-file=$notesFile"
} else {
    $ghArgs += "--generate-notes"
}

# Add artifacts
foreach ($artifact in $artifactPaths) {
    $ghArgs += $artifact.FullName
}

# Create release
Write-Log "Creating release with gh CLI..."
$releaseOutput = $null
try {
    if ($PSCmdlet.ShouldProcess("$Tag with $($artifactPaths.Count) artifacts", "Create Release")) {
        $releaseOutput = & $gh @ghArgs 2>&1
        Write-Log "Release created successfully"
        Write-Log $releaseOutput
    } else {
        Write-Log "[WhatIf] Would execute: gh $ghArgs"
    }
} catch {
    Write-Log "Release creation failed: $_"
    throw
}

# Cleanup temp notes file
if ($notesFile -and (Test-Path $notesFile)) {
    Remove-Item $notesFile -Force
}

# Parse release URL
$releaseUrl = $null
if ($releaseOutput -match 'https://github\.com/[^/]+/[^/]+/releases/tag/[^\s]+') {
    $releaseUrl = $matches[0]
}

# Build result
$results = @{
    timestamp = (Get-Date -Format "o")
    tag = $Tag
    repo = $repo
    draft = $Draft.IsPresent
    prerelease = $Prerelease.IsPresent
    releaseUrl = $releaseUrl
    artifacts = @($artifactPaths | ForEach-Object { 
        @{
            name = $_.Name
            path = $_.FullName
            size = $_.Length
        }
    })
    releaseNotesLength = $releaseNotes.Length
    success = ($null -ne $releaseUrl)
}

# Write JSON
$outDir = "hfr-runs"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$outFile = Join-Path $outDir ("release_automation_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
$results | ConvertTo-Json -Depth 5 | Set-Content -Path $outFile -Encoding UTF8
Write-Log "Results: $outFile"

# Summary
Write-Host "`n=== Release Automation Summary ===" -ForegroundColor Cyan
Write-Host "Tag: $Tag"
Write-Host "Repository: $repo"
if ($releaseUrl) {
    Write-Host "Release URL: $releaseUrl" -ForegroundColor Green
} else {
    Write-Host "Release URL: (not captured)" -ForegroundColor Yellow
}
Write-Host "Draft: $($Draft.IsPresent)"
Write-Host "Pre-release: $($Prerelease.IsPresent)"
Write-Host "Artifacts uploaded: $($results.artifacts.Count)"

exit $(if ($results.success) { 0 } else { 1 })
