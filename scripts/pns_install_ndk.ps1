# Point & Shoot - non-interactive NDK + CMake installer (host-side helper).
#
# This script is intentionally idempotent and human-triggered. **assembleDebug**
# now invokes CMake via **`externalNativeBuild`** and links **`libpns_native.so`**
# (JNI stubs). Gradle can auto-install the pinned NDK side-by-side if licensed;
# use this script for CI hosts / offline parity or to install CMake alongside.
# Real libavif/libjxl encode bodies remain a CMake FetchContent follow-on.
#
# What it does:
# 1. Locates the Android SDK (prefers $env:ANDROID_HOME, falls back to
#    %LOCALAPPDATA%\Android\Sdk on Windows).
# 2. Finds `sdkmanager.bat` under cmdline-tools. If absent, prints exact
#    instructions for installing cmdline-tools via Android Studio's SDK
#    Manager UI (or via a future bootstrap step) and exits 1.
# 3. Runs `sdkmanager --install "ndk;<NdkVersion>" "cmake;<CmakeVersion>"`
#    with `--licenses` accepted automatically (driven by stdin pipe to handle
#    the EULA prompts non-interactively).
# 4. Verifies the install by checking the resolved paths.
# 5. Prints the env vars the user needs to set so Gradle picks up the new
#    toolchain (`ANDROID_NDK_HOME`, `ANDROID_NDK_ROOT`).
#
# Usage:
#   .\scripts\pns_install_ndk.ps1
#   .\scripts\pns_install_ndk.ps1 -NdkVersion 26.3.11579264
#   .\scripts\pns_install_ndk.ps1 -CmakeVersion 3.22.1
#   .\scripts\pns_install_ndk.ps1 -DryRun
#
# Returns:
#   0 - install succeeded (or already present); env vars printed.
#   1 - prerequisites missing; manual action documented.
#   2 - sdkmanager invocation failed.

param(
    [string]$NdkVersion = "26.3.11579264",
    [string]$CmakeVersion = "3.22.1",
    [string]$AndroidHome = "",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$msg) {
    Write-Host "`[install-ndk] $msg"
}

function Resolve-AndroidSdk {
    if (-not [string]::IsNullOrWhiteSpace($AndroidHome) -and (Test-Path $AndroidHome)) {
        return $AndroidHome
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_HOME) -and (Test-Path $env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }
    if (-not [string]::IsNullOrWhiteSpace($env:ANDROID_SDK_ROOT) -and (Test-Path $env:ANDROID_SDK_ROOT)) {
        return $env:ANDROID_SDK_ROOT
    }
    $local = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $local) {
        return $local
    }
    return $null
}

function Find-Sdkmanager([string]$sdk) {
    $candidates = @(
        "$sdk\cmdline-tools\latest\bin\sdkmanager.bat",
        "$sdk\cmdline-tools\bin\sdkmanager.bat",
        "$sdk\tools\bin\sdkmanager.bat"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) { return $c }
    }
    # Fall back to a shallow scan in case of an off-version layout.
    $cmdline = "$sdk\cmdline-tools"
    if (Test-Path $cmdline) {
        $direct = Get-ChildItem -Path $cmdline -Filter "sdkmanager.bat" -Recurse -Depth 3 -ErrorAction SilentlyContinue |
            Select-Object -First 1
        if ($direct) { return $direct.FullName }
    }
    return $null
}

Write-Step "Point & Shoot NDK installer (target: ndk;$NdkVersion + cmake;$CmakeVersion)"

$sdk = Resolve-AndroidSdk
if ([string]::IsNullOrWhiteSpace($sdk)) {
    Write-Host ""
    Write-Host "ERROR: Could not locate the Android SDK." -ForegroundColor Red
    Write-Host "Set `$env:ANDROID_HOME or pass -AndroidHome <path>." -ForegroundColor Red
    exit 1
}
Write-Step "Android SDK: $sdk"

$existingNdk = Join-Path $sdk "ndk\$NdkVersion"
$existingCmake = Join-Path $sdk "cmake\$CmakeVersion"
$ndkPresent = Test-Path $existingNdk
$cmakePresent = Test-Path $existingCmake

