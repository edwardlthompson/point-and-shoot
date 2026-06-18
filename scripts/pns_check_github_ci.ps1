# Poll GitHub Actions for required P&S workflows on a commit.
#
# Usage:
#   .\scripts\pns_check_github_ci.ps1
#   .\scripts\pns_check_github_ci.ps1 -WaitSeconds 300

param(
  [string]$Ref = "",
  [int]$WaitSeconds = 0
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $Root

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
  Write-Host "ERROR: gh CLI required (https://cli.github.com/)"
  exit 1
}

if (-not $Ref) { $Ref = "HEAD" }
$Ref = (git rev-parse $Ref).Trim()

$Required = @("Toolchain verify", "Security scan", "CodeQL")

$RepoJson = gh repo view --json nameWithOwner 2>$null
if (-not $RepoJson) {
  Write-Host "ERROR: run from a git repo with gh auth"
  exit 1
}
$Repo = (ConvertFrom-Json $RepoJson).nameWithOwner
$ShortRef = $Ref.Substring(0, [Math]::Min(7, $Ref.Length))
Write-Host "GitHub Actions status for $Repo @ $ShortRef"

$deadline = (Get-Date).AddSeconds($WaitSeconds)
while ($true) {
  $runs = gh run list --repo $Repo --commit $Ref --json workflowName,conclusion,status,url | ConvertFrom-Json
  $pending = 0
  $failed = 0

  foreach ($wf in $Required) {
    $wfRuns = @($runs | Where-Object { $_.workflowName -eq $wf })
    if ($wfRuns.Count -eq 0) {
      Write-Host "WAIT ${wf}: no run yet"
      $pending++
      continue
    }
    $run = $wfRuns | Where-Object { $_.conclusion -eq "success" } | Select-Object -First 1
    if (-not $run) {
      $run = $wfRuns | Where-Object { $_.status -ne "completed" } | Select-Object -First 1
    }
    if (-not $run) {
      $run = $wfRuns | Select-Object -First 1
    }
    switch ($run.conclusion) {
      "success" { Write-Host "OK   ${wf}: $($run.url)" }
      { $_ -in @("failure", "cancelled", "timed_out", "action_required") } {
        Write-Host "FAIL ${wf} ($($run.conclusion)): $($run.url)"
        $failed++
      }
      default {
        if ($run.status -eq "completed") {
          Write-Host "FAIL ${wf} ($($run.conclusion)): $($run.url)"
          $failed++
        } else {
          Write-Host "WAIT ${wf} ($($run.status)): $($run.url)"
          $pending++
        }
      }
    }
  }

  if ($failed -gt 0) {
    Write-Host "$failed required workflow(s) failed on GitHub"
    exit 1
  }
  if ($pending -eq 0) {
    Write-Host "All required GitHub checks passed"
    exit 0
  }
  if ($WaitSeconds -eq 0 -or (Get-Date) -ge $deadline) {
    Write-Host "INCOMPLETE: $pending workflow(s) still pending (use -WaitSeconds 300)"
    exit 1
  }
  Start-Sleep -Seconds 15
}
