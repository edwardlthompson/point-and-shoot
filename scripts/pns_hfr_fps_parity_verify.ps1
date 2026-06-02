param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [ValidateSet(24, 30, 60, 120, 240)]
    [int]$TargetFps = 60,
    [switch]$AllFps,
    [string]$MatrixJsonPath = "",
    [switch]$SkipInstall,
    [switch]$SkipAssemble
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
$fpsTargets = if ($AllFps) { @(24, 30, 60, 120, 240) } else { @($TargetFps) }

if (-not $OutDir) {
    $suffix = if ($AllFps) { "all" } else { "$TargetFps" }
    $OutDir = Join-Path $repoRoot "hfr-runs\hfr_fps_parity_$(Get-Date -Format yyyyMMdd_HHmmss)_$suffix"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing $apk" }

function Read-PnsSerial([string]$Value) {
    if ($Value) { return $Value }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return "" }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim().Trim('"') }
    }
    return ""
}

$Serial = Read-PnsSerial $Serial

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk) | Out-Null
}
try { Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.CAMERA") | Out-Null } catch { }
try { Invoke-Adb @("shell", "pm", "grant", $pkg, "android.permission.RECORD_AUDIO") | Out-Null } catch { }

function Get-MatrixObject {
    if ($MatrixJsonPath -and (Test-Path -LiteralPath $MatrixJsonPath)) {
        try { return (Get-Content -LiteralPath $MatrixJsonPath -Raw | ConvertFrom-Json) } catch { }
    }
    try {
        $raw = (Invoke-Adb @("exec-out", "run-as", $pkg, "cat", "files/fleet_device_matrix.json")) -join "`n"
        if ($raw -and $raw.Trim().StartsWith("{")) { return ($raw | ConvertFrom-Json) }
    } catch { }
    return $null
}

function Get-MaxHfrFpsAt1080($MatrixObj) {
    if (-not $MatrixObj -or -not $MatrixObj.cameras) { return $null }
    $max = $null
    foreach ($cam in @($MatrixObj.cameras)) {
        if ($null -eq $cam) { continue }
        $v = $null
        if ($cam.PSObject.Properties.Name -contains "hfrMaxFpsAt1080") { $v = $cam.hfrMaxFpsAt1080 }
        if ($v -is [int]) {
            if ($null -eq $max -or $v -gt $max) { $max = $v }
        }
    }
    return $max
}

function Invoke-RegularFpsProbe([int]$Fps, [string]$CaseOutDir) {
    $recordSec = 6
    $waitSec = 70
    Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
    Invoke-Adb @("logcat", "-c") | Out-Null
    Invoke-Adb @(
        "shell", "am", "start", "-W", "-n", "$pkg/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_primary_photo", "false",
        "--ei", "pns_preview_automation_in_app_video_sec", "$recordSec",
        "--ei", "pns_preview_video_fps", "$Fps",
        "--ei", "pns_preview_video_encode_w", "1920",
        "--ei", "pns_preview_video_encode_h", "1080",
        "--es", "pns_preview_imaging_profile", "standard_pro"
    ) | Out-Null
    Start-Sleep -Seconds $waitSec

    $logPath = Join-Path $CaseOutDir "logcat.txt"
    $logLines = Invoke-Adb @("exec-out", "logcat", "-d", "-s", "PNS.AdbValidation:I", "PNS.MCVideoRec:I", "PNS.VideoRec:I")
    Set-Content -LiteralPath $logPath -Encoding utf8 -Value (($logLines | ForEach-Object { "$_" }) -join [Environment]::NewLine)
    $logText = Get-Content -LiteralPath $logPath -Raw
    $saved = $logText -match "inAppVideoSaved ok=true"
    $fpsLine = $logText -match "fps=$Fps"
    $fpsOk = [bool]$fpsLine
    Invoke-Adb @("shell", "am", "force-stop", $pkg) | Out-Null
    return [ordered]@{
        targetFps = $Fps
        pass = ([bool]$saved -and [bool]$fpsOk)
        delegatedScript = $null
        delegatedCase = $null
        videoSaved = [bool]$saved
        fpsNeedle = [bool]$fpsLine
        actualFps = $null
        skippedReason = $null
        outDir = $CaseOutDir
        timestampUtc = [DateTime]::UtcNow.ToString("o")
    }
}

function Invoke-HsMediaCodecProbe([int]$Fps, [string]$CaseOutDir) {
    $caseMap = @{
        120 = "HFR_1080p_120fps"
        240 = "HFR_1080p_240fps"
    }
    if (-not $caseMap.ContainsKey($Fps)) {
        throw "No MediaCodec HFR case mapping for fps=$Fps"
    }
    $fallbackScript = Join-Path $PSScriptRoot "pns_hfr_video_verify.ps1"
    $fallbackArgs = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", $fallbackScript,
        "-TargetFps", "$Fps",
        "-Fast"
    )
    if ($Serial) { $fallbackArgs += @("-Serial", $Serial) }
    & powershell @fallbackArgs | Out-Null
    $exit = $LASTEXITCODE
    return [ordered]@{
        targetFps = $Fps
        pass = ($exit -eq 0)
        delegatedScript = "pns_hfr_video_verify.ps1"
        delegatedCase = "mapped:$($caseMap[$Fps])"
        videoSaved = $null
        fpsNeedle = $null
        actualFps = $null
        skippedReason = $null
        outDir = $CaseOutDir
        timestampUtc = [DateTime]::UtcNow.ToString("o")
    }
}

$matrixObj = Get-MatrixObject
$matrixMaxFps = Get-MaxHfrFpsAt1080 $matrixObj
$rows = @()
foreach ($fps in $fpsTargets) {
    $caseOutDir = Join-Path $OutDir "$fps"
    New-Item -ItemType Directory -Force -Path $caseOutDir | Out-Null
    if ($fps -ge 120 -and $matrixMaxFps -is [int] -and $matrixMaxFps -lt $fps) {
        $rows += [ordered]@{
            targetFps = $fps
            pass = $true
            delegatedScript = $null
            delegatedCase = $null
            videoSaved = $null
            fpsNeedle = $null
            actualFps = $null
            skippedReason = "matrix_gate:hfrMaxFpsAt1080<$fps"
            outDir = $caseOutDir
            timestampUtc = [DateTime]::UtcNow.ToString("o")
        }
        continue
    }
    if ($fps -ge 120) {
        $rows += Invoke-HsMediaCodecProbe -Fps $fps -CaseOutDir $caseOutDir
    } else {
        $rows += Invoke-RegularFpsProbe -Fps $fps -CaseOutDir $caseOutDir
    }
}

$pass = (@($rows | Where-Object { -not $_.pass -and -not $_.skippedReason }).Count -eq 0)
$result = [ordered]@{
    schema = "pns.hfr_fps_parity_verify.v2"
    allFps = [bool]$AllFps
    targetFps = if ($AllFps) { $null } else { [int]$TargetFps }
    matrixMaxFpsAt1080 = $matrixMaxFps
    pass = $pass
    rows = $rows
    outDir = $OutDir
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}
$result | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $OutDir "gate.json") -Encoding utf8
if (-not $pass) { exit 1 }
exit 0
