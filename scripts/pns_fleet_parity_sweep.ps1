# Milestone 21 — Fleet Parity Sweep (honest v2 reports).
#
# Pulls in-app parity_report_{mode}.json via run-as; logcat is fallback only.
# Artifacts: hfr-runs/parity_sweep_*/parity_report.json + parity_closure_plan.md + parity_ship_blockers.md

param(
    [string]$Serial = "",
    [ValidateSet("Quick", "Full", "Delta")]
    [string]$Mode = "",
    [string]$OutDir = "",
    [switch]$IncludeRecord,
    [switch]$IncludeProofPack,
    [switch]$IncludeDngSubTrack,
    [switch]$IncludeWorkflowPresets,
    [switch]$SkipMatrixRefresh,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$Interactive,
    [switch]$HostOnlyFixture,
    [switch]$HostProofPackMergeFixture,
    [string]$BaselineTag = "",
    [string]$BaselineJson = "",
    [string]$CompareMatrix = "",
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_fleet_parity_sweep.ps1 — Fleet Parity Sweep (M21)

  -Mode Quick|Full|Delta   (required unless -HostOnlyFixture)
  -SkipMatrixRefresh        skip pns_fleet_matrix_scan (avoids hub hang)
  -IncludeRecord            pass pns_parity_sweep_include_record to app
  -IncludeProofPack         Full only: run parity_proof_manifest scripts + merge provenOk (M22)
  -HostOnlyFixture          parse bundled sample logcat (no device)
  -HostProofPackMergeFixture  run host-only proof-pack merge fixture (no device)

Artifacts: parity_report.json (host wrapper), in_app_parity_report.json, gapBreakdown, parity_closure_plan.md
"@
    exit 0
}

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$activity = "$pkg/.MainActivity"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$gradlewHelper = Join-Path $PSScriptRoot "pns_gradlew.ps1"
$matrixScan = Join-Path $PSScriptRoot "pns_fleet_matrix_scan.ps1"

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") { return $t.Substring($eq + 1).Trim() }
    }
    return $null
}

function Parse-ParityCellLine([string]$Line) {
    if ($Line -notmatch 'parityCell=catalogId=(\S+)\s+advertised=(\w+)\s+sessionOk=(\S+)\s+appEnabled=(\w+)\s+provenOk=(\w+)') {
        return $null
    }
    return [ordered]@{
        catalogId = $Matches[1]
        advertised = ($Matches[2] -eq 'true')
        sessionOk = $Matches[3]
        appEnabled = ($Matches[4] -eq 'true')
        provenOk = ($Matches[5] -eq 'true')
        gap = if ($Line -match '\sgap=(\S+)') { $Matches[1] } else { $null }
        impact = if ($Line -match '\simpact=(\S+)') { $Matches[1] } else { $null }
        failReason = if ($Line -match '\sfailReason=(\S*)') { $Matches[1] } else { $null }
        durationMs = if ($Line -match '\sdurationMs=(\d+)') { [int]$Matches[1] } else { 0 }
    }
}

function Parse-LogcatParityCells([string]$LogPath) {
    $seen = @{}
    $cells = @()
    foreach ($line in Get-Content -LiteralPath $LogPath -ErrorAction SilentlyContinue) {
        $parsed = Parse-ParityCellLine $line
        if (-not $parsed) { continue }
        if ($seen.ContainsKey($parsed.catalogId)) { continue }
        $seen[$parsed.catalogId] = $true
        $cells += $parsed
    }
    return $cells
}

function Build-GapBreakdownFromLogcat($Cells) {
    $counts = @{}
    foreach ($c in $Cells) {
        $gap = if ($c.gap) { $c.gap } elseif (-not $c.provenOk -and $c.advertised) { 'GAP_ADVERTISED_NOT_PROVEN' } else { 'OK' }
        if ($gap -eq 'OK' -or $gap -eq 'GAP_PROBE_INVENTORY' -or $gap -eq 'GAP_PLANNED') { continue }
        if (-not $counts.ContainsKey($gap)) { $counts[$gap] = 0 }
        $counts[$gap]++
    }
    return $counts
}

function Write-ClosurePlanFromJson($InAppJson, [string]$Path) {
    $lines = @("# Parity closure plan", "")
    $cells = @($InAppJson.cells)
    if ($cells.Count -eq 0) {
        $lines += "- No in-app cells - check run-as pull"
    } else {
        foreach ($c in $cells) {
            if ($c.provenOk -eq $true) { continue }
            $id = $c.catalogId
            $gap = if ($c.gap) { $c.gap } else { 'review' }
            $impact = if ($c.impact) { $c.impact } elseif ($c.consumerImpact) { $c.consumerImpact } else { 'SHIP_BLOCKER' }
            $reason = if ($c.failReason) { $c.failReason } else { 'review' }
            $lines += "- **$id** ($gap, $impact) - $reason"
        }
    }
    if ($lines.Count -le 2) { $lines += "- No gaps - parity OK" }
    $lines | Set-Content -LiteralPath $Path -Encoding utf8
}

