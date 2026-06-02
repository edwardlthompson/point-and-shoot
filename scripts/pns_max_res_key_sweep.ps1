<#
.SYNOPSIS
  Root-gated max-resolution vendor key sweep on cameraId=2.

.DESCRIPTION
  1) Pulls `dumpsys media.camera` and extracts candidate request/session vendor keys.
  2) Runs one scripted RAW still per candidate on camera 2 (experimental lane enabled).
  3) Auto-ranks candidates by:
       - present
       - settable
       - no rejection

  Optional profile:
    -Profile QualcommQCFA
      Runs a cross-device-derived matrix with CamX override variants inspired by
      Xiaomi/OP9/XDA QCFA unlock routes, then executes the same request/session
      sweep for each variant.

  Artifacts:
    hfr-runs/max_res_key_sweep_<timestamp>/
      - dumpsys_media_camera.txt
      - candidates_*.txt
      - per_candidate_logs/<scope>_<index>_logcat.txt
      - max_res_key_sweep_summary.json
      - max_res_key_sweep_summary.md
#>
param(
    [string]$Serial = "",
    [ValidateSet("Default", "QualcommQCFA")]
    [string]$Profile = "Default",
    [string]$CamxTargetPathOverride = "",
    [int]$WaitSec = 55,
    [int]$MaxSessionCandidates = 24,
    [int]$MaxRequestCandidates = 24,
    [switch]$SkipAssemble,
    [switch]$SkipInstall
)

$ErrorActionPreference = "Stop"
$resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolve) { . $resolve -PrependToPath -Quiet }

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq "PNS_ADB_SERIAL") { return $t.Substring($eq + 1).Trim() }
    }
    return $null
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}

$adbPrefix = @()
if ($Serial) { $adbPrefix = @("-s", $Serial) }

$null = New-Item -ItemType Directory -Force -Path (Join-Path $PSScriptRoot "..\hfr-runs") -ErrorAction SilentlyContinue

$repo = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$apk = Join-Path $repo "app\build\outputs\apk\debug\app-debug.apk"
$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $repo "hfr-runs\max_res_key_sweep_$utc"
$logsDir = Join-Path $outDir "per_candidate_logs"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
New-Item -ItemType Directory -Force -Path $logsDir | Out-Null

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    & adb @adbPrefix install -r -t $apk 2>&1 | Out-Null
}

& adb @adbPrefix shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null
& adb @adbPrefix shell pm grant $pkg android.permission.READ_MEDIA_IMAGES 2>$null | Out-Null

# Ensure root is available for this lane.
$rootCheck = & adb @adbPrefix shell su -c "id" 2>$null
if (-not ($rootCheck -match "uid=0")) {
    throw "Root check failed (`su -c id` did not return uid=0)."
}

$dumpsysPath = Join-Path $outDir "dumpsys_media_camera.txt"
& adb @adbPrefix shell dumpsys media.camera > $dumpsysPath

$dumpText = Get-Content -LiteralPath $dumpsysPath -Raw

function Merge-CamxOverrideContent(
    [string]$baseText,
    [hashtable]$overrides
) {
    $result = $baseText
    foreach ($entry in $overrides.GetEnumerator()) {
        $k = [regex]::Escape($entry.Key)
        $line = "$($entry.Key)=$($entry.Value)"
        $pattern = "(?im)^[ \t]*$k[ \t]*=.*$"
        if ([regex]::IsMatch($result, $pattern)) {
            $result = [regex]::Replace($result, $pattern, $line)
        } else {
            if ($result.Length -gt 0 -and -not $result.EndsWith("`n")) { $result += "`n" }
            $result += "$line`n"
        }
    }
    return $result
}

