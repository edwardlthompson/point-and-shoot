# Point & Shoot - toolchain verification gate (run after Kotlin or PowerShell changes).
# Proves: Gradle assembleDebug, UTF-8 host scripts + Kotlin sources, PowerShell parse OK.
# Usage: .\scripts\pns_verify_toolchain.ps1 [-ProjectRoot path] [-SkipGradle] [-ReportDir path]

param(
  [string]$ProjectRoot = "",
  [switch]$SkipGradle,
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
  (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1")
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

$apkDir = [System.IO.Path]::Combine($ProjectRoot, "app", "build", "outputs", "apk", "debug")
if ((Test-Path -LiteralPath $apkDir) -and -not $SkipGradle.IsPresent) {
  $apk = @(Get-ChildItem -LiteralPath $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)[0]
  if ($apk) {
    [void]$report.Add(("OK: APK {0} ({1} KB)" -f $apk.Name, [int]($apk.Length / 1024)))
  } else {
    [void]$report.Add("WARN: no APK under debug output (build may have skipped APK)")
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
