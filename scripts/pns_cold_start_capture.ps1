#Requires -Version 5.1
<#
.SYNOPSIS
  Writes perf-runs/perf_<stamp>.md (or perf_release_<stamp>.md with -Release) with cold-start TotalTime + PSS (+ log tail) per PERFORMANCE_BUDGETS.md.

.DESCRIPTION
  Thin wrapper around scripts/pns_hfr_autorun.ps1 -PerfReport (no probe suite). USB device required
  when adb is on PATH and a single device is online (or set PNS_ADB_SERIAL / -Serial).

.PARAMETER ProjectRoot
  Repo root (folder containing gradlew.bat). Default: parent of scripts/.

.PARAMETER Serial
  adb -s <serial>. Omit to use scripts/pns_adb_device.env (PNS_ADB_SERIAL) or a single online device.

.PARAMETER Release
  Build/install Release APK (``assembleRelease`` unless ``-SkipGradleBuild`` on the inner autorun) then same cold-start markdown as debug. Writes ``perf-runs/perf_release_*.md``.
#>
param(
    [string]$ProjectRoot = "",
    [string]$Serial = "",
    [switch]$Release,
    [switch]$SkipGradleBuild
)

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $here "..")).Path
}
else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$resolve = Join-Path $here "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

$hfr = Join-Path $here "pns_hfr_autorun.ps1"
if (-not (Test-Path -LiteralPath $hfr)) { throw "Missing $hfr" }

$args = @("-ProjectRoot", $ProjectRoot, "-PerfReport")
if ($Release) {
    $args += @("-PerfReportApkVariant", "Release")
}
if ($SkipGradleBuild) {
    $args += "-SkipGradleBuild"
}
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $args += @("-Serial", $Serial)
}

& $hfr @args
exit $LASTEXITCODE
