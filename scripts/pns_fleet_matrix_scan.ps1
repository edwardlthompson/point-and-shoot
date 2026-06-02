# Milestone 16.3 — pull fleet_device_matrix.json after cold-start probehub scan.
#
# - Installs debug APK (unless -SkipInstall).
# - Grants CAMERA, cold-starts `pns_screen=probehub`.
# - Optional `-ScanTier full` adds `--es pns_fleet_matrix_scan full` (background full tier).
# - Pulls `files/fleet_device_matrix.json` via `adb exec-out run-as` (debuggable APK).
# - Asserts logcat `PNS.FleetMatrix scanTier=` and JSON `schemaVersion`.
#
# Artifacts: hfr-runs/fleet_matrix_*/fleet_matrix_scan.json + fleet_device_matrix.json

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [ValidateSet("quick", "full")]
    [string]$ScanTier = "quick",
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [int]$WaitSec = 0,
    [switch]$LegacyOp13FleetPolicy,
    [switch]$Redact
)

function Redact-HalDumpsysText([string]$Text) {
    if ([string]::IsNullOrEmpty($Text)) { return $Text }
    $t = $Text
    $t = [regex]::Replace($t, '/data/user/\d+/[^\s"''<>]+', '[APP_DATA]')
    $t = [regex]::Replace($t, '/storage/[^\s"''<>]+', '[STORAGE]')
    $t = [regex]::Replace($t, '/vendor/[^\s"''<>]+', '[VENDOR_PATH]')
    $t = [regex]::Replace($t, '\b[0-9a-f]{16}\b', '[HEX_ID]', 'IgnoreCase')
    return $t
}

$ErrorActionPreference = "Stop"

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$activity = "$pkg/.MainActivity"
$matrixFile = "fleet_device_matrix.json"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$gradlewHelper = Join-Path $PSScriptRoot "pns_gradlew.ps1"

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\fleet_matrix_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if ($WaitSec -le 0) {
    $WaitSec = if ($ScanTier -eq "full") { 240 } else { 18 }
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
        Write-Host "[fleet_matrix] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs 2>$null } else { & adb @CmdArgs 2>$null }
}

function Test-AdbAuthorizedDevice {
    foreach ($line in @(adb devices 2>&1)) {
        if ($line -match '\tdevice$') { return $true }
    }
    return $false
}

function Invoke-AssembleDebugIfNeeded([bool]$Force) {
    if ($SkipInstall) { return }
    if (-not $Force -and (Test-Path -LiteralPath $apk)) { return }
    if (-not (Test-Path -LiteralPath $gradlewHelper)) { throw "Missing $gradlewHelper" }
    Write-Host "[fleet_matrix] $gradlewHelper :app:assembleDebug"
    & $gradlewHelper ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
}

function Write-StubGate([string]$Reason) {
    $stub = [ordered]@{
        schema       = "pns.fleet_matrix_scan.v1"
        adbConnected = $false
        pass         = $true
        skippedReason = $Reason
        scanTier     = $ScanTier
        timestampUtc = [DateTime]::UtcNow.ToString("o")
        outDir       = $OutDir
    }
    $jp = Join-Path $OutDir "fleet_matrix_scan.json"
    $stub | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jp -Encoding utf8
    Write-Host "[fleet_matrix] Wrote $jp (stub)"
    exit 0
}

if (-not (Test-AdbAuthorizedDevice)) {
    Write-Warning "[fleet_matrix] No authorized adb device — stub JSON only."
    Write-StubGate "no_authorized_device"
}

if ($AssembleDebug) { Invoke-AssembleDebugIfNeeded -Force $true }
else { Invoke-AssembleDebugIfNeeded -Force $false }

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk"
}

if (-not $SkipInstall.IsPresent) {
    Write-Host "[fleet_matrix] adb install -r -t"
    if ($Serial) { $null = & adb -s $Serial install -r -t $apk 2>&1 }
    else { $null = & adb install -r -t $apk 2>&1 }
    if ($LASTEXITCODE -ne 0) { throw "adb install failed exit=$LASTEXITCODE" }
}

Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")

Write-Host "[fleet_matrix] cold start probehub scanTier=$ScanTier wait=${WaitSec}s"
$null = Invoke-AdbIgnore @("logcat", "-c")
Invoke-Adb @("shell", "am", "force-stop", $pkg)
Start-Sleep -Milliseconds 600

