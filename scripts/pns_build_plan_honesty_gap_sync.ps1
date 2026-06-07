param(
    [string]$BuildPlanPath = "",
    [string]$ParityHistoryPath = "",
    [switch]$DryRun,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_build_plan_honesty_gap_sync.ps1

Auto-syncs HAL honesty gap fix tasks from latest Full parity run into BUILD_PLAN.md.

Options:
  -BuildPlanPath      Target BUILD_PLAN.md (default: <repo>/BUILD_PLAN.md)
  -ParityHistoryPath  Source history JSONL (default: <repo>/docs/FLEET_PARITY_HISTORY.jsonl)
  -DryRun             Print generated section without writing file
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $BuildPlanPath) { $BuildPlanPath = Join-Path $repoRoot "BUILD_PLAN.md" }
if (-not $ParityHistoryPath) { $ParityHistoryPath = Join-Path $repoRoot "docs\FLEET_PARITY_HISTORY.jsonl" }

if (-not (Test-Path -LiteralPath $BuildPlanPath)) { throw "BUILD_PLAN.md not found: $BuildPlanPath" }
if (-not (Test-Path -LiteralPath $ParityHistoryPath)) { throw "Parity history not found: $ParityHistoryPath" }

$startMarker = "<!-- AUTO_HAL_HONESTY_GAPS_START -->"
$endMarker = "<!-- AUTO_HAL_HONESTY_GAPS_END -->"

function Read-HistoryRows([string]$Path) {
    $rows = @()
    foreach ($line in Get-Content -LiteralPath $Path -ErrorAction SilentlyContinue) {
        $trim = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trim)) { continue }
        try { $rows += ($trim | ConvertFrom-Json) } catch { }
    }
    return $rows
}

$history = Read-HistoryRows $ParityHistoryPath
$latestFull = @($history | Where-Object { $_.mode -eq "Full" } | Sort-Object timestampUtc -Descending | Select-Object -First 1)

$warnings = @()
$rows = @()
$runRef = $null
if ($latestFull.Count -eq 0) {
    $warnings += "No Full run found in parity history."
} else {
    $run = $latestFull[0]
    $runRef = [string]$run.outDir
    $inAppPath = Join-Path $runRef "in_app_parity_report.json"
    if (-not (Test-Path -LiteralPath $inAppPath)) {
        $warnings += "in_app_parity_report.json missing for latest Full run: $runRef"
    } else {
        $inApp = $null
        try {
            $inApp = Get-Content -LiteralPath $inAppPath -Raw | ConvertFrom-Json
        } catch {
            $warnings += "Failed to parse in_app_parity_report.json for run: $runRef"
        }
        if ($inApp -and $inApp.cells) {
            $rows = @($inApp.cells | Where-Object {
                    $_.advertised -eq $true -and
                    $_.provenOk -ne $true -and
                    $_.failReason -ne "not_advertised"
                })
        }
    }
}

$generated = @()
$generated += $startMarker
$generated += ("- Generated: {0}" -f ([DateTime]::UtcNow.ToString("o")))
if ($runRef) { $generated += ("- Source run: {0}" -f $runRef) }
$generated += ("- Open honesty gaps: {0}" -f $rows.Count)

if ($warnings.Count -gt 0) {
    $generated += "- Warnings:"
    foreach ($w in $warnings) { $generated += ("  - {0}" -f $w) }
}

if ($rows.Count -eq 0) {
    $generated += "- [ ] **[AGENT]** No advertised-vs-proven honesty gaps in latest Full run."
} else {
    $priorityOrder = @("session_failed", "not_proven", "advertised_not_surfaced", "matrix_tier_quick")
    $sorted = @(
        $rows | Sort-Object `
            @{ Expression = { $idx = [array]::IndexOf($priorityOrder, [string]$_.failReason); if ($idx -lt 0) { 999 } else { $idx } }; Descending = $false }, `
            @{ Expression = { [string]$_.catalogId }; Descending = $false }
    )

    $generated += "- Priority order: session_failed -> not_proven -> advertised_not_surfaced -> matrix_tier_quick"
    foreach ($r in $sorted) {
        $id = [string]$r.catalogId
        $reason = if ($r.failReason) { [string]$r.failReason } else { "unknown" }
        $impact = if ($r.consumerImpact) { [string]$r.consumerImpact } else { "unknown" }
        $generated += ("- [ ] **[AGENT]** Honesty fix: {0} (reason={1}, impact={2})" -f $id, $reason, $impact)
    }
}
$generated += $endMarker

$planText = [System.IO.File]::ReadAllText($BuildPlanPath, [System.Text.Encoding]::UTF8)
$escapedStart = [regex]::Escape($startMarker)
$escapedEnd = [regex]::Escape($endMarker)
$pattern = "(?s)$escapedStart.*?$escapedEnd"
if ($planText -notmatch $pattern) { throw "Auto-sync markers not found in BUILD_PLAN.md for HAL honesty gaps." }

$replacement = ($generated -join "`r`n")
$newText = [regex]::Replace($planText, $pattern, $replacement, 1)

if ($DryRun) {
    Write-Host $replacement
    exit 0
}

$utf8Bom = New-Object System.Text.UTF8Encoding($true)
[System.IO.File]::WriteAllText($BuildPlanPath, $newText, $utf8Bom)
Write-Host "[honesty_gap_sync] updated $BuildPlanPath rows=$($rows.Count)"
exit 0
