#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.B** — adb logcat crash buffer triage (requires device).
#>
param(
    [string]$Serial = "",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
if (Test-Path (Join-Path $PSScriptRoot "pns_resolve_adb.ps1")) {
    . (Join-Path $PSScriptRoot "pns_resolve_adb.ps1") -PrependToPath -Quiet
}
$root = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
if (-not $OutDir) { $OutDir = Join-Path $root "hfr-runs\crash_triage_$utc" }
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$adb = @()
if ($Serial) { $adb = @("-s", $Serial) }
$log = Join-Path $OutDir "logcat_crash.txt"
& adb @adb shell logcat -b crash -d 2>$null | Out-File $log -Encoding utf8
$hay = Get-Content $log -Raw -ErrorAction SilentlyContinue
$fatals = @()
if ($hay) {
    $fatals = [regex]::Matches($hay, '(?m)^.*FATAL EXCEPTION.*$') | ForEach-Object { $_.Value }
}
$report = Join-Path $OutDir "crash_triage_$utc.md"
@"
# Crash triage $utc

Fatal lines: $($fatals.Count)

$(if ($fatals.Count -gt 0) { $fatals -join "`n" } else { '(none in crash buffer)' })
"@ | Set-Content $report -Encoding utf8
Write-Host "CRASH TRIAGE: wrote $report"
& adb @adb shell am force-stop dev.pointandshoot 2>$null | Out-Null
exit 0
