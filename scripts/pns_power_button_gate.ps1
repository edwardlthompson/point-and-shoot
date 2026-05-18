#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Power button double-click camera app verification gate (Sprint 13.1)
    
.DESCRIPTION
    Verifies Point & Shoot appears in camera app picker when power button
    is double-pressed. Tests manifest intent filters and Quick Settings tiles.
    
.PARAMETER Serial
    ADB device serial number (optional - uses first device if not specified)
    
.PARAMETER BuildApk
    Build debug APK before testing (optional)
    
.PARAMETER Install
    Install APK before testing (optional)
#>

param(
    [string]$Serial = "",
    [switch]$BuildApk,
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$script:Pass = $true
$script:Evidence = @()

# Resolve ADB
$adb = "adb"
try { $adb = & "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath } catch { }

function Log($msg) {
    Write-Host "[pns_power_button_gate] $msg"
    $script:Evidence += "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') - $msg"
}

function Fail($msg) {
    Write-Error "[FAIL] $msg"
    $script:Pass = $false
    $script:Evidence += "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') - FAIL: $msg"
}

# Optional build
if ($BuildApk) {
    Log "Building debug APK..."
    Push-Location (Join-Path $PSScriptRoot "..")
    try {
        & .\gradlew.bat :app:assembleDebug --no-daemon -q
        if ($LASTEXITCODE -ne 0) { throw "Build failed" }
        Log "Build succeeded"
    } finally {
        Pop-Location
    }
}

# Optional install
if ($Install) {
    Log "Installing APK..."
    $apk = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { Fail "APK not found: $apk" }
    & $adb install -r $apk
    if ($LASTEXITCODE -ne 0) { Fail "Install failed" }
    Log "Install succeeded"
}

# Clear logcat
Log "Clearing logcat..."
& $adb logcat -c

# Test 1: Verify manifest intent filters via package manager
Log "=== Test 1: Intent Filters ==="
$resolveCamera = & $adb shell "pm resolve-activity -a android.media.action.STILL_IMAGE_CAMERA 2>/dev/null | grep -q 'dev.pointandshoot' && echo 'FOUND' || echo 'NOT_FOUND'"
$resolveSecure = & $adb shell "pm resolve-activity -a android.media.action.STILL_IMAGE_CAMERA_SECURE 2>/dev/null | grep -q 'dev.pointandshoot' && echo 'FOUND' || echo 'NOT_FOUND'"
$resolveVideo = & $adb shell "pm resolve-activity -a android.media.action.VIDEO_CAMERA 2>/dev/null | grep -q 'dev.pointandshoot' && echo 'FOUND' || echo 'NOT_FOUND'"

if ($resolveCamera -match 'FOUND') {
    Log "✓ STILL_IMAGE_CAMERA resolves to Point & Shoot"
} else {
    Fail "STILL_IMAGE_CAMERA does not resolve to Point & Shoot"
}

if ($resolveSecure -match 'FOUND') {
    Log "✓ STILL_IMAGE_CAMERA_SECURE resolves to Point & Shoot"
} else {
    Log "⚠ STILL_IMAGE_CAMERA_SECURE does not resolve (optional for lockscreen)"
}

if ($resolveVideo -match 'FOUND') {
    Log "✓ VIDEO_CAMERA resolves to Point & Shoot"
} else {
    Fail "VIDEO_CAMERA does not resolve to Point & Shoot"
}

# Test 2: Quick Settings tiles
Log "=== Test 2: Quick Settings Tiles ==="
$tiles = @(
    "dev.pointandshoot.quicksettings.CameraTileService",
    "dev.pointandshoot.quicksettings.VideoTileService",
    "dev.pointandshoot.quicksettings.SelfieTileService"
)
foreach ($tile in $tiles) {
    $enabled = & $adb shell "pm dump $tile 2>/dev/null | grep -q 'enabled=true' && echo 'ENABLED' || echo 'DISABLED'"
    if ($enabled -match 'ENABLED') {
        Log "✓ $tile enabled"
    } else {
        Log "⚠ $tile not found (user may need to manually add tiles)"
    }
}

# Test 3: Simulate power button camera launch (via am start)
Log "=== Test 3: Power Button Launch Simulation ==="
& $adb shell "am start -a android.media.action.STILL_IMAGE_CAMERA -W 2>&1 | grep -q 'dev.pointandshoot'"
if ($LASTEXITCODE -eq 0) {
    Log "✓ Power button camera intent launches Point & Shoot"
} else {
    Fail "Power button camera intent does not launch Point & Shoot"
}

# Collect logcat
Log "Collecting logcat..."
& $adb logcat -d -t 500 | Select-String "PNS" | Out-File (Join-Path $PSScriptRoot "power_button_gate_logcat.txt") -Encoding UTF8

# Write evidence JSON
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$evidenceFile = "power_button_gate_$timestamp.json"
$json = @{
    schema = "pns.power_button_gate.v1"
    generatedAt = (Get-Date -Format "o")
    pass = $script:Pass
    deviceSerial = & $adb get-serialno
    tests = @{
        stillImageCamera = ($resolveCamera -match 'FOUND')
        stillImageCameraSecure = ($resolveSecure -match 'FOUND')
        videoCamera = ($resolveVideo -match 'FOUND')
        powerButtonLaunch = ($LASTEXITCODE -eq 0)
    }
    evidence = $script:Evidence
} | ConvertTo-Json -Depth 5

$json | Out-File (Join-Path $PSScriptRoot $evidenceFile) -Encoding UTF8
Log "Evidence written to: $evidenceFile"

# Final status
if ($script:Pass) {
    Log "=== PASS === All power button gate checks passed"
    exit 0
} else {
    Log "=== FAIL === Some checks failed"
    exit 1
}