function Export-M21Artifacts($InAppObj, [string]$ArtifactDir) {
    if (-not $InAppObj) { return }
    if ($InAppObj.encoderCrossCheck) {
        $InAppObj.encoderCrossCheck | ConvertTo-Json -Depth 8 |
            Set-Content -LiteralPath (Join-Path $ArtifactDir "parity_encoder_crosscheck.json") -Encoding utf8
    }
    if ($InAppObj.sessionTemplates) {
        $InAppObj.sessionTemplates | ConvertTo-Json -Depth 8 |
            Set-Content -LiteralPath (Join-Path $ArtifactDir "parity_session_templates.json") -Encoding utf8
    }
    if ($InAppObj.conflictPairs) {
        $InAppObj.conflictPairs | ConvertTo-Json -Depth 8 |
            Set-Content -LiteralPath (Join-Path $ArtifactDir "parity_conflict_risks.json") -Encoding utf8
    }
    $surfacing = @($InAppObj.cells | Where-Object { $_.failReason -match 'surfaced|not_surfaced' })
    if ($surfacing.Count -gt 0) {
        $surfacing | ConvertTo-Json -Depth 6 |
            Set-Content -LiteralPath (Join-Path $ArtifactDir "parity_surfacing_audit.json") -Encoding utf8
    }
    $presets = @($InAppObj.cells | Where-Object { $_.catalogId -like 'workflow.preset.*' })
    if ($presets.Count -gt 0) {
        $presets | ConvertTo-Json -Depth 6 |
            Set-Content -LiteralPath (Join-Path $ArtifactDir "parity_workflow_presets.json") -Encoding utf8
    }
    $historyPath = Join-Path (Split-Path -Parent $PSScriptRoot) "docs\FLEET_PARITY_HISTORY.jsonl"
    if (Test-Path -LiteralPath $historyPath) {
        $lines = Get-Content -LiteralPath $historyPath -Tail 20
        $failures = ($lines | Where-Object { $_ -match '"pass"\s*:\s*false' }).Count
        $flake = [ordered]@{ sampleSize = $lines.Count; recentFailures = $failures; flakeScore = [math]::Round($failures / [math]::Max(1, $lines.Count), 3) }
        $flake | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ArtifactDir "parity_flake_score.json") -Encoding utf8
    }
}

function Write-ShipBlockersMd($InAppJson, [string]$Path) {
    $lines = @("# Parity ship blockers", "")
    $blockers = @($InAppJson.cells | Where-Object {
            $_.consumerImpact -eq 'SHIP_BLOCKER' -and
            ($_.gap -in @('GAP_ADVERTISED_NOT_PROVEN', 'GAP_DELIVERY_MISMATCH', 'GAP_REGRESSION_SINCE_BASELINE') -or
                (-not $_.provenOk -and $_.advertised -eq $true))
        })
    if ($blockers.Count -eq 0) {
        $lines += "- None (ship_blocker blocking gaps = 0)"
    } else {
        foreach ($b in $blockers) {
            $lines += "- **$($b.catalogId)** gap=$($b.gap) reason=$($b.failReason)"
        }
    }
    $lines | Set-Content -LiteralPath $Path -Encoding utf8
}

function Get-ThermalSnapshot([string]$Label) {
    $snap = [ordered]@{ label = $Label; timestampUtc = [DateTime]::UtcNow.ToString("o") }
    try {
        $adbArgs = @()
        if ($Serial) { $adbArgs += "-s", $Serial }
        $ts = & adb @adbArgs shell dumpsys thermalservice 2>$null | Out-String
        $snap.thermalservice = ($ts -split "`n" | Select-Object -First 12) -join "`n"
        $status = & adb @adbArgs shell dumpsys battery 2>$null | Select-String "status|level|temperature"
        $snap.battery = ($status | ForEach-Object { $_.Line }) -join "; "
    } catch {
        $snap.error = $_.Exception.Message
    }
    return $snap
}

function Write-ThermalCostMd($Before, $After, [string]$Path) {
    $lines = @(
        "# Parity thermal cost (IncludeRecord)",
        "",
        "## Before record",
        "``````",
        $Before.thermalservice,
        "``````",
        "",
        "## After record",
        "``````",
        $After.thermalservice,
        "``````",
        "",
        "- **Battery before:** $($Before.battery)",
        "- **Battery after:** $($After.battery)",
        "- **thermalCostTier:** informational (compare thermalservice head above)"
    )
    $lines | Set-Content -LiteralPath $Path -Encoding utf8
}

function Test-FfprobeDelivery([string]$LocalPath, [int]$TargetFps, [int]$Width, [int]$Height) {
    $result = [ordered]@{
        requestedWidth = $Width
        requestedHeight = $Height
        requestedFps = $TargetFps
        matchOk = $false
        mismatchReason = $null
    }
    if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
        $result.mismatchReason = "ffprobe_missing"
        return $result
    }
    if (-not (Test-Path -LiteralPath $LocalPath)) {
        $result.mismatchReason = "clip_missing"
        return $result
    }
    $vw = (& ffprobe -v error -select_streams v:0 -show_entries stream=width -of default=nw=1:nk=1 $LocalPath 2>&1) -join ""
    $vh = (& ffprobe -v error -select_streams v:0 -show_entries stream=height -of default=nw=1:nk=1 $LocalPath 2>&1) -join ""
    $fpsRaw = (& ffprobe -v error -select_streams v:0 -show_entries stream=avg_frame_rate -of default=nw=1:nk=1 $LocalPath 2>&1) -join ""
    if ($fpsRaw -eq "0/0" -or [string]::IsNullOrWhiteSpace($fpsRaw)) {
        $fpsRaw = (& ffprobe -v error -select_streams v:0 -show_entries stream=r_frame_rate -of default=nw=1:nk=1 $LocalPath 2>&1) -join ""
    }
    $fpsVal = 0.0
    if ($fpsRaw -match "^(\d+)/(\d+)$") {
        $den = [double]$Matches[2]
        if ($den -gt 0) { $fpsVal = [double]$Matches[1] / $den }
    }
    $result.actualWidth = [int]$vw
    $result.actualHeight = [int]$vh
    $result.actualFps = $fpsVal
    $resOk = ([int]$vw -eq $Width) -and ([int]$vh -eq $Height)
    $fpsTol = if ($TargetFps -ge 120) { $TargetFps * 0.75 } else { 3.0 }
    $fpsOk = if ($TargetFps -ge 120) { $fpsVal -ge $fpsTol } else { [math]::Abs($fpsVal - $TargetFps) -le $fpsTol }
    $result.matchOk = $resOk -and $fpsOk
    if (-not $result.matchOk) {
        if (-not $resOk) { $result.mismatchReason = "resolution_mismatch" }
        elseif ($fpsVal -lt ($TargetFps - $fpsTol)) { $result.mismatchReason = "fps_low" }
        else { $result.mismatchReason = "fps_high" }
    }
    return $result
}

