<#
.SYNOPSIS
  Sprint **UX.2** — navigation mode + inset telemetry on cold preview.

.EXAMPLE
  .\scripts\pns_navigation_compatibility_test.ps1
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 20,
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
$outDir = Join-Path $projRoot "hfr-runs\navigation_compatibility_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_nav_compat.txt"
$gatePath = Join-Path $outDir "gate.json"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview 2>&1 | Out-Null

Write-Host "[navigation_compatibility_test] waiting ${WaitSec}s (preview nav)..."
Start-Sleep -Seconds $WaitSec

& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 500
& adb @adbPrefix shell logcat -c 2>$null | Out-Null

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_open_gallery true 2>&1 | Out-Null

Write-Host "[navigation_compatibility_test] gallery + BACK smoke..."
Start-Sleep -Seconds 12
& adb @adbPrefix shell input keyevent KEYCODE_BACK 2>$null | Out-Null
Start-Sleep -Milliseconds 350
& adb @adbPrefix shell input keyevent KEYCODE_BACK 2>$null | Out-Null
Start-Sleep -Milliseconds 350

& adb @adbPrefix exec-out logcat -d -s "PNS.NavUx:I" "PNS.NavUx:D" "PNS.AdbValidation:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$navModeOk = $hay -match "navUx mode=(Gesture|ThreeButton|Unknown)"
$adbNavOk = $hay -match "navUx mode="
$gestureExclusion = $hay -match "gestureExclusion"
$galleryOpenOk = $hay -match "preview openGallery=true"
$navBackOk = $hay -match "navBack galleryExit|navBack previewGalleryClosed"
$pass = $navModeOk -and $adbNavOk -and $galleryOpenOk -and $navBackOk

$gate = @{
    pass = $pass
    navModeLogged = $navModeOk
    adbNavUx = $adbNavOk
    gestureExclusionLogged = $gestureExclusion
    galleryOpenOk = $galleryOpenOk
    navBackOk = $navBackOk
    logPath = $logPath
}
$gate | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $gatePath -Encoding utf8

if ($pass) {
    Write-Host "[navigation_compatibility_test] PASS -> $outDir"
    exit 0
}
Write-Host "[navigation_compatibility_test] FAIL navModeOk=$navModeOk adbNavOk=$adbNavOk -> $outDir"
exit 1
