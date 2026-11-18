<#
.SYNOPSIS
  adb pull of **DCIM/PointAndShoot** (canonical) and legacy **DCIM/Point & Shoot** to the host for desktop RAW / HDR validation.

.DESCRIPTION
  Supports the **Sprint 7.3 / Milestone H.1** gate: copy indexed captures off the device, then open samples in desktop tools per **STORAGE_STRATEGY.md**.
  Reads **PNS_ADB_SERIAL** from **scripts/pns_adb_device.env** when **-Serial** is omitted (USB serial from **adb devices**).

.PARAMETER Serial
  Device serial for **adb -s**. Omit to use **pns_adb_device.env** or a single default **device** row.

.PARAMETER OutDir
  Local folder to receive the pull (created if missing). Default: **hfr-runs/pull_dcim_<UTC stamp>/** under the repo root.

.EXAMPLE
  .\scripts\pns_pull_dcim_captures.ps1
  .\scripts\pns_pull_dcim_captures.ps1 -Serial <serial> -OutDir .\pulls\pns_dcim
#>
param(
    [string]$Serial = "",
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

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

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs 2>$null
    }
    else {
        & adb @CmdArgs 2>$null
    }
}

function Get-AdbOnlineSerials {
    $ids = @()
    & adb devices | ForEach-Object {
        if ($_ -match '^(\S+)\s+device\s*$') {
            $ids += $Matches[1]
        }
    }
    return , $ids
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[pull_dcim] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

Write-Host "`[pull_dcim] adb devices:"
& adb devices -l
if ($LASTEXITCODE -ne 0) {
    throw "adb devices -l failed exit=$LASTEXITCODE"
}

$onlineSerials = @(Get-AdbOnlineSerials)
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($onlineSerials.Count -gt 1) {
        throw "Multiple adb devices online ($($onlineSerials -join ', ')). Set PNS_ADB_SERIAL in scripts/pns_adb_device.env or pass -Serial."
    }
}
elseif ($onlineSerials -notcontains $Serial) {
    if ($onlineSerials.Count -eq 1) {
        Write-Host "`[pull_dcim] WARN: serial '$Serial' not online; using $($onlineSerials[0])"
        $Serial = $onlineSerials[0]
    }
    elseif ($onlineSerials.Count -eq 0) {
        throw "No adb device in 'device' state."
    }
    else {
        throw "adb serial '$Serial' not online. Connected: $($onlineSerials -join ', ')"
    }
}

if ([string]::IsNullOrWhiteSpace($OutDir)) {
    $stamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\pull_dcim_$stamp"
}

$OutDir = [System.IO.Path]::GetFullPath($OutDir)
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

# Canonical write folder plus legacy spaced folder (do not migrate files on device).
$dcimPaths = Join-Path $PSScriptRoot "pns_dcim_paths.ps1"
if (Test-Path -LiteralPath $dcimPaths) { . $dcimPaths }
$remoteDirs = if ($script:PnsDcimRemoteDirs) { @($script:PnsDcimRemoteDirs) } else {
    @("/sdcard/DCIM/PointAndShoot", "/sdcard/DCIM/Point & Shoot")
}

$pulledAny = $false
foreach ($remoteDcim in $remoteDirs) {
    Write-Host "`[pull_dcim] pulling $remoteDcim -> $OutDir"
    if ($Serial) {
        & adb -s $Serial pull $remoteDcim $OutDir
    }
    else {
        & adb pull $remoteDcim $OutDir
    }
    if ($LASTEXITCODE -eq 0) {
        $pulledAny = $true
    }
    else {
        Write-Host "`[pull_dcim] WARN: pull of $remoteDcim exited $LASTEXITCODE (folder may be absent)"
    }
}
if (-not $pulledAny) {
    throw "adb pull failed for every P&S DCIM folder"
}

Write-Host "`[pull_dcim] OK. Next (human): open **pns_*.dng** / **.avif** / **.jxl** in desktop tools; on device open the same folder in the OEM gallery (**STORAGE_STRATEGY.md**, **BUILD_PLAN** Sprint 7.3 / Milestone H.1)."
