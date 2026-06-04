#Requires -Version 5.1
<#
.SYNOPSIS
    Measure longest sustained 4K120 recording window.

.DESCRIPTION
    Runs 4K120 MediaCodec verification in stepped durations and records the longest
    duration that still passes strict truth class (`true_4k120`).
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [int]$StartSec = 30,
    [int]$StepSec = 15,
    [int]$MaxSec = 180,
    [switch]$Help,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if ($Help) {
    Write-Host @"
pns_4k120_endurance.ps1 - stepped strict 4K120 endurance gate

  -StartSec      first record duration (default 30)
  -StepSec       increment per run (default 15)
  -MaxSec        max duration tested (default 180)
  -SkipAssemble  skip assembleDebug
  -SkipInstall   skip adb install
"@
    exit 0
}
Push-Location $repoRoot
try {
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    if (-not $OutDir) { $OutDir = "hfr-runs\4k120_endurance_$ts" }
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

    $attempts = @()
    $bestPassSec = 0
    $terminalReason = "unknown"

    for ($dur = $StartSec; $dur -le $MaxSec; $dur += $StepSec) {
        $runOut = Join-Path $OutDir ("run_{0}s" -f $dur)
        New-Item -ItemType Directory -Force -Path $runOut | Out-Null
        Write-Host "[4k120_endurance] duration=${dur}s"
        $gateArgs = @{
            OnlyTest = "4K_120fps_MediaCodec"
            RequireFfprobeAv = $true
            RecordSec = $dur
            OutDir = $runOut
            SkipAssemble = $true
            SkipInstall = $true
        }
        if ($Serial -ne "") { $gateArgs.Serial = $Serial }
        if (-not $SkipAssemble -and $dur -eq $StartSec) { $gateArgs.SkipAssemble = $false }
        if (-not $SkipInstall -and $dur -eq $StartSec) { $gateArgs.SkipInstall = $false }

        & "$PSScriptRoot\pns_mediacodec_hfr_verify.ps1" @gateArgs
        $exitCode = $LASTEXITCODE
        $summaryPath = Join-Path $runOut "summary.json"
        $truthClass = "unknown"
        $pass = $false
        if (Test-Path -LiteralPath $summaryPath) {
            $rows = Get-Content -LiteralPath $summaryPath -Raw | ConvertFrom-Json
            $row = @($rows | Where-Object { $_.Test -eq "4K_120fps_MediaCodec" } | Select-Object -First 1)
            if ($row) {
                $truthClass = if ($row.TruthClass) { [string]$row.TruthClass } else { "unknown" }
                $pass = ($row.Pass -eq $true)
            }
        }
        $attempts += [ordered]@{
            durationSec = $dur
            pass = $pass
            exitCode = $exitCode
            truthClass = $truthClass
            artifactDir = $runOut
        }
        if ($pass -and $truthClass -eq "true_4k120") {
            $bestPassSec = $dur
            continue
        }
        $terminalReason =
            switch ($truthClass) {
                "hs120_sub4k" { "fps_collapse_or_sub4k_path" }
                "blocked_unstable" { "session_disconnect_or_encoder_stall" }
                default { "verification_failed" }
            }
        break
    }

    $report = [ordered]@{
        schema = "pns.4k120_endurance.v1"
        timestampUtc = [DateTime]::UtcNow.ToString("o")
        serial = $Serial
        outDir = $OutDir
        startSec = $StartSec
        stepSec = $StepSec
        maxSec = $MaxSec
        bestPassSec = $bestPassSec
        terminalReason = $terminalReason
        attempts = $attempts
        min30sPass = ($bestPassSec -ge 30)
        pass = ($bestPassSec -ge 30)
    }
    $jsonPath = Join-Path $OutDir "endurance_report.json"
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
    $md = @(
        "# 4K120 endurance report",
        "",
        "- **bestPassSec:** $bestPassSec",
        "- **terminalReason:** $terminalReason",
        "- **min30sPass:** $($report.min30sPass)",
        ""
    )
    foreach ($a in @($attempts)) {
        $md += "- ${($a.durationSec)}s -> pass=$($a.pass) truth=$($a.truthClass) exit=$($a.exitCode)"
    }
    $md | Set-Content -LiteralPath (Join-Path $OutDir "endurance_report.md") -Encoding utf8
    Write-Host "[4k120_endurance] bestPassSec=$bestPassSec terminalReason=$terminalReason report=$jsonPath"
    if (-not $report.pass) { exit 1 }
    exit 0
}
finally {
    Pop-Location
    if ($Serial -ne "") { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null }
    else { & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null }
}
