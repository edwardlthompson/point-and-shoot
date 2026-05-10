# Append one row to PROBE_BUILD_PLAN.md Section 5 (Progress log) from milestone6_gate.json evidence.
# Intended for automation after scripts/pns_adb_preview_validate.ps1 -Milestone6Pack writes milestone6_gate.json.

param(
    [Parameter(Mandatory = $true)]
    [string]$GateJson,
    [string]$ProbePlan = "",
    [string]$Item = "",
    [switch]$PassOnly,
    [switch]$WhatIf
)

$ErrorActionPreference = "Stop"

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

if ($PassOnly.IsPresent -and -not $j.pass) {
    Write-Host "[probe_append_section5] skip: milestone6_gate.pass is false (-PassOnly)"
    exit 0
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

$dateUtc = [DateTime]::UtcNow.ToString("yyyy-MM-dd")
# Markdown table row: avoid "|" in double-quoted strings (PowerShell pipe operator).
$newRow = '| ' + $dateUtc + ' | ' + $Item + ' | ' + $evidence + ' |'

$placeholder = '| | *(append next verification here)* | |'
$text = [System.IO.File]::ReadAllText($ProbePlan)
if (-not $text.Contains($placeholder)) {
    throw "Placeholder line not found in $ProbePlan (expected exact: $placeholder)"
}

$replacement = $newRow + "`r`n" + $placeholder
$newText = $text.Replace($placeholder, $replacement)

if ($WhatIf.IsPresent) {
    Write-Host "[probe_append_section5] WHATIF row:"
    Write-Host $newRow
    exit 0
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($ProbePlan, $newText, $utf8NoBom)
Write-Host "[probe_append_section5] appended row to $ProbePlan"
