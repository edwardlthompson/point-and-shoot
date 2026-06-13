# Point & Shoot - Tier 0 local parallel host gates (no Gradle).
#
# Runs independent host scripts concurrently (PowerShell 7+ -Parallel).
# Use while editing; before commit run pns_prerelease_gate.ps1 -SkipGradle or -RunTests tier.
#
# Usage:
#   .\scripts\pns_local_dev_parallel.ps1
#   .\scripts\pns_local_dev_parallel.ps1 -ProjectRoot C:\path\to\repo

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

$useParallel = ($PSVersionTable.PSVersion.Major -ge 7)
if (-not $useParallel) {
  Write-Host "NOTE: PowerShell 5.1 - running Tier 0 gates sequentially (install PS7 for parallel)"
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$started = Get-Date

$steps = @(
  @{ Name = "changelog_gate";     Script = "pns_changelog_gate.ps1" }
  @{ Name = "template_doc_links"; Script = "pns_template_doc_link_check.ps1" }
  @{ Name = "perf_budget_host";   Script = "pns_perf_budget_host_gate.ps1" }
  @{ Name = "license_inventory";  Script = "pns_license_inventory.ps1" }
  @{ Name = "fdroid_metadata";    Script = "pns_fdroid_metadata_validate.ps1" }
  @{ Name = "repro_build_verify";  Script = "pns_repro_build_verify.ps1" }
  @{ Name = "fixture_dng_gates";  Script = "pns_fixture_dng_gates.ps1" }
)

foreach ($step in $steps) {
  $scriptPath = Join-Path $PSScriptRoot $step.Script
  if (-not (Test-Path -LiteralPath $scriptPath)) {
    Write-Host "FAIL: missing script $($step.Script)"
    exit 1
  }
  $step.ScriptPath = $scriptPath
}

$modeLabel = if ($useParallel) { "parallel" } else { "sequential (PS5.1)" }
Write-Host "LOCAL PARALLEL HOST (Tier 0): $($steps.Count) jobs ($modeLabel), no Gradle"
Write-Host "ProjectRoot: $ProjectRoot"
Write-Host ""

if ($useParallel) {
  $results = $steps | ForEach-Object -Parallel {
    $root = $using:ProjectRoot
    $name = $_.Name
    $path = $_.ScriptPath
    Set-Location $root
    if ($name -eq "fixture_dng_gates") {
      $out = & $path 2>&1 | Out-String
    } else {
      $out = & $path -ProjectRoot $root 2>&1 | Out-String
    }
    $code = $LASTEXITCODE
    if ($null -eq $code) { $code = 0 }
    [pscustomobject]@{
      Name   = $name
      Exit   = $code
      Output = $out.Trim()
    }
  } -ThrottleLimit 7
} else {
  $results = foreach ($step in $steps) {
    Set-Location $ProjectRoot
    if ($step.Name -eq "fixture_dng_gates") {
      $out = & $step.ScriptPath 2>&1 | Out-String
    } else {
      $out = & $step.ScriptPath -ProjectRoot $ProjectRoot 2>&1 | Out-String
    }
    $code = $LASTEXITCODE
    if ($null -eq $code) { $code = 0 }
    [pscustomobject]@{
      Name   = $step.Name
      Exit   = $code
      Output = $out.Trim()
    }
  }
}

$failed = @()
foreach ($r in $results) {
  if ($r.Exit -ne 0) {
    $failed += $r.Name
    Write-Host "FAIL: $($r.Name) (exit $($r.Exit))"
    if ($r.Output) {
      $tail = ($r.Output -split "`n" | Select-Object -Last 5) -join "`n"
      Write-Host $tail
    }
  } else {
    $lastLine = ($r.Output -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 1)
    Write-Host "OK: $($r.Name) - $lastLine"
  }
}

$elapsed = [int]((Get-Date) - $started).TotalSeconds
Write-Host ""
if ($failed.Count -gt 0) {
  Write-Host "LOCAL PARALLEL HOST: FAIL ($($failed.Count)/$($steps.Count)) in ${elapsed}s - $($failed -join ', ')"
  exit 1
}

Write-Host "LOCAL PARALLEL HOST: PASS ($($steps.Count)/$($steps.Count)) in ${elapsed}s"
exit 0
