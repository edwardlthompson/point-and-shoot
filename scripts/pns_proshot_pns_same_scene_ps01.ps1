<#
.SYNOPSIS
  Same-scene ProShot UI taps (calibrated) + P&S PS01 RAW stills on OP13-class devices.

.DESCRIPTION
  Measures / uses screencap-calibrated ProShot lens chips (small), then captures ProShot DNGs
  and Point & Shoot DNGs with --ez pns_preview_dng_proshot_pipeline true for the same slots.

.EXAMPLE
  .\scripts\pns_proshot_pns_same_scene_ps01.ps1 -Serial 8bf09993 -Slots 73,14
#>
param(
    [string]$Serial = "",
    [string]$Slots = "73,14",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [switch]$CalibrateOnly,
    [int]$SettleSec = 3,
    [int]$AfterShutterSec = 4
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

if (-not $Serial) {
    $envFile = Join-Path $repo "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim() }
        }
    }
}
if (-not $Serial) { throw "Pass -Serial or set PNS_ADB_SERIAL" }

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$outDir = Join-Path $repo "hfr-runs\proshot_pns_same_scene_ps01_$stamp"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# Calibrated on OP13 1440x3168 from hfr-runs/proshot_ui_calibrate_20260713/proshot_01_launch.png
# Digit-glyph centroids of the small square chips (not the larger AF readout).
$ProShotChips = @{
    "73" = @{ x = 1316; y = 2485; camHint = "4"; pnsSlot = 73 }
    "23" = @{ x = 1316; y = 2588; camHint = "2"; pnsSlot = 23 }
    "15" = @{ x = 1223; y = 2588; camHint = "3"; pnsSlot = 14 }  # ProShot label 15 ≈ P&S UW 14
}
$ProShotShutter = @{ x = 720; y = 2840 }
$ProShotPkg = "com.riseupgames.proshot2"
$PnsPkg = "dev.pointandshoot"

function Invoke-Adb {
    param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
    & adb -s $Serial @AdbArgs
}

function Save-Cap([string]$Name) {
    $path = Join-Path $outDir $Name
    & (Join-Path $repo "scripts\pns_device_screencap.ps1") -Serial $Serial -OutPath $path | Out-Null
    return $path
}

function Get-ProShotDngRows {
    $findOut = (& adb -s $Serial shell "find /sdcard/DCIM -maxdepth 2 -name '*.dng' 2>/dev/null") | Out-String
    $rows = @()
    foreach ($p in ($findOut -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ -match '\.dng$' -and $_ -notmatch 'Point' })) {
        $stat = (& adb -s $Serial shell "stat -c '%Y %s' '$p' 2>/dev/null").ToString().Trim()
        $parts = $stat -split "\s+"
        if ($parts.Count -ge 2) {
            $rows += [pscustomobject]@{ Path = $p; Mtime = [long]$parts[0]; Size = [long]$parts[1] }
        }
    }
    return @($rows | Sort-Object Mtime)
}

function Wait-NewProShotDng([long]$SinceEpoch, [int]$TimeoutSec) {
    $deadline = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds() + $TimeoutSec
    while ([DateTimeOffset]::UtcNow.ToUnixTimeSeconds() -lt $deadline) {
        $hit = @(Get-ProShotDngRows | Where-Object { $_.Mtime -ge ($SinceEpoch - 1) } | Select-Object -Last 1)
        if ($hit.Count -gt 0 -and $hit[0]) { return $hit[0] }
        Start-Sleep -Milliseconds 400
    }
    return $null
}

Write-Host "[same_scene] outDir=$outDir"

# --- Calibrate screencap ---
Invoke-Adb @("shell", "am", "force-stop", $ProShotPkg)
Invoke-Adb @("shell", "am", "force-stop", $PnsPkg)
Start-Sleep -Seconds 1
Invoke-Adb @("shell", "pm", "grant", $ProShotPkg, "android.permission.CAMERA")
Invoke-Adb @("shell", "am", "start", "-W", "-n", "$ProShotPkg/.activities.PermissionsActivity") | Out-Host
Start-Sleep -Seconds $SettleSec
$cap0 = Save-Cap "00_proshot_home.png"
Write-Host "[same_scene] home screencap $cap0"

# Verify taps: tap each chip and screencap (no shutter yet)
$slotList = @($Slots.Split(",") | ForEach-Object { $_.Trim() } | Where-Object { $_ })
foreach ($slot in $slotList) {
    $psLabel = if ($slot -eq "14") { "15" } else { $slot }
    if (-not $ProShotChips.ContainsKey($psLabel)) { throw "Unknown slot $slot (ProShot label map missing)" }
    $tap = $ProShotChips[$psLabel]
    Write-Host "[same_scene] calibrate tap ProShot chip $psLabel at $($tap.x),$($tap.y)"
    Invoke-Adb @("shell", "input", "tap", "$($tap.x)", "$($tap.y)")
    Start-Sleep -Seconds 2
    Save-Cap ("01_after_tap_${psLabel}.png") | Out-Null
}

if ($CalibrateOnly) {
    Write-Host "[same_scene] CalibrateOnly — inspect PNGs under $outDir then re-run without -CalibrateOnly"
    Invoke-Adb @("shell", "am", "force-stop", $ProShotPkg)
    exit 0
}

