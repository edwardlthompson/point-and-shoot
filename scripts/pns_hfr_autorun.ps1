param(
  [string]$OutDir = ".\\hfr-runs",
  # Android project root (folder containing gradlew.bat). Default: parent of scripts/.
  [string]$ProjectRoot = "",
  [int]$MaxRuns = 3,
  [int]$ExhaustiveTimeoutMinutes = 60,
  [int]$ProgressIntervalSeconds = 45,
  # Seconds to pause between encoder probe runs (Phase 9 / thermal spacing). 0 = no pause.
  [int]$EncoderPauseSeconds = 0,
  [switch]$RestartCameraServerBetweenRuns,
  [switch]$RunDeepCaps,
  [switch]$RunSessionMatrix,
  [switch]$RunHdrDcgRuntime,
  [switch]$RunCaptureLatency,
  [switch]$RunRawHdrExcl,
  [switch]$RunBurstProbe,
  [switch]$RunLogicalPhysical,
  [switch]$RunExhaustive,
  [switch]$ExhaustiveIncludeLogical,
  # Skip regular (<=120fps) matrix; HFR/constrained high-speed attempts only per camera.
  [switch]$ExhaustiveHfrOnly,
  [switch]$RunLegacyCamera1,
  # deep_caps -> session_matrix -> exhaustive (HFR-only) -> encoder (skips legacy Camera1).
  [switch]$RunCoreProbePlan,
  # Same as -RunCoreProbePlan but exhaustive runs full ≤120fps + HFR matrix; legacy Camera1 still skipped.
  [switch]$RunCoreProbePlanFullMatrix,
  [switch]$RunFullSuite,
  # Same as -RunFullSuite but exhaustive phase omits HFR-only (full ≤120fps + HFR matrix; very long).
  [switch]$RunFullSuiteFullMatrix,
  [switch]$NoRestartBetweenPhases,
  # Build debug APK and adb install -r before probe phases (see also -SkipSideload).
  [switch]$Sideload,
  # Only assembleDebug + adb install, then exit.
  [switch]$SideloadOnly,
  # Only gradlew assembleRelease (no adb). Fails if release signing is not configured.
  [switch]$AssembleReleaseOnly,
  # Do not run Gradle; install newest APK already under app/build/outputs/apk/debug/.
  [switch]$SkipGradleBuild,
  # Disable automatic sideload for suite modes (-RunCoreProbePlan*, -RunFullSuite*).
  [switch]$SkipSideload,
  # Only write Phase 9 thermal/battery snapshots (no probe phases).
  [switch]$ThermalSnapshotOnly,
  # Fast sanity path: deep_caps -> session_matrix only (optional sideload); writes suite_run_summary_smoke_*.txt
  [switch]$RunProbeSmoke,
  # With -RunProbeSmoke: also write Phase 9 thermal bundle (label smoke) after probes.
  [switch]$SmokeIncludeThermal,
  # Run scripts/pns_verify_toolchain.ps1 (assembleDebug + script parse + UTF-8); no device required. Exits process.
  [switch]$VerifyToolchain,
  # adb pull getExternalFilesDir(null)/calibration/* -> <OutDir>/calibration/ (JSON + optional .cube sidecars). No Gradle.
  [switch]$PullCalibration,
  # Write perf-runs/perf_<stamp>.md: host budgets + optional device `am start -W`, `dumpsys meminfo`, `PNS.Reader` grep (PERFORMANCE_BUDGETS.md).
  [switch]$PerfReport,
  # Device serial for `-PerfReport` adb steps. Omit to use scripts/pns_adb_device.env (`PNS_ADB_SERIAL`) or a single online device.
  [string]$Serial = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

if ($VerifyToolchain.IsPresent) {
  $v = Join-Path $PSScriptRoot "pns_verify_toolchain.ps1"
  if (-not (Test-Path -LiteralPath $v)) { throw "Missing verifier script: $v" }
  & $v -ProjectRoot $ProjectRoot
  exit $LASTEXITCODE
}

if ($PerfReport.IsPresent) {
  function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
      $t = $line.Trim()
      if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
      $eq = $t.IndexOf("=")
      if ($eq -lt 1) { continue }
      $k = $t.Substring(0, $eq).Trim()
      $v = $t.Substring($eq + 1).Trim()
      if ($k -eq "PNS_ADB_SERIAL") { return $v }
    }
    return $null
  }

  function Get-AdbOnlineSerials {
    $ids = @()
    foreach ($line in (& adb devices 2>$null)) {
      if ($line -match '^(\S+)\s+device\s*$') { $ids += $Matches[1] }
    }
    return @($ids)
  }

  function Invoke-AdbPerf([string]$Ser, [string[]]$CmdArgs) {
    if ($Ser) { & adb -s $Ser @CmdArgs }
    else { & adb @CmdArgs }
  }

  New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
  $stamp = Get-Date -Format "yyyyMMdd_HHmmss"
  $perfDir = Join-Path $ProjectRoot "perf-runs"
  New-Item -ItemType Directory -Force -Path $perfDir | Out-Null
  $outFile = Join-Path $perfDir "perf_$stamp.md"
  # Pinned to PerfBudget.Defaults / PERFORMANCE_BUDGETS.md (cold start + PSS after launch).
  $coldStartBudgetMs = 800
  $pssBudgetMb = 180

  $sb = New-Object System.Text.StringBuilder
  [void]$sb.AppendLine("# Point & Shoot perf report")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("- Generated: $(Get-Date -Format 'o')")
  [void]$sb.AppendLine("- OutDir: $OutDir")
  [void]$sb.AppendLine("- Budgets (see ``PerfBudget.Defaults`` / PERFORMANCE_BUDGETS.md): cold start <= **$coldStartBudgetMs** ms; post-launch PSS <= **$pssBudgetMb** MB")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Host")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("| Metric | Budget | This run |")
  [void]$sb.AppendLine("|--------|--------|----------|")

  $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
  if (-not $adbCmd) {
    [void]$sb.AppendLine("| adb | (on PATH) | **not found**  -  device section skipped |")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("Install Android platform-tools or add ``adb`` to PATH to record device rows.")
  }
  else {
    $wantSerial = $Serial
    if ([string]::IsNullOrWhiteSpace($wantSerial)) {
      $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
      if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $wantSerial = $fromEnv
        Write-Host "`[perf] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $wantSerial"
      }
    }
    if ($wantSerial -match '^\d+\.\d+\.\d+\.\d+:\d+$') {
      Write-Host "`[perf] adb connect $wantSerial (TCP/IP)"
      & adb connect $wantSerial 2>&1 | Out-Null
    }

    $online = @(Get-AdbOnlineSerials)
    $resolvedSerial = $null
    if (-not [string]::IsNullOrWhiteSpace($wantSerial)) {
      if ($online -contains $wantSerial) {
        $resolvedSerial = $wantSerial
      }
      elseif ($online.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace($online[0])) {
        Write-Host "`[perf] WARN: serial '$wantSerial' not in device list; using $($online[0])"
        $resolvedSerial = $online[0]
      }
      else {
        $list = if ($online.Count -gt 0) { $online -join ', ' } else { "(none)" }
        [void]$sb.AppendLine("| device | serial online | **skipped**  -  '$wantSerial' not among: $list |")
      }
    }
    else {
      if ($online.Count -gt 1) {
        [void]$sb.AppendLine("| device | single resolve | **skipped**  -  $($online.Count) devices ($($online -join ', ')); set PNS_ADB_SERIAL or -Serial |")
      }
      elseif ($online.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace($online[0])) {
        $resolvedSerial = $online[0]
      }
      else {
        [void]$sb.AppendLine("| device | authorized | **skipped**  -  no device in state ``device`` |")
      }
    }

    if ($null -ne $resolvedSerial -and -not [string]::IsNullOrWhiteSpace($resolvedSerial)) {
      $pkg = "dev.pointandshoot"
      $startCmp = "$pkg/.MainActivity"
      try {
        Invoke-AdbPerf $resolvedSerial @("shell", "am", "force-stop", $pkg) 2>&1 | Out-Null
        $wOut = Invoke-AdbPerf $resolvedSerial @("shell", "am", "start", "-W", "-n", $startCmp, "--es", "pns_screen", "preview") 2>&1 | Out-String
        $totalMs = $null
        $mTot = [regex]::Match($wOut, '(?m)^TotalTime:\s*(\d+)')
        if ($mTot.Success) { $totalMs = [int]$mTot.Groups[1].Value }
        $coldCell = if ($null -ne $totalMs) { "$totalMs ms" } else { "(unparsed)" }
        $coldGrade = ""
        if ($null -ne $totalMs) {
          if ($totalMs -le $coldStartBudgetMs) { $coldGrade = " OK" }
          elseif ($totalMs -le [int]($coldStartBudgetMs * 1.25)) { $coldGrade = " WARN" }
          else { $coldGrade = " FAIL" }
        }
        [void]$sb.AppendLine("| Cold start ``am start -W`` | ${coldStartBudgetMs} ms | $coldCell$coldGrade |")

        $memOut = Invoke-AdbPerf $resolvedSerial @("shell", "dumpsys", "meminfo", $pkg) 2>&1 | Out-String
        $pssKb = $null
        foreach ($pat in @('TOTAL PSS:\s*(\d+)', '(?m)^\s*TOTAL\s+(\d+)\s')) {
          $mm = [regex]::Match($memOut, $pat)
          if ($mm.Success) { $pssKb = [long]$mm.Groups[1].Value; break }
        }
        $pssCell = if ($null -ne $pssKb) {
          $mb = [math]::Round($pssKb / 1024.0, 1)
          $g = if ($mb -le $pssBudgetMb) { " OK" } elseif ($mb -le ($pssBudgetMb * 1.15)) { " WARN" } else { " FAIL" }
          "$mb MB ($pssKb KB)$g"
        }
        else { "(unparsed TOTAL PSS)" }
        [void]$sb.AppendLine("| Process PSS (post-launch) | <= $pssBudgetMb MB | $pssCell |")

        $logTail = Invoke-AdbPerf $resolvedSerial @("shell", "logcat", "-d", "-t", "12000") 2>&1 | Out-String
        $dropMatches = [regex]::Matches($logTail, 'PNS\.Reader.*drop oldest')
        $nDrop = $dropMatches.Count
        [void]$sb.AppendLine("| ``PNS.Reader`` ``drop oldest`` lines (log tail) | informational | count=$nDrop |")
        [void]$sb.AppendLine("")
        [void]$sb.AppendLine("### Device detail")
        [void]$sb.AppendLine("")
        [void]$sb.AppendLine("- serial: ``$resolvedSerial``")
        [void]$sb.AppendLine("")
        [void]$sb.AppendLine("``````")
        [void]$sb.AppendLine(($wOut.TrimEnd() -split "`n" | Select-Object -First 25) -join "`n")
        [void]$sb.AppendLine("``````")
      }
      catch {
        [void]$sb.AppendLine("| device capture | | **error:** $($_.Exception.Message) |")
      }
    }
  }

  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("## Perfetto / frame-jank baselines")
  [void]$sb.AppendLine("")
  [void]$sb.AppendLine("Optional: run ``scripts/pns_capture_perfetto_light.ps1`` (device ``/system/bin/perfetto`` light mode; many OEMs need ``adb root`` for the profiling output path) and keep the pulled ``perf-runs/perfetto_*.perfetto-trace`` next to this file. Alternatively use Android Studio Profiler or the desktop ``perfetto`` CLI with a checked-in pbtxt config (PERFORMANCE_BUDGETS.md).")
  [void]$sb.AppendLine("")

  Set-Content -LiteralPath $outFile -Value $sb.ToString() -Encoding utf8
  Write-Host "`[perf] Wrote $outFile"
  exit 0
}

