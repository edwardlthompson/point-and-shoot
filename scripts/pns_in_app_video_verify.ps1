<#
.SYNOPSIS
  Build (optional), install, cold-start preview in video-primary mode, scripted in-app MediaRecorder clip,
  assert **`PNS.AdbValidation`** **`inAppVideoSaved ok=true`** with minimum byte size.

.DESCRIPTION
  Uses **`pns_preview_automation_in_app_video_sec`** (debug APK) to toggle recording without UI taps.
  Passes **`pns_preview_imaging_profile=standard_pro`** so persisted Ultra-Max cannot affect the default gate.

.PARAMETER Serial
  adb **-s** serial (optional **`scripts/pns_adb_device.env`** **`PNS_ADB_SERIAL`**).

.PARAMETER MaxAttempts
  Maximum cold-start cycles (default **12**).

.PARAMETER WaitSec
  Seconds after **`am start`** before reading logcat (default **42**).

.PARAMETER RecordSec
  **`pns_preview_automation_in_app_video_sec`** duration (default **5**, max **120** in-app).

.PARAMETER MinBytes
  Minimum **`bytes=`** parsed from **`inAppVideoSaved`** line (default **50_000**).

.PARAMETER SkipAssemble / SkipInstall
  Same semantics as **`pns_photo_capture_verify.ps1`**.

.EXAMPLE
  .\scripts\pns_in_app_video_verify.ps1
  .\scripts\pns_in_app_video_verify.ps1 -WaitSec 55 -RecordSec 6
#>
param(
    [string]$Serial = "",
    [int]$MaxAttempts = 12,
    [int]$WaitSec = 60,
    [int]$RecordSec = 5,
    [long]$MinBytes = 50000,
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

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "[in_app_video_verify] PNS_ADB_SERIAL from pns_adb_device.env -> $Serial"
    }
}

$rec = [Math]::Max(1, [Math]::Min($RecordSec, 120))

if (-not $SkipAssemble) {
    $gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
    Write-Host "[in_app_video_verify] assembleDebug..."
    & $gw ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk"
}

Invoke-AdbTimed @("devices", "-l") 30000 | Out-Host
if (-not $SkipInstall) {
    Write-Host "[in_app_video_verify] install $apk"
    Invoke-AdbTimed @("install", "-r", "-t", $apk) 300000 | Out-Null
}
Invoke-AdbTimedIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 60000 | Out-Null
Invoke-AdbTimedIgnore @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") 60000 | Out-Null
Invoke-AdbTimedIgnore @("shell", "logcat", "-G", "64M") 15000 | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\in_app_video_verify_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
Write-Host "[in_app_video_verify] artifacts -> $outDir"

$successNeedle = "inAppVideoSaved ok=true bytes="
$failNeedles = @(
    "in-app MediaRecorder prepare failed",
    "MediaRecorder.start failed",
    "Can't start in-app video",
    "inAppVideoAutomation recorderMissingOrFailed",
    "inAppVideoShellStartFailed",
    "FATAL EXCEPTION"
)

