<#
.SYNOPSIS
  Sprint **IP.2** — connectivity gate (LAN transfer, WebDAV probe, social stream, collaborative, cloud).

.EXAMPLE
  .\scripts\pns_connectivity_test.ps1
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
$lanPorts = @(28766, 28767, 28768, 28769, 38866, 48866)

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
$outDir = Join-Path $projRoot "hfr-runs\connectivity_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_connectivity.txt"
$gatePath = Join-Path $outDir "gate.json"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

foreach ($p in $lanPorts) {
    & adb @adbPrefix reverse "tcp:${p}" "tcp:${p}" 2>$null | Out-Null
}

& adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
    --activity-clear-task `
    --es pns_screen preview `
    --ez pns_preview_lan_transfer_probe true `
    --ez pns_preview_webdav_probe true `
    --ez pns_preview_social_stream_probe true `
    --ez pns_preview_collaborative_probe true `
    --ez pns_preview_cloud_backup_probe true `
    --ez pns_preview_cloud_backup_sync true 2>&1 | Out-Null

Write-Host "[connectivity_test] waiting ${WaitSec}s..."
Start-Sleep -Seconds $WaitSec

& adb @adbPrefix exec-out logcat -d -s "PNS.AdbValidation:I" "PNS.Connectivity:I" "PNS.LanTransfer:I" "PNS.CloudBackup:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$statusBody = ""
$lanPortUsed = 0
$portMatches = [regex]::Matches($hay, "connectivity lanServer listening port=(\d+)")
if ($portMatches.Count -gt 0) {
    $lanPortUsed = [int]$portMatches[$portMatches.Count - 1].Groups[1].Value
}
if ($lanPortUsed -gt 0) {
    & adb @adbPrefix reverse "tcp:${lanPortUsed}" "tcp:${lanPortUsed}" 2>$null | Out-Null
    try {
        $statusBody = Invoke-WebRequest -Uri "http://127.0.0.1:${lanPortUsed}/status" -UseBasicParsing -TimeoutSec 8 |
            Select-Object -ExpandProperty Content
    } catch { }
    if ($statusBody -notmatch '"ok":true') {
        $statusBody = (& adb @adbPrefix shell "curl -s --max-time 6 http://127.0.0.1:${lanPortUsed}/status" 2>$null | Out-String).Trim()
    }
}

& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
if ($lanPortUsed -gt 0) {
    & adb @adbPrefix reverse --remove "tcp:${lanPortUsed}" 2>$null | Out-Null
}

$lanOk = ($hay -match "connectivity lanServer listening port=") -and ($statusBody -match '"ok":true')
$webDavOk = $hay -match "connectivity webdavConfigured="
$socialOk = $hay -match "connectivity socialStream"
$collabOk = $hay -match "connectivity collaborative"
$cloudOk = $hay -match "cloudBackup syncDone copied="

$pass = $lanOk -and $webDavOk -and $socialOk -and $collabOk -and $cloudOk
$gate = @{
    pass = $pass
    lanOk = $lanOk
    lanStatus = $statusBody
    webDavOk = $webDavOk
    socialOk = $socialOk
    collabOk = $collabOk
    cloudOk = $cloudOk
    artifact = $outDir
} | ConvertTo-Json
Set-Content -LiteralPath $gatePath -Value $gate -Encoding utf8

Write-Host "CONNECTIVITY: $(if ($pass) { 'PASS' } else { 'FAIL' })"
Write-Host "  lan=$lanOk webdav=$webDavOk social=$socialOk collab=$collabOk cloud=$cloudOk"
Write-Host "  artifacts: $outDir"
if (-not $pass) { exit 1 }
exit 0
