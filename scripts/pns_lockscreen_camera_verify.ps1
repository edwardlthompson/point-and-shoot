<#
.SYNOPSIS
  Verify secure lockscreen camera launch wiring.

.DESCRIPTION
  Launches MainActivity with STILL_IMAGE_CAMERA_SECURE action and checks
  logcat for secure launch policy + secure session markers.
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 8,
    [int]$PollAttempts = 6,
    [int]$PollIntervalSec = 4,
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

function Invoke-Adb([string[]]$CmdArgs) {
    & adb @adbPrefix @CmdArgs
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

$pkg = "dev.pointandshoot"
$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\lockscreen_camera_verify_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$summaryPath = Join-Path $outDir "summary.txt"
$latestLogPath = Join-Path $outDir "logcat_latest.txt"
$passFile = Join-Path $outDir "VERIFY_OK.txt"

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    & adb @adbPrefix install -r -t $apk 2>&1 | Out-Null
}
& adb @adbPrefix shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null

Invoke-Adb @("shell", "logcat", "-c")
Invoke-Adb @("shell", "am", "force-stop", $pkg)
Invoke-Adb @(
    "shell", "am", "start", "-W",
    "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "-a", "android.media.action.STILL_IMAGE_CAMERA_SECURE",
    "--es", "pns_screen", "preview"
)
Start-Sleep -Seconds $WaitSec

$okPolicy = $false
$okSession = $false
$needlePolicy = "secureLaunchPolicy showWhenLocked=true turnScreenOn=true"
$needleSession = "secureSession=true mode="

for ($attempt = 1; $attempt -le $PollAttempts; $attempt++) {
    $tagged =
        & adb @adbPrefix exec-out logcat -d -s "PNS.HardwareKey:I" "PNS.ChromeUx:I" "PNS.AdbValidation:I" 2>$null
    $text = if ($tagged -is [System.Array]) { $tagged -join "`n" } else { [string]$tagged }

    if ([string]::IsNullOrWhiteSpace($text)) {
        $fallback = & adb @adbPrefix logcat -d -v threadtime -t 20000 2>$null
        $text = if ($fallback -is [System.Array]) { $fallback -join "`n" } else { [string]$fallback }
    }

    $attemptPath = Join-Path $outDir ("logcat_attempt_{0:D2}.txt" -f $attempt)
    $text | Out-File -LiteralPath $attemptPath -Encoding utf8
    $text | Out-File -LiteralPath $latestLogPath -Encoding utf8

    $okPolicy = $text.Contains($needlePolicy)
    $okSession = $text.Contains($needleSession)
    if ($okPolicy -and $okSession) { break }
    if ($attempt -lt $PollAttempts) { Start-Sleep -Seconds $PollIntervalSec }
}

& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

if ($okPolicy -and $okSession) {
    @(
        "artifactDir=$outDir",
        "policy=true",
        "session=true"
    ) | Out-File -LiteralPath $summaryPath -Encoding utf8
    "[lockscreen_camera_verify] PASS secure policy/session markers found artifactDir=$outDir" | Out-File -LiteralPath $passFile -Encoding utf8
    Write-Host "[lockscreen_camera_verify] PASS secure policy/session markers found artifactDir=$outDir"
    exit 0
}

@(
    "artifactDir=$outDir",
    "policy=$okPolicy",
    "session=$okSession"
) | Out-File -LiteralPath $summaryPath -Encoding utf8
Write-Error "[lockscreen_camera_verify] FAIL missing secure markers policy=$okPolicy session=$okSession artifactDir=$outDir"
exit 1

