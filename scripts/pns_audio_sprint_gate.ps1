<#
.SYNOPSIS
  Sprint **AS** combined gate — JVM audio/shutter tests + optional USB AS.1/AS.2 scripts.

.PARAMETER HostOnly
  Skip USB gates when no device or for CI.

.EXAMPLE
  .\scripts\pns_audio_sprint_gate.ps1
  .\scripts\pns_audio_sprint_gate.ps1 -HostOnly
#>
param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipAssemble
)

$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent $PSScriptRoot
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\audio_sprint_gate_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

Write-Host "[audio_sprint_gate] unit tests..."
& (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:testDebugUnitTest" "--tests" "dev.pointandshoot.ShutterSoundPackTest" "--tests" "dev.pointandshoot.PnsAudioCaptureSupportTest"
if ($LASTEXITCODE -ne 0) { throw "unit tests failed" }

$usbOk = $false
if (-not $HostOnly) {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) {
        $devices = & adb devices 2>&1 | Out-String
        if ($devices -match "`tdevice") {
            Write-Host "[audio_sprint_gate] USB AS.1 + AS.2..."
            & (Join-Path $PSScriptRoot "pns_audio_quality_test.ps1") -Serial $Serial -SkipAssemble:$SkipAssemble
            if ($LASTEXITCODE -eq 0) {
                & (Join-Path $PSScriptRoot "pns_shutter_sound_test.ps1") -Serial $Serial -SkipAssemble -SkipInstall
                if ($LASTEXITCODE -eq 0) { $usbOk = $true }
            }
        }
    }
}

@{
    hostUnitTests = "pass"
    usbGates = if ($HostOnly) { "skipped" } elseif ($usbOk) { "pass" } else { "skipped_or_fail" }
    outDir = $outDir
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outDir "audio_sprint_gate.json") -Encoding utf8

if (-not $HostOnly -and -not $usbOk) {
    Write-Host "[audio_sprint_gate] FAIL: USB gates not run or failed"
    exit 1
}
Write-Host "[audio_sprint_gate] PASS -> $outDir"
exit 0
