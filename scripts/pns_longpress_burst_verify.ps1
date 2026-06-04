<#
.SYNOPSIS
  Targeted USB gate for shutter long-press burst timing + RAW burst profile behavior.

.DESCRIPTION
  Runs long-press burst scenarios for both JPEG and RAW under paced/aggressive
  pipeline strategies, then validates that each scenario executed and saved output.

  Captures logcat proof and writes a summary JSON/MD bundle under hfr-runs/.
#>
param(
    [string]$Serial = "",
    [int]$IntervalMs = 17,
    [int]$HoldMs = 2200,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"

$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

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
    if ($fromEnv) { $Serial = $fromEnv }
}

$adbPrefix = @()
if ($Serial) { $adbPrefix = @("-s", $Serial) }

$projRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    & adb @adbPrefix install -r -t $apk 2>&1 | Out-Null
}
& adb @adbPrefix shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
& adb @adbPrefix shell pm grant $pkg android.permission.READ_MEDIA_IMAGES 2>$null | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\longpress_burst_verify_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_longpress_burst.txt"

function Run-LongPressScenario(
    [string]$Label,
    [string]$BurstFile,
    [string]$BurstStrategy,
    [int]$BurstIntervalMs,
    [int]$HoldMs
) {
    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
    Start-Sleep -Milliseconds 700
    & adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
        --activity-clear-task `
        --es pns_screen preview `
        --es pns_preview_burst_file $BurstFile `
        --es pns_preview_burst_strategy $BurstStrategy `
        --ei pns_preview_burst_interval_ms $BurstIntervalMs `
        --ei pns_preview_longpress_burst_hold_ms $HoldMs `
        --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null
    Write-Host "[longpress_burst_verify] $Label file=$BurstFile strategy=$BurstStrategy intervalMs=$BurstIntervalMs holdMs=$HoldMs"
    $waitMs = [Math]::Max($HoldMs + 22000, 26000)
    Start-Sleep -Milliseconds $waitMs
}

& adb @adbPrefix shell logcat -c 2>$null | Out-Null

Run-LongPressScenario -Label "jpeg-aggressive" -BurstFile "jpeg" -BurstStrategy "aggressive" -BurstIntervalMs $IntervalMs -HoldMs $HoldMs
Run-LongPressScenario -Label "jpeg-paced" -BurstFile "jpeg" -BurstStrategy "paced" -BurstIntervalMs $IntervalMs -HoldMs $HoldMs
Run-LongPressScenario -Label "raw-aggressive" -BurstFile "raw" -BurstStrategy "aggressive" -BurstIntervalMs $IntervalMs -HoldMs $HoldMs
Run-LongPressScenario -Label "raw-paced" -BurstFile "raw" -BurstStrategy "paced" -BurstIntervalMs $IntervalMs -HoldMs $HoldMs

& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.ChromeUx:I" "PNS.CaptureStill:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8

# Mandatory cleanup: never leave camera app running after automation.
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$starts = [regex]::Matches($hay, "PNS\.AdbValidation:\s+longPressBurst start intervalMs=(\d+) profile=([A-Za-z]+) strategy=([a-z]+)")
$shots = [regex]::Matches($hay, "PNS\.AdbValidation:\s+longPressBurst shot profile=([A-Za-z]+) strategy=([a-z_]+) intervalMs=(\d+) raw=([A-Za-z]+) jpeg=([A-Za-z]+)")
$finishes = [regex]::Matches($hay, "PNS\.AdbValidation:\s+longPressBurst finished profile=([A-Za-z]+) strategy=([a-z_]+) captured=(\d+) saved=(\d+) savePending=(\d+) drops=(\d+) captureLatBuckets=le100:(\d+),le250:(\d+),le500:(\d+),gt500:(\d+)")
$captureLatencyEvents = [regex]::Matches($hay, "PNS\.AdbValidation:\s+longPressBurst captureLatencyMs=(\d+) profile=([A-Za-z]+) strategy=([a-z_]+)")
$shutterSound = [regex]::Matches($hay, "PNS\.AdbValidation:\s+shutterSound ok=true")

$jpegSeen = $false
$rawSeen = $false
$aggressiveSeen = $false
$pacedSeen = $false
$singleFormatOnly = $true
foreach ($m in $shots) {
    $raw = $m.Groups[4].Value
    $jpeg = $m.Groups[5].Value
    if ($raw -ne "Off" -and $jpeg -eq "Off") { $rawSeen = $true }
    if ($raw -eq "Off" -and $jpeg -ne "Off") { $jpegSeen = $true }
    $rawOnly = ($raw -ne "Off" -and $jpeg -eq "Off")
    $jpegOnly = ($raw -eq "Off" -and $jpeg -ne "Off")
    if (-not ($rawOnly -or $jpegOnly)) { $singleFormatOnly = $false }
}
foreach ($m in $starts) {
    $strategy = $m.Groups[3].Value
    if ($strategy -eq "aggressive") { $aggressiveSeen = $true }
    if ($strategy -eq "paced") { $pacedSeen = $true }
}

