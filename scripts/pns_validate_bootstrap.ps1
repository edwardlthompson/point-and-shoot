# Bootstrap template compliance gate (host-only, no Gradle).
#
# Usage:
#   .\scripts\pns_validate_bootstrap.ps1
#   .\scripts\pns_validate_bootstrap.ps1 -ProjectRoot C:\path\to\repo

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$failures = New-Object System.Collections.Generic.List[string]

function Require-Path {
  param([string]$Rel, [string]$Label = $Rel)
  $full = Join-Path $ProjectRoot $Rel
  if (-not (Test-Path -LiteralPath $full)) {
    $failures.Add("missing: $Label ($Rel)")
  }
}

function Require-FileContains {
  param([string]$Rel, [string]$Pattern, [string]$Label)
  $full = Join-Path $ProjectRoot $Rel
  if (-not (Test-Path -LiteralPath $full)) {
    $failures.Add("missing: $Label ($Rel)")
    return
  }
  $text = Get-Content -LiteralPath $full -Raw -ErrorAction SilentlyContinue
  if ($text -notmatch $Pattern) {
    $failures.Add("pattern: $Label ($Rel)")
  }
}

# Core template files
Require-Path "AGENTS.md"
Require-Path "AGENT_MEMORY.md"
Require-Path "BUILD_PLAN.md"
Require-Path "BUILD_PLAN_COMPLETED.md"
Require-Path "COMPLETED_TASKS.md"
Require-Path "DECISION_LOG.md"
Require-Path "KNOWLEDGE_BASE.md"
Require-Path "CONTRIBUTING.md"
Require-Path "HUMAN_BACKLOG.md"
Require-Path "TEMPLATE_INDEX.json"
Require-Path "docs/START_HERE.md"
Require-Path "docs/CURSOR_MODES.md"
Require-Path "docs/FOR_AGENTS.md"
Require-Path "docs/INITIALIZATION_PROMPT.md"
Require-Path "docs/UPGRADING_FROM_TEMPLATE.md"
Require-Path "docs/BOOTSTRAP_ALIGNMENT.md"
Require-Path "docs/BOOTSTRAP_TEMPLATE_MAP.md"
Require-Path "docs/SECURITY_TRIAGE.md"
Require-Path "docs/adr/README.md"
Require-Path ".cursor/rules/agent-automation-hub.mdc"
Require-Path ".cursor/rules/cursor-modes.mdc"
Require-Path ".cursor/rules/destructive-ops.mdc"
Require-Path ".cursor/rules/core-directives.mdc"
Require-Path ".pre-commit-config.yaml"
Require-Path ".github/CODEOWNERS"
Require-Path ".github/workflows/dependency-review.yml"
Require-Path "scripts/pns_local_dev_parallel.ps1"
Require-Path "scripts/pns_watch_agent_gates.ps1"
Require-Path "scripts/pns_check_batch_commands.ps1"
Require-Path "scripts/pns_check_template_updates.ps1"
Require-Path "scripts/pns_check_repo_hygiene.ps1"
Require-Path ".cursor/rules/batch-commands.mdc"
Require-Path "docs/BATCH_COMMANDS.md"
Require-Path "docs/help/BATCH_COMMANDS.md"
Require-Path "CODE_REVIEW.md.example"
Require-Path "RELEASE_NOTES.md.example"
Require-Path ".template-version"
Require-Path ".template-update.json"
Require-Path ".env.example"
Require-Path ".cursor-session-state.example.json"

foreach ($cmd in @(
    "audit", "debug", "gates", "triage", "dependabot", "push", "prerelease", "regress",
    "feature", "fix", "init", "prune", "ci", "docs", "upgrade", "setup", "plan", "restore", "compact", "scope",
    "cleanup",
    "bootstrap", "verify", "build", "ship", "maintain"
  )) {
  Require-Path ".cursor/commands/$cmd.md" ".cursor/commands/$cmd.md"
}

# Sprint labels
Require-FileContains "BUILD_PLAN.md" '\[AGENT\]' "BUILD_PLAN has [AGENT] labels"
Require-FileContains "BUILD_PLAN.md" '\[HUMAN\]' "BUILD_PLAN has [HUMAN] labels"
Require-FileContains "BUILD_PLAN.md" '\[ADB\]' "BUILD_PLAN has [ADB] labels"
Require-FileContains "BUILD_PLAN.md" '\[AUTO\]' "BUILD_PLAN has [AUTO] labels"
Require-FileContains "AGENTS.md" 'Template file map' "AGENTS.md template map"
Require-FileContains "AGENTS.md" 'Agent router' "AGENTS.md agent router"
Require-FileContains ".template-version" '0\.15\.0' ".template-version is 0.15.0"

# Module scaffolds (Sprint TM)
foreach ($mod in @("pns-core", "pns-fleet", "pns-capture", "pns-preview")) {
  Require-Path "modules/$mod/MODULE.md" "modules/$mod/MODULE.md"
}

Require-Path "examples/golden-path/README.md"

# No committed device secrets
$envExample = Join-Path $ProjectRoot "scripts/pns_adb_device.env.example"
$envLive = Join-Path $ProjectRoot "scripts/pns_adb_device.env"
if (Test-Path -LiteralPath $envLive) {
  $gitCheck = & git -C $ProjectRoot check-ignore -q "scripts/pns_adb_device.env" 2>$null
  if ($LASTEXITCODE -ne 0) {
    $failures.Add("scripts/pns_adb_device.env must be gitignored")
  }
}

if ($failures.Count -gt 0) {
  Write-Host "BOOTSTRAP VALIDATE: FAIL ($($failures.Count) issue(s))"
  foreach ($f in $failures) { Write-Host "  - $f" }
  exit 1
}

& (Join-Path $PSScriptRoot "pns_check_batch_commands.ps1") -ProjectRoot $ProjectRoot
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "BOOTSTRAP VALIDATE: PASS (template map + labels + module scaffolds + batch commands)"
exit 0
