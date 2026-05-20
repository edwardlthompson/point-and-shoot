<#
.SYNOPSIS
  Pull ProShot base.apk, decompile with jadx, run proshot_decompile_scan.py.

.EXAMPLE
  .\scripts\pns_proshot_apk_decompile.ps1 -Serial 8bf09993
#>
param(
    [string]$Serial = "",
    [string]$ProShotPackage = "com.riseupgames.proshot2"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$outRoot = Join-Path $projRoot "hfr-runs\proshot_apk_decompile"
$apk = Join-Path $outRoot "proshot2_base.apk"
$jadxDir = Join-Path $projRoot "tools\jadx"
$jadxBat = Join-Path $jadxDir "bin\jadx.bat"
$src = Join-Path $outRoot "jadx_sources"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }

if (-not (Test-Path $jadxBat)) {
    New-Item -ItemType Directory -Force -Path $jadxDir | Out-Null
    $zip = Join-Path $jadxDir "jadx.zip"
    Write-Host "[proshot_decompile] downloading jadx 1.5.1..."
    Invoke-WebRequest -Uri "https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip" -OutFile $zip -UseBasicParsing
    Expand-Archive -Path $zip -DestinationPath $jadxDir -Force
    Remove-Item $zip
}

New-Item -ItemType Directory -Force -Path $outRoot | Out-Null
$pathOut = if ($Serial) { & adb -s $Serial shell pm path $ProShotPackage 2>&1 } else { & adb shell pm path $ProShotPackage 2>&1 }
$baseLine = ($pathOut -split "`n" | Where-Object { $_ -match "^package:(.+\.apk)" } | Select-Object -First 1)
if (-not $baseLine) { throw "ProShot package not found: $ProShotPackage" }
$remote = ($baseLine -replace "^package:", "").Trim()
Write-Host "[proshot_decompile] pull $remote"
if ($Serial) { & adb -s $Serial pull $remote $apk } else { & adb pull $remote $apk }

Write-Host "[proshot_decompile] jadx -> $src"
& $jadxBat -d $src --show-bad-code --no-res $apk 2>&1 | Out-Host

$scanPy = Join-Path $PSScriptRoot "proshot_decompile_scan.py"
if (Test-Path $scanPy) {
    python $scanPy $src (Join-Path $outRoot "scan.json")
}

Write-Host "[proshot_decompile] done. APK: $apk"
