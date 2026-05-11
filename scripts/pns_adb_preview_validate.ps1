<#
.SYNOPSIS
  Automated BUILD_PLAN §4 preview validation on a USB device (ADB).

.DESCRIPTION
  Installs app-debug.apk, grants camera permission, runs scripted PreviewEngineScreen
  launches via intent extras (see EXTRA_PNS_PREVIEW_* in CameraCapabilitiesProbe.kt),
  captures logcat, and writes artifacts under -OutDir (including
  logcat_*_app_pid.txt lines filtered by pidof when a PID was resolved).
  When not using -ChromeUxPack, waits briefly after scenarios then writes DCIM ls,
  a bounded MediaStore content query tail, and mediastore_probe.json (Sprint 7.3).

  Prerequisites: USB debugging, authorized device, optional adb root for grants.

.PARAMETER Serial
  adb serial (-s). Omit for single-device default.

.PARAMETER SkipInstall
  Skip adb install (use when APK already on device).

.PARAMETER OutDir
  Output folder for logs (default .\hfr-runs\adb_preview_validate_<utc>)

.PARAMETER SuperMacroOnly
  Run only scenario sprint53_super_macro_vv (BUILD_PLAN Sprint 5.3 MIXED gate). Uses -UltraWideCameraId.

.PARAMETER UltraWideCameraId
  Camera2 id for ultra-wide when probing vendor macro (default 3 = OnePlus 13 dodge).

.PARAMETER RequireSuperMacroPass
  Exit non-zero if super_macro_gate.json would record pass=false (no log line with
  vendorKeyApplied=true). Use in automation when a device is attached.

.PARAMETER Milestone6Pack
  Run only BUILD_PLAN Milestone 6 scenarios (DNG 50708 stamp, LUT FPS probe, Calibrate +
  GLES preview smoke). Implies short run  -  skips Sprint 5.2/5.3/4 highlight/BKT suite.

.PARAMETER ChromeUxPack
  Run only BUILD_PLAN Milestone 9 ChromeUx intent scenarios (`pns_preview_self_timer_sec`, …).
  Writes chrome_ux_smoke.json (device-only). Mutually exclusive with -Milestone6Pack and -SuperMacroOnly.

.EXAMPLE
  .\scripts\pns_adb_preview_validate.ps1
  .\scripts\pns_adb_preview_validate.ps1 -Serial 8bf09993
  .\scripts\pns_adb_preview_validate.ps1 -SuperMacroOnly -RequireSuperMacroPass
  .\scripts\pns_adb_preview_validate.ps1 -Milestone6Pack
  .\scripts\pns_adb_preview_validate.ps1 -ChromeUxPack
#>
param(
    [string]$Serial = "",
    [switch]$SkipInstall,
    [string]$OutDir = "",
    [switch]$SuperMacroOnly,
    [string]$UltraWideCameraId = "3",
    [switch]$RequireSuperMacroPass,
    [switch]$Milestone6Pack,
    [switch]$ChromeUxPack
)

$ErrorActionPreference = "Stop"

if (($ChromeUxPack -and $Milestone6Pack) -or ($ChromeUxPack -and $SuperMacroOnly)) {
    throw "ChromeUxPack is mutually exclusive with Milestone6Pack and SuperMacroOnly."
}

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path -LiteralPath $apk)) {
    throw "Missing APK: $apk - run .\gradlew.bat :app:assembleDebug first."
}

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\adb_preview_validate_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs
    }
    else {
        & adb @CmdArgs
    }
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE"
    }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs 2>$null
    }
    else {
        & adb @CmdArgs 2>$null
    }
}

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($k -eq "PNS_ADB_SERIAL") {
            return $v
        }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[adb_preview_validate] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

if ($Serial -match '^\d+\.\d+\.\d+\.\d+:\d+$') {
    Write-Host "`[adb_preview_validate] adb connect $Serial (TCP/IP)"
    Invoke-AdbIgnore @("connect", $Serial)
}

Write-Host "`[adb_preview_validate] devices:"
Invoke-Adb @("devices", "-l")

$pkg = "dev.pointandshoot"

if (-not $SkipInstall) {
    Write-Host "`[adb_preview_validate] install $apk"
    Invoke-Adb @("install", "-r", $apk)
}

Write-Host "`[adb_preview_validate] pm grant CAMERA"
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")

# Best-effort storage (Android 13+ / legacy)
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_IMAGES")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.READ_MEDIA_VIDEO")
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.POST_NOTIFICATIONS")

