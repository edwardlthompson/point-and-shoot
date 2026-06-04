#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 14.13 — host release APK packaging (no GitHub CLI).

.DESCRIPTION
  assembleRelease (optional), copy to dist/{AppDisplayName}-{versionName}.apk, zipalign -c -v 4.
  Naming policy: scripts/release_config.v1.json + scripts/pns_release_naming.ps1.

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

. "$PSScriptRoot\pns_release_naming.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleKts = Join-Path $repoRoot "app\build.gradle.kts"
$configPath = Join-Path $PSScriptRoot "release_config.v1.json"
$config = Get-PnsReleaseConfig $configPath

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
    $gradleText = Get-Content -LiteralPath $gradleKts -Raw
    if ($gradleText -notmatch 'versionName\s*=\s*"([^"]+)"') {
        throw "versionName not found in $gradleKts"
    }
    $versionName = ConvertTo-PnsSemverTag $Matches[1]
    $destApkName = Get-PnsReleaseApkFileName `
        -VersionName $versionName `
        -AppDisplayName $config.appDisplayName `
        -Template $config.apkFileNameTemplate
    Write-Host "[pns_release_packaging] versionName=$versionName artifact=$destApkName"

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
    $destApk = Join-Path $destDir $destApkName
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
        packagedApkName = $destApkName
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
