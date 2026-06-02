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
$shots = [regex]::Matches($hay, "PNS\.AdbValidation:\s+longPressBurst shot profile=([A-Za-z]+) intervalMs=(\d+) raw=([A-Za-z]+) jpeg=([A-Za-z]+)")
$finishes = [regex]::Matches($hay, "PNS\.AdbValidation:\s+longPressBurst finished saved=(\d+)")
$shutterSound = [regex]::Matches($hay, "PNS\.AdbValidation:\s+shutterSound ok=true")

$jpegSeen = $false
$rawSeen = $false
$aggressiveSeen = $false
$pacedSeen = $false
$singleFormatOnly = $true
foreach ($m in $shots) {
    $raw = $m.Groups[3].Value
    $jpeg = $m.Groups[4].Value
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

$savedCounts = @()
foreach ($m in $finishes) { $savedCounts += [int]$m.Groups[1].Value }
$savedAny = ($savedCounts | Measure-Object -Sum).Sum

$pass =
    ($starts.Count -ge 4) -and
    ($shots.Count -ge 4) -and
    $jpegSeen -and
    $rawSeen -and
    $aggressiveSeen -and
    $pacedSeen -and
    $singleFormatOnly -and
    ($shutterSound.Count -ge 3) -and
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
    "- **shutter sound events:** $($shutterSound.Count)",
    "- **jpeg shot seen:** $jpegSeen",
    "- **raw shot seen:** $rawSeen",
    "- **aggressive strategy seen:** $aggressiveSeen",
    "- **paced strategy seen:** $pacedSeen",
    "- **single format only (RAW xor JPEG):** $singleFormatOnly",
    "- **saved total:** $savedAny",
    "",
    "Artifact log: $logPath",
    "Summary JSON: $summaryPath"
)
$mdPath = Join-Path $outDir "longpress_burst_verify_summary.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "LONGPRESS_BURST_VERIFY: pass=$pass starts=$($starts.Count) shots=$($shots.Count) finishes=$($finishes.Count) shutter=$($shutterSound.Count) jpegSeen=$jpegSeen rawSeen=$rawSeen aggressiveSeen=$aggressiveSeen pacedSeen=$pacedSeen singleFormatOnly=$singleFormatOnly savedAny=$savedAny"
Write-Host "Artifacts: $outDir"
if (-not $pass) { exit 1 }
exit 0