# Best-effort: enlarge device log ring so PNS lines survive HAL spam (may be ignored without privileges).
Write-Host "`[adb_preview_validate] best-effort: logcat ring size"
Invoke-AdbIgnore @("shell", "logcat", "-G", "32M")

function Write-ScenarioLogcat([string]$OutPath) {
    # Full-system `logcat -t N` often drops app lines on noisy devices (camera HAL spam fills the ring buffer).
    # Prefer our package PID so PNS.AdbValidation / capture lines survive long waits.
    # Always append a ring-buffer tail (`-t N`) after the pid dump: pid-only buffers can be thin or miss tags
    # that landed in the mixed ring (BUILD_PLAN §4 / Phase 1 V&V grep discipline).
    # USB flakes can make adb exit non-zero during pidof/logcat  -  do not abort the whole script ($ErrorActionPreference = Stop).
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"
    # Noisy camera HALs can exhaust 100k lines in <3 min; tag-filtered supplement below is the reliable hook.
    $fallbackTail = 250000
    $chosenPid = $null
    try {
        $pidLine = if ($Serial) {
            (& adb -s $Serial shell pidof $pkg 2>&1 | Out-String)
        } else {
            (& adb shell pidof $pkg 2>&1 | Out-String)
        }
        $pidTokens = @( ($pidLine.Trim() -split '\s+') | Where-Object { $_ -match '^\d+$' } )
        # Some builds return multiple PIDs (main + helpers); keep the numerically largest  -  usually the active cold-start PID.
        foreach ($t in $pidTokens) {
            if ($null -eq $chosenPid -or [int64]$t -gt [int64]$chosenPid) { $chosenPid = $t }
        }
        $pidBlock = New-Object System.Collections.Generic.List[string]
        if ($chosenPid -match '^\d+$') {
            Write-Host "`[adb_preview_validate] logcat -d --pid=$chosenPid (app process)"
            $pidLines = if ($Serial) {
                @(& adb -s $Serial logcat -d --pid=$chosenPid 2>&1)
            } else {
                @(adb logcat -d --pid=$chosenPid 2>&1)
            }
            foreach ($ln in $pidLines) { [void]$pidBlock.Add($ln) }
            $lc = $pidBlock.Count
            if ($lc -lt 20) {
                Write-Host "`[adb_preview_validate] WARN: pid-scoped dump thin ($lc lines); still appending ring tail"
            }
        }
        else {
            Write-Host "`[adb_preview_validate] WARN: pidof returned empty; ring tail only"
        }
        Write-Host "`[adb_preview_validate] logcat -d -t $fallbackTail (ring supplement)"
        $tailLines = if ($Serial) {
            @(& adb -s $Serial logcat -d -t $fallbackTail 2>&1)
        } else {
            @(adb logcat -d -t $fallbackTail 2>&1)
        }
        $sb = New-Object System.Text.StringBuilder
        if ($pidBlock.Count -gt 0) {
            foreach ($ln in $pidBlock) { [void]$sb.AppendLine($ln) }
            [void]$sb.AppendLine("--- supplement: logcat -d -t $fallbackTail (full ring snapshot) ---")
        }
        foreach ($ln in $tailLines) { [void]$sb.AppendLine($ln) }
        # Tag-filtered dump survives even when the mixed ring drops app lines (filters apply before line cap).
        [void]$sb.AppendLine("--- supplement: tag-filtered PNS.* (bounded tail) ---")
        $tagCmd = "logcat -d -t 80000 *:S PNS.AdbValidation:I PNS.Preview:I PNS.Cam:I PNS.GLES:I PNS.ModeTransition:I PNS.ChromeUx:I"
        $tagLines = if ($Serial) {
            @(& adb -s $Serial shell $tagCmd 2>&1)
        } else {
            @(adb shell $tagCmd 2>&1)
        }
        foreach ($ln in $tagLines) { [void]$sb.AppendLine($ln) }
        [System.IO.File]::WriteAllText($OutPath, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
    }
    finally {
        $ErrorActionPreference = $prevEap
    }
    # Pull app lines by PID substring from the merged buffer for quick review.
    if ($chosenPid -match '^\d+$' -and (Test-Path -LiteralPath $OutPath)) {
        $fragPath = ($OutPath -replace '\.txt$', '_app_pid.txt')
        Get-Content -LiteralPath $OutPath -ErrorAction SilentlyContinue |
            Where-Object { $_ -match " $chosenPid " } |
            Set-Content -LiteralPath $fragPath -Encoding utf8
        Write-Host "`[adb_preview_validate] pid-filtered fragment $fragPath"
    }
}

function Write-MediaStorePnsProbe([string]$OutDir) {
    # BUILD_PLAN Sprint 7.3 / STORAGE_STRATEGY.md  -  DCIM listing + bounded MediaProvider tail (grep Point & Shoot).
    # IMPORTANT: paths contain `&` ("Point & Shoot"). Do **not** pass `ls`, `-la`, and the path as separate `adb shell`
    # argv tokens  -  toybox/sh treats `&` as background unless the path is inside **single quotes** for one shell string.
    $dcimLsPath = Join-Path $OutDir "ls_dcim_point_and_shoot.txt"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "SilentlyContinue"

    function Invoke-AdbShellOneLiner([string]$Cmd) {
        if ($Serial) {
            @(& adb -s $Serial shell $Cmd 2>&1)
        }
        else {
            @(adb shell $Cmd 2>&1)
        }
    }

    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine("=== ls '/sdcard/DCIM/Point & Shoot/' ===")
    [void]$sb.AppendLine((Invoke-AdbShellOneLiner "ls -la '/sdcard/DCIM/Point & Shoot/'") -join "`n")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("=== ls '/sdcard/DCIM/Point & Shoot/Ultra-Max/' ===")
    [void]$sb.AppendLine((Invoke-AdbShellOneLiner "ls -la '/sdcard/DCIM/Point & Shoot/Ultra-Max/'") -join "`n")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("=== find /sdcard/DCIM pns_*.(dng|jxl|avif) maxdepth 4 (first 40) ===")
    # toybox find on device: keep pattern simple (three runs) for OEM compatibility.
    foreach ($pat in @("pns_*.dng", "pns_*.jxl", "pns_*.avif")) {
        $fcmd = "find /sdcard/DCIM -maxdepth 4 -type f -name '$pat' 2>/dev/null | head -n 40"
        [void]$sb.AppendLine("-- pattern: $pat --")
        [void]$sb.AppendLine((Invoke-AdbShellOneLiner $fcmd) -join "`n")
    }

    try {
        Set-Content -LiteralPath $dcimLsPath -Value $sb.ToString() -Encoding utf8
    }
    catch {
        "ls/find aggregate failed: $($_.Exception.Message)" | Set-Content -LiteralPath $dcimLsPath -Encoding utf8
    }

    $tailPath = Join-Path $OutDir "mediastore_images_tail.txt"
    $ErrorActionPreference = "SilentlyContinue"
    try {
        # Tail keeps payload small on busy devices; filter PNS rows in PowerShell below.
        $sh = "content query --uri content://media/external/images/media --projection _id,relative_path,display_name,is_pending 2>/dev/null | tail -n 500"
        $raw = Invoke-AdbShellOneLiner $sh
        $raw | Set-Content -LiteralPath $tailPath -Encoding utf8
    }
    catch {
        "content query failed: $($_.Exception.Message)" | Set-Content -LiteralPath $tailPath -Encoding utf8
    }
    $ErrorActionPreference = $prevEap

    $dcimText = ""
    if (Test-Path -LiteralPath $dcimLsPath) {
        $dcimText = [System.IO.File]::ReadAllText($dcimLsPath)
    }
    # Any deterministic capture filename under DCIM counts (see CaptureStorage.filename).
    $dcimOk = $dcimText -match '(?im)pns_.+\.(dng|jxl|avif)'
    $tailText = ""
    if (Test-Path -LiteralPath $tailPath) {
        $tailText = [System.IO.File]::ReadAllText($tailPath)
    }
    $pnsMediaRows = [regex]::Matches($tailText, "DCIM/Point.{0,3}Shoot", [System.Text.RegularExpressions.RegexOptions]::IgnoreCase).Count
    $pnsDisplayHits = [regex]::Matches($tailText, "(?i)pns_\d{8}T\d{6}Z_", [System.Text.RegularExpressions.RegexOptions]::None).Count

    $probeObj = [ordered]@{
        schema            = "pns.mediastore_probe.v1"
        generatedAtUtc    = [DateTime]::UtcNow.ToString("o")
        outDir            = $OutDir
        dcimLsArtifact    = "ls_dcim_point_and_shoot.txt"
        dcimHasPnsCapture = [bool]$dcimOk
        mediaTailArtifact = "mediastore_images_tail.txt"
        mediaTailPnsRows   = [int]$pnsMediaRows
        mediaTailPnsDisplayNameHits = [int]$pnsDisplayHits
        contentQueryNote  = "ls uses single-quoted paths (ampersand-safe). Tail grep counts rows mentioning DCIM/Point & Shoot; mediaTailPnsDisplayNameHits counts `pns_<UTC>_` display_name prefixes in the tail."
    }
    $probeJson = Join-Path $OutDir "mediastore_probe.json"
    $probeObj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $probeJson -Encoding utf8
    Write-Host "`[adb_preview_validate] mediastore_probe dcimHasPnsCapture=$dcimOk mediaTailPnsRows=$pnsMediaRows displayNameHits=$pnsDisplayHits -> $probeJson"
}

function Run-Scenario([string]$Name, [int]$WaitSec, [string[]]$AmArgs) {
    Write-Host ""
    Write-Host "=== Scenario: $Name (${WaitSec} sec) ==="
    Invoke-Adb @("logcat", "-c")
    Invoke-Adb @("shell", "am", "force-stop", $pkg)
    Start-Sleep -Milliseconds 600
    $amFull = @("shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity") + $AmArgs
    Invoke-Adb $amFull
    Start-Sleep -Seconds $WaitSec
    $logPath = Join-Path $OutDir "logcat_$Name.txt"
    Write-ScenarioLogcat $logPath
    Write-Host "Wrote $logPath"
}

if ($ChromeUxPack) {
    Write-Host "`[adb_preview_validate] ChromeUxPack: BUILD_PLAN Milestone 9 ChromeUx intent scenarios only"
    Run-Scenario "m9_self_timer_adb_seed" 22 @(
        "--es", "pns_screen", "preview",
        "--ei", "pns_preview_self_timer_sec", "3"
    )
}
elseif ($Milestone6Pack) {
    Write-Host "`[adb_preview_validate] Milestone6Pack: BUILD_PLAN M6 automation only"
    # 1) DNG UniqueCameraModel 50708 IFD append + Software LUT line (Ultra-Max RAW12 single still).
    Run-Scenario "m6_raw12_ultra_50708" 75 @(
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_imaging_profile", "ultra_max",
        "--ei", "pns_preview_raw_count", "1",
        "--es", "pns_preview_stills_lut", "PnsCinematic"
    )
    # 2) Baseline vs bundled LUT preview FPS (≤5% budget checked in-app).
    Run-Scenario "m6_lut_fps_probe" 100 @(
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_m6_fps_lut_probe", "true"
    )
    # 2b) Live preview -> TextureView bitmap grab (same path as in-app "Calibrate from preview").
    Run-Scenario "m6_preview_calibrate_grab_smoke" 45 @(
        "--es", "pns_screen", "preview",
        "--ez", "pns_preview_calibrate_grab_smoke", "true"
    )
    # 3) Calibrate flow reachable + GLES LUT test pattern (shader path smoke).
    Run-Scenario "m6_calibrate_smoke" 28 @(
        "--es", "pns_screen", "calibrate"
    )
    Run-Scenario "m6_glpreview_smoke" 35 @(
        "--es", "pns_screen", "glpreview"
    )
}
elseif ($SuperMacroOnly) {
    Write-Host "`[adb_preview_validate] SuperMacroOnly: scenario sprint53_super_macro_vv only (UW camera id=$UltraWideCameraId)"
}
else {
    # 0) Sprint 5.2 V&V  -  structured mode logs + preview restart lines (short settle window).
    Run-Scenario "sprint52_mode_vv" 28 @(
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_imaging_profile", "ultra_max",
        "--es", "pns_preview_dial", "H"
    )
}

if (-not $Milestone6Pack -and -not $ChromeUxPack) {
    # Sprint 5.3  -  ultra-wide + OPLUS macro close-up vendor key on repeating preview (BUILD_PLAN MIXED gate).
    Run-Scenario "sprint53_super_macro_vv" 35 @(
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_camera_id", $UltraWideCameraId,
        "--ez", "pns_preview_super_macro_probe", "true"
    )
}

if (-not $SuperMacroOnly -and -not $Milestone6Pack -and -not $ChromeUxPack) {
    # 1) Highlight metering (dial H, ~119 fps default 60)  -  allow time for YUV histogram + AE comp iterations.
    Run-Scenario "highlight_dial_H" 45 @(
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_dial", "H"
    )

    # 2) Ten sequential RAW stills (ADB validation tag PNS.AdbValidation)
    # Wall budget: camera settle + 10×(capture + IO + inter-shot gap)  -  keep comfortably above ~120s on slow storage.
    Run-Scenario "raw_still_x10" 180 @(
        "--es", "pns_screen", "preview",
        "--ei", "pns_preview_raw_count", "10"
    )

    # 2b) Ultra-Max (RAW12 DNG) single still  -  BUILD_PLAN Milestone 4 Sprint 4.3
    Run-Scenario "raw12_ultra_max_x1" 75 @(
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_imaging_profile", "ultra_max",
        "--ei", "pns_preview_raw_count", "1"
    )

    # 3) BKT 3-shot bracket (dial BKT + bracket pattern)
    Run-Scenario "bracket_bkt3" 45 @(
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_dial", "BKT",
        "--es", "pns_preview_bracket", "3"
    )
}

# DCIM/MediaStore probe (Sprint 7.3) then merge grep summary
if (-not $ChromeUxPack) {
    Write-Host "`[adb_preview_validate] settle before DCIM/MediaStore probe (7s)..."
    Start-Sleep -Seconds 7
    Write-MediaStorePnsProbe $OutDir
}

$summaryPath = Join-Path $OutDir "summary_grep.txt"
$patterns = @(
    "PNS.AdbValidation",
    "PNS.ModeTransition",
    "PNS.Cam",
    "PNS.Preview",
    "HighlightMeter",
    "highlightMeter",
    "eyeAf",
    "tracker lockedIds",
    "YUV highlight ImageReader",
    "CameraDevice",
    "CameraCaptureSession",
    "ERROR",
    "Eviction",
    "superMacroCloseup",
    "50708 IFD append",
    "m6 lutFpsBudget",
    "calibrate screen compose",
    "calibrate preview frame grab ok",
    "glpreview screen compose",
    "preview seeded stillsLut",
    "PNS.ChromeUx",
    "selfTimerSec=",
    "preview adb seed selfTimerDelaySec=",
    "encode_lane_busy"
)
$sb = New-Object System.Text.StringBuilder
foreach ($p in $patterns) {
    [void]$sb.AppendLine("==== $p ====")
    Get-ChildItem -LiteralPath $OutDir -Filter "logcat_*.txt" | ForEach-Object {
        [void]$sb.AppendLine("--- $($_.Name) ---")
        Select-String -LiteralPath $_.FullName -Pattern $p -SimpleMatch -ErrorAction SilentlyContinue |
            ForEach-Object { [void]$sb.AppendLine($_.Line) }
    }
    [void]$sb.AppendLine("")
}
[System.IO.File]::WriteAllText($summaryPath, $sb.ToString(), [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote $summaryPath"

# Sprint 5.3 MIXED  -  machine-readable gate (vendor close-up key accepted on repeating preview).
$macroLog = Join-Path $OutDir "logcat_sprint53_super_macro_vv.txt"
$macroPass = $false
$macroLine = $null
if (-not $Milestone6Pack -and (Test-Path -LiteralPath $macroLog)) {
    $hits = Select-String -LiteralPath $macroLog -Pattern "superMacroCloseup probe" -SimpleMatch -ErrorAction SilentlyContinue
    foreach ($h in $hits) {
        $ln = $h.Line
        if ($ln -match "vendorKeyApplied=true") {
            $macroPass = $true
            $macroLine = $ln.Trim()
            break
        }
    }
}
$gateObj = [ordered]@{
    schema           = "pns.super_macro_gate.v1"
    scenario         = "sprint53_super_macro_vv"
    pass             = $macroPass
    matchedLine      = $macroLine
    ultraWideCameraId = $UltraWideCameraId
    logArtifact      = "logcat_sprint53_super_macro_vv.txt"
    outDir           = $OutDir
    generatedAtUtc   = [DateTime]::UtcNow.ToString("o")
}
$gateJson = Join-Path $OutDir "super_macro_gate.json"
$gateTxt = Join-Path $OutDir "super_macro_gate.txt"
$gateObj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $gateJson -Encoding utf8
$gateTxtLines = New-Object System.Collections.Generic.List[string]
[void]$gateTxtLines.Add("Sprint 5.3 Super Macro gate (automated)")
[void]$gateTxtLines.Add("pass=$macroPass")
[void]$gateTxtLines.Add("ultraWideCameraId=$UltraWideCameraId")
if ($macroLine) {
    [void]$gateTxtLines.Add("matchedLine=$macroLine")
}
else {
    [void]$gateTxtLines.Add("matchedLine=(none)")
}
[void]$gateTxtLines.Add("see logcat_sprint53_super_macro_vv.txt and super_macro_gate.json")
($gateTxtLines -join "`n") | Set-Content -LiteralPath $gateTxt -Encoding utf8
Write-Host "`[adb_preview_validate] super_macro_gate pass=$macroPass -> $gateJson"

if ($RequireSuperMacroPass -and -not $Milestone6Pack -and -not $ChromeUxPack -and -not $macroPass) {
    throw "Super Macro gate failed: expected PNS.AdbValidation line containing 'superMacroCloseup probe' and 'vendorKeyApplied=true' in $macroLog"
}

if ($Milestone6Pack) {
    function Test-M6LogPattern([string]$pattern) {
        # foreach  -  `return` inside ForEach-Object only exits the scriptblock and can yield boolean arrays.
        $files = @( Get-ChildItem -LiteralPath $OutDir -Filter "logcat_m6_*.txt" -ErrorAction SilentlyContinue )
        foreach ($f in $files) {
            if (Select-String -LiteralPath $f.FullName -Pattern $pattern -SimpleMatch -Quiet -ErrorAction SilentlyContinue) {
                return $true
            }
        }
        return $false
    }
    $m6Obj = [ordered]@{
        schema          = "pns.milestone6_gate.v1"
        generatedAtUtc  = [DateTime]::UtcNow.ToString("o")
        outDir          = $OutDir
        dng50708IfdOk   = (Test-M6LogPattern "50708 IFD append")
        lutFpsBudgetOk  = (Test-M6LogPattern "m6 lutFpsBudget ok=true")
        calibrateSmoke  = (Test-M6LogPattern "calibrate screen compose active")
        calibrateLiveGrabOk = (Test-M6LogPattern "calibrate preview frame grab ok")
        glPreviewSmoke  = (Test-M6LogPattern "glpreview screen compose active")
        stillsLutSeed   = (Test-M6LogPattern "preview seeded stillsLut=PnsCinematic")
    }
    $m6Obj.pass = (
        $m6Obj.dng50708IfdOk -and $m6Obj.lutFpsBudgetOk -and $m6Obj.calibrateSmoke -and $m6Obj.calibrateLiveGrabOk -and $m6Obj.glPreviewSmoke
    )
    $m6Json = Join-Path $OutDir "milestone6_gate.json"
    $m6Obj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $m6Json -Encoding utf8
    Write-Host "`[adb_preview_validate] milestone6_gate pass=$($m6Obj.pass) -> $m6Json"
    if (-not $m6Obj.pass) {
        throw "Milestone 6 gate failed (see logcat_m6_*.txt and $m6Json under $OutDir)"
    }
}

if ($ChromeUxPack) {
    $m9Log = Join-Path $OutDir "logcat_m9_self_timer_adb_seed.txt"
    $m9Text = ""
    if (Test-Path -LiteralPath $m9Log) {
        $m9Text = [System.IO.File]::ReadAllText($m9Log)
    }
    $selfTimerUxOk = $m9Text -match 'PNS\.ChromeUx.*selfTimerSec=3'
    $adbSeedOk = $m9Text -match 'preview adb seed selfTimerDelaySec=3'
    $cxObj = [ordered]@{
        schema             = "pns.chrome_ux_smoke.v1"
        scenario           = "m9_self_timer_adb_seed"
        pass               = ($selfTimerUxOk -and $adbSeedOk)
        selfTimerChromeUxOk = $selfTimerUxOk
        adbSelfTimerSeedOk = $adbSeedOk
        logArtifact        = "logcat_m9_self_timer_adb_seed.txt"
        outDir             = $OutDir
        generatedAtUtc     = [DateTime]::UtcNow.ToString("o")
    }
    $cxJson = Join-Path $OutDir "chrome_ux_smoke.json"
    $cxObj | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $cxJson -Encoding utf8
    Write-Host "`[adb_preview_validate] chrome_ux_smoke pass=$($cxObj.pass) -> $cxJson"
    if (-not $cxObj.pass) {
        throw "ChromeUxPack smoke failed: expected PNS.ChromeUx selfTimerSec=3 and AdbValidation preview adb seed selfTimerDelaySec=3 in $m9Log"
    }
}

# List Pictures (avoid `&` in folder name breaking adb/sh tokenization on Windows).
Invoke-AdbIgnore @("shell", "ls", "-la", "/sdcard/Pictures/") |
    Set-Content -LiteralPath (Join-Path $OutDir "ls_pictures_parent.txt") -Encoding utf8

Write-Host ""
Write-Host "`[adb_preview_validate] DONE OutDir=$OutDir"
