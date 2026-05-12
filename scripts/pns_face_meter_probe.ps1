<#
.SYNOPSIS
  On-device face / eye / metering (Camera2 static) probe — writes markdown + JSON, optional pull.

.DESCRIPTION
  Installs debug APK (unless -SkipInstall), grants CAMERA (optional; probe uses characteristics only),
  cold-starts MainActivity with `--es pns_screen facemeter --ez pns_autofacemeter true`,
  waits for logcat `PNS.SWEEP_SIGNAL` `FACE_METER_PROBE_DONE`, then pulls the new files from
  `Android/data/dev.pointandshoot/files/` into -OutDir.

.PARAMETER Serial
  adb serial; omit to use scripts/pns_adb_device.env (`PNS_ADB_SERIAL`) or the single default device.

.PARAMETER OutDir
  Host folder for pulled `face_meter_probe_*.{md,json}`. Default: hfr-runs\face_meter_probe_<utc>.

.EXAMPLE
  .\scripts\pns_face_meter_probe.ps1
  .\scripts\pns_face_meter_probe.ps1 -Serial 8bf09993 -SkipInstall
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$pkg = "dev.pointandshoot"
$cmp = "$pkg/.MainActivity"
$ScriptRoot = $PSScriptRoot
$projRoot = Split-Path -Parent $ScriptRoot

$resolveAdbForSession = Join-Path $ScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

function Read-PnsAdbSerialFromEnvFile([string]$root) {
    $envFile = Join-Path $root "pns_adb_device.env"
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
    $fromEnv = Read-PnsAdbSerialFromEnvFile $ScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[face_meter_probe] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
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
    throw "[face_meter_probe] No authorized adb device. Connect USB or Wi‑Fi adb and authorize debugging."
}

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\face_meter_probe_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not $SkipInstall.IsPresent) {
    $apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path -LiteralPath $apk)) {
        Write-Host "`[face_meter_probe] assembleDebug…"
        & (Join-Path $ScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    }
    Write-Host "`[face_meter_probe] adb install -r -t"
    Invoke-Adb @("install", "-r", "-t", $apk)
}

Write-Host "`[face_meter_probe] pm grant CAMERA (best-effort)"
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")

Write-Host "`[face_meter_probe] logcat clear + cold start facemeter"
if ($Serial) { adb -s $Serial logcat -c 2>$null | Out-Null } else { adb logcat -c 2>$null | Out-Null }
Invoke-AdbIgnore @("shell", "am", "force-stop", $pkg)
Start-Sleep -Milliseconds 300
Invoke-Adb @("shell", "am", "start", "-S", "-W", "-n", $cmp, "--es", "pns_screen", "facemeter", "--ez", "pns_autofacemeter", "true")

$deadline = [DateTime]::UtcNow.AddSeconds(45)
$doneLine = $null
while ([DateTime]::UtcNow -lt $deadline) {
    Start-Sleep -Milliseconds 600
    $tail = if ($Serial) {
        & adb -s $Serial logcat -d -s "PNS.SWEEP_SIGNAL:I" 2>&1
    } else {
        adb logcat -d -s "PNS.SWEEP_SIGNAL:I" 2>&1
    }
    $hit = @($tail) | Where-Object { $_ -match "FACE_METER_PROBE_DONE" } | Select-Object -Last 1
    if ($hit) {
        $doneLine = $hit
        break
    }
}
if (-not $doneLine) {
    throw "[face_meter_probe] Timeout: FACE_METER_PROBE_DONE not seen in PNS.SWEEP_SIGNAL logcat."
}
Write-Host "`[face_meter_probe] $doneLine"

$mdPath = $null
$jsonPath = $null
if ($doneLine -match 'mdPath=([^\s]+)') { $mdPath = $Matches[1] }
if ($doneLine -match 'jsonPath=([^\s]+)') { $jsonPath = $Matches[1] }

function Pull-One([string]$devicePath, [string]$localName) {
    if ([string]::IsNullOrWhiteSpace($devicePath)) { return }
    $dest = Join-Path $OutDir $localName
    try {
        Invoke-Adb @("pull", $devicePath, $dest)
        Write-Host "`[face_meter_probe] pulled -> $dest"
    } catch {
        $dirHint = [IO.Path]::GetDirectoryName($devicePath)
        Write-Warning "[face_meter_probe] adb pull failed for $devicePath (try: adb shell ls $dirHint)"
    }
}

if ($mdPath) { Pull-One $mdPath ([IO.Path]::GetFileName($mdPath)) }
if ($jsonPath) { Pull-One $jsonPath ([IO.Path]::GetFileName($jsonPath)) }

$summary = [ordered]@{
    schema      = "pns.face_meter_probe_adb.v1"
    pass        = ($null -ne $mdPath -and $null -ne $jsonPath)
    outDir      = $OutDir
    sweepLine   = $doneLine.Trim()
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}
$summary | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir "face_meter_probe_adb.json") -Encoding utf8
Write-Host "`[face_meter_probe] Done. Artifacts under $OutDir"