if ($PullCalibration.IsPresent) {
  $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
  if (-not $adbCmd) { throw "Required command not found: adb" }
  New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
  $localCal = Join-Path $OutDir "calibration"
  New-Item -ItemType Directory -Force -Path $localCal | Out-Null
  $remote = "/sdcard/Android/data/dev.pointandshoot/files/calibration"
  Write-Host "`[pull-cal] adb: pulling $remote -> $localCal"
  try { adb wait-for-device | Out-Null } catch {}
  & adb pull $remote $localCal
  if ($LASTEXITCODE -ne 0) {
    Write-Host "`[pull-cal] WARN: adb pull exited $LASTEXITCODE (remote empty/missing, device offline, or permission). Remote=$remote"
    exit 1
  }
  Write-Host "`[pull-cal] OK"
  exit 0
}

function Require-Cmd([string]$name) {
  $cmd = Get-Command $name -ErrorAction SilentlyContinue
  if (-not $cmd) { throw "Required command not found: $name" }
}

Require-Cmd adb

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

function Grant-CameraPermission {
  # Headless / CI runs: avoid blocking on the runtime permission dialog.
  try {
    adb shell pm grant dev.pointandshoot android.permission.CAMERA 2>$null | Out-Null
  } catch {}
}

function Install-PnsDebugApk {
  param(
    [Parameter(Mandatory = $true)][string]$Root,
    [switch]$SkipBuild
  )
  Write-Host "`[sideload] Project root: $Root"
  $gradlew = Join-Path $Root "gradlew.bat"
  if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "gradlew.bat not found at $gradlew (set -ProjectRoot to the point-and-shoot repo root)"
  }
  if (-not $SkipBuild.IsPresent) {
    Write-Host "`[sideload] Running assembleDebug..."
    Push-Location $Root
    try {
      & $gradlew assembleDebug --no-daemon
      if ($LASTEXITCODE -ne 0) { throw "Gradle assembleDebug failed (exit $LASTEXITCODE)" }
    } finally {
      Pop-Location
    }
  }
  $apkDir = Join-Path $Root "app\build\outputs\apk\debug"
  if (-not (Test-Path -LiteralPath $apkDir)) {
    throw "APK directory missing: $apkDir (run a full build or omit -SkipGradleBuild)"
  }
  $apk = @(Get-ChildItem -LiteralPath $apkDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)[0]
  if (-not $apk) {
    throw "No .apk under $apkDir"
  }
  Write-Host "`[sideload] Installing $($apk.Name) ($([int]($apk.Length / 1024)) KB)..."
  try { adb wait-for-device | Out-Null } catch {}
  $installOut = & adb install -r -t $apk.FullName 2>&1
  $installOut | ForEach-Object { Write-Host $_ }
  if ($LASTEXITCODE -ne 0) {
    throw "adb install failed (exit $LASTEXITCODE). Try: adb devices"
  }
  Write-Host "`[sideload] Install OK."
  Grant-CameraPermission
}

