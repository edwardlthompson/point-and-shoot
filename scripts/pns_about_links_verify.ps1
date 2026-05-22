#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 14.11 — host gate: Venmo donation URL resolves + JVM constant test; optional USB About overlay.

.PARAMETER HostOnly
  Skip USB; JVM + HTTP only.

.PARAMETER Serial
  adb serial; else scripts/pns_adb_device.env.
#>
param(
    [switch]$HostOnly,
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdb) { . $resolveAdb -PrependToPath -Quiet }

Push-Location $repoRoot
try {
    & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest `
        --tests "dev.pointandshoot.PnsExternalUrlTest"
    if ($LASTEXITCODE -ne 0) { throw "PnsExternalUrlTest failed" }

    $url = "https://venmo.com/code?user_id=1857304970395648420"
    Write-Host "[pns_about_links] HEAD $url"
    try {
        $resp = Invoke-WebRequest -Uri $url -Method Head -MaximumRedirection 5 -TimeoutSec 30
        $code = [int]$resp.StatusCode
        if ($code -lt 200 -or $code -ge 400) {
            throw "unexpected status $code"
        }
        Write-Host "[pns_about_links] HTTP $code OK"
    } catch {
        Write-Warning "[pns_about_links] HEAD failed ($($_.Exception.Message)); trying GET..."
        $resp = Invoke-WebRequest -Uri $url -Method Get -MaximumRedirection 5 -TimeoutSec 30
        $code = [int]$resp.StatusCode
        if ($code -lt 200 -or $code -ge 400) {
            throw "GET status $code"
        }
        Write-Host "[pns_about_links] GET $code OK"
    }

    if ($HostOnly) {
        Write-Host "[pns_about_links] HOST_PASS"
        exit 0
    }

    $adbArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adbArgs += "-s", $Serial }

    $pkg = "dev.pointandshoot"
    $apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path -LiteralPath $apk)) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }
    & adb @adbArgs install -r -t $apk | Out-Host

    & adb @adbArgs shell am force-stop $pkg | Out-Null
    & adb @adbArgs logcat -c | Out-Null
    & adb @adbArgs shell am start -n "$pkg/.MainActivity" `
        --es pns_screen preview `
        --ez pns_preview_show_about true | Out-Null

    Start-Sleep -Seconds 10
    $log = & adb @adbArgs logcat -d -s "PNS.ChromeUx:I" 2>&1 | Out-String
    if ($log -notmatch "settingsAbout=open") {
        throw "missing PNS.ChromeUx settingsAbout=open in logcat"
    }
    Write-Host "[pns_about_links] USB_PASS settingsAbout=open"
    & adb @adbArgs shell am force-stop $pkg | Out-Null
    exit 0
} finally {
    Pop-Location
}
