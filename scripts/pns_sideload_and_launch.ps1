<#
.SYNOPSIS
  Optionally build debug APK, adb install -r, grant CAMERA, launch Point & Shoot.

.DESCRIPTION
  Reads **PNS_ADB_SERIAL** from **scripts/pns_adb_device.env** when **-Serial** is omitted.
  For Wi‑Fi debugging (**host:port**), runs **adb connect** automatically (same as **pns_adb_preview_validate.ps1**).

.PARAMETER Serial
  Device serial for **adb -s**. Omit to use **pns_adb_device.env** or a single default device.

.PARAMETER Build
  Run **gradlew :app:assembleDebug** before install (default: **true**).

.PARAMETER SkipBuild
  Skip Gradle; install existing **app/build/outputs/apk/debug/app-debug.apk**.

.PARAMETER LaunchScreen
  **--es pns_screen** value (**preview** opens **PreviewEngineScreen**).

.PARAMETER ColdStart
  Pass **-S** to **am start** (stop app process before launch).

.PARAMETER ExtraArgs
  Additional tokens appended to **am start** (e.g. `--ei pns_preview_self_timer_sec 0`).

.EXAMPLE
  .\scripts\pns_sideload_and_launch.ps1
  .\scripts\pns_sideload_and_launch.ps1 -SkipBuild
  .\scripts\pns_sideload_and_launch.ps1 -Serial 8bf09993 -Build:$false
#>
param(
    [string]$Serial = "",
    [switch]$Build,
    [switch]$SkipBuild,
    [string]$LaunchScreen = "preview",
    [switch]$ColdStart,
    [string[]]$ExtraArgs = @()
)

$ErrorActionPreference = "Stop"

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

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

function Get-AdbOnlineSerials {
    $ids = @()
    & adb devices | ForEach-Object {
        if ($_ -match '^(\S+)\s+device\s*$') {
            $ids += $Matches[1]
        }
    }
    # Unary comma: PowerShell unwraps single-element arrays from `return $ids`, which makes
    # `$onlineSerials[0]` index the first *character* of the serial string  -  breaking adb -s.
    return , $ids
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[sideload_launch] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

if ($Serial -match '^\d+\.\d+\.\d+\.\d+:\d+$') {
    Write-Host "`[sideload_launch] adb connect $Serial (TCP/IP)"
    Invoke-AdbIgnore @("connect", $Serial)
}

Write-Host "`[sideload_launch] adb devices:"
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
        Write-Host "`[sideload_launch] WARN: serial '$Serial' not online; using $($onlineSerials[0])"
        $Serial = $onlineSerials[0]
    }
    elseif ($onlineSerials.Count -eq 0) {
        throw "No adb device in 'device' state."
    }
    else {
        throw "adb serial '$Serial' not online. Connected: $($onlineSerials -join ', ')"
    }
}

$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"
$activity = "dev.pointandshoot/.MainActivity"

$doBuild = $false
if ($SkipBuild) {
    $doBuild = $false
}
elseif ($PSBoundParameters.ContainsKey("Build")) {
    $doBuild = [bool]$Build
}
else {
    $doBuild = $true
}

if ($doBuild) {
    $gradlew = Join-Path $projRoot "gradlew.bat"
    if (-not (Test-Path -LiteralPath $gradlew)) {
        throw "gradlew.bat not found at $gradlew"
    }
    Write-Host "`[sideload_launch] gradlew :app:assembleDebug --no-daemon"
    Push-Location $projRoot
    try {
        & $gradlew ":app:assembleDebug" --no-daemon
        if ($LASTEXITCODE -ne 0) { throw "Gradle assembleDebug failed exit=$LASTEXITCODE" }
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk; run assembleDebug or omit -SkipBuild after building once."
}

Write-Host "`[sideload_launch] adb install -r -t $apk"
try {
    adb wait-for-device | Out-Null
}
catch {}

if ($Serial) {
    $installOut = & adb -s $Serial install -r -t $apk 2>&1
}
else {
    $installOut = & adb install -r -t $apk 2>&1
}
$installOut | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) {
    throw "adb install failed exit=$LASTEXITCODE"
}

Write-Host "`[sideload_launch] pm grant CAMERA (best-effort)"
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_IMAGES")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_VIDEO")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.POST_NOTIFICATIONS")

$startArgs = @("shell", "am", "start", "-W", "-n", $activity)
if ($ColdStart) {
    $startArgs += "-S"
}
if (-not [string]::IsNullOrWhiteSpace($LaunchScreen)) {
    $startArgs += "--es", "pns_screen", $LaunchScreen
}
if ($ExtraArgs -and $ExtraArgs.Count -gt 0) {
    $startArgs += $ExtraArgs
}

Write-Host "`[sideload_launch] $($startArgs -join ' ')"
if ($Serial) {
    & adb -s $Serial @startArgs
}
else {
    & adb @startArgs
}
if ($LASTEXITCODE -ne 0) {
    throw "am start failed exit=$LASTEXITCODE"
}

Write-Host "`[sideload_launch] OK"
