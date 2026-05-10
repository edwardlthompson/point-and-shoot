# BUILD_PLAN Milestone 7.2 - lightweight ADB "failure matrix" smoke.
# - Installs app-debug.apk (unless -SkipInstall)
# - Scenario A: CAMERA granted, cold-start preview, capture logcat, assert no FATAL for this package
# - Scenario B: revoke CAMERA, cold-start preview, assert no FATAL (graceful handling per FAILURE_MATRIX.md)
# - Restores CAMERA grant
# Writes failure_matrix_smoke.json under -OutDir.
#
# If no authorized device: writes JSON with adbConnected=false and exits 0 (host-friendly).
# Use scripts/pns_adb_device.env (PNS_ADB_SERIAL) or -Serial, same as pns_adb_preview_validate.ps1.

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\failure_matrix_smoke_$utc"
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
        Write-Host "[failure_matrix_smoke] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
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

if ($Serial -match '^\d+\.\d+\.\d+\.\d+:\d+$') {
    Write-Host "[failure_matrix_smoke] adb connect $Serial (TCP/IP)"
    Invoke-AdbIgnore @("connect", $Serial)
}

function Test-AdbAuthorizedDevice {
    $lines = @(adb devices 2>&1)
    foreach ($line in $lines) {
        if ($line -match '\tdevice$') {
            return $true
        }
    }
    return $false
}

function Save-LogcatTail([string]$OutPath) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        # Keep this modest: huge `-t` transfers over TCP/IP adb can stall for many minutes.
        $tail = if ($Serial) {
            @(& adb -s $Serial shell "logcat -d -t 25000" 2>&1)
        }
        else {
            @(adb shell "logcat -d -t 25000" 2>&1)
        }
        $utf8 = New-Object System.Text.UTF8Encoding $false
        [System.IO.File]::WriteAllLines($OutPath, $tail, $utf8)
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

function Test-NoFatalForPackage([string]$LogPath, [string]$PackageName) {
    if (-not (Test-Path -LiteralPath $LogPath)) {
        return $false
    }
    $text = [System.IO.File]::ReadAllText($LogPath)
    # Typical crash block: AndroidRuntime FATAL EXCEPTION ... Process: <pkg>
    if ($text -match '(?ms)FATAL EXCEPTION:.*?Process:\s+' + [regex]::Escape($PackageName)) {
        return $false
    }
    # Alternative ordering on some builds
    if ($text -match '(?ms)Process:\s+' + [regex]::Escape($PackageName) + '.*?FATAL EXCEPTION:') {
        return $false
    }
    return $true
}

function Run-Scenario([string]$Name, [int]$WaitSec, [string[]]$AmExtraArgs) {
    Write-Host ""
    Write-Host "=== failure_matrix_smoke: $Name (${WaitSec}s) ==="
    $null = Invoke-AdbIgnore @("logcat", "-c")
    $null = Invoke-Adb @("shell", "am", "force-stop", $pkg)
    Start-Sleep -Milliseconds 600
    # Avoid `am start -W` on Wi-Fi adb (can block). Use one shell string — on Windows, splitting
    # `adb shell am …` into many argv tokens sometimes wedges the transport until timeout.
    $extraFlat = ($AmExtraArgs | ForEach-Object { "$_" }) -join " "
    $shellCmd = "am start -n ${pkg}/.MainActivity $extraFlat"
    # adb prints "Starting: Intent …" to stdout; swallow so Run-Scenario returns only $logPath.
    $null = Invoke-Adb @("shell", $shellCmd)
    Start-Sleep -Seconds $WaitSec
    $logPath = Join-Path $OutDir "logcat_$Name.txt"
    Save-LogcatTail $logPath
    Write-Host "Wrote $logPath"
    return $logPath
}

if (-not (Test-AdbAuthorizedDevice)) {
    Write-Warning "[failure_matrix_smoke] No authorized adb device - skip scenarios."
    $stub = @{
        adbConnected        = $false
        pass                = $false
        skippedReason       = "no_authorized_device"
        previewGrantedOk    = $false
        previewRevokedOk    = $false
        timestampUtc        = [DateTime]::UtcNow.ToString("o")
        outDir              = $OutDir
    }
    $stub | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "failure_matrix_smoke.json") -Encoding utf8
    Write-Host "[failure_matrix_smoke] Wrote failure_matrix_smoke.json (stub)"
    exit 0
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk - run .\gradlew.bat :app:assembleDebug first."
}

Write-Host "[failure_matrix_smoke] devices:"
Invoke-Adb @("devices", "-l")

if (-not $SkipInstall.IsPresent) {
    Write-Host "[failure_matrix_smoke] install $apk"
    Invoke-Adb @("install", "-r", $apk)
}

function Grant-Camera {
    Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")
}

function Revoke-Camera {
    Invoke-AdbIgnore @("shell", "pm", "revoke", $pkg, "android.permission.CAMERA")
}

Grant-Camera
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_IMAGES")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_VIDEO")

$previewExtras = @("--es", "pns_screen", "preview")

$pathGranted = Run-Scenario "fm_preview_granted" 42 $previewExtras
$okGranted = Test-NoFatalForPackage $pathGranted $pkg

Revoke-Camera
Start-Sleep -Milliseconds 400
$pathRevoked = Run-Scenario "fm_preview_revoked" 28 $previewExtras
$okRevoked = Test-NoFatalForPackage $pathRevoked $pkg

Grant-Camera

$pass = $okGranted -and $okRevoked
$obj = @{
    adbConnected     = $true
    pass             = $pass
    previewGrantedOk = $okGranted
    previewRevokedOk = $okRevoked
    timestampUtc     = [DateTime]::UtcNow.ToString("o")
    outDir           = $OutDir
    serial           = $(if ($Serial) { $Serial } else { "default" })
}
$jsonPath = Join-Path $OutDir "failure_matrix_smoke.json"
$obj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host "[failure_matrix_smoke] Wrote $jsonPath pass=$pass"

if (-not $pass) {
    Write-Host "[failure_matrix_smoke] FAIL: fatal detected or log parse failed"
    exit 1
}
exit 0
