#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Sprint guardrail regression orchestrator — dispatches to verification packs.
    
.DESCRIPTION
    Central orchestrator for running sprint verification "packs" (video/audio,
    capture pipeline, chrome UX, etc.). Provides unified JSON output and
    PROBE_BUILD_PLAN.md §5 evidence collection.
    
    Packs implemented:
    - VideoAudio: Runs pns_video_audio_verify.ps1 for audio-enabled recording
    - CapturePipeline: Runs pns_capture_pipeline_verify.ps1 for RAW/DNG stills
    
.PARAMETER Pack
    Verification pack to run: VideoAudio, CapturePipeline, or All
    
.PARAMETER Serial
    ADB device serial (optional if PNS_ADB_SERIAL env var set)
    
.PARAMETER Fast
    Skip APK rebuild (use existing debug APK)
    
.PARAMETER ProbeSection5
    Append evidence to PROBE_BUILD_PLAN.md §5 (default: true)
    
.EXAMPLE
    .\pns_sprint_guardrail.ps1 -Pack VideoAudio -Serial <serial>
    
    .\pns_sprint_guardrail.ps1 -Pack All -Fast
    
.OUTPUTS
    Writes sprint_guardrail.json and PROBE_BUILD_PLAN.md §5 evidence
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)]
    [ValidateSet("VideoAudio", "CapturePipeline", "All")]
    [string]$Pack,
    
    [string]$Serial = $env:PNS_ADB_SERIAL,
    [switch]$Fast,
    [bool]$ProbeSection5 = $true
)

$ErrorActionPreference = "Stop"
$script:tag = "PNS.SprintGuardrail"
$script:repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")

function Write-Log {
    param([string]$Message, [string]$Level = "INFO")
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    $color = switch ($Level) {
        "ERROR" { "Red" }
        "WARN"  { "Yellow" }
        "PASS"  { "Green" }
        default { "White" }
    }
    Write-Host "[$ts] [$Level] $Message" -ForegroundColor $color
}

function Invoke-VideoAudioPack {
    param([string]$DeviceSerial, [switch]$SkipBuild)
    
    Write-Log "Running VideoAudio pack..."
    $scriptPath = Join-Path $PSScriptRoot "pns_video_audio_verify.ps1"
    
    if (-not (Test-Path $scriptPath)) {
        throw "VideoAudio pack not found: $scriptPath"
    }
    
    $scriptArgs = @("-RecordSec", "5", "-RequireAudioTrack")
    if ($DeviceSerial) { $scriptArgs += @("-Serial", $DeviceSerial) }
    
    # The video audio script handles its own build, but -Fast means use existing APK
    # This script doesn't have a -Fast parameter, so we rely on it handling builds
    
    try {
        & $scriptPath @scriptArgs 2>&1 | ForEach-Object { Write-Log "VideoAudio: $_" }
        
        # Check for the gate file
        $hfrRuns = Join-Path $script:repoRoot "hfr-runs"
        $gateFiles = Get-ChildItem -Path $hfrRuns -Filter "video_audio_gate_*.json" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
        
        if ($gateFiles -and $gateFiles.Count -gt 0) {
            $latestGate = Get-Content $gateFiles[0].FullName | ConvertFrom-Json
            return @{
                pack = "VideoAudio"
                pass = $latestGate.pass
                details = $latestGate
                evidenceFile = $gateFiles[0].Name
            }
        } else {
            return @{
                pack = "VideoAudio"
                pass = $false
                error = "No gate file found"
            }
        }
    } catch {
        return @{
            pack = "VideoAudio"
            pass = $false
            error = $_.Exception.Message
        }
    }
}

function Invoke-CapturePipelinePack {
    param([string]$DeviceSerial, [switch]$SkipBuild)
    
    Write-Log "Running CapturePipeline pack..."
    $scriptPath = Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1"
    
    if (-not (Test-Path $scriptPath)) {
        throw "CapturePipeline pack not found: $scriptPath"
    }
    
    $scriptArgs = @()
    if ($DeviceSerial) { $scriptArgs += @("-Serial", $DeviceSerial) }
    if ($SkipBuild) { $scriptArgs += "-Fast" }
    
    try {
        & $scriptPath @scriptArgs 2>&1 | ForEach-Object { Write-Log "CapturePipeline: $_" }
        
        # Check for the gate file
        $hfrRuns = Join-Path $script:repoRoot "hfr-runs"
        $gateFiles = Get-ChildItem -Path $hfrRuns -Filter "capture_pipeline_gate_*.json" -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending
        
        if ($gateFiles -and $gateFiles.Count -gt 0) {
            $latestGate = Get-Content $gateFiles[0].FullName | ConvertFrom-Json
            return @{
                pack = "CapturePipeline"
                pass = ($latestGate.exitCode -eq 0)
                details = $latestGate
                evidenceFile = $gateFiles[0].Name
            }
        } else {
            return @{
                pack = "CapturePipeline"
                pass = $false
                error = "No gate file found"
            }
        }
    } catch {
        return @{
            pack = "CapturePipeline"
            pass = $false
            error = $_.Exception.Message
        }
    }
}

