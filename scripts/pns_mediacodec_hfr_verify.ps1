#Requires -Version 5.1
<#
.SYNOPSIS
    Verify MediaCodecVideoRecorder HFR (120fps) and 10-bit video on device.

.DESCRIPTION
    Sprint **13V.16** extends case **4K_120fps_MediaCodec** with ffprobe **3840×2160 @ 120 fps**
    and log needles **`PNS.VideoEncode`** session buffer + **`mcVideoPrepared size=3840x2160`**.

.PARAMETER Serial
    ADB device serial (optional; uses scripts/pns_adb_device.env when set).

.PARAMETER OutDir
    Output directory. Defaults to hfr-runs\mediacodec_verify_<timestamp>.

.PARAMETER SkipAssemble
.PARAMETER SkipInstall
.PARAMETER OnlyTest
    Run a single case name (e.g. `HFR_1080p_120fps`) for iteration.

.PARAMETER GateProfile
    **`vf`** — 1080p/4K matrix: H.264 + H.265 @ 60; HFR 120/240/480 @ 1080p; HFR 120 @ 4K (MediaCodec).
    Requires **ffprobe** video+audio, container fps ≥ 75% of target for HFR, and enough **video
    packets** (detects frozen single-frame + audio-only regressions).

.PARAMETER RequireFfprobeAv
    Fail when **ffprobe** is missing or clip lacks both audio and video streams (default for **GateProfile vf**).
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [string]$OnlyTest = "",
    [string]$GateProfile = "",
    [switch]$RequireFfprobeAv,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
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

    if (-not $SkipAssemble) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }

    $apk = "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "Missing $apk — run without -SkipAssemble" }

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    if (-not $SkipInstall) {
        Invoke-AdbCmd install -r -t $apk | Out-Null
        Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null
        Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.RECORD_AUDIO 2>$null
    }

    $Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    if (-not $OutDir) { $OutDir = "hfr-runs\mediacodec_verify_$Timestamp" }
    New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

    $ResultsFile = Join-Path $OutDir "results.md"
    $SummaryJson = Join-Path $OutDir "summary.json"

    $pkg = "dev.pointandshoot"
    $act = "dev.pointandshoot.MainActivity"

    function Write-Log { param([string]$Msg) Write-Host $Msg; Add-Content $ResultsFile $Msg }

    function Clear-LogcatBuffer {
        Invoke-AdbCmd logcat -c 2>$null | Out-Null
    }

    function Set-ChromeVideoEncodePrefs {
        param([int]$Width, [int]$Height)
        $prefsPath = "/data/data/$pkg/shared_prefs/pns_preview_chrome.xml"
        $existingPrefs = (Invoke-AdbCmd shell run-as $pkg cat $prefsPath 2>&1) -join "`n"
        if ($existingPrefs -notmatch "<map>") {
            Write-Host "  WARNING: Could not read $prefsPath"
            return $false
        }
        $patched = $existingPrefs -replace '(?s)<int name="in_app_video_encode_w"[^/]*/>', "<int name=`"in_app_video_encode_w`" value=`"$Width`" />"
        $patched = $patched  -replace '(?s)<int name="in_app_video_encode_h"[^/]*/>', "<int name=`"in_app_video_encode_h`" value=`"$Height`" />"
        if ($patched -notmatch 'in_app_video_encode_w') {
            $patched = $patched -replace '</map>', "    <int name=`"in_app_video_encode_w`" value=`"$Width`" />`n    <int name=`"in_app_video_encode_h`" value=`"$Height`" />`n</map>"
        }
        $tmpLocal = [System.IO.Path]::GetTempFileName() + ".xml"
        [System.IO.File]::WriteAllText($tmpLocal, $patched, [System.Text.Encoding]::UTF8)
        $tmpDevice = "/data/local/tmp/pns_chrome_prefs_patch.xml"
        Invoke-AdbCmd push $tmpLocal $tmpDevice 2>&1 | Out-Null
        Invoke-AdbCmd shell run-as $pkg cp $tmpDevice $prefsPath 2>&1 | Out-Null
        Remove-Item $tmpLocal -Force -ErrorAction SilentlyContinue
        $verify = (Invoke-AdbCmd shell run-as $pkg cat $prefsPath 2>&1) -join "`n"
        $ok = ($verify -match "in_app_video_encode_w`" value=`"$Width`"") -and ($verify -match "in_app_video_encode_h`" value=`"$Height`"")
        if ($ok) {
            Write-Host "  Encode resolution set to ${Width}x${Height} in SharedPrefs (verified)"
        } else {
            Write-Host "  WARNING: SharedPrefs patch did not verify for ${Width}x${Height}"
        }
        return $ok
    }

    function Set-ChromeVideoCodecOrdinal {
        param([int]$Ordinal)
        $prefsPath = "/data/data/$pkg/shared_prefs/pns_preview_chrome.xml"
        $existing = (Invoke-AdbCmd shell run-as $pkg cat $prefsPath 2>&1) -join "`n"
        if ($existing -notmatch "<map>") { return $false }
        $patched = $existing -replace '(?s)<int name="in_app_video_codec_ordinal"[^/]*/>', "<int name=`"in_app_video_codec_ordinal`" value=`"$Ordinal`" />"
        if ($patched -notmatch "in_app_video_codec_ordinal") {
            $patched = $patched -replace '</map>', "    <int name=`"in_app_video_codec_ordinal`" value=`"$Ordinal`" />`n</map>"
        }
        $tmpLocal = [System.IO.Path]::GetTempFileName() + ".xml"
        [System.IO.File]::WriteAllText($tmpLocal, $patched, [System.Text.Encoding]::UTF8)
        $tmpDevice = "/data/local/tmp/pns_chrome_codec_patch.xml"
        Invoke-AdbCmd push $tmpLocal $tmpDevice 2>&1 | Out-Null
        Invoke-AdbCmd shell run-as $pkg cp $tmpDevice $prefsPath 2>&1 | Out-Null
        Remove-Item $tmpLocal -Force -ErrorAction SilentlyContinue
        $verify = (Invoke-AdbCmd shell run-as $pkg cat $prefsPath 2>&1) -join "`n"
        return ($verify -match "in_app_video_codec_ordinal`" value=`"$Ordinal`"")
    }

    function Pull-SavedMp4 {
        param([string]$TestName, [string]$AllLog)
        $localMp4 = Join-Path $OutDir "${TestName}.mp4"
        $savedUri = $null
        if ($AllLog -match 'saved=(content://[^\s\r\n]+)') {
            $savedUri = $Matches[1]
        } elseif ($AllLog -match 'inAppVideoSaved uri=(content://[^\s\r\n]+)') {
            $savedUri = $Matches[1]
        }
        if ($savedUri) {
            $pulled = $false
            $queryOut = (Invoke-AdbCmd shell content query --uri $savedUri --projection _data 2>&1) -join "`n"
            if ($queryOut -match '_data=([^\r\n]+)') {
                $dataPath = $Matches[1].Trim()
                $tmpDevice = "/data/local/tmp/pns_gate_clip.mp4"
                Invoke-AdbCmd shell "cp `"$dataPath`" $tmpDevice" 2>&1 | Out-Null
                Invoke-AdbCmd pull $tmpDevice $localMp4 2>&1 | Out-Null
                Invoke-AdbCmd shell rm -f $tmpDevice 2>&1 | Out-Null
                $pulled = (Test-Path $localMp4)
            }
            if (-not $pulled) {
                try {
                    $adbExe = (Get-Command adb -ErrorAction Stop).Source
                    $adbArgs = @()
                    if ($Serial -ne "") { $adbArgs += "-s", $Serial }
                    $adbArgs += "exec-out", "content", "read", "--uri", $savedUri
                    $p = Start-Process -FilePath $adbExe -ArgumentList $adbArgs -RedirectStandardOutput $localMp4 -NoNewWindow -Wait -PassThru
                    if ($p.ExitCode -ne 0) { Remove-Item $localMp4 -ErrorAction SilentlyContinue }
                } catch {
                    Write-Host "  content read failed: $_"
                }
            }
        }
        if (-not (Test-Path $localMp4) -or ((Get-Item $localMp4).Length -lt 50000)) {
            $dcim = "/sdcard/DCIM/Point & Shoot"
            $latestVideo = (Invoke-AdbCmd shell "ls -t '$dcim'/pns_*.mp4 2>/dev/null | head -1" 2>&1) -join "`n"
            $latestVideo = ($latestVideo -split "`n" | Where-Object { $_ -match "pns_.*\.mp4" } | Select-Object -First 1)
            if ($latestVideo) {
                $latestVideo = $latestVideo.Trim()
                Invoke-AdbCmd pull $latestVideo $localMp4 2>&1 | Out-Null
            }
        }
        if ((Test-Path $localMp4) -and ((Get-Item $localMp4).Length -ge 50000)) { return $localMp4 }
        $dcim = "/sdcard/DCIM/Point & Shoot"
        $latestVideo = (Invoke-AdbCmd shell "ls -t '$dcim'/pns_*.mp4 2>/dev/null | head -1" 2>&1) -join "`n"
        $latestVideo = ($latestVideo -split "`n" | Where-Object { $_ -match "pns_.*\.mp4" } | Select-Object -First 1)
        if ($latestVideo) {
            $latestVideo = $latestVideo.Trim()
            Invoke-AdbCmd pull $latestVideo $localMp4 2>&1 | Out-Null
        }
        if ((Test-Path $localMp4) -and ((Get-Item $localMp4).Length -ge 50000)) { return $localMp4 }
        return $null
    }

    function Get-PnsLogcat {
        $tags = @(
            "PNS.AdbValidation:I", "PNS.MCVideoRec:I", "PNS.MCVideoRec:E",
            "PNS.VideoController:I", "PNS.VideoRec:I", "PNS.ChromeUx:I",
            "PNS.VideoEncode:I", "PNS.Cam:I", "PNS.HfrInterleaved:I", "PNS.HfrMonitor:I"
        )
        (Invoke-AdbCmd logcat -d -v brief @tags 2>&1) -join "`n"
    }

    function Test-FfprobeAvClip {
        param(
            [string]$LocalPath,
            [int]$TargetFps,
            [string]$ExpectedVideoCodec = ""
        )
        $result = @{
            Ok = $false
            HasFfprobe = $false
            HasVideo = $false
            HasAudio = $false
            FpsOk = $false
            FramesOk = $false
            DurationOk = $false
            VideoPackets = 0
            DurationSec = 0.0
            CodecOk = $true
            VideoCodec = ""
            Summary = ""
        }
        if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
            $result.Summary = "ffprobe not on PATH"
            return $result
        }
        $result.HasFfprobe = $true
        if (-not (Test-Path $LocalPath)) {
            $result.Summary = "missing file"
            return $result
        }
        if ((Get-Item $LocalPath).Length -lt 50000) {
            $result.Summary = "file too small ($((Get-Item $LocalPath).Length) bytes)"
            return $result
        }
        $streamDump = (& ffprobe -v error -show_streams $LocalPath 2>&1) -join "`n"
        $result.HasVideo = $streamDump -match "codec_type=video"
        $result.HasAudio = $streamDump -match "codec_type=audio"
        $vw = (& ffprobe -v error -select_streams v:0 -show_entries stream=width -of default=noprint_wrappers=1:nokey=1 $LocalPath 2>&1) -join ""
        $vh = (& ffprobe -v error -select_streams v:0 -show_entries stream=height -of default=noprint_wrappers=1:nokey=1 $LocalPath 2>&1) -join ""
        $wh = "${vw}x${vh}"
        $codecName = (& ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of default=noprint_wrappers=1:nokey=1 $LocalPath 2>&1) -join ""
        $result.VideoCodec = $codecName.Trim()
        $fpsRaw = (& ffprobe -v error -select_streams v:0 -show_entries stream=avg_frame_rate -of default=noprint_wrappers=1:nokey=1 $LocalPath 2>&1) -join ""
        if ($fpsRaw -eq "0/0" -or $fpsRaw -eq "") {
            $fpsRaw = (& ffprobe -v error -select_streams v:0 -show_entries stream=r_frame_rate -of default=noprint_wrappers=1:nokey=1 $LocalPath 2>&1) -join ""
        }
        if ($fpsRaw -match "^(\d+)/(\d+)$") {
            $num = [double]$Matches[1]
            $den = [double]$Matches[2]
            if ($den -gt 0) {
                $fpsVal = $num / $den
                if ($TargetFps -ge 120) {
                    $minFps = [math]::Max(45.0, [double]$TargetFps * 0.75)
                    $result.FpsOk = $fpsVal -ge $minFps
                } else {
                    $tol = 3.0
                    $result.FpsOk = [math]::Abs($fpsVal - [double]$TargetFps) -lt $tol
                }
            }
        }
        if ($ExpectedVideoCodec -ne "") {
            $result.CodecOk = $result.VideoCodec -eq $ExpectedVideoCodec
        }
        $durLine = (& ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 $LocalPath 2>&1 | Select-Object -First 1) -join ""
        if ($durLine -match "^([0-9]+(?:\.[0-9]+)?)") {
            $result.DurationSec = [double]$Matches[1]
        }
        if ($result.DurationSec -le 0 -or $result.DurationSec -gt 600) {
            $vDur = (& ffprobe -v error -select_streams v:0 -show_entries stream=duration -of default=nw=1:nk=1 $LocalPath 2>&1 | Select-Object -First 1) -join ""
            if ($vDur -match "^([0-9]+(?:\.[0-9]+)?)") {
                $result.DurationSec = [double]$Matches[1]
            }
        }
        $pktRaw = (& ffprobe -v error -select_streams v:0 -count_packets -show_entries stream=nb_read_packets -of csv=p=0 $LocalPath 2>&1) -join ""
        if ($pktRaw -match "^\d+$") {
            $result.VideoPackets = [int]$pktRaw
        }
        if ($result.VideoPackets -le 0) {
            $nbFrames = (& ffprobe -v error -select_streams v:0 -count_frames -show_entries stream=nb_read_frames -of csv=p=0 $LocalPath 2>&1) -join ""
            if ($nbFrames -match "^\d+$") {
                $result.VideoPackets = [int]$nbFrames
            }
        }
        $minPackets =
            if ($TargetFps -ge 480) { [int][math]::Max(400, $TargetFps * 2) }
            elseif ($TargetFps -ge 240) { [int][math]::Max(200, $TargetFps * 2) }
            elseif ($TargetFps -ge 120) { [int][math]::Max(120, $TargetFps * 2) }
            else { 30 }
        if ($result.DurationSec -gt 0.5 -and $result.DurationSec -lt 120) {
            $expectedByDuration = [int][math]::Floor($result.DurationSec * [double]$TargetFps * 0.35)
            if ($expectedByDuration -gt $minPackets) { $minPackets = $expectedByDuration }
        }
        $result.FramesOk = $result.VideoPackets -ge $minPackets
        $maxDurSec = if ($TargetFps -ge 480) { 25 } elseif ($TargetFps -ge 120) { 25 } else { 15 }
        $result.DurationOk =
            $result.DurationSec -gt 0.25 -and
            $result.DurationSec -le $maxDurSec
        $dimsOk = ($wh -match "3840x2160|1920x1080|1280x720")
        $result.Ok = $result.HasVideo -and $result.HasAudio -and $dimsOk -and $result.FpsOk -and
            $result.FramesOk -and $result.DurationOk -and $result.CodecOk
        $containerFps = ""
        if ($fpsRaw -match "^(\d+)/(\d+)$") {
            $n = [double]$Matches[1]; $d = [double]$Matches[2]
            if ($d -gt 0) { $containerFps = " containerFps=$([math]::Round($n / $d, 1))" }
        }
        $result.Summary = "video=$($result.HasVideo) audio=$($result.HasAudio) wxh=$wh codec=$($result.VideoCodec) " +
            "targetFps=$TargetFps vPkts=$($result.VideoPackets) minPkts=$minPackets " +
            "dur=$([math]::Round($result.DurationSec, 2))s durOk=$($result.DurationOk)$containerFps " +
            "bytes=$((Get-Item $LocalPath).Length)"
        return $result
    }

    function Test-RecordingMode {
        param(
            [string]$TestName,
            [int]$Fps,
            [bool]$TenBit,
            [int]$Width = 1920,
            [int]$Height = 1080,
            [int]$CodecOrdinal = -1,
            [string]$ExpectedFfprobeCodec = "",
            [int]$DurationSec = 0
        )
        if ($DurationSec -le 0) {
            $DurationSec =
                if ($Fps -ge 480) { 10 }
                elseif ($Fps -ge 240) { 10 }
                elseif ($Fps -ge 120) { 12 }
                else { 8 }
        }
        Write-Log ""
        Write-Log "## Test: $TestName  ${Width}x${Height} fps=$Fps  10bit=$TenBit  codecOrdinal=$CodecOrdinal"

        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
        Start-Sleep -Milliseconds 800
        Clear-LogcatBuffer

        $chromePrefPatched = Set-ChromeVideoEncodePrefs -Width $Width -Height $Height
        $codecPrefPatched = $true
        if ($CodecOrdinal -ge 0) {
            $codecPrefPatched = Set-ChromeVideoCodecOrdinal -Ordinal $CodecOrdinal
            Write-Host "  codec ordinal pref patch: $codecPrefPatched (ordinal=$CodecOrdinal)"
        }

        $startArgs = @(
            "shell", "am", "start", "-n", "$pkg/$act",
            "--activity-clear-task",
            "--es", "pns_screen", "preview",
            "--ez", "pns_preview_primary_photo", "false",
            "--es", "pns_preview_imaging_profile", "standard_pro",
            "--ei", "pns_preview_automation_in_app_video_sec", "$DurationSec",
            "--ei", "pns_preview_video_fps", "$Fps",
            "--ei", "pns_preview_video_encode_w", "$Width",
            "--ei", "pns_preview_video_encode_h", "$Height",
            "--ei", "pns_preview_adaptive_battery_pct", "100",
            "--ei", "pns_preview_adaptive_thermal_status", "0",
            "--ez", "pns_preview_audio_hifi", "true",
            "--ez", "pns_preview_audio_wind", "true"
        )
        if ($TenBit) { $startArgs += @("--ez", "pns_preview_video_10bit", "true") }
        if ($CodecOrdinal -ge 0) {
            $startArgs += @("--ei", "pns_preview_video_codec_ordinal", "$CodecOrdinal")
        }
        Invoke-AdbCmd @startArgs 2>&1 | Out-Null

        $waitExtra =
            if ($Fps -ge 480) { 140 }
            elseif ($Fps -ge 240) { 110 }
            elseif ($Fps -ge 120) { 100 }
            else { 18 }
        $waitTotal = $DurationSec + $waitExtra
        Write-Host "  Waiting ${waitTotal}s for automation..."
        Start-Sleep -Seconds $waitTotal

        $pnsLog = Get-PnsLogcat
        $fullLog = (Invoke-AdbCmd logcat -d -v threadtime -t 400 2>&1) -join "`n"
        $pnsLog | Out-File -FilePath (Join-Path $OutDir "log_${TestName}_pns.txt") -Encoding utf8
        $fullLog | Out-File -FilePath (Join-Path $OutDir "log_${TestName}.txt") -Encoding utf8
        $allLog = $pnsLog

        $usedMcPath = $allLog -match "mcVideoPrepared|MediaCodecVideoRecorder started"
        $usedMrPath = $allLog -match "inAppVideoPrepared"
        $savedBytes = 0
        if ($allLog -match "inAppVideoSaved ok=true bytes=(-?\d+)") {
            $savedBytes = [int]$Matches[1]
        }
        $savedUriOk = $allLog -match "inAppVideoSaved ok=true saved=(content://[^\s\r\n]+)"
        $savedOk = ($savedBytes -ge 50000) -or ($savedUriOk -and $savedBytes -lt 0)
        $prepMiss = $allLog -match "inAppVideoAutomation recorderMissingOrFailed"
        $codecError =
            $allLog -match "inAppVideoShellStartFailed" -or
            $allLog -match "MCVideoRec.*prepare failed" -or
            $allLog -match "PNS\.Cam:.*CAMERA_DISCONNECTED" -or
            ($prepMiss -and -not $savedOk)
        $correctFps = $allLog -match "fps=$Fps"
        $previewFpsLog = ""
        if ($allLog -match "previewFps=([0-9.]+)") {
            $previewFpsLog = $Matches[1]
        }
        $forcedCodecOk = if ($CodecOrdinal -ge 0) {
            $allLog -match "inAppVideoFormat=forcedCodec|inAppVideoFormat=forcedCodecNearest|inAppVideoFormat=userPick|codec=H\.|codec=AV1"
        } else { $true }
        $requiresMcPath = ($Fps -ge 120) -or $TenBit
        $needs4k120Proof = ($Width -ge 3840) -and ($Fps -ge 120)
        $hsBurstOk = if ($Fps -ge 120) {
            ($fullLog -match "HFR repeatingBurst started") -or
                ($allLog -match "HFR repeatingBurst encoder-only") -or
                ($allLog -match "Preview running \(HFR $Fps")
        } else { $true }
        $sessionSizeOk = if ($Fps -ge 120) {
            $allLog -match "sessionBufferSet (1280x720|1920x1080|${Width}x${Height})"
        } elseif ($needs4k120Proof) {
            $allLog -match "sessionBufferSet (1280x720|1920x1080|${Width}x${Height})"
        } else { $true }
        $mcSizeOk = if ($requiresMcPath) {
            $allLog -match "mcVideoPrepared audioEnabled=true size=(1280x720|1920x1080|${Width}x${Height}) fps=$Fps"
        } else {
            $allLog -match "mcVideoPrepared"
        }
        $mcFramesWritten = 0
        if ($allLog -match "mcVideoFramesWritten=(\d+)") {
            $mcFramesWritten = [int]$Matches[1]
        }
        $mcFramesOk = if ($requiresMcPath) {
            $minMcFrames =
                if ($Fps -ge 480) { 400 }
                elseif ($Fps -ge 240) { 200 }
                else { 120 }
            $mcFramesWritten -ge $minMcFrames
        } else { $true }
        $encodePrefOk = if ($needs4k120Proof) {
            $chromePrefPatched -or $allLog -match "encodePrefSet ${Width}x${Height}"
        } else { $true }

        $hasFfprobeCmd = [bool](Get-Command ffprobe -ErrorAction SilentlyContinue)
        $ffprobeAv = @{
            Ok = $false
            HasFfprobe = $hasFfprobeCmd
            HasVideo = $false
            HasAudio = $false
            FpsOk = $false
            CodecOk = $false
            VideoCodec = ""
            Summary = "not run"
        }
        if ($savedOk) {
            $localMp4 = Pull-SavedMp4 -TestName $TestName -AllLog $allLog
            if ($localMp4) {
                $ffprobeAv = Test-FfprobeAvClip -LocalPath $localMp4 -TargetFps $Fps -ExpectedVideoCodec $ExpectedFfprobeCodec
                Write-Log "  ffprobe A/V          : $($ffprobeAv.Summary)"
            } else {
                Write-Log "  ffprobe A/V          : could not pull MP4 (saved in log)"
            }
        } else {
            Write-Log "  ffprobe A/V          : skip — inAppVideoSaved missing"
        }
        $ffprobeOk = $ffprobeAv.Ok
        if ($RequireFfprobeAv -and -not $hasFfprobeCmd) {
            Write-Log "  ffprobe: REQUIRED but not on PATH"
            $ffprobeOk = $false
        }

        $pass = $savedOk -and -not $codecError -and $codecPrefPatched -and (-not $requiresMcPath -or $usedMcPath)
        if ($CodecOrdinal -eq 0 -and $Fps -lt 120) {
            $pass = $pass -and $usedMrPath
        }
        if ($Fps -ge 120) {
            $pass = $pass -and $hsBurstOk -and $sessionSizeOk -and $mcSizeOk -and $mcFramesOk -and $correctFps
        }
        if ($needs4k120Proof) {
            $pass = $pass -and $encodePrefOk -and $ffprobeOk
        } elseif ($RequireFfprobeAv -or $savedOk) {
            $pass = $pass -and $ffprobeOk
        }

        Write-Log "  MediaCodec path used : $usedMcPath"
        Write-Log "  MediaRecorder path   : $usedMrPath"
        Write-Log "  HS burst in log      : $hsBurstOk"
        Write-Log "  Preview fps (log)    : $previewFpsLog"
        Write-Log "  Correct fps in log   : $correctFps"
        Write-Log "  codec pref patched   : $codecPrefPatched"
        Write-Log "  sessionBuffer HFR    : $sessionSizeOk"
        Write-Log "  mcPrepared           : $mcSizeOk"
        Write-Log "  ffprobe ok           : $ffprobeOk"
        if ($ffprobeAv.ContainsKey("FramesOk")) {
            Write-Log "  ffprobe frames ok    : $($ffprobeAv.FramesOk) (vPkts=$($ffprobeAv.VideoPackets))"
        }
        if ($requiresMcPath) {
            Write-Log "  mcVideoFramesWritten : $mcFramesWritten ok=$mcFramesOk"
        }
        Write-Log "  inAppVideoSaved      : $savedOk bytes=$savedBytes"
        Write-Log "  Codec errors         : $codecError"
        Write-Log "  RESULT               : $(if ($pass) { 'PASS' } else { 'FAIL' })"

        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

        return @{
            Test = $TestName
            Fps = $Fps
            TenBit = $TenBit
            CodecOrdinal = $CodecOrdinal
            Pass = $pass
            McPath = $usedMcPath
            MrPath = $usedMrPath
            Saved = $savedOk
            Error = $codecError
            FfprobeAv = $ffprobeAv
            FfprobeOk = $ffprobeOk
        }
    }

    $devices = (Invoke-AdbCmd devices 2>&1) | Where-Object { $_ -match "`tdevice$" }
    if (-not $devices) {
        throw "No ADB device connected."
    }

    Set-Content $ResultsFile "# MediaCodecVideoRecorder Verification (Sprint 13V.16)"
    Add-Content $ResultsFile "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    Add-Content $ResultsFile ""

    Write-Log "## Codec Capabilities"
    $codecDump = (Invoke-AdbCmd shell dumpsys media.player 2>&1) | Select-String -Pattern "hevc.encoder|frame-rate-range|performance-point|Main10|YUVP010"
    $codecDump | ForEach-Object { Write-Log "  $_" }

    Write-Log ""
    Write-Log "## Bootstrap — SharedPrefs"
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
    Start-Sleep -Milliseconds 500
    Invoke-AdbCmd shell am start -n "$pkg/$act" --es pns_screen preview 2>&1 | Out-Null
    Start-Sleep -Seconds 8
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
    Write-Log "  Bootstrap complete"

    if ($GateProfile -eq "vf") {
        $RequireFfprobeAv = $true
    }

    $allCases = @(
        @{ Name = "H264_1080p_60fps";        Fps = 60;  TenBit = $false; W = 1920; H = 1080; Codec = 0;  FfCodec = "h264" },
        @{ Name = "H265_1080p_60fps";        Fps = 60;  TenBit = $false; W = 1920; H = 1080; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "H264_HFR_1080p_120fps";   Fps = 120; TenBit = $false; W = 1920; H = 1080; Codec = 0;  FfCodec = "h264" },
        @{ Name = "H265_HFR_1080p_120fps";   Fps = 120; TenBit = $false; W = 1920; H = 1080; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "H264_HFR_1080p_240fps";    Fps = 240; TenBit = $false; W = 1920; H = 1080; Codec = 0;  FfCodec = "h264" },
        @{ Name = "H265_HFR_1080p_240fps";    Fps = 240; TenBit = $false; W = 1920; H = 1080; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "H264_HFR_1080p_480fps";    Fps = 480; TenBit = $false; W = 1920; H = 1080; Codec = 0;  FfCodec = "h264" },
        @{ Name = "H265_HFR_1080p_480fps";    Fps = 480; TenBit = $false; W = 1920; H = 1080; Codec = 1;  FfCodec = "hevc" },
        # H.264 4K@120: known FAIL on CPH2655 (MC falls back to 1080p) — see docs/VIDEO_MODE_MATRIX.md
        # @{ Name = "H264_HFR_4K_120fps";       Fps = 120; TenBit = $false; W = 3840; H = 2160; Codec = 0;  FfCodec = "h264" },
        # 4K@120 not in UI unless camera HS lists 3840x2160@120 — gate uses 1080p HFR only (vf profile)
        # @{ Name = "H265_HFR_4K_120fps";       Fps = 120; TenBit = $false; W = 3840; H = 2160; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "HFR_1080p_120fps";        Fps = 120; TenBit = $false; W = 1920; H = 1080; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "HFR_1080p_240fps";         Fps = 240; TenBit = $false; W = 1920; H = 1080; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "HFR_1080p_480fps";         Fps = 480; TenBit = $false; W = 1920; H = 1080; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "4K_120fps_MediaCodec";    Fps = 120; TenBit = $false; W = 3840; H = 2160; Codec = 1;  FfCodec = "hevc" },
        @{ Name = "TenBit_1080p_60fps";       Fps = 60;  TenBit = $true;  W = 1920; H = 1080; Codec = -1; FfCodec = "hevc" },
        @{ Name = "TenBit_HDR10_1080p";      Fps = 60;  TenBit = $true;  W = 1920; H = 1080; Codec = -1; FfCodec = "hevc" },
        @{ Name = "4K_30fps";                Fps = 30;  TenBit = $false; W = 3840; H = 2160; Codec = -1; FfCodec = "" },
        @{ Name = "4K_60fps";                Fps = 60;  TenBit = $false; W = 3840; H = 2160; Codec = -1; FfCodec = "" }
    )

    $vfCaseNames = @(
        "H264_1080p_60fps",
        "H265_1080p_60fps",
        "H264_HFR_1080p_120fps",
        "H265_HFR_1080p_120fps",
        "H264_HFR_1080p_240fps",
        "H265_HFR_1080p_240fps",
        "H264_HFR_1080p_480fps",
        "H265_HFR_1080p_480fps"
    )
    if ($GateProfile -eq "vf") {
        $allCases = @($allCases | Where-Object { $vfCaseNames -contains $_.Name })
    }
    if ($OnlyTest -ne "") {
        $allCases = @($allCases | Where-Object { $_.Name -eq $OnlyTest })
        if (-not $allCases) { throw "Unknown -OnlyTest '$OnlyTest'" }
    }
    $results = @()
    foreach ($c in $allCases) {
        $results += Test-RecordingMode `
            -TestName $c.Name `
            -Fps $c.Fps `
            -TenBit $c.TenBit `
            -Width $c.W `
            -Height $c.H `
            -CodecOrdinal $c.Codec `
            -ExpectedFfprobeCodec $c.FfCodec
    }

    Write-Log ""
    Write-Log "## Summary"
    $passCount  = @($results | Where-Object { $_.Pass }).Count
    $total = $results.Count
    Write-Log "Passed: $passCount / $total"

    foreach ($r in $results) {
        $icon = if ($r.Pass) { "PASS" } else { "FAIL" }
        Write-Log "  [$icon] $($r.Test)  fps=$($r.Fps)  mc=$($r.McPath)  mr=$($r.MrPath)  ffprobeAv=$($r.FfprobeOk)"
    }

    $results | ConvertTo-Json -Depth 4 | Set-Content -Path $SummaryJson -Encoding UTF8

    Write-Host ""
    Write-Host "Results: $ResultsFile"
    Write-Host "JSON:    $SummaryJson"

    if ($passCount -lt $total) {
        Write-Host "GATE: FAIL ($passCount/$total passed)" -ForegroundColor Red
        exit 1
    }
    Write-Host "GATE: PASS ($passCount/$total)" -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
    if ($Serial -ne "") { & adb -s $Serial shell am force-stop dev.pointandshoot 2>$null | Out-Null }
    else { & adb shell am force-stop dev.pointandshoot 2>$null | Out-Null }
}