if ($SideloadOnly.IsPresent) {
  Install-PnsDebugApk -Root $ProjectRoot -SkipBuild:$SkipGradleBuild.IsPresent
  exit 0
}

if ($AssembleReleaseOnly.IsPresent) {
  $gradlew = Join-Path $ProjectRoot "gradlew.bat"
  if (-not (Test-Path -LiteralPath $gradlew)) {
    throw "gradlew.bat not found at $gradlew"
  }
  Push-Location $ProjectRoot
  try {
    & $gradlew assembleRelease --no-daemon
    exit $LASTEXITCODE
  } finally {
    Pop-Location
  }
}

function Wait-SignalLogFile {
  param(
    [string]$OutFile,
    [string]$DoneSubstring,
    [int]$TimeoutMinutes,
    [string]$PhaseTag
  )
  $deadline = (Get-Date).AddMinutes($TimeoutMinutes)
  $nextProg = (Get-Date).AddSeconds($ProgressIntervalSeconds)
  $lastLineCount = -1
  $stallCycles = 0
  while ((Get-Date) -lt $deadline) {
    Start-Sleep -Milliseconds 400
    if (-not (Test-Path -LiteralPath $OutFile)) { continue }
    if (Select-String -LiteralPath $OutFile -Pattern $DoneSubstring -SimpleMatch -Quiet) {
      return $true
    }
    if ((Get-Date) -ge $nextProg) {
      $lines = @(Get-Content -LiteralPath $OutFile -ErrorAction SilentlyContinue)
      $n = $lines.Count
      $last = "(empty)"
      if ($n -gt 0) {
        $raw = [string]$lines[$n - 1]
        $take = [Math]::Min(140, $raw.Length)
        $last = $raw.Substring(0, $take)
      }
      Write-Host "`[$PhaseTag] progress: signal_lines=$n last=$last"
      if ($lastLineCount -ge 0 -and $n -eq $lastLineCount) {
        $stallCycles++
        $stallSec = $stallCycles * $ProgressIntervalSeconds
        Write-Host "`[$PhaseTag] WARN: no new lines for ~${stallSec}s (possible hang or very slow HAL)"
      } else {
        $stallCycles = 0
      }
      $lastLineCount = $n
      $nextProg = (Get-Date).AddSeconds($ProgressIntervalSeconds)
    }
  }
  Write-Host "`[$PhaseTag] TIMEOUT: no '$DoneSubstring' within ${TimeoutMinutes}m"
  return $false
}

# Best-effort: if the device supports adbd root, enable it for this session.
# (Even when it fails, it's harmless; and it makes cameraserver restarts fully automatic.)
try { adb root | Out-Null } catch {}
try { adb wait-for-device | Out-Null } catch {}
Start-Sleep -Milliseconds 800
Grant-CameraPermission

function Test-AdbRoot() {
  try { adb wait-for-device | Out-Null } catch {}
  for ($i = 0; $i -lt 12; $i++) {
    try {
      $id = (adb shell id 2>$null)
      if ($id) { $id = $id.Trim() }
      if ($id -match "uid=0\\(root\\)") { return $true }
    } catch {
      Start-Sleep -Milliseconds 250
    }
    Start-Sleep -Milliseconds 250
  }
  # Some userdebug builds expose su even when adbd isn't root.
  try {
    $sid = (adb shell "su 0 id" 2>$null)
    if ($sid) { $sid = $sid.Trim() }
    return ($sid -match "uid=0\\(root\\)")
  } catch {
    return $false
  }
}

