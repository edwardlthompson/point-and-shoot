# Point & Shoot - license inventory drift check.
# Cross-references the dependencies declared in `gradle/libs.versions.toml` against
# the static license map embedded in this script (which mirrors `LICENSES.md`).
# Reports drift in either direction:
#   * A coordinate appears in the version catalog but not in the license map.
#   * A coordinate appears in the license map but not in the version catalog.
# Closes BUILD_PLAN.md §9 "Security/F-Droid hygiene": "Dependency/license scan
# gate (FOSS-only)". Run by `pns_verify_toolchain.ps1` on every gate invocation.
#
# Also walks `app/src/main/assets/luts/<spdx-folder>/<name>/` per BUILD_PLAN
# §7 "License & sourcing rules": every leaf folder MUST contain LICENSE.txt,
# SOURCE.txt, and SHA256.txt; every leaf folder MUST be referenced by
# LICENSES.md "Bundled LUTs" section. Reports drift in either direction.
# When the LUTs directory does not exist (no asset-backed LUTs shipped yet),
# the LUT walker reports "no asset-backed LUTs" and skips silently - this is
# the expected state until the Gradle `downloadBundledLuts` task lands a real
# LUT into the assets directory.
#
# Usage:
#   .\scripts\pns_license_inventory.ps1                 # check only (exits 1 on drift)
#   .\scripts\pns_license_inventory.ps1 -DumpMarkdown   # also writes Markdown table to stdout
#   .\scripts\pns_license_inventory.ps1 -SkipLutWalk    # skip the bundled-LUTs folder check

param(
  [string]$ProjectRoot = "",
  [switch]$DumpMarkdown,
  [switch]$SkipLutWalk
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

# Authoritative license map - mirrors LICENSES.md. Update both together.
# Each row is: coordinate -> @{ Spdx = '<SPDX>'; Scope = 'runtime' | 'debug' | 'test' | 'plugin'; Notes = '<optional>' }
$LicenseMap = @{
  # Runtime (shipped in the APK)
  'androidx.core:core-ktx'                       = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.lifecycle:lifecycle-runtime-ktx'     = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.activity:activity-compose'           = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.compose:compose-bom'                 = @{ Spdx = 'Apache-2.0'; Scope = 'runtime'; Notes = 'BOM' }
  'androidx.compose.ui:ui'                       = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.compose.ui:ui-graphics'              = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.compose.ui:ui-tooling-preview'       = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.compose.material3:material3'         = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.camera:camera-camera2'               = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.graphics:graphics-core'              = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }
  'androidx.exifinterface:exifinterface'         = @{ Spdx = 'Apache-2.0'; Scope = 'runtime' }

  # Debug-only
  'androidx.compose.ui:ui-tooling'               = @{ Spdx = 'Apache-2.0'; Scope = 'debug' }

  # Test-only (never linked into the APK)
  'junit:junit'                                  = @{ Spdx = 'EPL-1.0';   Scope = 'test'; Notes = 'EPL-1.0 acceptable for testImplementation' }
  'org.json:json'                                = @{ Spdx = 'JSON-LICENSE'; Scope = 'test'; Notes = 'real org.json for testing EncoderAttemptJsonAdapter.decode; MIT-equivalent for redistribution' }

  # Build-time plugins (host toolchain only)
  'com.android.application'                      = @{ Spdx = 'Apache-2.0'; Scope = 'plugin' }
  'org.jetbrains.kotlin.android'                 = @{ Spdx = 'Apache-2.0'; Scope = 'plugin' }
  'org.jetbrains.kotlin.plugin.compose'          = @{ Spdx = 'Apache-2.0'; Scope = 'plugin' }
}

$catalog = Join-Path $ProjectRoot "gradle/libs.versions.toml"
if (-not (Test-Path -LiteralPath $catalog)) {
  Write-Error "[license] FAIL: $catalog not found"
  exit 1
}
$text = [System.IO.File]::ReadAllText($catalog)

# Parse [libraries] block: lines like
#   foo = { module = "g:a", version.ref = "x" }
# We only care about the module coordinate.
function Get-CatalogCoordinates([string]$tomlText) {
  $coords = @()
  $insideLibraries = $false
  $insidePlugins = $false
  foreach ($raw in ($tomlText -split "`r?`n")) {
    $line = $raw.Trim()
    if ($line -eq '[libraries]')  { $insideLibraries = $true;  $insidePlugins = $false; continue }
    if ($line -eq '[plugins]')    { $insideLibraries = $false; $insidePlugins = $true;  continue }
    if ($line.StartsWith('[') -and $line.EndsWith(']')) { $insideLibraries = $false; $insidePlugins = $false; continue }
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }

    if ($insideLibraries) {
      $m = [regex]::Match($line, 'module\s*=\s*"([^"]+)"')
      if ($m.Success) { $coords += $m.Groups[1].Value }
    } elseif ($insidePlugins) {
      $m = [regex]::Match($line, 'id\s*=\s*"([^"]+)"')
      if ($m.Success) { $coords += $m.Groups[1].Value }
    }
  }
  return $coords | Sort-Object -Unique
}