function Get-QcfaProfileVariants {
    return @(
        [ordered]@{
            id = "baseline"
            description = "No camx override bind mount"
            camxOverrides = @{}
        },
        [ordered]@{
            id = "qcfa_force_feature0"
            description = "Force sensor mode + expose QCFA full size (feature0)"
            camxOverrides = @{
                overrideForceSensorMode = "0"
                exposeFullSizeForQCFA = "TRUE"
                useFeatureForQCFA = "0"
            }
        },
        [ordered]@{
            id = "qcfa_force_feature1_adv"
            description = "QCFA full size with feature1 + advance mask"
            camxOverrides = @{
                overrideForceSensorMode = "0"
                exposeFullSizeForQCFA = "TRUE"
                useFeatureForQCFA = "1"
                disablePDAF = "FALSE"
                advanceFeatureMask = "0x7E7"
            }
        },
        [ordered]@{
            id = "qcfa_force_feature0_dump"
            description = "Feature0 plus debug dump flag"
            camxOverrides = @{
                overrideForceSensorMode = "0"
                exposeFullSizeForQCFA = "TRUE"
                useFeatureForQCFA = "0"
                enableFeature2Dump = "1"
            }
        }
    )
}

function Resolve-FwkConfigPathFromInventory(
    [object[]]$inventory
) {
    if (-not $inventory) { return $null }
    $preferred = @(
        "/odm/etc/camera/fwk_config.json",
        "/vendor/etc/camera/fwk_config.json",
        "/system_ext/etc/camera/fwk_config.json",
        "/product/etc/camera/fwk_config.json"
    )
    foreach ($p in $preferred) {
        $hit = @($inventory | Where-Object { $_.path -ieq $p } | Select-Object -First 1)
        if ($hit) { return $hit[0].path }
    }
    $fallback = @($inventory | Where-Object { $_.name -ieq "fwk_config.json" } | Select-Object -First 1)
    if ($fallback) { return $fallback[0].path }
    return $null
}

function Ensure-ArrayContainsValue(
    [hashtable]$target,
    [string]$key,
    [string]$value
) {
    $existing = $target[$key]
    $arr = @()
    if ($null -eq $existing) {
        $arr = @()
    } elseif ($existing -is [string]) {
        $arr = @($existing)
    } elseif ($existing -is [System.Collections.IEnumerable]) {
        $arr = @($existing)
    } else {
        $arr = @("$existing")
    }
    if ($arr -notcontains $value) {
        $arr += $value
    }
    $target[$key] = @($arr)
}

function Merge-FwkConfigContent(
    [string]$baseText,
    [string]$mode
) {
    $obj = $baseText | ConvertFrom-Json -AsHashtable
    if ($null -eq $obj) { throw "fwk json parse produced null object" }
    if ($null -eq $obj["default"] -or -not ($obj["default"] -is [hashtable])) {
        $obj["default"] = @{}
    }
    $default = [hashtable]$obj["default"]
    switch ($mode) {
        "privileged_only" {
            Ensure-ArrayContainsValue -target $default -key "privileged_app_list" -value "dev.pointandshoot"
        }
        "privileged_and_skiprus" {
            Ensure-ArrayContainsValue -target $default -key "privileged_app_list" -value "dev.pointandshoot"
            Ensure-ArrayContainsValue -target $default -key "skip_rus_check_list" -value "dev.pointandshoot"
        }
        default {
            throw "unsupported fwk patch mode: $mode"
        }
    }
    return ($obj | ConvertTo-Json -Depth 64)
}

function Get-CamxBaseContent {
    param([string]$camxTargetPath)
    if ([string]::IsNullOrWhiteSpace($camxTargetPath)) { return $null }
    $raw = & adb @adbPrefix shell su -c "cat $camxTargetPath" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    if (-not $raw) { return "" }
    return ($raw -join "`n")
}

function Get-FwkBaseContent {
    param([string]$fwkTargetPath)
    if ([string]::IsNullOrWhiteSpace($fwkTargetPath)) { return $null }
    $raw = & adb @adbPrefix shell su -c "cat $fwkTargetPath" 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    if (-not $raw) { return "" }
    return ($raw -join "`n")
}

function Resolve-CamxTargetPath {
    $candidates = @(
        "/vendor/etc/camera/camxoverridesettings.txt",
        "/vendor/etc/camera/camxoverridesettingsofultra.txt",
        "/vendor/etc/camera/camxoverridesettings_default.txt",
        "/odm/etc/camera/camxoverridesettings.txt",
        "/odm/etc/camera/camxoverridesettingsofultra.txt"
    )
    foreach ($p in $candidates) {
        & adb @adbPrefix shell su -c "test -f $p"
        if ($LASTEXITCODE -eq 0) { return $p }
    }
    $lsOut = & adb @adbPrefix shell su -c "ls /vendor/etc/camera /odm/etc/camera 2>/dev/null" 2>$null
    $camxName =
        @($lsOut |
            Where-Object { $_ -match "camxoverride" } |
            ForEach-Object { $_.Trim() } |
            Select-Object -First 1)
    if ($camxName) {
        $name = $camxName[0]
        foreach ($root in @("/vendor/etc/camera", "/odm/etc/camera")) {
            $p = "$root/$name"
            & adb @adbPrefix shell su -c "test -f $p"
            if ($LASTEXITCODE -eq 0) { return $p }
        }
    }
    return $null
}

