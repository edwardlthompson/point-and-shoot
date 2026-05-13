# BUILD_PLAN Milestone 6 - one-shot host + device gate (USB adb).
# - Builds debug APK unless -SkipGradle
# - Runs scripts/pns_adb_preview_validate.ps1 -Milestone6Pack (reads scripts/pns_adb_device.env)
# - On success: runs scripts/pns_probe_append_section5.ps1 (-PassOnly) to append PROBE_BUILD_PLAN.md §5 row
#   unless -SkipAppendSection5. Output defaults to hfr-runs\adb_preview_validate_milestone6_latest\.
#
# Prerequisites: adb on PATH; optional scripts/pns_adb_device.env with PNS_ADB_SERIAL (USB serial from adb devices).
# Pass -Serial <serial> to pick a device when more than one is connected or to override the env file.
# Root on device is not required for this gate (optional pulls may use su in future scripts).

param(
    [string]$Serial = "",
    [switch]$SkipGradle,
    [switch]$SkipInstall,
    # Stable folder so milestone6_gate.json path is predictable (also enables §5 append).
    [string]$PackOutDir = "",
    [switch]$SkipAppendSection5
)

$ErrorActionPreference = "Stop"

$projRoot = Split-Path -Parent $PSScriptRoot
$envExample = Join-Path $PSScriptRoot "pns_adb_device.env.example"
$envLocal = Join-Path $PSScriptRoot "pns_adb_device.env"

if (-not (Test-Path -LiteralPath $envLocal)) {
    Write-Warning "Missing $envLocal - copy from $envExample and set PNS_ADB_SERIAL."
}

if (-not $SkipGradle.IsPresent) {
    Write-Host "`[milestone6_gate] pns_gradlew.ps1 :app:assembleDebug"
    $gradlewHelper = Join-Path $PSScriptRoot "pns_gradlew.ps1"
    & $gradlewHelper ":app:assembleDebug" "--no-daemon"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
}

$validate = Join-Path $PSScriptRoot "pns_adb_preview_validate.ps1"
# Use hashtable splatting  -  an array starting with "-Milestone6Pack" binds positionally to
# [string]$Serial (first param of pns_adb_preview_validate.ps1), breaking adb -s.
$packDir =
    if (-not [string]::IsNullOrWhiteSpace($PackOutDir)) {
        $PackOutDir
    }
    else {
        $defaultPackDir = Join-Path $projRoot "hfr-runs\adb_preview_validate_milestone6_latest"
        New-Item -ItemType Directory -Force -Path $defaultPackDir | Out-Null
        Write-Host "`[milestone6_gate] PackOutDir (default) $defaultPackDir"
        $defaultPackDir
    }
$m6Invoke = @{
    Milestone6Pack = $true
    OutDir         = $packDir
}
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $m6Invoke.Serial = $Serial
    Write-Host "`[milestone6_gate] Serial=$Serial (passed through to adb preview validate; env PNS_ADB_SERIAL ignored)"
}
if ($SkipInstall.IsPresent) {
    $m6Invoke.SkipInstall = $true
}
Write-Host "`[milestone6_gate] Invoke $validate -Milestone6Pack -OutDir $packDir"
& $validate @m6Invoke
if (-not $?) {
    throw "pns_adb_preview_validate.ps1 failed"
}

$gateJsonPath = Join-Path $packDir "milestone6_gate.json"

if (-not $SkipAppendSection5.IsPresent -and (Test-Path -LiteralPath $gateJsonPath)) {
    $append = Join-Path $PSScriptRoot "pns_probe_append_section5.ps1"
    Write-Host "`[milestone6_gate] append PROBE_BUILD_PLAN section 5 <- $gateJsonPath"
    & $append -GateJson $gateJsonPath -PassOnly
}
elseif (-not $SkipAppendSection5.IsPresent) {
    Write-Warning "[milestone6_gate] milestone6_gate.json missing at $gateJsonPath - skip §5 append"
}

Write-Host "`[milestone6_gate] DONE"
