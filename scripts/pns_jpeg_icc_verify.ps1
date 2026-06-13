#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.17** — composed JPEG with embedded Display P3 ICC (exiftool gate).

.PARAMETER SkipGateCheck
  Run capture attempt even when host exiftool is missing (expect fail).

.EXAMPLE
  .\scripts\pns_jpeg_icc_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$CaptureWaitSec = 75,
    [switch]$SkipGateCheck
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet
. (Join-Path $repo "scripts\pns_adb_serial.ps1")

$Serial = Resolve-PnsAdbSerial -Serial $Serial -ScriptRoot (Join-Path $repo "scripts") -LogPrefix "jpeg_icc"
if (-not $Serial) { throw "Set PNS_ADB_SERIAL or -Serial" }

$pkg = "dev.pointandshoot"
$gateStartUtc = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$outDir = Join-Path $repo "hfr-runs\jpeg_icc_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$exiftool = Get-Command exiftool -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $exiftool -and -not $SkipGateCheck) {
    @{
        schema = "pns.jpeg_icc_verify.v1"
        pass = $false
        skipped = $true
        reason = "exiftool_missing"
        gateNote = "exiftool not on PATH"
        outDir = $outDir
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir "gate.json") -Encoding utf8
    Write-Host "[jpeg_icc_verify] SKIPPED — exiftool not on PATH (install ExifTool for 15.17 gate)."
    exit 0
}
if (-not $exiftool) {
    Write-Host "FAIL: exiftool not on PATH and -SkipGateCheck set" -ForegroundColor Red
    exit 1
}

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Host
}
adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null

adb -s $Serial logcat -c | Out-Null
adb -s $Serial shell am force-stop $pkg | Out-Null
$am = @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--es", "pns_preview_dial", "P",
    "--ez", "pns_preview_composed_still", "true",
    "--ez", "pns_preview_jpeg_companion", "true",
    "--es", "pns_preview_imaging_profile", "standard_pro",
    "--ei", "pns_preview_camera_id", "2"
)
adb -s $Serial @am | Out-Host

Write-Host "[jpeg_icc] waiting ${CaptureWaitSec}s for composed still..."
Start-Sleep -Seconds $CaptureWaitSec

$logPath = Join-Path $outDir "logcat.txt"
adb -s $Serial logcat -d -v threadtime -s PNS.AdbValidation:I PNS.CaptureStill:I PNS.StillExif:I AndroidRuntime:E 2>&1 |
    Out-File -Encoding utf8 $logPath
$logRaw = Get-Content $logPath -Raw

$savedName = $null
if ($logRaw -match "captureIndependentTonalStill[^\r\n]*saved=(pns_[^\r\n]+\.jpg)") {
    $savedName = $Matches[1].Trim()
} elseif ($logRaw -match "captureComposedStill[^\r\n]*tonal=content://[^\r\n]+") {
    # fallback: newest jpeg_only in pull
}

$pullDir = Join-Path $outDir "dcim"
New-Item -ItemType Directory -Force -Path $pullDir | Out-Null
if ($savedName) {
    $remote = "/sdcard/DCIM/Point & Shoot/$savedName"
    $local = Join-Path $pullDir $savedName
    adb -s $Serial pull $remote $local 2>&1 | Out-Host
    $jpeg = if (Test-Path $local) { Get-Item $local } else { $null }
} else {
    & (Join-Path $repo "scripts\pns_pull_dcim_captures.ps1") -Serial $Serial -OutDir $pullDir | Out-Host
    $cutoff = (Get-Date).AddMinutes(-5)
    $jpeg = Get-ChildItem $pullDir -Recurse -Include "*.jpg", "*.jpeg" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -like "pns_*jpeg_only*" -and $_.LastWriteTime -ge $cutoff } |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if (-not $jpeg) {
        $jpeg = Get-ChildItem $pullDir -Recurse -Include "*.jpg", "*.jpeg" -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "pns_*jpeg_only*" } |
            Sort-Object Name -Descending |
            Select-Object -First 1
    }
}
if (-not $jpeg) {
    Write-Host "FAIL: no JPEG pulled from DCIM" -ForegroundColor Red
    adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null
    exit 1
}

$iccOut = & $exiftool -icc_profile:all -ProfileDescription -ColorSpace $jpeg.FullName 2>&1 | Out-String
$iccOut | Set-Content (Join-Path $outDir "exiftool_icc.txt") -Encoding UTF8
Write-Host $iccOut

$p3Ok = ($iccOut -match "Display P3") -or ([System.Text.Encoding]::ASCII.GetString([System.IO.File]::ReadAllBytes($jpeg.FullName)).Contains("Display P3"))
$iccPresent = ($iccOut -match "ICC Profile") -or ($iccOut -match "Profile Description") -or $p3Ok
$applyOk = $logRaw -match "apply JPEG metadata ok.*icc=true"
$pass = $p3Ok -and ($iccPresent -or $applyOk)

$report = [ordered]@{
    schema = "pns.jpeg_icc_verify.v1"
    pass = $pass
    skipped = $false
    jpeg = $jpeg.FullName
    displayP3 = [bool]$p3Ok
    iccPresent = [bool]$iccPresent
    applyJpegMetadataIcc = [bool]$applyOk
    outDir = $outDir
}
$report | ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8
adb -s $Serial shell am force-stop $pkg 2>$null | Out-Null

if (-not $pass) {
    Write-Host "FAIL jpeg ICC gate - $outDir" -ForegroundColor Red
    exit 1
}
Write-Host "PASS jpeg ICC gate (Display P3) - $outDir" -ForegroundColor Green
