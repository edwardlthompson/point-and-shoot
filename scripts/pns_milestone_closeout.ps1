param(
    [string]$ConfigPath = "",
    [string]$ProjectRoot = "",
    [switch]$Apply,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_milestone_closeout.ps1 — validate/apply milestone archive cleanup

Usage:
  .\scripts\pns_milestone_closeout.ps1 -ConfigPath .\scripts\milestones\m23.closeout.json
  .\scripts\pns_milestone_closeout.ps1 -ConfigPath .\scripts\milestones\m23.closeout.json -Apply

Behavior:
  - Validates evidence/doc requirements from config.
  - Ensures BUILD_PLAN milestone section is archived.
  - With -Apply, rewrites BUILD_PLAN active section to the configured archived pointer block.
  - Writes a JSON report under hfr-runs/.
"@
    exit 0
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

if ([string]::IsNullOrWhiteSpace($ConfigPath)) {
    $ConfigPath = Join-Path $PSScriptRoot "milestones\m23.closeout.json"
}
$ConfigPath = (Resolve-Path -LiteralPath $ConfigPath).Path

$cfg = Get-Content -LiteralPath $ConfigPath -Raw | ConvertFrom-Json

$buildPlanPath = Join-Path $ProjectRoot ([string]$cfg.buildPlanPath)
$completedPath = Join-Path $ProjectRoot ([string]$cfg.buildPlanCompletedPath)
$changelogPath = Join-Path $ProjectRoot ([string]$cfg.changelogPath)
$coveragePath = Join-Path $ProjectRoot ([string]$cfg.coverageManifestPath)

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$reportDir = Join-Path $ProjectRoot ("hfr-runs\{0}_{1}" -f [string]$cfg.reportPrefix, $utc)
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportPath = Join-Path $reportDir "closeout_report.json"

$result = [ordered]@{
    schema = "pns.milestone_closeout.report.v1"
    milestone = [string]$cfg.milestoneTag
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    apply = [bool]$Apply
    configPath = $ConfigPath
    projectRoot = $ProjectRoot
    reportPath = $reportPath
    steps = @()
}

function Add-Step([string]$Name, [bool]$Pass, [string]$Note = "") {
    $step = [ordered]@{
        name = $Name
        pass = $Pass
    }
    if ($Note) { $step.note = $Note }
    $result.steps += $step
}

foreach ($requiredFile in @($buildPlanPath, $completedPath, $changelogPath, $coveragePath)) {
    $exists = Test-Path -LiteralPath $requiredFile
    Add-Step ("exists_" + [IO.Path]::GetFileName($requiredFile)) $exists $requiredFile
}

$buildPlan = Get-Content -LiteralPath $buildPlanPath -Raw
$completed = Get-Content -LiteralPath $completedPath -Raw
$changelog = Get-Content -LiteralPath $changelogPath -Raw
$coverage = Get-Content -LiteralPath $coveragePath -Raw | ConvertFrom-Json

$activeHeading = [string]$cfg.activeHeading
$archivedHeading = [string]$cfg.archivedHeading
$activeIdx = $buildPlan.IndexOf($activeHeading)
$archivedIdx = $buildPlan.IndexOf($archivedHeading)

if ($activeIdx -ge 0 -and $archivedIdx -ge 0) {
    Add-Step "build_plan_heading_state" $false "Both active and archived milestone headings exist."
} elseif ($activeIdx -lt 0 -and $archivedIdx -lt 0) {
    Add-Step "build_plan_heading_state" $false "Neither active nor archived milestone heading found."
} else {
    $headingState = if ($activeIdx -ge 0) { "active" } else { "archived" }
    Add-Step "build_plan_heading_state" $true $headingState
}

$mutated = $false
if ($activeIdx -ge 0 -and $archivedIdx -lt 0) {
    $nextSectionIdx = $buildPlan.IndexOf("`n## ", $activeIdx + $activeHeading.Length)
    if ($nextSectionIdx -lt 0) { $nextSectionIdx = $buildPlan.Length }
    $activeSection = $buildPlan.Substring($activeIdx, $nextSectionIdx - $activeIdx)
    $unchecked = [regex]::Matches($activeSection, "(?m)^\s*-\s\[\s\]\s").Count
    Add-Step "active_section_no_unchecked_items" ($unchecked -eq 0) ("unchecked=$unchecked")
    if ($Apply -and $unchecked -eq 0) {
        $archiveBlock = (($cfg.archiveBlockLines | ForEach-Object { [string]$_ }) -join "`r`n")
        $buildPlan = $buildPlan.Substring(0, $activeIdx) + $archiveBlock + "`r`n`r`n" + $buildPlan.Substring($nextSectionIdx + 1)
        [IO.File]::WriteAllText($buildPlanPath, $buildPlan)
        $mutated = $true
        Add-Step "build_plan_archive_apply" $true "active->archived pointer applied"
    } elseif (-not $Apply) {
        Add-Step "build_plan_archive_apply" $false "Run with -Apply to archive active milestone section."
    }
} else {
    Add-Step "build_plan_archive_apply" $true "already archived"
}

$completedHeading = [string]$cfg.requiredCompletedHeading
$completedOk = $completed -match [regex]::Escape($completedHeading)
Add-Step "completed_plan_heading" $completedOk $completedHeading

foreach ($needle in @($cfg.requiredChangelogMentions)) {
    $pattern = [string]$needle
    $ok = $changelog -match [regex]::Escape($pattern)
    Add-Step ("changelog_mentions_" + $pattern.Replace(" ", "_")) $ok $pattern
}

$coverageMentions = @()
foreach ($m in @($coverage.requiredMentions)) { $coverageMentions += [string]$m.pattern }
foreach ($needle in @($cfg.requiredCoverageMentions)) {
    $pattern = [string]$needle
    $ok = $coverageMentions -contains $pattern
    Add-Step ("coverage_mentions_" + $pattern.Replace(" ", "_")) $ok $pattern
}

foreach ($rel in @($cfg.requiredPaths)) {
    $p = Join-Path $ProjectRoot ([string]$rel)
    $ok = Test-Path -LiteralPath $p
    Add-Step ("evidence_path_" + ([string]$rel).Replace("\", "/")) $ok $p
}

$result.pass = -not ($result.steps | Where-Object { -not $_.pass })
$result.mutated = $mutated
$result | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8

Write-Host "[milestone_closeout] milestone=$($result.milestone) pass=$($result.pass) apply=$Apply mutated=$mutated report=$reportPath"
if (-not $result.pass) { exit 1 }
exit 0
