param(
    [int]$MilestoneNumber,
    [string]$Title = "",
    [string]$ProjectRoot = "",
    [switch]$Overwrite,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help -or $MilestoneNumber -le 0) {
    Write-Host @"
pns_milestone_closeout_init.ps1 — scaffold milestone closeout config

Usage:
  .\scripts\pns_milestone_closeout_init.ps1 -MilestoneNumber 24 -Title "Fleet next-wave hardening"
  .\scripts\pns_milestone_closeout_init.ps1 -MilestoneNumber 24 -Overwrite

Creates:
  scripts\milestones\m<NN>.closeout.json
"@
    if ($Help) { exit 0 }
    throw "MilestoneNumber must be > 0."
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
    $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
    $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$milestoneTag = "M$MilestoneNumber"
$milestoneSlug = "m{0}" -f $MilestoneNumber
if ([string]::IsNullOrWhiteSpace($Title)) {
    $Title = "Milestone $MilestoneNumber closeout"
}

$milestonesDir = Join-Path $PSScriptRoot "milestones"
New-Item -ItemType Directory -Force -Path $milestonesDir | Out-Null
$configPath = Join-Path $milestonesDir "$milestoneSlug.closeout.json"
if ((Test-Path -LiteralPath $configPath) -and -not $Overwrite) {
    throw "Config already exists: $configPath (use -Overwrite to replace)"
}

$today = (Get-Date -Format "yyyy-MM-dd")
$archiveHeadingDate = (Get-Date -Format "yyyy-MM-dd")

$cfg = [ordered]@{
    schema = "pns.milestone_closeout.v1"
    milestoneNumber = $MilestoneNumber
    milestoneTag = $milestoneTag
    buildPlanPath = "BUILD_PLAN.md"
    buildPlanCompletedPath = "BUILD_PLAN_COMPLETED.md"
    changelogPath = "CHANGELOG.md"
    coverageManifestPath = "scripts/changelog_coverage.v1.json"
    activeHeading = "## Milestone $MilestoneNumber — $Title *(active)*"
    archivedHeading = "## Milestone $MilestoneNumber — $Title *(archived)*"
    archiveBlockLines = @(
        "## Milestone $MilestoneNumber — $Title *(archived)*",
        "",
        "Milestone $MilestoneNumber is complete and moved to **`BUILD_PLAN_COMPLETED.md`** under **Milestone $MilestoneNumber archive section**, including Sprint **$MilestoneNumber.0+** closure details and gate outcomes."
    )
    requiredCompletedHeading = "### Milestone $MilestoneNumber — $Title *(archived $archiveHeadingDate)*"
    requiredChangelogMentions = @(
        "Milestone $MilestoneNumber"
    )
    requiredCoverageMentions = @(
        "Milestone $MilestoneNumber"
    )
    requiredPaths = @(
        "hfr-runs/$milestoneSlug`_closeout_chain_$today"
    )
    reportPrefix = "milestone_closeout_$milestoneSlug"
}

($cfg | ConvertTo-Json -Depth 8) + "`n" | Set-Content -LiteralPath $configPath -Encoding utf8
Write-Host "[milestone_closeout_init] wrote $configPath"
exit 0
