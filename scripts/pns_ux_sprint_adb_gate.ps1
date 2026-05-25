<#
.SYNOPSIS
  Sprint **UX** — full ADB gate: theme, navigation (gesture + 3-button), back, batch share, workflows.

.EXAMPLE
  .\scripts\pns_ux_sprint_adb_gate.ps1
  .\scripts\pns_ux_sprint_adb_gate.ps1 -SkipGradle -SkipInstall
#>
param(
    [string]$Serial = "",
    [int]$GalleryBatchCount = 2,
    [switch]$SkipNavModeToggle,
    [switch]$SkipGradle,
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

function Invoke-Adb([string[]]$Extra) {
    if ($script:adbPrefix.Count -gt 0) { & adb @script:adbPrefix @Extra }
    else { & adb @Extra }
    if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Extra -join ' ')" }
}

function Invoke-AdbIgnore([string[]]$Extra) {
    if ($script:adbPrefix.Count -gt 0) { & adb @script:adbPrefix @Extra 2>$null }
    else { & adb @Extra 2>$null }
}

function Start-Preview([hashtable]$Extras, [int]$WaitSec) {
    $args = @(
        "shell", "am", "start", "-W", "-n", "${script:pkg}/.MainActivity",
        "--activity-clear-task",
        "--es", "pns_screen", "preview"
    )
    foreach ($k in $Extras.Keys) {
        $v = $Extras[$k]
        if ($v -is [bool]) {
            $args += @("--ez", $k, ($(if ($v) { "true" } else { "false" })))
        } elseif ($v -is [int]) {
            $args += @("--ei", $k, "$v")
        } else {
            $args += @("--es", $k, "$v")
        }
    }
    Invoke-AdbIgnore $args | Out-Null
    Start-Sleep -Seconds $WaitSec
}

function Pull-Log([string]$Path) {
    Invoke-Adb @("exec-out", "logcat", "-d", "-s",
        "PNS.AdbValidation:I", "PNS.NavUx:I", "PNS.NavUx:D",
        "PNS.Workflow:I", "PNS.Gallery:I") 2>$null |
        Out-File -LiteralPath $Path -Encoding utf8
}

function Get-SecureNavMode {
    $raw = Invoke-Adb @("shell", "settings", "get", "secure", "navigation_mode")
    return ($raw -join "").Trim()
}

function Set-SecureNavMode([string]$Mode) {
    Invoke-AdbIgnore @("shell", "settings", "put", "secure", "navigation_mode", $Mode) | Out-Null
}

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$script:pkg = "dev.pointandshoot"

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}
$script:adbPrefix = @()
if ($Serial) { $script:adbPrefix = @("-s", $Serial) }

if (-not $SkipGradle) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    Invoke-Adb @("install", "-r", "-t", $apk) | Out-Null
}
Invoke-AdbIgnore @("shell", "pm", "grant", $script:pkg, "android.permission.CAMERA") | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\ux_sprint_adb_gate_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_ux_sprint.txt"
$gatePath = Join-Path $outDir "gate.json"

$origNavMode = Get-SecureNavMode
Write-Host "[ux_sprint_adb_gate] device nav_mode=$origNavMode"

Invoke-AdbIgnore @("shell", "logcat", "-c") | Out-Null
Invoke-AdbIgnore @("shell", "am", "force-stop", $script:pkg) | Out-Null
Start-Sleep -Milliseconds 600

# UX.1 — theme Dark + Light
Start-Preview @{ "pns_preview_theme_mode" = "Dark" } 12
Start-Preview @{ "pns_preview_theme_mode" = "Light" } 10

# UX.2 — preview nav telemetry + gesture exclusion
Start-Preview @{} 15

# UX.2 — gallery open + batch share + system BACK
Start-Preview @{
    "pns_preview_open_gallery" = $true
    "pns_preview_gallery_batch_share" = $GalleryBatchCount
} 18
Invoke-AdbIgnore @("shell", "input", "keyevent", "KEYCODE_BACK") | Out-Null
Start-Sleep -Milliseconds 400
Invoke-AdbIgnore @("shell", "input", "keyevent", "KEYCODE_BACK") | Out-Null
Start-Sleep -Milliseconds 400