$coords = Get-CatalogCoordinates $text

$missingFromMap = @()
$missingFromCatalog = @()

foreach ($c in $coords) {
  if (-not $LicenseMap.ContainsKey($c)) {
    $missingFromMap += $c
  }
}
foreach ($k in $LicenseMap.Keys) {
  if (-not ($coords -contains $k)) {
    $missingFromCatalog += $k
  }
}

if ($DumpMarkdown.IsPresent) {
  Write-Host "| Coordinate | SPDX | Scope | Notes |"
  Write-Host "|---|---|---|---|"
  foreach ($c in $coords) {
    if ($LicenseMap.ContainsKey($c)) {
      $row = $LicenseMap[$c]
      $notes = if ($row.ContainsKey('Notes')) { $row.Notes } else { '' }
      Write-Host ("| {0} | {1} | {2} | {3} |" -f $c, $row.Spdx, $row.Scope, $notes)
    } else {
      Write-Host ("| {0} | UNKNOWN | UNKNOWN | NOT IN LICENSES.md |" -f $c)
    }
  }
}

$failed = $false
if ($missingFromMap.Count -gt 0) {
  Write-Host "`[license] FAIL: catalog deps not in LICENSES.md / pns_license_inventory.ps1:" -ForegroundColor Red
  foreach ($c in $missingFromMap) { Write-Host "  - $c" }
  $failed = $true
}
if ($missingFromCatalog.Count -gt 0) {
  Write-Host "`[license] FAIL: license-map entries not in catalog (stale):" -ForegroundColor Red
  foreach ($c in $missingFromCatalog) { Write-Host "  - $c" }
  $failed = $true
}

