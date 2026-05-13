<#
.SYNOPSIS
  Cold-start preview scripted RAW still once per matrix cell (2 imaging profiles × 5 raw streams × 2 JPEG companion = **20** cells; use **`-Quick`** for 4 cells).

.DESCRIPTION
  Exercises **`pns_preview_imaging_profile`**, **`pns_preview_raw_stream`**, **`pns_preview_jpeg_companion`**, and optional **`pns_preview_camera_id`**
  with **`pns_preview_dial=H`** and **`pns_preview_raw_count=1`**. Writes **`matrix.csv`** + **`matrix.md`** under **`hfr-runs/raw_capture_matrix_*`**.

.PARAMETER Serial
  adb **-s** serial. Omit to use **scripts/pns_adb_device.env** (**PNS_ADB_SERIAL**).

.PARAMETER WaitSec
  Seconds after **`am start`** before logcat (default **88** — must cover ADB sequential RAW wait + still I/O on slow devices).

.PARAMETER Quick
  Subset only: **default** + **raw_sensor_first** × **standard_pro** × JPEG on/off (4 cells).

.PARAMETER CameraId
  Optional **`pns_preview_camera_id`** (e.g. **`2`** for logical wide on some fleets). Omit for default seed.

.PARAMETER SkipAssemble
  Skip **`:app:assembleDebug`**.

.PARAMETER SkipInstall
  Skip **adb install** (assumes debug APK already installed).

.PARAMETER Fast
  Pass **`pns_preview_raw_still_fast`**.

.EXAMPLE
  .\scripts\pns_raw_capture_matrix.ps1 -Quick
  .\scripts\pns_raw_capture_matrix.ps1 -CameraId 2 -WaitSec 55
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 88,
    [switch]$Quick,
    [string]$CameraId = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [switch]$Fast
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

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
        if ($k -eq "PNS_ADB_SERIAL") { return $v }
    }
    return $null
}

function Get-AdbPath {
    (Get-Command adb -ErrorAction Stop).Source
}

function Invoke-AdbExe([string]$adbExe, [string[]]$PrefixArgs, [string[]]$CmdArgs, [int]$TimeoutMs) {
    $all = $PrefixArgs + $CmdArgs
    $p = Start-Process -FilePath $adbExe -ArgumentList $all -NoNewWindow -PassThru -Wait:$false `
        -RedirectStandardOutput "$env:TEMP\pns_adb_out_$PID.txt" `
        -RedirectStandardError "$env:TEMP\pns_adb_err_$PID.txt"
    if (-not $p.WaitForExit($TimeoutMs)) {
        try { $p.Kill() } catch { }
        throw "adb timeout (${TimeoutMs}ms): $($CmdArgs -join ' ')"
    }
    $exitCode = 0
    try {
        if ($null -ne $p.ExitCode) { $exitCode = [int]$p.ExitCode }
    }
    catch {
        $exitCode = -1
    }
    $out = if (Test-Path "$env:TEMP\pns_adb_out_$PID.txt") {
        Get-Content -LiteralPath "$env:TEMP\pns_adb_out_$PID.txt" -Raw -ErrorAction SilentlyContinue
    } else { "" }
    $err = if (Test-Path "$env:TEMP\pns_adb_err_$PID.txt") {
        Get-Content -LiteralPath "$env:TEMP\pns_adb_err_$PID.txt" -Raw -ErrorAction SilentlyContinue
    } else { "" }
    if ($null -eq $out) { $out = "" }
    if ($null -eq $err) { $err = "" }
    Remove-Item "$env:TEMP\pns_adb_out_$PID.txt" -ErrorAction SilentlyContinue
    Remove-Item "$env:TEMP\pns_adb_err_$PID.txt" -ErrorAction SilentlyContinue
    return [pscustomobject]@{ ExitCode = $exitCode; StdOut = $out; StdErr = $err }
}

function Invoke-AdbTimed([string[]]$CmdArgs, [int]$TimeoutMs = 120000) {
    $adbExe = Get-AdbPath
    $prefix = @()
    if ($Serial) { $prefix = @("-s", $Serial) }
    $r = Invoke-AdbExe $adbExe $prefix $CmdArgs $TimeoutMs
    if ($null -eq $r) { throw "adb (no result): $($CmdArgs -join ' ')" }
    if ($r.ExitCode -ne 0) {
        $es = if ($null -ne $r.StdErr) { [string]$r.StdErr.Trim() } else { "" }
        throw "adb $($CmdArgs -join ' ') exit=$($r.ExitCode) stderr=$es"
    }
    if ($null -eq $r.StdOut) { return "" }
    return [string]$r.StdOut
}

function Invoke-AdbTimedIgnore([string[]]$CmdArgs, [int]$TimeoutMs = 120000) {
    $adbExe = Get-AdbPath
    $prefix = @()
    if ($Serial) { $prefix = @("-s", $Serial) }
    try {
        $r = Invoke-AdbExe $adbExe $prefix $CmdArgs $TimeoutMs
        return $r.ExitCode
    }
    catch {
        return 1
    }
}

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "[raw_capture_matrix] PNS_ADB_SERIAL from pns_adb_device.env -> $Serial"
    }
}

if (-not $SkipAssemble) {
    $gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
    Write-Host "[raw_capture_matrix] assembleDebug..."
    & $gw ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk"
}

Invoke-AdbTimed @("devices", "-l") 30000 | Out-Host
if (-not $SkipInstall) {
    Write-Host "[raw_capture_matrix] install $apk"
    Invoke-AdbTimed @("install", "-r", "-t", $apk) 300000 | Out-Null
}
Invoke-AdbTimedIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 60000 | Out-Null
Invoke-AdbTimedIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_IMAGES") 60000 | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\raw_capture_matrix_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "[raw_capture_matrix] artifacts -> $outDir"

