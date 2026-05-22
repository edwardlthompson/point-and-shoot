# Gallery Features Verification Script
# Tests all gallery functionality including metadata, zoom, DNG handling, etc.

param(
    [string]$Serial = "8bf09993",
    [switch]$Continuous = $false
)

function Write-Section($title) {
    Write-Host "`n=== $title ===" -ForegroundColor Cyan
}

function Write-Test($name, $result) {
    $color = if ($result -eq "PASS") { "Green" } elseif ($result -eq "FAIL") { "Red" } else { "Yellow" }
    Write-Host "  $name : $result" -ForegroundColor $color
}

function Write-Info($message) {
    Write-Host "  $message" -ForegroundColor Gray
}

function Test-AppInstalled {
    Write-Section "App Installation Check"
    try {
        $result = adb -s $Serial shell pm list packages dev.pointandshoot
        if ($result -match "dev.pointandshoot") {
            Write-Test "App Installed" "PASS"
            return $true
        } else {
            Write-Test "App Installed" "FAIL"
            return $false
        }
    } catch {
        Write-Test "App Installed" "FAIL"
        return $false
    }
}

function Test-GalleryLaunch {
    Write-Section "Gallery Launch Test"
    try {
        # Launch app
        adb -s $Serial shell am start -n dev.pointandshoot/.MainActivity
        Start-Sleep 3
        
        # Try to open gallery (empty state should work now)
        adb -s $Serial shell input tap 950 1800  # Approx gallery icon position
        Start-Sleep 2
        
        # Check if gallery activity is in foreground
        $activity = adb -s $Serial shell dumpsys window windows | Select-String "mCurrentFocus"
        if ($activity -match "BespokeGalleryScreen|Gallery") {
            Write-Test "Gallery Opens" "PASS"
            return $true
        } else {
            Write-Test "Gallery Opens" "FAIL"
            Write-Info "Current focus: $activity"
            return $false
        }
    } catch {
        Write-Test "Gallery Opens" "FAIL"
        return $false
    }
}

function Test-MetadataExtraction {
    Write-Section "Metadata Extraction Test"
    try {
        # Clear logs and start monitoring
        adb -s $Serial logcat -c
        adb -s $Serial shell am start -n dev.pointandshoot/.MainActivity
        
        Write-Info "Take a test photo to check metadata extraction..."
        Write-Info "Press Enter when photo is taken"
        $null = Read-Host
        
        # Open gallery
        adb -s $Serial shell input tap 950 1800
        Start-Sleep 2
        
        # Get EXIF debug logs
        $logs = adb -s $Serial logcat -d -s BespokeGallery
        
        if ($logs -match "Focal length raw: [\d/]+") {
            Write-Test "Focal Length Extraction" "PASS"
        } else {
            Write-Test "Focal Length Extraction" "FAIL"
        }
        
        if ($logs -match "Color space tag: \d+") {
            Write-Test "Color Space Detection" "PASS"
        } else {
            Write-Test "Color Space Detection" "FAIL"
        }
        
        if ($logs -match "Make: \w+") {
            Write-Test "Camera Make Detection" "PASS"
        } else {
            Write-Test "Camera Make Detection" "FAIL"
        }
        
        if ($logs -match "F-Number: f[\d/.]+") {
            Write-Test "Aperture Extraction" "PASS"
        } else {
            Write-Test "Aperture Extraction" "FAIL"
        }
        
        if ($logs -match "ISO: \d+") {
            Write-Test "ISO Extraction" "PASS"
        } else {
            Write-Test "ISO Extraction" "FAIL"
        }
        
        Write-Info "EXIF Debug Logs:"
        $logs | ForEach-Object { Write-Info "  $_" }
        
    } catch {
        Write-Test "Metadata Extraction" "FAIL"
        Write-Info "Error: $_"
    }
}

