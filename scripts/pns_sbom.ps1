# Point & Shoot - CycloneDX 1.5 SBOM emitter.
#
# Closes BUILD_PLAN.md §9 "SBOM generation (optional, but recommended for
# transparency)". Reads the authoritative dependency list from
# `gradle/libs.versions.toml` (cross-checked by `pns_license_inventory.ps1`)
# plus the SPDX map embedded in `pns_license_inventory.ps1`, and emits a
# CycloneDX-format JSON SBOM to stdout (or to `-OutFile`).
#
# CycloneDX is the OWASP-stewarded open SBOM standard
# (https://cyclonedx.org/specification/overview/); SPDX coordinates and SBOM
# emit are deliberately host-side PowerShell rather than a Gradle plugin so
# the project's runtime classpath stays minimal (no third-party plugin
# required, no transitive plugin license to audit).
#
# Scope: emits ONLY the direct dependencies declared in
# `gradle/libs.versions.toml`. Transitive resolution (the full Maven graph)
# is intentionally out of scope today; the CycloneDX spec allows partial
# graphs ("declared dependencies") and our LICENSES.md commitment is to
# the direct deps only. A future enhancement could shell out to Gradle's
# `dependencies` task and merge the transitives in.
#
# Usage:
#   .\scripts\pns_sbom.ps1                            # writes JSON to stdout
#   .\scripts\pns_sbom.ps1 -OutFile docs\sbom.json    # writes to a file
#   .\scripts\pns_sbom.ps1 -Verify                    # parses + structural-checks the most recent emit

param(
  [string]$ProjectRoot = "",
  [string]$OutFile = "",
  [switch]$Verify
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

# Authoritative coordinate -> SPDX map. Mirrors the one in
# pns_license_inventory.ps1 so a single source of truth feeds both. (We do
# not import the other script to keep this one independently invokable.)
$LicenseMap = @{
  'androidx.core:core-ktx'                       = 'Apache-2.0'
  'androidx.lifecycle:lifecycle-runtime-ktx'     = 'Apache-2.0'
  'androidx.activity:activity-compose'           = 'Apache-2.0'
  'androidx.compose:compose-bom'                 = 'Apache-2.0'
  'androidx.compose.ui:ui'                       = 'Apache-2.0'
  'androidx.compose.ui:ui-graphics'              = 'Apache-2.0'
  'androidx.compose.ui:ui-tooling-preview'       = 'Apache-2.0'
  'androidx.compose.material3:material3'         = 'Apache-2.0'
  'androidx.camera:camera-camera2'               = 'Apache-2.0'
  'androidx.camera:camera-core'                  = 'Apache-2.0'
  'androidx.camera:camera-lifecycle'             = 'Apache-2.0'
  'androidx.camera:camera-view'                  = 'Apache-2.0'
  'com.google.zxing:core'                        = 'Apache-2.0'
  'androidx.graphics:graphics-core'              = 'Apache-2.0'
  'androidx.compose.ui:ui-tooling'               = 'Apache-2.0'
  'junit:junit'                                  = 'EPL-1.0'
  'org.json:json'                                = 'JSON'
  'com.android.application'                      = 'Apache-2.0'
  'com.android.library'                          = 'Apache-2.0'
  'org.jetbrains.kotlin.android'                 = 'Apache-2.0'
  'org.jetbrains.kotlin.plugin.compose'          = 'Apache-2.0'
}

# Per-coordinate scope (matches LICENSES.md categorization).
$ScopeMap = @{
  'androidx.compose.ui:ui-tooling' = 'optional'   # debug-only
  'junit:junit'                    = 'optional'   # test-only
  'org.json:json'                  = 'optional'   # test-only
  'com.android.application'        = 'excluded'   # build-time plugin
  'com.android.library'            = 'excluded'   # build-time plugin
  'org.jetbrains.kotlin.android'   = 'excluded'   # build-time plugin
  'org.jetbrains.kotlin.plugin.compose' = 'excluded'  # build-time plugin
}

$catalog = Join-Path $ProjectRoot "gradle/libs.versions.toml"
if (-not (Test-Path -LiteralPath $catalog)) {
  Write-Error "[sbom] FAIL: $catalog not found"
  exit 1
}
$tomlText = [System.IO.File]::ReadAllText($catalog)

# Parse [versions], [libraries], [plugins].
function Get-CatalogContents([string]$tomlText) {
  $versions = @{}
  $libraries = @()
  $plugins = @()
  $section = ""
  foreach ($raw in ($tomlText -split "`r?`n")) {
    $line = $raw.Trim()
    if ($line.StartsWith('[') -and $line.EndsWith(']')) {
      $section = $line.Trim('[', ']'); continue
    }
    if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith('#')) { continue }

    if ($section -eq 'versions') {
      $m = [regex]::Match($line, '^([A-Za-z0-9_.-]+)\s*=\s*"([^"]+)"')
      if ($m.Success) { $versions[$m.Groups[1].Value] = $m.Groups[2].Value }
    } elseif ($section -eq 'libraries') {
      $modMatch = [regex]::Match($line, 'module\s*=\s*"([^"]+)"')
      $verRefMatch = [regex]::Match($line, 'version\.ref\s*=\s*"([^"]+)"')
      $verLitMatch = [regex]::Match($line, 'version\s*=\s*"([^"]+)"')
      if ($modMatch.Success) {
        $libraries += [pscustomobject]@{
          Module      = $modMatch.Groups[1].Value
          VersionRef  = if ($verRefMatch.Success) { $verRefMatch.Groups[1].Value } else { $null }
          VersionLit  = if ($verLitMatch.Success -and -not $verRefMatch.Success) { $verLitMatch.Groups[1].Value } else { $null }
        }
      }
    } elseif ($section -eq 'plugins') {
      $idMatch = [regex]::Match($line, 'id\s*=\s*"([^"]+)"')
      $verRefMatch = [regex]::Match($line, 'version\.ref\s*=\s*"([^"]+)"')
      $verLitMatch = [regex]::Match($line, 'version\s*=\s*"([^"]+)"')
      if ($idMatch.Success) {
        $plugins += [pscustomobject]@{
          PluginId   = $idMatch.Groups[1].Value
          VersionRef = if ($verRefMatch.Success) { $verRefMatch.Groups[1].Value } else { $null }
          VersionLit = if ($verLitMatch.Success -and -not $verRefMatch.Success) { $verLitMatch.Groups[1].Value } else { $null }
        }
      }
    }
  }
  return [pscustomobject]@{
    Versions  = $versions
    Libraries = $libraries
    Plugins   = $plugins
  }
}

