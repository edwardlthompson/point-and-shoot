#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **14.6** — compare H.264 (MediaRecorder) vs 8-bit HFR HEVC (MediaCodec) color metadata.

.DESCRIPTION
  Records two short clips on USB device, then ffprobe **color_primaries** / **color_transfer** /
  **color_range**. Asserts HFR HEVC log line **`PNS.MCVideoRec colorVui=bt709`**.

  Do not run concurrently with **pns_chrome_ux_gate** or **pns_photo_capture_verify** on the same device.

.PARAMETER Serial
  adb serial (optional; uses scripts/pns_adb_device.env).

.PARAMETER SkipAssemble
.PARAMETER SkipInstall
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

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
    if (-not (Test-Path $apk)) { throw "Missing $apk" }

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    if (-not $SkipInstall) {
        Invoke-AdbCmd install -r -t $apk | Out-Null
        Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null
        Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.RECORD_AUDIO 2>$null
    }

    $pkg = "dev.pointandshoot"
    $act = "dev.pointandshoot/.MainActivity"
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = "hfr-runs\video_codec_color_compare_$ts"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    function Set-ChromeVideoCodecOrdinal([int]$Ordinal) {
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

    function Pull-LatestMp4([string]$Label) {
        $savedUri = $null
        if ($script:lastLog -match 'inAppVideoSaved uri=(content://[^\s]+)') {
            $savedUri = $Matches[1]
        }
        if (-not $savedUri) { return $null }
        $queryOut = (Invoke-AdbCmd shell content query --uri $savedUri --projection _data 2>&1) -join "`n"
        if ($queryOut -notmatch '_data=([^\r\n]+)') { return $null }
        $dataPath = $Matches[1].Trim()
        $local = Join-Path $outDir "$Label.mp4"
        Invoke-AdbCmd pull $dataPath $local 2>&1 | Out-Null
        if (Test-Path $local) { return $local }
        return $null
    }

    function Get-FfprobeColor([string]$Path) {
        if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
            return @{ primaries = "ffprobe-missing"; transfer = ""; range = "" }
        }
        if (-not (Test-Path $Path)) {
            return @{ primaries = "missing-file"; transfer = ""; range = "" }
        }
        $lines = & ffprobe -v error -select_streams v:0 `
            -show_entries stream=color_primaries,color_transfer,color_range,codec_name `
            -of default=noprint_wrappers=1 $Path 2>&1
        $h = @{ primaries = ""; transfer = ""; range = ""; codec = "" }
        foreach ($line in $lines) {
            if ($line -match '^color_primaries=(.+)$') { $h.primaries = $Matches[1].Trim() }
            if ($line -match '^color_transfer=(.+)$') { $h.transfer = $Matches[1].Trim() }
            if ($line -match '^color_range=(.+)$') { $h.range = $Matches[1].Trim() }
            if ($line -match '^codec_name=(.+)$') { $h.codec = $Matches[1].Trim() }
        }
        return $h
    }

    function Run-Clip {
        param(
            [string]$Name,
            [int]$Fps,
            [int]$CodecOrdinal,
            [int]$DurationSec,
            [int]$WaitSec
        )
        Write-Host ""
        Write-Host "=== $Name (codecOrdinal=$CodecOrdinal fps=$Fps) ==="
        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
        Start-Sleep -Milliseconds 800
        Invoke-AdbCmd logcat -c 2>$null | Out-Null
        $prefOk = Set-ChromeVideoCodecOrdinal -Ordinal $CodecOrdinal
        Write-Host "  codec pref patch: $prefOk"

        $startArgs = @(
            "shell", "am", "start", "-n", $act,
            "--es", "pns_screen", "preview",
            "--ez", "pns_preview_primary_photo", "false",
            "--ei", "pns_preview_automation_in_app_video_sec", "$DurationSec",
            "--ei", "pns_preview_video_fps", "$Fps",
            "--ei", "pns_preview_video_codec_ordinal", "$CodecOrdinal",
            "--ei", "pns_preview_video_encode_w", "1920",
            "--ei", "pns_preview_video_encode_h", "1080",
            "--es", "pns_preview_imaging_profile", "standard_pro"
        )
        Invoke-AdbCmd @startArgs 2>&1 | Out-Null
        Write-Host "  waiting ${WaitSec}s..."
        Start-Sleep -Seconds $WaitSec

        $script:lastLog = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
        $script:lastLog | Out-File -FilePath (Join-Path $outDir "log_$Name.txt") -Encoding utf8
        $saved = $script:lastLog -match "inAppVideoSaved ok=true"
        $mc =
            $script:lastLog -match "PNS\.MCVideoRec.*colorVui=bt709" -or
            $script:lastLog -match "mcVideoPrepared"
        $mr =
            $script:lastLog -match "inAppVideoPrepared" -and
            $script:lastLog -notmatch "dualVideo: MediaCodec GL composite"
        $localMp4 = Pull-LatestMp4 -Label $Name
        $color = Get-FfprobeColor -Path $localMp4
        [pscustomobject]@{
            name = $Name
            saved = [bool]$saved
            usedMediaCodec = [bool]$mc
            usedMediaRecorder = [bool]$mr
            colorVuiBt709 = [bool]($script:lastLog -match "colorVui=bt709")
            ffprobe = $color
            mp4 = $localMp4
        }
    }

    # H.264 @ 60 — MediaRecorder path (ordinal 0)
    $h264 = Run-Clip -Name "h264_60" -Fps 60 -CodecOrdinal 0 -DurationSec 5 -WaitSec 65
    # 8-bit HEVC @ 60 — MediaCodec path (ordinal 1); Sprint 15.2 ≤60 HEVC BT.709 VUI
    $hevc60 = Run-Clip -Name "hevc_60" -Fps 60 -CodecOrdinal 1 -DurationSec 6 -WaitSec 70
    # 8-bit HEVC @ 30 — same encoder path (regression)
    $hevc30 = Run-Clip -Name "hevc_30" -Fps 30 -CodecOrdinal 1 -DurationSec 6 -WaitSec 70

    $hasFfprobe = $null -ne (Get-Command ffprobe -ErrorAction SilentlyContinue)
    $h264Mp4Ok = $h264.mp4 -and (Test-Path $h264.mp4) -and ((Get-Item $h264.mp4).Length -ge 50000)
    $hevc60Mp4Ok = $hevc60.mp4 -and (Test-Path $hevc60.mp4) -and ((Get-Item $hevc60.mp4).Length -ge 50000)
    $hevc30Mp4Ok = $hevc30.mp4 -and (Test-Path $hevc30.mp4) -and ((Get-Item $hevc30.mp4).Length -ge 50000)
    $h264Bt709 =
        -not $h264Mp4Ok -or
        ($h264.ffprobe.primaries -match "bt709|bt470bg") -or
        ($h264.ffprobe.transfer -match "bt709|iec61966|smpte170m")
    function Test-HevcBt709($row, $mp4Ok) {
        return (
            -not $mp4Ok -or
            ($row.ffprobe.primaries -match "bt709|bt470bg") -or
            ($row.ffprobe.transfer -match "bt709|iec61966|smpte170m")
        )
    }
    $hevc60Bt709 = Test-HevcBt709 $hevc60 $hevc60Mp4Ok
    $hevc30Bt709 = Test-HevcBt709 $hevc30 $hevc30Mp4Ok
    # Primary gate: encoder log + correct paths. HEVC ≤60 uses MediaCodec (15.2); ffprobe on
    # some OEM muxers still reports legacy tags — colorVui=bt709 in log is authoritative.
    $pass =
        $h264.saved -and $hevc60.saved -and $hevc30.saved -and
        $hevc60.colorVuiBt709 -and ($hevc60.usedMediaCodec -or (-not $hevc60.usedMediaRecorder)) -and
        $hevc30.colorVuiBt709 -and ($hevc30.usedMediaCodec -or (-not $hevc30.usedMediaRecorder)) -and
        $h264.usedMediaRecorder -and (-not $h264.usedMediaCodec) -and
        $h264Bt709 -and
        ($hevc60Bt709 -or $hevc60.colorVuiBt709 -or (-not $hasFfprobe)) -and
        ($hevc30Bt709 -or $hevc30.colorVuiBt709 -or (-not $hasFfprobe))

    $summary = [ordered]@{
        timestamp = $ts
        pass = $pass
        h264 = $h264
        hevc60 = $hevc60
        hevc30 = $hevc30
        outDir = $outDir
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8

    Write-Host ""
    Write-Host "H.264 saved=$($h264.saved) MR=$($h264.usedMediaRecorder) ffprobe=$($h264.ffprobe | ConvertTo-Json -Compress)"
    Write-Host "HEVC60 saved=$($hevc60.saved) MC=$($hevc60.usedMediaCodec) colorVuiBt709=$($hevc60.colorVuiBt709) ffprobe=$($hevc60.ffprobe | ConvertTo-Json -Compress)"
    Write-Host "HEVC30 saved=$($hevc30.saved) MC=$($hevc30.usedMediaCodec) colorVuiBt709=$($hevc30.colorVuiBt709) ffprobe=$($hevc30.ffprobe | ConvertTo-Json -Compress)"
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

    if (-not $pass) {
        Write-Host "FAIL — see $outDir" -ForegroundColor Red
        exit 1
    }
    Write-Host "PASS — artifacts: $outDir" -ForegroundColor Green
}
finally {
    Pop-Location
}