function Get-CameraConfigInventory {
    $roots = @("/vendor/etc/camera", "/odm/etc/camera", "/system_ext/etc/camera", "/product/etc/camera")
    $rows = New-Object System.Collections.Generic.List[object]
    foreach ($root in $roots) {
        & adb @adbPrefix shell su -c "test -d $root"
        if ($LASTEXITCODE -ne 0) { continue }
        $ls = & adb @adbPrefix shell su -c "ls -1 $root" 2>$null
        foreach ($name in @($ls | ForEach-Object { $_.Trim() } | Where-Object { $_ })) {
            $path = "$root/$name"
            $meta = & adb @adbPrefix shell su -c "ls -lZ $path" 2>$null
            $metaLine = (@($meta) -join " ").Trim()
            $rows.Add([ordered]@{
                root = $root
                path = $path
                name = $name
                looksCamx = ($name -match "camxoverride")
                looksCameraConfig = ($name -match "camera|camx|qcamera|qcom|chi|override|feature")
                lsMeta = $metaLine
            })
        }
    }
    return $rows.ToArray()
}

function Resolve-CamxTargetPathFromInventory(
    [object[]]$inventory
) {
    if (-not $inventory) { return $null }
    $preferred = @(
        "camxoverridesettings.txt",
        "camxoverridesettingsofultra.txt",
        "camxoverridesettings_default.txt"
    )
    foreach ($n in $preferred) {
        $hit = @($inventory | Where-Object { $_.name -ieq $n } | Select-Object -First 1)
        if ($hit) { return $hit[0].path }
    }
    $camxLike = @(
        $inventory |
            Where-Object { $_.looksCamx } |
            Sort-Object -Property @{ Expression = { $_.name.Length }; Descending = $false }
    )
    if ($camxLike.Count -gt 0) { return $camxLike[0].path }
    return $null
}

function Apply-CamxVariant(
    [hashtable]$variant,
    [string]$baseCamxText,
    [string]$camxTargetPath
) {
    if ($variant.id -eq "baseline") {
        if (-not [string]::IsNullOrWhiteSpace($camxTargetPath)) {
            & adb @adbPrefix shell su -c "umount $camxTargetPath" 2>$null | Out-Null
            return [ordered]@{ applied = $true; reason = "baseline_no_mount" }
        }
        return [ordered]@{ applied = $true; reason = "baseline_no_camx_path" }
    }
    if ([string]::IsNullOrWhiteSpace($camxTargetPath)) {
        return [ordered]@{ applied = $false; reason = "camx_target_missing" }
    }
    if ($null -eq $baseCamxText) {
        return [ordered]@{ applied = $false; reason = "base_camx_unavailable" }
    }
    $merged = Merge-CamxOverrideContent -baseText $baseCamxText -overrides $variant.camxOverrides
    $localCamx = Join-Path $outDir ("camx_{0}.txt" -f $variant.id)
    $remoteCamx = "/data/local/tmp/pns_camx_{0}.txt" -f $variant.id
    Set-Content -LiteralPath $localCamx -Value $merged -Encoding ascii
    & adb @adbPrefix push $localCamx $remoteCamx 2>&1 | Out-Null
    & adb @adbPrefix shell su -c "chcon u:object_r:vendor_configs_file:s0 $remoteCamx" 2>$null | Out-Null
    & adb @adbPrefix shell su -c "umount $camxTargetPath" 2>$null | Out-Null
    $mountOut = (& adb @adbPrefix shell su -c "mount --bind $remoteCamx $camxTargetPath" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        # Some root environments require Magisk mount namespace switch.
        $mountOutMm = (& adb @adbPrefix shell su -mm -c "mount --bind $remoteCamx $camxTargetPath" 2>&1)
        if ($LASTEXITCODE -ne 0) {
            return [ordered]@{
                applied = $false
                reason = "bind_mount_failed"
                mountError = ($mountOut -join "; ")
                mountErrorMm = ($mountOutMm -join "; ")
                localCamx = $localCamx
                remoteCamx = $remoteCamx
            }
        }
    }
    & adb @adbPrefix shell su -c "pkill -f vendor.qti.camera.provider-service_64; pkill -f cameraserver" 2>$null | Out-Null
    Start-Sleep -Seconds 2
    return [ordered]@{ applied = $true; reason = "bind_mount_ok"; localCamx = $localCamx; remoteCamx = $remoteCamx }
}

