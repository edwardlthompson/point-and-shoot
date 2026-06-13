#Requires -Version 5.1
<#
.SYNOPSIS
  Resolve ReferenceApp DNG fixture directory: legacy_sku first, then referenceapp_cph2655.

.DESCRIPTION
  Dot-source this script and call Resolve-PnsReferenceAppFixtureDir. Mirrors the candidate
  order in pns_fixture_dng_gates.ps1 so host gates work when only CPH2655 fixtures are checked in.

.EXAMPLE
  . .\scripts\pns_resolve_referenceapp_fixture_dir.ps1
  $dir = Resolve-PnsReferenceAppFixtureDir -ProjectRoot $root
#>
function Resolve-PnsReferenceAppFixtureDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ProjectRoot,
        [switch]$RequireExists
    )
    $candidates = @(
        (Join-Path $ProjectRoot "tests\fixtures\referenceapp_legacy_sku"),
        (Join-Path $ProjectRoot "tests\fixtures\referenceapp_cph2655")
    )
    foreach ($dir in $candidates) {
        $uw = Join-Path $dir "referenceapp_uw_cam3.dng"
        if ((Test-Path -LiteralPath $dir) -and (Test-Path -LiteralPath $uw)) {
            return $dir
        }
    }
    if ($RequireExists) {
        throw "No ReferenceApp fixture directory found. Tried:`n  $($candidates -join "`n  ")"
    }
    return $null
}
