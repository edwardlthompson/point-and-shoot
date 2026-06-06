param(
    [string]$RunsRoot = "",
    [string]$LeaderboardJsonPath = "",
    [string]$LeaderboardMarkdownPath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_fleet_parity_leaderboard_refresh.ps1

Rebuilds docs/FLEET_PARITY_DEVICE_LEADERBOARD.{json,md} from historical
hfr-runs/parity_sweep_* artifacts.

Options:
  -RunsRoot                 Root folder containing parity_sweep_* directories
                            (default: <repo>/hfr-runs)
  -LeaderboardJsonPath      Output JSON path
                            (default: <repo>/docs/FLEET_PARITY_DEVICE_LEADERBOARD.json)
  -LeaderboardMarkdownPath  Output Markdown path
                            (default: <repo>/docs/FLEET_PARITY_DEVICE_LEADERBOARD.md)
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "pns_leaderboard_common.ps1")
if (-not $RunsRoot) { $RunsRoot = Join-Path $repoRoot "hfr-runs" }
if (-not $LeaderboardJsonPath) { $LeaderboardJsonPath = Join-Path $repoRoot "docs\FLEET_PARITY_DEVICE_LEADERBOARD.json" }
if (-not $LeaderboardMarkdownPath) { $LeaderboardMarkdownPath = Join-Path $repoRoot "docs\FLEET_PARITY_DEVICE_LEADERBOARD.md" }

# Scoring helpers live in pns_leaderboard_common.ps1 (dot-sourced above).

function Write-LeaderboardMarkdown($LeaderboardObj, [string]$MarkdownPath) {
    $lines = @(
        "# Fleet parity device leaderboard",
        "",
        "Scored from parity sweep cells + fleet matrix capability gates. Higher is better.",
        ""
    )
    foreach ($entry in @($LeaderboardObj.entries)) {
        $lines += ("- #{0} **{1}** - total {2}/{3} ({4}%)" -f $entry.rank, $entry.deviceLabel, $entry.score.total.score, $entry.score.total.maxScore, $entry.score.total.percent)
        $lines += ("  - features: {0}/{1} ({2}%)" -f $entry.score.features.score, $entry.score.features.maxScore, $entry.score.features.percent)
        $lines += ("  - resolutions: {0}/{1} ({2}%)" -f $entry.score.resolutions.score, $entry.score.resolutions.maxScore, $entry.score.resolutions.percent)
        $lines += ("  - capabilities: {0}/{1} ({2}%)" -f $entry.score.capabilities.score, $entry.score.capabilities.maxScore, $entry.score.capabilities.percent)
        if ($entry.testedApiLevel) {
            $lines += "  - tested API: $($entry.testedApiLevel)"
        } elseif ($entry.sdkInt) {
            $lines += "  - tested API: API $($entry.sdkInt)"
        }
        $lines += "  - last sweep: $($entry.lastSeenUtc) ($($entry.lastSweepDir))"
        $lines += ""
    }
    if (@($LeaderboardObj.entries).Count -eq 0) {
        $lines += "- No scored devices yet."
    }
    $lines | Set-Content -LiteralPath $MarkdownPath -Encoding utf8
}

if (-not (Test-Path -LiteralPath $RunsRoot)) {
    Write-Error "RunsRoot not found: $RunsRoot"
    exit 1
}

$sweepDirs = @(Get-ChildItem -LiteralPath $RunsRoot -Directory -Filter "parity_sweep_*" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTimeUtc -Descending)
if ($sweepDirs.Count -eq 0) {
    Write-Warning "No parity_sweep_* directories under $RunsRoot"
}