function Apply-FwkVariant(
    [hashtable]$variant,
    [string]$baseFwkText,
    [string]$fwkTargetPath
) {
    if ([string]::IsNullOrWhiteSpace($fwkTargetPath)) {
        return [ordered]@{ applied = $false; reason = "fwk_target_missing" }
    }
    if ($null -eq $baseFwkText) {
        return [ordered]@{ applied = $false; reason = "base_fwk_unavailable" }
    }
    try {
        $merged = Merge-FwkConfigContent -baseText $baseFwkText -mode $variant.fwkPatchMode
    } catch {
        return [ordered]@{ applied = $false; reason = "fwk_merge_failed"; mergeError = $_.Exception.Message }
    }
    $localFwk = Join-Path $outDir ("fwk_{0}.json" -f $variant.id)
    $remoteFwk = "/data/local/tmp/pns_fwk_{0}.json" -f $variant.id
    Set-Content -LiteralPath $localFwk -Value $merged -Encoding utf8
    & adb @adbPrefix push $localFwk $remoteFwk 2>&1 | Out-Null
    & adb @adbPrefix shell su -c "chcon u:object_r:vendor_configs_file:s0 $remoteFwk" 2>$null | Out-Null
    & adb @adbPrefix shell su -c "umount $fwkTargetPath" 2>$null | Out-Null
    $mountOut = (& adb @adbPrefix shell su -c "mount --bind $remoteFwk $fwkTargetPath" 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $mountOutMm = (& adb @adbPrefix shell su -mm -c "mount --bind $remoteFwk $fwkTargetPath" 2>&1)
        if ($LASTEXITCODE -ne 0) {
            return [ordered]@{
                applied = $false
                reason = "bind_mount_failed"
                mountError = ($mountOut -join "; ")
                mountErrorMm = ($mountOutMm -join "; ")
                localFwk = $localFwk
                remoteFwk = $remoteFwk
            }
        }
    }
    & adb @adbPrefix shell su -c "pkill -f vendor.qti.camera.provider-service_64; pkill -f cameraserver" 2>$null | Out-Null
    Start-Sleep -Seconds 2
    return [ordered]@{ applied = $true; reason = "bind_mount_ok"; localFwk = $localFwk; remoteFwk = $remoteFwk }
}

function Clear-CamxVariantMount {
    param([string]$camxTargetPath)
    if ([string]::IsNullOrWhiteSpace($camxTargetPath)) { return }
    & adb @adbPrefix shell su -c "umount $camxTargetPath" 2>$null | Out-Null
}

function Clear-FwkVariantMount {
    param([string]$fwkTargetPath)
    if ([string]::IsNullOrWhiteSpace($fwkTargetPath)) { return }
    & adb @adbPrefix shell su -c "umount $fwkTargetPath" 2>$null | Out-Null
}

$sessionRegex = [regex]'(?<![A-Za-z0-9_])(org\.codeaurora\.qcamera3\.sessionParameters\.[A-Za-z0-9_.]+|com\.oplus\.camera\.sessionParameters\.[A-Za-z0-9_.]+)'
$requestRegex = [regex]'(?<![A-Za-z0-9_])(com\.oplus\.[A-Za-z0-9_.]+|org\.quic\.camera2\.[A-Za-z0-9_.]+)'

