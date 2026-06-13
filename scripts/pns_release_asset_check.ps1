#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.B** — gh release view; assert APK asset size > 1 MB

.PARAMETER RequireRelease
  When set, missing release or APK asset is exit 1 (strict release-cut lane).
  Default host-gate behavior: exit 0 SKIP when no release/APK is published yet.
#>
param(
    [string]$Tag = "",
    [long]$MinBytes = 1048576,
    [switch]$RequireRelease
)

$ErrorActionPreference = "Stop"
$gh = Get-Command gh -ErrorAction SilentlyContinue
if (-not $gh) {
    Write-Host "RELEASE ASSET CHECK: SKIP (gh not on PATH)"
    exit 0
}
$args = @("release", "view", "--json", "assets")
if ($Tag) { $args += @("--repo", (gh repo view --json nameWithOwner -q .nameWithOwner)) }
$json = gh @args 2>$null | ConvertFrom-Json
if (-not $json) {
    if ($RequireRelease) {
        Write-Host "RELEASE ASSET CHECK: FAIL (no release assets)"
        exit 1
    }
    Write-Host "RELEASE ASSET CHECK: SKIP (no release assets)"
    exit 0
}
$apk = $json | Where-Object { $_.name -match '\.apk$' } | Select-Object -First 1
if (-not $apk) {
    if ($RequireRelease) {
        Write-Host "RELEASE ASSET CHECK: FAIL (no APK asset)"
        exit 1
    }
    Write-Host "RELEASE ASSET CHECK: SKIP (no APK asset)"
    exit 0
}
$ok = [long]$apk.size -gt $MinBytes
Write-Host "RELEASE ASSET CHECK: $(if ($ok) { 'PASS' } else { 'FAIL' }) asset=$($apk.name) size=$($apk.size)"
if (-not $ok) { exit 1 }
exit 0
