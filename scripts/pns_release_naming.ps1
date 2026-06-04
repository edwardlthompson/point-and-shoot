#Requires -Version 5.1
<#
.SYNOPSIS
  Shared release artifact naming + semver versionCode helpers for Point & Shoot.

.DESCRIPTION
  Industry-aligned conventions:
  - versionName: semver in app/build.gradle.kts (e.g. 0.14.0-beta.6) — user-visible.
  - versionCode: monotonic positive integer (Android requirement); derived from semver when
    the encoded value exceeds the installed baseline so upgrades keep working.
  - APK filename: {AppDisplayName}-{versionName}.apk (hyphens, no spaces).

  Dot-source from release scripts:
    . "$PSScriptRoot\pns_release_naming.ps1"
#>
Set-StrictMode -Version Latest

function Get-PnsReleaseConfig {
    param([string]$ConfigPath)
    if (-not (Test-Path -LiteralPath $ConfigPath)) {
        throw "Missing release config: $ConfigPath"
    }
    return ([System.IO.File]::ReadAllText($ConfigPath) | ConvertFrom-Json)
}

function ConvertTo-PnsSemverTag {
    param([string]$VersionName)
    $clean = $VersionName.Trim().TrimStart('v')
    if ($clean -notmatch '^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$') {
        throw "versionName '$VersionName' is not semver (expected MAJOR.MINOR.PATCH[-prerelease])"
    }
    return $clean
}

function ConvertTo-PnsGitTag {
    param(
        [string]$VersionName,
        [string]$TagPrefix = "v"
    )
    $semver = ConvertTo-PnsSemverTag $VersionName
    if ([string]::IsNullOrEmpty($TagPrefix)) { return $semver }
    return "$TagPrefix$semver"
}

function ConvertTo-PnsVersionCodeFromSemver {
    <#
      Encode semver into versionCode (Android monotonic integer):
        MAJOR * 1_000_000 + MINOR * 10_000 + PATCH * 100 + prereleaseSerial

      Stable 1.2.3 -> 1000203
      Pre-release 0.14.0-beta.6 -> 140006
      Pre-release without numeric tail (e.g. -rc) -> prereleaseSerial = 99
    #>
    param([string]$VersionName)

    $semver = ConvertTo-PnsSemverTag $VersionName
    if ($semver -match '^(\d+)\.(\d+)\.(\d+)(?:-(.+))?$') {
        $major = [int]$Matches[1]
        $minor = [int]$Matches[2]
        $patch = [int]$Matches[3]
        $pre = $Matches[4]
        $preSerial = 0
        if ($pre) {
            if ($pre -match '(?:^|\.)beta\.(\d+)$') {
                $preSerial = [int]$Matches[1]
            } elseif ($pre -match '(?:^|\.)alpha\.(\d+)$') {
                $preSerial = [int]$Matches[1]
            } elseif ($pre -match '(?:^|\.)rc\.(\d+)$') {
                $preSerial = [int]$Matches[1]
            } elseif ($pre -match '(\d+)$') {
                $preSerial = [int]$Matches[1]
            } else {
                $preSerial = 99
            }
        }
        return ($major * 1000000) + ($minor * 10000) + ($patch * 100) + $preSerial
    }
    throw "Could not parse semver for versionCode: $VersionName"
}

function Get-PnsNextVersionCode {
    param(
        [string]$VersionName,
        [int]$CurrentVersionCode,
        [ValidateSet("semverOrIncrement", "incrementOnly", "semverAtStable")]
        [string]$Policy = "semverOrIncrement",
        [int]$Step = 1
    )

    if ($Policy -eq "incrementOnly") {
        return $CurrentVersionCode + $Step
    }

    $encoded = ConvertTo-PnsVersionCodeFromSemver $VersionName
    $next = $CurrentVersionCode + $Step

    if ($Policy -eq "semverAtStable") {
        $semver = ConvertTo-PnsSemverTag $VersionName
        if ($semver -notmatch '-' -and $encoded -gt $CurrentVersionCode) {
            if ($encoded -gt $next) { $next = $encoded }
        }
        return $next
    }

    # semverOrIncrement: adopt encoding only when it stays above the installed baseline.
    if ($encoded -gt $CurrentVersionCode -and $encoded -gt $next) {
        $next = $encoded
    }
    return $next
}

function Get-PnsReleaseApkFileName {
    param(
        [string]$VersionName,
        [string]$AppDisplayName = "Point-and-Shoot",
        [string]$Template = ""
    )

    $semver = ConvertTo-PnsSemverTag $VersionName
    if ([string]::IsNullOrWhiteSpace($Template)) {
        return "$AppDisplayName-$semver.apk"
    }
    return $Template `
        -replace '\{appDisplayName\}', $AppDisplayName `
        -replace '\{versionName\}', $semver
}

function Get-PnsNextSemverVersionName {
    param([string]$CurrentVersionName)

    $semver = ConvertTo-PnsSemverTag $CurrentVersionName
    if ($semver -match '^(.+-beta\.)(\d+)$') {
        return "$($Matches[1])$([int]$Matches[2] + 1)"
    }
    if ($semver -match '^(.+-alpha\.)(\d+)$') {
        return "$($Matches[1])$([int]$Matches[2] + 1)"
    }
    if ($semver -match '^(.+-rc\.)(\d+)$') {
        return "$($Matches[1])$([int]$Matches[2] + 1)"
    }
    if ($semver -match '^(\d+)\.(\d+)\.(\d+)$') {
        return "$($Matches[1]).$($Matches[2]).$([int]$Matches[3] + 1)"
    }
    throw "Cannot auto-increment versionName '$CurrentVersionName' — pass an explicit tag."
}
