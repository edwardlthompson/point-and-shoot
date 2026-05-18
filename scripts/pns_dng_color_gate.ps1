<#
.SYNOPSIS
  Automated DNG color gate: captures one Standard Pro RAW still per camera (2=UW, 3=Wide, 4=Tele),
  pulls the DNGs, and verifies that cam-WB decode G/R delta vs wide is within threshold.
  Uses the same am-start + pns_preview_camera_id mechanism as pns_photo_capture_verify.ps1.

.USAGE
  .\scripts\pns_dng_color_gate.ps1 [-SkipBuild] [-WaitSec 55] [-Serial <serial>]
#>
param(
    [string]$Serial    = "",
    [int]   $WaitSec   = 55,
    [switch]$SkipBuild,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$pkg      = "dev.pointandshoot"
$utc      = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir   = Join-Path $projRoot "hfr-runs\dng_color_gate_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

# Prefer SDK adb over any system adb to avoid version mismatches
$adbExe = "C:\Users\edwar\AppData\Local\Android\Sdk\platform-tools\adb.exe"
if (-not (Test-Path $adbExe)) { $adbExe = (Get-Command adb -ErrorAction Stop).Source }

function adbCmd([string[]]$cmdArgs, [int]$timeoutMs = 120000) {
    $prefix = if ($Serial) { @("-s", $Serial) } else { @() }
    $all    = [string[]]($prefix + $cmdArgs)
    $exe    = $adbExe   # capture outer var for job
    $out    = [System.IO.Path]::GetTempFileName()
    $err    = [System.IO.Path]::GetTempFileName()
    $job = Start-Job -ScriptBlock {
        param([string]$e,[string[]]$a,[string]$o,[string]$r)
        try { & $e @a 1>$o 2>$r } catch { }
        if ($null -eq $LASTEXITCODE) { 0 } else { [int]$LASTEXITCODE }
    } -ArgumentList $exe,$all,$out,$err
    $w = Wait-Job $job -Timeout ([int][Math]::Ceiling($timeoutMs/1000))
    $ec = if ($w) { try { [int](Receive-Job $job) } catch { 1 } } else { Stop-Job $job; 1 }
    Remove-Job $job -Force -ErrorAction SilentlyContinue
    $stdout = if (Test-Path $out) { Get-Content $out -Raw -ErrorAction SilentlyContinue } else { "" }
    Remove-Item $out,$err -ErrorAction SilentlyContinue
    return [pscustomobject]@{ EC=$ec; Out=[string]$stdout }
}

Write-Host ""
Write-Host "=== PNS DNG Color Gate ===" -ForegroundColor Cyan
Write-Host "Output: $outDir"
Write-Host ""

# Build
if (-not $SkipBuild) {
    Write-Host "[gate] assembleDebug..."
    $gw = Join-Path $PSScriptRoot "pns_gradlew.ps1"
    if (Test-Path $gw) { & $gw ":app:assembleDebug" }
    else {
        $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
        & "$projRoot\gradlew.bat" ":app:assembleDebug" "--no-daemon"
    }
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}

# Install
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    Write-Host "[gate] Installing APK..."
    $r = adbCmd @("install","-r","-t",$apk) 300000
    if ($r.EC -ne 0) { throw "adb install failed" }
}

# Logcat buffer
adbCmd @("shell","logcat","-G","64M") 15000 | Out-Null
adbCmd @("shell","logcat","-c")       20000 | Out-Null

$captured = @()

