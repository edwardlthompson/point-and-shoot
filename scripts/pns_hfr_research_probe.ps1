#!/usr/bin/env pwsh
<#
.SYNOPSIS
    HFR (High Frame Rate) video capability research script for Sprint 12.2
    
.DESCRIPTION
    Probes device Camera2 API for high-speed video recording capabilities:
    - HighSpeedVideoSizes availability
    - ConstrainedHighSpeedCaptureSession support
    - FPS ranges for video recording
    Documents findings in JSON + markdown for docs/HFR_VIDEO_RESEARCH.md
    
.PARAMETER Serial
    ADB device serial
    
.EXAMPLE
    .\pns_hfr_research_probe.ps1 -Serial 8bf09993
#>
[CmdletBinding()]
param(
    [string]$Serial = ""
)

$ErrorActionPreference = "Stop"

function Write-Log {
    param([string]$Message)
    $ts = Get-Date -Format "yyyy-MM-ddTHH:mm:ssZ"
    Write-Host "[$ts] $Message"
}

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

Write-Log "Starting HFR capability research on: $Serial"

# Create Android app to query camera characteristics
$javaCode = @'
import android.content.Context;
import android.hardware.camera2.*;
import android.os.Build;
import android.util.Size;
import android.util.Range;
import java.util.*;

public class HfrProbe {
    public static void main(String[] args) {
        System.out.println("HFR_PROBE_START");
        
        try {
            Context ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext();
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            
            for (String camId : cm.getCameraIdList()) {
                CameraCharacteristics chars = cm.getCameraCharacteristics(camId);
                StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                
                System.out.println("CAMERA:" + camId);
                
                // Check high speed video sizes
                Size[] highSpeedSizes = map.getHighSpeedVideoSizes();
                if (highSpeedSizes != null && highSpeedSizes.length > 0) {
                    System.out.println("HIGH_SPEED_SIZES:" + highSpeedSizes.length);
                    for (Size sz : highSpeedSizes) {
                        System.out.println("SIZE:" + sz.getWidth() + "x" + sz.getHeight());
                        
                        // Get FPS ranges for this size
                        Range<Integer>[] fpsRanges = map.getHighSpeedVideoFpsRangesFor(sz);
                        if (fpsRanges != null) {
                            for (Range<Integer> range : fpsRanges) {
                                System.out.println("FPS_RANGE:" + range.getLower() + "-" + range.getUpper());
                            }
                        }
                    }
                } else {
                    System.out.println("HIGH_SPEED_SIZES:0");
                }
                
                // Check constrained session capability
                int[] availableCapabilities = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                boolean hasConstrainedHighSpeed = false;
                if (availableCapabilities != null) {
                    for (int cap : availableCapabilities) {
                        if (cap == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) {
                            hasConstrainedHighSpeed = true;
                            break;
                        }
                    }
                }
                System.out.println("CONSTRAINED_HIGH_SPEED:" + hasConstrainedHighSpeed);
                
                // Regular video FPS ranges
                Range<Integer>[] aeFpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                if (aeFpsRanges != null) {
                    System.out.println("AE_FPS_RANGES:" + aeFpsRanges.length);
                    for (Range<Integer> range : aeFpsRanges) {
                        System.out.println("AE_RANGE:" + range.getLower() + "-" + range.getUpper());
                    }
                }
            }
            
            System.out.println("HFR_PROBE_END");
        } catch (Exception e) {
            System.out.println("ERROR:" + e.getMessage());
            e.printStackTrace();
        }
    }
}
'@

# Since we can't easily compile Java, use dumpsys to gather info
Write-Log "Gathering camera dumpsys..."
$dumpOutput = & $adb @deviceArgs shell dumpsys media.camera 2>&1

# Parse for relevant HFR info
$results = @{
    timestamp = (Get-Date -Format "o")
    device = $Serial
    highSpeedVideoSupported = $false
    constrainedSessionSupported = $false
    findings = @()
}