function Test-DNGColorSpace {
    Write-Section "DNG Color Space Test"
    try {
        Write-Info "Take a DNG photo to test color space..."
        Write-Info "Press Enter when DNG photo is taken"
        $null = Read-Host
        
        # Open gallery
        adb -s $Serial shell input tap 950 1800
        Start-Sleep 2
        
        # Check logs for DNG color space
        $logs = adb -s $Serial logcat -d -s BespokeGallery
        
        if ($logs -match "ProPhoto RGB") {
            Write-Test "DNG Color Space (ProPhoto RGB)" "PASS"
        } else {
            Write-Test "DNG Color Space (ProPhoto RGB)" "FAIL"
            Write-Info "Looking for 'ProPhoto RGB' in logs"
        }
        
        if ($logs -match "isDNG: true") {
            Write-Test "DNG File Detection" "PASS"
        } else {
            Write-Test "DNG File Detection" "FAIL"
        }
        
    } catch {
        Write-Test "DNG Color Space" "FAIL"
        Write-Info "Error: $_"
    }
}

function Test-ZoomFunctionality {
    Write-Section "Zoom Functionality Test"
    try {
        Write-Info "Testing zoom controls..."
        Write-Info "Take a photo and open gallery, then test zoom buttons"
        Write-Info "Press Enter when ready to test"
        $null = Read-Host
        
        # Open gallery
        adb -s $Serial shell input tap 950 1800
        Start-Sleep 2
        
        Write-Info "Test zoom by clicking + and - buttons on screen"
        Write-Info "Press Enter when zoom test complete"
        $null = Read-Host
        
        Write-Test "Zoom Controls Available" "MANUAL"
        Write-Info "Please verify zoom buttons work in gallery"
        
    } catch {
        Write-Test "Zoom Functionality" "FAIL"
        Write-Info "Error: $_"
    }
}

function Test-GalleryPersistence {
    Write-Section "Gallery Persistence Test"
    try {
        # Force close app
        adb -s $Serial shell am force-stop dev.pointandshoot
        Start-Sleep 2
        
        # Launch app
        adb -s $Serial shell am start -n dev.pointandshoot/.MainActivity
        Start-Sleep 3
        
        Write-Info "Check if gallery thumbnail shows previous media"
        Write-Test "Gallery Persistence" "MANUAL"
        Write-Info "Please verify thumbnail shows last taken photo"
        
    } catch {
        Write-Test "Gallery Persistence" "FAIL"
        Write-Info "Error: $_"
    }
}

function Test-SwipeNavigation {
    Write-Section "Swipe Navigation Test"
    try {
        Write-Info "Testing swipe navigation between photos..."
        Write-Info "Take multiple photos and test swipe in gallery"
        Write-Info "Press Enter when ready to test"
        $null = Read-Host
        
        # Open gallery
        adb -s $Serial shell input tap 950 1800
        Start-Sleep 2
        
        Write-Info "Test swipe left/right to navigate between photos"
        Write-Info "Press Enter when swipe test complete"
        $null = Read-Host
        
        Write-Test "Swipe Navigation" "MANUAL"
        Write-Info "Please verify swipe navigation works"
        
    } catch {
        Write-Test "Swipe Navigation" "FAIL"
        Write-Info "Error: $_"
    }
}

# Main execution
Write-Host "Point & Shoot Gallery Features Verification" -ForegroundColor Magenta
Write-Host "Device: $Serial" -ForegroundColor Gray

if (-not (Test-AppInstalled)) {
    Write-Host "App not installed. Exiting." -ForegroundColor Red
    exit 1
}

# Run all tests
Test-GalleryLaunch
Test-MetadataExtraction
Test-DNGColorSpace
Test-ZoomFunctionality
Test-GalleryPersistence
Test-SwipeNavigation

Write-Section "Test Summary"
Write-Host "Verification complete. Check results above." -ForegroundColor Green
Write-Host "For detailed logs: adb -s $Serial logcat -s BespokeGallery" -ForegroundColor Gray

if ($Continuous) {
    Write-Host "Continuous monitoring enabled. Press Ctrl+C to stop." -ForegroundColor Yellow
    while ($true) {
        Start-Sleep 30
        Write-Host "$(Get-Date): Monitoring gallery functionality..." -ForegroundColor Gray
        $logs = adb -s $Serial logcat -d -s BespokeGallery
        if ($logs -match "ProPhoto RGB") {
            Write-Host "$(Get-Date): DNG ProPhoto RGB detected!" -ForegroundColor Green
        }
    }
}