$sessionCandidates = @(
    $sessionRegex.Matches($dumpText) |
        ForEach-Object { $_.Groups[1].Value } |
        Sort-Object -Unique
)
$requestCandidates = @(
    $requestRegex.Matches($dumpText) |
        ForEach-Object { $_.Groups[1].Value } |
        Where-Object { $_ -notmatch "\.sessionParameters\." } |
        Sort-Object -Unique
)

function Prioritize-Candidates([string[]]$all, [string[]]$priority, [int]$limit) {
    $ordered = New-Object System.Collections.Generic.List[string]
    foreach ($p in $priority) {
        if ($all -contains $p -and -not $ordered.Contains($p)) { $ordered.Add($p) }
    }
    foreach ($k in $all) {
        if (-not $ordered.Contains($k)) { $ordered.Add($k) }
    }
    return @($ordered | Select-Object -First ([Math]::Max(0, $limit)))
}

$sessionPriority = @(
    "org.codeaurora.qcamera3.sessionParameters.availableStreamMap",
    "org.codeaurora.qcamera3.sessionParameters.EnableXCFAOptimization",
    "org.codeaurora.qcamera3.sessionParameters.EnableQCFAMode",
    "org.codeaurora.qcamera3.sessionParameters.EnableQuadCFAMode"
)
$requestPriority = @(
    "com.oplus.QCFARemosaicType",
    "com.oplus.bypass.snapshot.SFERemosaic",
    "com.oplus.bokeh.zoom.picture.size"
)

if ($requestCandidates -notcontains "com.oplus.QCFARemosaicType") {
    $requestCandidates = @("com.oplus.QCFARemosaicType") + $requestCandidates
}

$sessionCandidates = Prioritize-Candidates -all $sessionCandidates -priority $sessionPriority -limit $MaxSessionCandidates
$requestCandidates = Prioritize-Candidates -all $requestCandidates -priority $requestPriority -limit $MaxRequestCandidates

Set-Content -LiteralPath (Join-Path $outDir "candidates_session.txt") -Value ($sessionCandidates -join "`n")
Set-Content -LiteralPath (Join-Path $outDir "candidates_request.txt") -Value ($requestCandidates -join "`n")

function Invoke-CandidateRun(
    [string]$Scope,
    [string]$Key,
    [int]$Index,
    [string]$VariantId
) {
    & adb @adbPrefix shell logcat -c 2>$null | Out-Null
    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
    & adb @adbPrefix shell su -c "rm -f /data/user/0/$pkg/shared_prefs/pns_experimental_safe_mode.xml" 2>$null | Out-Null

    $startArgs = @(
        "shell", "am", "start", "-W", "-n", "${pkg}/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview",
        "--es", "pns_preview_imaging_profile", "standard_pro",
        "--es", "pns_preview_still_resolution_mode", "max_resolution",
        "--es", "pns_preview_dial", "H",
        "--es", "pns_preview_camera_id", "2",
        "--ei", "pns_preview_raw_count", "1",
        "--ez", "pns_preview_raw_still_fast", "true",
        "--ez", "pns_preview_experimental_master", "true",
        "--ez", "pns_preview_experimental_max_res_unlock", "true",
        "--ez", "pns_preview_experimental_vendor_session", "true",
        "--ez", "pns_preview_force_safe_mode", "false"
    )
    if ($Scope -eq "session") {
        $startArgs += @("--es", "pns_preview_max_res_sweep_session_keys", $Key)
    } else {
        $startArgs += @("--es", "pns_preview_max_res_sweep_request_keys", $Key)
    }
    & adb @adbPrefix @startArgs 2>&1 | Out-Null
    Start-Sleep -Seconds $WaitSec

    $safeName = ($Key -replace "[^A-Za-z0-9_.-]", "_")
    $logPath = Join-Path $logsDir ("{0}_{1}_{2:000}_{3}_logcat.txt" -f $VariantId, $Scope, $Index, $safeName)
    & adb @adbPrefix exec-out logcat -d 2>$null | Out-File -LiteralPath $logPath -Encoding utf8
    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

    $text = Get-Content -LiteralPath $logPath -Raw
    $escapedKey = [regex]::Escape($Key)
    $scopeLineRegex = [regex]"maxResSweep scope=$Scope cam=2 key=$escapedKey present=(true|false) settable=(true|false) type=([^\s]+)"
    $scopeMatches = $scopeLineRegex.Matches($text)
    $scopeLine = if ($scopeMatches.Count -gt 0) { $scopeMatches[$scopeMatches.Count - 1].Value } else { $null }
    $present = if ($scopeLine) { $scopeLine -match "present=true" } else { $false }
    $settable = if ($scopeLine) { $scopeLine -match "settable=true" } else { $false }
    $type = if ($scopeLine) { ([regex]::Match($scopeLine, "type=([^\s]+)")).Groups[1].Value } else { "none" }

    $unlockApplied = $text -match "maxResUnlock active=true applied=true"
    $captureOk = $text -match "captureRawStill 1/1 ok=true saved="
    $noRejection =
        -not (
            $text -match "request rejected|Request rejected|onCaptureFailed|ERROR_CAMERA_DEVICE|createCaptureSession failed|session create failed|IllegalArgumentException"
        )
    $rawDiag = [regex]::Match($text, "dng save diag .*rawWxH=(\d+x\d+)").Groups[1].Value
    $rawReader = [regex]::Match($text, "RAW ImageReader (\d+x\d+) format=(\d+)").Groups[1].Value

    $score = 0
    if ($present) { $score += 2 }
    if ($settable) { $score += 3 }
    if ($noRejection) { $score += 2 }
    if ($captureOk) { $score += 2 }
    if ($unlockApplied) { $score += 1 }

    return [ordered]@{
        variant = $VariantId
        scope = $Scope
        key = $Key
        present = $present
        settable = $settable
        type = $type
        noRejection = $noRejection
        captureOk = $captureOk
        unlockApplied = $unlockApplied
        rawReaderWxH = if ($rawReader) { $rawReader } else { $null }
        rawDiagWxH = if ($rawDiag) { $rawDiag } else { $null }
        score = $score
        logPath = $logPath
        scopeLine = $scopeLine
    }
}