$byDevice = @{}
foreach ($dir in $sweepDirs) {
    $reportPath = Join-Path $dir.FullName "parity_report.json"
    $inAppPath = Join-Path $dir.FullName "in_app_parity_report.json"
    if (-not (Test-Path -LiteralPath $reportPath) -or -not (Test-Path -LiteralPath $inAppPath)) { continue }
    try {
        $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
        $inApp = Get-Content -LiteralPath $inAppPath -Raw | ConvertFrom-Json
    } catch {
        continue
    }
    if (-not $inApp -or -not $inApp.cells) { continue }
    $inApp = Apply-ProofEvidenceToInApp $inApp $report $dir.FullName
    $matrix = Get-MatrixFromSweepDir $dir.FullName

    $manufacturer = if ($matrix -and $matrix.device -and $matrix.device.manufacturer) { [string]$matrix.device.manufacturer } else { "" }
    $model = if ($matrix -and $matrix.device -and $matrix.device.model) { [string]$matrix.device.model } else { "" }
    if ([string]::IsNullOrWhiteSpace($manufacturer) -or [string]::IsNullOrWhiteSpace($model) -or
        $manufacturer.Trim().ToLowerInvariant() -eq "unknown" -or
        $model.Trim().ToLowerInvariant() -eq "unknown") {
        continue
    }
    $fingerprint = if ($matrix -and $matrix.scanMeta -and $matrix.scanMeta.fingerprintSha256Prefix) { [string]$matrix.scanMeta.fingerprintSha256Prefix } elseif ($inApp.fingerprintSha256Prefix) { [string]$inApp.fingerprintSha256Prefix } else { "unknown" }
    $serialRaw = if ($report.serial) { [string]$report.serial } else { "unknown" }
    $serialSuffix = if ($serialRaw.Length -ge 4) { $serialRaw.Substring($serialRaw.Length - 4) } else { $serialRaw }
    $deviceKey = "$manufacturer|$model|$fingerprint"
    $marketingMapPath = Join-Path $repoRoot "docs\leaderboard\data\device_marketing_names.json"
    $marketingMap = $null
    if (Test-Path -LiteralPath $marketingMapPath) {
        try { $marketingMap = Get-Content -LiteralPath $marketingMapPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { }
    }
    $marketingEntry = Get-MarketingEntry $marketingMap $model
    $score = Get-ParityScoreBreakdown $inApp $matrix
    $sdkInt = if ($matrix -and $matrix.scanMeta) { $matrix.scanMeta.sdkInt } else { $null }

    $entry = [ordered]@{
        deviceKey = $deviceKey
        deviceLabel = (Get-DeviceDisplayLabel $manufacturer $model $marketingEntry $serialSuffix)
        marketingName = if ($marketingEntry) { [string]$marketingEntry.marketingName } else { $null }
        manufacturer = $manufacturer
        model = $model
        fingerprintSha256Prefix = $fingerprint
        serialSuffix = $serialSuffix
        sdkInt = $sdkInt
        testedApiLevel = if ($sdkInt) { Get-AndroidApiLabel $sdkInt } else { $null }
        lastSeenUtc = if ($report.timestampUtc) { [string]$report.timestampUtc } else { [DateTime]::UtcNow.ToString("o") }
        lastSweepTimestampUtc = if ($report.timestampUtc) { [string]$report.timestampUtc } else { [DateTime]::UtcNow.ToString("o") }
        lastSweepMode = $report.mode
        lastSweepPass = ($report.pass -eq $true)
        lastSweepDir = if ($report.outDir) { [string]$report.outDir } else { [string]$dir.FullName }
        score = $score
    }

    if ($byDevice.ContainsKey($deviceKey)) {
        $existing = $byDevice[$deviceKey]
        $newTs = Parse-Utc $entry.lastSweepTimestampUtc
        $oldTs = Parse-Utc $existing.lastSweepTimestampUtc
        if ($newTs -gt $oldTs) {
            $byDevice[$deviceKey] = $entry
        }
    } else {
        $byDevice[$deviceKey] = $entry
    }
}

$entries = @($byDevice.Values)
$sorted = @($entries | Sort-Object @{ Expression = { [double]$_.score.total.score }; Descending = $true }, @{ Expression = { [double]$_.score.total.percent }; Descending = $true }, @{ Expression = { $_.lastSeenUtc }; Descending = $true })
for ($i = 0; $i -lt $sorted.Count; $i++) {
    $sorted[$i] | Add-Member -NotePropertyName rank -NotePropertyValue ($i + 1) -Force
}

$leaderboard = [ordered]@{
    schema = "pns.fleet_parity_device_leaderboard.v1"
    updatedUtc = [DateTime]::UtcNow.ToString("o")
    entries = $sorted
}

$jsonDir = Split-Path -Parent $LeaderboardJsonPath
$mdDir = Split-Path -Parent $LeaderboardMarkdownPath
if (-not (Test-Path -LiteralPath $jsonDir)) { New-Item -ItemType Directory -Force -Path $jsonDir | Out-Null }
if (-not (Test-Path -LiteralPath $mdDir)) { New-Item -ItemType Directory -Force -Path $mdDir | Out-Null }

$leaderboard | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $LeaderboardJsonPath -Encoding utf8
Write-LeaderboardMarkdown $leaderboard $LeaderboardMarkdownPath

Write-Host "[leaderboard_refresh] sweeps=$($sweepDirs.Count) devices=$($sorted.Count)"
Write-Host "[leaderboard_refresh] json=$LeaderboardJsonPath"
Write-Host "[leaderboard_refresh] md=$LeaderboardMarkdownPath"
exit 0
