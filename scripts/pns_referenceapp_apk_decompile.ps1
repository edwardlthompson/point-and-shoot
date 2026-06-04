<#
.SYNOPSIS
  Pull ReferenceCam base.apk, decompile with jadx, run referenceapp_decompile_scan.py.

.EXAMPLE
  .\scripts\pns_referenceapp_apk_decompile.ps1 -Serial <serial>
#>
param(
    [string]$Serial = "",
    [string]$ReferenceAppPackage = "com.riseupgames.referenceapp2"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$outRoot = Join-Path $projRoot "hfr-runs\referenceapp_apk_decompile"
$apk = Join-Path $outRoot "referenceapp2_base.apk"
$jadxDir = Join-Path $projRoot "tools\jadx"
$jadxBat = Join-Path $jadxDir "bin\jadx.bat"
$src = Join-Path $outRoot "jadx_sources"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }

if (-not (Test-Path $jadxBat)) {
    New-Item -ItemType Directory -Force -Path $jadxDir | Out-Null
    $zip = Join-Path $jadxDir "jadx.zip"
    Write-Host "[referenceapp_decompile] downloading jadx 1.5.1..."
    Invoke-WebRequest -Uri "https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $jadxDir -Force
    Remove-Item $zip
}

New-Item -ItemType Directory -Force -Path $outRoot | Out-Null
$pathOut = if ($Serial) { & adb -s $Serial shell pm path $ReferenceAppPackage 2>&1 } else { & adb shell pm path $ReferenceAppPackage 2>&1 }
$baseLine = ($pathOut -split "`n" | Where-Object { $_ -match "^package:(.+\.apk)" } | Select-Object -First 1)
if (-not $baseLine) { throw "ReferenceCam package not found: $ReferenceAppPackage" }
$remote = ($baseLine -replace "^package:", "").Trim()
Write-Host "[referenceapp_decompile] pull $remote"
if ($Serial) { & adb -s $Serial pull $remote $apk } else { & adb pull $remote $apk }

Write-Host "[referenceapp_decompile] jadx -> $src"
& $jadxBat -d $src --show-bad-code --no-res $apk 2>&1 | Out-Host

$scanPy = Join-Path $PSScriptRoot "referenceapp_decompile_scan.py"
if (Test-Path $scanPy) {
    python $scanPy $src (Join-Path $outRoot "scan.json")
}

Write-Host "[referenceapp_decompile] done. APK: $apk"