function Adb-ShellRoot([string]$cmd) {
  # Best-effort root. If adbd isn't root and su isn't present, this may fail.
  $id = ""
  try { $id = (adb shell id 2>$null) } catch {}
  if ($id -match "uid=0\\(root\\)") {
    adb shell $cmd
    return
  }
  adb shell "su 0 sh -c `"$cmd`"" 2>$null
}

function Wait-Prop([string]$name, [string]$expectedRegex, [int]$timeoutSec = 20) {
  $deadline = (Get-Date).AddSeconds($timeoutSec)
  while ((Get-Date) -lt $deadline) {
    $v = (Adb-ShellRoot "getprop $name" 2>$null).Trim()
    if ($v -match $expectedRegex) { return $true }
    Start-Sleep -Milliseconds 300
  }
  return $false
}

function Phase-Restart-CameraServer() {
  if ($NoRestartBetweenPhases.IsPresent) { return }
  Restart-CameraServer
  Start-Sleep -Seconds 2
}

function Clear-RemoteLegacyCamera1Json {
  # Ensures ls -t pulls the file from this run, not an older timestamp left on device.
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/legacy_camera1_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteEncProbeJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/enc_probe_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteCaptureLatencyJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/capture_latency_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteRawHdrExclJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/raw_hdr_exclusivity_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteExhaustiveProbeJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/exhaustive_probe_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteDeepCapsJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/deep_caps_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteSessionMatrixJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/session_matrix_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteHdrDcgSessionJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/hdr_dcg_session_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteBurstProbeJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/burst_probe_*.json'" 2>$null | Out-Null
  } catch {}
}

function Clear-RemoteLogicalPhysicalJson {
  try {
    adb shell "sh -c 'rm -f /sdcard/Android/data/dev.pointandshoot/files/logical_physical_*.json'" 2>$null | Out-Null
  } catch {}
}

function Write-Phase9ThermalArtifacts {
  param(
    [Parameter(Mandatory = $true)][string]$OutDir,
    [Parameter(Mandatory = $true)][string]$SuiteLabel
  )
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $prefix = Join-Path $OutDir "phase9_thermal_${SuiteLabel}_$ts"
  $utf8 = New-Object System.Text.UTF8Encoding $false
  Write-Host "`[phase9] Thermal / battery snapshots (Phase 9, label=$SuiteLabel)..."
  try {
    $lines = adb shell dumpsys thermalservice 2>$null
    if ($lines) {
      $text = if ($lines -is [array]) { $lines -join "`n" } else { [string]$lines }
      [System.IO.File]::WriteAllText("${prefix}_thermalservice.txt", $text, $utf8)
    }
  } catch {}
  try {
    $lines = adb shell dumpsys battery 2>$null
    if ($lines) {
      $text = if ($lines -is [array]) { $lines -join "`n" } else { [string]$lines }
      [System.IO.File]::WriteAllText("${prefix}_battery.txt", $text, $utf8)
    }
  } catch {}
  try {
    $lines = adb shell getprop 2>$null | Select-String -Pattern "thermal|temp" -CaseSensitive:$false
    if ($lines) {
      [System.IO.File]::WriteAllText("${prefix}_getprop_thermal.txt", ($lines | Out-String).Trim(), $utf8)
    }
  } catch {}
  try {
    # sysfs (works when adbd is root or readable zones)
    $tzTemp = adb shell 'cat /sys/class/thermal/thermal_zone*/temp 2>/dev/null'
    if ($tzTemp) {
      $txt = if ($tzTemp -is [array]) { $tzTemp -join "`n" } else { [string]$tzTemp }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_thermal_zone_temp.txt", $txt, $utf8)
    }
  } catch {}
  try {
    $tzType = adb shell 'cat /sys/class/thermal/thermal_zone*/type 2>/dev/null'
    if ($tzType) {
      $txt = if ($tzType -is [array]) { $tzType -join "`n" } else { [string]$tzType }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_thermal_zone_type.txt", $txt, $utf8)
    }
  } catch {}
  try {
    # hwmon CPU/GPU/board temps on many kernels (glob order aligns temp*_input with zone names when present)
    $hwTemp = adb shell 'cat /sys/class/hwmon/hwmon*/temp*_input 2>/dev/null'
    if ($hwTemp) {
      $txt = if ($hwTemp -is [array]) { $hwTemp -join "`n" } else { [string]$hwTemp }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_hwmon_temp_input.txt", $txt, $utf8)
    }
  } catch {}
  try {
    $hwName = adb shell 'cat /sys/class/hwmon/hwmon*/name 2>/dev/null'
    if ($hwName) {
      $txt = if ($hwName -is [array]) { $hwName -join "`n" } else { [string]$hwName }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_hwmon_name.txt", $txt, $utf8)
    }
  } catch {}
  try {
    # Per-zone thermal (some vendors symlink here; duplicates class/thermal on many builds)
    $vtTemp = adb shell 'cat /sys/devices/virtual/thermal/thermal_zone*/temp 2>/dev/null'
    if ($vtTemp) {
      $txt = if ($vtTemp -is [array]) { $vtTemp -join "`n" } else { [string]$vtTemp }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_virtual_thermal_zone_temp.txt", $txt, $utf8)
    }
  } catch {}
  try {
    $vtType = adb shell 'cat /sys/devices/virtual/thermal/thermal_zone*/type 2>/dev/null'
    if ($vtType) {
      $txt = if ($vtType -is [array]) { $vtType -join "`n" } else { [string]$vtType }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_virtual_thermal_zone_type.txt", $txt, $utf8)
    }
  } catch {}
  try {
    $cdType = adb shell 'cat /sys/class/thermal/cooling_device*/type 2>/dev/null'
    if ($cdType) {
      $txt = if ($cdType -is [array]) { $cdType -join "`n" } else { [string]$cdType }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_cooling_device_type.txt", $txt, $utf8)
    }
  } catch {}
  try {
    $cdCur = adb shell 'cat /sys/class/thermal/cooling_device*/cur_state 2>/dev/null'
    if ($cdCur) {
      $txt = if ($cdCur -is [array]) { $cdCur -join "`n" } else { [string]$cdCur }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_cooling_device_cur_state.txt", $txt, $utf8)
    }
  } catch {}
  try {
    $cdMax = adb shell 'cat /sys/class/thermal/cooling_device*/max_state 2>/dev/null'
    if ($cdMax) {
      $txt = if ($cdMax -is [array]) { $cdMax -join "`n" } else { [string]$cdMax }
      [System.IO.File]::WriteAllText("${prefix}_sysfs_cooling_device_max_state.txt", $txt, $utf8)
    }
  } catch {}
  Write-Host "`[phase9] Wrote phase9_thermal_${SuiteLabel}_$ts*.txt"
}

function Write-SuiteRunSummary {
  param(
    [Parameter(Mandatory = $true)][string]$OutDir,
    [Parameter(Mandatory = $true)][string]$SuiteLabel,
    [Parameter(Mandatory = $true)][string]$ProjectRoot
  )
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $path = Join-Path $OutDir "suite_run_summary_${SuiteLabel}_$ts.txt"
  $utf8 = New-Object System.Text.UTF8Encoding $false
  $lines = New-Object System.Collections.Generic.List[string]
  [void]$lines.Add("suite=$SuiteLabel")
  [void]$lines.Add(("completedLocal={0}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss")))
  $prevPwd = Get-Location
  try {
    Set-Location -LiteralPath $ProjectRoot
    $head = (& git rev-parse HEAD 2>$null)
    if ($head) { [void]$lines.Add(("gitHead={0}" -f ([string]$head).Trim())) }
    $branch = (& git rev-parse --abbrev-ref HEAD 2>$null)
    if ($branch) { [void]$lines.Add(("gitBranch={0}" -f ([string]$branch).Trim())) }
  } catch {}
  finally {
    Set-Location -LiteralPath $prevPwd.Path
  }
  try {
    [void]$lines.Add(("outDir={0}" -f ((Resolve-Path -LiteralPath $OutDir).Path)))
  } catch {
    [void]$lines.Add("outDir=$OutDir")
  }
  try {
    $serial = (& adb get-serialno 2>$null)
    if ($serial) { [void]$lines.Add(("adbSerial={0}" -f ([string]$serial).Trim())) }
    $model = (& adb shell getprop ro.product.model 2>$null)
    if ($model) { [void]$lines.Add(("deviceModel={0}" -f ([string]$model).Trim())) }
    $rel = (& adb shell getprop ro.build.version.release 2>$null)
    if ($rel) { [void]$lines.Add(("androidRelease={0}" -f ([string]$rel).Trim())) }
    $mfg = (& adb shell getprop ro.product.manufacturer 2>$null)
    if ($mfg) { [void]$lines.Add(("deviceManufacturer={0}" -f ([string]$mfg).Trim())) }
    $abi = (& adb shell getprop ro.product.cpu.abi 2>$null)
    if ($abi) { [void]$lines.Add(("cpuAbi={0}" -f ([string]$abi).Trim())) }
  } catch {}
  [System.IO.File]::WriteAllText($path, ($lines.ToArray() -join "`n"), $utf8)
  Write-Host "`[summary] Wrote $path"
}