$results = New-Object System.Collections.Generic.List[object]
$variantApply = New-Object System.Collections.Generic.List[object]

$cameraConfigInventory = if ($Profile -eq "QualcommQCFA") { Get-CameraConfigInventory } else { @() }
$cameraConfigInventoryPath = Join-Path $outDir "camera_config_inventory.json"
$cameraConfigInventory | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $cameraConfigInventoryPath -Encoding utf8
$cameraConfigInventoryMdPath = Join-Path $outDir "camera_config_inventory.md"
$invMd = New-Object System.Collections.Generic.List[string]
$invMd.Add("# Camera config inventory")
$invMd.Add("")
if ($cameraConfigInventory.Count -eq 0) {
    $invMd.Add("- No readable camera config directories found via root shell.")
} else {
    foreach ($row in $cameraConfigInventory) {
        $invMd.Add("- path=$($row.path) camx=$($row.looksCamx) cameraLike=$($row.looksCameraConfig)")
    }
}
$invMd | Set-Content -LiteralPath $cameraConfigInventoryMdPath -Encoding utf8

$camxTargetPath =
    if ($Profile -eq "QualcommQCFA") {
        if (-not [string]::IsNullOrWhiteSpace($CamxTargetPathOverride)) {
            $CamxTargetPathOverride
        } else {
            $resolved = Resolve-CamxTargetPath
            if ([string]::IsNullOrWhiteSpace($resolved)) {
                Resolve-CamxTargetPathFromInventory -inventory $cameraConfigInventory
            } else {
                $resolved
            }
        }
    } else {
        $null
    }
$fwkTargetPath = if ($Profile -eq "QualcommQCFA") { Resolve-FwkConfigPathFromInventory -inventory $cameraConfigInventory } else { $null }
$baseCamxText = if ($Profile -eq "QualcommQCFA") { Get-CamxBaseContent -camxTargetPath $camxTargetPath } else { $null }
$baseFwkText = if ($Profile -eq "QualcommQCFA") { Get-FwkBaseContent -fwkTargetPath $fwkTargetPath } else { $null }
$matrixMode = "default"

