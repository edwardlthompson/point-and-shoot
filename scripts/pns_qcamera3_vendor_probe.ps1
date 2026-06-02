#!/usr/bin/env pwsh
<#
.SYNOPSIS
    QCamera3 vendor key deep probe for HFR and 10-bit video research

.DESCRIPTION
    Probes device Camera2 API for Qualcomm QCamera3 vendor keys:
    - Extracts all org.codeaurora.qcamera3.* keys
    - Categorizes by type: session, request, result, characteristic
    - Documents key value types and expected ranges
    - Creates QCamera3 key catalog for legacy device

.PARAMETER Serial
    ADB device serial

.EXAMPLE
    .\pns_qcamera3_vendor_probe.ps1 -Serial <serial>
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

Write-Log "Starting QCamera3 vendor key probe on: $Serial"

# Use the app's existing probe mechanism
# Launch the app with autoDeepCaps flag to run the deep capabilities probe
Write-Log "Launching app with auto deep caps probe..."

# First, ensure app is installed
$pkg = "dev.pointandshoot"
$checkInstalled = & $adb @deviceArgs shell pm list packages $pkg 2>&1
if (-not ($checkInstalled -match $pkg)) {
    Write-Log "App not installed, building and installing..."
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    & ".\gradlew.bat" :app:assembleDebug 2>&1 | Out-Null
    & $adb @deviceArgs install -r app\build\outputs\apk\debug\app-debug.apk 2>&1 | Out-Null
}

# Launch app with autoDeepCaps flag
Write-Log "Launching CameraCapabilitiesProbe with autoDeepCaps..."
& $adb @deviceArgs shell am start -n "$pkg/.MainActivity" --ez pns_auto_deep_caps true 2>&1 | Out-Null

# Wait for probe to complete (give it 30 seconds)
Write-Log "Waiting for probe to complete (30 seconds)..."
Start-Sleep -Seconds 30

# Pull the probe output
Write-Log "Pulling probe output..."
$outDir = "hfr-runs"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

# The app writes probe output to external files
$probeDir = "/storage/emulated/0/Android/data/$pkg/files"
& $adb @deviceArgs shell ls -la "$probeDir" 2>&1 | Out-Null

# Try to pull JSON probe output
$jsonFiles = @()
$pullOutput = & $adb @deviceArgs shell ls "$probeDir" 2>&1 | Select-String "\.json$"
if ($pullOutput) {
    foreach ($line in $pullOutput) {
        $fileName = ($line -split '\s+')[-1]
        $localFile = Join-Path $outDir $fileName
        & $adb @deviceArgs pull "$probeDir/$fileName" $localFile 2>&1 | Out-Null
        if (Test-Path $localFile) {
            $jsonFiles += $localFile
            Write-Log "Pulled: $fileName"
        }
    }
}