function Restart-CameraServer() {
  Write-Host "Restarting cameraserver..."
  # Prefer init ctl.restart when available.
  try { Adb-ShellRoot "setprop ctl.restart cameraserver" | Out-Null } catch {}
  # Wait for it to come back.
  $ok = $false
  try { $ok = (Wait-Prop "init.svc.cameraserver" "running" 25) } catch { $ok = $false }
  if (-not $ok) {
    # Fallback: kill and let init respawn.
    $camPid = ""
    try { $camPid = (Adb-ShellRoot "pidof cameraserver" 2>$null).Trim() } catch {}
    if ($camPid) {
      try { Adb-ShellRoot "kill -9 $camPid" | Out-Null } catch {}
      try { $ok = (Wait-Prop "init.svc.cameraserver" "running" 25) } catch { $ok = $false }
    }
  }
  if ($ok) { Write-Host "cameraserver is running." }
  else { Write-Host "cameraserver restart attempted (status unknown)." }
}

function Run-Once([string]$runTag) {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "hfr_${runTag}_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $camDumpBefore = Join-Path $OutDir "${base}_dumpsys_media_camera_before.txt"
  $camDumpAfter  = Join-Path $OutDir "${base}_dumpsys_media_camera_after.txt"

  Write-Host "`[$runTag] Clearing logcat..."
  adb logcat -c | Out-Null

  Write-Host "`[$runTag] Force-stopping app (clean run)..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission

  Clear-RemoteEncProbeJson

  try { Adb-ShellRoot "dumpsys media.camera" > $camDumpBefore } catch {}

  Write-Host "`[$runTag] Launching encoder probe..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen enc --ez pns_autoenc true | Out-Null

  Write-Host "`[$runTag] Capturing until ENC_PROBE_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile

  try {
    $deadline = (Get-Date).AddMinutes(6)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 350
      if (Test-Path $outFile) {
        $hit = Select-String -Path $outFile -Pattern "ENC_PROBE_DONE" -SimpleMatch -Quiet
        if ($hit) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }

  try { Adb-ShellRoot "dumpsys media.camera" > $camDumpAfter } catch {}

  Write-Host "`[$runTag] Saved signals: $outFile"
  Write-Host "`[$runTag] Summary:"
  Select-String -Path $outFile -Pattern "ENC_SAMPLE" -SimpleMatch | ForEach-Object { $_.Line }

  Write-Host "`[$runTag] Dumping full logcat (threadtime)..."
  adb logcat -d -v threadtime > $dumpFile
  Write-Host "`[$runTag] Saved logcat: $dumpFile"

  $encJson = Join-Path $OutDir "${base}_enc_probe.json"
  Pull-LatestEncProbe $encJson
  if (Test-Path $encJson) {
    Write-Host "`[$runTag] Saved encoder JSON: $encJson"
  }
}

function Pull-LatestHdrDcgSession([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/hdr_dcg_session_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}

  if (-not $remote) {
    Write-Host "`[hdrdcg] No hdr_dcg_session json found in $remoteDir"
    return
  }

  Write-Host "`[hdrdcg] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestSessionMatrix([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/session_matrix_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}

  if (-not $remote) {
    Write-Host "`[sessionmatrix] No session_matrix json found in $remoteDir"
    return
  }

  Write-Host "`[sessionmatrix] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestDeepCaps([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/deep_caps_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}

  if (-not $remote) {
    Write-Host "`[deepcaps] No deep caps json found in $remoteDir"
    return
  }

  Write-Host "`[deepcaps] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestExhaustiveProbe([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/exhaustive_probe_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}

  if (-not $remote) {
    Write-Host "`[exhaustive] No exhaustive_probe json found in $remoteDir"
    return
  }

  Write-Host "`[exhaustive] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestLegacyCamera1([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/legacy_camera1_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}

  if (-not $remote) {
    Write-Host "`[legacy1] No legacy_camera1 json found in $remoteDir"
    return
  }

  Write-Host "`[legacy1] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestEncProbe([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/enc_probe_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}

  if (-not $remote) {
    Write-Host "`[encjson] No enc probe json found in $remoteDir"
    return
  }

  Write-Host "`[encjson] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Run-DeepCapsOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "deepcaps_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"

  Write-Host "`[deepcaps] Clearing logcat..."
  adb logcat -c | Out-Null

  Write-Host "`[deepcaps] Force-stopping app (clean run)..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission

  Clear-RemoteDeepCapsJson

  Write-Host "`[deepcaps] Launching deep caps..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen deepcaps --ez pns_autodeepcaps true | Out-Null

  Write-Host "`[deepcaps] Capturing until DEEP_CAPS_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile

  try {
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      if (Test-Path $outFile) {
        $hit = Select-String -Path $outFile -Pattern "DEEP_CAPS_DONE" -SimpleMatch -Quiet
        if ($hit) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }

  Write-Host "`[deepcaps] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Write-Host "`[deepcaps] Saved logcat: $dumpFile"

  Pull-LatestDeepCaps $pulledJson
  if (Test-Path $pulledJson) {
    Write-Host "`[deepcaps] Saved JSON: $pulledJson"
  }
}

function Run-SessionMatrixOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "sessionmatrix_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"

  Write-Host "`[sessionmatrix] Clearing logcat..."
  adb logcat -c | Out-Null

  Write-Host "`[sessionmatrix] Force-stopping app (clean run)..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission

  Clear-RemoteSessionMatrixJson

  Write-Host "`[sessionmatrix] Launching session configuration matrix..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen sessionmatrix --ez pns_autosessionmatrix true | Out-Null

  Write-Host "`[sessionmatrix] Capturing until SESSION_MATRIX_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile

  try {
    $deadline = (Get-Date).AddMinutes(8)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      if (Test-Path $outFile) {
        $hit = Select-String -Path $outFile -Pattern "SESSION_MATRIX_DONE" -SimpleMatch -Quiet
        if ($hit) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }

  Write-Host "`[sessionmatrix] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Write-Host "`[sessionmatrix] Saved logcat: $dumpFile"

  Pull-LatestSessionMatrix $pulledJson
  if (Test-Path $pulledJson) {
    Write-Host "`[sessionmatrix] Saved JSON: $pulledJson"
  }
}