function Invoke-ParityDeliveryVerify([string]$ArtifactDir) {
    $deliveryDir = Join-Path $ArtifactDir "delivery_verify"
    New-Item -ItemType Directory -Force -Path $deliveryDir | Out-Null
    $thermalBefore = Get-ThermalSnapshot "before_record"
    $verifyArgs = @{ SkipInstall = $true; SkipAssemble = $true; RecordSec = 8; WaitSec = 65 }
    if ($Serial) { $verifyArgs.Serial = $Serial }
    & (Join-Path $PSScriptRoot "pns_in_app_video_verify.ps1") @verifyArgs
    $verifyOk = ($LASTEXITCODE -eq 0)
    $thermalAfter = Get-ThermalSnapshot "after_record"
    Write-ThermalCostMd $thermalBefore $thermalAfter (Join-Path $ArtifactDir "parity_thermal_cost.md")
    $thermalBefore | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ArtifactDir "thermal_before.json") -Encoding utf8
    $thermalAfter | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $ArtifactDir "thermal_after.json") -Encoding utf8

    $clipPath = $null
    $adbArgs = @()
    if ($Serial) { $adbArgs += "-s", $Serial }
    $findCmd = "find /sdcard/DCIM -name '*.mp4' -type f 2>/dev/null | head -5"
    $candidates = & adb @adbArgs shell $findCmd 2>$null
    if ($candidates) {
        $latest = ($candidates -split "`n" | Where-Object { $_.Trim().Length -gt 0 } | Select-Object -Last 1).Trim()
        if ($latest) {
            $localClip = Join-Path $deliveryDir "delivery_clip.mp4"
            try {
                $pullOut = & adb @adbArgs pull $latest $localClip 2>&1
                if ($LASTEXITCODE -ne 0) {
                    Write-Warning "delivery pull failed: $($pullOut -join ' ')"
                }
            } catch {
                Write-Warning "delivery pull failed: $($_.Exception.Message)"
            }
            if (Test-Path -LiteralPath $localClip) { $clipPath = $localClip }
        }
    }

    $probe = if ($clipPath) { Test-FfprobeDelivery $clipPath 30 1920 1080 } else {
        [ordered]@{ matchOk = $verifyOk; mismatchReason = if ($verifyOk) { "clip_not_pulled" } else { "record_failed" }; requestedFps = 30; requestedWidth = 1920; requestedHeight = 1080 }
    }
    $delivery = [ordered]@{
        schema = "pns.parity_delivery_verify.v1"
        verifyOk = $verifyOk
        probe = $probe
        clipPath = $clipPath
        tolerances = "30fps±3; HFR≥75% target (aligned with pns_mediacodec_hfr_verify.ps1)"
        timestampUtc = [DateTime]::UtcNow.ToString("o")
    }
    $delivery | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $ArtifactDir "delivery_mismatch.json") -Encoding utf8
    $md = @(
        "# Delivery verification",
        "",
        "- **Record verify:** $verifyOk",
        "- **Match OK:** $($probe.matchOk)",
        "- **Reason:** $($probe.mismatchReason)",
        "- **Requested:** 1920x1080 @ 30",
        "- **Actual:** $($probe.actualWidth)x$($probe.actualHeight) @ $($probe.actualFps)",
        ""
    )
    $md | Set-Content -LiteralPath (Join-Path $ArtifactDir "delivery_mismatch.md") -Encoding utf8
    return $delivery
}

function Get-CatalogStatusMap {
    $catalogKt = Join-Path (Split-Path -Parent $PSScriptRoot) "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalog.kt"
    $expansionKt = Join-Path (Split-Path -Parent $PSScriptRoot) "app\src\main\java\dev\pointandshoot\fleet\CameraCapabilityCatalogExpansion.kt"
    $map = @{}
    foreach ($path in @($catalogKt, $expansionKt)) {
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $text = Get-Content -LiteralPath $path -Raw
        [regex]::Matches($text, 'CatalogRow\s*\(\s*"([^"]+)"|row\s*\(\s*"([^"]+)"') | ForEach-Object {
            $id = if ($_.Groups[1].Value) { $_.Groups[1].Value } else { $_.Groups[2].Value }
            if ($id) { $map[$id] = "Shipped" }
        }
        [regex]::Matches($text, '(?:CatalogRow|row)\([^)]*id\s*=\s*"([^"]+)"[^)]*appStatus\s*=\s*AppStatus\.(\w+)') | ForEach-Object {
            $map[$_.Groups[1].Value] = $_.Groups[2].Value
        }
        [regex]::Matches($text, 'appStatus\s*=\s*AppStatus\.(\w+)[^)]*\)[^,]*,\s*[^)]*\)[^,]*,\s*"([^"]+)"') | ForEach-Object { }
    }
    return $map
}

