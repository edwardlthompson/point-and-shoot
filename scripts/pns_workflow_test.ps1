<#
.SYNOPSIS
  Sprint **UX.3** — workflow preset ADB apply (street / portrait / video_log).

.EXAMPLE
  .\scripts\pns_workflow_test.ps1 -PresetId street
#>
param(
    [string]$Serial = "",
    [ValidateSet("street", "portrait", "video_log")]
    [string]$PresetId = "street",
    [switch]$AllPresets,
    [int]$WaitSec = 25,
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

$projRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$pkg = "dev.pointandshoot"

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if ($fromEnv) { $Serial = $fromEnv }
}
$adbPrefix = @()
if ($Serial) { $adbPrefix = @("-s", $Serial) }

if (-not $SkipAssemble) {
    & (Join-Path $PSScriptRoot "pns_gradlew.ps1") ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
}
if (-not (Test-Path -LiteralPath $apk)) { throw "Missing APK: $apk" }
if (-not $SkipInstall) {
    & adb @adbPrefix install -r -t $apk 2>&1 | Out-Null
}
& adb @adbPrefix shell pm grant $pkg android.permission.CAMERA 2>$null | Out-Null

$utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
$outDir = Join-Path $projRoot "hfr-runs\workflow_test_$utc"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$logPath = Join-Path $outDir "logcat_workflow.txt"
$gatePath = Join-Path $outDir "gate.json"

& adb @adbPrefix shell logcat -c 2>$null | Out-Null
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
Start-Sleep -Milliseconds 600

$presetsToRun = if ($AllPresets) { @("street", "portrait", "video_log") } else { @($PresetId) }
foreach ($id in $presetsToRun) {
    & adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null
    Start-Sleep -Milliseconds 400
    & adb @adbPrefix shell am start -W -n "${pkg}/.MainActivity" `
        --activity-clear-task `
        --es pns_screen preview `
        --es pns_preview_workflow_preset $id 2>&1 | Out-Null
    Write-Host "[workflow_test] waiting ${WaitSec}s preset=$id..."
    Start-Sleep -Seconds $WaitSec
}

& adb @adbPrefix exec-out logcat -d -s "PNS.Workflow:I" "PNS.AdbValidation:I" "PNS.Gallery:I" 2>$null |
    Out-File -LiteralPath $logPath -Encoding utf8
& adb @adbPrefix shell am force-stop $pkg 2>$null | Out-Null

$hay = Get-Content -LiteralPath $logPath -Raw -ErrorAction SilentlyContinue
if (-not $hay) { $hay = "" }

$appliedOk = $hay -match "workflowPreset applied id=$PresetId"
$adbOk = $hay -match "workflowPreset applied id=$PresetId"
$streetOk = $hay -match "workflowPreset applied id=street"
$portraitOk = $hay -match "workflowPreset applied id=portrait"
$videoLogOk = $hay -match "workflowPreset applied id=video_log"
$batchOk = $hay -match "gallery batchShare count="
$pass = if ($AllPresets) { $streetOk -and $portraitOk -and $videoLogOk } else { $appliedOk -and $adbOk }

$gate = @{
    pass = $pass
    presetId = $PresetId
    allPresets = $AllPresets.IsPresent
    workflowApplied = $appliedOk
    adbWorkflow = $adbOk
    streetOk = $streetOk
    portraitOk = $portraitOk
    videoLogOk = $videoLogOk
    batchShareLogged = $batchOk
    logPath = $logPath
}
$gate | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $gatePath -Encoding utf8

if ($pass) {
    Write-Host "[workflow_test] PASS -> $outDir"
    exit 0
}
Write-Host "[workflow_test] FAIL appliedOk=$appliedOk adbOk=$adbOk -> $outDir"
exit 1
