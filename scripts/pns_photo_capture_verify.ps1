<#
.SYNOPSIS
  Build (optional), install, cold-start preview, and verify one scripted RAW still (H dial) until success or max attempts.

.DESCRIPTION
  Uses the same **`captureRawStill`** path as preview **H** mode / ADB **`pns_preview_raw_count`** (not UI coordinate taps).
  Each cold start passes **`pns_preview_imaging_profile=standard_pro`** so a user-persisted **Ultra-Max** HUD choice cannot break the default scripted gate (Ultra-Max remains testable via **`pns_adb_preview_validate.ps1`** scenarios / explicit extras).
  Also passes **`pns_preview_camera_id=0`** so the M23 focal-slot default (which may resolve to a tele / no-RAW logical id on some OEM stacks) cannot strand scripted RAW on a non-RAW camera; use **`-SweepCameraIds`** when you need a wider id sweep.
  After each cold start, pulls filtered **logcat** and checks for **`PNS.AdbValidation`** `captureRawStill 1/1 ok=true saved=`.
  Treats **`PNS.CaptureStill`** `ok=false`, **`save ok=false`**, **`No RAW buffer`**, and **`FATAL EXCEPTION`** as hard failures for that attempt, then retries.

.PARAMETER Serial
  adb **-s** serial. Omit to use **scripts/pns_adb_device.env** (**PNS_ADB_SERIAL**).

.PARAMETER MaxAttempts
  Maximum cold-start / capture cycles (default **30**).

.PARAMETER WaitSec
  Seconds to wait after **`am start`** before reading logcat (default **55**).

.PARAMETER Fast
  Pass **`pns_preview_raw_still_fast`** (shorter in-app ADB settle when combined with app defaults).

.PARAMETER SweepCameraIds
  After each **`am start`**, optionally pass **`pns_preview_camera_id`** for each of **`(default)`, `0`, `1`, `2`, `3`**
  until **`captureRawStill 1/1 ok=true`** or all seeds fail. Use with **`-MaxAttempts 1`** for a quick sweep.

.PARAMETER SkipAssemble
  Do not run **`:app:assembleDebug`**.

.PARAMETER SkipInstall
  Skip **adb install** (assumes current debug APK already installed).

.EXAMPLE
  .\scripts\pns_photo_capture_verify.ps1
  .\scripts\pns_photo_capture_verify.ps1 -Fast -WaitSec 45 -MaxAttempts 20
  .\scripts\pns_photo_capture_verify.ps1 -SweepCameraIds -Fast -MaxAttempts 1 -WaitSec 55
