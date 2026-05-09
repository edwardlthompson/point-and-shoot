# Point & Shoot - toolchain verification gate (run after Kotlin or PowerShell changes).
# Proves: Gradle assembleDebug, UTF-8 host scripts + Kotlin sources, PowerShell parse OK,
#         FOSS dep-audit (no Play Services / proprietary SDK references), and (with -RunTests)
#         JVM unit tests (:app:testDebugUnitTest).
# Usage:
#   .\scripts\pns_verify_toolchain.ps1                              # full
#   .\scripts\pns_verify_toolchain.ps1 -SkipGradle                  # docs-only
#   .\scripts\pns_verify_toolchain.ps1 -RunTests                    # full + unit tests
#   .\scripts\pns_verify_toolchain.ps1 -SkipGradle -RunTests        # tests only (still needs Gradle)

param(
  [string]$ProjectRoot = "",
  [switch]$SkipGradle,
  [switch]$RunTests,
  [string]$ReportDir = ""
)

$ErrorActionPreference = "Stop"

function Write-Verify([string]$msg) {
  Write-Host "[verify] $msg"
}

function Test-AsciiLikeSourceByte([byte]$x) {
  return ($x -ge 0x20 -and $x -le 0x7E) -or ($x -in @(0x09, 0x0A, 0x0D))
}

function Test-LikelyUtf16LeFile([string]$path) {
  $b = [System.IO.File]::ReadAllBytes($path)
  if ($b.Length -lt 8) { return $false }
  if ($b[0] -eq 0xFF -and $b[1] -eq 0xFE) { return $true }
  # UTF-16 LE without BOM: ASCII text has 0x00 on odd indices for the first several code units.
  if ($b[1] -eq 0 -and $b[3] -eq 0 -and $b[5] -eq 0 -and $b[7] -eq 0 -and
      (Test-AsciiLikeSourceByte $b[0]) -and (Test-AsciiLikeSourceByte $b[2]) -and (Test-AsciiLikeSourceByte $b[4]) -and (Test-AsciiLikeSourceByte $b[6])) {
    return $true
  }
  return $false
}