$catalogData = Get-CatalogContents $tomlText

function Resolve-Version($entry, $versions) {
  if ($entry.VersionRef) {
    if ($versions.ContainsKey($entry.VersionRef)) { return $versions[$entry.VersionRef] }
    return "unresolved-ref:$($entry.VersionRef)"
  }
  if ($entry.VersionLit) { return $entry.VersionLit }
  return "unspecified"
}

function New-PurlMaven($groupArtifact, $version) {
  $parts = $groupArtifact -split ':', 2
  $g = [uri]::EscapeDataString($parts[0])
  $a = [uri]::EscapeDataString($parts[1])
  $v = [uri]::EscapeDataString($version)
  return "pkg:maven/$g/$a@$v"
}

function New-PurlGradlePlugin($pluginId, $version) {
  # Gradle plugin coordinates resolve to <pluginId>:<pluginId>.gradle.plugin
  # on the Maven side; we encode that as a Maven purl with the .gradle.plugin
  # suffix so SBOM consumers can locate the artifact deterministically.
  $g = [uri]::EscapeDataString($pluginId)
  $a = [uri]::EscapeDataString("$pluginId.gradle.plugin")
  $v = [uri]::EscapeDataString($version)
  return "pkg:maven/$g/$a@$v"
}

$components = @()
foreach ($lib in $catalogData.Libraries) {
  $version = Resolve-Version $lib $catalogData.Versions
  $module = $lib.Module
  $spdx = if ($LicenseMap.ContainsKey($module)) { $LicenseMap[$module] } else { 'NOASSERTION' }
  $scope = if ($ScopeMap.ContainsKey($module)) { $ScopeMap[$module] } else { 'required' }
  $purl = New-PurlMaven $module $version
  $parts = $module -split ':', 2
  $components += [ordered]@{
    'type'    = 'library'
    'bom-ref' = $purl
    'group'   = $parts[0]
    'name'    = $parts[1]
    'version' = $version
    'purl'    = $purl
    'scope'   = $scope
    'licenses' = @( @{ 'license' = @{ 'id' = $spdx } } )
  }
}
foreach ($plugin in $catalogData.Plugins) {
  $version = Resolve-Version $plugin $catalogData.Versions
  $pluginId = $plugin.PluginId
  $spdx = if ($LicenseMap.ContainsKey($pluginId)) { $LicenseMap[$pluginId] } else { 'NOASSERTION' }
  $scope = if ($ScopeMap.ContainsKey($pluginId)) { $ScopeMap[$pluginId] } else { 'required' }
  $purl = New-PurlGradlePlugin $pluginId $version
  $components += [ordered]@{
    'type'    = 'library'
    'bom-ref' = $purl
    'group'   = $pluginId
    'name'    = "$pluginId.gradle.plugin"
    'version' = $version
    'purl'    = $purl
    'scope'   = $scope
    'licenses' = @( @{ 'license' = @{ 'id' = $spdx } } )
  }
}

