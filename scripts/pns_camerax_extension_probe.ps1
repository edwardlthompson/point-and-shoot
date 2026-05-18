<#
.SYNOPSIS
    Sprint 13.18 gate: verify CameraX OEM ISP extension probe ran and log results.

.DESCRIPTION
    Installs + launches the app (optional), waits for PNS.CamXExtProbe logcat lines,
    and writes a probe.json artifact. This is a NON-BLOCKING gate: an empty extension
    list is expected on LineageOS/AOSP and results in GATE: PROBE_OK (not GATE: FAIL).
    GATE: FAIL only fires when the probe never logged (app crashed before probe ran).

    Output artifact: hfr-runs/camerax_ext_probe_<timestamp>/probe.json

.PARAMETER Serial
    ADB device serial. Falls back to PNS_ADB_SERIAL env var, then first device.

.PARAMETER SkipInstall
    Skip assembleDebug + adb install. Use when the APK is already sideloaded.

.PARAMETER WaitSec
    Seconds to wait for probe logcat lines after launch (default: 20).

.EXAMPLE
    .\scripts\pns_camerax_extension_probe.ps1
    .\scripts\pns_camerax_extension_probe.ps1 -SkipInstall -WaitSec 30
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [int]$WaitSec = 20
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path $PSScriptRoot -Parent
$outDir = Join-Path $repoRoot "hfr-runs\camerax_ext_probe_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-Step { param([string]$msg) Write-Host "[pns_camerax_ext_probe] $msg" }

# Resolve ADB serial
if (-not $Serial) { $Serial = $env:PNS_ADB_SERIAL }
$adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }

# --- Step 1: optional build + install ---
if (-not $SkipInstall) {
    Write-Step "assembleDebug..."
    $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
    & "$repoRoot\gradlew.bat" :app:assembleDebug --no-daemon
    if ($LASTEXITCODE -ne 0) { Write-Step "BUILD FAILED"; exit 1 }

    $apk = Get-ChildItem "$repoRoot\app\build\outputs\apk\debug\*.apk" | Select-Object -First 1
    if (-not $apk) { Write-Step "APK not found"; exit 1 }
    Write-Step "Installing $($apk.Name)..."
    & adb @adbArgs install -r -t $apk.FullName
    if ($LASTEXITCODE -ne 0) { Write-Step "adb install failed"; exit 1 }
}

# --- Step 2: clear logcat + launch ---
Write-Step "Clearing logcat..."
& adb @adbArgs logcat -c 2>$null

Write-Step "Launching app..."
& adb @adbArgs shell am start -n "dev.pointandshoot/.MainActivity" --es pns_screen preview 2>&1 | Out-Null

# --- Step 3: collect logcat for WaitSec ---
Write-Step "Waiting ${WaitSec}s for PNS.CamXExtProbe lines..."
Start-Sleep -Seconds $WaitSec

$rawLog = & adb @adbArgs logcat -d -s "PNS.CamXExtProbe" 2>&1
$logLines = $rawLog | Where-Object { $_ -match "PNS\.CamXExtProbe" }

# --- Step 4: parse results ---
$probeComplete = $logLines | Where-Object { $_ -match "extensionProbeComplete" }
$extensionAvail = $logLines | Where-Object { $_ -match "extensionAvail=" }

$hasAny = $false
$modes = @{}
foreach ($line in $extensionAvail) {
    if ($line -match "extensionAvail=none") { break }
    if ($line -match "extensionAvail=(.+)$") {
        $hasAny = $true
        # Parse cam0=[NIGHT,BOKEH] style
        $summary = $Matches[1]
        $camMatches = [regex]::Matches($summary, "cam(\w+)=\[([^\]]*)\]")
        foreach ($m in $camMatches) {
            $camId = $m.Groups[1].Value
            $modeList = $m.Groups[2].Value -split "," | Where-Object { $_ }
            $modes[$camId] = $modeList
        }
    }
}

# --- Step 5: determine gate result ---
$gateResult = if (-not $probeComplete) {
    "FAIL"
} elseif ($hasAny) {
    "PASS"
} else {
    "PROBE_OK_NO_EXTENSIONS"
}

# --- Step 6: write artifact ---
$artifact = [ordered]@{
    schema        = "pns.camerax_ext_probe.v1"
    timestamp     = (Get-Date -Format "o")
    device        = if ($Serial) { $Serial } else { "default" }
    gateResult    = $gateResult
    probeComplete = ($null -ne $probeComplete)
    hasAnyExtension = $hasAny
    availableByCamera = $modes
    logLines      = @($logLines)
}

$jsonOut = Join-Path $outDir "probe.json"
$artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut

# --- Step 7: force-stop app ---
Write-Step "Stopping app (battery rule)..."
& adb @adbArgs shell am force-stop dev.pointandshoot 2>&1 | Out-Null

# --- Step 8: report ---
Write-Step "GATE: $gateResult"
if ($gateResult -eq "FAIL") {
    Write-Step "FAIL: PNS.CamXExtProbe never logged. App may have crashed before probe ran."
    Write-Step "  Check: adb logcat -d -s AndroidRuntime | Select-String 'FATAL'"
} elseif ($gateResult -eq "PROBE_OK_NO_EXTENSIONS") {
    Write-Step "PROBE_OK: Probe ran successfully. No OEM extensions on this device/ROM (expected on LineageOS)."
    Write-Step "  Night/Bokeh dial modes will be hidden in the UI on this device."
} else {
    Write-Step "PASS: OEM extensions available:"
    foreach ($cam in $modes.Keys) {
        Write-Step "  camera $cam: $($modes[$cam] -join ', ')"
    }
}

Write-Step "Artifact: $jsonOut"

if ($gateResult -eq "FAIL") { exit 1 }
exit 0
