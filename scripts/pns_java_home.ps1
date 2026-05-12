# Point & Shoot — resolve JDK for Gradle / CLI (JAVA_HOME on Windows).
#
# Why: Cursor and some shells do not inherit Android Studio's JDK; Gradle needs JAVA_HOME.
#
# Usage:
#   # A) Set JAVA_HOME for this terminal process (persists for the rest of that pwsh.exe):
#   .\scripts\pns_java_home.ps1 -Session
#
#   # B) Persist for your Windows user (new terminals pick it up after restart):
#   .\scripts\pns_java_home.ps1 -PersistUser
#
#   # C) Run Gradle in one shot (same process; no global JAVA_HOME needed):
#   .\scripts\pns_gradlew.ps1 :app:assembleDebug
#
#   # D) Machine-readable path only (for scripts):
#   $jdk = .\scripts\pns_java_home.ps1 -EmitPath
#
#   # E) Inspect search order:
#   .\scripts\pns_java_home.ps1 -ListCandidates
#
# Optional: force a JDK root (must contain bin\java.exe):
#   .\scripts\pns_java_home.ps1 -JdkHome "C:\Program Files\Android\Android Studio\jbr" -PersistUser
#
# Optional env override (checked first): PNS_JAVA_HOME

param(
  [string]$JdkHome = "",
  [switch]$Session,
  [switch]$PersistUser,
  [switch]$ListCandidates,
  [switch]$EmitPath
)

$ErrorActionPreference = "Stop"

function Test-ValidJdkRoot([string]$root) {
  if ([string]::IsNullOrWhiteSpace($root)) { return $false }
  $java = Join-Path $root "bin\java.exe"
  return (Test-Path -LiteralPath $java)
}

function Get-PnsJdkCandidatePaths {
  $candidates = New-Object System.Collections.Generic.List[string]

  $e = $env:PNS_JAVA_HOME
  if (-not [string]::IsNullOrWhiteSpace($e)) { [void]$candidates.Add($e.TrimEnd('\', '/')) }

  foreach ($p in @(
      "${env:ProgramFiles}\Android\Android Studio\jbr",
      "${env:ProgramFiles}\Android\Android Studio\jre",
      "${env:ProgramFiles(x86)}\Android\Android Studio\jbr",
      "${env:LocalAppData}\Programs\Android Studio\jbr",
      "${env:ProgramFiles}\JetBrains\Android Studio\jbr"
    )) {
    if (-not [string]::IsNullOrWhiteSpace($p)) { [void]$candidates.Add($p) }
  }

  $toolboxBase = Join-Path $env:LocalAppData "JetBrains\Toolbox\apps\AndroidStudio"
  if (Test-Path -LiteralPath $toolboxBase) {
    Get-ChildItem -LiteralPath $toolboxBase -Directory -ErrorAction SilentlyContinue |
      ForEach-Object {
        Get-ChildItem -LiteralPath $_.FullName -Directory -ErrorAction SilentlyContinue |
          Where-Object { $_.Name -match '^(ch-)?\d' } |
          ForEach-Object {
            $jbr = Join-Path $_.FullName "jbr"
            if (Test-Path -LiteralPath $jbr) { [void]$candidates.Add($jbr) }
          }
      }
  }

  foreach ($base in @(
      "${env:ProgramFiles}\Eclipse Adoptium",
      "${env:ProgramFiles}\Microsoft",
      "${env:ProgramFiles}\Java",
      "${env:ProgramFiles(x86)}\Java"
    )) {
    if (-not (Test-Path -LiteralPath $base)) { continue }
    Get-ChildItem -LiteralPath $base -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -match 'jdk|jdk-|java|temurin|microsoft' } |
      ForEach-Object { [void]$candidates.Add($_.FullName) }
  }

  try {
    $javaCmd = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCmd -and $javaCmd.Source) {
      $bin = Split-Path -Parent $javaCmd.Source
      $root = Split-Path -Parent $bin
      if (Test-ValidJdkRoot $root) { [void]$candidates.Add($root) }
    }
  } catch { }

  $jh = $env:JAVA_HOME
  if (-not [string]::IsNullOrWhiteSpace($jh)) { [void]$candidates.Add($jh.TrimEnd('\', '/')) }

  $seen = @{}
  $out = New-Object System.Collections.Generic.List[string]
  foreach ($c in $candidates) {
    if ([string]::IsNullOrWhiteSpace($c)) { continue }
    $n = $null
    try { $n = (Resolve-Path -LiteralPath $c -ErrorAction Stop).Path } catch { $n = $c }
    if ($seen.ContainsKey($n)) { continue }
    if (-not (Test-Path -LiteralPath $n)) { continue }
    $seen[$n] = $true
    [void]$out.Add($n)
  }
  return $out
}

function Get-PnsJdkHome {
  param([string]$ForceRoot)
  if (Test-ValidJdkRoot $ForceRoot) { return (Resolve-Path -LiteralPath $ForceRoot).Path }
  foreach ($c in (Get-PnsJdkCandidatePaths)) {
    if (Test-ValidJdkRoot $c) { return (Resolve-Path -LiteralPath $c).Path }
  }
  return $null
}

$resolved = Get-PnsJdkHome -ForceRoot $JdkHome

if ($ListCandidates) {
  Write-Host "JDK candidates (first valid wins unless -JdkHome is set):"
  $i = 0
  foreach ($c in (Get-PnsJdkCandidatePaths)) {
    $i++
    $ok = Test-ValidJdkRoot $c
    Write-Host ("  {0,-2} {1}  [{2}]" -f $i, $c, ($(if ($ok) { "ok" } else { "no bin\java.exe" })))
  }
  Write-Host ""
}

if (-not $resolved) {
  Write-Error @"
No usable JDK found (need a directory containing bin\java.exe).

Try:
  - Install Android Studio, or set PNS_JAVA_HOME to your JDK 17+ root, or
  - .\scripts\pns_java_home.ps1 -ListCandidates
"@
}

if ($EmitPath) {
  Write-Output $resolved
  exit 0
}

Write-Host "Using JDK: $resolved"
# java -version writes to stderr; under $ErrorActionPreference Stop that can surface as a terminating error.
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
  & (Join-Path $resolved "bin\java.exe") -version 2>&1 | ForEach-Object { Write-Host $_ }
}
finally {
  $ErrorActionPreference = $prevEap
}

if ($PersistUser) {
  [Environment]::SetEnvironmentVariable("JAVA_HOME", $resolved, "User")
  $bin = Join-Path $resolved "bin"
  $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
  if ([string]::IsNullOrWhiteSpace($userPath)) { $userPath = "" }
  $parts = $userPath -split ';' | Where-Object { $_ -and $_.Trim() -ne '' }
  $norm = @($bin) + ($parts | Where-Object { $_ -ne $bin })
  $newPath = ($norm -join ';').TrimEnd(';')
  [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
  Write-Host "Persisted User JAVA_HOME and prepended JDK bin to User Path. Open a new terminal (or restart Cursor) to pick it up."
}

if ($Session) {
  $env:JAVA_HOME = $resolved
  $bin = Join-Path $resolved "bin"
  $paths = @($env:Path -split ';' | Where-Object { $_ })
  if ($paths -notcontains $bin) {
    $env:Path = "$bin;$env:Path"
  }
  Write-Host "Session: JAVA_HOME and Path updated for this process (this terminal)."
}
