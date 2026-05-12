# Append one row to PROBE_BUILD_PLAN.md Section 5 (Progress log) from gate JSON evidence.
#
# Supported JSON shapes (schema property or inferred):
#   - pns.milestone6_gate.v1  -  milestone6_gate.json (pns_adb_preview_validate -Milestone6Pack / pns_milestone6_gate.ps1)
#   - pns.mediastore_probe.v1  -  mediastore_probe.json (pns_adb_preview_validate.ps1, not -ChromeUxPack)
#   - pns.super_macro_gate.v1  -  super_macro_gate.json (pns_adb_preview_validate sprint53)
#   - pns.chrome_ux_gate.v1  -  chrome_ux_gate.json (pns_chrome_ux_gate.ps1)
#   - pns.failure_matrix_smoke.v1  -  failure_matrix_smoke.json (pns_failure_matrix_smoke.ps1)
#   - pns.root_capability_adb.v1  -  root_capability_adb.json (pns_root_capability_adb.ps1)

param(
    [Parameter(Mandatory = $true)]
    [string]$GateJson,
    [string]$ProbePlan = "",
    [string]$Item = "",
    [switch]$PassOnly,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

if (-not (Test-Path -LiteralPath $GateJson)) {
    throw "Gate JSON not found: $GateJson"
}

$projRoot = Split-Path -Parent $PSScriptRoot
if (-not $ProbePlan) {
    $ProbePlan = Join-Path $projRoot "PROBE_BUILD_PLAN.md"
}
if (-not (Test-Path -LiteralPath $ProbePlan)) {
    throw "PROBE_BUILD_PLAN.md not found: $ProbePlan"
}

$raw = [System.IO.File]::ReadAllText($GateJson)
$j = $raw | ConvertFrom-Json

$schema = $j.schema
if (-not $schema) {
    if ($null -ne $j.dng50708IfdOk) {
        $schema = "pns.milestone6_gate.v1"
    }
    elseif ($null -ne $j.dcimHasPnsCapture) {
        $schema = "pns.mediastore_probe.v1"
    }
    elseif ($j.scenario -eq "sprint53_super_macro_vv") {
        $schema = "pns.super_macro_gate.v1"
    }
    elseif ($null -ne $j.previewGrantedOk -and $null -ne $j.previewRevokedOk -and $null -eq $j.hostTestsPass) {
        $schema = "pns.failure_matrix_smoke.v1"
    }
    elseif ($null -ne $j.seedOk -and $null -ne $j.hostTestsPass) {
        $schema = "pns.chrome_ux_gate.v1"
    }
    elseif ($null -ne $j.adbShellId -and $null -ne $j.adbRootAttempted) {
        $schema = "pns.root_capability_adb.v1"
    }
}

if (-not $schema) {
    throw "Unsupported gate JSON (missing schema and no known field fingerprint): $GateJson"
}

$adbSerial = "unknown"
try {
    $adbSerial = (& adb get-serialno 2>$null | Select-Object -First 1).ToString().Trim()
    if ([string]::IsNullOrWhiteSpace($adbSerial)) { $adbSerial = "unknown" }
}
catch {
    $adbSerial = "unknown"
}

$fullGate = (Resolve-Path -LiteralPath $GateJson).Path
$relArtifact = $GateJson
if ($fullGate.StartsWith($projRoot, [StringComparison]::OrdinalIgnoreCase)) {
    $relArtifact = $fullGate.Substring($projRoot.Length).TrimStart([char[]]@('\', '/'))
}

$dateUtc = [DateTime]::UtcNow.ToString("yyyy-MM-dd")
$newRow = $null

switch -Wildcard ($schema) {
    "pns.milestone6_gate.v1" {
        if ($PassOnly.IsPresent -and -not $j.pass) {
            Write-Host "`[probe_append_section5] skip: milestone6 pass=false (-PassOnly)"
            exit 0
        }
        if (-not $Item) {
            $pf = if ($j.pass) { "pass" } else { "FAIL" }
            $Item = "**Milestone 6 gate - Milestone6Pack ($pf)**"
        }
        $evidence =
            "``milestone6_gate.json`` pass=$($j.pass); " +
            "dng50708IfdOk=$($j.dng50708IfdOk); lutFpsBudgetOk=$($j.lutFpsBudgetOk); " +
            "calibrateSmoke=$($j.calibrateSmoke); calibrateLiveGrabOk=$($j.calibrateLiveGrabOk); " +
            "glPreviewSmoke=$($j.glPreviewSmoke); stillsLutSeed=$($j.stillsLutSeed); " +
            "adb serial=$adbSerial; artifact=$relArtifact"
        $newRow = '| ' + $dateUtc + ' | ' + $Item + ' | ' + $evidence + ' |'
    }
    "pns.mediastore_probe.v1" {
        if ($PassOnly.IsPresent -and -not $j.dcimHasPnsCapture) {
            Write-Host "`[probe_append_section5] skip: mediastore dcimHasPnsCapture=false (-PassOnly)"
            exit 0
        }
        if (-not $Item) {
            $pf = if ($j.dcimHasPnsCapture) { "dcim ok" } else { "dcim empty" }
            $Item = "**Sprint 7.3  -  MediaStore / DCIM probe ($pf)**"
        }
        $hasDisp = $null -ne $j.PSObject.Properties['mediaTailPnsDisplayNameHits']
        $dispPart = if ($hasDisp) { "mediaTailPnsDisplayNameHits=$($j.mediaTailPnsDisplayNameHits); " } else { "" }
        $evidence =
            "``mediastore_probe.json`` schema=$schema; dcimHasPnsCapture=$($j.dcimHasPnsCapture); " +
            "mediaTailPnsRows=$($j.mediaTailPnsRows); ${dispPart}outDir=$($j.outDir); " +
            "adb serial=$adbSerial; artifact=$relArtifact"
        $newRow = '| ' + $dateUtc + ' | ' + $Item + ' | ' + $evidence + ' |'
    }
    "pns.super_macro_gate.v1" {
        if ($PassOnly.IsPresent -and -not $j.pass) {
            Write-Host "`[probe_append_section5] skip: super_macro_gate pass=false (-PassOnly)"
            exit 0
        }
        if (-not $Item) {
            $pf = if ($j.pass) { "pass" } else { "FAIL" }
            $Item = "**Sprint 5.3  -  Super Macro gate ($pf)**"
        }
        $ml = if ($j.matchedLine) { $j.matchedLine.ToString().Trim() } else { "(none)" }
        if ($ml.Length -gt 220) { $ml = $ml.Substring(0, 217) + "..." }
        $evidence =
            "``super_macro_gate.json`` pass=$($j.pass); ultraWideCameraId=$($j.ultraWideCameraId); " +
            "matchedLine=$ml; adb serial=$adbSerial; artifact=$relArtifact"
        $newRow = '| ' + $dateUtc + ' | ' + $Item + ' | ' + $evidence + ' |'
    }
    "pns.failure_matrix_smoke.v1" {
        if ($PassOnly.IsPresent -and -not $j.pass) {
            Write-Host "`[probe_append_section5] skip: failure_matrix_smoke pass=false (-PassOnly)"
            exit 0
        }
        if (-not $Item) {
            $pf = if ($j.pass) { "pass" } else { "FAIL" }
            $Item = "**Milestone 7.2  -  failure matrix smoke ($pf)**"
        }
        $skip = if ($j.skippedReason) { $j.skippedReason } else { "" }
        $evidence =
            "``failure_matrix_smoke.json`` pass=$($j.pass); previewGrantedOk=$($j.previewGrantedOk); " +
            "previewRevokedOk=$($j.previewRevokedOk); adbConnected=$($j.adbConnected); skippedReason=$skip; " +
            "adb serial=$adbSerial; artifact=$relArtifact"
        $newRow = '| ' + $dateUtc + ' | ' + $Item + ' | ' + $evidence + ' |'
    }
    "pns.root_capability_adb.v1" {
        if ($PassOnly.IsPresent -and -not $j.pass) {
            Write-Host "`[probe_append_section5] skip: root_capability_adb pass=false (-PassOnly)"
            exit 0
        }
        if (-not $Item) {
            $pf = if ($j.pass) { "pass" } else { "FAIL" }
            $Item = "**Sprint 7.5  -  ADB transport root probe ($pf)**"
        }
        $evidence =
            "``root_capability_adb.json`` pass=$($j.pass); uid0FromAdbShell=$($j.uid0FromAdbShell); " +
            "uid0FromSuCommand=$($j.uid0FromSuCommand); adbRootAttempted=$($j.adbRootAttempted); " +
            "roSerialno=$($j.roSerialno); roProductModel=$($j.roProductModel); adb serial=$adbSerial; artifact=$relArtifact"
        $newRow = '| ' + $dateUtc + ' | ' + $Item + ' | ' + $evidence + ' |'
    }
    "pns.chrome_ux_gate.v1" {
        if ($PassOnly.IsPresent -and -not $j.pass) {
            Write-Host "`[probe_append_section5] skip: chrome_ux_gate pass=false (-PassOnly)"
            exit 0
        }
        if (-not $Item) {
            $pf = if ($j.pass) { "pass" } else { "FAIL" }
            $Item = "**Milestone 9  -  chrome_ux_gate ($pf)**"
        }
        $evidence =
            "``chrome_ux_gate.json`` pass=$($j.pass); hostTestsPass=$($j.hostTestsPass); adbConnected=$($j.adbConnected); " +
            "seedOk=$($j.seedOk); safeInsetsOk=$($j.safeInsetsOk); dndPreviewOk=$($j.dndPreviewOk); readoutOk=$($j.readoutOk); " +
            "dualShutterOk=$($j.dualShutterOk); grid7Ok=$($j.grid7Ok); modeDialPopoutOk=$($j.modeDialPopoutOk); " +
            "readoutCaptureOk=$($j.readoutCaptureOk); selfTimerOk=$($j.selfTimerOk); flashQsGrid7Ok=$($j.flashQsGrid7Ok); " +
            "flashPreviewHardwareOk=$($j.flashPreviewHardwareOk); deviceSkipReason=$($j.deviceSkipReason); " +
            "adb serial=$adbSerial; artifact=$relArtifact"
        $newRow = '| ' + $dateUtc + ' | ' + $Item + ' | ' + $evidence + ' |'
    }
    default {
        throw "Unsupported gate JSON schema '$schema' in $GateJson"
    }
}

$placeholder = '| | *(append next verification here)* | |'
$text = [System.IO.File]::ReadAllText($ProbePlan)
if (-not $text.Contains($placeholder)) {
    throw "Placeholder line not found in $ProbePlan (expected exact: $placeholder)"
}

$replacement = $newRow + "`r`n" + $placeholder
$newText = $text.Replace($placeholder, $replacement)

if ($WhatIf.IsPresent) {
    Write-Host "`[probe_append_section5] WHATIF row:"
    Write-Host $newRow
    exit 0
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($ProbePlan, $newText, $utf8NoBom)
Write-Host "`[probe_append_section5] appended row to $ProbePlan (schema=$schema)"
exit 0