function Classify-ParityGap($Cell, [string]$AppStatus) {
    if ($Cell.gap) { return $Cell.gap }
    if ($Cell.failReason -eq "unautomated") { return "GAP_UNAUTOMATED" }
    if ($Cell.failReason -eq "planned") { return "GAP_PLANNED" }
    if ($Cell.failReason -like "skip:probe_only_inventory*") { return "GAP_PROBE_INVENTORY" }
    if ($AppStatus -eq "Planned") { return "GAP_PLANNED" }
    if ($Cell.provenOk -eq $true) { return "OK" }
    if ($Cell.advertised -eq $true -and $Cell.provenOk -ne $true) { return "GAP_ADVERTISED_NOT_PROVEN" }
    if ($Cell.provenOk -eq $true -and $Cell.advertised -ne $true) { return "GAP_PROVEN_NOT_ADVERTISED" }
    return "OK"
}

function Build-GapBreakdownFromCells($Cells, $StatusMap) {
    $counts = @{}
    foreach ($c in $Cells) {
        $st = if ($StatusMap -and $StatusMap.ContainsKey($c.catalogId)) { $StatusMap[$c.catalogId] } else { "Shipped" }
        $gap = Classify-ParityGap $c $st
        if ($gap -in @("OK", "GAP_PROBE_INVENTORY", "GAP_HUMAN_ONLY", "GAP_FLEET_PLUGIN_CANDIDATE", "GAP_CONFLICT_RISK")) { continue }
        if (-not $counts.ContainsKey($gap)) { $counts[$gap] = 0 }
        $counts[$gap]++
    }
    return $counts
}

function Merge-ParityProofResults($InAppObj, [string]$ProofResultsPath, [string]$DeliveryPath) {
    if (-not $InAppObj -or -not (Test-Path -LiteralPath $ProofResultsPath)) { return $InAppObj }
    $proof = Get-Content -LiteralPath $ProofResultsPath -Raw | ConvertFrom-Json
    $proofById = @{}
    foreach ($r in @($proof.rows)) { $proofById[$r.catalogId] = $r }

    if ($DeliveryPath -and (Test-Path -LiteralPath $DeliveryPath)) {
        $delivery = Get-Content -LiteralPath $DeliveryPath -Raw | ConvertFrom-Json
        $proofById["video.delivery_honesty"] = [ordered]@{
            catalogId = "video.delivery_honesty"
            pass = ($delivery.probe.matchOk -eq $true)
            source = "delivery_mismatch"
        }
    }

    $cells = @($InAppObj.cells)
    foreach ($cell in $cells) {
        $id = $cell.catalogId
        if (-not $proofById.ContainsKey($id)) { continue }
        $pr = $proofById[$id]
        if ($pr.skippedReason -like "matrix_gate:*") {
            if ($cell.PSObject.Properties.Name -contains "provenOk") { $cell.provenOk = $true } else { $cell | Add-Member -NotePropertyName provenOk -NotePropertyValue $true -Force }
            if ($cell.PSObject.Properties.Name -contains "failReason") { $cell.failReason = $null } else { $cell | Add-Member -NotePropertyName failReason -NotePropertyValue $null -Force }
            if ($cell.PSObject.Properties.Name -contains "gap") { $cell.gap = "OK" } else { $cell | Add-Member -NotePropertyName gap -NotePropertyValue "OK" -Force }
            $cell | Add-Member -NotePropertyName proofSkipped -NotePropertyValue $pr.skippedReason -Force
            $cell | Add-Member -NotePropertyName proofMerged -NotePropertyValue $true -Force
            continue
        }
        if ($pr.pass -eq $true) {
            if ($cell.PSObject.Properties.Name -contains "provenOk") { $cell.provenOk = $true } else { $cell | Add-Member -NotePropertyName provenOk -NotePropertyValue $true -Force }
            if ($cell.PSObject.Properties.Name -contains "failReason") { $cell.failReason = $null } else { $cell | Add-Member -NotePropertyName failReason -NotePropertyValue $null -Force }
            if ($cell.PSObject.Properties.Name -contains "gap") { $cell.gap = "OK" } else { $cell | Add-Member -NotePropertyName gap -NotePropertyValue "OK" -Force }
            $cell | Add-Member -NotePropertyName proofMerged -NotePropertyValue $true -Force
        }
    }

    $statusMap = Get-CatalogStatusMap
    $gapBreakdown = Build-GapBreakdownFromCells $cells $statusMap
    if ($InAppObj.PSObject.Properties.Name -contains "cells") { $InAppObj.cells = $cells } else { $InAppObj | Add-Member -NotePropertyName cells -NotePropertyValue $cells -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "gapBreakdown") { $InAppObj.gapBreakdown = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapBreakdown -NotePropertyValue $gapBreakdown -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "gapCounts") { $InAppObj.gapCounts = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapCounts -NotePropertyValue $gapBreakdown -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "proofPackMerged") { $InAppObj.proofPackMerged = $true } else { $InAppObj | Add-Member -NotePropertyName proofPackMerged -NotePropertyValue $true -Force }
    return $InAppObj
}

function Write-BaselineDelta($CurrentObj, [string]$BaselinePath, [string]$OutPath) {
    if (-not (Test-Path -LiteralPath $BaselinePath)) { return }
    $base = Get-Content -LiteralPath $BaselinePath -Raw | ConvertFrom-Json
    $regressions = @()
    foreach ($cell in @($CurrentObj.cells)) {
        $id = $cell.catalogId
        $baseCell = @($base.cells | Where-Object { $_.catalogId -eq $id } | Select-Object -First 1)
        if ($baseCell -and $baseCell.provenOk -eq $true -and $cell.provenOk -eq $false) {
            $regressions += [ordered]@{ catalogId = $id; baselineProvenOk = $true; currentProvenOk = $false; gap = "GAP_REGRESSION_SINCE_BASELINE" }
        }
    }
    $delta = [ordered]@{
        schema = "pns.parity_regression_delta.v1"
        baselinePath = $BaselinePath
        regressionCount = $regressions.Count
        regressions = $regressions
        timestampUtc = [DateTime]::UtcNow.ToString("o")
    }
    $delta | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $OutPath -Encoding utf8
}

