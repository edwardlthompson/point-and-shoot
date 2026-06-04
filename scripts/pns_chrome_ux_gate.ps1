# Milestone 9 - host + device chrome UX gate (ADB; optional root not required).
# - Runs scripts/pns_verify_toolchain.ps1 -RunTests (unless -SkipHost or -SkipHostTests)
# - When an authorized device is connected: install APK, grant CAMERA, cold-start preview,
#   capture logcat; assert PNS.ChromeUx seedOk..grid7x3 (incl. flash QS), flashPreviewHardware=, modeDialPopout=, readoutCapture=, selfTimerSec=
#   (device start uses --ei pns_preview_self_timer_sec 3 to exercise ADB seed + selfTimerSec=3)
# - Writes hfr-runs/.../chrome_ux_gate.json
#
# Prerequisites: same as pns_failure_matrix_smoke.ps1; optional scripts/pns_adb_device.env (PNS_ADB_SERIAL).

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [ValidateSet(0, 3, 5, 10)]
    [int]$SelfTimerSec = 3,
    [string]$FocalMmSlot = "85",
    [switch]$SkipInstall,
    [switch]$SkipGradle,
    [switch]$SkipHost,
    [switch]$SkipHostTests
)

$ErrorActionPreference = "Stop"

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\chrome_ux_gate_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($k -eq "PNS_ADB_SERIAL") {
            return $v
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[chrome_ux_gate] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs
    }
    else {
        & adb @CmdArgs
    }
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE"
    }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs 2>$null
    }
    else {
        & adb @CmdArgs 2>$null
    }
}

function Test-AdbAuthorizedDevice {
    # Mixed PATH adb vs SDK adb can print "server version doesn't match; killing..." to stderr;
    # with $ErrorActionPreference = Stop that becomes a terminating NativeCommandError even when adb exits 0.
    $prevEa = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $lines = @(adb devices 2>&1)
    }
    finally {
        $ErrorActionPreference = $prevEa
    }
    foreach ($line in $lines) {
        if ($line -match '\tdevice$') {
            return $true
        }
    }
    return $false
}

