# Canonical DCIM tree for Point & Shoot USB scripts.
# Mirrors modules/pns-core/.../PnsDcimPaths.kt
# Writes go to PointAndShoot (no spaces). Reads also include the legacy
# "Point & Shoot" folder. Do not move or delete user files.
#
# USB gate pre-clean (rm) must target $PnsDcimRemoteCanonical only.
# Never rm $PnsDcimRemoteLegacy — new captures only land in the canonical
# tree, so wiping the legacy tree would delete preserved user stills/clips.
#
# Dot-source:  . (Join-Path $PSScriptRoot "pns_dcim_paths.ps1")

$script:PnsDcimFolderCanonical = "PointAndShoot"
$script:PnsDcimFolderLegacy = "Point & Shoot"
$script:PnsDcimRelativeCanonical = "DCIM/PointAndShoot"
$script:PnsDcimRelativeLegacy = "DCIM/Point & Shoot"
$script:PnsDcimRemoteCanonical = "/sdcard/DCIM/PointAndShoot"
$script:PnsDcimRemoteLegacy = "/sdcard/DCIM/Point & Shoot"
$script:PnsDcimRemoteDirs = @(
    $script:PnsDcimRemoteCanonical,
    $script:PnsDcimRemoteLegacy
)

function Test-PnsDcimRemotePath {
    param([string]$Path)
    if ([string]::IsNullOrWhiteSpace($Path)) { return $false }
    return (
        ($Path -like "*PointAndShoot*") -or
        ($Path -like "*Point & Shoot*") -or
        ($Path -match "Point.and.Shoot")
    )
}
