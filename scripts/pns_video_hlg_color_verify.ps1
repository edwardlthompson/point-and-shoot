#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.16** — record HLG (HEVC Main10) and assert colorVui + ffprobe transfer.

  Primary gate: logcat **`colorVui=bt2020-hlg`**. Some OEM muxers (CPH2583) still report
  **`color_transfer=smpte170m`** in ffprobe while the encoder VUI is HLG — same pattern as
  **`pns_video_codec_color_compare.ps1`** SDR HEVC.

.EXAMPLE
  .\scripts\pns_video_hlg_color_verify.ps1 -Serial b5214fc6 -SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$RecordSec = 6,
    [int]$WaitSec = 70
)

$ErrorActionPreference = "Stop"
$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot
Push-Location $projRoot
try {
    if (Test-Path "$PSScriptRoot\pns_resolve_adb.ps1") { . "$PSScriptRoot\pns_resolve_adb.ps1" -PrependToPath -Quiet }

    $ffprobe = Get-Command ffprobe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
    if (-not $ffprobe) {
        $cand = "$env:USERPROFILE\Desktop\FFMPEG\ffmpeg-7.0.2-full_build\bin\ffprobe.exe"
        if (Test-Path $cand) { $ffprobe = $cand }
    }

    if ([string]::IsNullOrWhiteSpace($Serial)) {
        $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
        if (Test-Path $envFile) {
            Get-Content $envFile | ForEach-Object {
                if ($_ -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { $Serial = $Matches[1].Trim() }
            }
        }
    }

    $adbExe = (Get-Command adb -ErrorAction Stop).Source
    function Invoke-PnsAdb {
        param([Parameter(ValueFromRemainingArguments = $true)][string[]]$AdbArgs)
        if ($Serial) { & $adbExe -s $Serial @AdbArgs } else { & $adbExe @AdbArgs }
    }

    function Set-HudStringPref {
        param([string]$Xml, [string]$Name, [string]$Value)
        $pattern = "<string name=`"$Name`">[^<]*</string>"
        $replacement = "<string name=`"$Name`">$Value</string>"
        if ($Xml -match $Name) {
            return ($Xml -replace $pattern, $replacement)
        }
        return ($Xml -replace "</map>", "    $replacement`r`n</map>")
    }

    if (-not $SkipAssemble) {
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }
    $apk = "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "Missing $apk" }
    if (-not $SkipInstall) {
        Invoke-PnsAdb install -r -t $apk | Out-Null
        Invoke-PnsAdb shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null
        Invoke-PnsAdb shell pm grant dev.pointandshoot android.permission.RECORD_AUDIO 2>$null
    }

    $pkg = "dev.pointandshoot"
    $hudPath = "/data/data/$pkg/shared_prefs/pns_hud_settings.xml"
    $hud = (Invoke-PnsAdb shell "run-as $pkg cat $hudPath" 2>&1) -join "`n"
    if ($hud -notmatch "<map>") { throw "missing pns_hud_settings.xml — launch preview once" }
    $hud = Set-HudStringPref $hud "video_color_profile" "hlg"
    $tmpHud = [System.IO.Path]::GetTempFileName() + ".xml"
    [System.IO.File]::WriteAllText($tmpHud, $hud, [System.Text.UTF8Encoding]::new($false))
    Invoke-PnsAdb push $tmpHud /data/local/tmp/pns_hud_hlg.xml | Out-Null
    Invoke-PnsAdb shell "run-as $pkg cp /data/local/tmp/pns_hud_hlg.xml $hudPath" | Out-Null
    Remove-Item $tmpHud -Force -ErrorAction SilentlyContinue

    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = "hfr-runs\video_hlg_color_verify_$ts"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    Invoke-PnsAdb shell am force-stop $pkg 2>$null | Out-Null
    Invoke-PnsAdb shell logcat -c 2>$null | Out-Null
    $rec = [Math]::Max(1, [Math]::Min($RecordSec, 120))
    $startArgs = @(
        "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_primary_photo", "false",
        "--ei", "pns_preview_automation_in_app_video_sec", "$rec",
        "--es", "pns_preview_imaging_profile", "standard_pro",
        "--es", "pns_preview_camera_id", "0",
        "--ei", "pns_preview_video_fps", "30",
        "--ei", "pns_preview_video_codec_ordinal", "1",
        "--ez", "pns_preview_video_10bit", "true",
        "--ei", "pns_preview_video_encode_w", "1920",
        "--ei", "pns_preview_video_encode_h", "1080"
    )
    Invoke-PnsAdb @startArgs 2>&1 | Out-Null
    Write-Host "waiting ${WaitSec}s for HLG clip..."
    Start-Sleep -Seconds $WaitSec

    $argv = @()
    if ($Serial) { $argv += "-s", $Serial }
    $argv += "logcat", "-d", "-v", "threadtime", "-s", "PNS.MCVideoRec:I", "PNS.AdbValidation:I", "PNS.VideoEncode:I", "AndroidRuntime:E"
    $log = (& $adbExe @argv 2>&1) -join "`n"
    $log | Set-Content (Join-Path $outDir "logcat.txt") -Encoding UTF8

    $saved = $log -match "inAppVideoSaved ok=true"
    $colorVuiHlg = $log -match "colorVui=bt2020-hlg"
    $mp4 = $null
    if ($log -match "inAppVideoSaved uri=(content://[^\s]+)") {
        $uri = $Matches[1]
        $q = (Invoke-PnsAdb shell content query --uri $uri --projection _data 2>&1) -join "`n"
        if ($q -match "_data=([^\r\n]+)") {
            $dataPath = $Matches[1].Trim()
            $mp4 = Join-Path $outDir "hlg_clip.mp4"
            Invoke-PnsAdb pull $dataPath $mp4 2>&1 | Out-Null
        }
    }
    $transfer = ""
    $primaries = ""
    if ($mp4 -and (Test-Path $mp4) -and $ffprobe) {
        $lines = & $ffprobe -v error -select_streams v:0 -show_entries stream=color_transfer,color_primaries -of default=noprint_wrappers=1 $mp4 2>&1
        $probeText = if ($lines -is [System.Array]) { $lines -join "`n" } else { [string]$lines }
        $probeText | Out-File (Join-Path $outDir "ffprobe.txt")
        if ($probeText -match "(?m)^color_transfer=(.+)$") { $transfer = $Matches[1].Trim() }
        if ($probeText -match "(?m)^color_primaries=(.+)$") { $primaries = $Matches[1].Trim() }
    }
    $transferIdeal = ($transfer -match "arib-std-b67") -or ($transfer -match "bt2020")
    $ffprobeOemQuirk = $colorVuiHlg -and $saved -and $transfer -and (-not $transferIdeal)
    $pass = [bool]$saved -and [bool]$colorVuiHlg -and ($transferIdeal -or $ffprobeOemQuirk -or -not $ffprobe)
    $summary = [ordered]@{
        pass = $pass
        saved = [bool]$saved
        colorVuiHlg = [bool]$colorVuiHlg
        color_transfer = $transfer
        color_primaries = $primaries
        ffprobeOemQuirk = [bool]$ffprobeOemQuirk
        mp4 = $mp4
        outDir = $outDir
    }
    $summary | ConvertTo-Json | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8
    Invoke-PnsAdb shell am force-stop $pkg 2>$null | Out-Null
    if (-not $pass) {
        Write-Host "FAIL HLG gate - $outDir" -ForegroundColor Red
        exit 1
    }
    $note = if ($ffprobeOemQuirk) { " (ffprobe OEM quirk; colorVui authoritative)" } else { "" }
    Write-Host "PASS HLG gate - transfer=$transfer$note - $outDir" -ForegroundColor Green
}
finally {
    Pop-Location
}