$serial = "urn:uuid:" + [guid]::NewGuid().ToString()
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")

$bom = [ordered]@{
  'bomFormat'    = 'CycloneDX'
  'specVersion'  = '1.5'
  'serialNumber' = $serial
  'version'      = 1
  'metadata'     = [ordered]@{
    'timestamp' = $timestamp
    'tools'     = @(
      [ordered]@{
        'vendor'  = 'Point & Shoot'
        'name'    = 'pns_sbom.ps1'
        'version' = '1'
      }
    )
    'component' = [ordered]@{
      'type'    = 'application'
      'bom-ref' = 'pkg:android/dev.pointandshoot@unreleased'
      'group'   = 'dev.pointandshoot'
      'name'    = 'point-and-shoot'
      'version' = 'unreleased'
      'description' = 'FOSS pro camera for legacy device (dodge) on LineageOS 23.'
      'licenses' = @( @{ 'license' = @{ 'id' = 'Apache-2.0' } } )
    }
  }
  'components' = $components
}

$json = $bom | ConvertTo-Json -Depth 10

if ($Verify.IsPresent) {
  # Re-parse + structural-check the JSON we just produced.
  try {
    $parsed = $json | ConvertFrom-Json
    if ($parsed.bomFormat -ne 'CycloneDX') { throw "bomFormat=$($parsed.bomFormat) (expected CycloneDX)" }
    if ($parsed.specVersion -ne '1.5')     { throw "specVersion=$($parsed.specVersion) (expected 1.5)" }
    if (-not $parsed.serialNumber.StartsWith('urn:uuid:')) { throw "serialNumber must start with urn:uuid:" }
    if ($parsed.components.Count -lt 1)    { throw "components is empty" }
    foreach ($c in $parsed.components) {
      if (-not $c.purl) { throw "component missing purl: $($c.name)" }
      if (-not $c.licenses -or $c.licenses.Count -lt 1) { throw "component missing licenses: $($c.name)" }
    }
    Write-Host ("[sbom] OK: {0} components emitted, structure valid CycloneDX 1.5" -f $parsed.components.Count)
  } catch {
    Write-Host "`[sbom] FAIL: $_"
    exit 1
  }
}

if ([string]::IsNullOrWhiteSpace($OutFile)) {
  Write-Output $json
} else {
  $outDir = Split-Path -Parent $OutFile
  if ($outDir -and -not (Test-Path -LiteralPath $outDir)) {
    New-Item -ItemType Directory -Path $outDir -Force | Out-Null
  }
  [System.IO.File]::WriteAllText($OutFile, $json, [System.Text.UTF8Encoding]::new($false))
  Write-Host "`[sbom] wrote $OutFile"
}

exit 0