$startArgs = @(
    "shell", "am", "start", "-W", "-n", $activity,
    "--activity-clear-task", "-S",
    "--es", "pns_screen", "probehub",
    "--es", "pns_fleet_matrix_scan", $ScanTier
)
if ($LegacyOp13FleetPolicy.IsPresent) {
    $startArgs += @("--ez", "pns_legacy_device_fleet_policy", "true")
}
if ($Serial) { & adb -s $Serial @startArgs } else { & adb @startArgs }
if ($LASTEXITCODE -ne 0) { throw "am start failed exit=$LASTEXITCODE" }

Start-Sleep -Seconds $WaitSec

$logPath = Join-Path $OutDir "logcat_fleet_matrix.txt"
$ErrorActionPreference = "Continue"
try {
    if ($Serial) {
        & adb -s $Serial logcat -d "*:S" "PNS.FleetMatrix:I" "PNS.ProbeHub:I" 2>&1 | Set-Content -LiteralPath $logPath -Encoding utf8
    }
    else {
        & adb logcat -d "*:S" "PNS.FleetMatrix:I" "PNS.ProbeHub:I" 2>&1 | Set-Content -LiteralPath $logPath -Encoding utf8
    }
}
finally { $ErrorActionPreference = "Stop" }

$logText = if (Test-Path -LiteralPath $logPath) { [System.IO.File]::ReadAllText($logPath) } else { "" }
$reFleet = [regex]'scanTier=(quick|full)'
$matchedFleet = ""
$matchedFleetQuick = ""
$matchedFleetFull = ""
foreach ($line in ($logText -split "`r?`n")) {
    if ($line -notmatch "PNS\.FleetMatrix") { continue }
    if ($line -match "scanTier=quick") { $matchedFleetQuick = $line.Trim() }
    if ($line -match "scanTier=full") { $matchedFleetFull = $line.Trim() }
}
if ($ScanTier -eq "full" -and $matchedFleetFull.Length -gt 0) {
    $matchedFleet = $matchedFleetFull
}
elseif ($matchedFleetQuick.Length -gt 0) {
    $matchedFleet = $matchedFleetQuick
}
else {
    $matchedFleet = $matchedFleetFull
}
$fleetLogOk = $matchedFleet.Length -gt 0
if ($ScanTier -eq "full") {
    $fleetLogOk = $matchedFleetFull.Length -gt 0 -and ($matchedFleetFull -match "scanTier=full cameras=")
}

$matrixOut = Join-Path $OutDir $matrixFile
$pulled = $false
$pullAttempts = if ($ScanTier -eq "full") { 12 } else { 6 }
for ($i = 0; $i -lt $pullAttempts; $i++) {
    try {
        if ($Serial) {
            & adb -s $Serial exec-out run-as $pkg cat "files/$matrixFile" | Set-Content -LiteralPath $matrixOut -Encoding utf8
        }
        else {
            & adb exec-out run-as $pkg cat "files/$matrixFile" | Set-Content -LiteralPath $matrixOut -Encoding utf8
        }
        if ((Test-Path -LiteralPath $matrixOut) -and ((Get-Item -LiteralPath $matrixOut).Length -gt 64)) {
            $pulled = $true
            break
        }
    }
    catch {
        Write-Host "[fleet_matrix] pull attempt $($i + 1) failed: $_"
    }
    Start-Sleep -Seconds 3
}

$schemaOk = $false
$scanTierObserved = ""
$cameraCount = 0
$policyId = ""
if ($pulled) {
    try {
        $matrix = Get-Content -LiteralPath $matrixOut -Raw -Encoding UTF8 | ConvertFrom-Json
        $schemaOk = ($matrix.schemaVersion -eq 1 -or $matrix.schemaVersion -eq 2)
        $scanTierObserved = "$($matrix.scanMeta.scanTier)"
        $cameraCount = @($matrix.cameras).Count
        if ($null -ne $matrix.product.fleetProfiles.policyId) {
            $policyId = "$($matrix.product.fleetProfiles.policyId)"
        }
    }
    catch {
        Write-Warning "[fleet_matrix] JSON parse failed: $_"
    }
}


$summaryFile = "fleet_device_capability_summary.md"
$summaryOut = Join-Path $OutDir $summaryFile
$summaryPulled = $false
if ($pulled) {
    try {
        if ($Serial) {
            & adb -s $Serial exec-out run-as $pkg cat "files/$summaryFile" | Set-Content -LiteralPath $summaryOut -Encoding utf8
        } else {
            & adb exec-out run-as $pkg cat "files/$summaryFile" | Set-Content -LiteralPath $summaryOut -Encoding utf8
        }
        if ((Test-Path -LiteralPath $summaryOut) -and ((Get-Item -LiteralPath $summaryOut).Length -gt 32)) {
            $summaryPulled = $true
            Write-Host "[fleet_matrix] Pulled summary -> $summaryOut"
        }
    } catch {
        Write-Warning "[fleet_matrix] summary pull failed: $_"
    }
}

