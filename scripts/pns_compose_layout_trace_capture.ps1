#Requires -Version 5.1
<#
.SYNOPSIS
  Warm-launch Preview route then capture a device Perfetto light trace tuned for UI / Compose investigation.

.DESCRIPTION
  Prepends SDK platform-tools to PATH (see pns_resolve_adb.ps1), optionally force-stops and starts
  MainActivity with ``pns_screen=preview``, waits, then runs ``pns_capture_perfetto_light.ps1`` with
  heavier atrace categories: ``gfx view sched wm input``. Output: ``perf-runs/perfetto_*.perfetto-trace``.

  Many OEMs require ``adb root`` for the default trace path; see pns_capture_perfetto_light.ps1.

.PARAMETER Serial
  adb -s. Omit to use scripts/pns_adb_device.env (PNS_ADB_SERIAL) or a single device.

.PARAMETER SkipLaunch
  Do not force-stop / am start; trace whatever is already on screen.

.PARAMETER SkipAdbRoot
  Forwarded to pns_capture_perfetto_light.ps1 (skip ``adb root`` attempt).

.PARAMETER WaitSecondsAfterLaunch
  Seconds to wait after ``am start`` before starting Perfetto (default 3).
#>
param(
    [string]$Serial = "",
    [string]$ProjectRoot = "",
    [ValidateRange(3, 120)]
    [int]$DurationSeconds = 8,
    [ValidateRange(0, 60)]
    [int]$WaitSecondsAfterLaunch = 3,
    [switch]$SkipLaunch,
    [switch]$SkipAdbRoot
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
    $fromEnv = Read-PnsAdbSerialFromEnvFile $here
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[compose_trace] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

function Invoke-AdbLine([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs }
    else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb not on PATH after pns_resolve_adb.ps1"
}

if (-not $SkipLaunch.IsPresent) {
    $pkg = "dev.pointandshoot"
    $startCmp = "$pkg/.MainActivity"
    Write-Host "`[compose_trace] force-stop + warm launch preview..."
    try { if ($Serial) { adb -s $Serial wait-for-device 2>$null | Out-Null } else { adb wait-for-device 2>$null | Out-Null } } catch {}
    Invoke-AdbLine @("shell", "am", "force-stop", $pkg)
    Invoke-AdbLine @("shell", "am", "start", "-n", $startCmp, "--es", "pns_screen", "preview")
    if ($WaitSecondsAfterLaunch -gt 0) {
        Start-Sleep -Seconds $WaitSecondsAfterLaunch
    }
}

$cap = Join-Path $here "pns_capture_perfetto_light.ps1"
if (-not (Test-Path -LiteralPath $cap)) { throw "Missing $cap" }

$composeCategories = "gfx view sched wm input"
Write-Host "`[compose_trace] invoking pns_capture_perfetto_light.ps1 (-DurationSeconds $DurationSeconds, categories: $composeCategories)"

$capArgs = @(
    "-ProjectRoot", $ProjectRoot,
    "-DurationSeconds", $DurationSeconds,
    "-Categories", $composeCategories
)
if ($Serial) { $capArgs += @("-Serial", $Serial) }
if ($SkipAdbRoot) { $capArgs += "-SkipAdbRoot" }

& $cap @capArgs
exit $LASTEXITCODE
