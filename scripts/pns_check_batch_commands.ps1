# Verify batch command registry matches .cursor/commands/*.md and super chains.
#
# Usage:
#   .\scripts\pns_check_batch_commands.ps1

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$atomic = @(
  "audit", "debug", "gates", "triage", "dependabot", "push", "prerelease", "regress",
  "feature", "fix", "init", "prune", "ci", "docs", "upgrade", "setup", "plan", "restore", "compact", "scope"
)
$super = @("bootstrap", "verify", "build", "ship", "maintain")
$superChains = @{
  bootstrap = @("init", "prune", "setup", "gates")
  verify    = @("docs", "gates", "ci")
  build     = @("plan", "feature", "gates")
  ship      = @("prerelease", "push", "regress")
  maintain  = @("triage", "dependabot", "audit")
}

$errors = 0
$commandsDir = Join-Path $ProjectRoot ".cursor\commands"

function Test-CommandFile {
  param([string]$Name)
  $path = Join-Path $commandsDir "$Name.md"
  if (-not (Test-Path -LiteralPath $path)) {
    Write-Host "MISSING: .cursor/commands/$Name.md"
    $script:errors++
  }
}

foreach ($cmd in ($atomic + $super)) {
  Test-CommandFile $cmd
}

if (Test-Path -LiteralPath $commandsDir) {
  Get-ChildItem -LiteralPath $commandsDir -Filter "*.md" -File | ForEach-Object {
    $base = [System.IO.Path]::GetFileNameWithoutExtension($_.Name)
    if ($base -notin ($atomic + $super)) {
      Write-Host "ORPHAN: $($_.FullName) (not in registry)"
      $script:errors++
    }
  }
}

foreach ($superName in $super) {
  foreach ($child in $superChains[$superName]) {
    $childPath = Join-Path $commandsDir "$child.md"
    if (-not (Test-Path -LiteralPath $childPath)) {
      Write-Host "SUPER_CHAIN: $superName references missing child $child"
      $script:errors++
    }
  }
}

$required = @(
  ".cursor\rules\batch-commands.mdc",
  "docs\BATCH_COMMANDS.md",
  "docs\help\BATCH_COMMANDS.md",
  "CODE_REVIEW.md.example",
  "RELEASE_NOTES.md.example"
)
foreach ($rel in $required) {
  if (-not (Test-Path -LiteralPath (Join-Path $ProjectRoot $rel))) {
    Write-Host "MISSING: $rel"
    $script:errors++
  }
}

$expected = $atomic.Count + $super.Count
$actual = 0
if (Test-Path -LiteralPath $commandsDir) {
  $actual = @(Get-ChildItem -LiteralPath $commandsDir -Filter "*.md" -File).Count
}
if ($actual -ne $expected) {
  Write-Host "COUNT: expected $expected command files, found $actual"
  $script:errors++
}

if ($errors -gt 0) {
  Write-Host "BATCH COMMANDS: FAIL ($errors issue(s))"
  exit 1
}

Write-Host "BATCH COMMANDS: PASS ($expected files: $($atomic.Count) atomic + $($super.Count) super)"
exit 0
