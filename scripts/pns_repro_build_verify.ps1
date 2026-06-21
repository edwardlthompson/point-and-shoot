# Point & Shoot — reproducible build host smoke (Milestone T Sprint T.11).
#
# Verifies version pins, dependency lockfile, legal files, and SBOM component fingerprint
# without requiring a full release APK build.
#
# Usage:
#   .\scripts\pns_repro_build_verify.ps1
#   .\scripts\pns_repro_build_verify.ps1 -ApkPath app\build\outputs\apk\release\app-release.apk
#   .\scripts\pns_repro_build_verify.ps1 -WriteSbomBaseline   # refresh docs/repro/sbom-purl-list.sha256

param(
  [string]$ProjectRoot = "",
  [string]$ApkPath = "",
  [switch]$WriteSbomBaseline
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$failures = New-Object System.Collections.Generic.List[string]

function Add-Fail {
  param([string]$Message)
  $failures.Add("FAIL: $Message")
}

function Get-Sha256Hex([string]$Text) {
  $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
  $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
  return ($hash | ForEach-Object { $_.ToString("x2") }) -join ""
}

$gradlePath = Join-Path $ProjectRoot "app\build.gradle.kts"
$metaYml = Join-Path $ProjectRoot "metadata\metadata.yml"
$lockfile = Join-Path $ProjectRoot "app\gradle.lockfile"
$privacy = Join-Path $ProjectRoot "PRIVACY.md"
$notice = Join-Path $ProjectRoot "NOTICE"
$readme = Join-Path $ProjectRoot "README.md"
$sbomBaseline = Join-Path $ProjectRoot "docs\repro\sbom-purl-list.sha256"

foreach ($required in @($gradlePath, $metaYml, $privacy, $notice, $readme)) {
  if (-not (Test-Path -LiteralPath $required)) {
    Add-Fail "missing required file '$required'"
  }
}

if ($failures.Count -eq 0) {
  $gradle = [System.IO.File]::ReadAllText($gradlePath)
  $yaml = [System.IO.File]::ReadAllText($metaYml)
  $readmeText = [System.IO.File]::ReadAllText($readme)

  if ($gradle -notmatch 'versionCode\s*=\s*(\d+)') {
    Add-Fail "could not parse versionCode from app/build.gradle.kts"
  } else {
    $gradleCode = [int]$Matches[1]
  }
  if ($gradle -notmatch 'versionName\s*=\s*"([^"]+)"') {
    Add-Fail "could not parse versionName from app/build.gradle.kts"
  } else {
    $gradleName = $Matches[1]
  }

  if ($yaml -notmatch "versionCode:\s*$gradleCode\b") {
    Add-Fail "metadata.yml versionCode must match app ($gradleCode)"
  }
  if ($yaml -notmatch [regex]::Escape("versionName: '$gradleName'")) {
    Add-Fail "metadata.yml versionName must match app ('$gradleName')"
  }

  if (-not (Test-Path -LiteralPath $lockfile)) {
    Add-Fail 'missing app/gradle.lockfile - run :app:dependencies --write-locks after enabling dependency locking'
  } elseif ((Get-Item -LiteralPath $lockfile).Length -lt 32) {
    Add-Fail "app/gradle.lockfile looks empty or truncated"
  }

  if ($readmeText -notmatch 'PRIVACY\.md') {
    Add-Fail "README.md must link to PRIVACY.md"
  }
  if ($readmeText -notmatch 'NOTICE') {
    Add-Fail "README.md must link to NOTICE"
  }

  $privacyText = [System.IO.File]::ReadAllText($privacy)
  if ($privacyText -notmatch 'ML Kit') {
    Add-Fail "PRIVACY.md should document on-device ML Kit usage"
  }
  $noticeText = [System.IO.File]::ReadAllText($notice)
  if ($noticeText -notmatch 'LICENSES\.md') {
    Add-Fail "NOTICE must reference LICENSES.md"
  }

  # SBOM stable fingerprint (sorted purls, excludes volatile metadata timestamp/uuid)
  $sbomScript = Join-Path $PSScriptRoot "pns_sbom.ps1"
  $sbomJson = & $sbomScript -ProjectRoot $ProjectRoot 2>&1 | Out-String
  try {
    $parsed = $sbomJson | ConvertFrom-Json
    if ($parsed.bomFormat -ne 'CycloneDX') { throw "bomFormat not CycloneDX" }
    if ($parsed.components.Count -lt 1) { throw "SBOM components empty" }
    $purls = @($parsed.components | ForEach-Object { $_.purl } | Sort-Object)
    $fingerprint = Get-Sha256Hex ($purls -join "`n")
    if ($WriteSbomBaseline.IsPresent) {
      $reproDir = Split-Path -Parent $sbomBaseline
      if (-not (Test-Path -LiteralPath $reproDir)) {
        New-Item -ItemType Directory -Path $reproDir -Force | Out-Null
      }
      [System.IO.File]::WriteAllText($sbomBaseline, "$fingerprint`n", [System.Text.UTF8Encoding]::new($false))
      Write-Host "Wrote SBOM purl fingerprint baseline: $sbomBaseline"
    } elseif (-not (Test-Path -LiteralPath $sbomBaseline)) {
    Add-Fail 'missing docs/repro/sbom-purl-list.sha256 - run pns_repro_build_verify.ps1 -WriteSbomBaseline'
    } else {
      $expected = ([System.IO.File]::ReadAllText($sbomBaseline)).Trim()
      if ($expected -ne $fingerprint) {
        Add-Fail ("SBOM purl fingerprint drift (expected {0}, got {1}) - refresh with -WriteSbomBaseline if deps changed intentionally" -f $expected, $fingerprint)
      }
    }
  } catch {
    Add-Fail "SBOM parse/verify failed: $_"
  }

  # Signing cert class (optional APK)
  if (-not [string]::IsNullOrWhiteSpace($ApkPath)) {
    $apkFull = Resolve-Path -LiteralPath $ApkPath -ErrorAction SilentlyContinue
    if (-not $apkFull) {
      Add-Fail "ApkPath not found: $ApkPath"
    } else {
      $apksigner = $null
      if ($env:ANDROID_HOME) {
        $candidates = Get-ChildItem -Path (Join-Path $env:ANDROID_HOME "build-tools") -Recurse -Filter "apksigner.bat" -ErrorAction SilentlyContinue |
          Sort-Object FullName -Descending
        if ($candidates) { $apksigner = $candidates[0].FullName }
      }
      if (-not $apksigner) {
        Add-Fail "ANDROID_HOME build-tools apksigner not found (needed for -ApkPath cert class check)"
      } else {
        $verifyOut = & $apksigner verify --print-certs $apkFull.Path 2>&1 | Out-String
        if ($verifyOut -match 'DEBUG') {
          Write-Host "WARN: APK appears DEBUG-signed (expected for local debug-key release smoke)"
        } elseif ($verifyOut -match 'Signer #1 certificate') {
          Write-Host "OK: APK has release-style signing certificate"
        } else {
          Add-Fail "apksigner verify output unexpected for $($apkFull.Path)"
        }
      }
    }
  } else {
    Write-Host "SKIP: APK cert class (pass -ApkPath for apksigner check)"
  }
}

if ($failures.Count -gt 0) {
  foreach ($f in $failures) { Write-Host $f }
  Write-Host ('REPRO BUILD VERIFY: FAIL (' + $failures.Count + ' issues)')
  exit 1
}

Write-Host "REPRO BUILD VERIFY: PASS (version sync, lockfile, legal links, SBOM fingerprint)"
exit 0
