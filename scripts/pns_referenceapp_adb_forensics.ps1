<#
.SYNOPSIS
  Capture ReferenceCam package intents + optional logcat/dumpsys during manual RAW captures.

.DESCRIPTION
  Clears logcat, prints ReferenceCam launcher activity, waits for user to take ReferenceCam DNG shots,
  then saves filtered logcat and media.camera dumpsys excerpt.

.EXAMPLE
  .\scripts\pns_referenceapp_adb_forensics.ps1 -Serial <serial> -WaitSec 120
#>
param(
    [string]$Serial = "",
    [int]$WaitSec = 90,
    [string]$ReferenceAppPackage = "com.riseupgames.referenceapp2"
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

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
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") {
            return $t.Substring($eq + 1).Trim()
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$ts = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\referenceapp_adb_forensics_$ts"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
}

function Invoke-AdbOut([string[]]$CmdArgs) {
    if ($Serial) { return (& adb -s $Serial @CmdArgs 2>&1 | Out-String) }
    return (& adb @CmdArgs 2>&1 | Out-String)
}

Write-Host "[referenceapp_forensics] -> $outDir"
Write-Host "[referenceapp_forensics] dumping package activities / intent filters..."
$dump = Invoke-AdbOut @("shell", "dumpsys", "package", $ReferenceAppPackage)
$dump | Set-Content (Join-Path $outDir "dumpsys_package.txt") -Encoding UTF8

$acts = $dump | Select-String -Pattern "android.intent.action|Activity|activity" | Select-Object -First 80
$acts | ForEach-Object { $_.Line } | Set-Content (Join-Path $outDir "package_activities_excerpt.txt") -Encoding UTF8

Invoke-Adb @("shell", "logcat", "-G", "64M") | Out-Null
Invoke-Adb @("shell", "logcat", "-c") | Out-Null

Write-Host ""
Write-Host "=== Take 3 ReferenceCam RAW/DNG stills now (UW, wide, 73mm tele) ===" -ForegroundColor Cyan
Write-Host "Waiting ${WaitSec}s..."
Start-Sleep -Seconds $WaitSec

$logPath = Join-Path $outDir "referenceapp_capture_logcat.txt"
Invoke-Adb @(
    "shell", "logcat", "-d", "-v", "threadtime", "-t", "30000",
    "CameraDevice:I", "CameraCaptureSession:I", "CameraManager:I",
    "DngCreator:I", "ReferenceCam:I", "riseup:I", "AndroidRuntime:E"
) | Out-File -Encoding utf8 $logPath

$dumpCam = Invoke-AdbOut @("shell", "dumpsys", "media.camera")
$dumpCamPath = Join-Path $outDir "dumpsys_media_camera.txt"
$dumpCam | Set-Content $dumpCamPath -Encoding UTF8

$grepPath = Join-Path $outDir "dumpsys_media_camera_grep.txt"
$patterns = @("Camera ID", "Device", "physical", "RAW", "DNG", "cameraId", "Logical")
$grepLines = [System.Collections.Generic.List[string]]::new()
foreach ($pat in $patterns) {
    $matches = $dumpCam | Select-String -Pattern $pat -CaseSensitive:$false | Select-Object -First 40
    if ($matches) {
        [void]$grepLines.Add("--- $pat ---")
        $matches | ForEach-Object { [void]$grepLines.Add($_.Line) }
    }
}
$grepLines | Out-File -Encoding utf8 $grepPath

$findings = @"
# ReferenceCam ADB forensics ($ts)

## Package
- ``$ReferenceAppPackage``
- Full dump: ``dumpsys_package.txt``
- Activity excerpt: ``package_activities_excerpt.txt``

## Capture window
- User asked to shoot 3 ReferenceCam DNGs during ${WaitSec}s wait after logcat clear.
- Filtered logcat: ``referenceapp_capture_logcat.txt``

## media.camera
- Full: ``dumpsys_media_camera.txt`` (large)
- Grep excerpt: ``dumpsys_media_camera_grep.txt``

## Notes
- ReferenceCam is not debuggable; no ``run-as``.
- No documented ``am start`` RAW automation found in package dump; use manual UI or future UI-automate.
"@
$findings | Set-Content (Join-Path $outDir "referenceapp_adb_findings.md") -Encoding UTF8

Invoke-Adb @("shell", "am", "force-stop", "dev.pointandshoot") | Out-Null
Invoke-Adb @("shell", "am", "force-stop", $ReferenceAppPackage) | Out-Null

Write-Host "[referenceapp_forensics] findings -> $(Join-Path $outDir 'referenceapp_adb_findings.md')"
Write-Host "[referenceapp_forensics] done; apps force-stopped"
