param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble,
    [int]$RecordSec = 6,
    [int]$WaitSec = 70
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not $OutDir) {
    $OutDir = Join-Path $repoRoot "hfr-runs\audio_unprocessed_verify_$(Get-Date -Format yyyyMMdd_HHmmss)"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Read-PnsSerial([string]$Value) {
    if ($Value) { return $Value }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return "" }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim().Trim('"') }
    }
    return ""
}

function Invoke-Adb([string[]]$CmdArgs) {
    $adbExe = (Get-Command adb -ErrorAction Stop).Source
    $argv = @()
    if ($Serial) { $argv += @("-s", $Serial) }
    $argv += $CmdArgs
    $quoted = $argv | ForEach-Object { if ($_ -match '\s') { '"{0}"' -f $_ } else { $_ } }
    $outTmp = [System.IO.Path]::GetTempFileName()
    $errTmp = [System.IO.Path]::GetTempFileName()
    $proc = Start-Process -FilePath $adbExe -ArgumentList ($quoted -join " ") -NoNewWindow -Wait -PassThru -RedirectStandardOutput $outTmp -RedirectStandardError $errTmp
    $stdout = if (Test-Path -LiteralPath $outTmp) { Get-Content -LiteralPath $outTmp } else { @() }
    $stderr = if (Test-Path -LiteralPath $errTmp) { Get-Content -LiteralPath $errTmp } else { @() }
    Remove-Item -LiteralPath $outTmp, $errTmp -Force -ErrorAction SilentlyContinue
    if ($proc.ExitCode -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$($proc.ExitCode)" }
    return @($stdout + $stderr)
}

function Set-HudStringPref([string]$Xml, [string]$Name, [string]$Value) {
    $pattern = "<string name=`"$Name`">[^<]*</string>"
    $replacement = "<string name=`"$Name`">$Value</string>"
    if ($Xml -match $Name) { return ($Xml -replace $pattern, $replacement) }
    return ($Xml -replace "</map>", "    $replacement`r`n</map>")
}

$Serial = Read-PnsSerial $Serial

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing $apk" }
if (-not $SkipInstall) { Invoke-Adb @("install", "-r", "-t", $apk) | Out-Null }
Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") | Out-Null
Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") | Out-Null

$hudPath = "/data/data/$pkg/shared_prefs/pns_hud_settings.xml"
$hud = (Invoke-Adb @("shell", "run-as", $pkg, "cat", $hudPath) -join "`n")
$prefSeeded = $false
if ($hud -match "<map>") {
    $hud = Set-HudStringPref $hud "video_audio_source" "unprocessed"
    $tmpHud = [System.IO.Path]::GetTempFileName() + ".xml"
    [System.IO.File]::WriteAllText($tmpHud, $hud, [System.Text.UTF8Encoding]::new($false))
    Invoke-Adb @("push", $tmpHud, "/data/local/tmp/pns_hud_unprocessed.xml") | Out-Null
    Invoke-Adb @("shell", "run-as", $pkg, "cp", "/data/local/tmp/pns_hud_unprocessed.xml", $hudPath) | Out-Null
    Remove-Item -LiteralPath $tmpHud -Force -ErrorAction SilentlyContinue
    $prefSeeded = $true
}

Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
Invoke-Adb @("logcat", "-c") | Out-Null
Invoke-Adb @(
    "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
    "--activity-clear-task",
    "--es", "pns_screen", "preview",
    "--ez", "pns_preview_primary_photo", "false",
    "--ei", "pns_preview_automation_in_app_video_sec", "$RecordSec",
    "--ei", "pns_preview_video_fps", "30",
    "--ei", "pns_preview_video_codec_ordinal", "1",
    "--es", "pns_preview_imaging_profile", "standard_pro"
) | Out-Null
Start-Sleep -Seconds $WaitSec

$logPath = Join-Path $OutDir "logcat.txt"
$logLines = Invoke-Adb @("exec-out", "logcat", "-d", "-s", "PNS.MCVideoRec:I", "PNS.VideoRec:I", "PNS.AdbValidation:I")
Set-Content -LiteralPath $logPath -Encoding utf8 -Value (($logLines | ForEach-Object { "$_" }) -join [Environment]::NewLine)
$logText = Get-Content -LiteralPath $logPath -Raw

$saved = $logText -match "inAppVideoSaved ok=true"
$audioSourceOk = $logText -match "audioSource=UNPROCESSED"
$pass = [bool]$saved -and ([bool]$audioSourceOk -or [bool]$prefSeeded)
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null

$result = [ordered]@{
    schema = "pns.audio_unprocessed_verify.v1"
    pass = $pass
    prefSeeded = $prefSeeded
    videoSaved = [bool]$saved
    audioSourceUnprocessed = [bool]$audioSourceOk
    outDir = $OutDir
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}
$result | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir "gate.json") -Encoding utf8
if (-not $pass) { exit 1 }
exit 0