function Save-LogcatTail([string]$OutPath) {
    # Noisy OEM HALs can fill >12k lines in seconds  -  ring-tail-only drops early PNS.ChromeUx (seedOk, grid7x3, …).
    # Large mixed tail + tag-filtered ChromeUx supplement matches scripts/pns_adb_preview_validate.ps1 discipline.
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $mixedTail = 250000
        $tail = if ($Serial) {
            @(& adb -s $Serial logcat -d -t $mixedTail 2>&1)
        }
        else {
            @(& adb logcat -d -t $mixedTail 2>&1)
        }
        $tagLines = if ($Serial) {
            @(& adb -s $Serial logcat -d "*:S" "PNS.ChromeUx:I" "PNS.AdbValidation:I" 2>&1)
        }
        else {
            @(& adb logcat -d "*:S" "PNS.ChromeUx:I" "PNS.AdbValidation:I" 2>&1)
        }
        $sb = New-Object System.Text.StringBuilder
        foreach ($ln in $tail) { [void]$sb.AppendLine($ln) }
        [void]$sb.AppendLine("--- supplement: tag-filtered PNS.ChromeUx + PNS.AdbValidation ---")
        foreach ($ln in $tagLines) { [void]$sb.AppendLine($ln) }
        $utf8 = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllText($OutPath, $sb.ToString(), $utf8)
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

function Read-ChromeUxLogcatFiltered {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        $lines = if ($Serial) {
            @(& adb -s $Serial logcat -d "*:S" "PNS.ChromeUx:I" "PNS.AdbValidation:I" 2>&1)
        }
        else {
            @(& adb logcat -d "*:S" "PNS.ChromeUx:I" "PNS.AdbValidation:I" 2>&1)
        }
        return ($lines -join "`n")
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

# --- Host: toolchain + unit tests (Milestone 9.1) ---
$hostPass = $true
if (-not $SkipHost.IsPresent) {
    $verify = Join-Path $PSScriptRoot "pns_verify_toolchain.ps1"
    if (-not $SkipHostTests.IsPresent) {
        Write-Host "`[chrome_ux_gate] $verify -RunTests"
        & $verify -ProjectRoot $projRoot -RunTests
    }
    else {
        Write-Host "`[chrome_ux_gate] $verify (no -RunTests)"
        & $verify -ProjectRoot $projRoot
    }
    if (-not $?) {
        $hostPass = $false
    }
}
else {
    Write-Host "`[chrome_ux_gate] -SkipHost (no toolchain verify this run)"
}

$adbConnected = Test-AdbAuthorizedDevice
$seedOk = $false
$safeInsetsOk = $false
$dndPreviewOk = $false
$readoutOk = $false
$dualShutterOk = $false
$grid7Ok = $false
$modeDialPopoutOk = $false
$readoutCaptureOk = $false
$selfTimerOk = $false
$flashQsGrid7Ok = $false
$flashPreviewHardwareOk = $false
$expandModalHostOk = $false
$teleFocalSlotOk = $false
$deviceSkipReason = ""

if (-not $adbConnected) {
    $deviceSkipReason = "no_authorized_device"
    Write-Warning "[chrome_ux_gate] No authorized adb device - device checks skipped."
}

if ($adbConnected -and -not (Test-Path -LiteralPath $apk)) {
    if (-not $SkipGradle.IsPresent) {
        Write-Host "`[chrome_ux_gate] gradlew :app:assembleDebug"
        $gradlew = Join-Path $projRoot "gradlew.bat"
        Push-Location $projRoot
        try {
            & $gradlew ":app:assembleDebug" "--no-daemon"
            if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
        }
        finally {
            Pop-Location
        }
    }
    if (-not (Test-Path -LiteralPath $apk)) {
        $deviceSkipReason = "missing_apk"
        Write-Warning "[chrome_ux_gate] Missing $apk - cannot run device checks."
    }
}

if ($adbConnected -and (Test-Path -LiteralPath $apk) -and $deviceSkipReason -ne "missing_apk") {
    Write-Host "`[chrome_ux_gate] devices:"
    Invoke-Adb @("devices", "-l")
    if (-not $SkipInstall.IsPresent) {
        Write-Host "`[chrome_ux_gate] install -r -t $apk"
        Invoke-Adb @("install", "-r", "-t", $apk)
    }
    Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")
    Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_IMAGES")
    Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_VIDEO")

    $null = Invoke-AdbIgnore @("logcat", "-c")
    $null = Invoke-Adb @("shell", "am", "force-stop", $pkg)
    Start-Sleep -Milliseconds 600
    # Seed self-timer + optional focal mm slot (Milestone 9.13 tele preset ADB proof).
    $shellCmd = "am start -W -n ${pkg}/.MainActivity --es pns_screen preview --ei pns_preview_self_timer_sec $SelfTimerSec"
    if (-not [string]::IsNullOrWhiteSpace($FocalMmSlot)) {
        $slot = $FocalMmSlot.Trim()
        $shellCmd += " --es pns_preview_focal_mm_slot $slot"
    }
    if ($Serial) {
        & adb -s $Serial shell $shellCmd
    }
    else {
        & adb shell $shellCmd
    }
    if ($LASTEXITCODE -ne 0) {
        throw "adb shell am start failed exit=$LASTEXITCODE"
    }
    # Focal-slot ADB probe can take ~36s (canCaptureStill + post-switch settle); default chrome seed ~35s.
    $deviceWaitSec = if ([string]::IsNullOrWhiteSpace($FocalMmSlot)) { 35 } else { 52 }
    $collectedChromeUx = New-Object System.Text.StringBuilder
    $pollStepSec = 4
    $elapsedSec = 0
    while ($elapsedSec -lt $deviceWaitSec) {
        Start-Sleep -Seconds $pollStepSec
        $elapsedSec += $pollStepSec
        $snap = Read-ChromeUxLogcatFiltered
        if (-not [string]::IsNullOrWhiteSpace($snap)) {
            [void]$collectedChromeUx.AppendLine("--- poll t=${elapsedSec}s ---")
            [void]$collectedChromeUx.AppendLine($snap)
            $collectedText = $collectedChromeUx.ToString()
            $hasSeed = $collectedText -match 'PNS\.ChromeUx.*seedOk\s+slot=M23'
            $hasFocal = [string]::IsNullOrWhiteSpace($FocalMmSlot) -or ($collectedText -match 'PNS\.ChromeUx.*focalSlotTap=')
            if ($hasSeed -and $hasFocal) {
                break
            }
        }
    }

    $logPath = Join-Path $OutDir "logcat_chrome_seed.txt"
    Save-LogcatTail $logPath
    Write-Host "`[chrome_ux_gate] Wrote $logPath"

    $logText = [System.IO.File]::ReadAllText($logPath)
    $collectedText = $collectedChromeUx.ToString()
    if (-not [string]::IsNullOrWhiteSpace($collectedText)) {
        $logText += "`n--- collected chrome-ux poll ---`n$collectedText`n"
    }
    # Kotlin: Log.i("PNS.ChromeUx", "seedOk slot=M23 cameraId=...")
    if ($logText -match 'PNS\.ChromeUx.*seedOk\s+slot=M23') {
        $seedOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx seedOk slot=M23 in logcat (fallback path may have logged seedOk fallback)."
    }
    if ($logText -match 'PNS\.ChromeUx.*safeInsetsTopPx=\d+') {
        $safeInsetsOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx safeInsetsTopPx in logcat."
    }
    # Kotlin: Log.i("PNS.ChromeUx", "dndPreview=applied|skipped_no_policy|skipped_disabled|skipped_api|...")
    if ($logText -match 'PNS\.ChromeUx.*dndPreview=\w+') {
        $dndPreviewOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx dndPreview= in logcat."
    }
    # readout=live (metadata) or readout=fallback after ~10s when OEM omits keys in preview
    if ($logText -match 'PNS\.ChromeUx.*readout=(live|fallback)') {
        $readoutOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx readout=live|fallback in logcat."
    }
    if ($logText -match 'PNS\.ChromeUx.*(trayShutter=centerOfBar|trayShutter=centerColumn|trayShutter=singleCenter|dualShutter=visible|photoVideoToggle=combinedToggleFab|photoVideoToggle=modeFabStyle)') {
        $dualShutterOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx tray shutter line (centerOfBar|centerColumn|singleCenter|dualShutter=visible) in logcat."
    }
    # Sprint 10.13: grid7x3=layout (legacy APKs may still emit grid7=layout).
    if ($logText -match 'PNS\.ChromeUx.*grid7x3=layout' -or $logText -match 'PNS\.ChromeUx.*grid7=layout') {
        $grid7Ok = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx grid7x3=layout (or legacy grid7=layout) in logcat."
    }
    if ($logText -match 'PNS\.ChromeUx.*modeDialPopout=(anchorVisible|expanded|skipped_no_dial|menuSelect|menuSections)') {
        $modeDialPopoutOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx modeDialPopout= in logcat."
    }
    # Keep in sync with [PreviewReadoutStillPipeline.chromeUxLogValue] (DNG/RAW/JPEG/JXL/AVIF/HEIC/TIFF16 variants).
    if ($logText -match 'PNS\.ChromeUx.*readoutCapture=([A-Za-z0-9+_. -]+)') {
        $readoutCaptureOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx readoutCapture= in logcat."
    }
    if (-not $readoutOk -and $readoutCaptureOk -and $logText -match 'PNS\.ChromeUx.*statusBar=') {
        # Ring buffers can drop the periodic readout live/fallback line while preserving
        # readoutCapture + statusBar telemetry from the same session startup.
        $readoutOk = $true
        Write-Warning "[chrome_ux_gate] readout=live|fallback missing; using readoutCapture+statusBar fallback evidence."
    }
    # Cold-start self-timer pref log (default 0)
    if ($logText -match 'PNS\.ChromeUx.*selfTimerSec=\d+') {
        $selfTimerOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx selfTimerSec= in logcat."
    }
    if ($logText -match 'PNS\.ChromeUx.*quickActions=.*flash') {
        $flashQsGrid7Ok = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx quickActions=...flash... (Sprint 9.12 flash QS) in logcat."
    }
    if ($logText -match 'PNS\.ChromeUx.*flashPreviewHardware=(true|false)') {
        $flashPreviewHardwareOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx flashPreviewHardware=true|false in logcat."
    }
    if ($logText -match 'PNS\.ChromeUx.*expandShortcuts=surface=modalDialog') {
        $expandModalHostOk = $true
    }
    else {
        Write-Warning "[chrome_ux_gate] Did not find PNS.ChromeUx expandShortcuts=surface=modalDialog in logcat."
    }
    if ([string]::IsNullOrWhiteSpace($FocalMmSlot)) {
        $teleFocalSlotOk = $true
    }
    else {
        $slotNeedle = [regex]::Escape($FocalMmSlot.Trim())
        $lineMatch = [regex]::Match($logText, "PNS\.ChromeUx.*focalSlotTap=mm=$slotNeedle.*")
        $hasSkipped = [regex]::IsMatch($logText, "PNS\.ChromeUx.*focalSlotTap=mm=$slotNeedle.*(skipped=|session not ready)")
        if ($lineMatch.Success -and -not $hasSkipped) {
            $teleFocalSlotOk = $true
        }
        else {
            Write-Warning "[chrome_ux_gate] focal slot probe missing/invalid for slot=$FocalMmSlot (requires focalSlotTap mm match without skipped/session not ready)."
        }
    }
}

# Pass: host + device evidence required for fleet chrome gate.
$gatePass = $false
if ($deviceSkipReason -eq "missing_apk") {
    $gatePass = $false
}
elseif ($adbConnected -and (Test-Path -LiteralPath $apk) -and $deviceSkipReason -ne "missing_apk") {
    $gatePass = $hostPass -and $seedOk -and $safeInsetsOk -and $dndPreviewOk -and $readoutOk -and $dualShutterOk -and $grid7Ok -and $modeDialPopoutOk -and $readoutCaptureOk -and $selfTimerOk -and $flashQsGrid7Ok -and $flashPreviewHardwareOk -and $expandModalHostOk -and $teleFocalSlotOk
}

$obj = @{
    schema             = "pns.chrome_ux_gate.v1"
    pass               = $gatePass
    hostTestsPass      = $hostPass
    adbConnected       = $adbConnected
    seedOk             = $seedOk
    safeInsetsOk       = $safeInsetsOk
    dndPreviewOk       = $dndPreviewOk
    readoutOk          = $readoutOk
    dualShutterOk      = $dualShutterOk
    grid7Ok            = $grid7Ok
    modeDialPopoutOk   = $modeDialPopoutOk
    readoutCaptureOk   = $readoutCaptureOk
    selfTimerOk        = $selfTimerOk
    flashQsGrid7Ok     = $flashQsGrid7Ok
    flashPreviewHardwareOk = $flashPreviewHardwareOk
    expandModalHostOk  = $expandModalHostOk
    teleFocalSlotOk    = $teleFocalSlotOk
    focalMmSlotParam   = $(if ([string]::IsNullOrWhiteSpace($FocalMmSlot)) { "" } else { $FocalMmSlot.Trim() })
    deviceSkipReason   = $deviceSkipReason
    timestampUtc       = [DateTime]::UtcNow.ToString("o")
    outDir             = $OutDir
    serial             = $(if ($Serial) { $Serial } else { "default" })
}
$jsonPath = Join-Path $OutDir "chrome_ux_gate.json"
$obj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host "`[chrome_ux_gate] Wrote $jsonPath pass=$gatePass hostPass=$hostPass seedOk=$seedOk safeInsetsOk=$safeInsetsOk dndPreviewOk=$dndPreviewOk readoutOk=$readoutOk dualShutterOk=$dualShutterOk grid7Ok=$grid7Ok modeDialPopoutOk=$modeDialPopoutOk readoutCaptureOk=$readoutCaptureOk selfTimerOk=$selfTimerOk flashQsGrid7Ok=$flashQsGrid7Ok flashPreviewHardwareOk=$flashPreviewHardwareOk expandModalHostOk=$expandModalHostOk teleFocalSlotOk=$teleFocalSlotOk"
# §5 append: .\scripts\pns_probe_append_section5.ps1 -GateJson <path\to\chrome_ux_gate.json> [-PassOnly]

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)

if (-not $gatePass) {
    exit 1
}
exit 0
