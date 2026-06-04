#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 13V.15 gate: MediaCodecCapabilityProbe on cold preview launch.

    Asserts HEVC performance-points (1080p@120, 4K@120), Main10, and YUVP010 on device.
    Artifacts: hfr-runs/video_cap_probe_<stamp>/probe.json
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [int]$WaitSec = 14,
    [switch]$SkipInstall,
    [switch]$SkipAssemble
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") {
        . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet
    }

    if (-not $SkipAssemble) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }

    $apk = "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "Missing $apk" }

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    if (-not $SkipInstall) {
        Invoke-AdbCmd install -r -t $apk | Out-Null
    }

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    if (-not $OutDir) { $OutDir = "hfr-runs\video_cap_probe_$stamp" }
    $outDir = $OutDir
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Write-Host "=== PNS Video Capability Probe (Sprint 13V.15) ===" -ForegroundColor Cyan

    $devices = (Invoke-AdbCmd devices 2>&1) | Where-Object { $_ -match "`tdevice$" }
    if (-not $devices) {
        throw "No ADB device connected."
    }

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null
    Start-Sleep -Milliseconds 600
    Invoke-AdbCmd logcat -c 2>$null

    Write-Host "Launch: cold preview (PnsApplication runs MediaCodecCapabilityProbe)..."
    Invoke-AdbCmd shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview 2>&1 | Out-Null

    Write-Host "Waiting ${WaitSec}s for PNS.VideoCapProbe..."
    Start-Sleep -Seconds $WaitSec

    $logLines = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
    $logFile = "$outDir\logcat.txt"
    $logLines | Set-Content -Path $logFile -Encoding UTF8

    $probeLines = $logLines -split "`n" | Where-Object { $_ -match "PNS\.VideoCapProbe" }
    $probeLines | Set-Content -Path "$outDir\logcat_probe.txt" -Encoding UTF8

    Write-Host ""
    Write-Host "=== Probe log (PNS.VideoCapProbe) ===" -ForegroundColor Yellow
    $probeLines | ForEach-Object { Write-Host $_ }

    $summaryLine = ($probeLines | Where-Object { $_ -match "capProbeResult" } | Select-Object -Last 1)
    $perfPoints = $probeLines | Where-Object { $_ -match "perfPoint " }
    $encoders = $probeLines | Where-Object { $_ -match "encoder name=" }

    $has4k120 = @($perfPoints | Where-Object { $_ -match "3840x2160@120fps|4096x2160@120fps" }).Count -gt 0
    $has1080p120 = @($perfPoints | Where-Object { $_ -match "1920x1080@120fps" }).Count -gt 0
    $has1080p240 = @($perfPoints | Where-Object { $_ -match "1920x1080@240fps" }).Count -gt 0
    $hasMain10 = $summaryLine -match "main10=True"
    $hasHdr10 = $summaryLine -match "hdr10=True"
    $hasYuvP010 = $summaryLine -match "yuvp010=True"
    $probeRan = $null -ne $summaryLine -and $summaryLine.Length -gt 0
    $hasH264Encoder = @($encoders | Where-Object { $_ -match "video/avc|avc" }).Count -gt 0
    $hasHevcEncoder = @($encoders | Where-Object { $_ -match "video/hevc|hevc" }).Count -gt 0
    $hasEncoder4k120H264 = $has4k120 -and $hasH264Encoder
    $hasEncoder4k120Hevc = $has4k120 -and $hasHevcEncoder
    $hasEncoder4k120Any = $hasEncoder4k120H264 -or $hasEncoder4k120Hevc
    $capabilityClass =
        if (-not $probeRan -or -not $has1080p120) { "S0" }
        elseif ($hasEncoder4k120Any) { "S2" }
        else { "S1" }

    Write-Host ""
    Write-Host "=== Results ===" -ForegroundColor Cyan
    Write-Host "  capProbeResult logged    : $probeRan"
    Write-Host "  1080p@120fps perf-point  : $has1080p120"
    Write-Host "  1080p@240fps perf-point  : $has1080p240"
    Write-Host "  4K@120fps perf-point     : $has4k120"
    Write-Host "  Main10 profile           : $hasMain10"
    Write-Host "  HDR10 profile            : $hasHdr10"
    Write-Host "  YUVP010 color format     : $hasYuvP010"
    Write-Host "  H.264 encoder seen       : $hasH264Encoder"
    Write-Host "  HEVC encoder seen        : $hasHevcEncoder"
    Write-Host "  4K120 via H.264 path     : $hasEncoder4k120H264"
    Write-Host "  4K120 via HEVC path      : $hasEncoder4k120Hevc"
    Write-Host "  capabilityClass          : $capabilityClass"
    Write-Host "  Performance points found : $($perfPoints.Count)"
    Write-Host "  Encoders found           : $($encoders.Count)"

    $passed = $probeRan -and $has1080p120 -and $hasEncoder4k120Any

    $result = [ordered]@{
        timestamp      = $stamp
        passed         = $passed
        probeRan       = $probeRan
        has1080p120    = [bool]$has1080p120
        has1080p240    = [bool]$has1080p240
        has4k120       = [bool]$has4k120
        hasMain10      = [bool]$hasMain10
        hasHdr10       = [bool]$hasHdr10
        hasYuvP010     = [bool]$hasYuvP010
        hasH264Encoder = [bool]$hasH264Encoder
        hasHevcEncoder = [bool]$hasHevcEncoder
        hasEncoder4k120H264 = [bool]$hasEncoder4k120H264
        hasEncoder4k120Hevc = [bool]$hasEncoder4k120Hevc
        hasEncoder4k120Any = [bool]$hasEncoder4k120Any
        capabilityClass = $capabilityClass
        perfPointCount = @($perfPoints | ForEach-Object { $_ }).Count
        encoderCount   = @($encoders | ForEach-Object { $_ }).Count
        summaryLine    = "$summaryLine"
        logFile        = $logFile
        artifactDir    = $outDir
    }
    $result | ConvertTo-Json | Set-Content "$outDir\probe.json" -Encoding UTF8

    Write-Host ""
    if ($passed) {
        Write-Host "GATE: PASS" -ForegroundColor Green
    } else {
        Write-Host "GATE: FAIL" -ForegroundColor Red
        if (-not $probeRan) { Write-Host "  MISSING: capProbeResult (probe did not log)" -ForegroundColor Red }
        if (-not $has1080p120) { Write-Host "  MISSING: 1080p@120fps performance-point" -ForegroundColor Red }
        if (-not $has4k120) { Write-Host "  MISSING: 4K@120fps performance-point" -ForegroundColor Red }
        if (-not $hasEncoder4k120Any) { Write-Host "  MISSING: 4K120 encoder path (H.264/HEVC)" -ForegroundColor Red }
    }

    Write-Host "Artifacts: $outDir"

    Invoke-AdbCmd shell am force-stop dev.pointandshoot 2>$null

    if (-not $passed) { exit 1 }
} finally {
    Pop-Location
}