function Run-HdrDcgRuntimeOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "hdrdcg_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"

  Write-Host "`[hdrdcg] Clearing logcat..."
  adb logcat -c | Out-Null

  Write-Host "`[hdrdcg] Force-stopping app (clean run)..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission

  Clear-RemoteHdrDcgSessionJson

  Write-Host "`[hdrdcg] Launching HDR / dynamic-range session probe..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen hdrdcg --ez pns_autohdrdcg true | Out-Null

  Write-Host "`[hdrdcg] Capturing until HDR_DCG_SESSION_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile

  try {
    $deadline = (Get-Date).AddMinutes(12)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      if (Test-Path $outFile) {
        $hit = Select-String -Path $outFile -Pattern "HDR_DCG_SESSION_DONE" -SimpleMatch -Quiet
        if ($hit) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }

  Write-Host "`[hdrdcg] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Write-Host "`[hdrdcg] Saved logcat: $dumpFile"

  Pull-LatestHdrDcgSession $pulledJson
  if (Test-Path $pulledJson) {
    Write-Host "`[hdrdcg] Saved JSON: $pulledJson"
  }
}

function Pull-LatestCaptureLatency([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/capture_latency_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}
  if (-not $remote) {
    Write-Host "`[caplat] No capture_latency json found in $remoteDir"
    return
  }
  Write-Host "`[caplat] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestRawHdrExcl([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/raw_hdr_exclusivity_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}
  if (-not $remote) {
    Write-Host "`[rawhdr] No raw_hdr_exclusivity json found in $remoteDir"
    return
  }
  Write-Host "`[rawhdr] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestBurstProbe([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/burst_probe_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}
  if (-not $remote) {
    Write-Host "`[burst] No burst_probe json found in $remoteDir"
    return
  }
  Write-Host "`[burst] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Pull-LatestLogicalPhysical([string]$destPath) {
  $remoteDir = "/sdcard/Android/data/dev.pointandshoot/files"
  $remote = ""
  try {
    $remote = (adb shell "sh -c 'ls -t $remoteDir/logical_physical_*.json 2>/dev/null | head -n 1'").Trim()
  } catch {}
  if (-not $remote) {
    Write-Host "`[logphy] No logical_physical json found in $remoteDir"
    return
  }
  Write-Host "`[logphy] Pulling: $remote"
  adb pull $remote $destPath | Out-Null
}

function Run-LogicalPhysicalOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "logical_physical_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"
  Write-Host "`[logphy] Clearing logcat..."
  adb logcat -c | Out-Null
  Write-Host "`[logphy] Force-stopping app..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission

  Clear-RemoteLogicalPhysicalJson

  Write-Host "`[logphy] Launching logical / physical probe..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen logicalphysical --ez pns_autologicalphysical true | Out-Null
  Write-Host "`[logphy] Capturing until LOGICAL_PHYSICAL_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile
  try {
    $deadline = (Get-Date).AddMinutes(25)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      if (Test-Path $outFile) {
        if (Select-String -Path $outFile -Pattern "LOGICAL_PHYSICAL_DONE" -SimpleMatch -Quiet) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }
  Write-Host "`[logphy] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Pull-LatestLogicalPhysical $pulledJson
  if (Test-Path $pulledJson) { Write-Host "`[logphy] Saved JSON: $pulledJson" }
}

function Run-CaptureLatencyOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "capture_latency_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"
  Write-Host "`[caplat] Clearing logcat..."
  adb logcat -c | Out-Null
  Write-Host "`[caplat] Force-stopping app..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission
  Clear-RemoteCaptureLatencyJson
  Write-Host "`[caplat] Launching capture latency probe..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen capturelatency --ez pns_autocapturelatency true | Out-Null
  Write-Host "`[caplat] Capturing until CAPTURE_LATENCY_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile
  try {
    $deadline = (Get-Date).AddMinutes(18)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      if (Test-Path $outFile) {
        if (Select-String -Path $outFile -Pattern "CAPTURE_LATENCY_DONE" -SimpleMatch -Quiet) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }
  Write-Host "`[caplat] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Pull-LatestCaptureLatency $pulledJson
  if (Test-Path $pulledJson) { Write-Host "`[caplat] Saved JSON: $pulledJson" }
}

function Run-RawHdrExclOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "raw_hdr_excl_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"
  Write-Host "`[rawhdr] Clearing logcat..."
  adb logcat -c | Out-Null
  Write-Host "`[rawhdr] Force-stopping app..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission
  Clear-RemoteRawHdrExclJson
  Write-Host "`[rawhdr] Launching RAW/HDR exclusivity probe..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen rawhdrexcl --ez pns_autorawhdrexcl true | Out-Null
  Write-Host "`[rawhdr] Capturing until RAW_HDR_EXCL_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile
  try {
    $deadline = (Get-Date).AddMinutes(25)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      if (Test-Path $outFile) {
        if (Select-String -Path $outFile -Pattern "RAW_HDR_EXCL_DONE" -SimpleMatch -Quiet) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }
  Write-Host "`[rawhdr] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Pull-LatestRawHdrExcl $pulledJson
  if (Test-Path $pulledJson) { Write-Host "`[rawhdr] Saved JSON: $pulledJson" }
}

function Run-BurstProbeOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "burst_probe_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"
  Write-Host "`[burst] Clearing logcat..."
  adb logcat -c | Out-Null
  Write-Host "`[burst] Force-stopping app..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission
  Clear-RemoteBurstProbeJson
  Write-Host "`[burst] Launching burst probe..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen burst --ez pns_autoburst true | Out-Null
  Write-Host "`[burst] Capturing until BURST_PROBE_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile
  try {
    $deadline = (Get-Date).AddMinutes(20)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 300
      if (Test-Path $outFile) {
        if (Select-String -Path $outFile -Pattern "BURST_PROBE_DONE" -SimpleMatch -Quiet) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }
  Write-Host "`[burst] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Pull-LatestBurstProbe $pulledJson
  if (Test-Path $pulledJson) { Write-Host "`[burst] Saved JSON: $pulledJson" }
}