# ---------- bundled-LUTs walker (BUILD_PLAN §7 "License & sourcing rules") ----------
function Test-BundledLutFolders {
  param(
    [string]$LutsRoot,
    [string]$LicensesMdPath
  )

  $report = [pscustomobject]@{
    Walked          = $false
    LeafFolders     = @()
    MissingSidecars = @()
    NotInLicensesMd = @()
    Status          = ""
  }

  if (-not (Test-Path -LiteralPath $LutsRoot)) {
    $report.Status = "no asset-backed LUTs (folder $LutsRoot does not exist - run .\gradlew :app:downloadBundledLuts)"
    return $report
  }
  $report.Walked = $true

  # Required sidecar files per BUILD_PLAN §7: every leaf folder MUST have these.
  $requiredSidecars = @('LICENSE.txt', 'SOURCE.txt', 'SHA256.txt')

  # Walk: <luts>/<spdx-folder>/<name>/ - depth 2 leaf folders.
  $spdxFolders = Get-ChildItem -LiteralPath $LutsRoot -Directory -ErrorAction SilentlyContinue
  foreach ($spdxDir in $spdxFolders) {
    $leafFolders = Get-ChildItem -LiteralPath $spdxDir.FullName -Directory -ErrorAction SilentlyContinue
    foreach ($leaf in $leafFolders) {
      $relPath = "luts/$($spdxDir.Name)/$($leaf.Name)"
      $report.LeafFolders += $relPath
      $missing = @()
      foreach ($side in $requiredSidecars) {
        $sidePath = Join-Path $leaf.FullName $side
        if (-not (Test-Path -LiteralPath $sidePath -PathType Leaf)) {
          $missing += $side
        }
      }
      if ($missing.Count -gt 0) {
        $report.MissingSidecars += [pscustomobject]@{
          Folder  = $relPath
          Missing = $missing -join ', '
        }
      }
    }
  }

  if ($report.LeafFolders.Count -eq 0) {
    $report.Status = "luts/ exists but contains no leaf LUT folders"
    return $report
  }

  # Cross-reference against LICENSES.md - every leaf folder MUST be referenced
  # by name somewhere in the "Bundled LUTs" section.
  if (Test-Path -LiteralPath $LicensesMdPath -PathType Leaf) {
    $licensesText = [System.IO.File]::ReadAllText($LicensesMdPath)
    foreach ($leaf in $report.LeafFolders) {
      $name = $leaf.Split('/')[-1]
      if (-not $licensesText.Contains($name)) {
        $report.NotInLicensesMd += $leaf
      }
    }
  } else {
    $report.NotInLicensesMd = $report.LeafFolders
  }

  return $report
}

$lutsRoot = Join-Path $ProjectRoot "app/src/main/assets/luts"
$licensesMd = Join-Path $ProjectRoot "LICENSES.md"
if (-not $SkipLutWalk.IsPresent) {
  $lutReport = Test-BundledLutFolders -LutsRoot $lutsRoot -LicensesMdPath $licensesMd
  if (-not $lutReport.Walked) {
    Write-Host ("[license] OK: bundled LUTs - {0}" -f $lutReport.Status)
  } elseif ($lutReport.LeafFolders.Count -eq 0) {
    Write-Host ("[license] OK: bundled LUTs - {0}" -f $lutReport.Status)
  } else {
    if ($lutReport.MissingSidecars.Count -gt 0) {
      Write-Host "`[license] FAIL: bundled LUT leaf folders missing required sidecars (LICENSE.txt / SOURCE.txt / SHA256.txt):" -ForegroundColor Red
      foreach ($row in $lutReport.MissingSidecars) {
        Write-Host ("  - {0}: missing {1}" -f $row.Folder, $row.Missing)
      }
      $failed = $true
    }
    if ($lutReport.NotInLicensesMd.Count -gt 0) {
      Write-Host "`[license] FAIL: bundled LUT folders not referenced in LICENSES.md:" -ForegroundColor Red
      foreach ($f in $lutReport.NotInLicensesMd) { Write-Host "  - $f" }
      $failed = $true
    }
    if ($lutReport.MissingSidecars.Count -eq 0 -and $lutReport.NotInLicensesMd.Count -eq 0) {
      Write-Host ("[license] OK: bundled LUTs ({0} leaf folder(s) walked, all sidecars present and referenced in LICENSES.md)" -f $lutReport.LeafFolders.Count)
    }
  }
} else {
  Write-Host "`[license] SKIP: bundled LUTs walk (-SkipLutWalk)"
}

if ($failed) {
  Write-Host "`[license] RESULT: FAILED (drift)"
  exit 1
} else {
  Write-Host ("[license] OK: {0} coordinate(s) in catalog all accounted for in LICENSES.md" -f $coords.Count)
  Write-Host "`[license] RESULT: PASSED"
  exit 0
}
