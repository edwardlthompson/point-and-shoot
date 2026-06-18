#Requires -Version 5.1
<#
.SYNOPSIS
  Resolve Android SDK platform-tools adb.exe and optionally prepend it so PATH matches Android Studio / Gradle.

.DESCRIPTION
  Compares Get-Command adb to %ANDROID_SDK_ROOT%/platform-tools/adb.exe (or ANDROID_HOME, then
  %LOCALAPPDATA%\Android\Sdk). When they differ, client/server version mismatches are common;
  dot-source this script with -PrependToPath at the start of device automation.

.PARAMETER PrependToPath
  Prepend SDK platform-tools directory to $env:PATH for the current process.

.PARAMETER Quiet
  Suppress informational messages (warnings about PATH vs SDK mismatch still print unless you also use -Quiet inconsistently — currently mismatch uses Write-Warning always).

.PARAMETER EmitPath
  Print full path to adb.exe (SDK if present, else first on PATH) and exit. For subshells / CI.

.PARAMETER CheckOnly
  Exit 2 if PATH adb exists and differs from SDK adb; exit 0 if aligned or SDK missing. Does not modify PATH unless -PrependToPath is also set.
#>
param(
  [switch]$PrependToPath,
  [switch]$Quiet,
  [switch]$EmitPath,
  [switch]$CheckOnly
)

$ErrorActionPreference = "Stop"

function Get-PnsPlatformToolsAdbPath {
    param([Parameter(Mandatory = $true)][string]$SdkRoot)
    $pt = Join-Path $SdkRoot "platform-tools"
    foreach ($name in @("adb", "adb.exe")) {
        $candidate = Join-Path $pt $name
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    return $null
}

function Get-PnsAndroidSdkRoot {
  foreach ($c in @($env:ANDROID_SDK_ROOT, $env:ANDROID_HOME)) {
    if ([string]::IsNullOrWhiteSpace($c)) { continue }
    $root = $c.Trim().TrimEnd('\', '/')
    if (Get-PnsPlatformToolsAdbPath -SdkRoot $root) { return $root }
  }
  if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
    $local = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Get-PnsPlatformToolsAdbPath -SdkRoot $local) { return $local }
  }
  return $null
}

$sdk = Get-PnsAndroidSdkRoot
$canonical = if ($sdk) { Get-PnsPlatformToolsAdbPath -SdkRoot $sdk } else { $null }
$pathAdbObj = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -First 1
$pathAdb = if ($pathAdbObj) { $pathAdbObj.Source } else { $null }

if ($EmitPath) {
  if ($canonical -and (Test-Path -LiteralPath $canonical)) {
    Write-Output $canonical
  }
  elseif ($pathAdb) {
    Write-Output $pathAdb
  }
  else {
    throw "adb not found (no SDK platform-tools and not on PATH)."
  }
  return
}

$mismatch = $false
if ($canonical -and (Test-Path -LiteralPath $canonical) -and $pathAdb -and ($pathAdb -ne $canonical)) {
  $mismatch = $true
  Write-Warning "[pns_adb] PATH adb differs from Android SDK platform-tools:`n  PATH: $pathAdb`n  SDK:  $canonical`n  Use -PrependToPath (or dot-source this script with -PrependToPath) so device scripts match Studio/Gradle."
}

if ($CheckOnly.IsPresent -and $mismatch) {
  exit 2
}

if ($canonical -and $PrependToPath.IsPresent) {
  $pt = Split-Path -Parent $canonical
  if ($IsLinux -or $IsMacOS) {
    $env:PATH = "${pt}:$env:PATH"
  } else {
    $env:PATH = "$pt;$env:PATH"
  }
  if (-not $Quiet) {
    Write-Host "[pns_adb] Prepended platform-tools to PATH: $pt"
  }
}
