#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint 14.10 — DND restore on preview exit / toggle-off (best-effort USB).

.DESCRIPTION
  Host: InterruptionFilterHoldTest + assembleDebug.
  USB:
    - Cold preview → PNS.ChromeUx dndPreview=applied (or skipped_no_policy)
    - KEYCODE_HOME (ON_STOP) → dndPreview=restored
    - dumpsys notification (interruption filter line captured)
    - Relaunch preview → applied again

.PARAMETER HostOnly
  Skip USB.

.PARAMETER Serial
  ADB serial (scripts/pns_adb_device.env when omitted).

.PARAMETER SkipAssemble
#>
param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipAssemble
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$pkg = "dev.pointandshoot"
$act = "dev.pointandshoot.MainActivity"
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

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = Join-Path $repoRoot "hfr-runs\dnd_restore_verify_$ts"
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    $result = [ordered]@{
        schema = "pns_dnd_restore_verify.v1"
        timestampUtc = (Get-Date).ToUniversalTime().ToString("o")
        gateDir = $outDir
        serial = $Serial
        hostOnly = [bool]$HostOnly
        unitTestsPass = $false
        assemblePass = $false
        dndAppliedOk = $null
        dndRestoredOk = $null
        dndReappliedOk = $null
        dumpsysCaptured = $false
        gateResult = "FAIL"
    }

    Write-Host "[pns_dnd_restore] JVM tests..."
    & "$PSScriptRoot\pns_gradlew.ps1" :app:testDebugUnitTest `
        --tests "dev.pointandshoot.InterruptionFilterHoldTest"
    if ($LASTEXITCODE -ne 0) { throw "unit tests failed" }
    $result.unitTestsPass = $true

    if (-not $SkipAssemble) {
        Write-Host "[pns_dnd_restore] assembleDebug..."
        & "$PSScriptRoot\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
        $result.assemblePass = $true
    } else {
        $result.assemblePass = Test-Path (Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk")
    }

    if ($HostOnly) {
        $result.gateResult = if ($result.unitTestsPass -and $result.assemblePass) { "HOST_PASS" } else { "FAIL" }
        $jsonPath = Join-Path $outDir "gate.json"
        $result | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonPath
        Write-Host "[pns_dnd_restore] GATE: $($result.gateResult) -> $jsonPath"
        if ($result.gateResult -eq "FAIL") { exit 1 }
        exit 0
    }

    $devices = @( (Invoke-AdbCmd devices) 2>$null | Where-Object { $_ -match "^\S+\s+device$" } )
    if ($devices.Length -eq 0) {
        Write-Host "[pns_dnd_restore] No device — HOST_PASS only"
        $result.gateResult = if ($result.unitTestsPass -and $result.assemblePass) { "HOST_PASS" } else { "FAIL" }
        $result | ConvertTo-Json -Depth 5 | Set-Content (Join-Path $outDir "gate.json") -Encoding UTF8
        if ($result.gateResult -eq "FAIL") { exit 1 }
        exit 0
    }

    $apk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path $apk) {
        Invoke-AdbCmd install -r -t $apk | Out-Null
        Invoke-AdbCmd shell pm grant $pkg android.permission.CAMERA 2>$null
    }

    # Ensure preview DND pref is on for this gate (user may have toggled it off in chrome).
    $prefsXml = @"
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <boolean name="dnd_while_in_preview" value="true" />
</map>
"@
    $prefsPath = Join-Path $outDir "pns_preview_chrome.xml"
    $prefsXml | Set-Content -Encoding UTF8 $prefsPath
    Invoke-AdbCmd push $prefsPath "/data/local/tmp/pns_preview_chrome_gate.xml" 2>$null | Out-Null
    Invoke-AdbCmd shell run-as $pkg mkdir -p shared_prefs 2>$null | Out-Null
    Invoke-AdbCmd shell run-as $pkg cp /data/local/tmp/pns_preview_chrome_gate.xml shared_prefs/pns_preview_chrome.xml 2>$null | Out-Null

    $policyGranted =
        (Invoke-AdbCmd shell dumpsys notification 2>$null | Out-String) -match 'access granted:\s*true'
    if (-not $policyGranted) {
        Write-Warning "[pns_dnd_restore] Notification policy access not granted — expect dndPreview=skipped_no_policy (USB restore path not exercised)."
    }

    function Start-Preview {
        Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
        Invoke-AdbCmd logcat -c 2>$null | Out-Null
        Invoke-AdbCmd shell am start -W -n "$pkg/$act" --activity-clear-task --es pns_screen preview 2>&1 | Out-Null
        Start-Sleep -Seconds 10
    }

    Start-Preview
    $log1 = Invoke-AdbCmd logcat -d -s PNS.ChromeUx:I 2>$null
    $log1 | Set-Content (Join-Path $outDir "logcat_after_launch.txt") -Encoding UTF8
    $result.dndAppliedOk = $log1 -match 'dndPreview=applied'
    if (-not $result.dndAppliedOk) {
        $result.dndAppliedOk = $log1 -match 'dndPreview=skipped_no_policy'
        Write-Host "[pns_dnd_restore] No policy access — applied skipped (best-effort)"
    }

    Invoke-AdbCmd shell input keyevent KEYCODE_HOME 2>$null | Out-Null
    Start-Sleep -Seconds 2
    $log2 = Invoke-AdbCmd logcat -d -s PNS.ChromeUx:I 2>$null
    $log2 | Set-Content (Join-Path $outDir "logcat_after_home.txt") -Encoding UTF8
    $result.dndRestoredOk = $log2 -match 'dndPreview=restored'

    $dumpsys = Invoke-AdbCmd shell dumpsys notification 2>$null
    if ($dumpsys) {
        $dumpsys | Select-Object -First 80 | Set-Content (Join-Path $outDir "dumpsys_notification_head.txt") -Encoding UTF8
        $result.dumpsysCaptured = $true
    }

    Start-Preview
    $log3 = Invoke-AdbCmd logcat -d -s PNS.ChromeUx:I 2>$null
    $log3 | Set-Content (Join-Path $outDir "logcat_after_relaunch.txt") -Encoding UTF8
    $result.dndReappliedOk = $log3 -match 'dndPreview=applied'

    $policySkip = $log1 -match 'dndPreview=skipped_no_policy'
    $usbPass =
        $result.dndAppliedOk -and
        (
            $result.dndRestoredOk -or $policySkip
        ) -and
        (
            $result.dndReappliedOk -or $policySkip
        )

    $result.gateResult = if ($usbPass) { "USB_PASS" } else { "FAIL" }
    $jsonPath = Join-Path $outDir "gate.json"
    $result | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $jsonPath
    Write-Host "[pns_dnd_restore] GATE: $($result.gateResult) appliedOk=$($result.dndAppliedOk) restoredOk=$($result.dndRestoredOk) reappliedOk=$($result.dndReappliedOk) -> $jsonPath"

    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

    if (-not $usbPass) { exit 1 }
    exit 0
}
finally {
    Pop-Location
}
