# Bootstrap watch-agent-gates equivalent — routes agent steps to P&S gate scripts.
#
# Usage:
#   .\scripts\pns_watch_agent_gates.ps1 -Step tier0
#   .\scripts\pns_watch_agent_gates.ps1 -Step tier2
#   .\scripts\pns_watch_agent_gates.ps1 -Once -Step tier0 -Autofix
#   .\scripts\pns_watch_agent_gates.ps1 -Help

param(
  [ValidateSet("tier0", "tier1", "tier2", "usb-capture", "usb-chrome", "bootstrap")]
  [string]$Step = "tier0",
  [switch]$Once,
  [switch]$Autofix,
  [switch]$Help,
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ($Help) {
  @"
pns_watch_agent_gates.ps1 — bootstrap gate router (PowerShell)

  -Step tier0       -> pns_local_dev_parallel.ps1
  -Step bootstrap    -> pns_validate_bootstrap.ps1 only
  -Step tier1       -> pns_prerelease_gate.ps1 -SkipGradle
  -Step tier2       -> pns_verify_toolchain.ps1 -RunTests
  -Step usb-capture -> pns_capture_pipeline_verify.ps1
  -Step usb-chrome  -> pns_chrome_ux_gate.ps1

  Batch slash commands: docs/BATCH_COMMANDS.md (25 commands; /verify, /ship, …)

  -Autofix          -> run pns_changelog_gate.ps1 before tier0 (safe doc drift only)
  -Once             -> single invocation (default; no watch loop)
"@
  exit 0
}

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

Push-Location $ProjectRoot
try {
  if ($Autofix) {
    Write-Host "watch-agent-gates: autofix changelog coverage"
    & (Join-Path $PSScriptRoot "pns_changelog_gate.ps1") -ProjectRoot $ProjectRoot
    if ($LASTEXITCODE -ne 0) {
      Write-Host "watch-agent-gates: autofix could not fix changelog (exit $LASTEXITCODE)"
      exit $LASTEXITCODE
    }
  }

  switch ($Step) {
    "bootstrap" {
      & (Join-Path $PSScriptRoot "pns_validate_bootstrap.ps1") -ProjectRoot $ProjectRoot
    }
    "tier0" {
      & (Join-Path $PSScriptRoot "pns_local_dev_parallel.ps1") -ProjectRoot $ProjectRoot
    }
    "tier1" {
      & (Join-Path $PSScriptRoot "pns_prerelease_gate.ps1") -SkipGradle
    }
    "tier2" {
      & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -RunTests
    }
    "usb-capture" {
      & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1")
    }
    "usb-chrome" {
      & (Join-Path $PSScriptRoot "pns_chrome_ux_gate.ps1")
    }
  }

  $code = $LASTEXITCODE
  if ($null -eq $code) { $code = 0 }
  if ($code -ne 0) {
    Write-Host "watch-agent-gates: FAIL step=$Step exit=$code"
    exit $code
  }
  Write-Host "watch-agent-gates: PASS step=$Step"
  exit 0
} finally {
  Pop-Location
}