function Run-ExhaustiveOnce() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "exhaustive_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"

  Write-Host "`[exhaustive] Clearing logcat..."
  adb logcat -c | Out-Null

  Write-Host "`[exhaustive] Force-stopping app (clean run)..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission

  Clear-RemoteExhaustiveProbeJson

  $useHfrOnly = $ExhaustiveHfrOnly.IsPresent -or $script:CorePlanExhaustiveHfrOnly
  if ($script:SuppressExhaustiveHfrOnly) {
    $useHfrOnly = $false
  }

  Write-Host "`[exhaustive] Launching exhaustive media probe..."
  if ($useHfrOnly) {
    Write-Host "`[exhaustive] Mode: HFR-only (regular video matrix skipped)."
  }
  if ($ExhaustiveIncludeLogical.IsPresent -and $useHfrOnly) {
    adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen exhaustive --ez pns_autoexhaustive true --ez pns_include_logical true --ez pns_exhaustive_hfr_only true | Out-Null
  } elseif ($ExhaustiveIncludeLogical.IsPresent) {
    adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen exhaustive --ez pns_autoexhaustive true --ez pns_include_logical true | Out-Null
  } elseif ($useHfrOnly) {
    adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen exhaustive --ez pns_autoexhaustive true --ez pns_exhaustive_hfr_only true | Out-Null
  } else {
    adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen exhaustive --ez pns_autoexhaustive true | Out-Null
  }

  Write-Host "`[exhaustive] Capturing until EXHAUSTIVE_PROBE_DONE (timeout ${ExhaustiveTimeoutMinutes}m, progress every ${ProgressIntervalSeconds}s)..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile

  try {
    $ok = Wait-SignalLogFile -OutFile $outFile -DoneSubstring "EXHAUSTIVE_PROBE_DONE runId=" -TimeoutMinutes $ExhaustiveTimeoutMinutes -PhaseTag "exhaustive"
    if (-not $ok) {
      Write-Host "`[exhaustive] ERROR: probe did not finish in time; check device ANR/camera HAL."
    }
    if (Test-Path -LiteralPath $outFile) {
      $starts = @(Select-String -LiteralPath $outFile -Pattern "EXHAUSTIVE_PROBE_START" -SimpleMatch -ErrorAction SilentlyContinue)
      if ($starts.Count -gt 1) {
        Write-Host "`[exhaustive] WARN: $($starts.Count) x EXHAUSTIVE_PROBE_START in log (expected 1). Update app if you still see this after reinstall."
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }

  Write-Host "`[exhaustive] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Write-Host "`[exhaustive] Saved logcat: $dumpFile"

  Pull-LatestExhaustiveProbe $pulledJson
  if (Test-Path $pulledJson) {
    Write-Host "`[exhaustive] Saved JSON: $pulledJson"
  }
}

function Run-LegacyCamera1Once() {
  $ts = Get-Date -Format "yyyyMMdd_HHmmss"
  $base = "legacy1_$ts"
  $outFile = Join-Path $OutDir "${base}_signals.log"
  $dumpFile = Join-Path $OutDir "${base}_logcat_threadtime.log"
  $pulledJson = Join-Path $OutDir "${base}.json"

  Write-Host "`[legacy1] Clearing logcat..."
  adb logcat -c | Out-Null

  Write-Host "`[legacy1] Force-stopping app (clean run)..."
  adb shell am force-stop dev.pointandshoot | Out-Null
  Grant-CameraPermission

  Clear-RemoteLegacyCamera1Json

  Write-Host "`[legacy1] Launching Camera1 probe..."
  adb shell am start -S -n dev.pointandshoot/.MainActivity --es pns_screen camera1 --ez pns_autolegacy true | Out-Null

  Write-Host "`[legacy1] Capturing until LEGACY_CAM1_DONE..."
  $proc = Start-Process -FilePath adb -ArgumentList @("logcat", "-v", "brief", "-s", "PNS.SWEEP_SIGNAL:I") -NoNewWindow -PassThru -RedirectStandardOutput $outFile

  try {
    $deadline = (Get-Date).AddMinutes(6)
    while ((Get-Date) -lt $deadline) {
      Start-Sleep -Milliseconds 350
      if (Test-Path $outFile) {
        $hit = Select-String -Path $outFile -Pattern "LEGACY_CAM1_DONE" -SimpleMatch -Quiet
        if ($hit) { break }
      }
    }
  } finally {
    if (-not $proc.HasExited) { Stop-Process -Id $proc.Id -Force }
  }

  Write-Host "`[legacy1] Saved signals: $outFile"
  adb logcat -d -v threadtime > $dumpFile
  Write-Host "`[legacy1] Saved logcat: $dumpFile"

  Pull-LatestLegacyCamera1 $pulledJson
  if (Test-Path $pulledJson) {
    Write-Host "`[legacy1] Saved JSON: $pulledJson"
  }
}

$script:IsRoot = (Test-AdbRoot)
Write-Host "ADB root available: $script:IsRoot"

$script:SuppressExhaustiveHfrOnly = $false
# When true, Run-ExhaustiveOnce launches --ez pns_exhaustive_hfr_only true (core plan default; not full-matrix).
$script:CorePlanExhaustiveHfrOnly = $false

if ($ThermalSnapshotOnly.IsPresent) {
  Write-Phase9ThermalArtifacts -OutDir $OutDir -SuiteLabel "standalone"
  exit 0
}

if ($RunProbeSmoke.IsPresent) {
  Write-Host "=== Smoke probe: deep_caps -> session_matrix (no encoder / exhaustive) ==="
  if (-not $SkipSideload.IsPresent) {
    Install-PnsDebugApk -Root $ProjectRoot -SkipBuild:$SkipGradleBuild.IsPresent
  }
  Run-DeepCapsOnce
  Phase-Restart-CameraServer
  Run-SessionMatrixOnce
  Write-SuiteRunSummary -OutDir $OutDir -SuiteLabel "smoke" -ProjectRoot $ProjectRoot
  if ($SmokeIncludeThermal.IsPresent) {
    Write-Phase9ThermalArtifacts -OutDir $OutDir -SuiteLabel "smoke"
  }
  exit 0
}

# Piecemeal mode (not core/full suite): optional -Sideload before selected phases.
if (-not $RunCoreProbePlan.IsPresent -and -not $RunCoreProbePlanFullMatrix.IsPresent -and -not $RunFullSuite.IsPresent -and -not $RunFullSuiteFullMatrix.IsPresent) {
  if ($Sideload.IsPresent -and -not $SkipSideload.IsPresent) {
    $piecemealWantsRun = $RunDeepCaps.IsPresent -or $RunSessionMatrix.IsPresent -or $RunHdrDcgRuntime.IsPresent -or
      $RunCaptureLatency.IsPresent -or $RunRawHdrExcl.IsPresent -or $RunBurstProbe.IsPresent -or
      $RunLogicalPhysical.IsPresent -or $RunLegacyCamera1.IsPresent -or $RunExhaustive.IsPresent -or $MaxRuns -gt 0
    if ($piecemealWantsRun) {
      Install-PnsDebugApk -Root $ProjectRoot -SkipBuild:$SkipGradleBuild.IsPresent
    }
  }
}

