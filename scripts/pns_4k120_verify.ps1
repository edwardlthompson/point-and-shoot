#Requires -Version 5.1
<#
.SYNOPSIS
    USB gate: 4K @ 120 fps in-app video (H.264 MediaCodec + constrained high-speed).

.DESCRIPTION
    Thin wrapper around pns_mediacodec_hfr_verify.ps1 (-OnlyTest 4K_120fps_MediaCodec).
    Requires ffprobe on PATH for container verification.

.PARAMETER Serial
    ADB serial (optional; uses scripts/pns_adb_device.env when set).

.PARAMETER SkipAssemble
.PARAMETER SkipInstall
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [int]$MaxAttempts = 3,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    if ($OutDir -eq "") { $OutDir = "hfr-runs\verify_4k120_$ts" }
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

    $gateArgs = @{
        OnlyTest = "4K_120fps_MediaCodec"
        RequireFfprobeAv = $true
    }
    if ($Serial -ne "") { $gateArgs.Serial = $Serial }
    if ($SkipAssemble) { $gateArgs.SkipAssemble = $true }
    if ($SkipInstall) { $gateArgs.SkipInstall = $true }
    $attempts = @()
    $finalExit = 1
    for ($attempt = 1; $attempt -le $MaxAttempts; $attempt++) {
        $attemptDir = Join-Path $OutDir ("attempt_{0}" -f $attempt)
        New-Item -ItemType Directory -Force -Path $attemptDir | Out-Null
        $gateArgs.OutDir = $attemptDir
        if ($attempt -gt 1) {
            Write-Host "4K@120 gate retry $attempt/$MaxAttempts (HAL settle)..."
            if ($Serial -ne "") {
                & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null
            } else {
                & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null
            }
            Start-Sleep -Seconds 15
        }
        & "$PSScriptRoot\pns_mediacodec_hfr_verify.ps1" @gateArgs
        $attemptExit = $LASTEXITCODE
        $summaryPath = Join-Path $attemptDir "summary.json"
        $truthClass = "unknown"
        $hfrRoute = "unknown"
        $hfrWarmupAttempt = -1
        $hfrBlockReason = ""
        $pass = $false
        if (Test-Path -LiteralPath $summaryPath) {
            try {
                $rows = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
                $row = @($rows | Where-Object { $_.Test -eq "4K_120fps_MediaCodec" } | Select-Object -First 1)
                if ($row) {
                    $truthClass = if ($row.TruthClass) { [string]$row.TruthClass } else { "unknown" }
                    $hfrRoute = if ($row.HfrRoute) { [string]$row.HfrRoute } else { "unknown" }
                    if ($row.PSObject.Properties.Name -contains "HfrWarmupAttempt") { $hfrWarmupAttempt = [int]$row.HfrWarmupAttempt }
                    if ($row.HfrBlockReason) { $hfrBlockReason = [string]$row.HfrBlockReason }
                    $pass = ($row.Pass -eq $true)
                }
            } catch {
                Write-Warning "Failed parsing $summaryPath : $($_.Exception.Message)"
            }
        }
        $attempts += [ordered]@{
            attempt = $attempt
            pass = $pass
            exitCode = $attemptExit
            truthClass = $truthClass
            hfrRoute = $hfrRoute
            hfrWarmupAttempt = $hfrWarmupAttempt
            hfrBlockReason = $hfrBlockReason
            artifactDir = $attemptDir
        }
        if ($pass -and $truthClass -eq "true_4k120") {
            $finalExit = 0
            break
        }
        if ($truthClass -eq "hs120_sub4k") {
            # Truthful hard stop: retries will not turn sub-4K output into true 4K120.
            $finalExit = 1
            break
        }
        if ($truthClass -ne "blocked_unstable") {
            $finalExit = if ($attemptExit -eq 0) { 1 } else { $attemptExit }
            break
        }
        $finalExit = if ($attemptExit -eq 0) { 1 } else { $attemptExit }
    }
    $summary = [ordered]@{
        schema = "pns.4k120_verify.v2"
        timestampUtc = [DateTime]::UtcNow.ToString("o")
        serial = $Serial
        outDir = $OutDir
        maxAttempts = $MaxAttempts
        attempts = $attempts
        pass = ($finalExit -eq 0)
        finalTruthClass = if (@($attempts).Count -gt 0) { $attempts[-1].truthClass } else { "unknown" }
    }
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutDir "strict_4k120_summary.json") -Encoding utf8
    exit $finalExit
} finally {
    Pop-Location
}