$capturedCounts = @()
$savedCounts = @()
$savePendingCounts = @()
$dropCounts = @()
foreach ($m in $finishes) {
    $capturedCounts += [int]$m.Groups[3].Value
    $savedCounts += [int]$m.Groups[4].Value
    $savePendingCounts += [int]$m.Groups[5].Value
    $dropCounts += [int]$m.Groups[6].Value
}
$capturedAny = ($capturedCounts | Measure-Object -Sum).Sum
$savedAny = ($savedCounts | Measure-Object -Sum).Sum
$savePendingAny = ($savePendingCounts | Measure-Object -Sum).Sum
$dropsAny = ($dropCounts | Measure-Object -Sum).Sum

$scenarioMetricsMap = @{}
foreach ($m in $starts) {
    $profile = $m.Groups[2].Value
    $strategy = $m.Groups[3].Value
    $label = "$(if ($profile -eq 'ProcessedOnly') { 'jpeg' } else { 'raw' })-$strategy"
    if (-not $scenarioMetricsMap.ContainsKey($label)) {
        $scenarioMetricsMap[$label] = [ordered]@{
            label = $label
            profile = $profile
            strategy = $strategy
            shots = 0
            captured = 0
            saved = 0
            savePending = 0
            drops = 0
            capturedFps = 0.0
            savedFps = 0.0
            dropRatePct = 0.0
            latencyLe100 = 0
            latencyLe250 = 0
            latencyLe500 = 0
            latencyGt500 = 0
        }
    }
}
foreach ($m in $shots) {
    $profile = $m.Groups[1].Value
    $strategy = $m.Groups[2].Value
    $label = "$(if ($profile -eq 'ProcessedOnly') { 'jpeg' } else { 'raw' })-$strategy"
    if (-not $scenarioMetricsMap.ContainsKey($label)) {
        $scenarioMetricsMap[$label] = [ordered]@{
            label = $label
            profile = $profile
            strategy = $strategy
            shots = 0
            captured = 0
            saved = 0
            savePending = 0
            drops = 0
            capturedFps = 0.0
            savedFps = 0.0
            dropRatePct = 0.0
            latencyLe100 = 0
            latencyLe250 = 0
            latencyLe500 = 0
            latencyGt500 = 0
        }
    }
    $scenarioMetricsMap[$label].shots++
}
foreach ($m in $finishes) {
    $profile = $m.Groups[1].Value
    $strategy = $m.Groups[2].Value
    $label = "$(if ($profile -eq 'ProcessedOnly') { 'jpeg' } else { 'raw' })-$strategy"
    if (-not $scenarioMetricsMap.ContainsKey($label)) { continue }
    $scenarioMetricsMap[$label].captured += [int]$m.Groups[3].Value
    $scenarioMetricsMap[$label].saved += [int]$m.Groups[4].Value
    $scenarioMetricsMap[$label].savePending += [int]$m.Groups[5].Value
    $scenarioMetricsMap[$label].drops += [int]$m.Groups[6].Value
    $scenarioMetricsMap[$label].latencyLe100 += [int]$m.Groups[7].Value
    $scenarioMetricsMap[$label].latencyLe250 += [int]$m.Groups[8].Value
    $scenarioMetricsMap[$label].latencyLe500 += [int]$m.Groups[9].Value
    $scenarioMetricsMap[$label].latencyGt500 += [int]$m.Groups[10].Value
}
foreach ($m in $captureLatencyEvents) {
    $lat = [int]$m.Groups[1].Value
    $profile = $m.Groups[2].Value
    $strategy = $m.Groups[3].Value
    $label = "$(if ($profile -eq 'ProcessedOnly') { 'jpeg' } else { 'raw' })-$strategy"
    if (-not $scenarioMetricsMap.ContainsKey($label)) { continue }
    if ($lat -le 100) { $scenarioMetricsMap[$label].latencyLe100++ }
    elseif ($lat -le 250) { $scenarioMetricsMap[$label].latencyLe250++ }
    elseif ($lat -le 500) { $scenarioMetricsMap[$label].latencyLe500++ }
    else { $scenarioMetricsMap[$label].latencyGt500++ }
}
$scenarioMetrics = @($scenarioMetricsMap.Values)
foreach ($m in $scenarioMetrics) {
    $m.capturedFps = [Math]::Round(($m.captured * 1000.0) / [Math]::Max($HoldMs, 1), 2)
    $m.savedFps = [Math]::Round(($m.saved * 1000.0) / [Math]::Max($HoldMs, 1), 2)
    $shotDenominator = [Math]::Max($m.shots, 1)
    $dropEstimate = [Math]::Max($m.shots - $m.captured, 0)
    if ($m.drops -lt $dropEstimate) {
        $m.drops = $dropEstimate
    }
    $m.dropRatePct = [Math]::Round(($m.drops * 100.0) / $shotDenominator, 2)
}

