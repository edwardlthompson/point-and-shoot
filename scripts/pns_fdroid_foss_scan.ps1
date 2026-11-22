# Host F-Droid scanner stand-in for the foss variant.
# 1) Source: fail if GMS / Firebase / ML Kit coordinates appear outside play-only paths.
# 2) APK: if a foss APK exists, fail if those class prefixes are packaged.
#
# Usage:
#   .\scripts\pns_fdroid_foss_scan.ps1
#   .\scripts\pns_fdroid_foss_scan.ps1 -ProjectRoot C:\path\to\repo

param(
  [string]$ProjectRoot = "",
  [switch]$SkipApk
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$failures = New-Object System.Collections.Generic.List[string]
$needles = @(
  'com.google.mlkit',
  'com.google.android.gms',
  'com.google.firebase',
  'com.google.android.play',
  'com.android.billingclient'
)

function Test-PlayOnlyPath([string]$fullName) {
  $n = $fullName.Replace('\', '/')
  return (
    $n -match '/app/play\.gradle$' -or
    $n -match '/app/proguard-play-rules\.pro$' -or
    $n -match '/app/src/play/' -or
    $n -match '/app/src/playRelease/'
  )
}

$scanRoots = @(
  (Join-Path $ProjectRoot "app"),
  (Join-Path $ProjectRoot "gradle"),
  (Join-Path $ProjectRoot "modules")
)
$extOk = @{
  '.kt' = $true; '.kts' = $true; '.gradle' = $true; '.toml' = $true
  '.xml' = $true; '.pro' = $true; '.properties' = $true
}

foreach ($root in $scanRoots) {
  if (-not (Test-Path -LiteralPath $root)) { continue }
  Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction SilentlyContinue |
    Where-Object {
      $extOk.ContainsKey($_.Extension.ToLowerInvariant()) -and
      ($_.FullName.Replace('\', '/') -notmatch '/(build|\.gradle|\.cxx)/')
    } |
    ForEach-Object {
      if (Test-PlayOnlyPath $_.FullName) { return }
      $text = ''
      try { $text = [System.IO.File]::ReadAllText($_.FullName) } catch { $text = '' }
      if ([string]::IsNullOrEmpty($text)) { return }
      foreach ($n in $needles) {
        if ($text.Contains($n)) {
          $failures.Add("FAIL: foss source scan $($_.FullName) contains '$n'")
        }
      }
    }
}

if (-not $SkipApk) {
  $apkCandidates = @(
    (Join-Path $ProjectRoot "app/build/outputs/apk/foss/debug"),
    (Join-Path $ProjectRoot "app/build/outputs/apk/foss/release")
  )
  $apk = $null
  foreach ($dir in $apkCandidates) {
    if (-not (Test-Path -LiteralPath $dir)) { continue }
    $hit = Get-ChildItem -LiteralPath $dir -Filter "*.apk" -File -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -notlike "*.idsig" } |
      Select-Object -First 1
    if ($hit) { $apk = $hit; break }
  }
  if ($apk) {
    $py = Get-Command python3 -ErrorAction SilentlyContinue
    if (-not $py) { $py = Get-Command python -ErrorAction SilentlyContinue }
    if ($py) {
      $scanPy = @'
import sys, zipfile
apk = sys.argv[1]
needles = (
    b"Lcom/google/mlkit/",
    b"Lcom/google/android/gms/",
    b"Lcom/google/firebase/",
)
with zipfile.ZipFile(apk) as z:
    for name in z.namelist():
        if not name.endswith(".dex"):
            continue
        data = z.read(name)
        for n in needles:
            if n in data:
                print(n.decode("ascii"))
                sys.exit(2)
print("ok")
'@
      $tmpPy = Join-Path ([System.IO.Path]::GetTempPath()) ("pns_fdroid_foss_apk_scan_{0}.py" -f [guid]::NewGuid().ToString("N"))
      try {
        [System.IO.File]::WriteAllText($tmpPy, $scanPy)
        $dexOut = & $py.Source $tmpPy $apk.FullName 2>&1
        $dexCode = $LASTEXITCODE
        if ($dexCode -ne 0) {
          $failures.Add("FAIL: foss APK $($apk.Name) DEX contains '$dexOut'")
        }
      } finally {
        Remove-Item -LiteralPath $tmpPy -ErrorAction SilentlyContinue
      }
    } else {
      Write-Host "FDROID FOSS SCAN: SKIP APK DEX (no python); zip listing only"
      $unzip = Get-Command unzip -ErrorAction SilentlyContinue
      $jar = Get-Command jar -ErrorAction SilentlyContinue
      $listing = ""
      if ($unzip) {
        $listing = & unzip -l $apk.FullName 2>$null | Out-String
      } elseif ($jar) {
        $listing = & jar tf $apk.FullName 2>$null | Out-String
      }
      if (-not [string]::IsNullOrWhiteSpace($listing)) {
        $classNeedles = @(
          'com/google/mlkit/',
          'com/google/android/gms/',
          'com/google/firebase/'
        )
        foreach ($n in $classNeedles) {
          if ($listing.Contains($n)) {
            $failures.Add("FAIL: foss APK $($apk.Name) packages '$n'")
          }
        }
      }
    }
  } else {
    Write-Host "FDROID FOSS SCAN: SKIP APK (no foss APK under app/build/outputs/apk/foss/)"
  }
}

if ($failures.Count -gt 0) {
  Write-Host "FDROID FOSS SCAN: FAIL ($($failures.Count) issue(s))"
  foreach ($f in $failures) { Write-Host $f }
  exit 1
}

Write-Host "FDROID FOSS SCAN: PASS (no GMS/ML Kit/Firebase in foss source; APK checked if present)"
exit 0
