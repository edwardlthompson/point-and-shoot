<#
.SYNOPSIS
  Targeted USB gate for shutter long-press burst timing + RAW burst profile behavior.

.DESCRIPTION
  Runs two long-press scenarios via adb input:
    1) Fast interval (default 150 ms)  -> Auto profile should down-tier RAW off
    2) Slow interval (default 800 ms)  -> Auto profile should include RAW (Ultra)

  Captures logcat proof and writes a summary JSON/MD bundle under hfr-runs/.
#>
param(
    [string]$Serial = "",
    [int]$FastIntervalMs = 150,
    [int]$SlowIntervalMs = 800,
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
    [int]$BurstIntervalMs,
    [int]$HoldMs
) {
    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
    Start-Sleep -Milliseconds 700
    & adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
        --activity-clear-task `
        --es pns_screen preview `
        --ei pns_preview_burst_interval_ms $BurstIntervalMs `
        --ei pns_preview_longpress_burst_hold_ms $HoldMs `
        --es pns_preview_imaging_profile standard_pro 2>&1 | Out-Null
    Write-Host "[longpress_burst_verify] $Label intervalMs=$BurstIntervalMs holdMs=$HoldMs (adb automation extra)"
    $waitMs = [Math]::Max($HoldMs + 8000, 10000)
    Start-Sleep -Milliseconds $waitMs
}

& adb @adbPrefix shell logcat -c 2>$null | Out-Null

Run-LongPressScenario -Label "fast" -BurstIntervalMs $FastIntervalMs -HoldMs $HoldMs
Run-LongPressScenario -Label "slow" -BurstIntervalMs $SlowIntervalMs -HoldMs $HoldMs

& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.ChromeUx:I" "PNS.CaptureStill:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8

# Mandatory cleanup: never leave camera app running after automation.
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$starts = [regex]::Matches($hay, "longPressBurst start intervalMs=(\d+) profile=([A-Za-z]+)")
$shots = [regex]::Matches($hay, "longPressBurst shot profile=([A-Za-z]+) intervalMs=(\d+) raw=([A-Za-z]+) jpeg=([A-Za-z]+)")
$finishes = [regex]::Matches($hay, "longPressBurst finished saved=(\d+)")

$fastRawOff = $false
$slowRawOn = $false
foreach ($m in $shots) {
    $interval = [int]$m.Groups[2].Value
    $raw = $m.Groups[3].Value
    if ($interval -eq $FastIntervalMs -and $raw -eq "Off") { $fastRawOff = $true }
    if ($interval -eq $SlowIntervalMs -and $raw -ne "Off") { $slowRawOn = $true }
}

$savedCounts = @()
foreach ($m in $finishes) { $savedCounts += [int]$m.Groups[1].Value }
$savedAny = ($savedCounts | Measure-Object -Sum).Sum

$pass =
    ($starts.Count -ge 2) -and
    ($shots.Count -ge 2) -and
    ($finishes.Count -ge 2) -and
    $fastRawOff -and
    $slowRawOn -and
    ($savedAny -ge 2)

$summary = [ordered]@{
    schema = "pns.longpress_burst_verify.v1"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    serial = if ($Serial) { $Serial } else { "default" }
    pass = $pass
    config = [ordered]@{
        fastIntervalMs = $FastIntervalMs
        slowIntervalMs = $SlowIntervalMs
        holdMs = $HoldMs
    }
    checks = [ordered]@{
        startEvents = $starts.Count
        shotEvents = $shots.Count
        finishEvents = $finishes.Count
        fastIntervalRawOff = $fastRawOff
        slowIntervalRawOn = $slowRawOn
        savedAny = $savedAny
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
    "- **fast interval RAW off:** $fastRawOff",
    "- **slow interval RAW on:** $slowRawOn",
    "- **saved total:** $savedAny",
    "",
    "Artifact log: $logPath",
    "Summary JSON: $summaryPath"
)
$mdPath = Join-Path $outDir "longpress_burst_verify_summary.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "LONGPRESS_BURST_VERIFY: pass=$pass starts=$($starts.Count) shots=$($shots.Count) finishes=$($finishes.Count) fastRawOff=$fastRawOff slowRawOn=$slowRawOn savedAny=$savedAny"
Write-Host "Artifacts: $outDir"
if (-not $pass) { exit 1 }
exit 0

