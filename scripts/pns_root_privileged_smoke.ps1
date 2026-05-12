# Milestone 7 Sprint 7.5 — in-app read-only `su -c` diagnostics (`RootPrivilegedDiagnostics`).
#
# Cold-starts **MainActivity** with **`pns_screen=rootsettings`** and **`--ez pns_auto_root_diagnostics true`**.
# When **RootCapability** is **Granted** (user tapped **Grant Su** once; state persisted), the app logs
# **`PNS.AdbValidation`** lines containing **`rootPrivScan`** ending with **`suite=read_only_done`**.
#
# Requires a **debuggable** debug APK (**`app-debug.apk`**). Optional **`adb root`** only affects host transport;
# in-app probes use **`ProcessBuilder("su", "-c", …)`** (Magisk / KernelSU / etc.).
#
# Serial: **`-Serial`** or **`scripts/pns_adb_device.env`** (**`PNS_ADB_SERIAL`**).

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [int]$WaitSec = 14,
    [switch]$SkipInstall,
    [switch]$SkipGradle,
    [switch]$TryAdbRoot,
    [switch]$RequireGrantedSuite
)

$ErrorActionPreference = "Stop"

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

$pkg = "dev.pointandshoot"
$activity = "dev.pointandshoot/.MainActivity"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$gradlewHelper = Join-Path $PSScriptRoot "pns_gradlew.ps1"

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\root_privileged_smoke_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

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

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs 2>$null } else { & adb @CmdArgs 2>$null }
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "[root_privileged_smoke] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

if ($TryAdbRoot.IsPresent) {
    Write-Host "[root_privileged_smoke] adb root (best-effort)"
    if ($Serial) { adb -s $Serial root 2>$null | Out-Null } else { adb root 2>$null | Out-Null }
    Start-Sleep -Seconds 2
}

if (-not $SkipGradle.IsPresent) {
    if (-not (Test-Path -LiteralPath $gradlewHelper)) {
        throw "Missing $gradlewHelper; cannot assembleDebug."
    }
    Write-Host "[root_privileged_smoke] $($gradlewHelper) :app:assembleDebug"
    & $gradlewHelper ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
}

if (-not $SkipInstall.IsPresent) {
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "Missing APK: $apk (run without -SkipGradle or build once)."
    }
    Write-Host "[root_privileged_smoke] adb install -r -t"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($Serial) { $installOut = & adb -s $Serial install -r -t $apk 2>&1 }
        else { $installOut = & adb install -r -t $apk 2>&1 }
    }
    finally { $ErrorActionPreference = $prevEap }
    $installOut | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "adb install failed exit=$LASTEXITCODE" }
}

Write-Host "[root_privileged_smoke] pm grant CAMERA (best-effort)"
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")

Write-Host "[root_privileged_smoke] force-stop + cold start rootsettings + pns_auto_root_diagnostics"
Invoke-Adb @("shell", "am", "force-stop", $pkg)
$startArgs = @(
    "shell", "am", "start", "-W", "-n", $activity,
    "--activity-clear-task", "-S",
    "--es", "pns_screen", "rootsettings",
    "--ez", "pns_auto_root_diagnostics", "true"
)
if ($Serial) { & adb -s $Serial @startArgs } else { & adb @startArgs }
if ($LASTEXITCODE -ne 0) { throw "am start failed exit=$LASTEXITCODE" }

Write-Host "[root_privileged_smoke] waiting ${WaitSec}s for diagnostics..."
Start-Sleep -Seconds $WaitSec

$logPath = Join-Path $OutDir "logcat_tail_for_rootPrivScan.txt"
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
    if ($Serial) {
        & adb -s $Serial logcat -d -t 2500 "*:I" 2>&1 | Set-Content -LiteralPath $logPath -Encoding utf8
    }
    else {
        & adb logcat -d -t 2500 "*:I" 2>&1 | Set-Content -LiteralPath $logPath -Encoding utf8
    }
}
finally { $ErrorActionPreference = $prevEap }

if (-not (Test-Path -LiteralPath $logPath)) {
    "" | Set-Content -LiteralPath $logPath -Encoding utf8
}

$logText = ""
if (Test-Path -LiteralPath $logPath) {
    $logText = [System.IO.File]::ReadAllText($logPath, [System.Text.UTF8Encoding]::new($false))
}

$hits = @($logText -split "`n" | Where-Object { $_ -match "rootPrivScan" })
$hitsPath = Join-Path $OutDir "root_priv_scan_lines.txt"
$hits -join "`n" | Set-Content -LiteralPath $hitsPath -Encoding utf8

$suiteDone = $logText -match "rootPrivScan suite=read_only_done"
$skipped = $logText -match "rootPrivScan skipped"
# Default: wiring check (cold-start reached [RootPrivilegedDiagnostics] via intent).
# Use -RequireGrantedSuite for fleet gates that demand the full read-only SU suite.
$pass = if ($RequireGrantedSuite.IsPresent) { $suiteDone } else { $suiteDone -or $skipped }

$meta = [ordered]@{
    schema       = "pns.root_privileged_smoke.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    serial       = $(if ($Serial) { $Serial } else { "default" })
    outDir       = $OutDir
    waitSec      = $WaitSec
    pass         = $pass
    suiteDone    = $suiteDone
    skippedLine  = $skipped
    requireGrantedSuite = $RequireGrantedSuite.IsPresent
    hint         = $(if (-not $pass) {
            if ($RequireGrantedSuite.IsPresent) {
                "Use in-app Grant Su once (state persists) for suite=read_only_done, or omit -RequireGrantedSuite for wiring-only pass."
            }
            else {
                "No rootPrivScan lines in log tail; increase -WaitSec or check pns_screen=rootsettings."
            }
        } elseif ($skipped -and -not $suiteDone) {
            "Wiring OK (skipped state); tap Grant Su once for full read-only suite on this device."
        } else { $null })
}
$jsonPath = Join-Path $OutDir "root_privileged_smoke.json"
$meta | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $jsonPath -Encoding utf8

Write-Host "[root_privileged_smoke] wrote $jsonPath"
if (-not $pass) {
    Write-Warning "[root_privileged_smoke] pass=false (see $hitsPath and root_privileged_smoke.json)."
    exit 1
}
Write-Host "[root_privileged_smoke] OK"
exit 0
