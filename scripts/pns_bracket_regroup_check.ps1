#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Bracket capture set regroup verification script for Sprint 12.6
    
.DESCRIPTION
    Analyzes pulled capture sets by timestamp, filename patterns, EXIF sequence numbers.
    Validates bracket sets are complete (BKT3/5/7), files not orphaned.
    
.PARAMETER InputDir
    Directory containing capture files (default: hfr-runs/pull_dcim_* latest)
    
.PARAMETER MaxGapSeconds
    Maximum time gap between bracket shots (default: 5 seconds)
    
.PARAMETER BktPattern
    Expected bracket pattern: BKT3, BKT5, BKT7 (default: auto-detect)
    
.EXAMPLE
    .\pns_bracket_regroup_check.ps1 -InputDir "hfr-runs\pull_dcim_20250115"
    
.OUTPUTS
    Writes bracket_regroup_check.json with per-set analysis
#>
[CmdletBinding()]
param(
    [string]$InputDir = "",
    [int]$MaxGapSeconds = 5,
    [string]$BktPattern = ""  # Auto-detect if not specified
)

$ErrorActionPreference = "Stop"
$script:tag = "PNS.BracketCheck"

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    Write-Host "[$ts] $Message"
}

# Find latest pull directory if not specified
if (-not $InputDir) {
    $latestPull = Get-ChildItem -Directory -Path "hfr-runs" -Filter "pull_dcim_*" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    if (-not $latestPull) {
        Write-Log "WARNING: No pull_dcim_* directory found in hfr-runs"
        # Try to find any image files in subdirectories
        $allDirs = Get-ChildItem -Directory -Path "hfr-runs" -ErrorAction SilentlyContinue
        if ($allDirs) {
            $latestPull = $allDirs | Sort-Object LastWriteTime -Descending | Select-Object -First 1
            Write-Log "Using latest directory: $($latestPull.FullName)"
        }
    }
    if (-not $latestPull) {
        throw "No suitable input directory found. Run pns_pull_dcim_captures.ps1 first or specify -InputDir."
    }
    $InputDir = $latestPull.FullName
}

if (-not (Test-Path $InputDir)) {
    throw "Input directory not found: $InputDir"
}

Write-Log "Analyzing bracket sets in: $InputDir"

# Find EXIF tool
$exiftool = Get-Command exiftool -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source

# Get all image files
$imageFiles = Get-ChildItem -Path $InputDir -File | Where-Object { 
    $_.Extension -match '\.(dng|jpg|jpeg|avif|jxl)$' 
} | Sort-Object Name

