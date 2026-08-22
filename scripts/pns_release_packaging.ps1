#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 14.13 - host release APK packaging (no GitHub CLI).

.DESCRIPTION
  assembleRelease (optional), copy to dist/{AppDisplayName}-{versionName}.apk, zipalign -c -v 4.
  Naming policy: scripts/release_config.v1.json + scripts/pns_release_naming.ps1.

.PARAMETER SkipAssemble
  Use existing app/build/outputs/apk/release/app-release.apk.

.PARAMETER OutDir
  Host folder for the renamed APK (default: dist).

.PARAMETER AllowDebugKey
  Permit debug-key fallback. Forbidden for /ship and pns_github_release -Publish.
#>
param(
    [switch]$SkipAssemble,
    [string]$OutDir = "dist",
    [switch]$AllowDebugKey
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot\pns_release_naming.ps1"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradleKts = Join-Path $repoRoot "app\build.gradle.kts"
$configPath = Join-Path $PSScriptRoot "release_config.v1.json"
$config = Get-PnsReleaseConfig $configPath

function Get-AndroidSdkDir([string]$root) {
    if ($env:ANDROID_HOME -and (Test-Path -LiteralPath $env:ANDROID_HOME)) {
        return $env:ANDROID_HOME
    }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path -LiteralPath $env:ANDROID_SDK_ROOT)) {
        return $env:ANDROID_SDK_ROOT
    }
    $localProps = Join-Path $root "local.properties"
    if (-not (Test-Path -LiteralPath $localProps)) {
        throw "Missing local.properties (sdk.dir required for zipalign)"
    }
    foreach ($line in Get-Content -LiteralPath $localProps) {
        if ($line -match '^\s*sdk\.dir\s*=\s*(.+)\s*$') {
            $raw = $Matches[1].Trim().Trim('"')
            # Gradle local.properties: C\:/Users/... or C:\\Users\\...
            $normalized = $raw -replace '^([A-Za-z])\\:', '$1:'
            $normalized = $normalized -replace '/', [IO.Path]::DirectorySeparatorChar
            $normalized = $normalized -replace '\\\\', [IO.Path]::DirectorySeparatorChar
            return $normalized
        }
    }
    throw "sdk.dir not found in local.properties"
}

function Find-BuildTool([string]$sdkDir, [string]$leafWin, [string]$leafUnix) {
    $bt = Join-Path $sdkDir "build-tools"
    if (-not (Test-Path -LiteralPath $bt)) { throw "No build-tools under $sdkDir" }
    $dirs = Get-ChildItem -LiteralPath $bt -Directory | Sort-Object Name -Descending
    foreach ($d in $dirs) {
        $win = Join-Path $d.FullName $leafWin
        if (Test-Path -LiteralPath $win) { return $win }
        $unix = Join-Path $d.FullName $leafUnix
        if (Test-Path -LiteralPath $unix) { return $unix }
    }
    throw "$leafWin / $leafUnix not found under $bt"
}

function Find-Zipalign([string]$sdkDir) {
    return Find-BuildTool $sdkDir "zipalign.exe" "zipalign"
}

function Find-Apksigner([string]$sdkDir) {
    return Find-BuildTool $sdkDir "apksigner.bat" "apksigner"
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

    if (-not $AllowDebugKey) {
        Assert-PnsReleaseSigningReady -RepoRoot $repoRoot
    }

    if (-not $SkipAssemble) {
        $assembleOut = & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleRelease 2>&1
        $assembleCode = $LASTEXITCODE
        $assembleOut | ForEach-Object { Write-Host $_ }
        if ($assembleCode -ne 0) { throw "assembleRelease failed" }
        $joined = ($assembleOut | Out-String)
        if ($joined -match 'falling back to debug key') {
            if ($AllowDebugKey) {
                Write-Host "[pns_release_packaging] WARNING: debug-key fallback (AllowDebugKey)"
            } else {
                throw "assembleRelease used debug-key fallback. /ship requires keystore.properties or ANDROID_KEYSTORE_*."
            }
        }
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

    $apksigner = Find-Apksigner $sdkDir
    Write-Host "[pns_release_packaging] apksigner: $apksigner"
    $certLines = & $apksigner verify --print-certs $destApk 2>&1
    $certCode = $LASTEXITCODE
    $certOut = $certLines | Out-String
    if ($certCode -ne 0) { throw "apksigner verify failed" }
    Write-Host $certOut
    if ($certOut -match 'CN=Android Debug') {
        if ($AllowDebugKey) {
            Write-Host "[pns_release_packaging] WARNING: APK is debug-signed (AllowDebugKey)"
        } else {
            throw "APK is debug-signed (CN=Android Debug). /ship requires a production keystore."
        }
    }

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
