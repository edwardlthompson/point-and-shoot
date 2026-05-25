<#
.SYNOPSIS
  Sprint **IP.1** — platform integration gate (deep link, ShareTarget, widget, FileProvider).

.EXAMPLE
  .\scripts\pns_platform_integration_test.ps1
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 28,
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

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\platform_integration_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_platform.txt"
$gatePath = Join-Path $outDir "gate.json"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_platform_file_provider_probe true `
    --ez pns_preview_platform_widget_probe true `
    --ez pns_preview_platform_share_probe true 2>&1 | Out-Null

Write-Host "[platform_integration_test] waiting ${WaitSec}s..."
Start-Sleep -Seconds $WaitSec

& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.Platform:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8

# Deep link cold start
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 400
& adb @adbPrefix shell am start -W -a android.intent.action.VIEW `
    -d "pointandshoot://preview" `
    -n "${pkg}/.MainActivity" 2>&1 | Out-Null
Start-Sleep -Seconds 4
& adb @adbPrefix exec-out logcat -d -s "PNS.Platform:I" "PNS.AdbValidation:I" 2>$null |
    Out-File -LiteralPath (Join-Path $outDir "logcat_deeplink.txt") -Encoding utf8 -Append

& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }
$deepHay = Get-Content -LiteralPath (Join-Path $outDir "logcat_deeplink.txt") -Raw -ErrorAction SilentlyContinue
if (-not $deepHay) { $deepHay = "" }

$fileProviderOk = $hay -match "platform fileProviderOk=true"
$widgetOk = $hay -match "platform widgetRegistered="
$shareOk = $hay -match "platform shareStarted"
$deepLinkOk = ($deepHay -match "deepLink host=preview") -or ($deepHay -match "deepLink adb host=preview")

$pass = $fileProviderOk -and $widgetOk -and $shareOk -and $deepLinkOk
$gate = @{
    pass = $pass
    fileProviderOk = $fileProviderOk
    widgetOk = $widgetOk
    shareOk = $shareOk
    deepLinkOk = $deepLinkOk
    artifact = $outDir
} | ConvertTo-Json
Set-Content -LiteralPath $gatePath -Value $gate -Encoding utf8

Write-Host "PLATFORM INTEGRATION: $(if ($pass) { 'PASS' } else { 'FAIL' })"
Write-Host "  fileProvider=$fileProviderOk widget=$widgetOk share=$shareOk deepLink=$deepLinkOk"
Write-Host "  artifacts: $outDir"
if (-not $pass) { exit 1 }