#>
param(
    [string]$Serial = "",
    [int]$MaxAttempts = 30,
    [int]$WaitSec = 55,
    [switch]$Fast,
    [switch]$SweepCameraIds,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
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
    # Windows PowerShell: `Start-Process -ArgumentList $string[]` can corrupt long `adb shell am start …`
    # argv lists so `--es` / `--ei` extras never reach the device (Compose sees null intent seeds). Run the
    # same argv via call splatting in a child job and poll with a timeout.
    $all = @([string[]]$PrefixArgs) + @([string[]]$CmdArgs)
    $outPath = Join-Path $env:TEMP ("pns_adb_out_{0}.txt" -f [Guid]::NewGuid().ToString("N"))
    $errPath = Join-Path $env:TEMP ("pns_adb_err_{0}.txt" -f [Guid]::NewGuid().ToString("N"))
    Remove-Item -LiteralPath $outPath, $errPath -ErrorAction SilentlyContinue
    $job = Start-Job -ScriptBlock {
        param([string]$Exe, [string[]]$Argv, [string]$OutFile, [string]$ErrFile)
        $ErrorActionPreference = "Continue"
        try {
            & $Exe @Argv 1> $OutFile 2> $ErrFile
        }
        catch {
            [void][System.IO.File]::AppendAllText($ErrFile, "`n$($_.Exception.Message)`n")
        }
        if ($null -eq $LASTEXITCODE) { 0 } else { [int]$LASTEXITCODE }
    } -ArgumentList $adbExe, $all, $outPath, $errPath
    $sec = [Math]::Max(1, [int]([Math]::Ceiling($TimeoutMs / 1000.0)))
    $w = Wait-Job -Job $job -Timeout $sec
    if (-not $w) {
        try { Stop-Job $job -ErrorAction SilentlyContinue } catch { }
        try { Remove-Job $job -Force -ErrorAction SilentlyContinue } catch { }
        Remove-Item -LiteralPath $outPath, $errPath -ErrorAction SilentlyContinue
        throw "adb timeout (${TimeoutMs}ms): $($CmdArgs -join ' ')"
    }
    $exitCode = 0
    try {
        $exitCode = [int](Receive-Job $job -ErrorAction SilentlyContinue)
    }
    catch {
        $exitCode = -1
    }
    try { Remove-Job $job -Force -ErrorAction SilentlyContinue } catch { }
    $out = if (Test-Path -LiteralPath $outPath) {
        Get-Content -LiteralPath $outPath -Raw -ErrorAction SilentlyContinue
    }
    else { "" }
    $err = if (Test-Path -LiteralPath $errPath) {
        Get-Content -LiteralPath $errPath -Raw -ErrorAction SilentlyContinue
    }
    else { "" }
    if ($null -eq $out) { $out = "" }
    if ($null -eq $err) { $err = "" }
    Remove-Item -LiteralPath $outPath, $errPath -ErrorAction SilentlyContinue
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
        $msg = "adb $($CmdArgs -join ' ') exit=$($r.ExitCode) stderr=$es"
        throw $msg
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

# Logcat dumps can be large; piping stdout through Invoke-AdbExe's temp file sometimes yields empty reads on Windows.
function Invoke-AdbRedirectStdoutToFile([string[]]$CmdArgs, [string]$outFile, [int]$TimeoutMs) {
    $adbExe = Get-AdbPath
    $prefix = @()
    if ($Serial) { $prefix = @("-s", $Serial) }
    $errFile = "$outFile.stderr.txt"
    $all = @([string[]]$prefix) + @([string[]]$CmdArgs)
    Remove-Item -LiteralPath $errFile -ErrorAction SilentlyContinue
    $job = Start-Job -ScriptBlock {
        param([string]$Exe, [string[]]$Argv, [string]$OutFile, [string]$ErrFile)
        $ErrorActionPreference = "Continue"
        try {
            & $Exe @Argv 1> $OutFile 2> $ErrFile
        }
        catch {
            [void][System.IO.File]::AppendAllText($ErrFile, "`n$($_.Exception.Message)`n")
        }
        if ($null -eq $LASTEXITCODE) { 0 } else { [int]$LASTEXITCODE }
    } -ArgumentList $adbExe, $all, $outFile, $errFile
    $sec = [Math]::Max(1, [int]([Math]::Ceiling($TimeoutMs / 1000.0)))
    $w = Wait-Job -Job $job -Timeout $sec
    if (-not $w) {
        try { Stop-Job $job -ErrorAction SilentlyContinue } catch { }
        try { Remove-Job $job -Force -ErrorAction SilentlyContinue } catch { }
        throw "adb timeout (${TimeoutMs}ms): $($CmdArgs -join ' ')"
    }
    $exitCode = 0
    try {
        $exitCode = [int](Receive-Job $job -ErrorAction SilentlyContinue)
    }
    catch {
        $exitCode = -1
    }
    try { Remove-Job $job -Force -ErrorAction SilentlyContinue } catch { }
    Remove-Item $errFile -ErrorAction SilentlyContinue
    return $exitCode
}

function Test-UsableAppLogcatDump([string]$path, [string]$packageName) {
    if (-not (Test-Path -LiteralPath $path)) { return $false }
    $len = (Get-Item -LiteralPath $path).Length
    if ($len -lt 512) { return $false }
    $snipLen = [Math]::Min([int]$len, 262144)
    $buf = New-Object byte[] $snipLen
    $fs = [System.IO.File]::OpenRead($path)
    try { [void]$fs.Read($buf, 0, $snipLen) }
    finally { $fs.Dispose() }
    $head = [System.Text.Encoding]::UTF8.GetString($buf)
    if ($head -match 'PNS\.(AdbValidation|CaptureStill|StillBoundary|Cam|Preview)') { return $true }
    if ($head -match 'AndroidRuntime') { return $true }
    if ($head -match [regex]::Escape($packageName)) { return $true }
    return $false
}

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "[photo_capture_verify] PNS_ADB_SERIAL from pns_adb_device.env -> $Serial"
    }
}

if (-not $SkipAssemble) {
    $gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
    Write-Host "[photo_capture_verify] assembleDebug..."
    & $gw ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk"
}

