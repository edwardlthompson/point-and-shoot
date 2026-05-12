# Point & Shoot — run Gradle with a resolved JDK in the same process (no prior JAVA_HOME needed).
#
# Usage (from repo root):
#   .\scripts\pns_gradlew.ps1 :app:assembleDebug
#   .\scripts\pns_gradlew.ps1 :app:testDebugUnitTest --tests "dev.pointandshoot.FaceDetectAdapterTest"
#
# Optional JDK root (must contain bin\java.exe) — must be named; do not rely on position after Gradle tasks:
#   .\scripts\pns_gradlew.ps1 -JdkHome "C:\Program Files\Android\Android Studio\jbr" :app:assembleDebug
#
# Windows PowerShell 5.1: a leading ':' on the first argument binds to the next [string] parameter (e.g. -JdkHome),
# leaving Gradle args empty. This script must not declare [string] parameters that accept positionals; it parses
# "-JdkHome <path>" from $args and forwards the rest to gradlew. Do not use [CmdletBinding()] here — extra args would
# then be rejected instead of landing in $args.

param()

$ErrorActionPreference = "Stop"
$here = $PSScriptRoot

$gradleArgs = New-Object System.Collections.Generic.List[string]
$jdkHomeForResolve = ""
$raw = @($args)
$i = 0
while ($i -lt $raw.Count) {
    if ($raw[$i] -eq '-JdkHome' -and ($i + 1) -lt $raw.Count) {
        $jdkHomeForResolve = [string]$raw[$i + 1]
        $i += 2
        continue
    }
    [void]$gradleArgs.Add([string]$raw[$i])
    $i++
}

if ($gradleArgs.Count -eq 0) {
    Write-Error @"
Missing Gradle tasks (for example :app:assembleDebug).

Usage:
  .\scripts\pns_gradlew.ps1 :app:assembleDebug
  .\scripts\pns_gradlew.ps1 -JdkHome "C:\Program Files\Android\Android Studio\jbr" :app:assembleDebug
"@
}

$javaScript = Join-Path $here "pns_java_home.ps1"
if (-not (Test-Path -LiteralPath $javaScript)) {
    Write-Error "Missing $javaScript"
}

$resolved = & $javaScript -EmitPath -JdkHome $jdkHomeForResolve
if (-not $resolved -or -not (Test-Path -LiteralPath (Join-Path $resolved "bin\java.exe"))) {
    Write-Error "JDK resolution failed. Run: .\scripts\pns_java_home.ps1 -ListCandidates"
}

$env:JAVA_HOME = $resolved
$bin = Join-Path $resolved "bin"
$paths = @($env:Path -split ';' | Where-Object { $_ })
if ($paths -notcontains $bin) {
    $env:Path = "$bin;$env:Path"
}

$root = (Resolve-Path (Join-Path $here "..")).Path
$gw = Join-Path $root "gradlew.bat"
if (-not (Test-Path -LiteralPath $gw)) {
    Write-Error "gradlew.bat not found at $gw"
}

Push-Location $root
try {
    & $gw @gradleArgs
    exit $LASTEXITCODE
}
finally {
    Pop-Location
}
