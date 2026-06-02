<#
.SYNOPSIS
  Live ADB forensics while ReferenceCam captures RAW/DNG on UW / wide / tele (legacy SKU-class).

.DESCRIPTION
  - Streams logcat to host (Camera2, HAL, DngCreator, ReferenceCam process).
  - Polls dumpsys media.camera for CONNECT/DISCONNECT (leaf camera id per lens).
  - Detects new ReferenceCam DNGs under /sdcard/DCIM (timestamp files, not Point & Shoot).
  - Optional -TryUiAutomation: tap lens row + shutter (1440x3168 legacy device; calibrate with -Calibrate).
  - Default: supervised — you switch lens + shoot; script records timing and pulls artifacts.

.EXAMPLE
  .\scripts\pns_proshot_live_forensics.ps1 -Serial <serial>
  .\scripts\pns_proshot_live_forensics.ps1 -TryUiAutomation -PerLensSec 12
  .\scripts\pns_proshot_live_forensics.ps1 -ManualOnly -PerLensSec 25
#>
param(
    [string]$Serial = "",
    [int]$PerLensSec = 20,
    [switch]$TryUiAutomation,
    [switch]$ManualOnly,
    [switch]$Calibrate,
    [string]$ProShotPackage = "com.riseupgames.proshot2"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") {
            return $t.Substring($eq + 1).Trim()
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\proshot_live_forensics_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
}

function Invoke-AdbOut([string[]]$CmdArgs) {
    if ($Serial) { return (& adb -s $Serial @CmdArgs 2>&1 | Out-String) }
    return (& adb @CmdArgs 2>&1 | Out-String)
}

# legacy device portrait (1440x3168) — ReferenceCam 15/23/73 mm stack bottom-right of preview (vertical column).
# Calibrated from hfr-runs/proshot_lens_row.png May 2026.
# Top-to-bottom on device: 73 mm → 23 mm → 15 mm (logcat: 2497→cam4, 2610→cam2; 15 mm needs cam3).
$script:LensTaps = @(
    @{ label = "tele"; mm = 73; x = 1324; y = 2386; camId = "4" }
    @{ label = "wide"; mm = 23; x = 1324; y = 2497; camId = "2" }
    @{ label = "uw"; mm = 15; x = 1324; y = 2610; camId = "3" }
)
$script:ShutterTap = @{ x = 720; y = 2960 }

function Get-LatestProShotDngPaths {
    $findOut = Invoke-AdbOut @("shell", "find /sdcard/DCIM -maxdepth 1 -name '*.dng' 2>/dev/null")
    $paths = @($findOut -split "`n" | ForEach-Object { $_.Trim() } | Where-Object {
            $_ -match "\.dng$" -and $_ -notmatch "Point"
        })
    $rows = @()
    foreach ($p in $paths) {
        if (-not $p) { continue }
        $stat = (Invoke-AdbOut @("shell", "stat -c '%Y %s' '$p' 2>/dev/null")).Trim()
        $parts = $stat -split "\s+"
        if ($parts.Count -ge 2) {
            $rows += [pscustomobject]@{ Path = $p; Mtime = [int]$parts[0]; Size = [int]$parts[1] }
        }
    }
    return $rows | Sort-Object Mtime
}

function Get-CameraServiceEventsFromLogcat([string]$logFile, [string]$sinceMarker) {
    if (-not (Test-Path $logFile)) { return "" }
    $lines = Get-Content $logFile -Tail 12000 -ErrorAction SilentlyContinue
    $out = [System.Collections.Generic.List[string]]::new()
    $past = [string]::IsNullOrWhiteSpace($sinceMarker)
    foreach ($line in $lines) {
        if (-not $past) {
            if ($line -match $sinceMarker) { $past = $true }
            continue
        }
        if ($line -match "CameraService:.*connect call \(PID.*proshot2.*camera ID (\d+)") {
            [void]$out.Add($line.Trim())
        }
        if ($line -match "CameraService:.*(CONNECT|DISCONNECT) device (\d+).*proshot2") {
            [void]$out.Add($line.Trim())
        }
    }
    return ($out | Select-Object -Last 30) -join "`n"
}