if ($RunCoreProbePlan.IsPresent -or $RunCoreProbePlanFullMatrix.IsPresent) {
  $script:SuppressExhaustiveHfrOnly = $RunCoreProbePlanFullMatrix.IsPresent
  $script:CorePlanExhaustiveHfrOnly = -not $RunCoreProbePlanFullMatrix.IsPresent
  if ($RunCoreProbePlanFullMatrix.IsPresent) {
    Write-Host "=== Core probe plan (FULL exhaustive matrix): deep_caps -> session_matrix -> hdr_dcg -> capture_latency -> raw_hdr -> burst -> logical_physical -> exhaustive FULL (${ExhaustiveTimeoutMinutes}m) -> encoder (MaxRuns=$MaxRuns) ==="
  } else {
    Write-Host "=== Core probe plan: deep_caps -> session_matrix -> hdr_dcg -> capture_latency -> raw_hdr -> burst -> logical_physical -> exhaustive (${ExhaustiveTimeoutMinutes}m) -> encoder (MaxRuns=$MaxRuns) ==="
  }
  if (-not $SkipSideload.IsPresent) {
    Install-PnsDebugApk -Root $ProjectRoot -SkipBuild:$SkipGradleBuild.IsPresent
  }
  Run-DeepCapsOnce
  Phase-Restart-CameraServer
  Run-SessionMatrixOnce
  Phase-Restart-CameraServer
  Run-HdrDcgRuntimeOnce
  Phase-Restart-CameraServer
  Run-CaptureLatencyOnce
  Phase-Restart-CameraServer
  Run-RawHdrExclOnce
  Phase-Restart-CameraServer
  Run-BurstProbeOnce
  Phase-Restart-CameraServer
  Run-LogicalPhysicalOnce
  Phase-Restart-CameraServer
  Run-ExhaustiveOnce
  Phase-Restart-CameraServer
  $script:SuppressExhaustiveHfrOnly = $false
  for ($i = 1; $i -le $MaxRuns; $i++) {
    if ($i -gt 1 -and $RestartCameraServerBetweenRuns.IsPresent) {
      Restart-CameraServer
    }
    Run-Once ("run$i")
    if ($i -lt $MaxRuns -and $EncoderPauseSeconds -gt 0) {
      Write-Host "`[encoder] Pausing ${EncoderPauseSeconds}s before next run (thermal / sustained load spacing)..."
      Start-Sleep -Seconds $EncoderPauseSeconds
    }
  }
  $coreThermalLabel = if ($RunCoreProbePlanFullMatrix.IsPresent) { "core_matrix" } else { "core" }
  Write-Phase9ThermalArtifacts -OutDir $OutDir -SuiteLabel $coreThermalLabel
  Write-SuiteRunSummary -OutDir $OutDir -SuiteLabel $coreThermalLabel -ProjectRoot $ProjectRoot
  $script:CorePlanExhaustiveHfrOnly = $false
} elseif ($RunFullSuite.IsPresent -or $RunFullSuiteFullMatrix.IsPresent) {
  if ($RunFullSuiteFullMatrix.IsPresent) {
    Write-Host "=== Full probe suite (FULL exhaustive matrix; omit -ExhaustiveHfrOnly for device): deep_caps -> session_matrix -> hdr_dcg -> cap_lat -> raw_hdr -> burst -> logical_physical -> legacy1 -> exhaustive FULL (${ExhaustiveTimeoutMinutes}m) -> encoder (MaxRuns=$MaxRuns) ==="
    $script:SuppressExhaustiveHfrOnly = $true
  } else {
    Write-Host "=== Full probe suite: deep_caps -> session_matrix -> hdr_dcg -> cap_lat -> raw_hdr -> burst -> logical_physical -> legacy1 -> exhaustive (${ExhaustiveTimeoutMinutes}m) -> encoder (MaxRuns=$MaxRuns) ==="
  }
  if (-not $SkipSideload.IsPresent) {
    Install-PnsDebugApk -Root $ProjectRoot -SkipBuild:$SkipGradleBuild.IsPresent
  }
  Run-DeepCapsOnce
  Phase-Restart-CameraServer
  Run-SessionMatrixOnce
  Phase-Restart-CameraServer
  Run-HdrDcgRuntimeOnce
  Phase-Restart-CameraServer
  Run-CaptureLatencyOnce
  Phase-Restart-CameraServer
  Run-RawHdrExclOnce
  Phase-Restart-CameraServer
  Run-BurstProbeOnce
  Phase-Restart-CameraServer
  Run-LogicalPhysicalOnce
  Phase-Restart-CameraServer
  Run-LegacyCamera1Once
  Phase-Restart-CameraServer
  Run-ExhaustiveOnce
  Phase-Restart-CameraServer
  $script:SuppressExhaustiveHfrOnly = $false
  for ($i = 1; $i -le $MaxRuns; $i++) {
    if ($i -gt 1 -and $RestartCameraServerBetweenRuns.IsPresent) {
      Restart-CameraServer
    }
    Run-Once ("run$i")
    if ($i -lt $MaxRuns -and $EncoderPauseSeconds -gt 0) {
      Write-Host "`[encoder] Pausing ${EncoderPauseSeconds}s before next run (thermal / sustained load spacing)..."
      Start-Sleep -Seconds $EncoderPauseSeconds
    }
  }
  $label = if ($RunFullSuiteFullMatrix.IsPresent) { "full_matrix" } else { "full" }
  Write-Phase9ThermalArtifacts -OutDir $OutDir -SuiteLabel $label
  Write-SuiteRunSummary -OutDir $OutDir -SuiteLabel $label -ProjectRoot $ProjectRoot
} else {
  if ($RunDeepCaps.IsPresent) {
    Run-DeepCapsOnce
  }

  if ($RunSessionMatrix.IsPresent) {
    Run-SessionMatrixOnce
  }

  if ($RunHdrDcgRuntime.IsPresent) {
    Run-HdrDcgRuntimeOnce
  }

  if ($RunCaptureLatency.IsPresent) {
    Run-CaptureLatencyOnce
  }

  if ($RunRawHdrExcl.IsPresent) {
    Run-RawHdrExclOnce
  }

  if ($RunBurstProbe.IsPresent) {
    Run-BurstProbeOnce
  }

  if ($RunLogicalPhysical.IsPresent) {
    Run-LogicalPhysicalOnce
  }

  if ($RunLegacyCamera1.IsPresent) {
    Run-LegacyCamera1Once
  }

  if ($RunExhaustive.IsPresent) {
    Run-ExhaustiveOnce
  }

  for ($i = 1; $i -le $MaxRuns; $i++) {
    if ($i -gt 1 -and $RestartCameraServerBetweenRuns.IsPresent) {
      Restart-CameraServer
    }
    Run-Once ("run$i")
    if ($i -lt $MaxRuns -and $EncoderPauseSeconds -gt 0) {
      Write-Host "`[encoder] Pausing ${EncoderPauseSeconds}s before next run (thermal / sustained load spacing)..."
      Start-Sleep -Seconds $EncoderPauseSeconds
    }
  }
}

