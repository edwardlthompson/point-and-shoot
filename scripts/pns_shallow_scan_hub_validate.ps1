# Milestone 10.1 — validate **engineering hub shallow scan** log line after cold-start **probehub**.
#
# Asserts **logcat** contains **`PNS.ProbeHub`** (single-line gate; avoids huge **`PNS.Probe`** markdown dumps filling the ring buffer) matching:
#   `Shallow scan: <digits>ms cameras=<digits> degraded=true|false`
# (emitted from `CameraCapabilitiesProbe` after `buildProbeReport` on `Dispatchers.Default`).
#
# - Installs **app-debug.apk** unless **`-SkipInstall`** (requires prior **`:app:assembleDebug`**).
# - Grants **CAMERA**, clears logcat, **force-stop** + **`am start`** `pns_screen=probehub`, waits **`-WaitSec`**, dumps **PNS.Probe** buffer.
# - Writes **`shallow_scan_hub_validate.json`** under **`-OutDir`** (schema **`pns.shallow_scan_hub_validate.v1`**).
#
# No authorized USB device: writes stub JSON (**`adbConnected: false`**, **`pass: true`**, **`skippedReason`**) and exits **0** (host/CI friendly), same pattern as **`pns_failure_matrix_smoke.ps1`** stubs.
#
# Serial: **`-Serial`** or **`scripts/pns_adb_device.env`** (`PNS_ADB_SERIAL`).

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [int]$WaitSec = 14,
    [switch]$AppendSection5,
    [string]$ProbePlan = ""
)

$ErrorActionPreference = "Stop"

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$activity = "$pkg/.MainActivity"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$gradlewHelper = Join-Path $PSScriptRoot "pns_gradlew.ps1"

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\shallow_scan_hub_validate_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($k -eq "PNS_ADB_SERIAL") {
            return $v
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "[shallow_scan_hub] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs
    }
    else {
        & adb @CmdArgs
    }
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE"
    }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs 2>$null
    }
    else {
        & adb @CmdArgs 2>$null
    }
}

function Test-AdbAuthorizedDevice {
    $lines = @(adb devices 2>&1)
    foreach ($line in $lines) {
        if ($line -match '\tdevice$') {
            return $true
        }
    }
    return $false
}

$reShallow = [regex]'Shallow scan:\s+\d+ms\s+cameras=\d+\s+degraded=(true|false)'
$reProbeBuilt = [regex]'Probe built \(\d+ chars\)'
$reFleetMatrix = [regex]'scanTier=(quick|full)'
$matrixFileName = "fleet_device_matrix.json"

function Invoke-AssembleDebugIfNeeded {
    param([bool]$Force)
    if ($SkipInstall) { return }
    if (-not $Force -and (Test-Path -LiteralPath $apk)) { return }
    if (-not (Test-Path -LiteralPath $gradlewHelper)) {
        throw "Missing $gradlewHelper; cannot assembleDebug."
    }
    Write-Host "[shallow_scan_hub] $gradlewHelper :app:assembleDebug"
    & $gradlewHelper ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "assembleDebug finished but APK still missing: $apk"
    }
}

if (-not (Test-AdbAuthorizedDevice)) {
    Write-Warning "[shallow_scan_hub] No authorized adb device — stub JSON only."
    $stub = [ordered]@{
        schema            = "pns.shallow_scan_hub_validate.v1"
        adbConnected      = $false
        pass              = $true
        skippedReason     = "no_authorized_device"
        shallowScanHubOk  = $false
        matchedShallowLine = ""
        probeBuiltOk      = $false
        timestampUtc      = [DateTime]::UtcNow.ToString("o")
        outDir            = $OutDir
        serial            = $(if ($Serial) { $Serial } else { "none" })
    }
    $jp = Join-Path $OutDir "shallow_scan_hub_validate.json"
    $stub | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jp -Encoding utf8
    Write-Host "[shallow_scan_hub] Wrote $jp (stub)"
    exit 0
}

if ($AssembleDebug) {
    Invoke-AssembleDebugIfNeeded -Force $true
}
else {
    Invoke-AssembleDebugIfNeeded -Force $false
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk — run assembleDebug or use -AssembleDebug."
}

if (-not $SkipInstall.IsPresent) {
    Write-Host "[shallow_scan_hub] adb install -r -t"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($Serial) { $null = & adb -s $Serial install -r -t $apk 2>&1 }
        else { $null = & adb install -r -t $apk 2>&1 }
    }
    finally { $ErrorActionPreference = $prevEap }
    if ($LASTEXITCODE -ne 0) { throw "adb install failed exit=$LASTEXITCODE" }
}

Write-Host "[shallow_scan_hub] pm grant CAMERA (best-effort)"
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")

Write-Host "[shallow_scan_hub] logcat clear + cold start probehub (${WaitSec}s wait)"
$null = Invoke-AdbIgnore @("logcat", "-c")
Invoke-Adb @("shell", "am", "force-stop", $pkg)
Start-Sleep -Milliseconds 600
$startArgs = @(
    "shell", "am", "start", "-W", "-n", $activity,
    "--activity-clear-task", "-S",
    "--es", "pns_screen", "probehub"
)
if ($Serial) { & adb -s $Serial @startArgs } else { & adb @startArgs }
if ($LASTEXITCODE -ne 0) { throw "am start failed exit=$LASTEXITCODE" }

