<#
.SYNOPSIS
  USB gate: preview screencap vs composed-still JPEG framing (aspect + coarse NCC).

.DESCRIPTION
  1. Cold-start preview, wait for stream settle
  2. Screencap finder
  3. Fire one composed still (tray path)
  4. Pull DCIM, compare with scripts/preview_jpeg_framing_compare.py

.EXAMPLE
  .\scripts\pns_preview_jpeg_framing_gate.ps1 -Serial <serial>
#>
param(
    [string]$Serial = "",
    [int]$SettleSec = 12,
    [int]$CaptureWaitSec = 75,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

function Read-Serial {
    param([string]$S)
    if ($S) { return $S }
    $envFile = Join-Path $repo "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim() }
        }
    }
    throw "Set PNS_ADB_SERIAL or -Serial"
}

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$gateStartUtc = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$outDir = Join-Path $repo "hfr-runs\preview_jpeg_framing_gate_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    adb -s $Serial install -r -t $apk | Out-Host
}
adb -s $Serial shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null

adb -s $Serial logcat -c | Out-Null
adb -s $Serial shell am force-stop $pkg | Out-Null
$am = @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--es", "pns_preview_dial", "P",
    "--ez", "pns_preview_composed_still", "true",
    "--ez", "pns_preview_jpeg_companion", "true",
    "--es", "pns_preview_imaging_profile", "standard_pro",
    "--ei", "pns_preview_camera_id", "2"
)
adb -s $Serial @am | Out-Host

Write-Host "[framing_gate] settle ${SettleSec}s before screencap..."
Start-Sleep -Seconds $SettleSec
$capPath = Join-Path $outDir "preview_screencap.png"
& (Join-Path $repo "scripts\pns_device_screencap.ps1") -Serial $Serial -OutPath $capPath | Out-Host

Write-Host "[framing_gate] waiting ${CaptureWaitSec}s for composed still..."
Start-Sleep -Seconds $CaptureWaitSec

$logPath = Join-Path $outDir "logcat_framing.txt"
adb -s $Serial logcat -d -s PNS.AdbValidation:I PNS.CaptureStill:I PNS.Cam:D PNS.VideoEncode:I PNS.GLES.Preview:I 2>&1 |
    Out-File -Encoding utf8 $logPath

$logRaw = Get-Content $logPath -Raw

$glViewOk = $false
$geoMatches = [regex]::Matches($logRaw, "previewGeometry view=(\d+)x(\d+) buf=(\d+)x(\d+)")
for ($i = $geoMatches.Count - 1; $i -ge 0; $i--) {
    $g = $geoMatches[$i]
    $vw = [int]$g.Groups[1].Value
    $vh = [int]$g.Groups[2].Value
    $bw = [int]$g.Groups[3].Value
    $bh = [int]$g.Groups[4].Value
    if ($vw -le 0 -or $vh -le 0 -or $bw -le 0 -or $bh -le 0) { continue }
    $viewAspectWh = $vw / [math]::Max($vh, 1)
    $displayAspectWh =
        if ($bw -gt $bh) { $bh / [math]::Max($bw, 1) } else { $bw / [math]::Max($bh, 1) }
    $glViewOk = [math]::Abs($viewAspectWh - $displayAspectWh) -le 0.06
    Write-Host "[framing_gate] previewGeometry view=${vw}x${vh} buf=${bw}x${bh} viewAspectWh=$([math]::Round($viewAspectWh,4)) displayAspectWh=$([math]::Round($displayAspectWh,4)) glViewOk=$glViewOk"
    break
}

$pullDir = Join-Path $outDir "dcim_pull"
New-Item -ItemType Directory -Force -Path $pullDir | Out-Null
& (Join-Path $repo "scripts\pns_pull_dcim_captures.ps1") -Serial $Serial -OutDir $pullDir | Out-Host

adb -s $Serial shell am force-stop $pkg | Out-Null

$composedOk =
    $logRaw -match "composed_smoke ok=true" -or
    $logRaw -match "captureComposedStill.*ok=true" -or
    $logRaw -match "CaptureStill.*ok=true"
$jsonOut = Join-Path $outDir "framing_compare.json"
$py = Join-Path $repo "scripts\preview_jpeg_framing_compare.py"
$compareExit = 0
if (Test-Path $py) {
    python $py --screencap $capPath --jpeg-dir $pullDir --json-out $jsonOut --logcat $logPath --min-mtime $gateStartUtc
    $compareExit = $LASTEXITCODE
} else {
    Write-Host "[framing_gate] WARN: missing $py" -ForegroundColor Yellow
    $compareExit = 1
}

$comparePass = $false
if (Test-Path $jsonOut) {
    $j = Get-Content $jsonOut -Raw | ConvertFrom-Json
    $comparePass = [bool]$j.pass
}

@{
    composedStillOk = $composedOk
    glViewAspectOk = $glViewOk
    framingComparePass = $comparePass
    compareExitCode = $compareExit
    outDir = $outDir
    screencap = $capPath
    compareJson = $jsonOut
} | ConvertTo-Json | Out-File (Join-Path $outDir "gate.json") -Encoding utf8

if (-not $composedOk) {
    Write-Host "[framing_gate] FAIL: composed still success missing in logcat" -ForegroundColor Red
    exit 1
}
if (-not $glViewOk) {
    Write-Host "[framing_gate] FAIL: GLES view aspect does not match negotiated buffer" -ForegroundColor Red
    exit 1
}
if ($compareExit -ne 0) {
    Write-Host "[framing_gate] FAIL: preview vs JPEG framing compare" -ForegroundColor Red
    exit 1
}
Write-Host "[framing_gate] PASS artifacts: $outDir" -ForegroundColor Green
exit 0