$winnerJpeg = $null
$winnerRaw = $null
if ($scenarioMetrics.Count -gt 0) {
    $jpeg = @($scenarioMetrics | Where-Object { $_.label -like "jpeg-*" })
    $raw = @($scenarioMetrics | Where-Object { $_.label -like "raw-*" })
    if ($jpeg.Count -gt 0) { $winnerJpeg = ($jpeg | Sort-Object savedFps -Descending | Select-Object -First 1).label }
    if ($raw.Count -gt 0) { $winnerRaw = ($raw | Sort-Object savedFps -Descending | Select-Object -First 1).label }
}

$pass =
    ($starts.Count -ge 4) -and
    ($shots.Count -ge 4) -and
    $jpegSeen -and
    $rawSeen -and
    $aggressiveSeen -and
    $pacedSeen -and
    $singleFormatOnly -and
    ($shutterSound.Count -ge 3) -and
    ($capturedAny -ge 1) -and
    ($savedAny -ge 1)

$summary = [ordered]@{
    schema = "pns.longpress_burst_verify.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    serial = if ($Serial) { $Serial } else { "default" }
    pass = $pass
    config = [ordered]@{
        intervalMs = $IntervalMs
        holdMs = $HoldMs
    }
    checks = [ordered]@{
        startEvents = $starts.Count
        shotEvents = $shots.Count
        finishEvents = $finishes.Count
        shutterSoundEvents = $shutterSound.Count
        jpegSeen = $jpegSeen
        rawSeen = $rawSeen
        aggressiveSeen = $aggressiveSeen
        pacedSeen = $pacedSeen
        singleFormatOnly = $singleFormatOnly
        capturedAny = $capturedAny
        savedAny = $savedAny
        savePendingAny = $savePendingAny
        dropsAny = $dropsAny
    }
    metrics = [ordered]@{
        scenario = $scenarioMetrics
        winnerJpegBySavedFps = $winnerJpeg
        winnerRawBySavedFps = $winnerRaw
    }
    artifacts = [ordered]@{
        logcat = $logPath
    }
}
$summaryPath = Join-Path $outDir "longpress_burst_verify_summary.json"
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8

$md = @(
    "# Long-press burst verify",
    "",
    "- **PASS:** $pass",
    "- **start events:** $($starts.Count)",
    "- **shot events:** $($shots.Count)",
    "- **finish events:** $($finishes.Count)",
    "- **shutter sound events:** $($shutterSound.Count)",
    "- **jpeg shot seen:** $jpegSeen",
    "- **raw shot seen:** $rawSeen",
    "- **aggressive strategy seen:** $aggressiveSeen",
    "- **paced strategy seen:** $pacedSeen",
    "- **single format only (RAW xor JPEG):** $singleFormatOnly",
    "- **captured total:** $capturedAny",
    "- **saved total:** $savedAny",
    "- **save-pending total at stop:** $savePendingAny",
    "- **drop total:** $dropsAny",
    "- **winner JPEG (saved fps):** $(if ($winnerJpeg) { $winnerJpeg } else { 'n/a' })",
    "- **winner RAW (saved fps):** $(if ($winnerRaw) { $winnerRaw } else { 'n/a' })",
    "",
    "## Scenario metrics",
    "",
    "| Scenario | Shots | Captured | Saved | Pending | Drops | Drop % | Captured fps | Saved fps | Lat <=100 | Lat <=250 | Lat <=500 | Lat >500 |",
    "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|"
)
foreach ($m in $scenarioMetrics) {
    $md += "| $($m.label) | $($m.shots) | $($m.captured) | $($m.saved) | $($m.savePending) | $($m.drops) | $($m.dropRatePct) | $($m.capturedFps) | $($m.savedFps) | $($m.latencyLe100) | $($m.latencyLe250) | $($m.latencyLe500) | $($m.latencyGt500) |"
}
$md += @(
    "",
    "Artifact log: $logPath",
    "Summary JSON: $summaryPath"
)
$mdPath = Join-Path $outDir "longpress_burst_verify_summary.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "LONGPRESS_BURST_VERIFY: pass=$pass starts=$($starts.Count) shots=$($shots.Count) finishes=$($finishes.Count) shutter=$($shutterSound.Count) jpegSeen=$jpegSeen rawSeen=$rawSeen aggressiveSeen=$aggressiveSeen pacedSeen=$pacedSeen singleFormatOnly=$singleFormatOnly capturedAny=$capturedAny savedAny=$savedAny savePendingAny=$savePendingAny dropsAny=$dropsAny winnerJpeg=$winnerJpeg winnerRaw=$winnerRaw"
Write-Host "Artifacts: $outDir"
if (-not $pass) { exit 1 }
exit 0

