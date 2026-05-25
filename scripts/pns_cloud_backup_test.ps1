<#
.SYNOPSIS
  Sprint **UX.3** — cloud backup probe sync (app-private dir, no SAF folder required).

.EXAMPLE
  .\scripts\pns_cloud_backup_test.ps1
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 30,
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
$outDir = Join-Path $projRoot "hfr-runs\cloud_backup_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_cloud_backup.txt"
$gatePath = Join-Path $outDir "gate.json"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_cloud_backup true `
    --ez pns_preview_cloud_backup_sync true `
    --ez pns_preview_cloud_backup_probe true 2>&1 | Out-Null

Write-Host "[cloud_backup_test] waiting ${WaitSec}s..."
Start-Sleep -Seconds $WaitSec

& adb @adbPrefix exec-out logcat -d -s "PNS.CloudBackup:I" "PNS.AdbValidation:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$enabledOk = $hay -match "cloudBackup enabled=true"
$syncOk = $hay -match "cloudBackup syncDone copied="
$copiedOk = $hay -match "cloudBackup copied "
$pass = $enabledOk -and $syncOk -and $copiedOk

$gate = @{
    pass = $pass
    enabledOk = $enabledOk
    syncDoneOk = $syncOk
    copiedOk = $copiedOk
    logPath = $logPath
}
$gate | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $gatePath -Encoding utf8

if ($pass) {
    Write-Host "[cloud_backup_test] PASS -> $outDir"
    exit 0
}
Write-Host "[cloud_backup_test] FAIL enabledOk=$enabledOk syncOk=$syncOk copiedOk=$copiedOk -> $outDir"
exit 1
