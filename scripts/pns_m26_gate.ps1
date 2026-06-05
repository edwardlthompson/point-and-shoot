param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipInstall,
    [switch]$SkipVerifyScripts,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_m26_gate.ps1 — Milestone 26 parity closure gate

Host:
  - VideoDeliveryHonestyTest + parity debt/intake refresh scripts
  - pns_parity_proof_pack.ps1 -HostOnly

USB (CPH2583 default):
  - Top-3 AppFeature proof scripts (audio.spatial, still.jxl, still.motion_photo)
  - pns_fleet_parity_sweep.ps1 -Mode Delta
  - Mark intake closed for proven top-3 rows; refresh debt ledger + intake

M26 gate: top 3 AppFeature proven on device + intake status=closed + parity Delta pass.
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $repoRoot "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

function Read-PnsSerial {
    param([string]$S)
    if ($S) { return $S }
    $envFile = Join-Path $repoRoot "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            if ($line -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim().Trim('"') }
        }
    }
    return ""
}

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $repoRoot "hfr-runs\m26_gate_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

$top3AppFeature = @(
    "audio.spatial",
    "still.jxl",
    "still.motion_photo"
)

$results = [ordered]@{
    schema = "pns.m26_gate.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $outDir
    top3AppFeature = $top3AppFeature
    steps = @()
}

function Add-Step([string]$Name, [int]$ExitCode, [string]$Note = "") {
    $step = [ordered]@{ name = $Name; exitCode = $ExitCode; pass = ($ExitCode -eq 0) }
    if ($Note) { $step.note = $Note }
    $results.steps += $step
}