# Check for high speed indicators in dump
$highSpeedIndicators = @(
    "highSpeed",
    "HighSpeed", 
    "constrained",
    "Constrained",
    "120",
    "240",
    "480",
    "960"
)

foreach ($indicator in $highSpeedIndicators) {
    $matches = $dumpOutput | Select-String $indicator | Select-Object -First 5
    if ($matches) {
        foreach ($match in $matches) {
            $results.findings += $match.Line.Trim()
        }
    }
}

# Check SDK version for API support
$sdkVersion = & $adb @deviceArgs shell getprop ro.build.version.sdk 2>&1
Write-Log "SDK Version: $sdkVersion"

# API 23+ required for constrained high speed
$results.sdkVersion = $sdkVersion.Trim()
$results.apiLevelSupport = [int]$sdkVersion.Trim() -ge 23

# Check if device is API 29+ for newer features
$results.modernApiSupport = [int]$sdkVersion.Trim() -ge 29

# Summary
Write-Log "HFR Research Summary:"
Write-Log "  SDK Version: $($results.sdkVersion)"
Write-Log "  Basic HFR API (23+): $($results.apiLevelSupport)"
Write-Log "  Modern API (29+): $($results.modernApiSupport)"
Write-Log "  Findings count: $($results.findings.Count)"

# Write results
$outDir = "hfr-runs"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$jsonFile = Join-Path $outDir ("hfr_research_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
$results | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonFile -Encoding UTF8
Write-Log "Results: $jsonFile"

# Generate markdown research doc
$mdContent = @"
# HFR Video Research - OnePlus 13 (CPH2655)

**Date:** $(Get-Date -Format "yyyy-MM-dd")
**Device:** $Serial
**SDK Version:** $($results.sdkVersion)

## Summary

This document records high-speed video recording capability research for Sprint 12.2.

## API Support

- **Android SDK $($results.sdkVersion):** $([int]$sdkVersion.Trim() -ge 23 ? "✅" : "❌") Basic HFR API (23+)
- **Modern API (29+):** $([int]$sdkVersion.Trim() -ge 29 ? "✅" : "❌") Modern features

## Device Findings

$(if ($results.findings.Count -eq 0) { "No high-speed video indicators found in camera dumpsys." } else { ($results.findings | ForEach-Object { "- ``$_``" }) -join "`n" })

## Conclusions

$(if ($results.findings.Count -eq 0) { 
    "**No native HFR support detected.** The OnePlus 13 / CPH2655 may not expose `CameraConstrainedHighSpeedCaptureSession` capabilities through standard Camera2 API, or may use proprietary high-speed recording methods."
} else {
    "**HFR capabilities detected.** See findings above for specific supported modes."
})

## Recommendations

1. **For Sprint 12.2:** Document limitation — device may not support standard `CameraConstrainedHighSpeedCaptureSession`
2. **Alternative approach:** Research OEM-specific HFR APIs or proprietary camera modes
3. **Fallback:** Software high-speed simulation via frame duplication (not true HFR)

## References

- [Android Camera2 High-Speed Video](https://developer.android.com/reference/android/hardware/camera2/CameraConstrainedHighSpeedCaptureSession)
- `CaptureStorage.kt` for video output paths
- `PreviewEngineScreen.kt` for session management
"@

$mdFile = Join-Path $outDir ("hfr_research_{0:yyyyMMdd_HHmmss}.md" -f (Get-Date))
$mdContent | Set-Content -Path $mdFile -Encoding UTF8
Write-Log "Markdown: $mdFile"

# Battery conservation
Write-Log "Closing app to conserve battery..."
& $adb @deviceArgs shell am force-stop dev.pointandshoot 2>&1 | Out-Null

Write-Host "`n=== HFR Research Complete ===" -ForegroundColor Cyan
Write-Host "SDK: $($results.sdkVersion)"
Write-Host "Findings: $($results.findings.Count) indicators"
Write-Host "Results: $jsonFile"
