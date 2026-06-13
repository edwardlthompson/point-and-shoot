# Bootstrap or remove an isolated git worktree for parallel Cursor agents.
#
# Usage:
#   .\scripts\pns_agent_worktree_bootstrap.ps1 -TaskSlug fleet-docs -Create
#   .\scripts\pns_agent_worktree_bootstrap.ps1 -TaskSlug fleet-docs -Remove
#   .\scripts\pns_agent_worktree_bootstrap.ps1 -List
#
# Branch: feature/agent-<TaskSlug>  (TaskSlug: lowercase letters, digits, hyphens)

param(
  [string]$TaskSlug = "",
  [switch]$Create,
  [switch]$Remove,
  [switch]$List
)

$ErrorActionPreference = "Stop"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $repoRoot
try {
  if ($List) {
    git worktree list
    exit 0
  }

  if ([string]::IsNullOrWhiteSpace($TaskSlug)) {
    Write-Host "Usage: -TaskSlug <slug> -Create | -Remove | -List"
    exit 2
  }

  if ($TaskSlug -notmatch '^[a-z0-9]+(-[a-z0-9]+)*$') {
    Write-Host "FAIL: TaskSlug must be lowercase kebab-case (e.g. fleet-matrix-docs)"
    exit 1
  }

  $branch = "feature/agent-$TaskSlug"
  $parent = Split-Path -Parent $repoRoot
  $repoName = Split-Path -Leaf $repoRoot
  $wtPath = Join-Path $parent "$repoName-wt-$TaskSlug"

  if ($Create) {
    if (Test-Path -LiteralPath $wtPath) {
      Write-Host "FAIL: worktree path already exists: $wtPath"
      exit 1
    }
    $baseRef = "main"
    $null = git rev-parse --verify main 2>$null
    if ($LASTEXITCODE -ne 0) { $baseRef = "master" }

    git branch $branch $baseRef 2>$null
    if ($LASTEXITCODE -ne 0) {
      git checkout $branch 2>$null
      if ($LASTEXITCODE -ne 0) {
        Write-Host "FAIL: could not create or checkout branch $branch"
        exit 1
      }
      git checkout $baseRef
    }

    git worktree add $wtPath $branch
    if ($LASTEXITCODE -ne 0) {
      Write-Host "FAIL: git worktree add $wtPath $branch"
      exit 1
    }

    Write-Host "WORKTREE READY"
    Write-Host "  Path:   $wtPath"
    Write-Host "  Branch: $branch"
    Write-Host "  Open this path in Cursor / Cloud VM for the parallel agent."
    Write-Host "  Gates:  .\scripts\pns_local_dev_parallel.ps1 (host) in that worktree"
    exit 0
  }

  if ($Remove) {
    if (Test-Path -LiteralPath $wtPath) {
      git worktree remove $wtPath --force
    } else {
      Write-Host "WARN: worktree path not found: $wtPath"
    }
    git branch -D $branch 2>$null
    Write-Host "Removed worktree (if present) and branch $branch"
    exit 0
  }

  Write-Host "Specify -Create or -Remove"
  exit 2
} finally {
  Pop-Location
}