function Invoke-CompareMatrix([string]$CurrentMatrixPath, [string]$ComparePath, [string]$OutMarkdown) {
    if (-not (Test-Path -LiteralPath $CurrentMatrixPath)) {
        Write-Warning "CompareMatrix: missing current matrix at $CurrentMatrixPath"
        return
    }
    if (-not (Test-Path -LiteralPath $ComparePath)) {
        Write-Warning "CompareMatrix: missing compare path $ComparePath"
        return
    }
    & (Join-Path $PSScriptRoot "pns_fleet_matrix_diff.ps1") -PathA $ComparePath -PathB $CurrentMatrixPath -OutMarkdown $OutMarkdown
    $hints = @()
    $cur = Get-Content -LiteralPath $CurrentMatrixPath -Raw | ConvertFrom-Json
    $ref = Get-Content -LiteralPath $ComparePath -Raw | ConvertFrom-Json
    $curPolicy = $null
    $refPolicy = $null
    if ($null -ne $cur.product -and $null -ne $cur.product.fleetProfiles) { $curPolicy = $cur.product.fleetProfiles.policyId }
    if ($null -ne $ref.product -and $null -ne $ref.product.fleetProfiles) { $refPolicy = $ref.product.fleetProfiles.policyId }
    if ($curPolicy -ne $refPolicy) {
        $hints += "GAP_FLEET_PLUGIN_CANDIDATE: policyId changed '$refPolicy' -> '$curPolicy'"
    }
    if ($hints.Count -gt 0) {
        Add-Content -LiteralPath $OutMarkdown -Value ("", "## Fleet plugin hints", "", ($hints -join "`n"))
    }
}

if ($HostOnlyFixture) {
    $fixtureLog = Join-Path $projRoot "hfr-runs\parity_sweep_20260530_043424\logcat_parity.txt"
    if (-not (Test-Path -LiteralPath $fixtureLog)) {
        Write-Error "Missing fixture log at $fixtureLog"
        exit 1
    }
    $cells = Parse-LogcatParityCells $fixtureLog
    $breakdown = Build-GapBreakdownFromLogcat $cells
    $fixtureOut = Join-Path $projRoot "hfr-runs\parity_fixture_$(Get-Date -Format yyyyMMdd_HHmmss)"
    New-Item -ItemType Directory -Force -Path $fixtureOut | Out-Null
    $report = [ordered]@{
        schema = "pns.fleet_parity_fixture.v1"
        pass = ($cells.Count -gt 0)
        cellCount = $cells.Count
        gapBreakdown = $breakdown
        fixtureLog = $fixtureLog
    }
    $report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $fixtureOut "fixture_report.json") -Encoding utf8
    Write-Host "[parity_fixture] cells=$($cells.Count) breakdown=$($breakdown | ConvertTo-Json -Compress)"
    if ($cells.Count -lt 10) { exit 1 }
    exit 0
}

if ($HostProofPackMergeFixture) {
    $fixtureOut = Join-Path $projRoot "hfr-runs\parity_proof_merge_fixture_$(Get-Date -Format yyyyMMdd_HHmmss)"
    New-Item -ItemType Directory -Force -Path $fixtureOut | Out-Null

    $inAppFixture = [ordered]@{
        schema = "pns.fleet_parity_sweep.v2"
        cells = @(
            [ordered]@{
                catalogId = "video.hfr.120"
                advertised = $true
                provenOk = $false
                failReason = "unautomated"
                gap = "GAP_UNAUTOMATED"
            },
            [ordered]@{
                catalogId = "video.av1.8k"
                advertised = $false
                provenOk = $false
                failReason = "unautomated"
                gap = "GAP_UNAUTOMATED"
            },
            [ordered]@{
                catalogId = "video.delivery_honesty"
                advertised = $true
                provenOk = $false
                failReason = "delivery_mismatch"
                gap = "GAP_DELIVERY_MISMATCH"
            }
        )
    }
    $proofFixture = [ordered]@{
        schema = "parity_proof_results.v1"
        rows = @(
            [ordered]@{ catalogId = "video.hfr.120"; pass = $true; script = "pns_hfr_fps_parity_verify.ps1" },
            [ordered]@{ catalogId = "video.av1.8k"; pass = $true; skippedReason = "matrix_gate:cameraAny.encoder.supportsAv1" }
        )
    }
    $deliveryFixture = [ordered]@{
        schema = "pns.parity_delivery_verify.v1"
        probe = [ordered]@{ matchOk = $true; mismatchReason = $null }
    }

    $inAppPath = Join-Path $fixtureOut "in_app_fixture.json"
    $proofPath = Join-Path $fixtureOut "parity_proof_results.json"
    $deliveryPath = Join-Path $fixtureOut "delivery_mismatch.json"
    $inAppFixture | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $inAppPath -Encoding utf8
    $proofFixture | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $proofPath -Encoding utf8
    $deliveryFixture | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $deliveryPath -Encoding utf8

    $inAppObj = Get-Content -LiteralPath $inAppPath -Raw | ConvertFrom-Json
    $merged = Merge-ParityProofResults $inAppObj $proofPath $deliveryPath
    $mergedPath = Join-Path $fixtureOut "in_app_merged.json"
    $merged | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $mergedPath -Encoding utf8

    $cells = @($merged.cells)
    $allProven = $true
    foreach ($cell in $cells) {
        if ($cell.provenOk -ne $true -or $cell.gap -ne "OK") {
            $allProven = $false
            break
        }
    }
    $proofMerged = ($merged.PSObject.Properties.Name -contains "proofPackMerged") -and ($merged.proofPackMerged -eq $true)
    $pass = $allProven -and $proofMerged

    $fixtureReport = [ordered]@{
        schema = "pns.fleet_parity_proof_merge_fixture.v1"
        pass = $pass
        cellCount = $cells.Count
        allProven = $allProven
        proofPackMerged = $proofMerged
        outDir = $fixtureOut
    }
    $fixtureReport | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $fixtureOut "fixture_report.json") -Encoding utf8
    Write-Host "[parity_proof_merge_fixture] pass=$pass cells=$($cells.Count) out=$fixtureOut"
    if (-not $pass) { exit 1 }
    exit 0
}