function Test-Ps1ParseOk([string]$path) {
  $tokens = $null
  $errors = $null
  $null = [System.Management.Automation.Language.Parser]::ParseFile($path, [ref]$tokens, [ref]$errors)
  if ($errors -and $errors.Count -gt 0) {
    return ($errors | ForEach-Object { $_.ToString() }) -join "; "
  }
  return $null
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$report = New-Object System.Collections.Generic.List[string]
$failed = $false

if (-not $SkipGradle.IsPresent) {
  Write-Verify "Gradle assembleDebug (no-daemon) in $ProjectRoot"
  function Test-WindowsOs {
    if ($null -ne $PSVersionTable.Platform) {
      return $PSVersionTable.Platform -eq 'Win32NT'
    }
    return $env:OS -match '(?i)Windows'
  }
  $gradlewBat = Join-Path $ProjectRoot "gradlew.bat"
  $gradlewSh = Join-Path $ProjectRoot "gradlew"
  $gradlew = $null
  if ((Test-Path -LiteralPath $gradlewBat) -and (Test-WindowsOs)) {
    $gradlew = $gradlewBat
  } elseif (Test-Path -LiteralPath $gradlewSh) {
    $gradlew = $gradlewSh
  } elseif (Test-Path -LiteralPath $gradlewBat) {
    $gradlew = $gradlewBat
  }
  if (-not $gradlew) {
    [void]$report.Add("FAIL: gradlew not found (expected gradlew or gradlew.bat)")
    $failed = $true
  } else {
    Push-Location $ProjectRoot
    try {
      & $gradlew assembleDebug --no-daemon
      if ($LASTEXITCODE -ne 0) {
        [void]$report.Add("FAIL: assembleDebug exit code $LASTEXITCODE")
        $failed = $true
      } else {
        [void]$report.Add("OK: assembleDebug BUILD SUCCESSFUL")
      }
    } finally {
      Pop-Location
    }
  }
} else {
  [void]$report.Add("SKIP: Gradle (-SkipGradle)")
}

$scriptFiles = @(
  (Join-Path $PSScriptRoot "pns_hfr_autorun.ps1"),
  (Join-Path $PSScriptRoot "pns_probe_watch.ps1"),
  (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1"),
  (Join-Path $PSScriptRoot "pns_license_inventory.ps1"),
  (Join-Path $PSScriptRoot "pns_sbom.ps1"),
  (Join-Path $PSScriptRoot "pns_install_ndk.ps1")
)

foreach ($sf in $scriptFiles) {
  $leaf = Split-Path $sf -Leaf
  if (-not (Test-Path -LiteralPath $sf)) {
    [void]$report.Add("SKIP: $leaf (missing)")
    continue
  }
  if (Test-LikelyUtf16LeFile $sf) {
    [void]$report.Add("FAIL: $leaf looks UTF-16 LE - re-save as UTF-8")
    $failed = $true
  } else {
    [void]$report.Add("OK: $leaf encoding (not UTF-16 LE)")
  }
  $parseErr = Test-Ps1ParseOk $sf
  if ($parseErr) {
    [void]$report.Add("FAIL: $leaf parse: $parseErr")
    $failed = $true
  } else {
    [void]$report.Add("OK: $leaf PowerShell parse")
  }
}

$kotlinRoot = [System.IO.Path]::Combine($ProjectRoot, "app", "src", "main", "java")
if (Test-Path -LiteralPath $kotlinRoot) {
  Get-ChildItem -LiteralPath $kotlinRoot -Filter *.kt -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
    if (Test-LikelyUtf16LeFile $_.FullName) {
      [void]$report.Add(("FAIL: {0} looks UTF-16 LE - re-save as UTF-8" -f $_.FullName))
      $failed = $true
    }
  }
}

# Plan / docs encoding gate: the build plan, changelog, README, and audit log
# are all edited by hand on Windows where Notepad happily saves UTF-16 LE
# without warning. UTF-16 documentation files break GitHub rendering and
# trip the diff-as-binary heuristic, which masks meaningful changes during
# review. Walk every committed *.md under the project root (excluding
# build / cache / probe-artifact directories) and reject UTF-16 LE.
$docCount = 0
$docBad = @()
Get-ChildItem -LiteralPath $ProjectRoot -Filter *.md -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
  $full = $_.FullName.Replace('\','/').ToLowerInvariant()
  if ($full -match '/(build|\.gradle|_gradle_extract|\.git|hfr-runs|node_modules)/') { return }
  $docCount++
  if (Test-LikelyUtf16LeFile $_.FullName) {
    $docBad += $_.FullName
    [void]$report.Add(("FAIL: {0} looks UTF-16 LE - re-save as UTF-8" -f $_.FullName))
    $failed = $true
  }
}
if ($docBad.Count -eq 0) {
  [void]$report.Add(("OK: doc encoding ({0} *.md files; none UTF-16 LE)" -f $docCount))
}

# FOSS dependency audit: reject proprietary / Play Services groups in any Gradle build script
# or version catalog. Scope: any .gradle, .gradle.kts, libs.versions.toml under the project root,
# excluding build/ and .gradle/ caches.
$forbiddenGroups = @(
  'com.google.android.gms',     # Play Services
  'com.google.firebase',        # Firebase
  'com.google.mlkit',           # ML Kit (proprietary)
  'com.google.android.play',    # Play Core / In-App Updates / Asset Delivery
  'com.google.android.libraries.places',
  'com.android.billingclient',  # Play Billing
  'com.google.ads.mediation',
  'com.google.android.ads'
)

$auditPaths = @()
# NOTE: `Get-ChildItem -LiteralPath ... -Recurse -Include *.gradle` is a known
# Windows PowerShell trap: `-Include` is silently ignored when `-LiteralPath`
# does not end in a wildcard, so the cmdlet returns every file in the tree.
# Filter by extension explicitly with `Where-Object` to avoid auditing
# markdown / logcat / build artifacts.
$auditPaths += Get-ChildItem -LiteralPath $ProjectRoot -Recurse -File -ErrorAction SilentlyContinue |
  Where-Object {
    ($_.Name -like '*.gradle' -or $_.Name -like '*.gradle.kts') -and
    ($_.FullName.Replace('\','/').ToLowerInvariant() -notmatch '/(build|\.gradle|_gradle_extract)/')
  }
$catalog = Join-Path $ProjectRoot "gradle/libs.versions.toml"
if (Test-Path -LiteralPath $catalog) { $auditPaths += Get-Item -LiteralPath $catalog }

$audited = 0
foreach ($f in $auditPaths) {
  $audited++
  $text = ''
  try { $text = [System.IO.File]::ReadAllText($f.FullName) } catch { $text = '' }
  if ([string]::IsNullOrEmpty($text)) { continue }
  foreach ($g in $forbiddenGroups) {
    if ($text -match [regex]::Escape($g)) {
      [void]$report.Add(("FAIL: dep-audit {0} references forbidden group '{1}'" -f $f.FullName, $g))
      $failed = $true
    }
  }
}
[void]$report.Add(("OK: dep-audit (no Play Services / proprietary SDK references in {0} Gradle file(s))" -f $audited))

# License inventory drift check: cross-reference gradle/libs.versions.toml against
# the static license map in scripts/pns_license_inventory.ps1 (which mirrors LICENSES.md).
$licenseScript = Join-Path $PSScriptRoot "pns_license_inventory.ps1"
if (Test-Path -LiteralPath $licenseScript) {
  Write-Verify "License inventory drift check"
  & pwsh -NoProfile -ExecutionPolicy Bypass -File $licenseScript -ProjectRoot $ProjectRoot | Out-Null
  if ($LASTEXITCODE -ne 0) {
    [void]$report.Add("FAIL: license inventory drift (run pns_license_inventory.ps1 manually for details)")
    $failed = $true
  } else {
    [void]$report.Add("OK: license inventory (LICENSES.md in sync with libs.versions.toml)")
  }
} else {
  [void]$report.Add("SKIP: license inventory (pns_license_inventory.ps1 missing)")
}

# CycloneDX SBOM emit + structural verify. Closes BUILD_PLAN.md §9
# "SBOM generation". Does not write to disk by default; just confirms the
# emitter parses libs.versions.toml + the embedded SPDX map and produces
# valid CycloneDX 1.5 JSON.
$sbomScript = Join-Path $PSScriptRoot "pns_sbom.ps1"
if (Test-Path -LiteralPath $sbomScript) {
  Write-Verify "CycloneDX SBOM emit + structural verify"
  & pwsh -NoProfile -ExecutionPolicy Bypass -File $sbomScript -ProjectRoot $ProjectRoot -Verify | Out-Null
  if ($LASTEXITCODE -ne 0) {
    [void]$report.Add("FAIL: SBOM emit/verify (run pns_sbom.ps1 -Verify manually for details)")
    $failed = $true
  } else {
    [void]$report.Add("OK: CycloneDX 1.5 SBOM emit + structural verify")
  }
} else {
  [void]$report.Add("SKIP: SBOM (pns_sbom.ps1 missing)")
}

$apkDir = [System.IO.Path]::Combine($ProjectRoot, "app", "build", "outputs", "apk", "debug")
if ((Test-Path -LiteralPath $apkDir) -and -not $SkipGradle.IsPresent) {
  $apk = @(Get-ChildItem -LiteralPath $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)[0]
  if ($apk) {
    [void]$report.Add(("OK: APK {0} ({1} KB)" -f $apk.Name, [int]($apk.Length / 1024)))
  } else {
    [void]$report.Add("WARN: no APK under debug output (build may have skipped APK)")
  }
}

# Optional JVM unit tests. Useful in CI and any time Kotlin in app/src/test changes.
# Requires gradlew to be available (skipped if not). Failures bubble up to the report.
if ($RunTests.IsPresent) {
  Write-Verify "Gradle :app:testDebugUnitTest (no-daemon) in $ProjectRoot"
  $gradlewBat = Join-Path $ProjectRoot "gradlew.bat"
  $gradlewSh = Join-Path $ProjectRoot "gradlew"
  $gradlew = $null
  if ((Test-Path -LiteralPath $gradlewBat) -and ($null -ne $env:OS -and $env:OS -match '(?i)Windows')) {
    $gradlew = $gradlewBat
  } elseif (Test-Path -LiteralPath $gradlewSh) {
    $gradlew = $gradlewSh
  } elseif (Test-Path -LiteralPath $gradlewBat) {
    $gradlew = $gradlewBat
  }
  if (-not $gradlew) {
    [void]$report.Add("FAIL: -RunTests requested but gradlew not found")
    $failed = $true
  } else {
    Push-Location $ProjectRoot
    try {
      & $gradlew :app:testDebugUnitTest --no-daemon
      if ($LASTEXITCODE -ne 0) {
        [void]$report.Add("FAIL: :app:testDebugUnitTest exit code $LASTEXITCODE")
        $failed = $true
      } else {
        [void]$report.Add("OK: :app:testDebugUnitTest BUILD SUCCESSFUL")
      }
    } finally {
      Pop-Location
    }
  }
}

Write-Host ""
Write-Host "========== POINT-AND-SHOOT TOOLCHAIN VERIFY =========="
foreach ($line in $report) {
  Write-Host $line
}
Write-Host "======================================================"

if (-not [string]::IsNullOrWhiteSpace($ReportDir)) {
  New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
  $rp = Join-Path $ReportDir ("toolchain_verify_{0}.txt" -f (Get-Date -Format "yyyyMMdd_HHmmss"))
  $utf8 = New-Object System.Text.UTF8Encoding $false
  [System.IO.File]::WriteAllText($rp, (($report.ToArray() -join "`n") + "`nRESULT=" + ($(if ($failed) { "FAILED" } else { "PASSED" }))), $utf8)
  Write-Verify "Wrote $rp"
}

if ($failed) {
  Write-Host "[verify] RESULT: FAILED"
  exit 1
}
Write-Host "[verify] RESULT: PASSED"
exit 0