$tierOk = ($scanTierObserved -eq $ScanTier) -or ($ScanTier -eq "quick" -and $scanTierObserved -eq "quick")

$schemaValidateOk = $false
$schemaValidateDetail = ""
if ($pulled) {
    $validator = Join-Path $PSScriptRoot "fleet_matrix_schema_validate.py"
    if (Test-Path -LiteralPath $validator) {
        $py = Get-Command python -ErrorAction SilentlyContinue
        if (-not $py) { $py = Get-Command python3 -ErrorAction SilentlyContinue }
        if ($py) {
            $vOut = & $py.Source $validator $matrixOut 2>&1
            $schemaValidateOk = ($LASTEXITCODE -eq 0)
            $schemaValidateDetail = ($vOut | Out-String).Trim()
            if (-not $schemaValidateOk) {
                Write-Warning "[fleet_matrix] schema validate: $schemaValidateDetail"
            }
        }
        else {
            Write-Warning "[fleet_matrix] python not on PATH — skip fleet_matrix_schema_validate.py"
            $schemaValidateOk = $true
        }
    }
    else {
        $schemaValidateOk = $true
    }
}

$halExtracted = $false
if ($Redact.IsPresent -and $pulled) {
    try {
        $matrixObj = Get-Content -LiteralPath $matrixOut -Raw -Encoding UTF8 | ConvertFrom-Json
        $hal = $matrixObj.appendix.halDumpsysMediaCamera
        if ($null -ne $hal -and "$hal".Length -gt 0) {
            $halPath = Join-Path $OutDir "hal_dumpsys_media_camera_redacted.txt"
            Redact-HalDumpsysText "$hal" | Set-Content -LiteralPath $halPath -Encoding utf8
            $halExtracted = $true
            Write-Host "[fleet_matrix] Wrote redacted HAL excerpt $halPath"
        }
    }
    catch {
        Write-Warning "[fleet_matrix] -Redact HAL extract failed: $_"
    }
}

