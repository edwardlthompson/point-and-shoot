#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 14.13 — host release APK packaging (no GitHub CLI).

.DESCRIPTION
  assembleRelease (optional), copy to dist/Point-and-Shoot_<versionName>.apk, zipalign -c -v 4.

.PARAMETER SkipAssemble
  Use existing app/build/outputs/apk/release/app-release.apk.

.PARAMETER OutDir
  Host folder for the renamed APK (default: dist).
#>
param(
    [switch]$SkipAssemble,
    [string]$OutDir = "dist"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleKts = Join-Path $repoRoot "app\build.gradle.kts"

function Get-VersionNameFromGradle([string]$path) {
    $text = Get-Content -LiteralPath $path -Raw
    if ($text -match 'versionName\s*=\s*"([^"]+)"') { return $Matches[1] }
    throw "versionName not found in $path"
}

function Get-AndroidSdkDir([string]$root) {
    $localProps = Join-Path $root "local.properties"
    if (-not (Test-Path -LiteralPath $localProps)) {
        throw "Missing local.properties (sdk.dir required for zipalign)"
    }
    foreach ($line in Get-Content -LiteralPath $localProps) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.+)\s*$') {
            $raw = $Matches[1].Trim() -replace '\\', '\'
            return $raw -replace '\\', [IO.Path]::DirectorySeparatorChar
        }
    }
    throw "sdk.dir not found in local.properties"
}

function Find-Zipalign([string]$sdkDir) {
    $bt = Join-Path $sdkDir "build-tools"
    if (-not (Test-Path -LiteralPath $bt)) { throw "No build-tools under $sdkDir" }
    $dirs = Get-ChildItem -LiteralPath $bt -Directory | Sort-Object Name -Descending
    foreach ($d in $dirs) {
        $exe = Join-Path $d.FullName "zipalign.exe"
        if (Test-Path -LiteralPath $exe) { return $exe }
    }
    throw "zipalign.exe not found under $bt"
}

Push-Location $repoRoot
try {
    $versionName = Get-VersionNameFromGradle $gradleKts
    Write-Host "[pns_release_packaging] versionName=$versionName"

    if (-not $SkipAssemble) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw "assembleRelease failed" }
    }

    $srcApk = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path -LiteralPath $srcApk)) {
        throw "Release APK missing: $srcApk (run assembleRelease or drop -SkipAssemble)"
    }

    $destDir = Join-Path $repoRoot $OutDir
    if (-not (Test-Path -LiteralPath $destDir)) {
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
    }
    $destApk = Join-Path $destDir "Point-and-Shoot_$versionName.apk"
    Copy-Item -LiteralPath $srcApk -Destination $destApk -Force
    $sizeMb = [math]::Round((Get-Item -LiteralPath $destApk).Length / 1MB, 2)
    Write-Host "[pns_release_packaging] copied -> $destApk ($sizeMb MB)"

    $sdkDir = Get-AndroidSdkDir $repoRoot
    $zipalign = Find-Zipalign $sdkDir
    Write-Host "[pns_release_packaging] zipalign: $zipalign"
    & $zipalign -c -v 4 $destApk
    if ($LASTEXITCODE -ne 0) { throw "zipalign verification failed" }

    $runsDir = Join-Path $repoRoot "hfr-runs"
    if (-not (Test-Path -LiteralPath $runsDir)) {
        New-Item -ItemType Directory -Path $runsDir -Force | Out-Null
    }
    $jsonPath = Join-Path $runsDir ("release_packaging_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
    @{
        timestamp = (Get-Date -Format "o")
        versionName = $versionName
        sourceApk = $srcApk
        packagedApk = $destApk
        sizeBytes = (Get-Item -LiteralPath $destApk).Length
        zipalignOk = $true
    } | ConvertTo-Json | Set-Content -Path $jsonPath -Encoding UTF8
    Write-Host "[pns_release_packaging] HOST_PASS artifact=$jsonPath"
    exit 0
} finally {
    Pop-Location
}