function Wait-NewDng([int]$sinceEpoch, [int]$timeoutSec) {
    $deadline = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $timeoutSec
    while ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -lt $deadline) {
        $new = Get-LatestProShotDngPaths | Where-Object { $_.Mtime -ge $sinceEpoch - 2 }
        if ($new) { return $new | Select-Object -Last 1 }
        Start-Sleep -Milliseconds 400
    }
    return $null
}

function Tap([int]$x, [int]$y) {
    Invoke-Adb @("shell", "input", "tap", "$x", "$y") | Out-Null
}

function Fire-Shutter {
    Tap $script:ShutterTap.x $script:ShutterTap.y
    Start-Sleep -Milliseconds 350
    Invoke-Adb @("shell", "input", "keyevent", "24") | Out-Null
    Start-Sleep -Milliseconds 200
    Invoke-Adb @("shell", "input", "keyevent", "27") | Out-Null
}

# --- logcat background ---
$logPath = Join-Path $outDir "proshot_live_logcat.txt"
$logTags =
    "CameraService:V", "CameraDeviceClient:V", "Camera2ClientBase:V", "CameraCaptureSession:V",
    "CameraDevice:V", "CameraManager:V", "DngCreator:V", "CHI:V", "CamX:V", "OplusCamera:V",
    "camerahal:V", "AndroidRuntime:E", "riseupgames:V"

Invoke-Adb @("shell", "logcat", "-G", "64M") | Out-Null
Invoke-Adb @("shell", "logcat", "-c") | Out-Null

$adbBin = if ($Serial) { "adb -s $Serial" } else { "adb" }
$logProc = Start-Process -FilePath "cmd.exe" -ArgumentList @(
    "/c", "$adbBin shell logcat -v threadtime " + ($logTags -join " ")
) -RedirectStandardOutput $logPath -RedirectStandardError (Join-Path $outDir "logcat_stderr.txt") -PassThru -WindowStyle Hidden

Write-Host ""
Write-Host "=== ReferenceCam live forensics ===" -ForegroundColor Cyan
Write-Host "Output: $outDir"
Write-Host "Logcat PID $($logProc.Id) -> proshot_live_logcat.txt"
Write-Host ""

Invoke-Adb @("shell", "am", "force-stop", "dev.pointandshoot") | Out-Null
Invoke-Adb @("shell", "am", "force-stop", $ProShotPackage) | Out-Null
Start-Sleep -Milliseconds 800

$preDng = Get-LatestProShotDngPaths
$logMarker = "proshot_live_forensics_start"

Invoke-Adb @("shell", "am", "start", "-n", "$ProShotPackage/.activities.MainActivity") | Out-Host
Start-Sleep -Seconds 4
# Marker line in logcat stream (grep after this timestamp in filtered export).
"=== $logMarker $(Get-Date -Format o) ===" | Add-Content $logPath -ErrorAction SilentlyContinue

if ($Calibrate) {
    $cap = Join-Path $outDir "calibrate_screen.png"
    & (Join-Path $PSScriptRoot "pns_device_screencap.ps1") -Serial $Serial -OutPath $cap | Out-Host
    Write-Host "Calibrate: open $cap - set LensTaps / ShutterTap in script header, re-run without -Calibrate."
    if (-not $ManualOnly) { $ManualOnly = $true }
}

if ($TryUiAutomation -and -not $ManualOnly) {
    Write-Host "UI automation ON (lens row y~$($script:LensTaps[0].y), shutter $($script:ShutterTap.x),$($script:ShutterTap.y))" -ForegroundColor Yellow
} else {
    Write-Host "MANUAL: switch ReferenceCam to each lens (UW / wide / 73mm tele) and capture RAW/DNG." -ForegroundColor Yellow
}

$sessionLog = [System.Collections.Generic.List[string]]::new()
$epochStart = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

