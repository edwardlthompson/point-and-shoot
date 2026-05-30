# Milestone 18.6 — diff two parity_report.json artifacts.

param(
    [Parameter(Mandatory = $true)][string]$Before,
    [Parameter(Mandatory = $true)][string]$After,
    [string]$OutPath = ""
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $Before)) { throw "Missing Before: $Before" }
if (-not (Test-Path -LiteralPath $After)) { throw "Missing After: $After" }

$beforeObj = Get-Content -LiteralPath $Before -Raw | ConvertFrom-Json
$afterObj = Get-Content -LiteralPath $After -Raw | ConvertFrom-Json

$lines = @(
    "# Fleet Parity diff",
    "",
    "| Field | Before | After |",
    "|-------|--------|-------|",
    "| pass | $($beforeObj.pass) | $($afterObj.pass) |",
    "| mode | $($beforeObj.mode) | $($afterObj.mode) |",
    "| cellCount | $($beforeObj.cellCount) | $($afterObj.cellCount) |",
    "| gapAdvertisedNotProven | $($beforeObj.gapAdvertisedNotProven) | $($afterObj.gapAdvertisedNotProven) |",
    ""
)

$text = $lines -join "`n"
if ($OutPath) {
    Set-Content -LiteralPath $OutPath -Value $text -Encoding utf8
    Write-Host "Wrote $OutPath"
} else {
    Write-Host $text
}
