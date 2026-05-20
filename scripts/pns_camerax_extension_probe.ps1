#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13V.18 — CameraX OEM ISP extension probe gate.

.DESCRIPTION
    Installs + launches the app (optional), waits for PNS.CamXExtProbe logcat lines,
    and writes probe.json. Empty extension list → PROBE_OK_NO_EXTENSIONS (LineageOS).
    FAIL only when the probe never logged.

    -HostOnly: JVM CameraXExtensionProbeTest + assembleDebug (no ADB).

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
    [int]$WaitSec = 20
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

    $outDir = Join-Path $repoRoot "hfr-runs\camerax_ext_probe_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    function Write-Step { param([string]$msg) Write-Host "[pns_camerax_ext_probe] $msg" }

    if ($HostOnly) {
        Write-Step "HostOnly: unit tests + assembleDebug..."
        & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest --tests "dev.pointandshoot.CameraXExtensionProbeTest"
        if ($LASTEXITCODE -ne 0) { exit 1 }
        if (-not $SkipAssemble) {
            & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
            if ($LASTEXITCODE -ne 0) { exit 1 }
        }
        $artifact = [ordered]@{
            schema = "pns.camerax_ext_probe.v1"
            timestamp = (Get-Date -Format "o")
            gateResult = "HOST_PASS"
            probeComplete = $false
            hostOnly = $true
            note = "Run without -HostOnly on USB for PNS.CamXExtProbe log gate"
        }
        $jsonOut = Join-Path $outDir "probe.json"
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
        if (-not $apk) { Write-Step "APK not found — run assemble or drop -SkipInstall"; exit 1 }
        Write-Step "Installing $($apk.Name)..."
        & adb @adbArgs install -r -t $apk.FullName
        if ($LASTEXITCODE -ne 0) { Write-Step "adb install failed"; exit 1 }
    }

    Write-Step "Clearing logcat..."
    & adb @adbArgs logcat -c 2>$null

    Write-Step "Launching app..."
    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null
    & adb @adbArgs shell am start -n "dev.pointandshoot/.MainActivity" --es pns_screen preview 2>&1 | Out-Null

    Write-Step "Waiting ${WaitSec}s for PNS.CamXExtProbe lines..."
    Start-Sleep -Seconds $WaitSec

    $rawLog = & adb @adbArgs logcat -d -s "PNS.CamXExtProbe" 2>&1
    $logLines = @($rawLog | Where-Object { $_ -match "PNS\.CamXExtProbe" })

    $probeComplete = $logLines | Where-Object { $_ -match "extensionProbeComplete" }
    $extensionAvail = $logLines | Where-Object { $_ -match "extensionAvail=" }

    $hasAny = $false
    $modes = @{}
    foreach ($line in $extensionAvail) {
        if ($line -match "extensionAvail=none") { break }
        if ($line -match "extensionAvail=(.+)$") {
            $hasAny = $true
            $summary = $Matches[1]
            $camMatches = [regex]::Matches($summary, "cam(\w+)=\[([^\]]*)\]")
            foreach ($m in $camMatches) {
                $camId = $m.Groups[1].Value
                $modeList = $m.Groups[2].Value -split "," | Where-Object { $_ }
                $modes[$camId] = $modeList
            }
        }
    }

    $gateResult = if (-not $probeComplete) {
        "FAIL"
    } elseif ($hasAny) {
        "PASS"
    } else {
        "PROBE_OK_NO_EXTENSIONS"
    }

    $artifact = [ordered]@{
        schema = "pns.camerax_ext_probe.v1"
        timestamp = (Get-Date -Format "o")
        device = if ($Serial) { $Serial } else { "default" }
        gateResult = $gateResult
        probeComplete = ($null -ne $probeComplete)
        hasAnyExtension = $hasAny
        availableByCamera = $modes
        logLines = $logLines
    }

    $jsonOut = Join-Path $outDir "probe.json"
    $artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut

    Write-Step "Stopping app (battery rule)..."
    & adb @adbArgs shell am force-stop dev.pointandshoot 2>&1 | Out-Null

    Write-Step "GATE: $gateResult"
    if ($gateResult -eq "FAIL") {
        Write-Step "FAIL: PNS.CamXExtProbe never logged."
    } elseif ($gateResult -eq "PROBE_OK_NO_EXTENSIONS") {
        Write-Step "PROBE_OK: No OEM extensions (expected on LineageOS)."
    } else {
        Write-Step "PASS: OEM extensions available."
    }
    Write-Step "Artifact: $jsonOut"

    if ($gateResult -eq "FAIL") { exit 1 }
    exit 0
} finally {
    Pop-Location
}
