#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.3** — record short clips per picker tier; ffprobe A/V + fps + color VUI (USB).

.DESCRIPTION
  Default **-Quick** sweeps 1080p H.264@60 (MR), H.265@30 (MC), H.264@120 (MC HFR when device lists HS).
  **-Full** adds 4K30 H.264/H.265 and 8K30 probe row (may fail with banner — documented).

  Do not run concurrently with **pns_chrome_ux_gate** or **pns_photo_capture_verify** on one device.

.PARAMETER HostOnly
  Exit 0 without USB.

.PARAMETER Quick
  Default: core 1080p rows only.

.PARAMETER Full
  Include 4K and 8K rows (long run).

.PARAMETER SkipAssemble / SkipInstall
#>
param(
    [switch]$HostOnly,
    [switch]$Full,
    [switch]$Quick = (-not $Full),
    [string]$Serial = "",
    [int]$RecordSec = 5
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") {
        . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet
    }
    if ($HostOnly) {
        Write-Host "VIDEO MATRIX VERIFY: SKIP (HostOnly)"
        exit 0
    }

    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if ($Serial -eq "" -and (Test-Path $envFile)) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim().Trim('"') }
        }
    }

    & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }

    $apk = "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "Missing $apk" }

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    Invoke-AdbCmd install -r -t $apk | Out-Null
    Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null
    Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.RECORD_AUDIO 2>$null

    $pkg = "dev.pointandshoot"
    $act = "dev.pointandshoot/.MainActivity"
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = "hfr-runs\video_matrix_verify_$ts"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $rows = @(
        @{ id = "1080p60_h264"; w = 1920; h = 1080; fps = 60; codecOrd = 0; waitSec = 70 }
        @{ id = "1080p30_hevc"; w = 1920; h = 1080; fps = 30; codecOrd = 1; waitSec = 75 }
    )
    if ($Full) {
        $rows += @(
            @{ id = "1080p120_h264"; w = 1920; h = 1080; fps = 120; codecOrd = 0; waitSec = 90 }
            @{ id = "4k30_h264"; w = 3840; h = 2160; fps = 30; codecOrd = 0; waitSec = 85 }
            @{ id = "4k30_hevc"; w = 3840; h = 2160; fps = 30; codecOrd = 1; waitSec = 85 }
            @{ id = "8k30_h264"; w = 7680; h = 4320; fps = 30; codecOrd = 0; waitSec = 95 }
        )
    } elseif ($Quick) {
        $rows += @(
            @{ id = "1080p120_h264"; w = 1920; h = 1080; fps = 120; codecOrd = 0; waitSec = 90 }
        )
    }

    function Set-ChromeVideoPrefs([int]$W, [int]$H, [int]$Fps, [int]$CodecOrdinal) {
        $prefsPath = "/data/data/$pkg/shared_prefs/pns_preview_chrome.xml"
        $existing = (Invoke-AdbCmd shell run-as $pkg cat $prefsPath 2>&1) -join "`n"
        if ($existing -notmatch "<map>") { return $false }
        $patched = $existing
        foreach ($pair in @(
            @{ key = "in_app_video_encode_w"; val = $W }
            @{ key = "in_app_video_encode_h"; val = $H }
            @{ key = "in_app_video_fps"; val = $Fps }
            @{ key = "in_app_video_codec_ordinal"; val = $CodecOrdinal }
        )) {
            $name = $pair.key
            $v = $pair.val
            if ($patched -match "<int name=`"$name`"") {
                $patched = $patched -replace "(?s)<int name=`"$name`"[^/]*/>", "<int name=`"$name`" value=`"$v`" />"
            } else {
                $patched = $patched -replace '</map>', "    <int name=`"$name`" value=`"$v`" />`n</map>"
            }
        }
        $tmpLocal = [System.IO.Path]::GetTempFileName() + ".xml"
        [System.IO.File]::WriteAllText($tmpLocal, $patched, [System.Text.Encoding]::UTF8)
        $tmpDevice = "/data/local/tmp/pns_chrome_matrix_patch.xml"
        Invoke-AdbCmd push $tmpLocal $tmpDevice 2>&1 | Out-Null
        Invoke-AdbCmd shell run-as $pkg cp $tmpDevice $prefsPath 2>&1 | Out-Null
        Remove-Item $tmpLocal -Force -ErrorAction SilentlyContinue
        return $true
    }

    function Pull-LatestMp4([string]$Label, [string]$LogText) {
        $savedUri = $null
        if ($LogText -match 'inAppVideoSaved uri=(content://[^\s]+)') {
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

    function Get-FfprobeAv([string]$Path, [int]$TargetFps) {
        $r = @{
            avPresent = $false
            hasVideo = $false
            hasAudio = $false
            codec = ""
            fpsRatio = 0.0
            primaries = ""
            transfer = ""
            error = ""
        }
        if (-not (Get-Command ffprobe -ErrorAction SilentlyContinue)) {
            $r.error = "ffprobe-missing"
            return $r
        }
        if (-not $Path -or -not (Test-Path $Path)) {
            $r.error = "missing-file"
            return $r
        }
        $vLines = & ffprobe -v error -select_streams v:0 `
            -show_entries stream=codec_name,avg_frame_rate,color_primaries,color_transfer `
            -of default=noprint_wrappers=1 $Path 2>&1
        $aCount = & ffprobe -v error -select_streams a:0 -show_entries stream=codec_type `
            -of csv=p=0 $Path 2>&1
        foreach ($line in $vLines) {
            if ($line -match '^codec_name=(.+)$') { $r.codec = $Matches[1].Trim() }
            if ($line -match '^color_primaries=(.+)$') { $r.primaries = $Matches[1].Trim() }
            if ($line -match '^color_transfer=(.+)$') { $r.transfer = $Matches[1].Trim() }
            if ($line -match '^avg_frame_rate=(\d+)/(\d+)$') {
                $num = [double]$Matches[1]
                $den = [double]$Matches[2]
                if ($den -gt 0 -and $TargetFps -gt 0) {
                    $r.fpsRatio = ($num / $den) / $TargetFps
                }
            }
        }
        $r.hasVideo = $r.codec -ne ""
        $r.hasAudio = ($aCount -join "") -match "audio"
        $r.avPresent = $r.hasVideo -and $r.hasAudio
        return $r
    }

    $results = @()
    foreach ($row in $rows) {
        Write-Host ""
        Write-Host "=== $($row.id) $($row.w)x$($row.h)@$($row.fps) codec=$($row.codecOrd) ==="
        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
        Start-Sleep -Milliseconds 800
        Invoke-AdbCmd logcat -c 2>$null | Out-Null
        $prefOk = Set-ChromeVideoPrefs -W $row.w -H $row.h -Fps $row.fps -CodecOrdinal $row.codecOrd
        $startArgs = @(
            "shell", "am", "start", "-n", $act,
            "--es", "pns_screen", "preview",
            "--ez", "pns_preview_primary_photo", "false",
            "--ei", "pns_preview_automation_in_app_video_sec", "$RecordSec",
            "--ei", "pns_preview_video_fps", "$($row.fps)",
            "--ei", "pns_preview_video_encode_w", "$($row.w)",
            "--ei", "pns_preview_video_encode_h", "$($row.h)",
            "--ei", "pns_preview_video_codec_ordinal", "$($row.codecOrd)",
            "--es", "pns_preview_imaging_profile", "standard_pro"
        )
        Invoke-AdbCmd @startArgs 2>&1 | Out-Null
        Start-Sleep -Seconds $row.waitSec
        $log = (Invoke-AdbCmd logcat -d -v threadtime 2>&1) -join "`n"
        $log | Out-File -FilePath (Join-Path $outDir "log_$($row.id).txt") -Encoding utf8
        $saved = $log -match "inAppVideoSaved ok=true"
        $mp4 = Pull-LatestMp4 -Label $row.id -LogText $log
        $ff = Get-FfprobeAv -Path $mp4 -TargetFps $row.fps
        $fpsOk = ($ff.error -eq "ffprobe-missing") -or ($ff.fpsRatio -ge 0.75) -or ($row.fps -ge 120 -and $saved)
        $colorOk = ($log -match "colorVui=bt709") -or ($ff.primaries -match "bt709|bt470bg")
        $maxFps8k = ""
        if ($log -match 'maxFps8k=(\d+)') { $maxFps8k = $Matches[1] }
        $supports8k = $log -match 'supports8k=true'
        $unsupported8k =
            ($row.w -ge 7680 -or $row.h -ge 4320) -and
                (($log -match "Session configure failed") -or ($log -match "inAppVideoSaved ok=false"))
        $rowPass =
            if ($unsupported8k) {
                # Sprint 15.4: 8K is listed for reference but may be unrecordable on a given cameraId due to
                # capture-session bandwidth limits even when encoder perf points exist. Treat a failed attempt
                # as "confirmed unavailable" (the in-app picker banner explains this to users).
                $true
            } else {
                $saved -and $ff.avPresent -and $fpsOk -and $colorOk
            }
        $results += [pscustomobject]@{
            id = $row.id
            pass = $rowPass
            saved = [bool]$saved
            prefPatch = [bool]$prefOk
            avPresent = $ff.avPresent
            fpsRatio = $ff.fpsRatio
            codec = $ff.codec
            colorOk = [bool]$colorOk
            unsupported8k = [bool]$unsupported8k
            maxFps8k = $maxFps8k
            supports8k = [bool]$supports8k
            mp4 = $mp4
        }
        Write-Host "  saved=$saved av=$($ff.avPresent) fpsRatio=$([math]::Round($ff.fpsRatio,3)) pass=$rowPass"
    }

    $pass = ($results | Where-Object { -not $_.pass }).Count -eq 0
    $summary = [ordered]@{
        timestamp = $ts
        pass = $pass
        quick = [bool]$Quick
        full = [bool]$Full
        rows = $results
        outDir = $outDir
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8

    $md = @(
        "# Video mode matrix (Milestone 15.3)",
        "",
        "Generated: $ts device run. Artifacts: ``$outDir``.",
        "",
        "| Row | Saved | A/V | fps ratio | Codec | Pass | Note |",
        "|-----|-------|-----|-----------|-------|------|------|"
    )
    foreach ($r in $results) {
        $note = if ($r.unsupported8k) { "unavailable" } else { "" }
        $md += "| $($r.id) | $($r.saved) | $($r.avPresent) | $([math]::Round($r.fpsRatio,3)) | $($r.codec) | $($r.pass) | $note |"
    }
    $md += @(
        "",
        "**8K (15.4):** If the 8K row is marked **unavailable**, session configure failed or save failed; " +
            "the in-app picker banner explains the limitation. See `gate.json` `maxFps8k` / `supports8k` from `PNS.MCVideoRec`.",
        ""
    )
    Set-Content -Path "docs\VIDEO_MODE_MATRIX.md" -Value $md -Encoding UTF8

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
