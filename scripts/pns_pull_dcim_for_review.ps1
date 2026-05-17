#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Automated DCIM pull script for Sprint 10.16 gallery/desktop handoff
    
.DESCRIPTION
    Pulls recent captures from device DCIM for human review in desktop apps.
    Supports Sprint 10.16 and H.1 gallery handoff workflow.
    
.PARAMETER Serial
    ADB device serial
    
.PARAMETER SinceHours
    Pull files modified in last N hours (default: 24)
    
.PARAMETER OutDir
    Output directory (default: hfr-runs\dcim_review_<timestamp>)
    
.PARAMETER OpenFolder
    Open output folder after pull
    
.EXAMPLE
    .\pns_pull_dcim_for_review.ps1 -Serial 8bf09993 -SinceHours 2
    
.OUTPUTS
    Copies files to host for desktop review in darktable/RawTherapee
#>
[CmdletBinding()]
param(
    [string]$Serial = "",
    [int]$SinceHours = 24,
    [string]$OutDir = "",
    [switch]$OpenFolder
)

$ErrorActionPreference = "Stop"

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    Write-Host "[$ts] $Message"
}

# Find ADB
$adb = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $adb) {
    $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
}

$deviceArgs = if ($Serial) { @("-s", $Serial) } else { @() }

# Check device
$devices = & $adb @deviceArgs devices | Select-String "device$"
if (-not $devices) {
    throw "No ADB devices connected"
}

Write-Log "Starting DCIM pull for desktop review"

# Create output directory
if (-not $OutDir) {
    $OutDir = Join-Path "hfr-runs" ("dcim_review_{0:yyyyMMdd_HHmmss}" -f (Get-Date))
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$dcimPath = "/sdcard/DCIM/Point & Shoot"

# Find files modified in last N hours
$findMtime = "-mmin -$($SinceHours * 60)"
Write-Log "Finding files modified in last $SinceHours hours..."

$files = & $adb @deviceArgs shell "find '$dcimPath' -type f $findMtime 2>/dev/null | sort" 2>&1

if (-not $files -or $files -match "No such file") {
    Write-Log "No recent files found in $dcimPath"
    # Try listing all files
    $files = & $adb @deviceArgs shell "ls -t '$dcimPath'/*.dng '$dcimPath'/*.jpg '$dcimPath'/*.mp4 2>/dev/null | head -20" 2>&1
}

$pullCount = 0
$totalSize = 0

foreach ($file in $files) {
    $file = $file.Trim()
    if (-not $file -or $file -notmatch "\.(dng|jpg|jpeg|mp4|avif|jxl)$") { continue }
    
    Write-Log "Pulling: $file"
    try {
        & $adb @deviceArgs pull "$file" "$OutDir" 2>&1 | ForEach-Object {
            if ($_ -match "(\d+) bytes") {
                $totalSize += [int]$matches[1]
            }
        }
        $pullCount++
    } catch {
        Write-Log "  Failed to pull: $_"
    }
}

# Generate summary
$summary = @{
    timestamp = (Get-Date -Format "o")
    sourceDevice = if ($Serial) { $Serial } else { "default" }
    sinceHours = $SinceHours
    outputDir = $OutDir
    filesPulled = $pullCount
    totalSizeBytes = $totalSize
    files = @(Get-ChildItem $OutDir -File | Select-Object Name, Length, @{N="Type";E={$_.Extension.TrimStart('.').ToUpper()}})
}

$jsonFile = Join-Path $OutDir "pull_summary.json"
$summary | ConvertTo-Json -Depth 3 | Set-Content -Path $jsonFile -Encoding UTF8

# Generate markdown for PROBE_BUILD_PLAN.md §5
$mdFile = Join-Path $OutDir "review_manifest.md"
$mdContent = @"
# DCIM Review Manifest

**Date:** $(Get-Date -Format "yyyy-MM-dd HH:mm")
**Device:** $($summary.sourceDevice)
**Files:** $($summary.filesPulled)

## Files for Desktop Review

| File | Size | Type |
|------|------|------|
"@

foreach ($f in $summary.files) {
    $mdContent += "| $($f.Name) | $([math]::Round($f.Length/1MB, 2)) MB | $($f.Type) |`n"
}

$mdContent += @"

## Desktop Review Instructions

1. **DNG files:** Open in darktable, RawTherapee, or Adobe Camera Raw
2. **JPG files:** Verify in standard image viewer
3. **AVIF/JXL files:** Use Chrome, Irfanview (with plugins), or `djxl`/`avifdec`
4. **MP4 files:** Verify in VLC or similar

## Checklist for Human Review (Sprint 10.16 / H.1)

- [ ] DNG files open without corruption warnings
- [ ] Colors appear correct (check neutral gray patches if available)
- [ ] No obvious artifacts in highlights/shadows
- [ ] File metadata intact (timestamp, camera info)

## Evidence Location

Pulled to: `$OutDir`

---
**Next:** Complete Sprint H.1 visual sign-off or document issues in §5
"@

$mdContent | Set-Content -Path $mdFile -Encoding UTF8

Write-Log "Pulled $pullCount files ($([math]::Round($totalSize/1MB, 2)) MB) to $OutDir"
Write-Log "Summary: $jsonFile"
Write-Log "Manifest: $mdFile"

# Battery conservation
Write-Log "Closing app to conserve battery..."
& $adb @deviceArgs shell am force-stop dev.pointandshoot 2>&1 | Out-Null

# Open folder if requested
if ($OpenFolder -and $pullCount -gt 0) {
    Invoke-Item $OutDir
}

Write-Host "`n=== DCIM Pull for Review Complete ===" -ForegroundColor Cyan
Write-Host "Files: $pullCount" -ForegroundColor White
Write-Host "Total: $([math]::Round($totalSize/1MB, 2)) MB"
Write-Host "Output: $OutDir" -ForegroundColor Green

exit $(if ($pullCount -gt 0) { 0 } else { 1 })
