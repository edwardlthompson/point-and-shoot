<#
.SYNOPSIS
  Run **`pns_photo_capture_verify.ps1`** (BUILD_PLAN item **11**) and record outcome for bisect / audits.

.DESCRIPTION
  Invokes the scripted RAW still gate in a child **powershell.exe** so **`Write-Error`** from the verify
  script does not terminate this wrapper. Writes:
  - **`hfr-runs/capture_pipeline_gate_<UTC>/gate.json`** -- full record + paths
  - **`docs/CAPTURE_PIPELINE_VERIFY_LATEST.json`** -- last run (overwrite; safe for CI grep)
  - **`docs/CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl`** -- append-only NDJSON (trim in PRs if large)

  Forwards **`-Serial`**, **`-MaxAttempts`**, **`-WaitSec`**, **`-Fast`**, **`-SweepCameraIds`**, **`-SkipAssemble`**, **`-SkipInstall`**
  to **`pns_photo_capture_verify.ps1`**.

.PARAMETER BisectStep
  Optional label (e.g. **`1`** or **`1-no-still-stabilization`**) correlated with **`docs/REVERTED_FEATURES_RESTORE_LIST.md`**.

.PARAMETER Notes
  Optional free-text note stored in **gate.json** / history.

.PARAMETER NoHistoryAppend
  Do not append to **`docs/CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl`** or overwrite **`docs/CAPTURE_PIPELINE_VERIFY_LATEST.json`**
  (still writes **`hfr-runs/capture_pipeline_gate_*`**).

.EXAMPLE
  .\scripts\pns_capture_pipeline_verify.ps1 -BisectStep 1 -Fast -WaitSec 55

.EXAMPLE
  .\scripts\pns_capture_pipeline_verify.ps1 -SkipAssemble -BisectStep post-fix -Notes "after OIS still revert"
#>
param(
    [string]$Serial = "",
    [int]$MaxAttempts = 30,
    [int]$WaitSec = 55,
    [switch]$Fast,
    [switch]$SweepCameraIds,
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [string]$PreviewStillMode = "",
    [string]$BisectStep = "",
    [string]$Notes = "",
    [switch]$NoHistoryAppend
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) {
    . $resolve -PrependToPath -Quiet
}

$projRoot = Split-Path -Parent $PSScriptRoot
$hfrRuns = Join-Path $projRoot "hfr-runs"
$verifyScript = Join-Path $PSScriptRoot "pns_photo_capture_verify.ps1"
if (-not (Test-Path -LiteralPath $verifyScript)) {
    throw "Missing $verifyScript"
}

$existingVerifyDirs =
    if (Test-Path -LiteralPath $hfrRuns) {
        Get-ChildItem -LiteralPath $hfrRuns -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "photo_capture_verify_*" }
    }
    else {
        @()
    }
$beforeNames = [string[]]($existingVerifyDirs | ForEach-Object { $_.Name })

$argList = [System.Collections.Generic.List[string]]::new()
$argList.Add("-NoProfile")
$argList.Add("-ExecutionPolicy")
$argList.Add("Bypass")
$argList.Add("-File")
$argList.Add($verifyScript)
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    $argList.Add("-Serial")
    $argList.Add($Serial)
}
$argList.Add("-MaxAttempts")
$argList.Add("$MaxAttempts")
$argList.Add("-WaitSec")
$argList.Add("$WaitSec")
if ($Fast) { $argList.Add("-Fast") }
if ($SweepCameraIds) { $argList.Add("-SweepCameraIds") }
if ($SkipAssemble) { $argList.Add("-SkipAssemble") }
if ($SkipInstall) { $argList.Add("-SkipInstall") }
if (-not [string]::IsNullOrWhiteSpace($PreviewStillMode)) {
    $argList.Add("-PreviewStillMode")
    $argList.Add($PreviewStillMode)
}

Write-Host "[capture_pipeline_verify] invoking photo_capture_verify (child process)..."
$p = Start-Process -FilePath "powershell.exe" -ArgumentList $argList -Wait -PassThru -NoNewWindow
$exitCode = 0
try {
    if ($null -ne $p.ExitCode) { $exitCode = [int]$p.ExitCode }
}
catch {
    $exitCode = 1
}

$gitShort = "unknown"
try {
    $ln = & git -C $projRoot rev-parse --short HEAD 2>$null
    if (-not [string]::IsNullOrWhiteSpace($ln)) { $gitShort = [string]$ln.Trim() }
}
catch {
    $gitShort = "unknown"
}

$afterDirs =
    if (Test-Path -LiteralPath $hfrRuns) {
        Get-ChildItem -LiteralPath $hfrRuns -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "photo_capture_verify_*" }
    }
    else {
        @()
    }

$newOnes = $afterDirs | Where-Object { $beforeNames -notcontains $_.Name }
$latestVerify = $newOnes | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $latestVerify) {
    $latestVerify = $afterDirs | Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

$verifyOkPath = $null
$verifyOkRel = $null
if ($null -ne $latestVerify) {
    $cand = Join-Path $latestVerify.FullName "VERIFY_OK.txt"
    if (Test-Path -LiteralPath $cand) {
        $verifyOkPath = $cand
        $verifyOkRel = "hfr-runs/$($latestVerify.Name)/VERIFY_OK.txt"
    }
}

$stamp = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$gateDir = Join-Path $hfrRuns "capture_pipeline_gate_$stamp"
New-Item -ItemType Directory -Force -Path $gateDir | Out-Null

$record = [ordered]@{
    schema       = "pns.capture_pipeline_gate.v1"
    utc          = [DateTime]::UtcNow.ToString("o")
    exitCode     = $exitCode
    pass         = ($exitCode -eq 0)
    bisectStep   = $BisectStep
    gitRevShort  = $gitShort
    notes        = $Notes
    verifyRunDir = if ($null -ne $latestVerify) { "hfr-runs/$($latestVerify.Name)" } else { $null }
    verifyOkFile = $verifyOkRel
    childArgs    = ($argList -join " ")
}

$gateJsonPath = Join-Path $gateDir "gate.json"
($record | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $gateJsonPath -Encoding utf8
Write-Host "[capture_pipeline_verify] wrote $gateJsonPath exitCode=$exitCode"

if (-not $NoHistoryAppend) {
    $docsLatest = Join-Path $projRoot "docs\CAPTURE_PIPELINE_VERIFY_LATEST.json"
    ($record | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath $docsLatest -Encoding utf8
    Write-Host "[capture_pipeline_verify] wrote $docsLatest"

    $historyPath = Join-Path $projRoot "docs\CAPTURE_PIPELINE_VERIFY_HISTORY.jsonl"
    ($record | ConvertTo-Json -Compress -Depth 6) | Add-Content -LiteralPath $historyPath -Encoding utf8
    Write-Host "[capture_pipeline_verify] appended $historyPath"
}

exit $exitCode
