param(
    [string]$DebtLedgerPath = "",
    [string]$OwnershipPath = "",
    [string]$ParityLatestPath = "",
    [string]$OutJsonPath = "",
    [string]$OutMarkdownPath = "",
    [string]$PriorIntakePath = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_parity_build_plan_intake.ps1

Builds FLEET_PARITY_BUILD_PLAN_INTAKE from debt ledger v2 for BUILD_PLAN promotion.

Options:
  -DebtLedgerPath   Input ledger (default: <repo>/docs/FLEET_PARITY_DEBT_LEDGER.json)
  -OwnershipPath    Ownership map (default: <repo>/docs/M22_PROVIDER_OWNERSHIP.json)
  -ParityLatestPath Latest parity wrapper (default: <repo>/docs/FLEET_PARITY_LATEST.json)
  -OutJsonPath      Output JSON (default: <repo>/docs/FLEET_PARITY_BUILD_PLAN_INTAKE.json)
  -OutMarkdownPath  Output Markdown (default: <repo>/docs/FLEET_PARITY_BUILD_PLAN_INTAKE.md)
  -PriorIntakePath  Prior intake to preserve status (default: OutJsonPath if exists)
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $DebtLedgerPath) { $DebtLedgerPath = Join-Path $repoRoot "docs\FLEET_PARITY_DEBT_LEDGER.json" }
if (-not $OwnershipPath) { $OwnershipPath = Join-Path $repoRoot "docs\M22_PROVIDER_OWNERSHIP.json" }
if (-not $ParityLatestPath) { $ParityLatestPath = Join-Path $repoRoot "docs\FLEET_PARITY_LATEST.json" }
if (-not $OutJsonPath) { $OutJsonPath = Join-Path $repoRoot "docs\FLEET_PARITY_BUILD_PLAN_INTAKE.json" }
if (-not $OutMarkdownPath) { $OutMarkdownPath = Join-Path $repoRoot "docs\FLEET_PARITY_BUILD_PLAN_INTAKE.md" }
if (-not $PriorIntakePath -and (Test-Path -LiteralPath $OutJsonPath)) { $PriorIntakePath = $OutJsonPath }

function Load-JsonFile([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return $null }
    if (-not (Test-Path -LiteralPath $Path)) { return $null }
    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    } catch {
        return $null
    }
}

function Get-SuggestedMilestone([string]$WorkType, [string]$BuildPlanSprint) {
    if ($BuildPlanSprint -match '^(\d+)\.') {
        return "M$($Matches[1])"
    }
    switch ($WorkType) {
        "AppFeature" { return "M27.1" }
        "AutomationProof" { return "M27.3" }
        "DeliveryHonesty" { return "M24" }
        "MatrixGate" { return "M27.2" }
        default { return "M27" }
    }
}

$ledger = Load-JsonFile $DebtLedgerPath
if (-not $ledger -or -not $ledger.rows) {
    Write-Warning "[parity_intake] debt ledger missing or empty: $DebtLedgerPath"
    $ledgerRows = @()
} else {
    $ledgerRows = @($ledger.rows)
}

$priorStatus = @{}
$prior = Load-JsonFile $PriorIntakePath
if ($prior -and $prior.rows) {
    foreach ($r in @($prior.rows)) {
        if ($r.id) { $priorStatus[[string]$r.id] = [string]$r.status }
    }
}

$parityLatest = Load-JsonFile $ParityLatestPath
$latestSerial = if ($parityLatest -and $parityLatest.serial) { [string]$parityLatest.serial } else { "" }
$latestMode = if ($parityLatest -and $parityLatest.mode) { [string]$parityLatest.mode } else { "" }

$intakeRows = @()
$seenIds = @{}

foreach ($debt in $ledgerRows) {
    if ($debt.actionable -ne $true) { continue }
    $catalogId = [string]$debt.catalogId
    $workType = [string]$debt.workType
    $id = "PBI-$catalogId-$workType"
    if ($seenIds.ContainsKey($id)) { continue }
    $seenIds[$id] = $true

    $buildPlanSprint = if ($debt.buildPlanSprint) { [string]$debt.buildPlanSprint } else { $null }
    $status = if ($priorStatus.ContainsKey($id)) { $priorStatus[$id] } else { "open" }

    $sampleArtifact = $null
    if ($debt.sampleArtifacts -and @($debt.sampleArtifacts).Count -gt 0) {
        $sampleArtifact = [string]$debt.sampleArtifacts[0].sweepDir
    }

    $intakeRows += [ordered]@{
        id = $id
        catalogId = $catalogId
        displayName = if ($debt.displayName) { [string]$debt.displayName } else { $catalogId }
        workType = $workType
        gapClass = [string]$debt.gapClass
        failReason = [string]$debt.failReason
        ownerClass = if ($debt.ownerClass) { [string]$debt.ownerClass } else { "UNASSIGNED" }
        buildPlanSprint = $buildPlanSprint
        suggestedMilestone = Get-SuggestedMilestone $workType $buildPlanSprint
        proofScript = if ($debt.proofScript) { [string]$debt.proofScript } else { $null }
        closureEffort = if ($debt.closureEffort) { [string]$debt.closureEffort } else { $null }
        priority = [int]$debt.closurePriority
        recurrence = [int]$debt.recurrence
        devices = $debt.devices
        status = $status
        sampleArtifact = $sampleArtifact
    }
}

