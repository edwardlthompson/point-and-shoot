# Lightweight repo hygiene smoke for Point & Shoot (host-only).
# Fails if common build outputs or secrets appear tracked by git.
#
# Usage:
#   .\scripts\pns_check_repo_hygiene.ps1

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$failures = New-Object System.Collections.Generic.List[string]

$forbiddenTracked = @(
  "local.properties",
  "scripts/pns_adb_device.env",
  ".env",
  "keystore.properties"
)

Push-Location $ProjectRoot
try {
  foreach ($rel in $forbiddenTracked) {
    $tracked = & git ls-files --error-unmatch -- $rel 2>$null
    if ($LASTEXITCODE -eq 0 -and $tracked) {
      $failures.Add("tracked forbidden path: $rel")
    }
  }

  $badPatterns = @(
    "^app/build/",
    "^\.gradle/",
    "^build/",
    "\.apk$",
    "\.aab$"
  )
  $trackedFiles = & git ls-files
  foreach ($f in $trackedFiles) {
    foreach ($pat in $badPatterns) {
      if ($f -match $pat) {
        $failures.Add("tracked build/cache artifact: $f")
        break
      }
    }
  }
} finally {
  Pop-Location
}

if ($failures.Count -gt 0) {
  Write-Host "REPO HYGIENE: FAIL ($($failures.Count) issue(s))"
  foreach ($x in $failures) { Write-Host "  - $x" }
  exit 1
}

Write-Host "REPO HYGIENE: PASS"
exit 0
