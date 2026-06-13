# Milestone T — one-shot closure gate (host lane).
#
# Runs Tier 0 parallel host + prerelease host (SkipGradle). Does not require USB or full Gradle -RunTests
# (Detekt baseline debt is pre-existing; full ship still uses pns_prerelease_gate.ps1 without -SkipGradle).
#
# Usage:
#   .\scripts\pns_milestone_t_gate.ps1
#   .\scripts\pns_milestone_t_gate.ps1 -ProjectRoot C:\path\to\repo

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$started = Get-Date
$failed = $false
$steps = New-Object System.Collections.Generic.List[object]

function Invoke-MilestoneStep {
  param([string]$Name, [scriptblock]$Block)
  $t0 = Get-Date
  Write-Host ""
  Write-Host "== milestone_t: $Name =="
  & $Block
  $code = $LASTEXITCODE
  if ($null -eq $code) { $code = 0 }
  $sec = [int]((Get-Date) - $t0).TotalSeconds
  $ok = ($code -eq 0)
  if (-not $ok) { $script:failed = $true }
  $steps.Add([ordered]@{ name = $Name; exitCode = $code; seconds = $sec; ok = $ok }) | Out-Null
  if ($ok) { Write-Host "OK: $Name (${sec}s)" } else { Write-Host "FAIL: $Name (exit $code, ${sec}s)" }
}

Push-Location $ProjectRoot
try {
  Write-Host "MILESTONE T GATE (host lane) — $ProjectRoot"

  Invoke-MilestoneStep "local_dev_parallel_tier0" {
    & (Join-Path $PSScriptRoot "pns_local_dev_parallel.ps1") -ProjectRoot $ProjectRoot
  }

  Invoke-MilestoneStep "prerelease_host_skipgradle" {
    & (Join-Path $PSScriptRoot "pns_prerelease_gate.ps1") -SkipGradle
  }

  $totalSec = [int]((Get-Date) - $started).TotalSeconds
  $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $outDir = Join-Path $ProjectRoot "hfr-runs\milestone_t_gate_$stamp"
  New-Item -ItemType Directory -Path $outDir -Force | Out-Null
  $report = [ordered]@{
    milestone     = "T"
    closedAgentLane = (-not $failed)
    utc           = (Get-Date).ToUniversalTime().ToString("o")
    totalSeconds  = $totalSec
    humanDeferrals = @(
      "T.10 store copy creative review → Milestone H H.5"
      "Owner PRIVACY + metadata sign-off → Milestone H H.9"
    )
    steps         = $steps
  }
  $jsonPath = Join-Path $outDir "milestone_t_gate.json"
  ($report | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM

  Write-Host ""
  if ($failed) {
    Write-Host "MILESTONE T GATE: FAIL (${totalSec}s) — see $jsonPath"
    exit 1
  }
  Write-Host "MILESTONE T GATE: PASS (agent lane, ${totalSec}s)"
  Write-Host "Artifact: $jsonPath"
  Write-Host "Human carryover: store copy (H.5), PRIVACY sign-off (H.9)"
  exit 0
} finally {
  Pop-Location
}
