param(
    [string]$BuildPlanPath = "",
    [string]$ParityHistoryPath = "",
    [int]$RunsToConsider = 2,
    [switch]$DryRun,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_build_plan_ship_blockers_sync.ps1

Auto-syncs ship blockers from the latest Full parity sweeps into BUILD_PLAN.md.

Options:
  -BuildPlanPath      Target BUILD_PLAN.md (default: <repo>/BUILD_PLAN.md)
  -ParityHistoryPath  Source history JSONL (default: <repo>/docs/FLEET_PARITY_HISTORY.jsonl)
  -RunsToConsider     Number of latest Full runs to inspect (default: 2)
  -DryRun             Print generated section without writing file
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $BuildPlanPath) { $BuildPlanPath = Join-Path $repoRoot "BUILD_PLAN.md" }
if (-not $ParityHistoryPath) { $ParityHistoryPath = Join-Path $repoRoot "docs\FLEET_PARITY_HISTORY.jsonl" }

if (-not (Test-Path -LiteralPath $BuildPlanPath)) {
    throw "BUILD_PLAN.md not found: $BuildPlanPath"
}
if (-not (Test-Path -LiteralPath $ParityHistoryPath)) {
    throw "Parity history not found: $ParityHistoryPath"
}
if ($RunsToConsider -lt 1) {
    throw "RunsToConsider must be >= 1"
}

$startMarker = "<!-- AUTO_SHIP_BLOCKERS_START -->"
$endMarker = "<!-- AUTO_SHIP_BLOCKERS_END -->"

function Read-HistoryRows([string]$Path) {
    $rows = @()
    foreach ($line in Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue) {
        $trim = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trim)) { continue }
        try {
            $rows += ($trim | ConvertFrom-Json)
        } catch {
            # ignore malformed jsonl lines
        }
    }
    return $rows
}

function Parse-ShipBlockersMarkdown([string]$MarkdownPath) {
    $out = @()
    if (-not (Test-Path -LiteralPath $MarkdownPath)) { return $out }
    foreach ($line in Get-Content -LiteralPath $MarkdownPath -ErrorAction SilentlyContinue) {
        if ($line -match '^\s*-\s+\*\*([^\*]+)\*\*.*reason=([^\s]+)') {
            $out += [ordered]@{
                catalogId = [string]$Matches[1]
                failReason = [string]$Matches[2]
            }
        }
    }
    return $out
}

function Parse-ShipBlockersInApp([string]$InAppJsonPath) {
    $out = @()
    if (-not (Test-Path -LiteralPath $InAppJsonPath)) { return $out }
    $obj = $null
    try {
        $obj = (Get-Content -LiteralPath $InAppJsonPath -Raw | ConvertFrom-Json)
    } catch {
        return $out
    }
    if (-not $obj -or -not $obj.cells) { return $out }
    foreach ($cell in @($obj.cells)) {
        $isShipBlocker = ($cell.consumerImpact -eq "SHIP_BLOCKER")
        $isBlockingGap = ($cell.gap -in @("GAP_ADVERTISED_NOT_PROVEN", "GAP_DELIVERY_MISMATCH", "GAP_REGRESSION_SINCE_BASELINE")) -or
            (($cell.provenOk -ne $true) -and ($cell.advertised -eq $true))
        if ($isShipBlocker -and $isBlockingGap) {
            $out += [ordered]@{
                catalogId = [string]$cell.catalogId
                failReason = if ($cell.failReason) { [string]$cell.failReason } else { "unknown" }
            }
        }
    }
    return $out
}

$history = Read-HistoryRows $ParityHistoryPath
$fullRows = @($history | Where-Object { $_.mode -eq "Full" } | Sort-Object timestampUtc -Descending | Select-Object -First $RunsToConsider)

$warnings = @()
if ($fullRows.Count -eq 0) {
    $warnings += "No Full runs found in parity history."
}

$byCatalog = @{}
$runRefs = @()
$runIdx = 0
foreach ($run in $fullRows) {
    $runIdx++
    $outDir = [string]$run.outDir
    $runRefs += ("- run{0}: {1} (shipBlockerGapCount={2})" -f $runIdx, $outDir, [string]$run.shipBlockerGapCount)
    $mdPath = Join-Path $outDir "parity_ship_blockers.md"
    $blockers = Parse-ShipBlockersMarkdown $mdPath
    if ($blockers.Count -eq 0) {
        $inAppPath = Join-Path $outDir "in_app_parity_report.json"
        $blockers = Parse-ShipBlockersInApp $inAppPath
        if ($blockers.Count -eq 0) {
            $warnings += "No ship blocker rows found for run: $outDir"
        }
    }
    foreach ($b in $blockers) {
        $id = [string]$b.catalogId
        if ([string]::IsNullOrWhiteSpace($id)) { continue }
        if (-not $byCatalog.ContainsKey($id)) {
            $byCatalog[$id] = [ordered]@{
                catalogId = $id
                seen = 0
                failReasons = @{}
                latestOutDir = $outDir
            }
        }
        $entry = $byCatalog[$id]
        $entry.seen = [int]$entry.seen + 1
        $reason = if ($b.failReason) { [string]$b.failReason } else { "unknown" }
        $entry.failReasons[$reason] = $true
        if (-not $entry.latestOutDir) { $entry.latestOutDir = $outDir }
    }
}

$generated = @()
$generated += $startMarker
$generated += ("- Generated: {0}" -f ([DateTime]::UtcNow.ToString("o")))
$generated += ("- Source runs considered: {0}" -f $fullRows.Count)
if ($runRefs.Count -gt 0) {
    $generated += "- Runs:"
    $generated += $runRefs
}
if ($warnings.Count -gt 0) {
    $generated += "- Warnings:"
    foreach ($w in $warnings) { $generated += ("  - {0}" -f $w) }
}

$sorted = @($byCatalog.Values | Sort-Object @{ Expression = { [int]$_.seen }; Descending = $true }, @{ Expression = { $_.catalogId }; Descending = $false })
if ($sorted.Count -eq 0) {
    $generated += "- [ ] **[AGENT]** No ship blockers found in latest Full runs."
} else {
    foreach ($entry in $sorted) {
        $reasons = @($entry.failReasons.Keys | Sort-Object) -join ", "
        $generated += ("- [ ] **[AGENT]** Ship blocker: {0} ({1}, seen {2}/{3} recent runs) - latest: {4}" -f `
            $entry.catalogId, $reasons, [int]$entry.seen, $fullRows.Count, [string]$entry.latestOutDir)
    }
}
$generated += $endMarker

$planText = [System.IO.File]::ReadAllText($BuildPlanPath, [System.Text.Encoding]::UTF8)
$escapedStart = [regex]::Escape($startMarker)
$escapedEnd = [regex]::Escape($endMarker)
$pattern = "(?s)$escapedStart.*?$escapedEnd"
if ($planText -notmatch $pattern) {
    throw "Auto-sync markers not found in BUILD_PLAN.md"
}

$replacement = ($generated -join "`r`n")
$newText = [regex]::Replace($planText, $pattern, $replacement, 1)

if ($DryRun) {
    Write-Host $replacement
    exit 0
}

$utf8Bom = New-Object System.Text.UTF8Encoding($true)
[System.IO.File]::WriteAllText($BuildPlanPath, $newText, $utf8Bom)
Write-Host "[ship_blocker_sync] updated $BuildPlanPath blockers=$($sorted.Count) runs=$($fullRows.Count)"
exit 0