& (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:testDebugUnitTest" "--tests" "dev.pointandshoot.VideoDeliveryHonestyTest" 2>&1 | Out-Null
Add-Step "unit_VideoDeliveryHonestyTest" $LASTEXITCODE

& (Join-Path $PSScriptRoot "pns_parity_proof_pack.ps1") -HostOnly
Add-Step "proof_pack_manifest_host" $LASTEXITCODE

& (Join-Path $PSScriptRoot "pns_parity_debt_ledger_refresh.ps1") -RunsRoot (Join-Path $repoRoot "hfr-runs")
Add-Step "debt_ledger_refresh_pre" $LASTEXITCODE

if ($HostOnly) {
    Add-Step "usb_verify_top3" 0 "skipped HostOnly"
    Add-Step "parity_delta" 0 "skipped HostOnly"
    Add-Step "top3_proven_check" 0 "skipped HostOnly"
    Add-Step "intake_close_top3" 0 "skipped HostOnly"
} else {
    $Serial = Read-PnsSerial $Serial
    if (-not $Serial) { throw "Set PNS_ADB_SERIAL in scripts/pns_adb_device.env or pass -Serial" }

    if (-not $SkipVerifyScripts) {
        $verifySteps = @(
            @{ name = "spatial_audio_verify"; script = "pns_spatial_audio_verify.ps1"; args = @{} },
            @{ name = "still_export_jxl"; script = "pns_still_export_verify.ps1"; args = @{ Format = "jxl" } },
            @{ name = "still_export_motion_photo"; script = "pns_still_export_verify.ps1"; args = @{ Format = "motion_photo" } }
        )
        foreach ($vs in $verifySteps) {
            $vArgs = @{ Serial = $Serial; OutDir = (Join-Path $outDir $vs.name) }
            if ($SkipInstall) { $vArgs.SkipInstall = $true; $vArgs.SkipAssemble = $true }
            foreach ($k in $vs.args.Keys) { $vArgs[$k] = $vs.args[$k] }
            & (Join-Path $PSScriptRoot $vs.script) @vArgs
            Add-Step $vs.name $LASTEXITCODE
        }
    } else {
        Add-Step "usb_verify_top3" 0 "SkipVerifyScripts"
    }

    $parityOut = Join-Path $outDir "parity_delta"
    $parityArgs = @{
        Serial = $Serial
        Mode = "Delta"
        OutDir = $parityOut
    }
    if ($SkipInstall) { $parityArgs.SkipInstall = $true }
    & (Join-Path $PSScriptRoot "pns_fleet_parity_sweep.ps1") @parityArgs
    $parityExit = $LASTEXITCODE

    $inAppPath = Join-Path $parityOut "in_app_parity_report.json"
    $reportPath = Join-Path $parityOut "parity_report.json"
    $top3Ok = $false
    $top3Notes = @()
    $deltaGateOk = $false
    if (Test-Path -LiteralPath $inAppPath) {
        $inApp = Get-Content -LiteralPath $inAppPath -Raw | ConvertFrom-Json
        $allProven = $true
        foreach ($id in $top3AppFeature) {
            $cell = @($inApp.cells | Where-Object { $_.catalogId -eq $id } | Select-Object -First 1)
            if ($cell -and $cell.provenOk -eq $true) {
                $top3Notes += "$id=provenOk"
            } else {
                $allProven = $false
                $fr = if ($cell) { $cell.failReason } else { "missing_cell" }
                $top3Notes += "$id=fail($fr)"
            }
        }
        $top3Ok = $allProven
        $shipBlockers = 0
        if (Test-Path -LiteralPath $reportPath) {
            $parityReport = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
            if ($parityReport.PSObject.Properties.Name -contains "shipBlockerGapCount") {
                $shipBlockers = [int]$parityReport.shipBlockerGapCount
            }
        }
        $cellCount = if ($inApp.cellCount) { [int]$inApp.cellCount } else { @($inApp.cells).Count }
        $deltaGateOk = $top3Ok -and ($shipBlockers -eq 0) -and ($inApp.schema -eq "pns.fleet_parity_sweep.v2") -and ($cellCount -ge 45)
    } else {
        $top3Notes += "missing in_app_parity_report.json"
    }
    Add-Step "parity_delta" $(if ($deltaGateOk) { 0 } else { 1 }) "scriptExit=$parityExit $($top3Notes -join '; ')"
    Add-Step "top3_proven_check" $(if ($top3Ok) { 0 } else { 1 }) ($top3Notes -join "; ")

    $intakePath = Join-Path $repoRoot "docs\FLEET_PARITY_BUILD_PLAN_INTAKE.json"
    if (Test-Path -LiteralPath $intakePath) {
        $intake = Get-Content -LiteralPath $intakePath -Raw | ConvertFrom-Json
        $closed = 0
        foreach ($row in @($intake.rows)) {
            if ($row.workType -eq "AppFeature" -and $top3AppFeature -contains [string]$row.catalogId -and $top3Ok) {
                $row.status = "closed"
                $closed++
            }
        }
        $intake.openCount = @($intake.rows | Where-Object { $_.status -eq "open" }).Count
        $intake.generatedUtc = [DateTime]::UtcNow.ToString("o")
        $intake.latestParitySerial = $Serial
        $intake.latestParityMode = "Delta"
        $intake | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $intakePath -Encoding utf8
        & (Join-Path $PSScriptRoot "pns_parity_build_plan_intake.ps1") -PriorIntakePath $intakePath
        Add-Step "intake_close_top3" $(if ($top3Ok -and $closed -ge 3) { 0 } else { 1 }) "closed=$closed"
    } else {
        Add-Step "intake_close_top3" 1 "missing intake json"
    }

    & (Join-Path $PSScriptRoot "pns_parity_debt_ledger_refresh.ps1") -RunsRoot (Join-Path $repoRoot "hfr-runs")
    Add-Step "debt_ledger_refresh_post" $LASTEXITCODE

    try {
        & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null
    } catch { }
}

$results.pass = -not ($results.steps | Where-Object { -not $_.pass })
$report = Join-Path $outDir "m26_gate.json"
$results | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $report -Encoding utf8
Write-Host "[m26_gate] pass=$($results.pass) -> $report"
if (-not $results.pass) { exit 1 }
exit 0