if ([string]::IsNullOrWhiteSpace($Mode)) {
    if ($Interactive) {
        $pick = Read-Host "Quick / Full / Delta ?"
        $Mode = switch -Regex ($pick.Trim()) {
            '^[Qq]' { 'Quick'; break }
            '^[Ff]' { 'Full'; break }
            '^[Dd]' { 'Delta'; break }
            default { '' }
        }
    }
    if ([string]::IsNullOrWhiteSpace($Mode)) {
        Write-Error "pns_fleet_parity_sweep.ps1: -Mode is required (Quick | Full | Delta). Use -Help."
        exit 2
    }
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) { $Serial = $fromEnv }
}

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

function Test-AdbAuthorizedDevice {
    foreach ($line in @(adb devices 2>&1)) {
        if ($line -match '\tdevice$') { return $true }
    }
    return $false
}

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\parity_sweep_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not (Test-AdbAuthorizedDevice)) {
    $stub = [ordered]@{
        schema = "pns.fleet_parity_sweep.v2"
        pass = $false
        skippedReason = "no_adb_device"
        mode = $Mode
        timestampUtc = [DateTime]::UtcNow.ToString("o")
        outDir = $OutDir
    }
    $stub | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "parity_report.json") -Encoding utf8
    Write-Host "[parity_sweep] No ADB device — wrote stub"
    exit 1
}

if ($AssembleDebug -or (-not $SkipInstall -and -not (Test-Path -LiteralPath $apk))) {
    & $gradlewHelper ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}

if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk)
}

$installedVersion = (Invoke-Adb @("shell", "dumpsys", "package", $pkg) | Select-String "versionCode=").Line -replace '.*versionCode=(\d+).*', '$1'
$apkVersion = $null
if (Test-Path -LiteralPath $apk) {
    $aapt = Get-Command aapt -ErrorAction SilentlyContinue
    if ($aapt) {
        $badging = & aapt dump badging $apk 2>$null
        $vc = $badging | Select-String "versionCode="
        if ($vc) { $apkVersion = ($vc.Line -replace '.*versionCode=''(\d+)''.*', '$1') }
    }
}

if (-not $SkipMatrixRefresh) {
    $matrixOut = Join-Path $OutDir "matrix"
    New-Item -ItemType Directory -Force -Path $matrixOut | Out-Null
    Write-Host "[parity_sweep] matrix refresh -> $matrixOut"
    $scanArgs = @{ Serial = $Serial; OutDir = $matrixOut; SkipInstall = $true }
    & $matrixScan @scanArgs
    if ($LASTEXITCODE -ne 0) { throw "pns_fleet_matrix_scan failed" }
} else {
    Write-Host "[parity_sweep] SkipMatrixRefresh — using on-device matrix"
}

$modeLower = $Mode.ToLowerInvariant()
$waitSec = switch ($Mode) {
    'Quick' { 120 }
    'Full' { 240 }
    'Delta' { 90 }
    default { 60 }
}

Invoke-Adb @("shell", "am", "force-stop", $pkg)
Invoke-Adb @("logcat", "-c")

$startArgs = @(
    "shell", "am", "start", "-W", "-n", "$activity",
    "--es", "pns_screen", "probehub",
    "--ez", "pns_auto_parity_sweep", "true",
    "--es", "pns_parity_sweep_mode", $modeLower
)
if ($IncludeRecord) {
    $startArgs += @("--ez", "pns_parity_sweep_include_record", "true")
}
Invoke-Adb $startArgs | Out-Null

Write-Host "[parity_sweep] waiting ${waitSec}s mode=$Mode"
Start-Sleep -Seconds $waitSec

$inAppJsonPath = Join-Path $OutDir "in_app_parity_report.json"
$inAppRaw = $null
try {
    $inAppRaw = Invoke-Adb @("exec-out", "run-as", $pkg, "cat", "files/parity_report_$modeLower.json")
} catch {
    Write-Warning "run-as pull failed: $($_.Exception.Message)"
}
if ($inAppRaw) {
    $inAppRaw | Set-Content -LiteralPath $inAppJsonPath -Encoding utf8 -NoNewline
}

$logPath = Join-Path $OutDir "logcat_parity.txt"
if ($Serial) {
    & adb -s $Serial exec-out logcat -d -s "PNS.FleetParity:I" "PNS.AdbValidation:I" "PNS.FleetMatrix:I" | Set-Content -LiteralPath $logPath -Encoding utf8
} else {
    & adb exec-out logcat -d -s "PNS.FleetParity:I" "PNS.AdbValidation:I" "PNS.FleetMatrix:I" | Set-Content -LiteralPath $logPath -Encoding utf8
}

$logCells = Parse-LogcatParityCells $logPath
$sweepCompleteLogged = Select-String -Path $logPath -Pattern 'sweepComplete' -SimpleMatch -ErrorAction SilentlyContinue