if ($ndkPresent -and $cmakePresent) {
    Write-Step "NDK already present: $existingNdk"
    Write-Step "CMake already present: $existingCmake"
    Write-Host ""
    Write-Host "To use this toolchain set:" -ForegroundColor Green
    Write-Host "  `$env:ANDROID_NDK_HOME = '$existingNdk'" -ForegroundColor Green
    Write-Host "  `$env:ANDROID_NDK_ROOT = '$existingNdk'" -ForegroundColor Green
    exit 0
}

$sdkmgr = Find-Sdkmanager $sdk
if ([string]::IsNullOrWhiteSpace($sdkmgr)) {
    Write-Host ""
    Write-Host "ERROR: sdkmanager.bat not found under '$sdk'." -ForegroundColor Red
    Write-Host ""
    Write-Host "Install Android Studio command-line tools first:" -ForegroundColor Yellow
    Write-Host "  1. Open Android Studio -> Settings -> Languages & Frameworks -> Android SDK." -ForegroundColor Yellow
    Write-Host "  2. SDK Tools tab -> check 'Android SDK Command-line Tools (latest)'." -ForegroundColor Yellow
    Write-Host "  3. Apply / OK." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Then re-run this script." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Alternative: download cmdline-tools.zip from" -ForegroundColor Yellow
    Write-Host "  https://developer.android.com/studio#command-line-tools-only" -ForegroundColor Yellow
    Write-Host "and extract to '$sdk\cmdline-tools\latest\'." -ForegroundColor Yellow
    exit 1
}
Write-Step "sdkmanager: $sdkmgr"

$packages = @()
if (-not $ndkPresent) { $packages += "ndk;$NdkVersion" }
if (-not $cmakePresent) { $packages += "cmake;$CmakeVersion" }

if ($packages.Count -eq 0) {
    Write-Step "Nothing to install."
    exit 0
}

Write-Step "Will install: $($packages -join ', ')"

if ($DryRun) {
    Write-Step "DryRun = `$true; not installing. Command would be:"
    Write-Host "  & '$sdkmgr' --install $($packages -join ' ')" -ForegroundColor Cyan
    exit 0
}

# Accept all licenses non-interactively. The `y\n` stream feeds 256 'y'
# answers which is enough for any reasonable batch of new SDK packages.
Write-Step "Accepting licenses..."
$yPipe = ('y' * 256 -split '' | Where-Object { $_ }) -join "`n"
& cmd /c "echo $yPipe | `"$sdkmgr`" --licenses" 2>&1 | Out-Null

Write-Step "Installing $($packages -join ', ') ..."
$installArgs = @("--install") + $packages
& "$sdkmgr" @installArgs
$rc = $LASTEXITCODE
if ($rc -ne 0) {
    Write-Host "ERROR: sdkmanager exited with code $rc" -ForegroundColor Red
    exit 2
}

$ndkPath = Join-Path $sdk "ndk\$NdkVersion"
$cmakePath = Join-Path $sdk "cmake\$CmakeVersion"
if (-not (Test-Path $ndkPath)) {
    Write-Host "ERROR: Expected NDK at '$ndkPath' was not created." -ForegroundColor Red
    exit 2
}
if (-not (Test-Path $cmakePath)) {
    Write-Host "ERROR: Expected CMake at '$cmakePath' was not created." -ForegroundColor Red
    exit 2
}

Write-Step "Installed NDK: $ndkPath"
Write-Step "Installed CMake: $cmakePath"

Write-Host ""
Write-Host "Add these to your shell profile (or this PowerShell session):" -ForegroundColor Green
Write-Host "  `$env:ANDROID_NDK_HOME = '$ndkPath'" -ForegroundColor Green
Write-Host "  `$env:ANDROID_NDK_ROOT = '$ndkPath'" -ForegroundColor Green
Write-Host ""
Write-Host "Then turn on the externalNativeBuild block in app/build.gradle.kts" -ForegroundColor Green
Write-Host "(see NDK_PLAN.md 'Gradle / CMake wiring (target)')." -ForegroundColor Green

exit 0