$variants =
    if ($Profile -eq "QualcommQCFA") {
        if (-not [string]::IsNullOrWhiteSpace($camxTargetPath)) {
            $matrixMode = "qualcomm_qcfa_camx"
            @(
                [ordered]@{ id = "baseline"; description = "No override bind mount"; variantKind = "baseline" },
                [ordered]@{
                    id = "qcfa_force_feature0"
                    description = "Force sensor mode + expose QCFA full size (feature0)"
                    variantKind = "camx"
                    camxOverrides = @{ overrideForceSensorMode = "0"; exposeFullSizeForQCFA = "TRUE"; useFeatureForQCFA = "0" }
                },
                [ordered]@{
                    id = "qcfa_force_feature1_adv"
                    description = "QCFA full size with feature1 + advance mask"
                    variantKind = "camx"
                    camxOverrides = @{ overrideForceSensorMode = "0"; exposeFullSizeForQCFA = "TRUE"; useFeatureForQCFA = "1"; disablePDAF = "FALSE"; advanceFeatureMask = "0x7E7" }
                },
                [ordered]@{
                    id = "qcfa_force_feature0_dump"
                    description = "Feature0 plus debug dump flag"
                    variantKind = "camx"
                    camxOverrides = @{ overrideForceSensorMode = "0"; exposeFullSizeForQCFA = "TRUE"; useFeatureForQCFA = "0"; enableFeature2Dump = "1" }
                }
            )
        } elseif (-not [string]::IsNullOrWhiteSpace($fwkTargetPath)) {
            $matrixMode = "qualcomm_qcfa_fwk"
            @(
                [ordered]@{ id = "baseline"; description = "No override bind mount"; variantKind = "baseline" },
                [ordered]@{ id = "fwk_privileged_only"; description = "Add app to default.privileged_app_list"; variantKind = "fwk"; fwkPatchMode = "privileged_only" },
                [ordered]@{ id = "fwk_privileged_and_skiprus"; description = "Add app to privileged + skip_rus lists"; variantKind = "fwk"; fwkPatchMode = "privileged_and_skiprus" }
            )
        } else {
            $matrixMode = "qualcomm_qcfa_no_target"
            @([ordered]@{ id = "baseline"; description = "No override bind mount"; variantKind = "baseline" })
        }
    } else {
        @([ordered]@{ id = "baseline"; description = "Default sweep profile"; variantKind = "baseline" })
    }

try {
    foreach ($variant in $variants) {
        Write-Host "[max_res_key_sweep] variant=$($variant.id) profile=$Profile"
        $applyInfo =
            switch ($variant.variantKind) {
                "baseline" {
                    [ordered]@{ applied = $true; reason = "baseline_no_mount" }
                    break
                }
                "camx" {
                    Apply-CamxVariant -variant $variant -baseCamxText $baseCamxText -camxTargetPath $camxTargetPath
                    break
                }
                "fwk" {
                    Apply-FwkVariant -variant $variant -baseFwkText $baseFwkText -fwkTargetPath $fwkTargetPath
                    break
                }
                default {
                    [ordered]@{ applied = $false; reason = "unknown_variant_kind_$($variant.variantKind)" }
                    break
                }
            }
        $variantApply.Add([ordered]@{
            variant = $variant.id
            description = $variant.description
            variantKind = $variant.variantKind
            applied = $applyInfo.applied
            reason = $applyInfo.reason
            camxTargetPath = $camxTargetPath
            fwkTargetPath = $fwkTargetPath
            mountError = $applyInfo.mountError
            mountErrorMm = $applyInfo.mountErrorMm
            localCamx = $applyInfo.localCamx
            remoteCamx = $applyInfo.remoteCamx
            localFwk = $applyInfo.localFwk
            remoteFwk = $applyInfo.remoteFwk
            mergeError = $applyInfo.mergeError
        })
        if (-not $applyInfo.applied) {
            Write-Host "[max_res_key_sweep] skip variant=$($variant.id) reason=$($applyInfo.reason)"
            continue
        }
        $i = 1
        foreach ($k in $sessionCandidates) {
            Write-Host "[max_res_key_sweep] $($variant.id) session $i/$($sessionCandidates.Count): $k"
            $results.Add((Invoke-CandidateRun -Scope "session" -Key $k -Index $i -VariantId $variant.id))
            $i++
        }
        $i = 1
        foreach ($k in $requestCandidates) {
            Write-Host "[max_res_key_sweep] $($variant.id) request $i/$($requestCandidates.Count): $k"
            $results.Add((Invoke-CandidateRun -Scope "request" -Key $k -Index $i -VariantId $variant.id))
            $i++
        }
    }
} finally {
    Clear-CamxVariantMount -camxTargetPath $camxTargetPath
    Clear-FwkVariantMount -fwkTargetPath $fwkTargetPath
    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
}

