param(
    [Parameter(Mandatory = $true)][string]$SubmissionId,
    [ValidateSet("Approve", "Reject")][string]$Action = "Approve",
    [switch]$Help
)

$ErrorActionPreference = "Stop"
if ($Help) {
    Write-Host "pns_leaderboard_submission_review.ps1 -SubmissionId <uuid> -Action Approve|Reject"
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$pending = Join-Path $repoRoot "docs\leaderboard\submissions\pending\$SubmissionId.json"
$approved = Join-Path $repoRoot "docs\leaderboard\submissions\approved\$SubmissionId.json"
$rejected = Join-Path $repoRoot "docs\leaderboard\submissions\rejected\$SubmissionId.json"

if (-not (Test-Path -LiteralPath $pending)) {
    Write-Error "Pending submission not found: $pending"
    exit 1
}

if ($Action -eq "Approve") {
    Move-Item -LiteralPath $pending -Destination $approved -Force
    Write-Host "Approved -> $approved"
} else {
    Move-Item -LiteralPath $pending -Destination $rejected -Force
    Write-Host "Rejected -> $rejected"
}
exit 0
