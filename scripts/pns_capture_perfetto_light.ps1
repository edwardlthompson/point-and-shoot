# Sprint 7.1  -  lightweight Perfetto trace via *device* /system/bin/perfetto (light flags).
#
# Uses perfetto "light configuration" (see `perfetto --help` when NOT using -c): duration,
# ring buffer, `-a` atrace app, then atrace category tokens (e.g. gfx view sched).
#
# On many OEM builds the trace file cannot be written to /data/local/tmp as the adb shell user;
# this script writes to /data/misc/perfetto-traces/profiling/ which typically requires `adb root`.
#
# Serial: -Serial or scripts/pns_adb_device.env (PNS_ADB_SERIAL, USB serial from adb devices).
#
# Pair with `pns_hfr_autorun.ps1 -PerfReport` in the same release-prep slice (see PERFORMANCE_BUDGETS.md).

param(
    [string]$Serial = "",
    [ValidateRange(3, 120)]
    [int]$DurationSeconds = 5,
    [string]$Package = "dev.pointandshoot",
    # Space-separated atrace categories passed as trailing tokens to `perfetto` (light mode).
    [string]$Categories = "gfx view sched",
    [string]$ProjectRoot = "",
    [switch]$SkipAdbRoot,
    [switch]$AlsoPerfReport
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
}
else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($k -eq "PNS_ADB_SERIAL") { return $v }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[perfetto_light] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs 2>$null } else { & adb @CmdArgs 2>$null }
}

$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCmd) { throw "Required command not found: adb" }

if (-not $SkipAdbRoot.IsPresent) {
    Write-Host "`[perfetto_light] adb root (best-effort; required on many OEMs for trace write path)"
    if ($Serial) { adb -s $Serial root 2>$null | Out-Null } else { adb root 2>$null | Out-Null }
    Start-Sleep -Seconds 2
    if ($Serial) { adb -s $Serial wait-for-device | Out-Null } else { adb wait-for-device | Out-Null }
}

$idOut = if ($Serial) { & adb -s $Serial shell id 2>&1 } else { & adb shell id 2>&1 }
$isRoot = $idOut -match 'uid=0\(root\)'
if (-not $isRoot) {
    Write-Warning "[perfetto_light] adb shell is not uid=0(root). Trace open may fail; use -SkipAdbRoot only if your device allows perfetto writes without adb root, or capture via Android Studio (PERFORMANCE_BUDGETS.md)."
}

$resolvedSerial = (& adb get-serialno 2>$null | Select-Object -First 1)
if ($null -ne $resolvedSerial) { $resolvedSerial = $resolvedSerial.ToString().Trim() }
if ([string]::IsNullOrWhiteSpace($resolvedSerial) -or $resolvedSerial -eq "unknown") {
    throw "Could not resolve adb serial (adb get-serialno). Pass -Serial or connect exactly one authorized device."
}
$fnSerial = $resolvedSerial -replace ':', '_'

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$remoteName = "pns_perfetto_light_$utc.perfetto-trace"
$remotePath = "/data/misc/perfetto-traces/profiling/$remoteName"

$perfDir = Join-Path $ProjectRoot "perf-runs"
New-Item -ItemType Directory -Force -Path $perfDir | Out-Null
$localPath = Join-Path $perfDir "perfetto_${utc}_serial-$fnSerial.perfetto-trace"

$durArg = "${DurationSeconds}s"
$catParts = @($Categories -split '\s+' | Where-Object { $_.Length -gt 0 })
$catJoined = [string]::Join(' ', $catParts)

# perfetto light mode: trailing tokens are atrace categories; -a pins the app.
$inner = "rm -f $remotePath; perfetto -t $durArg -o $remotePath -a $Package $catJoined"
Write-Host "`[perfetto_light] recording ${DurationSeconds}s -> $remotePath (resolved adb serial: $resolvedSerial)"
Invoke-Adb @("shell", $inner)

Write-Host "`[perfetto_light] adb pull -> $localPath"
if ($Serial) {
    & adb -s $Serial pull $remotePath $localPath
}
else {
    & adb pull $remotePath $localPath
}
if ($LASTEXITCODE -ne 0) { throw "adb pull failed exit=$LASTEXITCODE" }

Write-Host "`[perfetto_light] OK: $localPath"

if ($AlsoPerfReport.IsPresent) {
    $hr = Join-Path $PSScriptRoot "pns_hfr_autorun.ps1"
    if (-not (Test-Path -LiteralPath $hr)) { throw "Missing: $hr" }
    $perfArgs = @("-ProjectRoot", $ProjectRoot, "-PerfReport")
    if ($Serial) {
        $perfArgs += @("-Serial", $Serial)
    }
    elseif ($resolvedSerial -and $resolvedSerial -ne "unknown") {
        $perfArgs += @("-Serial", $resolvedSerial)
    }
    Write-Host "`[perfetto_light] also running pns_hfr_autorun.ps1 -PerfReport"
    & $hr @perfArgs
    if ($LASTEXITCODE -ne 0) { throw "pns_hfr_autorun -PerfReport failed exit=$LASTEXITCODE" }
}