foreach ($lens in $script:LensTaps) {
    Write-Host ""
    Write-Host "--- Lens $($lens.label) (expect cameraId=$($lens.camId)) ---" -ForegroundColor Cyan
    $t0 = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    [void]$sessionLog.Add("=== $($lens.label) t0=$t0 expect cam=$($lens.camId) ===")

    if ($TryUiAutomation -and -not $ManualOnly) {
        Tap $lens.x $lens.y
        Start-Sleep -Seconds 2
        Fire-Shutter
    } else {
        $msg = "  Switch to $($lens.label) ($($lens.mm)mm) and shoot now (${PerLensSec}s window)..."
        Write-Host $msg
    }

    $deadline = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $PerLensSec
    $gotDng = $null
    while ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -lt $deadline) {
        $gotDng = Wait-NewDng $epochStart 1
        if ($gotDng) { break }
        Start-Sleep -Milliseconds 500
    }

    $events = Get-CameraServiceEventsFromLogcat $logPath $logMarker
    $events | Set-Content (Join-Path $outDir "camera_events_after_$($lens.label).txt") -Encoding UTF8

    if ($gotDng) {
        $local = Join-Path $outDir "proshot_$($lens.label)_$($lens.camId).dng"
        Write-Host "  DNG: $($gotDng.Path) -> $local" -ForegroundColor Green
        Invoke-Adb @("pull", $gotDng.Path, $local) | Out-Null
        [void]$sessionLog.Add("dng=$($gotDng.Path) local=$local size=$($gotDng.Size)")
        $epochStart = $gotDng.Mtime + 1
    } else {
        Write-Host "  No new DCIM/*.dng detected (timeout)" -ForegroundColor Red
        [void]$sessionLog.Add("dng=MISSING")
    }

    $connectLines = @($events -split "`n") | Where-Object { $_ -match "CONNECT device|DISCONNECT device" }
    $lastConnect = $connectLines | Select-Object -Last 3
    foreach ($c in $lastConnect) {
        Write-Host "  $c"
        [void]$sessionLog.Add($c)
    }
}

Start-Sleep -Seconds 2
try { Stop-Process -Id $logProc.Id -Force -ErrorAction SilentlyContinue } catch { }
Start-Sleep -Milliseconds 500

# Filtered logcat excerpt
$filterPath = Join-Path $outDir "proshot_live_logcat_filtered.txt"
$patterns = @(
    "CONNECT", "DISCONNECT", "openCamera", "CameraDevice", "capture", "still",
    "DngCreator", "RAW", "iso", "exposure", "SENSOR_", "CONTROL_AE", "LENS_SHADING",
    "device 2", "device 3", "device 4", "cameraId", "riseupgames"
)
if (Test-Path $logPath) {
    Get-Content $logPath -ErrorAction SilentlyContinue |
        Select-String -Pattern ($patterns -join "|") -CaseSensitive:$false |
        Select-Object -Last 800 |
        ForEach-Object { $_.Line } |
        Set-Content $filterPath -Encoding UTF8
}

$py = Join-Path $PSScriptRoot "proshot_live_parse.py"
if ((Test-Path $py) -and (Get-ChildItem $outDir -Filter "proshot_*.dng").Count -gt 0) {
    Write-Host ""
    Write-Host "[parse] proshot_live_parse.py (optional, 60s cap)..." -ForegroundColor Cyan
    $parseJob = Start-Job { param($p, $d) & python $p $d 2>&1 } -ArgumentList $py, $outDir
    $done = Wait-Job $parseJob -Timeout 60
    if ($done) {
        Receive-Job $parseJob | Out-Host
    } else {
        Stop-Job $parseJob -Force
        Write-Host "WARN: proshot_live_parse.py timed out (skipped)" -ForegroundColor Yellow
    }
    Remove-Job $parseJob -Force -ErrorAction SilentlyContinue
}

$sessionLog -join "`n" | Set-Content (Join-Path $outDir "session_timeline.txt") -Encoding UTF8

Invoke-Adb @("shell", "am", "force-stop", $ProShotPackage) | Out-Null
Invoke-Adb @("shell", "am", "force-stop", "dev.pointandshoot") | Out-Null

Write-Host ""
Write-Host "Done. Artifacts: $outDir" -ForegroundColor Green
Write-Host "  proshot_live_logcat.txt (full)"
Write-Host "  proshot_live_logcat_filtered.txt"
Write-Host "  camera_events_after_*.txt"
Write-Host "  session_timeline.txt"
Write-Host "Apps force-stopped."