$inAppObj = $null
if (Test-Path -LiteralPath $inAppJsonPath) {
    try {
        $inAppObj = Get-Content -LiteralPath $inAppJsonPath -Raw | ConvertFrom-Json
    } catch {
        Write-Warning "Failed to parse in-app JSON: $($_.Exception.Message)"
    }
}

$cellCount = if ($inAppObj -and $inAppObj.cellCount) { [int]$inAppObj.cellCount } else { $logCells.Count }
$gapBreakdown = @{}
if ($inAppObj -and $inAppObj.gapBreakdown) {
    $inAppObj.gapBreakdown.PSObject.Properties | ForEach-Object { $gapBreakdown[$_.Name] = [int]$_.Value }
} elseif ($inAppObj -and $inAppObj.gapCounts) {
    $inAppObj.gapCounts.PSObject.Properties | ForEach-Object { $gapBreakdown[$_.Name] = [int]$_.Value }
} else {
    $gapBreakdown = Build-GapBreakdownFromLogcat $logCells
}

$shipBlockerCount = 0
if ($inAppObj -and $null -ne $inAppObj.shipBlockerGapCount) {
    $shipBlockerCount = [int]$inAppObj.shipBlockerGapCount
} else {
    $shipBlockerCount = @($logCells | Where-Object { $_.impact -eq 'SHIP_BLOCKER' -and -not $_.provenOk -and $_.advertised }).Count
}

$experimentalUnlockState = $null
if ($inAppObj -and ($inAppObj.PSObject.Properties.Name -contains "experimentalUnlockState")) {
    $experimentalUnlockState = $inAppObj.experimentalUnlockState
}

$schemaOk = ($inAppObj -and $inAppObj.schema -eq 'pns.fleet_parity_sweep.v2')
$minQuickCells = 50
$pass = switch ($Mode) {
    'Quick' {
        ($cellCount -ge $minQuickCells) -and [bool]$sweepCompleteLogged -and ($shipBlockerCount -eq 0) -and ($schemaOk -or $logCells.Count -ge $minQuickCells)
    }
    default {
        ($shipBlockerCount -eq 0) -and [bool]$sweepCompleteLogged -and ($schemaOk -or ($logCells.Count -gt 100))
    }
}

if ($Mode -eq 'Full' -and $apkVersion -and $installedVersion -and ($installedVersion -ne $apkVersion)) {
    Write-Warning "APK preflight: installed versionCode=$installedVersion != built $apkVersion"
    $pass = $false
}

$report = [ordered]@{
    schema = "pns.fleet_parity_sweep.v2"
    pass = $pass
    mode = $Mode
    includeRecord = [bool]$IncludeRecord
    serial = $Serial
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    outDir = $OutDir
    cellCount = $cellCount
    shipBlockerGapCount = $shipBlockerCount
    gapBreakdown = $gapBreakdown
    inAppSchema = if ($inAppObj) { $inAppObj.schema } else { $null }
    experimentalUnlockState = $experimentalUnlockState
    sweepCompleteLogged = [bool]$sweepCompleteLogged
    installedVersionCode = $installedVersion
    builtVersionCode = $apkVersion
    baselineTag = if ($BaselineTag) { $BaselineTag } else { $null }
    logPath = $logPath
    inAppJsonPath = $inAppJsonPath
}

$reportPath = Join-Path $OutDir "parity_report.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8

if ($inAppObj) {
    Write-ClosurePlanFromJson $inAppObj (Join-Path $OutDir "parity_closure_plan.md")
    Write-ShipBlockersMd $inAppObj (Join-Path $OutDir "parity_ship_blockers.md")
    Export-M21Artifacts $inAppObj $OutDir
    if ($BaselineJson -and (Test-Path -LiteralPath $BaselineJson)) {
        Write-BaselineDelta $inAppObj $BaselineJson (Join-Path $OutDir "parity_regression_delta.json")
    }
} else {
    @("# Parity closure plan", "", "- In-app JSON missing - use logcat cells ($($logCells.Count))") |
        Set-Content -LiteralPath (Join-Path $OutDir "parity_closure_plan.md") -Encoding utf8
}

$md = @(
    "# Fleet Parity Sweep - $Mode",
    "",
    "- **Pass:** $($report.pass)",
    "- **Cells:** $cellCount",
    "- **Ship blockers:** $shipBlockerCount",
    "- **In-app schema:** $($report.inAppSchema)",
    "- **Experimental unlock state:** ``$(if ($report.experimentalUnlockState) { $report.experimentalUnlockState | ConvertTo-Json -Compress } else { 'null' })``",
    "- **Gap breakdown:** ``$($gapBreakdown | ConvertTo-Json -Compress)``",
    ""
)
$md | Set-Content -LiteralPath (Join-Path $OutDir "parity_report.md") -Encoding utf8

$latestPath = Join-Path $projRoot "docs\FLEET_PARITY_LATEST.json"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $latestPath -Encoding utf8

$historyPath = Join-Path $projRoot "docs\FLEET_PARITY_HISTORY.jsonl"
Add-Content -LiteralPath $historyPath -Value ($report | ConvertTo-Json -Compress -Depth 8)

Invoke-Adb @("shell", "am", "force-stop", $pkg)

