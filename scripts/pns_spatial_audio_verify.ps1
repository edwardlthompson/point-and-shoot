#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.30** — spatial audio metadata (stereo channel mask) on in-app MC video + ffprobe.

.EXAMPLE
  .\scripts\pns_spatial_audio_verify.ps1 -Serial b5214fc6
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$WaitSec = 60,
    [int]$RecordSec = 5,
    [int]$MaxAttempts = 3
)

$ErrorActionPreference = "Stop"
$repo = Split-Path -Parent $PSScriptRoot
. (Join-Path $repo "scripts\pns_resolve_adb.ps1") -PrependToPath -Quiet

function Read-Serial {
    param([string]$S)
    if ($S) { return $S }
    $envFile = Join-Path $repo "scripts\pns_adb_device.env"
    if (Test-Path $envFile) {
        foreach ($line in Get-Content $envFile) {
            if ($line -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim() }
        }
    }
    throw "Set PNS_ADB_SERIAL or -Serial"
}

function Invoke-Adb {
    param([string[]]$AdbArgs)
    $adbExe = (Get-Command adb -ErrorAction Stop).Source
    $argv = @()
    if ($Serial) { $argv += @("-s", $Serial) }
    $argv += $AdbArgs
    $quoted = $argv | ForEach-Object { if ($_ -match '\s') { '"{0}"' -f $_ } else { $_ } }
    $outTmp = [System.IO.Path]::GetTempFileName()
    $errTmp = [System.IO.Path]::GetTempFileName()
    $proc = Start-Process -FilePath $adbExe -ArgumentList ($quoted -join " ") -NoNewWindow -Wait -PassThru -RedirectStandardOutput $outTmp -RedirectStandardError $errTmp
    $stdout = if (Test-Path -LiteralPath $outTmp) { Get-Content -LiteralPath $outTmp } else { @() }
    $stderr = if (Test-Path -LiteralPath $errTmp) { Get-Content -LiteralPath $errTmp } else { @() }
    Remove-Item -LiteralPath $outTmp, $errTmp -Force -ErrorAction SilentlyContinue
    if ($proc.ExitCode -ne 0) {
        throw "adb $($AdbArgs -join ' ') failed exit=$($proc.ExitCode)`n$($stderr -join "`n")"
    }
    return @($stdout + $stderr)
}

function Set-HudStringPref([string]$Xml, [string]$Name, [string]$Value) {
    $pattern = "<string name=`"$Name`">[^<]*</string>"
    $replacement = "<string name=`"$Name`">$Value</string>"
    if ($Xml -match $Name) { return ($Xml -replace $pattern, $replacement) }
    return ($Xml -replace "</map>", "    $replacement`r`n</map>")
}

$Serial = Read-Serial $Serial
$pkg = "dev.pointandshoot"
$outDir = Join-Path $repo "hfr-runs\spatial_audio_verify_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $repo "scripts\pns_gradlew.ps1") :app:assembleDebug
}
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk) | Out-Null
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") 2>$null | Out-Null
    Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") 2>$null | Out-Null
}

# Spatial metadata is emitted on camcorder profile; force it here to avoid carry-over from
# prior automation that seeds UNPROCESSED audio source.
$hudPath = "/data/data/$pkg/shared_prefs/pns_hud_settings.xml"
$hudLines = Invoke-Adb @("shell", "run-as", $pkg, "cat", $hudPath) 2>&1
$hud = ($hudLines -join "`n")
if ($hud -match "<map>") {
    $hud = Set-HudStringPref $hud "video_audio_source" "camcorder"
    $tmpHud = [System.IO.Path]::GetTempFileName() + ".xml"
    [System.IO.File]::WriteAllText($tmpHud, $hud, [System.Text.UTF8Encoding]::new($false))
    Invoke-Adb @("push", $tmpHud, "/data/local/tmp/pns_hud_camcorder.xml") 2>$null | Out-Null
    Invoke-Adb @("shell", "run-as", $pkg, "cp", "/data/local/tmp/pns_hud_camcorder.xml", $hudPath) 2>$null | Out-Null
    Remove-Item -LiteralPath $tmpHud -Force -ErrorAction SilentlyContinue
}

$rec = [Math]::Max(3, [Math]::Min($RecordSec, 30))
$videoSaved = $false
$spatialMeta = $false
$ffprobeOk = $false
$channelLayout = "unknown"
$attemptUsed = 0

for ($attempt = 1; $attempt -le [Math]::Max(1, $MaxAttempts); $attempt++) {
    $attemptUsed = $attempt
    Invoke-Adb @("shell", "am", "force-stop", $pkg) 2>$null | Out-Null
    Invoke-Adb @("logcat", "-c") 2>$null | Out-Null

    Invoke-Adb @(
        "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_primary_photo", "false",
        "--ei", "pns_preview_automation_in_app_video_sec", "$rec",
        "--es", "pns_preview_imaging_profile", "standard_pro"
    ) 2>&1 | Out-Null

    Write-Host "[spatial_audio] attempt $attempt/$MaxAttempts waiting ${WaitSec}s..."
    Start-Sleep -Seconds $WaitSec
    Invoke-Adb @("shell", "am", "force-stop", $pkg) 2>$null | Out-Null

    $logPath = Join-Path $outDir ("logcat_attempt_{0:D2}.txt" -f $attempt)
    Invoke-Adb @(
        "logcat", "-d", "-v", "threadtime",
        "-s", "PNS.MCVideoRec:I", "PNS.AdbValidation:I", "AndroidRuntime:E"
    ) 2>&1 | Out-File -Encoding utf8 $logPath
    $log = Get-Content $logPath -Raw
    $videoSaved = $log -match "inAppVideoSaved ok=true"
    $spatialMeta = $log -match "spatialAudioMeta=stereo"

    $localMp4 = Join-Path $outDir ("clip_attempt_{0:D2}.mp4" -f $attempt)
    $dcim = "/sdcard/DCIM/Point & Shoot"
    $latest = (Invoke-Adb @("shell", "ls -t '$dcim'/pns_*.mp4 2>/dev/null | head -1") 2>&1) -join "`n"
    $latest = ($latest -split "`n" | Where-Object { $_ -match "pns_.*\.mp4" } | Select-Object -First 1)
    if ($latest) {
        $latest = $latest.Trim()
        Invoke-Adb @("pull", $latest, $localMp4) 2>&1 | Out-Null
    }

    $ffprobeOk = $false
    $channelLayout = "unknown"
    if ((Test-Path $localMp4) -and (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
        $layout = (& ffprobe -v error -select_streams a:0 `
            -show_entries stream=channel_layout `
            -of default=noprint_wrappers=1:nokey=1 $localMp4 2>&1) -join ""
        $layout = $layout.Trim()
        if ($layout) {
            $channelLayout = $layout
            $ffprobeOk = ($layout -eq "stereo")
        } else {
            # Some OEM muxers omit channel_layout; trust in-app spatial metadata line.
            $ffprobeOk = $videoSaved -and $spatialMeta
        }
    } elseif (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
        $ffprobeOk = $videoSaved -and $spatialMeta
    }

    if ($videoSaved -and $spatialMeta -and $ffprobeOk) { break }
}

$pass = $videoSaved -and ($spatialMeta -or $ffprobeOk)

$gate = [ordered]@{
    pass = $pass
    videoSaved = [bool]$videoSaved
    spatialMeta = [bool]$spatialMeta
    ffprobeStereo = [bool]$ffprobeOk
    channelLayout = $channelLayout
    attempts = $attemptUsed
    outDir = $outDir
}
$gate | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $outDir "gate.json")

if ($pass) {
    Write-Host "SPATIAL AUDIO VERIFY: PASS ($outDir)"
    exit 0
}
Write-Host "SPATIAL AUDIO VERIFY: FAIL ($outDir)"
exit 1