# --- ProShot captures ---
$proshotMeta = @()
foreach ($slot in $slotList) {
    $psLabel = if ($slot -eq "14") { "15" } else { $slot }
    $tap = $ProShotChips[$psLabel]
    Write-Host "[same_scene] ProShot capture slot=$slot chip=$psLabel"
    Invoke-Adb @("shell", "input", "tap", "$($tap.x)", "$($tap.y)")
    Start-Sleep -Seconds $SettleSec
    Save-Cap ("10_proshot_before_shutter_${slot}.png") | Out-Null
    $since = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    Invoke-Adb @("shell", "input", "tap", "$($ProShotShutter.x)", "$($ProShotShutter.y)")
    Start-Sleep -Seconds $AfterShutterSec
    $dng = Wait-NewProShotDng -SinceEpoch $since -TimeoutSec 20
    if (-not $dng) { throw "No ProShot DNG for slot $slot" }
    $local = Join-Path $outDir "proshot_mm${slot}.dng"
    Invoke-Adb @("pull", $dng.Path, $local) | Out-Host
    $proshotMeta += [ordered]@{ slot = $slot; chip = $psLabel; remote = $dng.Path; local = $local; size = $dng.Size }
    Write-Host "[same_scene] pulled $($dng.Path)"
}

Invoke-Adb @("shell", "am", "force-stop", $ProShotPkg)
Start-Sleep -Seconds 1

# --- P&S PS01 ---
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not $SkipInstall) {
    if (-not (Test-Path $apk)) { throw "Missing $apk" }
    Invoke-Adb @("install", "-r", "-t", $apk) | Out-Host
}

$pnsMeta = @()
foreach ($slot in $slotList) {
    Write-Host "[same_scene] P&S PS01 slot=$slot"
    Invoke-Adb @("shell", "pm unstop --user 0 $PnsPkg 2>/dev/null; pm enable --user 0 $PnsPkg 2>/dev/null")
    Invoke-Adb @("shell", "am", "force-stop", $PnsPkg)
    Start-Sleep -Seconds 1
    Invoke-Adb @("logcat", "-c")
    $am =
        "am start -W -n ${PnsPkg}/.MainActivity --activity-clear-task " +
        "--es pns_screen preview --es pns_preview_dial Auto " +
        "--ei pns_preview_raw_count 1 --es pns_preview_focal_mm_slot $slot " +
        "--ez pns_preview_primary_photo true --ez pns_preview_dng_proshot_pipeline true"
    Invoke-Adb @("shell", $am) | Out-Host
    $deadline = (Get-Date).AddSeconds(75)
    $ok = $false
    $logPath = Join-Path $outDir "pns_mm${slot}_logcat.txt"
    while ((Get-Date) -lt $deadline) {
        Start-Sleep -Seconds 4
        adb -s $Serial exec-out "logcat -d" | Out-File -FilePath $logPath -Encoding utf8
        $text = Get-Content $logPath -Raw -ErrorAction SilentlyContinue
        if ($text -and $text.Contains("captureRawStill 1/1 ok=true saved=")) { $ok = $true; break }
        if ($text -match "captureRawStill 1/1 ok=false") { break }
    }
    if (-not $ok) { throw "P&S PS01 capture failed for slot $slot — see $logPath" }
    $remoteList = adb -s $Serial shell "ls -t '/sdcard/DCIM/Point & Shoot/'*.dng 2>/dev/null | head -1"
    $remote = ($remoteList | Select-Object -First 1).ToString().Trim()
    $local = Join-Path $outDir "pns_ps01_mm${slot}.dng"
    Invoke-Adb @("pull", $remote, $local) | Out-Host
    $pnsMeta += [ordered]@{ slot = $slot; remote = $remote; local = $local; captureOk = $ok }
    Invoke-Adb @("shell", "am", "force-stop", $PnsPkg)
    Start-Sleep -Seconds 1
}

# --- Score ---
$scorePath = Join-Path $outDir "score.txt"
$scoreLines = @()
foreach ($slot in $slotList) {
    $ps = Join-Path $outDir "proshot_mm${slot}.dng"
    $pns = Join-Path $outDir "pns_ps01_mm${slot}.dng"
    $scoreLines += "=== slot $slot ==="
    $scoreLines += (& python (Join-Path $repo "scripts\dng_same_scene_exposure_metric.py") $pns $ps 2>&1 | Out-String)
}
$scoreLines | Set-Content $scorePath -Encoding utf8
Write-Host ($scoreLines -join "`n")

$gate = [ordered]@{
    serial       = $Serial
    outDir       = $outDir
    slots        = $slotList
    proshotTaps  = $ProShotChips
    shutter      = $ProShotShutter
    proshot      = $proshotMeta
    pns          = $pnsMeta
    scoreFile    = $scorePath
}
$gate | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir "gate.json") -Encoding utf8

Invoke-Adb @("shell", "am", "force-stop", $ProShotPkg)
Invoke-Adb @("shell", "am", "force-stop", $PnsPkg)
Write-Host "[same_scene] DONE $outDir"
exit 0
