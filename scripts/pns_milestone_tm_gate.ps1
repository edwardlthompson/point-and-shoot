# Milestone TM — Template Migration closure gate.
#
# Runs bootstrap validate + Tier 0 + Tier 2 (full Gradle tests). Optional USB smoke when device online.
#
# Usage:
#   .\scripts\pns_milestone_tm_gate.ps1
#   .\scripts\pns_milestone_tm_gate.ps1 -IncludeUsb
#   .\scripts\pns_milestone_tm_gate.ps1 -HostOnly

param(
  [string]$ProjectRoot = "",
  [switch]$IncludeUsb,
  [switch]$HostOnly
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
  Write-Host "== milestone_tm: $Name =="
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
  Write-Host "MILESTONE TM GATE — $ProjectRoot"

  Invoke-MilestoneStep "bootstrap_validate" {
    & (Join-Path $PSScriptRoot "pns_validate_bootstrap.ps1") -ProjectRoot $ProjectRoot
  }

  Invoke-MilestoneStep "local_dev_parallel_tier0" {
    & (Join-Path $PSScriptRoot "pns_local_dev_parallel.ps1") -ProjectRoot $ProjectRoot
  }

  Invoke-MilestoneStep "verify_toolchain_runtests" {
    & (Join-Path $PSScriptRoot "pns_verify_toolchain.ps1") -RunTests
  }

  $usbRan = $false
  if ($IncludeUsb -and -not $HostOnly) {
    $usbRan = $true
    Invoke-MilestoneStep "usb_capture_pipeline" {
      & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1")
    }
    Invoke-MilestoneStep "usb_chrome_ux" {
      & (Join-Path $PSScriptRoot "pns_chrome_ux_gate.ps1")
    }
  } elseif (-not $HostOnly) {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if ($adb) {
      $devices = (& adb devices 2>$null | Select-String "device$" | Where-Object { $_ -notmatch "List of devices" })
      if ($devices.Count -ge 1) {
        $usbRan = $true
        Write-Host "milestone_tm: device online — running USB smoke (capture then chrome)"
        Invoke-MilestoneStep "usb_capture_pipeline" {
          & (Join-Path $PSScriptRoot "pns_capture_pipeline_verify.ps1")
        }
        Invoke-MilestoneStep "usb_chrome_ux" {
          & (Join-Path $PSScriptRoot "pns_chrome_ux_gate.ps1")
        }
      } else {
        Write-Host "milestone_tm: no USB device — skipping capture/chrome (use -IncludeUsb when device ready)"
      }
    }
  }

  $totalSec = [int]((Get-Date) - $started).TotalSeconds
  $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $outDir = Join-Path $ProjectRoot "hfr-runs\milestone_tm_gate_$stamp"
  New-Item -ItemType Directory -Path $outDir -Force | Out-Null
  $report = [ordered]@{
    milestone      = "TM"
    closedAgentLane = (-not $failed)
    utc            = (Get-Date).ToUniversalTime().ToString("o")
    totalSeconds   = $totalSec
    usbSmokeRan    = $usbRan
    deferred       = @(
      "Full PreviewEngineScreen / RawCaptureSupport / fleet builder extraction → post-TM interface sprint (ADR-0009)"
    )
    steps          = $steps
  }
  $jsonPath = Join-Path $outDir "milestone_tm_gate.json"
  ($report | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $jsonPath -Encoding utf8NoBOM

  Write-Host ""
  if ($failed) {
    Write-Host "MILESTONE TM GATE: FAIL (${totalSec}s) — see $jsonPath"
    exit 1
  }
  Write-Host "MILESTONE TM GATE: PASS (agent lane, ${totalSec}s)"
  Write-Host "Artifact: $jsonPath"
  if (-not $usbRan) {
    Write-Host "Note: USB smoke skipped — run with -IncludeUsb before ship if capture/preview modules change"
  }
  exit 0
} finally {
  Pop-Location
}