for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
    Write-Host ""
    Write-Host "[in_app_video_verify] === attempt $attempt / $MaxAttempts ==="

    Invoke-AdbTimedIgnore @("shell", "logcat", "-c") 20000 | Out-Null
    Invoke-AdbTimedIgnore @("shell", "logcat", "-G", "64M") 15000 | Out-Null
    Invoke-AdbTimedIgnore @("shell", "am", "force-stop", $pkg) 30000 | Out-Null
    Start-Sleep -Milliseconds 800

    $amArgs = @(
        "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_primary_photo", "false",
        "--ei", "pns_preview_automation_in_app_video_sec", "$rec",
        "--es", "pns_preview_imaging_profile", "standard_pro",
        "--es", "pns_preview_camera_id", "0"
    )
    try {
        Invoke-AdbTimed $amArgs 120000 | Out-Null
    }
    catch {
        Write-Warning "[in_app_video_verify] am start failed: $_ (retrying)"
        Start-Sleep -Seconds 3
        continue
    }

    Write-Host "[in_app_video_verify] polling up to ${WaitSec}s for inAppVideoSaved..."
    $deadline = (Get-Date).AddSeconds($WaitSec)
    $pollHaystack = ""
    $capturedEarly = $false
    Start-Sleep -Seconds 10
    while ((Get-Date) -lt $deadline) {
        try {
            $adbExe = (Get-Command adb -ErrorAction Stop).Source
            $argv = @()
            if ($Serial) { $argv += "-s", $Serial }
            $argv += "logcat", "-d", "-v", "threadtime", "-s", "PNS.AdbValidation:I"
            $lines = & $adbExe @argv 2>&1
            $pollHaystack = if ($lines -is [System.Array]) { $lines -join "`n" } else { [string]$lines }
        }
        catch {
            Write-Warning "[in_app_video_verify] poll logcat failed: $_"
            $pollHaystack = ""
        }
        if ($pollHaystack.Contains($successNeedle)) {
            $capturedEarly = $true
            Write-Host "[in_app_video_verify] success needle seen during poll"
            break
        }
        Start-Sleep -Seconds 3
    }

    $rawPath = Join-Path $outDir ("attempt_{0:D2}_logcat_raw.txt" -f $attempt)
    $fullText = ""
    if ($capturedEarly -and $pollHaystack.Length -gt 0) {
        $fullText = $pollHaystack
    }
    else {
        try {
            $adbExe = (Get-Command adb -ErrorAction Stop).Source
            $argv = @()
            if ($Serial) { $argv += "-s", $Serial }
            $argv += "logcat", "-d", "-v", "threadtime", "-s", "PNS.AdbValidation:I", "PNS.ChromeUx:I", "AndroidRuntime:E"
            $lines = & $adbExe @argv 2>&1
            $fullText = if ($lines -is [System.Array]) { $lines -join "`n" } else { [string]$lines }
        }
        catch {
            Write-Warning "[in_app_video_verify] tag logcat pull failed: $_"
            $fullText = ""
        }
    }
    [System.IO.File]::WriteAllText($rawPath, $fullText, [System.Text.UTF8Encoding]::new($false))

    $logText = ""
    if ($fullText.Length -gt 0) {
        $logText = (
            $fullText -split "`r?`n" |
                Where-Object { $_ -match 'PNS\.(AdbValidation|ChromeUx|Cam)|AndroidRuntime|MediaRecorder' }
        ) -join "`n"
        if ([string]::IsNullOrWhiteSpace($logText)) {
            $logText = $fullText
        }
    }

    $attemptPath = Join-Path $outDir ("attempt_{0:D2}_logcat.txt" -f $attempt)
    [System.IO.File]::WriteAllText($attemptPath, $logText, [System.Text.UTF8Encoding]::new($false))

    $haystack = if ($fullText.Length -gt 0) { $fullText } else { $logText }
    $bad = $false
    foreach ($n in $failNeedles) {
        if ($haystack.Contains($n)) {
            Write-Host "[in_app_video_verify] saw failure signal: $n"
            $bad = $true
            break
        }
    }

    $bytesParsed = [long]-1
    if ($haystack -match 'inAppVideoSaved ok=true bytes=(-?\d+)') {
        $bytesParsed = [long]$Matches[1]
    }

    $okLine = ($haystack.Contains($successNeedle)) -and (-not $bad) -and ($bytesParsed -ge $MinBytes)

    if ($okLine) {
        Write-Host "[in_app_video_verify] VERIFIED: inAppVideoSaved ok=true bytes=$bytesParsed (>= $MinBytes)"
        $summary = Join-Path $outDir "VERIFY_OK.txt"
        @(
            "attempt=$attempt",
            "needle=inAppVideoSaved ok=true",
            "bytes=$bytesParsed",
            "log=$attemptPath"
        ) | Set-Content -LiteralPath $summary -Encoding utf8
        exit 0
    }

    Write-Host "[in_app_video_verify] attempt $attempt : bytes=$bytesParsed (need >= $MinBytes) bad=$bad"
    $tail = $logText.Split([char[]]@("`r", "`n"), [System.StringSplitOptions]::RemoveEmptyEntries) | Select-Object -Last 35
    Write-Host "--- log tail ---"
    $tail | ForEach-Object { Write-Host $_ }
    Write-Host "--- end tail ---"

    Start-Sleep -Seconds 2
}

Write-Error "[in_app_video_verify] FAILED. See $outDir"
exit 1
