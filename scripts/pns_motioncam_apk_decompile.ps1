<#
.SYNOPSIS
  Pull MotionCam base.apk, decompile with jadx, run proshot_decompile_scan.py (extended needles).

.EXAMPLE
  .\scripts\pns_motioncam_apk_decompile.ps1 -Serial <serial>
#>
param(
    [string]$Serial = "",
    [string]$MotionCamPackage = "com.motioncam.pro"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$outRoot = Join-Path $projRoot "hfr-runs\motioncam_apk_decompile"
$apk = Join-Path $outRoot "motioncam_pro_base.apk"
$jadxDir = Join-Path $projRoot "tools\jadx"
$jadxBat = Join-Path $jadxDir "bin\jadx.bat"
$src = Join-Path $outRoot "jadx_sources"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }

if (-not (Test-Path $jadxBat)) {
    New-Item -ItemType Directory -Force -Path $jadxDir | Out-Null
    $zip = Join-Path $jadxDir "jadx.zip"
    Write-Host "[motioncam_decompile] downloading jadx 1.5.1..."
    Invoke-WebRequest -Uri "https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $jadxDir -Force
    Remove-Item $zip
}

New-Item -ItemType Directory -Force -Path $outRoot | Out-Null
$pathOut = if ($Serial) { & adb -s $Serial shell pm path $MotionCamPackage 2>&1 } else { & adb shell pm path $MotionCamPackage 2>&1 }
$baseLine = ($pathOut -split "`n" | Where-Object { $_ -match "^package:(.+/base\.apk)" } | Select-Object -First 1)
if (-not $baseLine) { throw "MotionCam package not found: $MotionCamPackage (install from Play/store on device first)" }
$remote = ($baseLine -replace "^package:", "").Trim()
Write-Host "[motioncam_decompile] pull $remote"
if ($Serial) { & adb -s $Serial pull $remote $apk } else { & adb pull $remote $apk }

Write-Host "[motioncam_decompile] jadx -> $src"
& $jadxBat -d $src --show-bad-code --no-res $apk 2>&1 | Out-Host

$scanPy = Join-Path $PSScriptRoot "proshot_decompile_scan.py"
if (Test-Path $scanPy) {
    python $scanPy $src (Join-Path $outRoot "scan.json") --profile motioncam
}

Write-Host "[motioncam_decompile] done. APK: $apk"