Invoke-AdbTimed @("devices", "-l") 30000 | Out-Host
if (-not $SkipInstall) {
    Write-Host "[photo_capture_verify] install $apk"
    Invoke-AdbTimed @("install", "-r", "-t", $apk) 300000 | Out-Null
}
Invoke-AdbTimedIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 60000 | Out-Null
Invoke-AdbTimedIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_IMAGES") 60000 | Out-Null
# Default ring buffers are tiny (256 KiB) on some OEM builds; HAL spam evicts app lines before we pull logcat.
Invoke-AdbTimedIgnore @("shell", "logcat", "-G", "64M") 15000 | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\photo_capture_verify_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "[photo_capture_verify] artifacts -> $outDir"

$successNeedle = "captureRawStill 1/1 ok=true saved="
$failNeedles = @(
    "captureRawStill 1/1 ok=false",
    "captureRawStill save ok=false",
    "No RAW buffer",
    "FATAL EXCEPTION"
)

$seedList = if ($SweepCameraIds) { @("", "0", "1", "2", "3") } else { @("") }

foreach ($camSeed in $seedList) {
    $seedLabel = if ([string]::IsNullOrWhiteSpace($camSeed)) { "default" } else { $camSeed }
    $seedDir = if ($SweepCameraIds) { Join-Path $outDir ("seed_{0}" -f $seedLabel) } else { $outDir }
    if ($SweepCameraIds) {
        New-Item -ItemType Directory -Force -Path $seedDir | Out-Null
        Write-Host ""
        Write-Host "[photo_capture_verify] === camera seed '$seedLabel' ==="
    }

    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        Write-Host ""
        Write-Host "[photo_capture_verify] === attempt $attempt / $MaxAttempts (seed=$seedLabel) ==="

        Invoke-AdbTimedIgnore @("shell", "logcat", "-c") 20000 | Out-Null
        Invoke-AdbTimedIgnore @("shell", "logcat", "-G", "64M") 15000 | Out-Null
        Invoke-AdbTimedIgnore @("shell", "am", "force-stop", $pkg) 30000 | Out-Null
        Start-Sleep -Milliseconds 800

        $amArgs = @(
            "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
            "--activity-clear-task",
            "--es", "pns_screen", "preview",
            "--es", "pns_preview_dial", "H",
            "--ei", "pns_preview_raw_count", "1",
            "--es", "pns_preview_imaging_profile", "standard_pro",
            "--es", "pns_preview_camera_id", "0"
        )
        if ($Fast) {
            $amArgs += @("--ez", "pns_preview_raw_still_fast", "true")
        }
        if (-not [string]::IsNullOrWhiteSpace($camSeed)) {
            $amArgs += @("--es", "pns_preview_camera_id", $camSeed)
        }
        try {
            Invoke-AdbTimed $amArgs 120000 | Out-Null
        }
        catch {
            Write-Warning "[photo_capture_verify] am start failed: $_ (retrying)"
            Start-Sleep -Seconds 3
            continue
        }

        Write-Host "[photo_capture_verify] waiting ${WaitSec}s for scripted RAW still + save..."
        Start-Sleep -Seconds $WaitSec

        $rawPath = Join-Path $seedDir ("attempt_{0:D2}_logcat_raw.txt" -f $attempt)
        $logText = ""
        $fullText = ""
        $pidStr = ""
        try {
            $pidStr = ([string](Invoke-AdbTimed @("shell", "pidof", "-s", $pkg) 30000)).Trim()
        }
        catch {
            $pidStr = ""
        }
        if (-not [string]::IsNullOrWhiteSpace($pidStr)) {
            $pids = $pidStr -split "\s+" | Where-Object { $_ -match "^\d+$" }
            if ($pids.Count -ge 1) {
                $usePid = $pids[0]
                try {
                    # Two-arg --pid is more reliable than --pid=NNN with Start-Process on Windows adb.
                    $ec = Invoke-AdbRedirectStdoutToFile @("shell", "logcat", "-d", "-v", "threadtime", "--pid", $usePid, "-t", "50000") $rawPath 180000
                    if ($ec -ne 0) {
                        Write-Warning "[photo_capture_verify] logcat --pid=$usePid exit=$ec (reading dump anyway)"
                    }
                    if (-not (Test-UsableAppLogcatDump $rawPath $pkg)) {
                        Write-Warning "[photo_capture_verify] pid-filtered logcat unusable (wrong pid or buffer); will fall back"
                        Remove-Item -LiteralPath $rawPath -ErrorAction SilentlyContinue
                    }
                }
                catch {
                    Write-Warning "[photo_capture_verify] logcat --pid=$usePid failed: $_"
                }
            }
        }
        if (-not (Test-Path -LiteralPath $rawPath) -or ((Get-Item -LiteralPath $rawPath).Length -eq 0)) {
            $tagArgs = @(
                "shell", "logcat", "-d", "-v", "threadtime", "-t", "40000", "*:S",
                "PNS.AdbValidation:I", "PNS.CaptureStill:W", "PNS.StillBoundary:I", "PNS.Cam:W", "PNS.Cam:I", "PNS.Reader:W", "PNS.Dng:W",
                "AndroidRuntime:E"
            )
            try {
                $ec2 = Invoke-AdbRedirectStdoutToFile $tagArgs $rawPath 180000
                if ($ec2 -ne 0) {
                    Write-Warning "[photo_capture_verify] logcat tag-filter dump exit=$ec2"
                }
            }
            catch {
                Write-Warning "[photo_capture_verify] logcat tag filter failed: $_"
            }
        }
        if (-not (Test-Path -LiteralPath $rawPath) -or ((Get-Item -LiteralPath $rawPath).Length -eq 0)) {
            try {
                $ec3 = Invoke-AdbRedirectStdoutToFile @("shell", "logcat", "-d", "-v", "threadtime", "-t", "100000") $rawPath 180000
                if ($ec3 -ne 0) {
                    Write-Warning "[photo_capture_verify] logcat full dump exit=$ec3"
                }
            }
            catch {
                Write-Warning "[photo_capture_verify] logcat full dump failed: $_"
            }
        }

        $fullText = ""
        if ((Test-Path -LiteralPath $rawPath) -and ((Get-Item -LiteralPath $rawPath).Length -gt 0)) {
            $fullText = [System.IO.File]::ReadAllText($rawPath, [System.Text.UTF8Encoding]::new($false))
            $logText = (
                $fullText -split "`r?`n" |
                    Where-Object { $_ -match 'PNS\.(AdbValidation|CaptureStill|StillBoundary|Cam|Reader|Dng)|captureRawStill|AndroidRuntime' }
            ) -join "`n"
            if ([string]::IsNullOrWhiteSpace($logText)) {
                $logText = $fullText
            }
        }

        $attemptPath = Join-Path $seedDir ("attempt_{0:D2}_logcat.txt" -f $attempt)
        [System.IO.File]::WriteAllText($attemptPath, $logText, [System.Text.UTF8Encoding]::new($false))

        $diagPath = Join-Path $seedDir ("attempt_{0:D2}_capture_pipeline_diag.txt" -f $attempt)
        try {
            $diagText = Invoke-AdbTimed @("exec-out", "run-as", $pkg, "cat", "files/PNS_CAPTURE_PIPELINE_DIAGNOSTICS.txt") 60000
            if ($null -eq $diagText) { $diagText = "" }
            [System.IO.File]::WriteAllText($diagPath, [string]$diagText, [System.Text.UTF8Encoding]::new($false))
            Write-Host "[photo_capture_verify] wrote $diagPath"
        }
        catch {
            Write-Host "[photo_capture_verify] capture pipeline diag pull skipped: $_"
        }

        $haystack = if ($fullText.Length -gt 0) { $fullText } else { $logText }
        $ok = $haystack.Contains($successNeedle)
        $bad = $false
        foreach ($n in $failNeedles) {
            if ($haystack.Contains($n)) {
                Write-Host "[photo_capture_verify] saw failure signal: $n"
                $bad = $true
                break
            }
        }

        if ($ok -and -not $bad) {
            Write-Host "[photo_capture_verify] VERIFIED: $successNeedle"
            $summary = Join-Path $seedDir "VERIFY_OK.txt"
            @(
                "attempt=$attempt",
                "cameraSeed=$seedLabel",
                "needle=$successNeedle",
                "log=$attemptPath"
            ) | Set-Content -LiteralPath $summary -Encoding utf8
            Write-Host "[photo_capture_verify] wrote $summary"
            exit 0
        }

        if (-not $ok) {
            Write-Host "[photo_capture_verify] attempt $attempt : success needle not found (see $attemptPath)"
        }
        else {
            Write-Host "[photo_capture_verify] attempt $attempt : success needle present but failure signal also present"
        }

        $tail = $logText.Split([char[]]@("`r", "`n"), [System.StringSplitOptions]::RemoveEmptyEntries) | Select-Object -Last 40
        Write-Host "--- log tail ---"
        $tail | ForEach-Object { Write-Host $_ }
        Write-Host "--- end tail ---"

        Start-Sleep -Seconds 2
    }
}

Write-Error "[photo_capture_verify] FAILED (no verified capture). See $outDir"
exit 1