function Add-ProbeSection5Evidence {
    param([hashtable]$Results)
    
    $probeFile = Join-Path $script:repoRoot "PROBE_BUILD_PLAN.md"
    if (-not (Test-Path $probeFile)) {
        Write-Log "PROBE_BUILD_PLAN.md not found, skipping §5 evidence" "WARN"
        return
    }
    
    $timestamp = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    $deviceInfo = "unknown"
    
    # Try to get device info
    $adb = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if (-not $adb -and $env:LOCALAPPDATA) {
        $adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    }
    if ($adb -and (Test-Path $adb)) {
        $deviceArgs = if ($Serial) { @("-s", $Serial) } else { @() }
        $props = & $adb @deviceArgs shell "getprop ro.product.model; getprop ro.build.version.release" 2>&1
        if ($props -and $props.Count -ge 2) {
            $deviceInfo = "$($props[0].Trim()) Android $($props[1].Trim())"
        }
    }
    
    $evidenceLines = @(
        "",
        "### Sprint Guardrail — $timestamp",
        "",
        "**Device:** $deviceInfo  ",
        "**Pack:** $($Results.pack)  ",
        "**Result:** $(if ($Results.pass) { 'PASS ✓' } else { 'FAIL ✗' })  ",
        ""
    )
    
    if ($Results.details) {
        $evidenceLines += "**Details:**"
        $evidenceLines += "```json"
        $evidenceLines += ($Results.details | ConvertTo-Json -Depth 3)
        $evidenceLines += "```"
    }
    
    if ($Results.error) {
        $evidenceLines += "**Error:** $($Results.error)"
    }
    
    if ($Results.evidenceFile) {
        $evidenceLines += "**Evidence file:** $($Results.evidenceFile)"
    }
    
    $evidenceLines += ""
    
    # Append to §5 (after the "## 5. Sprint verification evidence" header)
    $content = Get-Content $probeFile -Raw
    $section5Marker = "## 5. Sprint verification evidence"
    
    if ($content -match $section5Marker) {
        $parts = $content -split $section5Marker, 2
        $newContent = $parts[0] + $section5Marker + ($evidenceLines -join "`n") + $parts[1]
        Set-Content -Path $probeFile -Value $newContent -NoNewline
        Write-Log "Evidence appended to PROBE_BUILD_PLAN.md §5"
    } else {
        Write-Log "Section 5 marker not found in PROBE_BUILD_PLAN.md" "WARN"
    }
}

# Main execution
Write-Log "=== Sprint Guardrail Orchestrator ==="
Write-Log "Pack: $Pack"
Write-Log "Repository: $script:repoRoot"

$results = @()
$overallPass = $true

switch ($Pack) {
    "VideoAudio" {
        $result = Invoke-VideoAudioPack -DeviceSerial $Serial -SkipBuild:$Fast
        $results += $result
        $overallPass = $overallPass -and $result.pass
    }
    "CapturePipeline" {
        $result = Invoke-CapturePipelinePack -DeviceSerial $Serial -SkipBuild:$Fast
        $results += $result
        $overallPass = $overallPass -and $result.pass
    }
    "All" {
        $result1 = Invoke-VideoAudioPack -DeviceSerial $Serial -SkipBuild:$Fast
        $results += $result1
        $overallPass = $overallPass -and $result1.pass
        
        $result2 = Invoke-CapturePipelinePack -DeviceSerial $Serial -SkipBuild:$Fast
        $results += $result2
        $overallPass = $overallPass -and $result2.pass
    }
}

# Write guardrail output
$hfrRuns = Join-Path $script:repoRoot "hfr-runs"
if (-not (Test-Path $hfrRuns)) {
    New-Item -ItemType Directory -Path $hfrRuns -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$guardrailFile = Join-Path $hfrRuns "sprint_guardrail_$timestamp.json"

$guardrailOutput = @{
    schema = "sprint_guardrail.v1"
    timestamp = Get-Date -Format "o"
    pack = $Pack
    overallPass = $overallPass
    results = $results
    deviceSerial = $Serial
}

$guardrailOutput | ConvertTo-Json -Depth 4 | Set-Content -Path $guardrailFile
Write-Log "Guardrail output: $guardrailFile"

# Add to PROBE_BUILD_PLAN.md §5
if ($ProbeSection5) {
    foreach ($result in $results) {
        Add-ProbeSection5Evidence -Results $result
    }
}

# Summary
Write-Log "=== Summary ==="
foreach ($result in $results) {
    $status = if ($result.pass) { "PASS" } else { "FAIL" }
    Write-Log "$($result.pack): $status" $(if ($result.pass) { "PASS" } else { "ERROR" })
}

if ($overallPass) {
    Write-Log "Overall: ALL PACKS PASSED ✓" "PASS"
    exit 0
} else {
    Write-Log "Overall: SOME PACKS FAILED ✗" "ERROR"
    exit 1
}
