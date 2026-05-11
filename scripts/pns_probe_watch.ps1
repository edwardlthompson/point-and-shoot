# Streams adb logcat while Point & Shoot probes run; highlights crashes, ANRs, fatals,
# camera HAL errors, and stalls (no PNS.SWEEP_SIGNAL for HangTimeoutSeconds while app is alive).
# Usage (second terminal, while probe runs):
#   .\scripts\pns_probe_watch.ps1
# Optional:
#   .\scripts\pns_probe_watch.ps1 -HangTimeoutSeconds 180 -ClearLogcatFirst -ExitOnFatal

param(
  [string]$OutDir = ".\\hfr-runs",
  [int]$HangTimeoutSeconds = 120,
  [int]$HangResignalSeconds = 45,
  [switch]$ClearLogcatFirst,
  [switch]$ExitOnFatal,
  [string]$Package = "dev.pointandshoot"
)

$ErrorActionPreference = "Stop"

function Require-Cmd([string]$name) {
  $cmd = Get-Command $name -ErrorAction SilentlyContinue
  if (-not $cmd) { throw "Required command not found: $name" }
}

Require-Cmd adb

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$ts = Get-Date -Format "yyyyMMdd_HHmmss"
$rawLog = Join-Path $OutDir "probe_watch_raw_$ts.log"
$evtLog = Join-Path $OutDir "probe_watch_events_$ts.log"

function Write-Evt([string]$severity, [string]$msg) {
  $line = "$(Get-Date -Format o) [$severity] $msg"
  Add-Content -LiteralPath $evtLog -Value $line -Encoding utf8
  switch ($severity) {
    "CRASH" { Write-Host $line -ForegroundColor Red }
    "ERROR" { Write-Host $line -ForegroundColor Red }
    "WARN" { Write-Host $line -ForegroundColor Yellow }
    default { Write-Host $line -ForegroundColor Cyan }
  }
}

Write-Host "probe_watch: raw -> $rawLog"
Write-Host "probe_watch: events -> $evtLog"
Write-Host "probe_watch: hang threshold = ${HangTimeoutSeconds}s (app must be alive); resignal every ${HangResignalSeconds}s"
Write-Evt "INFO" "Started watch package=$Package"

if ($ClearLogcatFirst) {
  try { adb logcat -c | Out-Null } catch {}
  Write-Evt "INFO" "Cleared logcat buffer"
}

# Tuned for Pixel-style tags; broad enough to catch most camera/probe failures.
$reCrashStr =
  '(?i)FATAL EXCEPTION|Fatal signal|DEBUG\s*:\s*signal|tombstone|ANR in\s+' + [regex]::Escape($Package) +
  '|ActivityManager:.*am_anr.*' + [regex]::Escape($Package) +
  '|ActivityManager:.*am_proc_died.*' + [regex]::Escape($Package) +
  '|Process\s+' + [regex]::Escape($Package) + '\s+(has\s+)?died|FORCE CLOSING|App crashed|cameraserver.*\b(died|fatal)\b'
$reCrash = [regex]$reCrashStr
# threadtime format uses " E TagName: message" (not brief "E/Tag")
$reError = [regex]'(?i)\sE\s+(AndroidRuntime|libc|libbinder|CameraService|Camera3-Device|Camera3Device|Camera2|cameraserver|CAMERA|dev\.pointandshoot|PNS\.\S+)\s*:'
$reWatch = [regex]'PNS\.SWEEP_SIGNAL'

$lastSignalUtc = [datetime]::UtcNow
$lastHangUtc = [datetime]::MinValue
$lastPid = ""
$fatalSeen = $false
$lastPidCheckUtc = [datetime]::MinValue
$pidPollSeconds = 2.0
$proc = $null

function Get-AppPid {
  try {
    $p = (adb shell "pidof $Package" 2>$null).Trim()
    if (-not $p) { return "" }
    return $p
  } catch {
    return ""
  }
}

try {
  $psi = New-Object System.Diagnostics.ProcessStartInfo
  $psi.FileName = "adb"
  $psi.Arguments = "logcat -v threadtime"
  $psi.RedirectStandardOutput = $true
  $psi.RedirectStandardError = $true
  $psi.UseShellExecute = $false
  $psi.CreateNoWindow = $true
  $proc = New-Object System.Diagnostics.Process
  $proc.StartInfo = $psi
  [void]$proc.Start()
  $reader = $proc.StandardOutput

  while (-not $reader.EndOfStream) {
    $line = $reader.ReadLine()
    if ($null -eq $line) { break }
    Add-Content -LiteralPath $rawLog -Value $line -Encoding utf8

    if ($reWatch.IsMatch($line)) {
      $lastSignalUtc = [datetime]::UtcNow
    }

    if ($reCrash.IsMatch($line)) {
      Write-Evt "CRASH" $line
      $fatalSeen = $true
      if ($ExitOnFatal) { break }
    } elseif ($reError.IsMatch($line)) {
      Write-Evt "ERROR" $line
    }

    $now = [datetime]::UtcNow
    $pidNow = ""
    if (($now - $lastPidCheckUtc).TotalSeconds -ge $pidPollSeconds) {
      $lastPidCheckUtc = $now
      $pidNow = Get-AppPid
      if ($lastPid -and -not $pidNow) {
        Write-Evt "CRASH" "Process $Package exited (was pid=$lastPid)  -  check tombstone/ANR above"
        $fatalSeen = $true
        if ($ExitOnFatal) { break }
      }
      if ($pidNow) { $lastPid = $pidNow }
    }

    $idle = ($now - $lastSignalUtc).TotalSeconds
    if ($idle -ge $HangTimeoutSeconds) {
      if (-not $pidNow) { $pidNow = Get-AppPid }
      if ($pidNow) {
        if (($now - $lastHangUtc).TotalSeconds -ge $HangResignalSeconds) {
          Write-Evt "WARN" "Possible HANG: no PNS.SWEEP_SIGNAL for $([int]$idle)s; $Package pid=$pidNow (camera session may be stuck)"
          $lastHangUtc = $now
        }
      }
    }
  }
} finally {
  if ($proc -and -not $proc.HasExited) {
    try { $proc.Kill() } catch {}
  }
  Write-Evt "INFO" "Watch ended fatalSeen=$fatalSeen"
}

if ($ExitOnFatal -and $fatalSeen) {
  exit 1
}
exit 0