$ranked = @(
    $results | Sort-Object -Stable -Property `
        @{ Expression = { [int]$_.score }; Descending = $true }, `
        @{ Expression = { if ($_.present) { 1 } else { 0 } }; Descending = $true }, `
        @{ Expression = { if ($_.settable) { 1 } else { 0 } }; Descending = $true }, `
        @{ Expression = { if ($_.noRejection) { 1 } else { 0 } }; Descending = $true }, `
        @{ Expression = { if ($_.captureOk) { 1 } else { 0 } }; Descending = $true }, `
        @{ Expression = { $_.variant }; Descending = $false }, `
        @{ Expression = { $_.key }; Descending = $false }
)

$topScore = @($ranked | Select-Object -First 1 | ForEach-Object { $_.score }) | Select-Object -First 1
$summary = [ordered]@{
    schema = "pns.max_res_key_sweep.v2"
    timestampUtc = [DateTime]::UtcNow.ToString("o")
    serial = if ($Serial) { $Serial } else { "default" }
    profile = $Profile
    matrixMode = $matrixMode
    resolvedTargets = [ordered]@{
        camxTargetPath = $camxTargetPath
        fwkTargetPath = $fwkTargetPath
    }
    candidateCounts = [ordered]@{
        session = $sessionCandidates.Count
        request = $requestCandidates.Count
    }
    variants = $variantApply
    cameraConfigInventory = [ordered]@{
        count = @($cameraConfigInventory).Count
        jsonPath = $cameraConfigInventoryPath
        mdPath = $cameraConfigInventoryMdPath
    }
    ranking = $ranked
    artifacts = [ordered]@{
        outDir = $outDir
        dumpsysPath = $dumpsysPath
    }
}

$summaryPath = Join-Path $outDir "max_res_key_sweep_summary.json"
$summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8

$top = @($ranked | Select-Object -First 12)
$md = New-Object System.Collections.Generic.List[string]
$md.Add("# Max-res key sweep")
$md.Add("")
$md.Add("- Profile: $Profile")
$md.Add("- Session candidates: $($sessionCandidates.Count)")
$md.Add("- Request candidates: $($requestCandidates.Count)")
if ($Profile -eq "QualcommQCFA") {
    $md.Add("- Matrix mode: $matrixMode")
    $md.Add("- Resolved CamX target: $(if ($camxTargetPath) { $camxTargetPath } else { "<none>" })")
    $md.Add("- Resolved fwk_config target: $(if ($fwkTargetPath) { $fwkTargetPath } else { "<none>" })")
    $md.Add("- Camera config inventory: $cameraConfigInventoryMdPath")
}
$md.Add("")
$md.Add("## Variant apply status")
foreach ($v in $variantApply) {
    $errMount = if (-not $v.applied -and $v.mountError) { " mountErr=$($v.mountError)" } else { "" }
    $errMerge = if (-not $v.applied -and $v.mergeError) { " mergeErr=$($v.mergeError)" } else { "" }
    $md.Add("- variant=$($v.variant) kind=$($v.variantKind) applied=$($v.applied) reason=$($v.reason)$errMount$errMerge")
}
$md.Add("")
$md.Add("## Top ranked candidates")
foreach ($r in $top) {
    $md.Add("- [$($r.score)] variant=$($r.variant) scope=$($r.scope) key=$($r.key) present=$($r.present) settable=$($r.settable) noRejection=$($r.noRejection) captureOk=$($r.captureOk) type=$($r.type) raw=$($r.rawDiagWxH)")
}
$md.Add("")
$md.Add("Summary JSON: $summaryPath")
$md.Add("Artifacts root: $outDir")
$mdPath = Join-Path $outDir "max_res_key_sweep_summary.md"
$md | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host "MAX_RES_KEY_SWEEP: profile=$Profile session=$($sessionCandidates.Count) request=$($requestCandidates.Count) topScore=$topScore"
Write-Host "Artifacts: $outDir"
exit 0

