#Requires -Version 5.1
<#
.SYNOPSIS
    Sprint 29.2 — settings JSON export/import host + optional USB gate.

.PARAMETER Serial
.PARAMETER SkipInstall
.PARAMETER SkipAssemble
.PARAMETER HostOnly
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [switch]$HostOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") {
        . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet
    }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if ($Serial -eq "" -and (Test-Path $envFile)) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim().Trim('"') }
        }
    }

    $outDir = Join-Path $repoRoot "hfr-runs\settings_export_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    function Write-Step { param([string]$msg) Write-Host "[pns_settings_export_verify] $msg" }

    Write-Step "SettingsExportBundleTest..."
    & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest --tests "dev.pointandshoot.SettingsExportBundleTest"
    if ($LASTEXITCODE -ne 0) { Write-Step "HOST FAIL: unit test"; exit 1 }

    if (-not $SkipAssemble) {
        Write-Step "assembleDebug..."
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { exit 1 }
    }

    if ($HostOnly) {
        $artifact = [ordered]@{
            schema = "pns.settings_export_verify.v1"
            timestamp = (Get-Date -Format "o")
            gateResult = "HOST_PASS"
            hostOnly = $true
        }
        $jsonOut = Join-Path $outDir "gate.json"
        $artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut
        Write-Step "HOST_PASS -> $jsonOut"
        exit 0
    }

    $adbArgs = if ($Serial) { @("-s", $Serial) } else { @() }
    $devices = & adb @adbArgs devices 2>&1 | Where-Object { $_ -match "\tdevice$" }
    if (-not $devices) {
        Write-Step "SKIP USB: no device (host test PASS)"
        exit 0
    }

    if (-not $SkipInstall) {
        $apk = Get-ChildItem "$repoRoot\app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $apk) { throw "APK missing" }
        & adb @adbArgs install -r -t $apk.FullName
        if ($LASTEXITCODE -ne 0) { exit 1 }
        & adb @adbArgs shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null | Out-Null
    }

    & adb @adbArgs shell pm clear dev.pointandshoot 2>$null | Out-Null
    & adb @adbArgs shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null | Out-Null

    & adb @adbArgs logcat -c 2>$null
    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null
    & adb @adbArgs shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview `
        --es pns_preview_theme_mode dark `
        --ez pns_preview_settings_export true 2>&1 | Out-Null

    Write-Step "Waiting 12s for settings export..."
    Start-Sleep -Seconds 12

    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null
    Start-Sleep -Milliseconds 500
    & adb @adbArgs shell am start -n "dev.pointandshoot/.MainActivity" `
        --es pns_screen preview `
        --es pns_preview_theme_mode system `
        --ez pns_preview_settings_import true 2>&1 | Out-Null

    Write-Step "Waiting 12s for settings import..."
    Start-Sleep -Seconds 12

    $logPath = Join-Path $outDir "logcat_adbvalidation.txt"
    & adb @adbArgs exec-out logcat -d -s "PNS.AdbValidation:I" 2>$null | Set-Content -Encoding UTF8 $logPath

    $hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
    if (-not $hay) { $hay = "" }

    $exportOk = $hay.Contains("settingsExport ok=true")
    $importOk = $hay.Contains("settingsImport ok=true") -and $hay.Contains("themeMode=Dark")

    $usbPass = ($exportOk -and $importOk)
    if ($exportOk -and $importOk) {
        $gateResult = "PASS"
    } else {
        $gateResult = "FAIL"
    }

    $artifact = [ordered]@{
        schema = "pns.settings_export_verify.v1"
        timestamp = (Get-Date -Format "o")
        device = if ($Serial) { $Serial } else { "default" }
        gateResult = $gateResult
        exportLogOk = $exportOk
        importThemeOk = $importOk
        outDir = $outDir
    }
    $jsonOut = Join-Path $outDir "gate.json"
    $artifact | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonOut
    Write-Step "USB gateResult=$gateResult -> $jsonOut"

    & adb @adbArgs shell am force-stop dev.pointandshoot 2>$null | Out-Null

    if ($gateResult -eq "FAIL") { exit 1 }
    exit 0
}
finally {
    Pop-Location
}
