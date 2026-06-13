# Point & Shoot — PERFORMANCE_BUDGETS.md ↔ PerfBudget.kt drift gate (host-only).
# Milestone T Sprint T.8. Wired into pns_verify_toolchain.ps1.
#
# Usage:
#   .\scripts\pns_perf_budget_host_gate.ps1
#   .\scripts\pns_perf_budget_host_gate.ps1 -RunPerfBudgetTest

param(
  [string]$ProjectRoot = "",
  [switch]$RunPerfBudgetTest
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

$budgetMd = Join-Path $ProjectRoot "PERFORMANCE_BUDGETS.md"
$perfKt = Join-Path $ProjectRoot "app/src/main/java/dev/pointandshoot/PerfBudget.kt"
$perfTest = Join-Path $ProjectRoot "app/src/test/java/dev/pointandshoot/PerfBudgetTest.kt"

foreach ($required in @($budgetMd, $perfKt, $perfTest)) {
  if (-not (Test-Path -LiteralPath $required)) {
    Write-Error "Missing required file: $required"
  }
}

$md = [System.IO.File]::ReadAllText($budgetMd)
$kt = [System.IO.File]::ReadAllText($perfKt)
$failures = New-Object System.Collections.Generic.List[string]

function Get-KotlinDefault {
  param([string]$Name)
  $pattern = "const val ${Name}:\s*(?:Long|Int)\s*=\s*([0-9_]+)L?"
  if ($kt -match $pattern) {
    return [int64]($Matches[1].Replace('_', ''))
  }
  $failures.Add("FAIL: PerfBudget.Defaults.$Name not found in PerfBudget.kt")
  return $null
}

function Test-MarkdownContains {
  param(
    [string]$Label,
    [string[]]$Patterns
  )
  foreach ($p in $Patterns) {
    if ($md -notmatch [regex]::Escape($p)) {
      $failures.Add("FAIL: PERFORMANCE_BUDGETS.md missing '$p' for $Label")
    }
  }
}

$pairs = @(
  @{ Name = "COLD_START_MS"; Patterns = @("<= 800 ms", "Cold start to first preview frame") }
  @{ Name = "DNG_SAVE_STANDARD_MS"; Patterns = @("<= 250 ms", "DNG written to MediaStore") }
  @{ Name = "DNG_SAVE_ULTRAMAX_MS"; Patterns = @("<= 600 ms", "RAW12 DNG written") }
  @{ Name = "BRACKET7_TOTAL_MS"; Patterns = @("<= 4 s", "Full 7-shot bracket complete") }
  @{ Name = "POST_READOUT_TICK_TARGET_MS"; Patterns = @("30 ms +/- 5 ms", "haptic tick") }
  @{ Name = "POST_READOUT_TICK_TOLERANCE_MS"; Patterns = @("30 ms +/- 5 ms") }
  @{ Name = "ENCODE_LANE_DRAIN_WAIT_MS"; Patterns = @("<= 200 ms", "PerfBudget.Defaults.ENCODE_LANE_DRAIN_WAIT_MS") }
  @{ Name = "STILL_IMAGE_READER_MAX_IMAGES"; Patterns = @("PerfBudget.Defaults.STILL_IMAGE_READER_MAX_IMAGES") }
  @{ Name = "LUT_SHADER_PER_FRAME_1080P_MS"; Patterns = @("<= 2 ms / frame", "LUT_SHADER_PER_FRAME_1080P_MS") }
  @{ Name = "LUT_CPU_STILL_12MP_MS"; Patterns = @("<= 80 ms", "LUT_CPU_STILL_12MP_MS") }
)

foreach ($pair in $pairs) {
  $value = Get-KotlinDefault -Name $pair.Name
  if ($null -eq $value) { continue }
  Test-MarkdownContains -Label $pair.Name -Patterns $pair.Patterns
}

# Numeric cross-checks where markdown states the budget inline (not only via Kotlin symbol).
$numericChecks = @(
  @{ Name = "COLD_START_MS"; Needle = "<= 800 ms"; Expected = 800 }
  @{ Name = "DNG_SAVE_STANDARD_MS"; Needle = "Readout complete -> DNG written to MediaStore | <= 250 ms"; Expected = 250 }
  @{ Name = "DNG_SAVE_ULTRAMAX_MS"; Needle = "Readout complete -> RAW12 DNG written | <= 600 ms"; Expected = 600 }
  @{ Name = "BRACKET7_TOTAL_MS"; Needle = "Full 7-shot bracket complete | <= 4 s"; Expected = 4000 }
  @{ Name = "ENCODE_LANE_DRAIN_WAIT_MS"; Needle = "BKT pre-bracket encode drain wait | <= 200 ms"; Expected = 200 }
  @{ Name = "LUT_SHADER_PER_FRAME_1080P_MS"; Needle = "Preview LUT shader (1920x1080"; Expected = 2 }
  @{ Name = "LUT_CPU_STILL_12MP_MS"; Needle = "Still LUT CPU pass (12 MP"; Expected = 80 }
)

foreach ($check in $numericChecks) {
  $value = Get-KotlinDefault -Name $check.Name
  if ($null -eq $value) { continue }
  if ([int64]$value -ne [int64]$check.Expected) {
    $failures.Add(
      "FAIL: PerfBudget.Defaults.$($check.Name)=$value but PERFORMANCE_BUDGETS.md implies $($check.Expected) near '$($check.Needle)'"
    )
  }
}

# PerfBudgetTest must pin the same defaults (grep contract rows).
$testSrc = [System.IO.File]::ReadAllText($perfTest)
foreach ($name in @(
    "COLD_START_MS", "DNG_SAVE_STANDARD_MS", "DNG_SAVE_ULTRAMAX_MS", "BRACKET7_TOTAL_MS",
    "POST_READOUT_TICK_TARGET_MS", "POST_READOUT_TICK_TOLERANCE_MS", "ENCODE_LANE_DRAIN_WAIT_MS",
    "STILL_IMAGE_READER_MAX_IMAGES", "LUT_SHADER_PER_FRAME_1080P_MS", "LUT_CPU_STILL_12MP_MS"
  )) {
  if ($testSrc -notmatch [regex]::Escape($name)) {
    $failures.Add("FAIL: PerfBudgetTest.kt does not reference Defaults.$name")
  }
}

if ($RunPerfBudgetTest.IsPresent) {
  $gradlewBat = Join-Path $ProjectRoot "gradlew.bat"
  $gradlewSh = Join-Path $ProjectRoot "gradlew"
  $gradlew = if ((Test-Path -LiteralPath $gradlewBat) -and ($env:OS -match '(?i)Windows')) { $gradlewBat } else { $gradlewSh }
  if (-not (Test-Path -LiteralPath $gradlew)) {
    $failures.Add("FAIL: gradlew not found for PerfBudgetTest")
  } else {
    Push-Location $ProjectRoot
    try {
      if ($gradlew -like "*.bat") {
        & $gradlew :app:testDebugUnitTest --tests "dev.pointandshoot.PerfBudgetTest" --no-daemon
      } else {
        & bash $gradlew :app:testDebugUnitTest --tests "dev.pointandshoot.PerfBudgetTest" --no-daemon
      }
      if ($LASTEXITCODE -ne 0) {
        $failures.Add("FAIL: :app:testDebugUnitTest PerfBudgetTest exit code $LASTEXITCODE")
      }
    } finally {
      Pop-Location
    }
  }
}

if ($failures.Count -gt 0) {
  Write-Host "PERF BUDGET HOST GATE: FAIL ($($failures.Count) issue(s))"
  foreach ($f in $failures) { Write-Host $f }
  exit 1
}

Write-Host "PERF BUDGET HOST GATE: PASS (PerfBudget.kt ↔ PERFORMANCE_BUDGETS.md ↔ PerfBudgetTest contract)"
exit 0
