<#
.SYNOPSIS
  Sprint 13.8d — Standard / ZSL / HDR still captures; timing + openability summary.

  Requires USB device. Use pns_preview_still_mode=standard|zsl|hdr per run, or -Mode all.

.EXAMPLE
  .\scripts\pns_still_mode_benchmark.ps1 -Serial 8bf09993 -Mode all -Repeats 1
  .\scripts\pns_still_mode_benchmark.ps1 -Mode standard -Repeats 3
#>
param(
    [string]$Serial = "",
    [ValidateSet("standard", "zsl", "hdr", "all")]
    [string]$Mode = "standard",
    [int]$Repeats = 1,
    [switch]$SkipBuild,
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$benchRoot =
    if ($OutDir) { $OutDir } else { Join-Path $projRoot "hfr-runs\still_mode_bench_$ts" }
New-Item -ItemType Directory -Force -Path $benchRoot | Out-Null

function Parse-StillTimingLine([string]$Line) {
    $o = [ordered]@{ raw = $Line }
    if ($Line -match 'stillMode=(\w+)') { $o.stillMode = $Matches[1] }
    if ($Line -match 't_request_to_raw_ms=(-?\d+)') { $o.t_request_to_raw_ms = [int]$Matches[1] }
    if ($Line -match 't_raw_to_dng_ms=(-?\d+)') { $o.t_raw_to_dng_ms = [int]$Matches[1] }
    if ($Line -match 't_request_to_dng_ms=(-?\d+)') { $o.t_request_to_dng_ms = [int]$Matches[1] }
    if ($Line -match 'hdr_frames=(\d+)') { $o.hdr_frames = [int]$Matches[1] }
    return $o
}

function Parse-RunLogcat([string]$RunDir) {
    $logPath = Join-Path $RunDir "M23_wide_logcat.txt"
    $reportPath = Join-Path $RunDir "capture_report.txt"
    $chunks = @()
    if (Test-Path $logPath) { $chunks += Get-Content -LiteralPath $logPath -Raw }
    if (Test-Path $reportPath) { $chunks += Get-Content -LiteralPath $reportPath -Raw }
    $text = ($chunks -join "`n")
    $timing = @()
    if ($text) {
        $timing = [regex]::Matches($text, '(?m)^.*still timing.*$') | ForEach-Object {
            Parse-StillTimingLine $_.Value.Trim()
        }
    }
    $openPass = ($text -match 'DNG DESKTOP OPEN GATE:\s*PASS') -or ($text -match 'CAPTURE \+ OPENABILITY:\s*PASS')
    $zslHit = $text -match 'zsl still ring hit'
    $zslMiss = $text -match 'zsl still ring miss'
    $hdrFrames = $null
    if ($text -match 'captureHdrStill\s+\S+\s+ok=true\s+frames=(\d+)') {
        $hdrFrames = [int]$Matches[1]
    }
    return [ordered]@{
        timing = $timing
        openabilityPass = [bool]$openPass
        zslRingHit = [bool]$zslHit
        zslRingMiss = [bool]$zslMiss
        hdrFrames = $hdrFrames
    }
}

$modes = if ($Mode -eq "all") { @("standard", "zsl", "hdr") } else { @($Mode) }
$results = [ordered]@{
    schema = "still_mode_bench.v2"
    timestampUtc = $ts
    benchRoot = $benchRoot
    modes = $modes
    repeats = $Repeats
    byMode = [ordered]@{}
}

foreach ($m in $modes) {
    $modeRuns = @()
    for ($i = 1; $i -le $Repeats; $i++) {
        Write-Host "[bench] mode=$m run $i/$Repeats" -ForegroundColor Cyan
        $runDir = Join-Path $benchRoot "$m\run_$i"
        $cap = @{
            Serial = $Serial
            PreviewDial = "A"
            WaitSec = 52
            OutDir = $runDir
            ExtraAmArgs = @("--es", "pns_preview_still_mode", $m, "--ei", "pns_preview_raw_count", "1")
        }
        if ($SkipBuild) { $cap["SkipBuild"] = $true; $cap["SkipInstall"] = $true }
        & (Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1") @cap
        $exitCode = $LASTEXITCODE
        $parsed = Parse-RunLogcat $runDir
        $modeRuns += [ordered]@{
            index = $i
            exitCode = $exitCode
            runDir = $runDir
            capturePass = ($exitCode -eq 0)
            parsed = $parsed
        }
    }
    $results.byMode[$m] = $modeRuns
}

$reportLines = [System.Collections.Generic.List[string]]::new()
$reportLines.Add("# Still mode benchmark $ts")
$reportLines.Add("")
$reportLines.Add("| Mode | Run | Capture | Openability | Timing (M23 wide) | ZSL/HDR notes |")
$reportLines.Add("|------|-----|---------|-------------|-------------------|---------------|")
foreach ($m in $modes) {
    foreach ($run in $results.byMode[$m]) {
        $capOk = if ($run.capturePass) { "PASS" } else { "FAIL" }
        $openOk = if ($run.parsed.openabilityPass) { "PASS" } else { "FAIL" }
        $timingStr = "-"
        $wide = @($run.parsed.timing | Where-Object { $_.stillMode -match 'Standard|Zsl|Hdr' })
        if ($wide.Count -gt 0) {
            $t = $wide[-1]
            $timingStr = "req→dng=$($t.t_request_to_dng_ms)ms"
            if ($t.hdr_frames) { $timingStr += " frames=$($t.hdr_frames)" }
        }
        $notes = ""
        if ($m -eq "zsl") {
            if ($run.parsed.zslRingHit) { $notes = "ring hit" }
            elseif ($run.parsed.zslRingMiss) { $notes = "ring miss (fallback)" }
        }
        if ($m -eq "hdr" -and $null -ne $run.parsed.hdrFrames) {
            $notes = "frames=$($run.parsed.hdrFrames)"
        }
        $reportLines.Add("| $m | $($run.index) | $capOk | $openOk | $timingStr | $notes |")
    }
}
$reportPath = Join-Path $benchRoot "report.md"
$reportLines | Out-File -Encoding utf8 $reportPath

$jsonPath = Join-Path $benchRoot "results.json"
($results | ConvertTo-Json -Depth 8) | Set-Content $jsonPath -Encoding UTF8
Write-Host "Wrote $jsonPath" -ForegroundColor Green
Write-Host "Wrote $reportPath" -ForegroundColor Green

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path $resolve) { . $resolve -PrependToPath -Quiet }
if ($Serial) { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null }
else { & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null }