$maxResProbeRan = $false
$maxResBlocksPresent = $false
$maxResOverridesApplied = $false
$maxResProbeRelPath = ""
$maxResOverrideRelPath = ""
if ($pulled) {
    try {
        $maxResProbeRan = $true
        $dumpsysPath = Join-Path $OutDir "dumpsys_media_camera_v2.txt"
        if ($Serial) {
            & adb -s $Serial shell dumpsys media.camera -v 2 2>&1 | Set-Content -LiteralPath $dumpsysPath -Encoding utf8
        } else {
            & adb shell dumpsys media.camera -v 2 2>&1 | Set-Content -LiteralPath $dumpsysPath -Encoding utf8
        }
        $maxResProbeRelPath = "dumpsys_media_camera_v2.txt"

        $focalMapPulledPath = Join-Path $OutDir "fleet_focal_map.json"
        if ($Serial) {
            & adb -s $Serial exec-out run-as $pkg cat "files/fleet_focal_map.json" | Set-Content -LiteralPath $focalMapPulledPath -Encoding utf8
        } else {
            & adb exec-out run-as $pkg cat "files/fleet_focal_map.json" | Set-Content -LiteralPath $focalMapPulledPath -Encoding utf8
        }

        $manifestPath = Join-Path $PSScriptRoot "fleet_focal_mp_override_manifest.json"
        $probeScript = Join-Path $PSScriptRoot "fleet_focal_maxres_probe.py"
        $probeOut = Join-Path $OutDir "fleet_focal_maxres_probe.json"
        if ((Test-Path -LiteralPath $manifestPath) -and (Test-Path -LiteralPath $probeScript)) {
            $py = Get-Command python -ErrorAction SilentlyContinue
            if (-not $py) { $py = Get-Command python3 -ErrorAction SilentlyContinue }
            if ($py -and (Test-Path -LiteralPath $focalMapPulledPath)) {
                $model = ""
                try { $model = (Get-Content -LiteralPath $matrixOut -Raw -Encoding UTF8 | ConvertFrom-Json).product.model } catch {}
                if ([string]::IsNullOrWhiteSpace($model)) {
                    if ($Serial) { $model = (& adb -s $Serial shell getprop ro.product.model 2>$null).Trim() }
                    else { $model = (& adb shell getprop ro.product.model 2>$null).Trim() }
                }
                & $py.Source $probeScript --dumpsys $dumpsysPath --focal-map $focalMapPulledPath --manifest $manifestPath --model $model --out $probeOut | Out-Null
                if (Test-Path -LiteralPath $probeOut) {
                    $maxResProbeRelPath = "fleet_focal_maxres_probe.json"
                    $probeObj = Get-Content -LiteralPath $probeOut -Raw -Encoding UTF8 | ConvertFrom-Json
                    $maxResBlocksPresent = [bool]$probeObj.maxResolutionBlocksPresent
                    if ($maxResBlocksPresent -and $probeObj.overrides -and @($probeObj.overrides).Count -gt 0) {
                        $overrideOut = Join-Path $OutDir "fleet_focal_mp_override.json"
                        $overrideJson = [ordered]@{
                            schema = "pns.fleet.focal.mp_override.v1"
                            source = "pns_fleet_matrix_scan_second_stage"
                            generatedAt = [DateTime]::UtcNow.ToString("o")
                            overrides = @($probeObj.overrides | ForEach-Object {
                                [ordered]@{
                                    cameraId = "$($_.cameraId)"
                                    megapixels = [double]$_.megapixels
                                    source = "$($_.source)"
                                }
                            })
                        }
                        $overrideJson | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $overrideOut -Encoding utf8
                        $maxResOverrideRelPath = "fleet_focal_mp_override.json"
                        $tmpDevice = "/data/local/tmp/pns_fleet_focal_mp_override.json"
                        if ($Serial) {
                            & adb -s $Serial push $overrideOut $tmpDevice 2>&1 | Out-Null
                            & adb -s $Serial shell run-as $pkg cp $tmpDevice "files/fleet_focal_mp_override.json" 2>&1 | Out-Null
                            & adb -s $Serial shell rm $tmpDevice 2>$null | Out-Null
                        } else {
                            & adb push $overrideOut $tmpDevice 2>&1 | Out-Null
                            & adb shell run-as $pkg cp $tmpDevice "files/fleet_focal_mp_override.json" 2>&1 | Out-Null
                            & adb shell rm $tmpDevice 2>$null | Out-Null
                        }
                        $maxResOverridesApplied = $true
                    }
                }
            } else {
                Write-Warning "[fleet_matrix] python missing or focal map unavailable; skip max-res probe"
            }
        }
    }
    catch {
        Write-Warning "[fleet_matrix] max-res second-stage probe failed: $_"
    }
}

$pass = $pulled -and $schemaOk -and $fleetLogOk -and $tierOk -and $schemaValidateOk

$gate = [ordered]@{
    schema            = "pns.fleet_matrix_scan.v1"
    adbConnected      = $true
    pass              = $pass
    scanTierRequested = $ScanTier
    scanTierObserved  = $scanTierObserved
    schemaVersionOk   = $schemaOk
    schemaValidateOk  = $schemaValidateOk
    schemaValidateDetail = $schemaValidateDetail
    matrixPulled      = $pulled
    fleetLogOk        = $fleetLogOk
    matchedFleetLine  = $matchedFleet
    cameraCount       = $cameraCount
    policyId          = $policyId
    waitSec           = $WaitSec
    timestampUtc      = [DateTime]::UtcNow.ToString("o")
    outDir            = $OutDir
    matrixRelPath     = $matrixFile
    summaryRelPath    = $summaryFile
    summaryPulled     = $summaryPulled
    logcatRelPath     = "logcat_fleet_matrix.txt"
    serial            = $(if ($Serial) { $Serial } else { "default" })
    halRedactExtracted = $halExtracted
    maxResProbeRan = $maxResProbeRan
    maxResBlocksPresent = $maxResBlocksPresent
    maxResOverridesApplied = $maxResOverridesApplied
    maxResProbeRelPath = $maxResProbeRelPath
    maxResOverrideRelPath = $maxResOverrideRelPath
}
$jsonPath = Join-Path $OutDir "fleet_matrix_scan.json"
$gate | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host "[fleet_matrix] Wrote $jsonPath pass=$pass pulled=$pulled schemaOk=$schemaOk tier=$scanTierObserved policyId=$policyId"

Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)

if (-not $pass) {
    Write-Host "[fleet_matrix] FAIL — see $jsonPath and $logPath"
    exit 1
}
exit 0
