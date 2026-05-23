#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint PO optimization gate: PO.1 memory + PO.2 battery/thermal with combined report.
#>
param(
    [string]$Serial = "",
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

    $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = "hfr-runs\po_optimization_gate_$stamp"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Write-Host "=== PNS PO Optimization Gate (PO.1 + PO.2) ===" -ForegroundColor Cyan

    $memParams = @{}
    $batParams = @{}
    if ($Serial -ne "") {
        $memParams["Serial"] = $Serial
        $batParams["Serial"] = $Serial
    }
    if ($SkipInstall) {
        $memParams["SkipInstall"] = $true
        $batParams["SkipInstall"] = $true
    }
    if ($SkipAssemble) {
        $memParams["SkipAssemble"] = $true
        $batParams["SkipAssemble"] = $true
    }
    if (-not $SkipAssemble) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }

    $memOk = $false
    $batOk = $false
    $memDir = $null
    $batDir = $null

    Write-Host "`n--- PO.1 memory profiler ---" -ForegroundColor Yellow
    try {
        & "$PSScriptRoot\pns_memory_profiler.ps1" @memParams
        if ($LASTEXITCODE -eq 0) { $memOk = $true }
    } catch {
        Write-Host "PO.1 failed: $_" -ForegroundColor Red
    }
    $memDir = Get-ChildItem "hfr-runs\memory_profiler_*" -Directory -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    Write-Host "`n--- PO.2 battery / thermal ---" -ForegroundColor Yellow
    try {
        & "$PSScriptRoot\pns_battery_life_test.ps1" @batParams
        if ($LASTEXITCODE -eq 0) { $batOk = $true }
    } catch {
        Write-Host "PO.2 failed: $_" -ForegroundColor Red
    }
    $batDir = Get-ChildItem "hfr-runs\battery_life_test_*" -Directory -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    $overallPass = $memOk -and $batOk
    $report = @"
# PO Optimization Gate — $stamp

| Sprint | Script | Result |
|--------|--------|--------|
| PO.1 Memory | pns_memory_profiler.ps1 | $(if ($memOk) { 'PASS' } else { 'FAIL' }) |
| PO.2 Battery | pns_battery_life_test.ps1 | $(if ($batOk) { 'PASS' } else { 'FAIL' }) |

**Overall:** $(if ($overallPass) { 'PASS' } else { 'FAIL' })

## Artifacts
- PO.1: $(if ($memDir) { $memDir.FullName } else { 'n/a' })
- PO.2: $(if ($batDir) { $batDir.FullName } else { 'n/a' })

## Metrics (automated)
- Preview session memory profiler lines (`PNS.MemoryProfiler`)
- Bitmap leak check (`PNS.Bitmap leakCheck`)
- Adaptive FPS cap (`PNS.PowerThermal adaptiveFpsCap`)
- Lifecycle pause/resume (`longRunningPaused`)

Human fleet benchmarks (15% battery improvement, 60-minute thermal soak) are out of scope for this gate.
"@
    $report | Set-Content "$outDir\report.md" -Encoding UTF8

    $gate = [ordered]@{
        timestamp   = $stamp
        passed      = $overallPass
        po1Memory   = $memOk
        po2Battery  = $batOk
        memoryDir   = if ($memDir) { $memDir.Name } else { $null }
        batteryDir  = if ($batDir) { $batDir.Name } else { $null }
        artifactDir = $outDir
    }
    $gate | ConvertTo-Json | Set-Content "$outDir\gate.json" -Encoding UTF8

    if ($overallPass) {
        Write-Host "`nPASS — PO optimization gate" -ForegroundColor Green
        Write-Host "Report: $outDir\report.md"
    } else {
        Write-Host "`nFAIL — PO optimization gate" -ForegroundColor Red
        exit 1
    }
} finally {
    Pop-Location
}
