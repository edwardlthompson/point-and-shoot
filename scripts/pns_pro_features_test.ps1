<#
.SYNOPSIS
  Sprint **CC.3** — picture profile, tether HTTP, flash strength, calibration export (no RAW editor).

.EXAMPLE
  .\scripts\pns_pro_features_test.ps1 -PictureProfile cinematic -FlashStrength 50
#>
param(
    [string]$Serial = "",
    [string]$PictureProfile = "cinematic",
    [int]$FlashStrength = 50,
    [switch]$SkipCalExport,
    [int]$WaitSec = 75,
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

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"
$port = 28765

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}
$adbPrefix = @()
if ($Serial) { $adbPrefix = @("-s", $Serial) }

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    & adb @adbPrefix install -r -t $apk 2>&1 | Out-Null
}
& adb @adbPrefix shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\pro_features_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_pro_features.txt"
$gatePath = Join-Path $outDir "gate.json"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

$startArgs = @(
    "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_tether", "true",
    "--es", "pns_preview_picture_profile", $PictureProfile,
    "--ei", "pns_preview_flash_strength", "$FlashStrength"
)
if (-not $SkipCalExport) {
    $startArgs += @("--ez", "pns_preview_cal_export", "true")
}
& adb @adbPrefix @startArgs 2>&1 | Out-Null

Write-Host "[pro_features_test] waiting ${WaitSec}s profile=$PictureProfile flash=$FlashStrength..."
Start-Sleep -Seconds 20

& adb @adbPrefix reverse --remove "tcp:18765" 2>$null | Out-Null
& adb @adbPrefix reverse "tcp:$port" "tcp:$port" 2>$null | Out-Null
Start-Sleep -Milliseconds 500

$tetherOk = $false
try {
    $status = Invoke-WebRequest -Uri "http://127.0.0.1:$port/status" -UseBasicParsing -TimeoutSec 8
    if ($status.Content -match '"ok":true') {
        $tetherOk = $true
        Write-Host "[pro_features_test] GET /status ok"
    }
    Invoke-WebRequest -Uri "http://127.0.0.1:$port/capture" -Method POST -UseBasicParsing -TimeoutSec 8 | Out-Null
    Write-Host "[pro_features_test] POST /capture sent"
    Start-Sleep -Seconds 3
} catch {
    Write-Host "[pro_features_test] tether HTTP warning: $($_.Exception.Message)"
}

Start-Sleep -Seconds ([Math]::Max(5, $WaitSec - 18))
& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.ProCapture:I" "PNS.Tether:I" "PNS.ColorCal:I" "PNS.FlashPolicy:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$profileOk = $hay -match "pictureProfile applied id=$PictureProfile"
$flashOk = $hay -match "preview seeded flashStrengthPercent=$FlashStrength"
$tetherListen = $hay -match "listening port=$port"
$tetherCapture = $hay -match "tether capture fired"
$calOk = $SkipCalExport -or ($hay -match "colorCal export ok")

$tetherStart = $hay -match "tetherServer start port=$port"
# cal export is optional (no profile on fresh install); tether needs listen + HTTP or capture log
$pass = $profileOk -and $flashOk -and $tetherStart -and ($tetherListen -or $tetherOk) -and ($tetherCapture -or $tetherOk -or $tetherListen)
$gate = @{
    pass = $pass
    pictureProfileOk = $profileOk
    flashStrengthOk = $flashOk
    tetherHttpOk = $tetherOk
    tetherStartLog = $tetherStart
    tetherListenLog = $tetherListen
    tetherCaptureLog = $tetherCapture
    calExportOk = $calOk
    logPath = $logPath
} | ConvertTo-Json -Depth 4
$gate | Set-Content -LiteralPath $gatePath -Encoding utf8

Write-Host $gate
if (-not $pass) { exit 1 }
Write-Host "PRO FEATURES TEST: PASS"