# UX.2 — nav mode matrix (3-button=0, gesture=2) when settings key works
$navThreeButtonOk = $false
$navGestureOk = $false
if (-not $SkipNavModeToggle) {
    foreach ($pair in @(@{ mode = "0"; label = "ThreeButton" }, @{ mode = "2"; label = "Gesture" })) {
        Set-SecureNavMode $pair.mode
        Start-Sleep -Milliseconds 800
        Invoke-AdbIgnore @("shell", "am", "force-stop", $script:pkg) | Out-Null
        Start-Sleep -Milliseconds 400
        Start-Preview @{} 10
        $snippet = Join-Path $outDir ("nav_" + $pair.label + ".txt")
        Pull-Log $snippet
        $h = Get-Content -LiteralPath $snippet -Raw -ErrorAction SilentlyContinue
        if ($h -match "navUx mode=$($pair.label)") {
            if ($pair.label -eq "ThreeButton") { $navThreeButtonOk = $true }
            if ($pair.label -eq "Gesture") { $navGestureOk = $true }
        }
    }
    if ($origNavMode) { Set-SecureNavMode $origNavMode }
}

# UX.3 — workflow presets
foreach ($preset in @("street", "portrait", "video_log")) {
    Invoke-AdbIgnore @("shell", "am", "force-stop", $script:pkg) | Out-Null
    Start-Sleep -Milliseconds 400
    Start-Preview @{ "pns_preview_workflow_preset" = $preset } 10
}

Pull-Log $logPath
Invoke-AdbIgnore @("shell", "am", "force-stop", $script:pkg) | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$themeDarkOk = $hay -match "preview seeded themeMode=Dark"
$themeLightOk = $hay -match "preview seeded themeMode=Light"
$navUxOk = $hay -match "navUx mode=(Gesture|ThreeButton|Unknown)"
$gestureExclusionOk = $hay -match "gestureExclusion"
$galleryOpenOk = $hay -match "preview openGallery=true"
$batchShareOk = $hay -match "gallery batchShare count=$GalleryBatchCount"
$navBackGalleryOk = $hay -match "navBack galleryExit|navBack previewGalleryClosed"
$workflowStreetOk = $hay -match "workflowPreset applied id=street"
$workflowPortraitOk = $hay -match "workflowPreset applied id=portrait"
$workflowVideoLogOk = $hay -match "workflowPreset applied id=video_log"

# If nav toggle unsupported on OEM, accept detected mode from baseline preview start
if (-not $SkipNavModeToggle -and -not $navThreeButtonOk -and -not $navGestureOk) {
    $navThreeButtonOk = $hay -match "navUx mode=ThreeButton"
    $navGestureOk = $hay -match "navUx mode=Gesture"
}
$navModeMatrixOk = $navThreeButtonOk -or $navGestureOk

$pass =
    $themeDarkOk -and $themeLightOk -and $navUxOk -and $gestureExclusionOk `
    -and $galleryOpenOk -and $batchShareOk -and $navBackGalleryOk `
    -and $workflowStreetOk -and $workflowPortraitOk -and $workflowVideoLogOk `
    -and $navModeMatrixOk

$gate = @{
    pass = $pass
    themeDarkOk = $themeDarkOk
    themeLightOk = $themeLightOk
    navUxOk = $navUxOk
    gestureExclusionOk = $gestureExclusionOk
    galleryOpenOk = $galleryOpenOk
    batchShareOk = $batchShareOk
    navBackGalleryOk = $navBackGalleryOk
    navThreeButtonOk = $navThreeButtonOk
    navGestureOk = $navGestureOk
    workflowStreetOk = $workflowStreetOk
    workflowPortraitOk = $workflowPortraitOk
    workflowVideoLogOk = $workflowVideoLogOk
    origNavMode = $origNavMode
    logPath = $logPath
}
$gate | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $gatePath -Encoding utf8

if ($pass) {
    Write-Host "[ux_sprint_adb_gate] PASS -> $outDir"
    exit 0
}
Write-Host "[ux_sprint_adb_gate] FAIL -> $outDir (see gate.json)"
exit 1
