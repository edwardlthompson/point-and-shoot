#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.8** — open chrome Settings rail via ADB seed; screencap + assert no research toggles visible.

.PARAMETER Serial
  adb serial; else scripts/pns_adb_device.env.
#>
param(
    [string]$Serial = ""
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
if (Test-Path (Join-Path $PSScriptRoot "pns_resolve_adb.ps1")) {
    . (Join-Path $PSScriptRoot "pns_resolve_adb.ps1") -PrependToPath -Quiet
}

$adb = @()
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $adb = @("-s", $Serial) }

$pkg = "dev.pointandshoot"
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
& adb @adb install -r -t $apk | Out-Host

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $repoRoot "hfr-runs\settings_rail_screencap_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

& adb @adb shell am force-stop $pkg | Out-Null
& adb @adb logcat -c | Out-Null
& adb @adb shell am start -n "$pkg/.MainActivity" `
    --es pns_screen preview `
    --ez pns_preview_open_settings true | Out-Null

Start-Sleep -Seconds 10
$log = & adb @adb logcat -d -s "PNS.ChromeUx:I" 2>&1 | Out-String
if ($log -notmatch "settingsRail=open") {
    throw "missing PNS.ChromeUx settingsRail=open in logcat"
}

$png = Join-Path $outDir "settings_rail.png"
$capArgs = @{ OutPath = $png }
if (-not [string]::IsNullOrWhiteSpace($Serial)) { $capArgs.Serial = $Serial }
& "$PSScriptRoot\pns_device_screencap.ps1" @capArgs

$xmlPath = "/sdcard/pns_settings_rail_dump.xml"
& adb @adb shell uiautomator dump $xmlPath 2>$null | Out-Null
$xml = & adb @adb exec-out cat $xmlPath 2>$null
if ($xml) {
    Set-Content -LiteralPath (Join-Path $outDir "settings_rail_uiautomator.xml") -Value $xml -Encoding UTF8
}

$researchNeedles = @(
    "Research DCG",
    "enableResearch",
    "Research AF",
    "HDR DCG research"
)
$hits = @()
foreach ($n in $researchNeedles) {
    if ($xml -and ($xml -match [regex]::Escape($n))) { $hits += $n }
}

$ok = $hits.Count -eq 0
Write-Host "SETTINGS RAIL SCREENCAP: $(if ($ok) { 'PASS' } else { "FAIL researchVisible=$($hits -join ',')" })"
Write-Host "artifact=$outDir"
if (-not $ok) { exit 1 }

& adb @adb shell am force-stop $pkg | Out-Null
exit 0
