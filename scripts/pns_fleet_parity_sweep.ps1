# Milestone 18.6 — Fleet Parity Sweep (FPS).
#
# -Mode is REQUIRED: Quick | Full | Delta (exit 2 without it).
# Refreshes matrix, cold-starts parity sweep ADB extras, greps PNS.FleetParity / parityCell=.
#
# Artifacts: hfr-runs/parity_sweep_*/parity_report.json + docs/FLEET_PARITY_LATEST.json

param(
    [string]$Serial = "",
    [ValidateSet("Quick", "Full", "Delta")]
    [string]$Mode = "",
    [string]$OutDir = "",
    [switch]$IncludeRecord,
    [switch]$Interactive,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_fleet_parity_sweep.ps1 — Fleet Parity Sweep (FPS)

  -Mode Quick   (~3-5 min) CI smoke; scripted rows only
  -Mode Full    (~15-30 min) all catalog rows; optional -IncludeRecord
  -Mode Delta   rows changed since last catalog/matrix version

  -Interactive  prompt Quick/Full/Delta when -Mode omitted (human convenience)
"@
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

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\parity_sweep_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

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

if (-not (Test-AdbAuthorizedDevice)) {
    $stub = [ordered]@{
        schema       = "pns.fleet_parity_sweep.v1"
        pass         = $true
        skippedReason = "no_adb_device"
        mode         = $Mode
        timestampUtc = [DateTime]::UtcNow.ToString("o")
        outDir       = $OutDir
    }
    $stub | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "parity_report.json") -Encoding utf8
    Write-Host "[parity_sweep] No ADB device — wrote stub parity_report.json"
    exit 0
}

if ($AssembleDebug -or (-not $SkipInstall -and -not (Test-Path -LiteralPath $apk))) {
    & $gradlewHelper ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}

$matrixOut = Join-Path $OutDir "matrix"
New-Item -ItemType Directory -Force -Path $matrixOut | Out-Null
Write-Host "[parity_sweep] matrix refresh -> $matrixOut"
& $matrixScan -Serial $Serial -OutDir $matrixOut -SkipInstall:$(Test-Path -LiteralPath $apk)
if ($LASTEXITCODE -ne 0) { throw "pns_fleet_matrix_scan failed" }

$modeLower = $Mode.ToLowerInvariant()
$waitSec = switch ($Mode) {
    'Quick' { 90 }
    'Full' { 180 }
    'Delta' { 60 }
    default { 45 }
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

$logPath = Join-Path $OutDir "logcat_parity.txt"
if ($Serial) {
    & adb -s $Serial exec-out logcat -d -s "PNS.FleetParity:I" "PNS.AdbValidation:I" "PNS.FleetMatrix:I" | Set-Content -LiteralPath $logPath -Encoding utf8
} else {
    & adb exec-out logcat -d -s "PNS.FleetParity:I" "PNS.AdbValidation:I" "PNS.FleetMatrix:I" | Set-Content -LiteralPath $logPath -Encoding utf8
}

$cells = @()
foreach ($line in Get-Content -LiteralPath $logPath -ErrorAction SilentlyContinue) {
    if ($line -match 'parityCell=([^\s]+)') {
        $cells += $Matches[1]
    }
}

$sweepModeLogged = Select-String -Path $logPath -Pattern 'sweepMode=' -SimpleMatch -ErrorAction SilentlyContinue
$sweepCompleteLogged = Select-String -Path $logPath -Pattern 'sweepComplete' -SimpleMatch -ErrorAction SilentlyContinue
$gapAdvertised = ($cells | Where-Object { $_ -match 'provenOk=false' }).Count
$gapDelivery = Select-String -Path $logPath -Pattern 'deliveryMismatch' -ErrorAction SilentlyContinue

$pass = switch ($Mode) {
    'Quick' {
        ($cells.Count -gt 0) -and ([bool]$sweepModeLogged) -and ($null -eq $gapDelivery)
    }
    default {
        ($gapAdvertised -eq 0) -and ($null -eq $gapDelivery)
    }
}

$report = [ordered]@{
    schema         = "pns.fleet_parity_sweep.v1"
    pass           = $pass
    mode           = $Mode
    includeRecord  = [bool]$IncludeRecord
    serial         = $Serial
    timestampUtc   = [DateTime]::UtcNow.ToString("o")
    outDir         = $OutDir
    cellCount      = $cells.Count
    gapAdvertisedNotProven = $gapAdvertised
    sweepModeLogged = [bool]$sweepModeLogged
    sweepCompleteLogged = [bool]$sweepCompleteLogged
    logPath        = $logPath
}

$reportPath = Join-Path $OutDir "parity_report.json"
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $reportPath -Encoding utf8

$md = @(
    "# Fleet Parity Sweep — $Mode",
    "",
    "- **Pass:** $($report.pass)",
    "- **Cells logged:** $($report.cellCount)",
    "- **GAP_ADVERTISED_NOT_PROVEN (heuristic):** $gapAdvertised",
    "- **Log:** ``$logPath``",
    ""
)
$md | Set-Content -LiteralPath (Join-Path $OutDir "parity_report.md") -Encoding utf8

$closureLines = @(
    "# Parity closure plan — $Mode",
    "",
    "- **Gap advertised-not-proven:** $gapAdvertised",
    "- **Delivery mismatch lines:** $(if ($gapDelivery) { ($gapDelivery | Measure-Object).Count } else { 0 })",
    "",
    "## Next steps",
    "1. Re-run matrix scan after capability changes.",
    "2. For each `provenOk=false` cell, verify catalog evaluator vs matrix featureGates.",
    "3. Full mode with `-IncludeRecord` for delivery_mismatch rows.",
    ""
)
$closureLines | Set-Content -LiteralPath (Join-Path $OutDir "parity_closure_plan.md") -Encoding utf8

$latestPath = Join-Path $projRoot "docs\FLEET_PARITY_LATEST.json"
$report | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $latestPath -Encoding utf8

$historyPath = Join-Path $projRoot "docs\FLEET_PARITY_HISTORY.jsonl"
Add-Content -LiteralPath $historyPath -Value ($report | ConvertTo-Json -Compress -Depth 6)

Invoke-Adb @("shell", "am", "force-stop", $pkg)

Write-Host "[parity_sweep] Wrote $reportPath pass=$($report.pass)"
if (-not $report.pass) { exit 1 }
exit 0
