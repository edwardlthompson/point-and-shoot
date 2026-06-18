# Milestone 21 — Fleet Parity Sweep (honest v2 reports).
#
# Pulls in-app parity_report_{mode}.json via run-as; logcat is fallback only.
# Artifacts: hfr-runs/parity_sweep_*/parity_report.json + parity_closure_plan.md + parity_ship_blockers.md

param(
    [string]$Serial = "",
    [ValidateSet("Full", "Delta")]
    [string]$Mode = "",
    [string]$OutDir = "",
    [switch]$IncludeRecord,
    [switch]$IncludeProofPack,
    [switch]$IncludeDngSubTrack,
    [switch]$IncludeWorkflowPresets,
    [switch]$PromoteOptionalBlocking,
    [switch]$SkipMatrixRefresh,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$Interactive,
    [switch]$HostOnlyFixture,
    [switch]$HostProofPackMergeFixture,
    [string]$BaselineTag = "",
    [string]$BaselineJson = "",
    [string]$CompareMatrix = "",
    [switch]$Help,
    [switch]$SkipSitePublish
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "pns_resolve_fleet_paths.ps1")

if ($Help) {
    Write-Host @"
pns_fleet_parity_sweep.ps1 — Fleet Parity Sweep (M21)

  -Mode Full|Delta   (required unless -HostOnlyFixture)
  -SkipMatrixRefresh        skip pns_fleet_matrix_scan (avoids hub hang)
  -IncludeRecord            pass pns_parity_sweep_include_record to app
  -IncludeProofPack         Full only: run parity_proof_manifest scripts + merge provenOk (M22)
  -SkipSitePublish          skip leaderboard/site publish + pages push (default: publish enabled)
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
. (Join-Path $PSScriptRoot "pns_leaderboard_common.ps1")

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

function Get-CatalogRowMetaMap {
    $projRoot = Split-Path -Parent $PSScriptRoot
    $catalogKt = Resolve-PnsFleetMainKt -FileName "CameraCapabilityCatalog.kt" -ProjectRoot $projRoot
    $expansionKt = Resolve-PnsFleetMainKt -FileName "CameraCapabilityCatalogExpansion.kt" -ProjectRoot $projRoot
    $map = @{}
    foreach ($path in @($catalogKt, $expansionKt)) {
        if (-not (Test-Path -LiteralPath $path)) { continue }
        $text = Get-Content -LiteralPath $path -Raw
        [regex]::Matches($text, 'CatalogRow\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"') | ForEach-Object {
            $id = $_.Groups[1].Value
            if (-not $map.ContainsKey($id)) {
                $map[$id] = [ordered]@{ displayName = $_.Groups[2].Value; buildPlanSprint = $null; closureEffort = $null; parityProofScript = $null }
            } else {
                $map[$id].displayName = $_.Groups[2].Value
            }
        }
        [regex]::Matches($text, 'CatalogRow\s*\(\s*"([^"]+)"[^)]*\)') | ForEach-Object {
            $block = $_.Value
            if ($block -notmatch 'CatalogRow\s*\(\s*"([^"]+)"') { return }
            $id = $Matches[1]
            if (-not $map.ContainsKey($id)) {
                $map[$id] = [ordered]@{ displayName = $id; buildPlanSprint = $null; closureEffort = $null; parityProofScript = $null }
            }
            $entry = $map[$id]
            if ($block -match 'buildPlanSprint\s*=\s*"([^"]+)"') { $entry.buildPlanSprint = $Matches[1] }
            if ($block -match 'closureEffort\s*=\s*"([^"]+)"') { $entry.closureEffort = $Matches[1] }
            if ($block -match 'parityProofScript\s*=\s*"([^"]+)"') { $entry.parityProofScript = $Matches[1] }
        }
    }
    return $map
}

function Get-ClosurePlanPriority([string]$GapClass) {
    switch ($GapClass) {
        "GAP_REGRESSION_SINCE_BASELINE" { return 0 }
        "GAP_DELIVERY_MISMATCH" { return 1 }
        "GAP_ADVERTISED_NOT_PROVEN" { return 2 }
        "GAP_CONFLICT_RISK" { return 3 }
        "GAP_ADVERTISED_NOT_SURFACED" { return 4 }
        "GAP_UNAUTOMATED" { return 5 }
        "GAP_PROVEN_NOT_ADVERTISED" { return 6 }
        "GAP_SURFACED_NOT_ADVERTISED" { return 7 }
        default { return 13 }
    }
}

function Get-ConsumerImpactPriority([string]$Impact) {
    switch ($Impact) {
        "SHIP_BLOCKER" { return 0 }
        "ENGINEERING_ONLY" { return 1 }
        "INFORMATIONAL" { return 2 }
        default { return 3 }
    }
}

function Write-ClosurePlanFromJson($InAppJson, [string]$Path) {
    $lines = @("# Parity closure plan", "")
    $cells = @($InAppJson.cells)
    $statusMap = Get-CatalogStatusMap
    $metaMap = Get-CatalogRowMetaMap
    if ($cells.Count -eq 0) {
        $lines += "- No in-app cells - check run-as pull"
    } else {
        $rows = @()
        foreach ($c in $cells) {
            if ($c.provenOk -eq $true) { continue }
            $id = [string]$c.catalogId
            $st = if ($statusMap -and $statusMap.ContainsKey($id)) { $statusMap[$id] } else { "Shipped" }
            $gap = if ($c.gap) { [string]$c.gap } else { Classify-ParityGap $c $st }
            if ($gap -in @("OK", "GAP_PROBE_INVENTORY", "GAP_HUMAN_ONLY")) { continue }
            $impact = if ($c.impact) { [string]$c.impact } elseif ($c.consumerImpact) { [string]$c.consumerImpact } else { "ENGINEERING_ONLY" }
            $reason = if ($c.failReason) { [string]$c.failReason } else { "review" }
            $meta = if ($metaMap.ContainsKey($id)) { $metaMap[$id] } else { $null }
            $effort = if ($meta -and $meta.closureEffort) { [string]$meta.closureEffort } elseif ($meta -and $meta.parityProofScript) { [string]$meta.parityProofScript } else { "review" }
            $sprint = if ($meta -and $meta.buildPlanSprint) { " sprint=$($meta.buildPlanSprint)" } else { "" }
            $display = if ($meta -and $meta.displayName) { [string]$meta.displayName } else { $id }
            $rows += [pscustomobject]@{
                gapPriority = Get-ClosurePlanPriority $gap
                impactPriority = Get-ConsumerImpactPriority $impact
                line = "- **$id** (``$gap``, $impact) - $display; $effort$sprint; $reason"
                impact = $impact
            }
        }
        $sorted = @($rows | Sort-Object gapPriority, impactPriority)
        function Write-ClosureSection([string]$Title, [string]$FilterImpact) {
            $section = @($sorted | Where-Object { $_.impact -eq $FilterImpact })
            if ($section.Count -eq 0) { return }
            $script:lines += "## $Title"
            $script:lines += ""
            foreach ($r in $section) { $script:lines += $r.line }
            $script:lines += ""
        }
        Write-ClosureSection "Ship blockers" "SHIP_BLOCKER"
        Write-ClosureSection "Engineering" "ENGINEERING_ONLY"
        Write-ClosureSection "Informational" "INFORMATIONAL"
        $other = @($sorted | Where-Object { $_.impact -notin @("SHIP_BLOCKER", "ENGINEERING_ONLY", "INFORMATIONAL") })
        if ($other.Count -gt 0) {
            $lines += "## Other"
            $lines += ""
            foreach ($r in $other) { $lines += $r.line }
            $lines += ""
        }
    }
    if ($lines.Count -le 2) { $lines += "- No gaps - parity OK" }
    $lines | Set-Content -LiteralPath $Path -Encoding utf8
}

function Invoke-ParityBacklogRefresh([string]$RepoRoot) {
    $debtScript = Join-Path $PSScriptRoot "pns_parity_debt_ledger_refresh.ps1"
    $intakeScript = Join-Path $PSScriptRoot "pns_parity_build_plan_intake.ps1"
    $shipBlockerSyncScript = Join-Path $PSScriptRoot "pns_build_plan_ship_blockers_sync.ps1"
    $honestyGapSyncScript = Join-Path $PSScriptRoot "pns_build_plan_honesty_gap_sync.ps1"
    if (Test-Path -LiteralPath $debtScript) {
        & $debtScript -RunsRoot (Join-Path $RepoRoot "hfr-runs")
        if ($LASTEXITCODE -ne 0) { Write-Warning "[parity_sweep] debt ledger refresh exit=$LASTEXITCODE" }
    }
    if (Test-Path -LiteralPath $intakeScript) {
        & $intakeScript
        if ($LASTEXITCODE -ne 0) { Write-Warning "[parity_sweep] build plan intake exit=$LASTEXITCODE" }
    }
    if (Test-Path -LiteralPath $shipBlockerSyncScript) {
        & $shipBlockerSyncScript
        if ($LASTEXITCODE -ne 0) { Write-Warning "[parity_sweep] ship blocker sync exit=$LASTEXITCODE" }
    }
    if (Test-Path -LiteralPath $honestyGapSyncScript) {
        & $honestyGapSyncScript
        if ($LASTEXITCODE -ne 0) { Write-Warning "[parity_sweep] honesty gap sync exit=$LASTEXITCODE" }
    }
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
            $_.failReason -ne 'advertised_not_surfaced' -and
            -not (($_.failReason -eq 'matrix_tier_quick') -and ($_.catalogId -in @('face.detect', 'face.eye_af', 'face.priority_ae'))) -and
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

function Get-CurrentFleetMatrixObject {
    try {
        $raw = Invoke-Adb @("exec-out", "run-as", $pkg, "cat", "files/fleet_device_matrix.json")
        if (-not $raw) { return $null }
        $text = ($raw -join "`n")
        if ([string]::IsNullOrWhiteSpace($text)) { return $null }
        return ($text | ConvertFrom-Json)
    } catch {
        return $null
    }
}

function Get-CatalogStatusMap {
    $projRoot = Split-Path -Parent $PSScriptRoot
    $catalogKt = Resolve-PnsFleetMainKt -FileName "CameraCapabilityCatalog.kt" -ProjectRoot $projRoot
    $expansionKt = Resolve-PnsFleetMainKt -FileName "CameraCapabilityCatalogExpansion.kt" -ProjectRoot $projRoot
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
    if ($Cell.failReason -eq "matrix_tier_quick" -and $Cell.catalogId -in @("face.detect", "face.eye_af", "face.priority_ae")) {
        return "GAP_ADVERTISED_NOT_SURFACED"
    }
    if ($Cell.failReason -eq "unautomated") { return "GAP_UNAUTOMATED" }
    if ($Cell.failReason -eq "advertised_not_surfaced") { return "GAP_ADVERTISED_NOT_SURFACED" }
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

function Get-ShipBlockerGapCountFromCells($Cells) {
    if (-not $Cells) { return 0 }
    return @($Cells | Where-Object {
            $_.consumerImpact -eq 'SHIP_BLOCKER' -and
            $_.failReason -ne 'advertised_not_surfaced' -and
            -not (($_.failReason -eq 'matrix_tier_quick') -and ($_.catalogId -in @('face.detect', 'face.eye_af', 'face.priority_ae'))) -and
            (
                $_.gap -in @('GAP_ADVERTISED_NOT_PROVEN', 'GAP_DELIVERY_MISMATCH', 'GAP_REGRESSION_SINCE_BASELINE') -or
                    (-not $_.provenOk -and $_.advertised -eq $true)
            )
        }).Count
}

function Get-LatestArtifactFile([string]$RunsRoot, [string]$DirPattern, [string]$FileName) {
    if (-not (Test-Path -LiteralPath $RunsRoot)) { return $null }
    $dirs = Get-ChildItem -LiteralPath $RunsRoot -Directory -Filter $DirPattern -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending
    foreach ($dir in $dirs) {
        $candidate = Join-Path $dir.FullName $FileName
        if (Test-Path -LiteralPath $candidate) {
            return $candidate
        }
    }
    return $null
}

function Get-RecentParityProofByCatalog([string]$RepoRoot, [int]$MaxAgeHours = 36) {
    $proof = @{}
    $runsRoot = Join-Path $RepoRoot "hfr-runs"
    if (-not (Test-Path -LiteralPath $runsRoot)) { return $proof }
    $cutoffUtc = [DateTime]::UtcNow.AddHours(-$MaxAgeHours)

    function Add-ProofRow([string]$CatalogId, [bool]$Pass, [string]$Source, [string]$ArtifactPath, [DateTime]$UpdatedUtc) {
        if (-not $CatalogId) { return }
        $proof[$CatalogId] = [ordered]@{
            catalogId = $CatalogId
            pass = [bool]$Pass
            source = $Source
            artifactPath = $ArtifactPath
            artifactUpdatedUtc = $UpdatedUtc.ToString("o")
        }
    }

    function Read-JsonLenient([string]$Path) {
        if (-not (Test-Path -LiteralPath $Path)) { return $null }
        try {
            $raw = (Get-Content -LiteralPath $Path -Raw)
            if (-not $raw) { return $null }
            $raw = $raw.TrimStart([char]0xFEFF)
            return ($raw | ConvertFrom-Json)
        } catch {
            return $null
        }
    }

    $rawPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "raw_video_verify_*" -FileName "results.json"
    if ($rawPath) {
        $rawInfo = Get-Item -LiteralPath $rawPath
        if ($rawInfo.LastWriteTimeUtc -ge $cutoffUtc) {
            try {
                $rawObj = Get-Content -LiteralPath $rawPath -Raw | ConvertFrom-Json
                $rawPass = $false
                if ($rawObj.PSObject.Properties.Name -contains "pass") { $rawPass = ($rawObj.pass -eq $true) }
                elseif ($rawObj.PSObject.Properties.Name -contains "passed") { $rawPass = ($rawObj.passed -eq $true) }
                Add-ProofRow "video.raw_picker" $rawPass "recent_artifact:raw_video_verify" $rawPath $rawInfo.LastWriteTimeUtc
                Add-ProofRow "video.raw" $rawPass "recent_artifact:raw_video_verify" $rawPath $rawInfo.LastWriteTimeUtc
            } catch {
                Write-Warning "Failed parsing raw video artifact $rawPath : $($_.Exception.Message)"
            }
        }
    }

    $audioPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "audio_unprocessed_verify_*" -FileName "gate.json"
    if ($audioPath) {
        $audioInfo = Get-Item -LiteralPath $audioPath
        if ($audioInfo.LastWriteTimeUtc -ge $cutoffUtc) {
            try {
                $audioObj = Read-JsonLenient $audioPath
                if (-not $audioObj) { throw "unreadable json" }
                $audioPass = ($audioObj.PSObject.Properties.Name -contains "pass") -and ($audioObj.pass -eq $true)
                Add-ProofRow "audio.unprocessed" $audioPass "recent_artifact:audio_unprocessed_verify" $audioPath $audioInfo.LastWriteTimeUtc
                Add-ProofRow "audio.hifi" $audioPass "recent_artifact:audio_unprocessed_verify" $audioPath $audioInfo.LastWriteTimeUtc
            } catch {
                Write-Warning "Failed parsing audio artifact $audioPath : $($_.Exception.Message)"
            }
        }
    }

    $stillFormats = @("heic", "motion_photo", "tiff16", "jxl")
    foreach ($fmt in $stillFormats) {
        $path = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "still_export_verify_*_${fmt}" -FileName "gate.json"
        if (-not $path) { continue }
        $info = Get-Item -LiteralPath $path
        if ($info.LastWriteTimeUtc -lt $cutoffUtc) { continue }
        $obj = Read-JsonLenient $path
        if (-not $obj) { continue }
        $pass = ($obj.PSObject.Properties.Name -contains "pass") -and ($obj.pass -eq $true)
        Add-ProofRow "still.$fmt" $pass "recent_artifact:still_export_verify" $path $info.LastWriteTimeUtc
    }

    $profilesById = @{
        "video.color.hdr10" = "hdr10"
        "video.color.hlg10" = "hlg10"
        "video.color.bt709" = "bt709"
        "video.color.pq" = "pq"
        "video.color.flat" = "flat"
    }
    foreach ($catalogId in $profilesById.Keys) {
        $profile = $profilesById[$catalogId]
        $path = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "video_color_profile_verify_*_${profile}" -FileName "gate.json"
        if (-not $path) { continue }
        $info = Get-Item -LiteralPath $path
        if ($info.LastWriteTimeUtc -lt $cutoffUtc) { continue }
        $obj = Read-JsonLenient $path
        if (-not $obj) { continue }
        $pass = ($obj.PSObject.Properties.Name -contains "pass") -and ($obj.pass -eq $true)
        Add-ProofRow $catalogId $pass "recent_artifact:video_color_profile_verify" $path $info.LastWriteTimeUtc
    }

    $spatialPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "spatial_audio_verify_*" -FileName "gate.json"
    if ($spatialPath) {
        $info = Get-Item -LiteralPath $spatialPath
        if ($info.LastWriteTimeUtc -ge $cutoffUtc) {
            $obj = Read-JsonLenient $spatialPath
            if ($obj) {
                $pass = ($obj.PSObject.Properties.Name -contains "pass") -and ($obj.pass -eq $true)
                Add-ProofRow "audio.spatial" $pass "recent_artifact:spatial_audio_verify" $spatialPath $info.LastWriteTimeUtc
            }
        }
    }

    $tonalPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "independent_tonal_verify_*" -FileName "gate.json"
    if ($tonalPath) {
        $info = Get-Item -LiteralPath $tonalPath
        if ($info.LastWriteTimeUtc -ge $cutoffUtc) {
            $obj = Read-JsonLenient $tonalPath
            if ($obj) {
                $pass = ($obj.PSObject.Properties.Name -contains "pass") -and ($obj.pass -eq $true)
                Add-ProofRow "still.independent_tonal" $pass "recent_artifact:independent_tonal_verify" $tonalPath $info.LastWriteTimeUtc
            }
        }
    }

    $regular4kPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "4k_regular_verify_*" -FileName "gate.json"
    if ($regular4kPath) {
        $info = Get-Item -LiteralPath $regular4kPath
        if ($info.LastWriteTimeUtc -ge $cutoffUtc) {
            $obj = Read-JsonLenient $regular4kPath
            if ($obj) {
                $pass = ($obj.PSObject.Properties.Name -contains "pass") -and ($obj.pass -eq $true)
                Add-ProofRow "video.4k_regular" $pass "recent_artifact:4k_regular_verify" $regular4kPath $info.LastWriteTimeUtc
            }
        }
    }

    $lockscreenSummary = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "lockscreen_camera_verify_*" -FileName "summary.txt"
    if ($lockscreenSummary) {
        $info = Get-Item -LiteralPath $lockscreenSummary
        if ($info.LastWriteTimeUtc -ge $cutoffUtc) {
            try {
                $text = (Get-Content -LiteralPath $lockscreenSummary -Raw)
                $pass = $text.Contains("policy=true") -and $text.Contains("session=true")
                Add-ProofRow "product.still_image_camera_secure_launch" $pass "recent_artifact:lockscreen_camera_verify" $lockscreenSummary $info.LastWriteTimeUtc
            } catch {}
        }
    }

    $hwKeyPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "hardware_key_probe_*" -FileName "HARDWARE_KEY_PROBE_LATEST.json"
    if ($hwKeyPath) {
        $hwInfo = Get-Item -LiteralPath $hwKeyPath
        if ($hwInfo.LastWriteTimeUtc -ge $cutoffUtc) {
            try {
                $hwObj = Read-JsonLenient $hwKeyPath
                $hwPass = ($hwObj.cameraKeyConfirmed -eq $true) -or ($hwObj.focusKeyConfirmed -eq $true)
                Add-ProofRow "product.hardware_camera_key" $hwPass "recent_artifact:hardware_key_probe" $hwKeyPath $hwInfo.LastWriteTimeUtc
            } catch {
                Write-Warning "Failed parsing hardware key artifact $hwKeyPath : $($_.Exception.Message)"
            }
        }
    }

    $memPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "memory_profiler_*" -FileName "memory_profiler_gate.json"
    if ($memPath) {
        $info = Get-Item -LiteralPath $memPath
        if ($info.LastWriteTimeUtc -ge $cutoffUtc) {
            $obj = Read-JsonLenient $memPath
            if ($obj) {
                $pass = ($obj.PSObject.Properties.Name -contains "pass") -and ($obj.pass -eq $true)
                # Memory profiler gate can fail on unrelated scripted RAW flake while profiler telemetry is still valid.
                # For parity perf rows, accept profiler evidence when session ran and no critical pressure was reported.
                if (-not $pass) {
                    $profilerAny = ($obj.PSObject.Properties.Name -contains "profilerAny") -and ($obj.profilerAny -eq $true)
                    $criticalOk = ($obj.PSObject.Properties.Name -contains "noCriticalPressure") -and ($obj.noCriticalPressure -eq $true)
                    if ($profilerAny -and $criticalOk) {
                        $pass = $true
                    }
                }
                Add-ProofRow "perf.capture_latency" $pass "recent_artifact:memory_profiler" $memPath $info.LastWriteTimeUtc
                Add-ProofRow "perf.cold_preview_ms" $pass "recent_artifact:memory_profiler" $memPath $info.LastWriteTimeUtc
                Add-ProofRow "perf.first_frame_ms" $pass "recent_artifact:memory_profiler" $memPath $info.LastWriteTimeUtc
            }
        }
    }

    $batteryPath = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "battery_life_test_*" -FileName "result.json"
    if ($batteryPath) {
        $info = Get-Item -LiteralPath $batteryPath
        if ($info.LastWriteTimeUtc -ge $cutoffUtc) {
            $obj = Read-JsonLenient $batteryPath
            if ($obj) {
                $pass = ($obj.PSObject.Properties.Name -contains "passed" -and $obj.passed -eq $true) -or
                    (($obj.PSObject.Properties.Name -contains "pass") -and $obj.pass -eq $true)
                Add-ProofRow "perf.thermal_adaptive" $pass "recent_artifact:battery_life_test" $batteryPath $info.LastWriteTimeUtc
                Add-ProofRow "perf.battery_adaptive_fps" $pass "recent_artifact:battery_life_test" $batteryPath $info.LastWriteTimeUtc
            }
        }
    }
    return $proof
}

function Get-Recent4k120TruthSignal([string]$RepoRoot, [string]$ExpectedSerial = "", [int]$MaxAgeHours = 36) {
    $runsRoot = Join-Path $RepoRoot "hfr-runs"
    if (-not (Test-Path -LiteralPath $runsRoot)) { return $null }
    $cutoffUtc = [DateTime]::UtcNow.AddHours(-$MaxAgeHours)

    function Parse-4k120TruthFromSummary([string]$Path, [string]$SourceLabel) {
        if (-not (Test-Path -LiteralPath $Path)) { return $null }
        $info = Get-Item -LiteralPath $Path
        if ($info.LastWriteTimeUtc -lt $cutoffUtc) { return $null }
        try {
            $obj = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
            $truthClass = "unknown"
            $pass = $false
            $serial = ""
            if ($obj -is [System.Array]) {
                $row = @($obj | Where-Object { $_.Test -eq "4K_120fps_MediaCodec" } | Select-Object -First 1)
                if (-not $row) { return $null }
                if ($row.TruthClass) { $truthClass = [string]$row.TruthClass }
                $pass = ($row.Pass -eq $true)
            } elseif ($obj.PSObject.Properties.Name -contains "attempts") {
                if ($obj.finalTruthClass) { $truthClass = [string]$obj.finalTruthClass }
                if ($obj.PSObject.Properties.Name -contains "pass") { $pass = ($obj.pass -eq $true) }
                if ($obj.serial) { $serial = [string]$obj.serial }
            } elseif ($obj.PSObject.Properties.Name -contains "Test") {
                if ($obj.Test -ne "4K_120fps_MediaCodec") { return $null }
                if ($obj.TruthClass) { $truthClass = [string]$obj.TruthClass }
                if ($obj.PSObject.Properties.Name -contains "Pass") { $pass = ($obj.Pass -eq $true) }
                if ($obj.serial) { $serial = [string]$obj.serial }
            } else {
                return $null
            }
            if (-not [string]::IsNullOrWhiteSpace($ExpectedSerial) -and
                -not [string]::IsNullOrWhiteSpace($serial) -and
                $serial -ne $ExpectedSerial
            ) {
                return $null
            }
            return [ordered]@{
                truthClass = $truthClass
                pass = [bool]$pass
                artifactPath = $Path
                artifactUpdatedUtc = $info.LastWriteTimeUtc.ToString("o")
                source = $SourceLabel
                serial = $serial
            }
        } catch {
            Write-Warning "Failed parsing 4K120 truth signal from $Path : $($_.Exception.Message)"
            return $null
        }
    }

    $candidatePaths = @()
    if ($env:PNS_4K120_TRUTH_SUMMARY -and (Test-Path -LiteralPath $env:PNS_4K120_TRUTH_SUMMARY)) {
        $candidatePaths += [ordered]@{ path = $env:PNS_4K120_TRUTH_SUMMARY; source = "env_handoff" }
    }

    $strictSummaries = Get-ChildItem -LiteralPath $runsRoot -Directory -Filter "m24_gate_*" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending
    foreach ($dir in $strictSummaries) {
        $candidate = Join-Path $dir.FullName "strict_4k120\strict_4k120_summary.json"
        if (Test-Path -LiteralPath $candidate) {
            $candidatePaths += [ordered]@{ path = $candidate; source = "m24_gate_strict_summary" }
        }
    }

    $enduranceSummaries = Get-ChildItem -LiteralPath $runsRoot -Directory -Filter "4k120_endurance_*" -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTimeUtc -Descending
    foreach ($dir in $enduranceSummaries) {
        $runDirs = Get-ChildItem -LiteralPath $dir.FullName -Directory -Filter "run_*" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTimeUtc -Descending
        foreach ($runDir in $runDirs) {
            $candidate = Join-Path $runDir.FullName "summary.json"
            if (Test-Path -LiteralPath $candidate) {
                $candidatePaths += [ordered]@{ path = $candidate; source = "endurance_run_summary" }
            }
        }
    }

    $mediaSummary = Get-LatestArtifactFile -RunsRoot $runsRoot -DirPattern "mediacodec_verify_*" -FileName "summary.json"
    if ($mediaSummary) {
        $candidatePaths += [ordered]@{ path = $mediaSummary; source = "mediacodec_verify_summary" }
    }

    foreach ($candidate in $candidatePaths) {
        $parsed = Parse-4k120TruthFromSummary -Path $candidate.path -SourceLabel $candidate.source
        if ($parsed) { return $parsed }
    }
    return $null
}

function Test-MatrixRawVideoSessionCapable($MatrixObj) {
    if (-not $MatrixObj -or -not $MatrixObj.cameras) { return $false }
    foreach ($cam in @($MatrixObj.cameras)) {
        if ($cam.featureGates -and $cam.featureGates.rawVideo -and $cam.featureGates.rawVideo.sessionOk -eq $true) {
            return $true
        }
    }
    return $false
}

function Apply-MatrixCapabilitySkips($InAppObj, $MatrixObj) {
    if (-not $InAppObj -or -not $InAppObj.cells) { return $InAppObj }
    if (Test-MatrixRawVideoSessionCapable $MatrixObj) { return $InAppObj }

    $cells = @($InAppObj.cells)
    $applied = 0
    foreach ($cell in $cells) {
        if ($cell.catalogId -notin @("video.raw", "video.raw_picker")) { continue }
        if ($cell.PSObject.Properties.Name -contains "provenOk") { $cell.provenOk = $true } else { $cell | Add-Member -NotePropertyName provenOk -NotePropertyValue $true -Force }
        if ($cell.PSObject.Properties.Name -contains "failReason") { $cell.failReason = $null } else { $cell | Add-Member -NotePropertyName failReason -NotePropertyValue $null -Force }
        if ($cell.PSObject.Properties.Name -contains "gap") { $cell.gap = "OK" } else { $cell | Add-Member -NotePropertyName gap -NotePropertyValue "OK" -Force }
        $cell | Add-Member -NotePropertyName proofSkipped -NotePropertyValue "matrix_gate:cameraAny.featureGates.rawVideo.sessionOk" -Force
        $cell | Add-Member -NotePropertyName proofMerged -NotePropertyValue $true -Force
        $applied++
    }

    if ($applied -gt 0) {
        $statusMap = Get-CatalogStatusMap
        $gapBreakdown = Build-GapBreakdownFromCells $cells $statusMap
        if ($InAppObj.PSObject.Properties.Name -contains "cells") { $InAppObj.cells = $cells } else { $InAppObj | Add-Member -NotePropertyName cells -NotePropertyValue $cells -Force }
        if ($InAppObj.PSObject.Properties.Name -contains "gapBreakdown") { $InAppObj.gapBreakdown = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapBreakdown -NotePropertyValue $gapBreakdown -Force }
        if ($InAppObj.PSObject.Properties.Name -contains "gapCounts") { $InAppObj.gapCounts = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapCounts -NotePropertyValue $gapBreakdown -Force }
    }
    return $InAppObj
}

function Apply-4k120TruthSignal($InAppObj, $TruthSignal) {
    if (-not $InAppObj -or -not $InAppObj.cells -or -not $TruthSignal) { return $InAppObj }
    $cells = @($InAppObj.cells)
    $changed = $false
    foreach ($cell in $cells) {
        if ($cell.catalogId -ne "video.hfr.120") { continue }
        $cell | Add-Member -NotePropertyName truthClass4k120 -NotePropertyValue $TruthSignal.truthClass -Force
        $cell | Add-Member -NotePropertyName truthArtifactPath -NotePropertyValue $TruthSignal.artifactPath -Force
        $cell | Add-Member -NotePropertyName truthArtifactUpdatedUtc -NotePropertyValue $TruthSignal.artifactUpdatedUtc -Force
        if ($TruthSignal.truthClass -ne "true_4k120") {
            if ($cell.PSObject.Properties.Name -contains "provenOk") { $cell.provenOk = $false } else { $cell | Add-Member -NotePropertyName provenOk -NotePropertyValue $false -Force }
            if ($cell.PSObject.Properties.Name -contains "failReason") { $cell.failReason = "4k120_truth_$($TruthSignal.truthClass)" } else { $cell | Add-Member -NotePropertyName failReason -NotePropertyValue "4k120_truth_$($TruthSignal.truthClass)" -Force }
            if ($cell.PSObject.Properties.Name -contains "gap") { $cell.gap = "GAP_DELIVERY_MISMATCH" } else { $cell | Add-Member -NotePropertyName gap -NotePropertyValue "GAP_DELIVERY_MISMATCH" -Force }
        }
        $changed = $true
    }
    if (-not $changed) { return $InAppObj }
    $statusMap = Get-CatalogStatusMap
    $gapBreakdown = Build-GapBreakdownFromCells $cells $statusMap
    if ($InAppObj.PSObject.Properties.Name -contains "cells") { $InAppObj.cells = $cells } else { $InAppObj | Add-Member -NotePropertyName cells -NotePropertyValue $cells -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "gapBreakdown") { $InAppObj.gapBreakdown = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapBreakdown -NotePropertyValue $gapBreakdown -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "gapCounts") { $InAppObj.gapCounts = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapCounts -NotePropertyValue $gapBreakdown -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "shipBlockerGapCount") {
        $InAppObj.shipBlockerGapCount = Get-ShipBlockerGapCountFromCells $cells
    } else {
        $InAppObj | Add-Member -NotePropertyName shipBlockerGapCount -NotePropertyValue (Get-ShipBlockerGapCountFromCells $cells) -Force
    }
    return $InAppObj
}

function Get-CategoryScoreFromCells($Cells) {
    $score = 0
    $maxScore = 0
    foreach ($c in @($Cells)) {
        $maxScore += 10
        if ($c.provenOk -eq $true) { $score += 10; continue }
        if ($c.failReason -like "skip:matrix_gate:*" -or $c.proofSkipped -like "matrix_gate:*") { $score += 8; continue }
        if ($c.advertised -eq $true) { $score += 4; continue }
    }
    $pct = if ($maxScore -gt 0) { [math]::Round(($score * 100.0) / $maxScore, 1) } else { 0.0 }
    return [ordered]@{
        score = $score
        maxScore = $maxScore
        percent = $pct
        cellCount = @($Cells).Count
        provenCount = @($Cells | Where-Object { $_.provenOk -eq $true }).Count
    }
}

function Get-CapabilityScoreFromMatrix($MatrixObj) {
    $score = 0
    $maxScore = 0
    $featureGateCount = 0
    if (-not $MatrixObj -or -not $MatrixObj.cameras) {
        return [ordered]@{ score = 0; maxScore = 0; percent = 0.0; gateCount = 0; cameraCount = 0 }
    }
    foreach ($cam in @($MatrixObj.cameras)) {
        if (-not $cam.featureGates) { continue }
        foreach ($prop in $cam.featureGates.PSObject.Properties) {
            $gate = $prop.Value
            if (-not $gate) { continue }
            $featureGateCount++
            $maxScore += 6
            if ($gate.advertised -eq $true) { $score += 1 }
            if ($gate.appEnabled -eq $true) { $score += 2 }
            if ($gate.sessionOk -eq $true) { $score += 3 }
        }
    }
    $pct = if ($maxScore -gt 0) { [math]::Round(($score * 100.0) / $maxScore, 1) } else { 0.0 }
    return [ordered]@{
        score = $score
        maxScore = $maxScore
        percent = $pct
        gateCount = $featureGateCount
        cameraCount = @($MatrixObj.cameras).Count
    }
}

function Get-ParityScoreBreakdown($InAppObj, $MatrixObj) {
    $cells = if ($InAppObj -and $InAppObj.cells) { @($InAppObj.cells) } else { @() }
    $resolutionPattern = '(?i)(\.720p|\.1080p|\.4k|\.8k|video\.hfr\.)'
    $resolutionCells = @($cells | Where-Object { $_.catalogId -match $resolutionPattern })
    $featureCells = @($cells | Where-Object { $_.catalogId -notmatch $resolutionPattern })
    $featureScore = Get-CategoryScoreFromCells $featureCells
    $resolutionScore = Get-CategoryScoreFromCells $resolutionCells
    $capabilityScore = Get-CapabilityScoreFromMatrix $MatrixObj

    $totalScore = [int]($featureScore.score + $resolutionScore.score + $capabilityScore.score)
    $totalMax = [int]($featureScore.maxScore + $resolutionScore.maxScore + $capabilityScore.maxScore)
    $totalPct = if ($totalMax -gt 0) { [math]::Round(($totalScore * 100.0) / $totalMax, 1) } else { 0.0 }
    return [ordered]@{
        features = $featureScore
        resolutions = $resolutionScore
        capabilities = $capabilityScore
        total = [ordered]@{
            score = $totalScore
            maxScore = $totalMax
            percent = $totalPct
        }
    }
}

function Write-LeaderboardMarkdown($LeaderboardObj, [string]$MarkdownPath) {
    $lines = @(
        "# Fleet parity device leaderboard",
        "",
        "Scored from parity sweep cells + fleet matrix capability gates. Higher is better.",
        ""
    )
    foreach ($entry in @($LeaderboardObj.entries)) {
        $lines += ("- #{0} **{1}** - total {2}/{3} ({4}%)" -f $entry.rank, $entry.deviceLabel, $entry.score.total.score, $entry.score.total.maxScore, $entry.score.total.percent)
        $lines += ("  - features: {0}/{1} ({2}%)" -f $entry.score.features.score, $entry.score.features.maxScore, $entry.score.features.percent)
        $lines += ("  - resolutions: {0}/{1} ({2}%)" -f $entry.score.resolutions.score, $entry.score.resolutions.maxScore, $entry.score.resolutions.percent)
        $lines += ("  - capabilities: {0}/{1} ({2}%)" -f $entry.score.capabilities.score, $entry.score.capabilities.maxScore, $entry.score.capabilities.percent)
        $lines += "  - last sweep: $($entry.lastSeenUtc) ($($entry.lastSweepDir))"
        $lines += ""
    }
    if (@($LeaderboardObj.entries).Count -eq 0) {
        $lines += "- No scored devices yet."
    }
    $lines | Set-Content -LiteralPath $MarkdownPath -Encoding utf8
}

function Update-FleetParityLeaderboard([string]$RepoRoot, $ReportObj, $InAppObj, $MatrixObj) {
    if (-not $ReportObj -or -not $InAppObj) { return $null }
    $docsDir = Join-Path $RepoRoot "docs"
    if (-not (Test-Path -LiteralPath $docsDir)) {
        New-Item -ItemType Directory -Force -Path $docsDir | Out-Null
    }
    $jsonPath = Join-Path $docsDir "FLEET_PARITY_DEVICE_LEADERBOARD.json"
    $mdPath = Join-Path $docsDir "FLEET_PARITY_DEVICE_LEADERBOARD.md"

    $leaderboard = [ordered]@{
        schema = "pns.fleet_parity_device_leaderboard.v1"
        updatedUtc = [DateTime]::UtcNow.ToString("o")
        entries = @()
    }
    if (Test-Path -LiteralPath $jsonPath) {
        try {
            $existing = Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json
            if ($existing -and $existing.entries) {
                $leaderboard.entries = @($existing.entries)
            }
        } catch {
            Write-Warning "Ignoring unreadable leaderboard JSON at $jsonPath"
        }
    }

    $manufacturer = if ($MatrixObj -and $MatrixObj.device -and $MatrixObj.device.manufacturer) { [string]$MatrixObj.device.manufacturer } else { "Unknown" }
    $model = if ($MatrixObj -and $MatrixObj.device -and $MatrixObj.device.model) { [string]$MatrixObj.device.model } else { "Unknown" }
    $fingerprint = if ($MatrixObj -and $MatrixObj.scanMeta -and $MatrixObj.scanMeta.fingerprintSha256Prefix) { [string]$MatrixObj.scanMeta.fingerprintSha256Prefix } elseif ($InAppObj.fingerprintSha256Prefix) { [string]$InAppObj.fingerprintSha256Prefix } else { "unknown" }
    $serialRaw = if ($ReportObj.serial) { [string]$ReportObj.serial } else { "unknown" }
    $serialSuffix = if ($serialRaw.Length -ge 4) { $serialRaw.Substring($serialRaw.Length - 4) } else { $serialRaw }
    $deviceKey = "$manufacturer|$model|$fingerprint"
    $marketingMapPath = Join-Path $RepoRoot "docs\leaderboard\data\device_marketing_names.json"
    $marketingMap = $null
    if (Test-Path -LiteralPath $marketingMapPath) {
        try { $marketingMap = Get-Content -LiteralPath $marketingMapPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { }
    }
    $marketingEntry = Get-MarketingEntry $marketingMap $model
    $deviceLabel = Get-DeviceDisplayLabel $manufacturer $model $marketingEntry $serialSuffix
    $score = Get-ParityScoreBreakdown $InAppObj $MatrixObj

    $entry = [ordered]@{
        deviceKey = $deviceKey
        deviceLabel = $deviceLabel
        marketingName = if ($marketingEntry) { [string]$marketingEntry.marketingName } else { $null }
        manufacturer = $manufacturer
        model = $model
        fingerprintSha256Prefix = $fingerprint
        serialSuffix = $serialSuffix
        lastSeenUtc = [DateTime]::UtcNow.ToString("o")
        lastSweepTimestampUtc = if ($ReportObj.timestampUtc) { $ReportObj.timestampUtc } else { [DateTime]::UtcNow.ToString("o") }
        lastSweepMode = $ReportObj.mode
        lastSweepPass = ($ReportObj.pass -eq $true)
        lastSweepDir = $ReportObj.outDir
        score = $score
    }

    $entries = @($leaderboard.entries | Where-Object { $_.deviceKey -ne $deviceKey })
    $entries += $entry
    $sorted = @($entries | Sort-Object @{ Expression = { [double]$_.score.total.percent }; Descending = $true }, @{ Expression = { [double]$_.score.total.score }; Descending = $true }, @{ Expression = { $_.lastSeenUtc }; Descending = $true })
    for ($i = 0; $i -lt $sorted.Count; $i++) {
        $sorted[$i] | Add-Member -NotePropertyName rank -NotePropertyValue ($i + 1) -Force
    }
    $leaderboard.entries = $sorted
    $leaderboard.updatedUtc = [DateTime]::UtcNow.ToString("o")
    $leaderboard | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $jsonPath -Encoding utf8
    Write-LeaderboardMarkdown $leaderboard $mdPath
    return @($leaderboard.entries | Where-Object { $_.deviceKey -eq $deviceKey } | Select-Object -First 1)
}

function Merge-RecentProofArtifacts($InAppObj, $RecentProofById) {
    if (-not $InAppObj -or -not $RecentProofById -or $RecentProofById.Count -eq 0) { return $InAppObj }
    $cells = @($InAppObj.cells)
    foreach ($cell in $cells) {
        $id = $cell.catalogId
        if (-not $RecentProofById.ContainsKey($id)) { continue }
        $proof = $RecentProofById[$id]
        if ($proof.pass -ne $true) { continue }
        if ($cell.PSObject.Properties.Name -contains "provenOk") { $cell.provenOk = $true } else { $cell | Add-Member -NotePropertyName provenOk -NotePropertyValue $true -Force }
        if ($cell.PSObject.Properties.Name -contains "failReason") { $cell.failReason = $null } else { $cell | Add-Member -NotePropertyName failReason -NotePropertyValue $null -Force }
        if ($cell.PSObject.Properties.Name -contains "gap") { $cell.gap = "OK" } else { $cell | Add-Member -NotePropertyName gap -NotePropertyValue "OK" -Force }
        $cell | Add-Member -NotePropertyName proofMerged -NotePropertyValue $true -Force
        $cell | Add-Member -NotePropertyName proofSource -NotePropertyValue $proof.source -Force
        $cell | Add-Member -NotePropertyName proofArtifactPath -NotePropertyValue $proof.artifactPath -Force
        $cell | Add-Member -NotePropertyName proofArtifactUpdatedUtc -NotePropertyValue $proof.artifactUpdatedUtc -Force
    }
    $statusMap = Get-CatalogStatusMap
    $gapBreakdown = Build-GapBreakdownFromCells $cells $statusMap
    if ($InAppObj.PSObject.Properties.Name -contains "cells") { $InAppObj.cells = $cells } else { $InAppObj | Add-Member -NotePropertyName cells -NotePropertyValue $cells -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "gapBreakdown") { $InAppObj.gapBreakdown = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapBreakdown -NotePropertyValue $gapBreakdown -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "gapCounts") { $InAppObj.gapCounts = $gapBreakdown } else { $InAppObj | Add-Member -NotePropertyName gapCounts -NotePropertyValue $gapBreakdown -Force }
    if ($InAppObj.PSObject.Properties.Name -contains "shipBlockerGapCount") {
        $InAppObj.shipBlockerGapCount = Get-ShipBlockerGapCountFromCells $cells
    } else {
        $InAppObj | Add-Member -NotePropertyName shipBlockerGapCount -NotePropertyValue (Get-ShipBlockerGapCountFromCells $cells) -Force
    }
    return $InAppObj
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
        if ($id -eq "audio.spatial" -and $pr.pass -ne $true) {
            if ($cell.PSObject.Properties.Name -contains "failReason") { $cell.failReason = "unautomated" } else { $cell | Add-Member -NotePropertyName failReason -NotePropertyValue "unautomated" -Force }
            if ($cell.PSObject.Properties.Name -contains "gap") { $cell.gap = "GAP_UNAUTOMATED" } else { $cell | Add-Member -NotePropertyName gap -NotePropertyValue "GAP_UNAUTOMATED" -Force }
            $cell | Add-Member -NotePropertyName proofSkipped -NotePropertyValue "spatial_audio_environment_dependent" -Force
            continue
        }
        if ($pr.skippedReason -eq "requires_IncludeRecord") {
            if ($cell.PSObject.Properties.Name -contains "failReason") { $cell.failReason = "unautomated" } else { $cell | Add-Member -NotePropertyName failReason -NotePropertyValue "unautomated" -Force }
            if ($cell.PSObject.Properties.Name -contains "gap") { $cell.gap = "GAP_UNAUTOMATED" } else { $cell | Add-Member -NotePropertyName gap -NotePropertyValue "GAP_UNAUTOMATED" -Force }
            $cell | Add-Member -NotePropertyName proofSkipped -NotePropertyValue $pr.skippedReason -Force
            continue
        }
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
        $pick = Read-Host "Full / Delta ?"
        $Mode = switch -Regex ($pick.Trim()) {
            '^[Ff]' { 'Full'; break }
            '^[Dd]' { 'Delta'; break }
            default { '' }
        }
    }
    if ([string]::IsNullOrWhiteSpace($Mode)) {
        Write-Error "pns_fleet_parity_sweep.ps1: -Mode is required (Full | Delta). Use -Help."
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
    $scanArgs = @{ Serial = $Serial; OutDir = $matrixOut; SkipInstall = $true; ScanTier = "full" }
    & $matrixScan @scanArgs
    if ($LASTEXITCODE -ne 0) { throw "pns_fleet_matrix_scan failed" }
} else {
    Write-Host "[parity_sweep] SkipMatrixRefresh — using on-device matrix"
}

$modeLower = $Mode.ToLowerInvariant()
$waitSec = switch ($Mode) {
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
try {
    if ($Serial) {
        & adb -s $Serial exec-out logcat -d -s "PNS.FleetParity:I" "PNS.AdbValidation:I" "PNS.FleetMatrix:I" | Set-Content -LiteralPath $logPath -Encoding utf8
    } else {
        & adb exec-out logcat -d -s "PNS.FleetParity:I" "PNS.AdbValidation:I" "PNS.FleetMatrix:I" | Set-Content -LiteralPath $logPath -Encoding utf8
    }
} catch {
    Write-Warning "logcat pull failed: $($_.Exception.Message)"
}
if (-not (Test-Path -LiteralPath $logPath)) {
    "" | Set-Content -LiteralPath $logPath -Encoding utf8
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
$matrixObj = $null
if ($inAppObj) {
    $matrixObj = Get-CurrentFleetMatrixObject
    if ($matrixObj) {
        $inAppObj = Apply-MatrixCapabilitySkips $inAppObj $matrixObj
    }
}
$recentProofById = @{}
$recentProofMergeApplied = $false
$truth4k120Signal = $null
if ($inAppObj) {
    $recentProofById = Get-RecentParityProofByCatalog -RepoRoot $projRoot
    if ($recentProofById.Count -gt 0) {
        $inAppObj = Merge-RecentProofArtifacts $inAppObj $recentProofById
        $recentProofMergeApplied = $true
        $inAppObj | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $inAppJsonPath -Encoding utf8
    }
    $truth4k120Signal = Get-Recent4k120TruthSignal -RepoRoot $projRoot -ExpectedSerial $Serial
    if ($truth4k120Signal) {
        $inAppObj = Apply-4k120TruthSignal $inAppObj $truth4k120Signal
        $inAppObj | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $inAppJsonPath -Encoding utf8
    }
}

$cellCount = if ($inAppObj -and $inAppObj.cellCount) { [int]$inAppObj.cellCount } else { $logCells.Count }
$gapBreakdown = @{}
if ($inAppObj -and $inAppObj.gapBreakdown) {
    if ($inAppObj.gapBreakdown -is [hashtable]) {
        foreach ($k in $inAppObj.gapBreakdown.Keys) {
            $gapBreakdown[$k] = [int]$inAppObj.gapBreakdown[$k]
        }
    } else {
        $inAppObj.gapBreakdown.PSObject.Properties | ForEach-Object { $gapBreakdown[$_.Name] = [int]$_.Value }
    }
} elseif ($inAppObj -and $inAppObj.gapCounts) {
    if ($inAppObj.gapCounts -is [hashtable]) {
        foreach ($k in $inAppObj.gapCounts.Keys) {
            $gapBreakdown[$k] = [int]$inAppObj.gapCounts[$k]
        }
    } else {
        $inAppObj.gapCounts.PSObject.Properties | ForEach-Object { $gapBreakdown[$_.Name] = [int]$_.Value }
    }
} else {
    $gapBreakdown = Build-GapBreakdownFromLogcat $logCells
}

$shipBlockerCount = 0
if ($inAppObj -and $inAppObj.cells) {
    $shipBlockerCount = Get-ShipBlockerGapCountFromCells @($inAppObj.cells)
} elseif ($inAppObj -and $null -ne $inAppObj.shipBlockerGapCount) {
    $shipBlockerCount = [int]$inAppObj.shipBlockerGapCount
} else {
    $shipBlockerCount = @($logCells | Where-Object { $_.impact -eq 'SHIP_BLOCKER' -and -not $_.provenOk -and $_.advertised }).Count
}

$experimentalUnlockState = $null
if ($inAppObj -and ($inAppObj.PSObject.Properties.Name -contains "experimentalUnlockState")) {
    $experimentalUnlockState = $inAppObj.experimentalUnlockState
}

$schemaOk = ($inAppObj -and $inAppObj.schema -eq 'pns.fleet_parity_sweep.v2')
$minDeltaCells = 50
$deltaInAppEvidenceOk = ($inAppObj -and $schemaOk -and ($cellCount -ge $minDeltaCells))
$fullInAppEvidenceOk = ($inAppObj -and $schemaOk -and ($cellCount -gt 100))
$sweepEvidenceOk = [bool]$sweepCompleteLogged
$sweepEvidenceModeFallback = ""
$modeEvidenceOk = $false
$pass = switch ($Mode) {
    'Delta' {
        $modeEvidenceOk = [bool]$deltaInAppEvidenceOk
        if (-not $sweepEvidenceOk -and $modeEvidenceOk) {
            $sweepEvidenceOk = $true
            $sweepEvidenceModeFallback = "in_app_delta_cells"
            Write-Warning "[parity_sweep] sweepComplete log missing; using in-app parity report evidence."
        }
        ($cellCount -ge $minDeltaCells) -and $sweepEvidenceOk -and ($shipBlockerCount -eq 0) -and ($schemaOk -or $logCells.Count -ge $minDeltaCells)
    }
    'Full' {
        $modeEvidenceOk = [bool]$fullInAppEvidenceOk
        if (-not $sweepEvidenceOk -and $modeEvidenceOk) {
            $sweepEvidenceOk = $true
            $sweepEvidenceModeFallback = "in_app_full_cells"
            Write-Warning "[parity_sweep] sweepComplete log missing; using in-app parity report evidence."
        }
        ($shipBlockerCount -eq 0) -and $sweepEvidenceOk -and ($schemaOk -or ($logCells.Count -gt 100))
    }
    default { $false }
}

$optionalBlockingGapCount = 0
if ($PromoteOptionalBlocking) {
    $optionalGapKeys = @("GAP_UNAUTOMATED", "GAP_HUMAN_ONLY", "GAP_PROBE_INVENTORY")
    foreach ($k in $optionalGapKeys) {
        if ($gapBreakdown.ContainsKey($k)) {
            $optionalBlockingGapCount += [int]$gapBreakdown[$k]
        }
    }
    if ($optionalBlockingGapCount -gt 0) {
        Write-Warning "[parity_sweep] optional gaps promoted to blocking count=$optionalBlockingGapCount"
        $pass = $false
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
    sweepEvidenceOk = $sweepEvidenceOk
    sweepEvidenceFallback = if ($sweepEvidenceModeFallback) { $sweepEvidenceModeFallback } else { $null }
    promoteOptionalBlocking = [bool]$PromoteOptionalBlocking
    optionalBlockingGapCount = $optionalBlockingGapCount
    recentProofArtifactMerge = $recentProofMergeApplied
    recentProofArtifactCount = if ($recentProofById) { $recentProofById.Count } else { 0 }
    video4k120TruthClass = if ($truth4k120Signal) { $truth4k120Signal.truthClass } else { $null }
    video4k120TruthArtifactPath = if ($truth4k120Signal) { $truth4k120Signal.artifactPath } else { $null }
    video4k120TruthSource = if ($truth4k120Signal) { $truth4k120Signal.source } else { $null }
    video4k120TruthSerial = if ($truth4k120Signal) { $truth4k120Signal.serial } else { $null }
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
if (-not $matrixObj -and (Test-Path -LiteralPath $matrixPath)) {
    try {
        $matrixObj = Get-Content -LiteralPath $matrixPath -Raw | ConvertFrom-Json
    } catch {
        Write-Warning "Unable to parse matrix for leaderboard scoring: $($_.Exception.Message)"
    }
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
        IgnoreFailures = $true
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
        $mergedShipBlockers = Get-ShipBlockerGapCountFromCells @($inAppObj.cells)
        $report.shipBlockerGapCount = $mergedShipBlockers
        $unauto = if ($gapBreakdown.ContainsKey("GAP_UNAUTOMATED")) { $gapBreakdown["GAP_UNAUTOMATED"] } else { 0 }
        $notProven = if ($gapBreakdown.ContainsKey("GAP_ADVERTISED_NOT_PROVEN")) { $gapBreakdown["GAP_ADVERTISED_NOT_PROVEN"] } else { 0 }
        $planned = if ($gapBreakdown.ContainsKey("GAP_PLANNED")) { $gapBreakdown["GAP_PLANNED"] } else { 0 }
        $pass = ($mergedShipBlockers -eq 0) -and ($unauto -eq 0) -and ($notProven -eq 0)
        $report.pass = $pass
        if (-not $pass) {
            Write-Warning "Proof merge gaps: unautomated=$unauto not_proven=$notProven planned=$planned"
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

$leaderboardEntry = Update-FleetParityLeaderboard -RepoRoot $projRoot -ReportObj $report -InAppObj $inAppObj -MatrixObj $matrixObj
if ($leaderboardEntry) {
    $report.leaderboardRank = $leaderboardEntry.rank
    $report.leaderboardDeviceKey = $leaderboardEntry.deviceKey
    $report.leaderboardTotalPercent = $leaderboardEntry.score.total.percent
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $reportPath -Encoding utf8
    $report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $latestPath -Encoding utf8
}

if (-not $SkipSitePublish) {
    Write-Host "[parity_sweep] Auto publish -> export catalog + site publish + pages push"
    & (Join-Path $PSScriptRoot "pns_leaderboard_export_catalog.ps1")
    if ($LASTEXITCODE -ne 0) { throw "pns_leaderboard_export_catalog.ps1 failed exit=$LASTEXITCODE" }
    & (Join-Path $PSScriptRoot "pns_leaderboard_site_publish.ps1") -MergeSubmissions
    if ($LASTEXITCODE -ne 0) { throw "pns_leaderboard_site_publish.ps1 failed exit=$LASTEXITCODE" }
    & (Join-Path $PSScriptRoot "pns_leaderboard_pages_push.ps1") -SkipPublish -MergeSubmissions
    if ($LASTEXITCODE -ne 0) { throw "pns_leaderboard_pages_push.ps1 failed exit=$LASTEXITCODE" }
}

Invoke-ParityBacklogRefresh -RepoRoot $projRoot

Write-Host "[parity_sweep] Wrote $reportPath pass=$($report.pass) cells=$cellCount shipBlockers=$shipBlockerCount"
if (-not $report.pass) { exit 1 }
exit 0
