#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.1** — face/eye overlay pixel gate (USB + optional PIL). -HostOnly skips device.
#>
param(
    [string]$Serial = "",
    [switch]$HostOnly,
    [switch]$SkipAssemble,
    [switch]$SkipInstall,
    [int]$MaxAttempts = 12,
    [int]$AttemptDelaySec = 2
)

$ErrorActionPreference = "Stop"
if ($HostOnly) {
    Write-Host "EYE AF PIXEL GATE: SKIP (HostOnly — run on USB with face in frame)"
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
Push-Location $repoRoot
try {
    if (Test-Path "$PSScriptRoot\\pns_resolve_adb.ps1") {
        . "$PSScriptRoot\\pns_resolve_adb.ps1" -PrependToPath -Quiet
    }
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if ($Serial -eq "" -and (Test-Path $envFile)) {
        Get-Content $envFile | ForEach-Object {
            if ($_ -match '^\\s*PNS_ADB_SERIAL\\s*=\\s*(.+)\\s*$') { $Serial = $Matches[1].Trim().Trim('\"') }
        }
    }

    function Invoke-AdbCmd {
        if ($Serial -ne "") { & adb -s $Serial @args } else { & adb @args }
    }

    if (-not $SkipAssemble) {
        & "$PSScriptRoot\\pns_gradlew.ps1" :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed" }
    }
    $apk = "app\\build\\outputs\\apk\\debug\\app-debug.apk"
    if (-not (Test-Path $apk)) { throw "Missing $apk" }

    if (-not $SkipInstall) {
        Invoke-AdbCmd install -r -t $apk | Out-Null
        Invoke-AdbCmd shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null
    }

    $pkg = "dev.pointandshoot"
    $act = "dev.pointandshoot/.MainActivity"
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $outDir = Join-Path $repoRoot ("hfr-runs\\eye_af_pixel_gate_{0}" -f $ts)
    New-Item -ItemType Directory -Force -Path $outDir | Out-Null

    # Cold preview + force overlay on + lock SS to enable chase logs (fps 30 so chase is active).
    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null
    Start-Sleep -Milliseconds 700
    Invoke-AdbCmd logcat -c 2>$null | Out-Null
    $startArgs = @(
        "shell", "am", "start", "-W", "-n", $act,
        "--es", "pns_screen", "preview",
        "--ei", "pns_preview_video_fps", "30",
        "--ez", "pns_preview_eye_af_overlay", "true",
        "--el", "pns_preview_readout_shutter_ns", "8333333"
    )
    Invoke-AdbCmd @startArgs 2>&1 | Out-Null
    Start-Sleep -Seconds 10

    $adbPrefix = if ($Serial -ne "") { "adb -s $Serial" } else { "adb" }
    $py = Join-Path $PSScriptRoot "pns_eye_af_overlay_align.py"
    $lastOut = $null
    $ok = $false
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        $png = Join-Path $outDir ("screencap_{0:00}.png" -f $i)
        & cmd /c "$adbPrefix exec-out screencap -p > `"$png`""

        $annot = Join-Path $outDir ("annot_{0:00}.png" -f $i)
        $json = Join-Path $outDir ("align_{0:00}.json" -f $i)
        $pyOut = & python $py $png $json $annot 2>&1
        $pyExit = $LASTEXITCODE
        $lastOut = $pyOut
        $pyOut | Set-Content (Join-Path $outDir ("align_stdout_{0:00}.txt" -f $i)) -Encoding UTF8
        if ($pyExit -eq 0) {
            $ok = $true
            break
        }
        Start-Sleep -Seconds $AttemptDelaySec
    }

    Invoke-AdbCmd shell am force-stop $pkg 2>$null | Out-Null

    if (-not $ok) {
        Write-Host "EYE AF PIXEL GATE: FAIL — see $outDir" -ForegroundColor Red
        exit 1
    }
    Write-Host "EYE AF PIXEL GATE: PASS — artifacts: $outDir" -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