$orderedIntake = @(
    $intakeRows |
        Sort-Object @{ Expression = { [int]$_.priority }; Descending = $false },
        @{ Expression = { [int]$_.recurrence }; Descending = $true },
        @{ Expression = { $_.catalogId }; Descending = $false }
)

$byWorkType = [ordered]@{}
foreach ($row in $orderedIntake) {
    $wt = [string]$row.workType
    if (-not $byWorkType.Contains($wt)) { $byWorkType[$wt] = 0 }
    $byWorkType[$wt] = [int]$byWorkType[$wt] + 1
}

$intake = [ordered]@{
    schema = "pns.fleet_parity_build_plan_intake.v1"
    generatedUtc = [DateTime]::UtcNow.ToString("o")
    debtLedgerSchema = if ($ledger) { [string]$ledger.schema } else { $null }
    debtLedgerGeneratedUtc = if ($ledger) { [string]$ledger.generatedUtc } else { $null }
    latestParitySerial = $latestSerial
    latestParityMode = $latestMode
    openCount = @($orderedIntake | Where-Object { $_.status -eq "open" }).Count
    rowCount = @($orderedIntake).Count
    summary = [ordered]@{
        byWorkType = $byWorkType
    }
    rows = $orderedIntake
}

$jsonDir = Split-Path -Parent $OutJsonPath
$mdDir = Split-Path -Parent $OutMarkdownPath
if ($jsonDir -and -not (Test-Path -LiteralPath $jsonDir)) { New-Item -ItemType Directory -Force -Path $jsonDir | Out-Null }
if ($mdDir -and -not (Test-Path -LiteralPath $mdDir)) { New-Item -ItemType Directory -Force -Path $mdDir | Out-Null }
$intake | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $OutJsonPath -Encoding utf8

$md = @(
    "# Fleet parity build-plan intake",
    "",
    "- Generated: $($intake.generatedUtc)",
    "- Open rows: $($intake.openCount) / $($intake.rowCount)",
    "- Latest parity: serial=$latestSerial mode=$latestMode",
    "",
    "Promote rows to [BUILD_PLAN.md](../BUILD_PLAN.md) Milestone 27 when scoped.",
    ""
)

foreach ($wt in @("AppFeature", "AutomationProof", "DeliveryHonesty", "MatrixGate")) {
    $subset = @($orderedIntake | Where-Object { $_.workType -eq $wt -and $_.status -eq "open" })
    if ($subset.Count -eq 0) { continue }
    $md += "## $wt ($($subset.Count) open)"
    $md += ""
    foreach ($row in $subset) {
        $proof = if ($row.proofScript) { $row.proofScript } else { '-' }
        $sprint = if ($row.buildPlanSprint) { $row.buildPlanSprint } else { $row.suggestedMilestone }
        $md += ('- [ ] **{0}** - {1} ({2}, recurrence={3}, owner={4}, sprint={5}, proof={6})' -f `
            $row.id, $row.displayName, $row.gapClass, $row.recurrence, $row.ownerClass, $sprint, $proof)
    }
    $md += ""
}

$halRows = @($ledgerRows | Where-Object { $_.workType -in @('HalHonestLimit', 'HumanOnly') } | Select-Object -First 15)
if ($halRows.Count -gt 0) {
    $md += '## Appendix - HAL / human limits (not actionable)'
    $md += ''
    foreach ($row in $halRows) {
        $md += ('- {0} ({1}, fail={2})' -f $row.catalogId, $row.workType, $row.failReason)
    }
    $md += ''
}

$md | Set-Content -LiteralPath $OutMarkdownPath -Encoding utf8

Write-Host "[parity_intake] rows=$($intake.rowCount) open=$($intake.openCount)"
Write-Host "[parity_intake] json=$OutJsonPath"
Write-Host "[parity_intake] md=$OutMarkdownPath"
exit 0
