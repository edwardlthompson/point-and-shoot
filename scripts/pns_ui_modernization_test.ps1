<#
.SYNOPSIS
  Sprint **UX.1** — theme mode ADB seed + PNS.AdbValidation log needles.

.EXAMPLE
  .\scripts\pns_ui_modernization_test.ps1 -ThemeMode Dark
#>
param(
    [string]$Serial = "",
    [ValidateSet("System", "Light", "Dark")]
    [string]$ThemeMode = "Dark",
    [int]$WaitSec = 25,
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
$outDir = Join-Path $projRoot "hfr-runs\ui_modernization_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_ui_modernization.txt"
$gatePath = Join-Path $outDir "gate.json"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --es pns_preview_theme_mode $ThemeMode 2>&1 | Out-Null

Write-Host "[ui_modernization_test] waiting ${WaitSec}s theme=$ThemeMode..."
Start-Sleep -Seconds $WaitSec

& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.NavUx:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$themeOk = $hay -match "preview seeded themeMode=$ThemeMode"
$navOk = $hay -match "navUx mode="
$pass = $themeOk -and $navOk

$gate = @{
    pass = $pass
    themeMode = $ThemeMode
    themeOk = $themeOk
    navUxLogged = $navOk
    logPath = $logPath
}
$gate | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $gatePath -Encoding utf8

if ($pass) {
    Write-Host "[ui_modernization_test] PASS -> $outDir"
    exit 0
}
Write-Host "[ui_modernization_test] FAIL themeOk=$themeOk navOk=$navOk -> $outDir"
exit 1