$matrixPath = Join-Path $OutDir "matrix\fleet_device_matrix.json"
if (-not (Test-Path -LiteralPath $matrixPath)) {
    try {
        $pulled = Invoke-Adb @("exec-out", "run-as", $pkg, "cat", "files/fleet_device_matrix.json")
        $pulled | Set-Content -LiteralPath (Join-Path $OutDir "fleet_device_matrix_pulled.json") -Encoding utf8 -NoNewline
        $matrixPath = Join-Path $OutDir "fleet_device_matrix_pulled.json"
    } catch { }
}
if ($CompareMatrix) {
    $comparePath = $CompareMatrix
} elseif (Test-Path -LiteralPath (Join-Path $projRoot "app\src\test\resources\fleet_golden_cph2583_v1.json")) {
    $comparePath = Join-Path $projRoot "app\src\test\resources\fleet_golden_cph2583_v1.json"
} else {
    $comparePath = $null
}
if ($comparePath -and (Test-Path -LiteralPath $matrixPath)) {
    try {
        Invoke-CompareMatrix $matrixPath $comparePath (Join-Path $OutDir "parity_fleet_diff.md")
    } catch {
        Write-Warning "CompareMatrix failed: $($_.Exception.Message)"
    }
}

if ($IncludeRecord -and $Mode -eq 'Full') {
    Write-Host "[parity_sweep] IncludeRecord -> delivery verify + thermal snapshot"
    $delivery = Invoke-ParityDeliveryVerify $OutDir
    if (-not $delivery.verifyOk) {
        Write-Warning "Delivery verify failed - see delivery_mismatch.json"
        if ($delivery.probe.matchOk -eq $false) {
            $pass = $false
            $report.pass = $false
        }
    }
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8
}

if ($IncludeProofPack -and $Mode -eq 'Full') {
    Write-Host "[parity_sweep] IncludeProofPack -> pns_parity_proof_pack.ps1 + merge"
    $matrixPathForProof = Join-Path $OutDir "matrix\fleet_device_matrix.json"
    if (-not (Test-Path -LiteralPath $matrixPathForProof)) {
        $matrixPathForProof = Join-Path $OutDir "fleet_device_matrix_pulled.json"
    }
    $proofOut = Join-Path $OutDir "proof_pack"
    New-Item -ItemType Directory -Force -Path $proofOut | Out-Null
    $packArgs = @{
        OutDir = $proofOut
        SkipInstall = $true
    }
    if ($Serial) { $packArgs.Serial = $Serial }
    if (Test-Path -LiteralPath $matrixPathForProof) { $packArgs.MatrixJsonPath = $matrixPathForProof }
    $deliveryPath = Join-Path $OutDir "delivery_mismatch.json"
    if (Test-Path -LiteralPath $deliveryPath) { $packArgs.DeliveryMismatchPath = $deliveryPath }
    & (Join-Path $PSScriptRoot "pns_parity_proof_pack.ps1") @packArgs
    $proofResultsPath = Join-Path $proofOut "parity_proof_results.json"
    if ($inAppObj -and (Test-Path -LiteralPath $proofResultsPath)) {
        $inAppObj = Merge-ParityProofResults $inAppObj $proofResultsPath $deliveryPath
        $inAppObj | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $inAppJsonPath -Encoding utf8
        $gapBreakdown = @{}
        if ($inAppObj.gapBreakdown) {
            if ($inAppObj.gapBreakdown -is [hashtable]) {
                foreach ($k in $inAppObj.gapBreakdown.Keys) {
                    $gapBreakdown[$k] = [int]$inAppObj.gapBreakdown[$k]
                }
            } else {
                $inAppObj.gapBreakdown.PSObject.Properties | ForEach-Object {
                    $gapBreakdown[$_.Name] = [int]$_.Value
                }
            }
        }
        $report.gapBreakdown = $gapBreakdown
        $report.proofPackMerged = $true
        $report.proofResultsPath = $proofResultsPath
        Write-ClosurePlanFromJson $inAppObj (Join-Path $OutDir "parity_closure_plan.md")
        Write-ShipBlockersMd $inAppObj (Join-Path $OutDir "parity_ship_blockers.md")
        $unauto = if ($gapBreakdown.ContainsKey("GAP_UNAUTOMATED")) { $gapBreakdown["GAP_UNAUTOMATED"] } else { 0 }
        $notProven = if ($gapBreakdown.ContainsKey("GAP_ADVERTISED_NOT_PROVEN")) { $gapBreakdown["GAP_ADVERTISED_NOT_PROVEN"] } else { 0 }
        $planned = if ($gapBreakdown.ContainsKey("GAP_PLANNED")) { $gapBreakdown["GAP_PLANNED"] } else { 0 }
        if ($unauto -gt 0 -or $notProven -gt 0) {
            Write-Warning "Proof merge gaps: unautomated=$unauto not_proven=$notProven planned=$planned"
            $pass = $false
            $report.pass = $false
        }
        $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8
        $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $latestPath -Encoding utf8
    }
}

if ($IncludeDngSubTrack) {
    Write-Host "[parity_sweep] IncludeDngSubTrack -> pns_aux_dng_capture_analyze.ps1"
    $dngArgs = @{ Serial = $Serial; SkipInstall = $true }
    & (Join-Path $PSScriptRoot "pns_aux_dng_capture_analyze.ps1") @dngArgs
    if ($LASTEXITCODE -ne 0) { Write-Warning "DNG sub-track failed exit=$LASTEXITCODE" }
}

if ($IncludeWorkflowPresets) {
    Write-Host "[parity_sweep] IncludeWorkflowPresets -> pns_workflow_test.ps1 -AllPresets"
    $wfArgs = @{ AllPresets = $true }
    if ($Serial) { $wfArgs.Serial = $Serial }
    & (Join-Path $PSScriptRoot "pns_workflow_test.ps1") @wfArgs
    if ($LASTEXITCODE -ne 0) { Write-Warning "workflow presets failed exit=$LASTEXITCODE" }
}

Write-Host "[parity_sweep] Wrote $reportPath pass=$($report.pass) cells=$cellCount shipBlockers=$shipBlockerCount"
if (-not $report.pass) { exit 1 }
exit 0