$successNeedle = "captureRawStill 1/1 ok=true saved="
$failNeedles = @(
    "captureRawStill 1/1 ok=false",
    "captureRawStill save ok=false",
    "No RAW buffer",
    "FATAL EXCEPTION",
    "PNS.Cam onError",
    "Surface was abandoned",
    "Session create aborted"
)

$profiles = @("standard_pro", "ultra_max")
$rawStreams = @("default", "raw_sensor_first", "raw12_only", "raw_sensor_only", "raw10_only")
$jpegVals = @($true, $false)

if ($Quick) {
    $profiles = @("standard_pro")
    $rawStreams = @("default", "raw_sensor_first")
    $jpegVals = @($true, $false)
}

$rows = New-Object System.Collections.Generic.List[string]
$rows.Add("profile,raw_stream,jpeg_companion,camera_id,ok,fail_signal,raw_reader_snippet")

$md = New-Object System.Text.StringBuilder
[void]$md.AppendLine("# RAW capture matrix (device)")
[void]$md.AppendLine()
[void]$md.AppendLine("| profile | raw_stream | jpeg | camera | ok | notes |")
[void]$md.AppendLine("|---------|------------|------|--------|----|-------|")

$cell = 0
$okCount = 0
foreach ($prof in $profiles) {
    foreach ($rs in $rawStreams) {
        foreach ($jpeg in $jpegVals) {
            $cell++
            Write-Host ""
            Write-Host "[raw_capture_matrix] === cell $cell profile=$prof raw_stream=$rs jpeg=$jpeg ==="

            Invoke-AdbTimedIgnore @("logcat", "-c") 20000 | Out-Null
            Invoke-AdbTimedIgnore @("shell", "am", "force-stop", $pkg) 30000 | Out-Null
            Start-Sleep -Milliseconds 800

            $amArgs = @(
                "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
                "--activity-clear-task",
                "--es", "pns_screen", "preview",
                "--es", "pns_preview_dial", "H",
                "--ei", "pns_preview_raw_count", "1",
                "--es", "pns_preview_imaging_profile", $prof,
                "--es", "pns_preview_raw_stream", $rs,
                "--ez", "pns_preview_jpeg_companion", $(if ($jpeg) { "true" } else { "false" })
            )
            if (-not [string]::IsNullOrWhiteSpace($CameraId)) {
                $amArgs += @("--es", "pns_preview_camera_id", $CameraId.Trim())
            }
            if ($Fast) {
                $amArgs += @("--ez", "pns_preview_raw_still_fast", "true")
            }

            try {
                Invoke-AdbTimed $amArgs 120000 | Out-Null
            }
            catch {
                Write-Warning "[raw_capture_matrix] am start failed: $_"
            }

            Write-Host "[raw_capture_matrix] waiting ${WaitSec}s..."
            Start-Sleep -Seconds $WaitSec

            $tagArgs = @(
                "logcat", "-d", "-t", "25000", "*:S",
                "PNS.AdbValidation:I", "PNS.CaptureStill:W", "PNS.Cam:W", "PNS.Cam:I", "PNS.Reader:W", "PNS.Dng:W",
                "AndroidRuntime:E"
            )
            $logText = ""
            try {
                $logText = Invoke-AdbTimed $tagArgs 180000
            }
            catch {
                $logText = ""
            }

            $camCol = if ([string]::IsNullOrWhiteSpace($CameraId)) { "" } else { $CameraId.Trim() }
            $logPath = Join-Path $outDir ("cell_{0:D3}_{1}_{2}_jpeg{3}.txt" -f $cell, $prof, $rs, $jpeg)
            [System.IO.File]::WriteAllText($logPath, $logText, [System.Text.UTF8Encoding]::new($false))

            $ok = $logText.Contains($successNeedle)
            $failSig = ""
            foreach ($n in $failNeedles) {
                if ($logText.Contains($n)) {
                    $failSig = $n
                    break
                }
            }
            if ($ok -and $failSig.Length -gt 0) {
                $ok = $false
            }
            if ($ok) { $okCount++ }

            $notes = if ($ok) { "PASS" } else { if ($failSig) { $failSig } else { "no success needle" } }

            $snippet = ""
            $lines = $logText -split "`r?`n"
            foreach ($ln in $lines) {
                if ($ln -match "RAW ImageReader") {
                    $snippet = $ln.Trim()
                    break
                }
            }
            if ($snippet.Length -gt 120) { $snippet = $snippet.Substring(0, 117) + "..." }

            $failEsc = ($failSig -replace '"', '""')
            $snipEsc = ($snippet -replace '"', '""')
            $rows.Add(
                "$prof,$rs,$($jpeg.ToString().ToLowerInvariant()),$camCol,$($ok.ToString().ToLowerInvariant()),`"$failEsc`",`"$snipEsc`""
            )

            $notesMd = ($notes -replace '\|', '\|')
            [void]$md.AppendLine("| $prof | $rs | $($jpeg.ToString().ToLowerInvariant()) | $camCol | $($ok.ToString().ToLowerInvariant()) | $notesMd |")
        }
    }
}

$csvPath = Join-Path $outDir "matrix.csv"
$rows | Set-Content -LiteralPath $csvPath -Encoding utf8
$mdPath = Join-Path $outDir "matrix.md"
[System.IO.File]::WriteAllText($mdPath, $md.ToString(), [System.Text.UTF8Encoding]::new($false))

Write-Host ""
Write-Host "[raw_capture_matrix] done cells=$cell ok=$okCount -> $csvPath"
exit 0