# If no JSON files, use dumpsys as fallback
if ($jsonFiles.Count -eq 0) {
    Write-Log "No JSON probe output found, using dumpsys fallback..."
    $dumpOutput = & $adb @deviceArgs shell dumpsys media.camera 2>&1
    
    # Parse dumpsys for QCamera3 vendor keys
    $qcamera3Keys = @()
    $lines = $dumpOutput -split "`n"
    foreach ($line in $lines) {
        if ($line -match "org\.codeaurora\.qcamera3") {
            $qcamera3Keys += $line.Trim()
        }
    }
    
    $results = @{
        timestamp = (Get-Date -Format "o")
        device = $Serial
        method = "dumpsys_fallback"
        qcamera3Keys = $qcamera3Keys
        sessionKeys = @()
        requestKeys = @()
        resultKeys = @()
        charKeys = @()
    }
    
    # Categorize keys
    foreach ($key in $qcamera3Keys) {
        if ($key -match "sessionParameters") { $results.sessionKeys += $key }
        elseif ($key -match "request") { $results.requestKeys += $key }
        elseif ($key -match "result") { $results.resultKeys += $key }
        else { $results.charKeys += $key }
    }
    
    $jsonFile = Join-Path $outDir ("qcamera3_probe_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
    $results | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonFile -Encoding UTF8
    Write-Log "Results: $jsonFile"
    $jsonFiles += $jsonFile
} else {
    # Parse JSON files for QCamera3 keys
    $allQCamera3Keys = @{
        sessionKeys = @()
        requestKeys = @()
        resultKeys = @()
        charKeys = @()
    }
    
    foreach ($jsonFile in $jsonFiles) {
        Write-Log "Parsing: $jsonFile"
        $json = Get-Content $jsonFile | ConvertFrom-Json
        
        # Extract QCamera3 keys from JSON structure
        # This depends on the actual probe output structure
        if ($json.cameras) {
            foreach ($cam in $json.cameras) {
                if ($cam.sessionKeys) {
                    foreach ($key in $cam.sessionKeys) {
                        if ($key.name -match "org\.codeaurora\.qcamera3") {
                            $allQCamera3Keys.sessionKeys += $key
                        }
                    }
                }
                if ($cam.requestKeys) {
                    foreach ($key in $cam.requestKeys) {
                        if ($key.name -match "org\.codeaurora\.qcamera3") {
                            $allQCamera3Keys.requestKeys += $key
                        }
                    }
                }
                if ($cam.resultKeys) {
                    foreach ($key in $cam.resultKeys) {
                        if ($key.name -match "org\.codeaurora\.qcamera3") {
                            $allQCamera3Keys.resultKeys += $key
                        }
                    }
                }
                if ($cam.charKeys) {
                    foreach ($key in $cam.charKeys) {
                        if ($key.name -match "org\.codeaurora\.qcamera3") {
                            $allQCamera3Keys.charKeys += $key
                        }
                    }
                }
            }
        }
    }
    
    $results = @{
        timestamp = (Get-Date -Format "o")
        device = $Serial
        method = "app_probe"
        sessionKeys = $allQCamera3Keys.sessionKeys
        requestKeys = $allQCamera3Keys.requestKeys
        resultKeys = $allQCamera3Keys.resultKeys
        charKeys = $allQCamera3Keys.charKeys
    }
    
    $jsonFile = Join-Path $outDir ("qcamera3_probe_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
    $results | ConvertTo-Json -Depth 5 | Set-Content -Path $jsonFile -Encoding UTF8
    Write-Log "Consolidated results: $jsonFile"
}

# Generate markdown catalog
$mdContent = @"
# QCamera3 Vendor Key Catalog - legacy device (legacy SKU)

**Date:** $(Get-Date -Format "yyyy-MM-dd")
**Device:** $Serial
**Probe Method:** $(if ($jsonFiles.Count -gt 0) { "App Probe" } else { "Dumpsys Fallback" })

## Summary

This document catalogs all Qualcomm QCamera3 vendor keys discovered on the legacy device device for HFR and 10-bit video research.

## Session Parameters

$(if ($results.sessionKeys.Count -eq 0) { "No session parameters found." } else { ($results.sessionKeys | ForEach-Object { "- ``$_``" }) -join "`n" })

## Request Parameters

$(if ($results.requestKeys.Count -eq 0) { "No request parameters found." } else { ($results.requestKeys | ForEach-Object { "- ``$_``" }) -join "`n" })

## Result Parameters

$(if ($results.resultKeys.Count -eq 0) { "No result parameters found." } else { ($results.resultKeys | ForEach-Object { "- ``$_``" }) -join "`n" })

## Characteristic Parameters

$(if ($results.charKeys.Count -eq 0) { "No characteristic parameters found." } else { ($results.charKeys | ForEach-Object { "- ``$_``" }) -join "`n" })

## Total Count

- Session Keys: $($results.sessionKeys.Count)
- Request Keys: $($results.requestKeys.Count)
- Result Keys: $($results.resultKeys.Count)
- Characteristic Keys: $($results.charKeys.Count)
- **Total:** $($results.sessionKeys.Count + $results.requestKeys.Count + $results.resultKeys.Count + $results.charKeys.Count)

## Next Steps

1. Test setting discovered session parameters via SessionConfiguration.setSessionParameters (API 33+)
2. Test setting discovered request keys on video recording CaptureRequest.Builder
3. Monitor result keys to verify vendor key acceptance
4. Document which keys enable HFR and 10-bit video modes

## References

- VendorKeyGuard.kt for vendor key infrastructure
- PreviewEngineScreen.kt for session parameter usage examples
- BUILD_PLAN.md Milestone 13 for HFR and 10-bit video requirements
"@

$mdFile = Join-Path $outDir ("qcamera3_catalog_{0:yyyyMMdd_HHmmss}.md" -f (Get-Date))
$mdContent | Set-Content -Path $mdFile -Encoding UTF8
Write-Log "Catalog: $mdFile"

# Battery conservation
Write-Log "Closing app to conserve battery..."
& $adb @deviceArgs shell am force-stop $pkg 2>&1 | Out-Null

Write-Host "`n=== QCamera3 Vendor Key Probe Complete ===" -ForegroundColor Cyan
Write-Host "Session Keys: $($results.sessionKeys.Count)"
Write-Host "Request Keys: $($results.requestKeys.Count)"
Write-Host "Result Keys: $($results.resultKeys.Count)"
Write-Host "Characteristic Keys: $($results.charKeys.Count)"
Write-Host "Total: $($results.sessionKeys.Count + $results.requestKeys.Count + $results.resultKeys.Count + $results.charKeys.Count)"
Write-Host "JSON: $jsonFile"
Write-Host "Catalog: $mdFile"