foreach ($camSpec in @(
    [pscustomobject]@{ Id="2"; Label="uw"   },
    [pscustomobject]@{ Id="3"; Label="wide" },
    [pscustomobject]@{ Id="4"; Label="tele" }
)) {
    Write-Host ""
    Write-Host "[gate] Capturing cam $($camSpec.Id) ($($camSpec.Label))..." -ForegroundColor Cyan

    adbCmd @("shell","logcat","-c") 15000 | Out-Null
    adbCmd @("shell","am","force-stop",$pkg) 30000 | Out-Null
    Start-Sleep -Milliseconds 800

    $amArgs = @(
        "shell","am","start","-W","-n","${pkg}/.MainActivity",
        "--activity-clear-task",
        "--es","pns_screen","preview",
        "--es","pns_preview_dial","H",
        "--ei","pns_preview_raw_count","1",
        "--es","pns_preview_imaging_profile","standard_pro",
        "--es","pns_preview_camera_id",$camSpec.Id
    )
    $r = adbCmd $amArgs 120000
    if ($r.EC -ne 0) { Write-Warning "[gate] am start failed for cam $($camSpec.Id), continuing..." }

    # Record device epoch before waiting so we only pick the DNG from this session
    $epochR = adbCmd @("shell","date +%s") 10000
    $captureEpoch = [int]($epochR.Out.Trim()) - 2  # 2s buffer

    Write-Host "[gate] Waiting ${WaitSec}s for capture + save..."
    Start-Sleep -Seconds $WaitSec

    # Pull logcat for this camera
    $logFile = Join-Path $outDir "cam$($camSpec.Id)_logcat.txt"
    $logR = adbCmd @("shell","logcat","-d","-v","threadtime","-t","20000","*:S","PNS.Dng:I","PNS.CaptureStill:I","PNS.AdbValidation:I","AndroidRuntime:E") 60000
    $logR.Out | Set-Content $logFile -Encoding UTF8

    # Find all DNGs and filter by modification timestamp against captureEpoch
    $allDngR = adbCmd @("shell","find '/sdcard/DCIM' -name '*.dng' 2>/dev/null | sort") 30000
    $allDngs = ($allDngR.Out -split "`n") | Where-Object { $_.Trim() -match "\.dng$" }
    # Pick only DNGs modified after captureEpoch using stat
    $newDngs = @()
    foreach ($d in $allDngs) {
        $dt = $d.Trim()
        if (-not $dt) { continue }
        $statR = adbCmd @("shell","stat -c %Y '$dt' 2>/dev/null") 10000
        $mtime = [int]($statR.Out.Trim())
        if ($mtime -ge $captureEpoch) { $newDngs += $dt }
    }
    $dngs = $newDngs | Sort-Object
    if ($dngs.Count -eq 0) {
        Write-Host "[gate] WARNING: No DNGs found for cam $($camSpec.Id)" -ForegroundColor Yellow
        $captured += [pscustomobject]@{ Label=$camSpec.Label; Path=$null }
        continue
    }
    $latest = ($dngs | Select-Object -Last 1).Trim()
    $localDng = Join-Path $outDir "$($camSpec.Label).dng"
    Write-Host "[gate]   Pulling $latest"
    adbCmd @("pull",$latest,$localDng) 120000 | Out-Null
    if (Test-Path $localDng) {
        Write-Host "[gate]   Saved $localDng ($([int]((Get-Item $localDng).Length/1MB)) MB)"
        $captured += [pscustomobject]@{ Label=$camSpec.Label; Path=$localDng }
    } else {
        Write-Host "[gate]   PULL FAILED" -ForegroundColor Red
        $captured += [pscustomobject]@{ Label=$camSpec.Label; Path=$null }
    }
}

adbCmd @("shell","am","force-stop",$pkg) 30000 | Out-Null
Write-Host ""
Write-Host "[gate] App stopped."

# Verify all three pulled
$uwPath   = ($captured | Where-Object { $_.Label -eq "uw"   } | Select-Object -First 1).Path
$widePath = ($captured | Where-Object { $_.Label -eq "wide" } | Select-Object -First 1).Path
$telePath = ($captured | Where-Object { $_.Label -eq "tele" } | Select-Object -First 1).Path

if (-not ($uwPath -and $widePath -and $telePath)) {
    Write-Host ""
    Write-Host "FAIL: Missing one or more DNG files." -ForegroundColor Red
    Write-Host "  uw:   $uwPath"
    Write-Host "  wide: $widePath"
    Write-Host "  tele: $telePath"
    exit 1
}

Write-Host ""
Write-Host "[gate] Running structural DNG verification..." -ForegroundColor Cyan
$pyScript = Join-Path $PSScriptRoot "structural_verify.py"
python $pyScript $uwPath $widePath $telePath
$pyExit = $LASTEXITCODE

# Save result summary
$summary = [pscustomobject]@{
    timestamp = $utc
    uw_path   = $uwPath
    wide_path = $widePath
    tele_path = $telePath
    result    = if ($pyExit -eq 0) { "PASS" } else { "FAIL" }
}
$summary | ConvertTo-Json | Set-Content (Join-Path $outDir "gate_result.json") -Encoding UTF8

Write-Host ""
if ($pyExit -eq 0) {
    Write-Host "=== DNG COLOR GATE: PASS ===" -ForegroundColor Green
} else {
    Write-Host "=== DNG COLOR GATE: FAIL ===" -ForegroundColor Red
    Write-Host ""
    Write-Host "Logcat (PNS.Dng) from last camera:" -ForegroundColor Yellow
    $lastLog = Join-Path $outDir "cam4_logcat.txt"
    if (Test-Path $lastLog) {
        Get-Content $lastLog | Select-String "PNS.Dng" | Select-Object -Last 20 | ForEach-Object { Write-Host "  $_" }
    }
}
exit $pyExit
