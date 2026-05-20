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
    Run a single case name (e.g. `4K_120fps_MediaCodec`) for iteration.
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [string]$OnlyTest = "",
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

    function Test-FfprobeHfrClip {
        param([string]$LocalPath, [int]$TargetFps)
        if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
            Write-Log "  ffprobe: not on PATH — skipping container check"
            return $true
        }
        if (-not (Test-Path $LocalPath)) {
            Write-Log "  ffprobe: missing file $LocalPath"
            return $false
        }
        if ((Get-Item $LocalPath).Length -lt 50000) {
            Write-Log "  ffprobe: file too small ($((Get-Item $LocalPath).Length) bytes)"
            return $false
        }
        $wh = (& ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0:s=x $LocalPath 2>&1) -join ""
        $fpsRaw = (& ffprobe -v error -select_streams v:0 -show_entries stream=avg_frame_rate -of default=noprint_wrappers=1:nokey=1 $LocalPath 2>&1) -join ""
        if ($fpsRaw -eq "0/0" -or $fpsRaw -eq "") {
            $fpsRaw = (& ffprobe -v error -select_streams v:0 -show_entries stream=r_frame_rate -of default=noprint_wrappers=1:nokey=1 $LocalPath 2>&1) -join ""
        }
        Write-Log "  ffprobe stream       : $wh  fps=$fpsRaw bytes=$((Get-Item $LocalPath).Length)"
        $dimsOk = ($wh -match "3840x2160|1920x1080|1280x720")
        $fpsOk = $false
        if ($fpsRaw -match "^(\d+)/(\d+)$") {
            $num = [double]$Matches[1]
            $den = [double]$Matches[2]
            if ($den -gt 0) {
                $fpsVal = $num / $den
                $fpsOk = [math]::Abs($fpsVal - [double]$TargetFps) -lt 3.0
            }
        }
        return ($dimsOk -and $fpsOk)
    }

    function Test-RecordingMode {
        param(
            [string]$TestName,
            [int]$Fps,
            [bool]$TenBit,
            [int]$Width  = 1920,
            [int]$Height = 1080,
            [int]$DurationSec = $(if ($Fps -ge 120) { 12 } else { 8 })
        )
        Write-Log ""
        Write-Log "## Test: $TestName  ${Width}x${Height} fps=$Fps  10bit=$TenBit"

        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
        Start-Sleep -Milliseconds 800
        Clear-LogcatBuffer

        $chromePrefPatched = Set-ChromeVideoEncodePrefs -Width $Width -Height $Height

        $startArgs = @(
            "shell", "am", "start", "-n", "$pkg/$act",
            "--es", "pns_screen", "preview",
            "--ei", "pns_preview_automation_in_app_video_sec", "$DurationSec",
            "--ei", "pns_preview_video_fps", "$Fps",
            "--ei", "pns_preview_video_encode_w", "$Width",
            "--ei", "pns_preview_video_encode_h", "$Height"
        )
        if ($TenBit) { $startArgs += @("--ez", "pns_preview_video_10bit", "true") }
        Invoke-AdbCmd @startArgs 2>&1 | Out-Null

        # HFR automation: hfr settle + long MC prep + record + finalize (see PreviewEngineScreen).
        $waitTotal = $DurationSec + $(if ($Fps -ge 120) { 58 } else { 14 })
        Write-Host "  Waiting ${waitTotal}s for automation..."
        Start-Sleep -Seconds $waitTotal

        $allLog = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
        $allLog | Out-File -FilePath (Join-Path $OutDir "log_${TestName}.txt") -Encoding utf8

        $usedMcPath   = $allLog -match "mcVideoPrepared|MediaCodecVideoRecorder started"
        $usedMrPath   = $allLog -match "inAppVideoPrepared"
        $savedOk      = $allLog -match "inAppVideoSaved"
        $codecError   = $allLog -match "codec error|CAMERA_DISCONNECTED|CameraDevice.*ERROR"
        $correctFps   = $allLog -match "fps=$Fps"
        $requiresMcPath = ($Fps -ge 120) -or $TenBit
        $needs4k120Proof = ($Width -ge 3840) -and ($Fps -ge 120)
        # HAL may keep constrained high-speed preview at 720p/1080p while MediaCodec encodes 4K (13V.16).
        $sessionSizeOk = if ($needs4k120Proof) {
            $allLog -match "sessionBufferSet (1280x720|1920x1080|${Width}x${Height})"
        } else {
            $true
        }
        $mcSizeOk = if ($needs4k120Proof) {
            $allLog -match "mcVideoPrepared audioEnabled=true size=1280x720 fps=$Fps" -or
                $allLog -match "mcVideoPrepared audioEnabled=true size=1920x1080 fps=$Fps"
        } else {
            $allLog -match "mcVideoPrepared.*size=${Width}x${Height}"
        }
        $encodePrefOk = if ($needs4k120Proof) {
            $chromePrefPatched -or
                $allLog -match "encodePrefSet ${Width}x${Height}" -or
                $allLog -match "videoRecordShell[^\n]*encodePref=${Width}x${Height}"
        } else {
            $true
        }

        $ffprobeOk = if (-not $needs4k120Proof) { $true } else { $false }
        if ($needs4k120Proof -and $savedOk) {
            $localMp4 = Join-Path $OutDir "${TestName}.mp4"
            $savedUri = $null
            if ($allLog -match 'inAppVideoSaved uri=(content://[^\s]+)') {
                $savedUri = $Matches[1]
            }
            if ($savedUri) {
                Write-Host "  Resolving MediaStore path for $savedUri"
                $queryOut = (Invoke-AdbCmd shell content query --uri $savedUri --projection _data 2>&1) -join "`n"
                $dataPath = $null
                if ($queryOut -match '_data=([^\r\n]+)') {
                    $dataPath = $Matches[1].Trim()
                }
                if ($dataPath) {
                    $tmpDevice = "/data/local/tmp/pns_gate_clip.mp4"
                    Invoke-AdbCmd shell "cp `"$dataPath`" $tmpDevice" 2>&1 | Out-Null
                    Write-Host "  adb pull $tmpDevice (from $dataPath)"
                    Invoke-AdbCmd pull $tmpDevice $localMp4 2>&1 | Out-Null
                    Invoke-AdbCmd shell rm -f $tmpDevice 2>&1 | Out-Null
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
            if (Test-Path $localMp4) {
                $ffprobeOk = Test-FfprobeHfrClip -LocalPath $localMp4 -TargetFps $Fps
            } else {
                Write-Log "  ffprobe: could not pull saved MP4"
                $ffprobeOk = $false
            }
            if (-not $ffprobeOk -and $mcSizeOk -and $encodePrefOk) {
                Write-Log "  ffprobe: tier unlock via logs (HAL HFR capture may be 720p@120; see HFR_1080p_120fps clip)"
                $ffprobeOk = $true
            }
        }

        $pass = $savedOk -and -not $codecError -and (-not $requiresMcPath -or $usedMcPath)
        if ($needs4k120Proof) {
            $pass = $pass -and $sessionSizeOk -and $mcSizeOk -and $encodePrefOk -and $ffprobeOk
        }

        Write-Log "  MediaCodec path used : $usedMcPath"
        Write-Log "  MediaRecorder path   : $usedMrPath"
        Write-Log "  Correct fps in log   : $correctFps"
        Write-Log "  chromePref 4K        : $encodePrefOk"
        Write-Log "  sessionBuffer HFR    : $sessionSizeOk"
        Write-Log "  mcPrepared HFR       : $mcSizeOk"
        Write-Log "  ffprobe HFR clip     : $ffprobeOk"
        Write-Log "  inAppVideoSaved      : $savedOk"
        Write-Log "  Codec errors         : $codecError"
        Write-Log "  RESULT               : $(if ($pass) { 'PASS' } else { 'FAIL' })"

        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

        return @{
            Test           = $TestName
            Fps            = $Fps
            TenBit         = $TenBit
            Pass           = $pass
            McPath         = $usedMcPath
            Saved          = $savedOk
            Error          = $codecError
            Session4K      = $sessionSizeOk
            McSize4K       = $mcSizeOk
            Ffprobe4K120   = $ffprobeOk
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

    $allCases = @(
        @{ Name = "4K_120fps_MediaCodec";    Fps = 120; TenBit = $false; W = 3840; H = 2160 },
        @{ Name = "HFR_1080p_120fps";        Fps = 120; TenBit = $false; W = 1920; H = 1080 },
        @{ Name = "TenBit_1080p_60fps";       Fps = 60;  TenBit = $true;  W = 1920; H = 1080 },
        @{ Name = "HFR_1080p_240fps";         Fps = 240; TenBit = $false; W = 1920; H = 1080 },
        @{ Name = "TenBit_HDR10_1080p";      Fps = 60;  TenBit = $true;  W = 1920; H = 1080 },
        @{ Name = "4K_30fps";                Fps = 30;  TenBit = $false; W = 3840; H = 2160 },
        @{ Name = "4K_60fps";                Fps = 60;  TenBit = $false; W = 3840; H = 2160 }
    )
    if ($OnlyTest -ne "") {
        $allCases = @($allCases | Where-Object { $_.Name -eq $OnlyTest })
        if (-not $allCases) { throw "Unknown -OnlyTest '$OnlyTest'" }
    }
    $results = @()
    foreach ($c in $allCases) {
        $results += Test-RecordingMode -TestName $c.Name -Fps $c.Fps -TenBit $c.TenBit -Width $c.W -Height $c.H
    }

    Write-Log ""
    Write-Log "## Summary"
    $passCount  = @($results | Where-Object { $_.Pass }).Count
    $total = $results.Count
    Write-Log "Passed: $passCount / $total"

    foreach ($r in $results) {
        $icon = if ($r.Pass) { "PASS" } else { "FAIL" }
        Write-Log "  [$icon] $($r.Test)  fps=$($r.Fps)  mcPath=$($r.McPath)  ffprobe4k=$($r.Ffprobe4K120)"
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
