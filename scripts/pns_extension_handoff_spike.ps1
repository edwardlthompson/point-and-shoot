#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 28.2 — isolated HDR extension handoff + preview return proof.

.DESCRIPTION
    Cold-start `pns_screen=extensionhandoff` with `pns_extension_handoff_return_preview=true`.
    PASS when logcat contains:
      - PNS.AdbValidation extensionHandoff ok=true
      - PNS.AdbValidation previewReturnAfterExtensionHandoff ok=true

    SKIP (exit 0) when device reports no extensions (PROBE_OK_NO_EXTENSIONS).

.PARAMETER Serial
    ADB device serial (scripts/pns_adb_device.env when omitted).

.PARAMETER SkipInstall
.PARAMETER SkipAssemble
.PARAMETER HostOnly
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [switch]$HostOnly,
    [int]$WaitSec = 35
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") {
        . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet
    }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if ($Serial -eq "" -and (Test-Path $envFile)) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim().Trim('"') }
        }
    }

    $outDir = Join-Path $repoRoot "hfr-runs\extension_handoff_spike_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    function Write-Step { param([string]$msg) Write-Host "[pns_extension_handoff_spike] $msg" }

    if ($HostOnly) {
        Write-Step "HostOnly: assembleDebug..."
        if (-not $SkipAssemble) {
            & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
            if ($LASTEXITCODE -ne 0) { exit 1 }
        }
        $artifact = [ordered]@{
            schema = "pns.extension_handoff_spike.v1"
            timestamp = (Get-Date -Format "o")
            gateResult = "HOST_PASS"
            hostOnly = $true
            note = "Run without -HostOnly on USB for extension handoff gate"
        }
        $jsonOut = Join-Path $outDir "gate.json"
        $artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut
        Write-Step "HOST_PASS -> $jsonOut"
        exit 0
    }

    if (-not $SkipAssemble) {
        Write-Step "assembleDebug..."
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { Write-Step "BUILD FAILED"; exit 1 }
    }

    $adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }

    if (-not $SkipInstall) {
        $apk = Get-ChildItem "$repoRoot\app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $apk) { Write-Step "APK not found"; exit 1 }
        Write-Step "Installing $($apk.Name)..."
        & adb @adbArgs install -r -t $apk.FullName
        if ($LASTEXITCODE -ne 0) { Write-Step "adb install failed"; exit 1 }
        & adb @adbArgs shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null | Out-Null
    }

    Write-Step "Clearing logcat..."
    & adb @adbArgs logcat -c 2>$null

    Write-Step "Launch extension handoff spike..."
    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null
    & adb @adbArgs shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen extensionhandoff `
        --ez pns_extension_handoff_return_preview true 2>&1 | Out-Null

    Write-Step "Waiting ${WaitSec}s for handoff + preview return..."
    Start-Sleep -Seconds $WaitSec

    $rawLog = & adb @adbArgs logcat -d -s "PNS.AdbValidation" 2>&1
    $logPath = Join-Path $outDir "logcat_adbvalidation.txt"
    $rawLog | Set-Content -Encoding UTF8 $logPath

    $handoffOk = $rawLog | Where-Object { $_ -match "AdbValidation: extensionHandoff ok=true " }
    $previewReturnOk = $rawLog | Where-Object { $_ -match "previewReturnAfterExtensionHandoff ok=true" }
    $noExtensions = $rawLog | Where-Object { $_ -match "AdbValidation: extensionHandoff ok=false.*reason=no_extensions" }

    $gateResult = if ($previewReturnOk -and $handoffOk) {
        "PASS"
    } elseif ($previewReturnOk -and $noExtensions) {
        "PROBE_OK_NO_EXTENSIONS"
    } elseif ($handoffOk -and -not $previewReturnOk) {
        "FAIL"
    } elseif (-not $previewReturnOk) {
        "FAIL"
    } else {
        "FAIL"
    }

    $artifact = [ordered]@{
        schema = "pns.extension_handoff_spike.v1"
        timestamp = (Get-Date -Format "o")
        device = if ($Serial) { $Serial } else { "default" }
        gateResult = $gateResult
        handoffOk = ($null -ne $handoffOk)
        previewReturnOk = ($null -ne $previewReturnOk)
        noExtensions = ($null -ne $noExtensions)
        outDir = $outDir
        logPath = $logPath
    }

    $jsonOut = Join-Path $outDir "gate.json"
    $artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut

    Write-Step "gateResult=$gateResult -> $jsonOut"

    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null

    if ($gateResult -eq "PASS" -or $gateResult -eq "PROBE_OK_NO_EXTENSIONS") {
        exit 0
    }
    exit 1
}
finally {
    Pop-Location
}