Start-Sleep -Seconds ([Math]::Max(5, $WaitSec))

$logPath = Join-Path $OutDir "logcat_shallow_scan_hub.txt"
$ErrorActionPreference = "Continue"
try {
    # `*:S` first silences all tags; then allow-list hub + probe INFO lines. Do not use `-t` here: huge `PNS.Probe`
    # markdown dumps can push the hub line out of a short tail even when filters are applied first on-device.
    if ($Serial) {
        & adb -s $Serial logcat -d "*:S" "PNS.ProbeHub:I" "PNS.Probe:I" "PNS.FleetMatrix:I" 2>&1 | Set-Content -LiteralPath $logPath -Encoding utf8
    }
    else {
        & adb logcat -d "*:S" "PNS.ProbeHub:I" "PNS.Probe:I" "PNS.FleetMatrix:I" 2>&1 | Set-Content -LiteralPath $logPath -Encoding utf8
    }
}
finally {
    $ErrorActionPreference = "Stop"
}

$logText = if (Test-Path -LiteralPath $logPath) { [System.IO.File]::ReadAllText($logPath) } else { "" }
$matched = ""
foreach ($line in ($logText -split "`r?`n")) {
    if ($line -match "PNS\.ProbeHub" -and $reShallow.IsMatch($line)) {
        $matched = $line.Trim()
        break
    }
}
if ($matched.Length -eq 0) {
    foreach ($line in ($logText -split "`r?`n")) {
        if ($reShallow.IsMatch($line)) {
            $matched = $line.Trim()
            break
        }
    }
}
$shallowOk = $matched.Length -gt 0
$builtOk = $reProbeBuilt.IsMatch($logText)
$matchedFleet = ""
foreach ($line in ($logText -split "`r?`n")) {
    if ($line -match "PNS\.FleetMatrix" -and $reFleetMatrix.IsMatch($line)) {
        $matchedFleet = $line.Trim()
        break
    }
}
$fleetMatrixLogOk = $matchedFleet.Length -gt 0

$matrixOut = Join-Path $OutDir $matrixFileName
$matrixPulled = $false
$matrixSchemaOk = $false
try {
    if ($Serial) {
        & adb -s $Serial exec-out run-as $pkg cat "files/$matrixFileName" | Set-Content -LiteralPath $matrixOut -Encoding utf8
    }
    else {
        & adb exec-out run-as $pkg cat "files/$matrixFileName" | Set-Content -LiteralPath $matrixOut -Encoding utf8
    }
    if ((Test-Path -LiteralPath $matrixOut) -and ((Get-Item -LiteralPath $matrixOut).Length -gt 64)) {
        $matrixPulled = $true
        $matrix = Get-Content -LiteralPath $matrixOut -Raw -Encoding UTF8 | ConvertFrom-Json
        $matrixSchemaOk = ($matrix.schemaVersion -eq 1)
    }
}
catch {
    Write-Warning "[shallow_scan_hub] fleet matrix pull failed: $_"
}

$pass = $shallowOk -and $builtOk -and $matrixPulled -and $matrixSchemaOk -and $fleetMatrixLogOk

$obj = [ordered]@{
    schema             = "pns.shallow_scan_hub_validate.v1"
    adbConnected       = $true
    pass               = $pass
    shallowScanHubOk   = $shallowOk
    probeBuiltOk       = $builtOk
    matchedShallowLine = $matched
    fleetMatrixLogOk     = $fleetMatrixLogOk
    matchedFleetLine     = $matchedFleet
    matrixPulled         = $matrixPulled
    matrixSchemaOk       = $matrixSchemaOk
    matrixRelPath        = $matrixFileName
    waitSec            = $WaitSec
    timestampUtc       = [DateTime]::UtcNow.ToString("o")
    outDir             = $OutDir
    logcatRelPath      = "logcat_shallow_scan_hub.txt"
    serial             = $(if ($Serial) { $Serial } else { "default" })
}
$jsonPath = Join-Path $OutDir "shallow_scan_hub_validate.json"
$obj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host "[shallow_scan_hub] Wrote $jsonPath pass=$pass shallowOk=$shallowOk builtOk=$builtOk matrix=$matrixPulled schema=$matrixSchemaOk fleetLog=$fleetMatrixLogOk"

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)

if (-not $pass) {
    Write-Host "[shallow_scan_hub] FAIL: expected PNS.ProbeHub Shallow scan + Probe built + fleet matrix (schemaVersion=1) + PNS.FleetMatrix in $logPath"
    exit 1
}

if ($AppendSection5.IsPresent) {
    $append = Join-Path $PSScriptRoot "pns_probe_append_section5.ps1"
    Write-Host "[shallow_scan_hub] AppendSection5 <- $jsonPath"
    $invokeArgs = @{ GateJson = $jsonPath; PassOnly = $true }
    if ($ProbePlan) { $invokeArgs["ProbePlan"] = $ProbePlan }
    & $append @invokeArgs
    if ($LASTEXITCODE -ne 0) {
        throw "pns_probe_append_section5.ps1 failed exit=$LASTEXITCODE"
    }
}

exit 0
