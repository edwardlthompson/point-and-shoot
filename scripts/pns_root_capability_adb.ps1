# BUILD_PLAN Sprint 7.5 / Milestone 7  -  ADB transport root probe (no app UI).
#
# - Optional `adb root` (best-effort; userdebug / eng builds; may restart adbd).
# - Captures `adb shell id` after reconnect (expect uid=0 when adbd is root).
# - Best-effort `adb shell su -c id` (Magisk/KernelSU style; often fails when only `adb root` is used).
# - Writes root_capability_adb.json under -OutDir for §5 append (pns_probe_append_section5.ps1).
#
# Serial: -Serial or scripts/pns_adb_device.env (PNS_ADB_SERIAL, USB serial from adb devices).

param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipAdbRoot
)

$ErrorActionPreference = "Stop"

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\root_capability_adb_$utc"
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

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[root_capability_adb] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
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
    $lines = @(adb devices 2>&1)
    foreach ($line in $lines) {
        if ($line -match '\tdevice$') { return $true }
    }
    return $false
}

if (-not (Test-AdbAuthorizedDevice)) {
    Write-Warning "[root_capability_adb] No authorized adb device."
    $stub = [ordered]@{
        schema              = "pns.root_capability_adb.v1"
        adbConnected        = $false
        pass                = $false
        skippedReason       = "no_authorized_device"
        adbRootAttempted    = $false
        adbShellId          = $null
        suInteractiveId     = $null
        timestampUtc        = [DateTime]::UtcNow.ToString("o")
        outDir              = $OutDir
        serial              = $(if ($Serial) { $Serial } else { "default" })
    }
    $stub | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "root_capability_adb.json") -Encoding utf8
    exit 0
}

if (-not $SkipAdbRoot.IsPresent) {
    Write-Host "`[root_capability_adb] adb root (best-effort)"
    if ($Serial) { adb -s $Serial root 2>$null | Out-Null } else { adb root 2>$null | Out-Null }
    Start-Sleep -Seconds 2
}

$adbRootAttempted = -not $SkipAdbRoot.IsPresent

function Capture-ShellText([string]$shellCmd) {
    $prev = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    try {
        if ($Serial) {
            return (@(& adb -s $Serial shell $shellCmd 2>&1) -join "`n").Trim()
        }
        return (@(adb shell $shellCmd 2>&1) -join "`n").Trim()
    }
    finally {
        $ErrorActionPreference = $prev
    }
}

$adbIdLine = Capture-ShellText "id"
$suIdLine = Capture-ShellText "su -c id"
$serialNo = Capture-ShellText "getprop ro.serialno"
$model = Capture-ShellText "getprop ro.product.model"

$uid0 = $adbIdLine -match 'uid=0\(root\)'
$suOk = $suIdLine -match 'uid=0\(root\)'

# Pass when adbd-side root works (typical USB `adb root`). Interactive su is optional extra.
$pass = $uid0

$obj = [ordered]@{
    schema               = "pns.root_capability_adb.v1"
    adbConnected         = $true
    pass                 = $pass
    adbRootAttempted     = $adbRootAttempted
    adbShellId           = $adbIdLine
    suInteractiveId      = $suIdLine
    roSerialno           = $serialNo
    roProductModel       = $model
    uid0FromAdbShell     = $uid0
    uid0FromSuCommand    = $suOk
    timestampUtc         = [DateTime]::UtcNow.ToString("o")
    outDir               = $OutDir
    serial               = $(if ($Serial) { $Serial } else { "default" })
}

$jsonPath = Join-Path $OutDir "root_capability_adb.json"
$obj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $jsonPath -Encoding utf8
Write-Host "`[root_capability_adb] Wrote $jsonPath pass=$pass"
Write-Host "`[root_capability_adb] adb shell id -> $adbIdLine"
if (-not $pass) {
    Write-Host "`[root_capability_adb] FAIL: expected uid=0(root) in adb shell id (try userdebug/eng build or adb root)"
    exit 1
}
exit 0