if (-not $imageFiles) {
    Write-Log "No image files found in $InputDir"
    $results = @{
        timestamp = (Get-Date -Format "o")
        inputDir = $InputDir
        summary = @{ totalFiles = 0; sets = 0; complete = 0; incomplete = 0; orphaned = 0 }
        sets = @()
        orphaned = @()
    }
    $outFile = Join-Path (Split-Path $InputDir -Parent) ("bracket_regroup_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
    $results | ConvertTo-Json -Depth 5 | Set-Content -Path $outFile -Encoding UTF8
    Write-Log "Empty results: $outFile"
    exit 0
}

Write-Log "Found $($imageFiles.Count) image files"

# Parse file metadata
$fileInfos = @()
foreach ($file in $imageFiles) {
    $info = @{
        name = $file.Name
        path = $file.FullName
        size = $file.Length
        extension = $file.Extension.ToLower()
        timestamp = $file.LastWriteTime
        parsed = $false
        bktType = $null
        sequence = $null
        baseName = $null
    }
    
    # Parse filename patterns
    # Patterns:
    #   pns_YYYYMMDD_HHmmss_mmm.dng (single capture)
    #   pns_YYYYMMDD_HHmmss_mmm_BKT3_1.dng (bracket part 1 of 3)
    #   pns_YYYYMMDD_HHmmss_mmm_Bracket_1.dng (alternative pattern)
    
    if ($file.Name -match '^(?<base>pns_\d{8}_\d{6}_\d{3})_(?<bkt>BKT\d+|Bracket)_(?<seq>\d+)\.(?<ext>dng|jpg|avif|jxl)$') {
        $info.parsed = $true
        $info.baseName = $matches.base
        $info.bktType = $matches.bkt
        $info.sequence = [int]$matches.seq
        
        # Normalize BKT pattern
        if ($info.bktType -match 'BKT(\d+)') {
            $info.expectedCount = [int]$matches[1]
        } elseif ($info.bktType -eq 'Bracket') {
            $info.expectedCount = 3  # Default assumption
        }
    } elseif ($file.Name -match '^(?<base>pns_\d{8}_\d{6}_\d{3})\.(?<ext>dng|jpg|avif|jxl)$') {
        $info.parsed = $true
        $info.baseName = $matches.base
        $info.bktType = "SINGLE"
        $info.sequence = 1
        $info.expectedCount = 1
    } else {
        $info.bktType = "UNKNOWN"
    }
    
    # Try to get EXIF timestamp if available
    if ($exiftool) {
        try {
            $exifJson = & $exiftool -json -DateTimeOriginal -SubSecTimeOriginal $file.FullName 2>&1 | ConvertFrom-Json
            if ($exifJson.DateTimeOriginal) {
                $info.exifTimestamp = $exifJson.DateTimeOriginal
                if ($exifJson.SubSecTimeOriginal) {
                    $info.exifSubSec = $exifJson.SubSecTimeOriginal
                }
            }
        } catch {
            # EXIF read failed, use file timestamp
        }
    }
    
    $fileInfos += $info
}

Write-Log "Parsed $($fileInfos.Count) files"

# Group by base timestamp (bracket sets)
$grouped = $fileInfos | Where-Object { $_.parsed } | Group-Object -Property baseName

$results = @{
    timestamp = (Get-Date -Format "o")
    inputDir = $InputDir
    summary = @{
        totalFiles = $imageFiles.Count
        parsedFiles = ($fileInfos | Where-Object { $_.parsed }).Count
        sets = 0
        complete = 0
        incomplete = 0
        orphaned = 0
        byType = @{}
    }
    sets = @()
    orphaned = @()
}

# Analyze each group
foreach ($group in $grouped) {
    $files = $group.Group | Sort-Object sequence
    $first = $files[0]
    
    $set = @{
        baseName = $group.Name
        type = $first.bktType
        expectedCount = $first.expectedCount
        actualCount = $files.Count
        files = @($files | ForEach-Object { $_.name })
        sequences = @($files | ForEach-Object { $_.sequence })
        timestamps = @($files | ForEach-Object { $_.timestamp.ToString("o") })
        complete = $false
        gaps = @()
        errors = @()
    }
    
    # Check completeness
    if ($first.expectedCount) {
        $set.complete = ($files.Count -eq $first.expectedCount)
        
        # Check for missing sequences
        $expectedSeq = 1..$first.expectedCount
        $missing = $expectedSeq | Where-Object { $_ -notin $set.sequences }
        if ($missing) {
            $set.errors += "Missing sequences: $($missing -join ', ')"
        }
    }
    
    # Check time gaps
    for ($i = 1; $i -lt $files.Count; $i++) {
        $gap = ($files[$i].timestamp - $files[$i-1].timestamp).TotalSeconds
        if ($gap -gt $MaxGapSeconds) {
            $set.gaps += @{
                between = "$($files[$i-1].sequence) -> $($files[$i].sequence)"
                gapSeconds = $gap
            }
        }
    }
    
    # Update summary
    $type = $set.type
    if (-not $results.summary.byType[$type]) {
        $results.summary.byType[$type] = @{ count = 0; complete = 0; incomplete = 0; files = 0 }
    }
    $results.summary.byType[$type].count++
    $results.summary.byType[$type].files += $files.Count
    if ($set.complete) {
        $results.summary.byType[$type].complete++
        $results.summary.complete++
    } else {
        $results.summary.byType[$type].incomplete++
        $results.summary.incomplete++
    }
    
    $results.summary.sets++
    $results.sets += $set
}

# Find orphaned files (unparsed or ungrouped)
$orphaned = $fileInfos | Where-Object { -not $_.parsed }
foreach ($orphan in $orphaned) {
    $results.orphaned += @{
        name = $orphan.name
        reason = "Could not parse filename"
    }
    $results.summary.orphaned++
}

# Write output
$outDir = "hfr-runs"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$outFile = if ($OutputJson) { $OutputJson } else { 
    Join-Path $outDir ("bracket_regroup_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
}
$results | ConvertTo-Json -Depth 10 | Set-Content -Path $outFile -Encoding UTF8
Write-Log "Results written to: $outFile"

# Summary output
Write-Host "`n=== Bracket Regroup Check Summary ===" -ForegroundColor Cyan
Write-Host "Total files: $($results.summary.totalFiles)"
Write-Host "Parsed: $($results.summary.parsedFiles) | Orphaned: $($results.summary.orphaned)"
Write-Host "Bracket sets: $($results.summary.sets)" -ForegroundColor White
Write-Host "  Complete: $($results.summary.complete)" -ForegroundColor Green
Write-Host "  Incomplete: $($results.summary.incomplete)" -ForegroundColor $(if ($results.summary.incomplete -gt 0) { "Yellow" } else { "Green" })

foreach ($type in ($results.summary.byType.Keys | Sort-Object)) {
    $stats = $results.summary.byType[$type]
    Write-Host "  $type`: $($stats.complete)/$($stats.count) complete ($($stats.files) files)"
}

if ($results.orphaned.Count -gt 0) {
    Write-Host "`nOrphaned files:" -ForegroundColor Yellow
    $results.orphaned | Select-Object -First 5 | ForEach-Object { Write-Host "  - $($_.name)" }
}

# Return exit code
exit $(if ($results.summary.incomplete -eq 0 -and $results.summary.orphaned -eq 0) { 0 } else { 1 })
