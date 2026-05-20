<#
.SYNOPSIS
  Host-only: write m13 lock bisect report template from current OnePlus13FleetPolicy JVM constants.

  USB: scripts/pns_m13_3e_lock_bisect.ps1 (see docs/M13_3E_LOCK_BISECT_RUNBOOK.md)

.EXAMPLE
  .\scripts\pns_m13_lock_bisect_host.ps1
#>
$ErrorActionPreference = "Stop"
$projRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\m13_lock_bisect_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$report = @"
# M13 lock bisect report (host template)

Generated: $ts UTC

## Shipped policy flags (Kotlin defaults)

| Lock | Policy API | Shipped value |
|------|------------|---------------|
| L9 | useWideLeafCalibrationForAuxDng | false |
| L9 | useProShotPureDngSave (leaf reconcile off) | true on OP13 |
| L3 | useOp13AsnReconcileOnly | false |
| L6 | useHalColorCalibrationReconcile | false |
| — | useProShotStillPrecapture | false |

## USB bisect (not run on host)

1. One lock per commit — order L2 → L3 → L6 → L4 → L5 → L7
2. ``pns_aux_dng_triage_focal_slots.ps1`` per slot
3. ``pns_aux_dng_capture_analyze.ps1 -PreviewDial A -NoFast``
4. Human ACR 3/3 + ``dng_desktop_open_gate.py`` PASS
5. Append row to ``docs/REVERTED_FEATURES_RESTORE_LIST.md`` §9

## Evidence rows

| Step | Lock | Result | Artifact |
|------|------|--------|----------|
| E1 | L2 | pending USB | |
| E2 | L3 | pending USB | |
| … | | | |

"@

$reportPath = Join-Path $outDir "report.md"
$report | Set-Content $reportPath -Encoding UTF8
Write-Host "Wrote $reportPath" -ForegroundColor Green
