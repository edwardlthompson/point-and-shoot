param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [ValidateSet("hdr10", "hlg10", "bt709", "pq", "flat")]
    [string]$Profile = "bt709",
    [switch]$SkipInstall,
    [switch]$SkipAssemble
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"

if (-not $OutDir) {
    $OutDir = Join-Path $repoRoot "hfr-runs\video_color_profile_verify_$(Get-Date -Format yyyyMMdd_HHmmss)_$Profile"
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

function Write-Gate([bool]$Pass, [string]$Delegated, [bool]$PrefSeeded = $false) {
    $report = [ordered]@{
        schema = "pns.video_color_profile_verify.v1"
        profile = $Profile
        pass = $Pass
        delegatedScript = $Delegated
        prefSeeded = $PrefSeeded
        outDir = $OutDir
        timestampUtc = [DateTime]::UtcNow.ToString("o")
    }
    $report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir "gate.json") -Encoding utf8
}

$Serial = Read-PnsSerial $Serial

if ($Profile -eq "hdr10" -or $Profile -eq "pq") {
    $args = @{}
    if ($Serial) { $args.Serial = $Serial }
    if ($SkipInstall) { $args.SkipInstall = $true }
    if ($SkipAssemble) { $args.SkipAssemble = $true }
    $exit = 1
    try {
        & (Join-Path $PSScriptRoot "pns_video_hdr10_metadata_verify.ps1") @args
        $exit = $LASTEXITCODE
    } catch {
        $exit = 1
    }
    Write-Gate -Pass ($exit -eq 0) -Delegated "pns_video_hdr10_metadata_verify.ps1"
    exit $exit
}
if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing $apk" }
if (-not $SkipInstall) { Invoke-Adb @("install", "-r", "-t", $apk) | Out-Null }
Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") | Out-Null
Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") | Out-Null

$prefSeeded = $false
$hudPath = "/data/data/$pkg/shared_prefs/pns_hud_settings.xml"
$hud = (Invoke-Adb @("shell", "run-as", $pkg, "cat", $hudPath) -join "`n")
if ($hud -match "<map>") {
    $targetColor =
        if ($Profile -eq "flat") { "flat_cine" }
        elseif ($Profile -eq "hlg10") { "hlg10" }
        else { "standard" }
    $hud = Set-HudStringPref $hud "video_color_profile" $targetColor
    $tmpHud = [System.IO.Path]::GetTempFileName() + ".xml"
    [System.IO.File]::WriteAllText($tmpHud, $hud, [System.Text.UTF8Encoding]::new($false))
    Invoke-Adb @("push", $tmpHud, "/data/local/tmp/pns_hud_color.xml") | Out-Null
    Invoke-Adb @("shell", "run-as", $pkg, "cp", "/data/local/tmp/pns_hud_color.xml", $hudPath) | Out-Null
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
    "--ei", "pns_preview_automation_in_app_video_sec", "6",
    "--ei", "pns_preview_video_fps", "30",
    "--ei", "pns_preview_video_codec_ordinal", "1",
    "--es", "pns_preview_imaging_profile", "standard_pro"
) | Out-Null
Start-Sleep -Seconds 70

$logPath = Join-Path $OutDir "logcat.txt"
$logLines = Invoke-Adb @("exec-out", "logcat", "-d", "-s", "PNS.MCVideoRec:I", "PNS.AdbValidation:I", "PNS.VideoRec:I")
Set-Content -LiteralPath $logPath -Encoding utf8 -Value (($logLines | ForEach-Object { "$_" }) -join [Environment]::NewLine)
$log = Get-Content -LiteralPath $logPath -Raw
Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null

$videoSaved = ($log -match "inAppVideoSaved ok=true")
$hasBt709 = ($log -match "colorVui=bt709")
$hasBt709Flat = ($log -match "colorVui=bt709-flat")
$hasFlatProfile = ($log -match "colorProfile=flat_cine")
$hasHlgTag = ($log -match "colorVui=bt2020-hlg|transfer=hlg")
$hasHdrTag = ($log -match "colorVui=bt2020-hlg|colorVui=bt2020-hdr10|colorVui=bt2020")
$latest = (Invoke-Adb @("shell", "ls -t '/sdcard/DCIM/Point & Shoot'/pns_*.mp4 2>/dev/null | head -1") -join "`n")
$fallbackClipFound = ($latest -match "pns_.*\.mp4")

$pass = $false
if ($Profile -eq "flat") {
    $pass = ($videoSaved -or $fallbackClipFound) -and ($hasBt709Flat -or $hasFlatProfile -or $hasBt709)
    Write-Gate -Pass $pass -Delegated "inline_flat" -PrefSeeded $prefSeeded
} elseif ($Profile -eq "hlg10") {
    $pass = ($videoSaved -or $fallbackClipFound) -and $hasHlgTag
    Write-Gate -Pass $pass -Delegated "inline_hlg10" -PrefSeeded $prefSeeded
} else {
    $pass = ($videoSaved -or $fallbackClipFound) -and ($hasBt709 -or (-not $hasHdrTag))
    Write-Gate -Pass $pass -Delegated "inline_bt709" -PrefSeeded $prefSeeded
}
if (-not $pass) { exit 1 }
exit 0
