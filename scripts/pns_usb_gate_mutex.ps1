param(
    [string]$Serial = "",
    [string]$GateName = "",
    [int]$StaleMinutes = 45,
    [switch]$Release,
    [switch]$Force,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_usb_gate_mutex.ps1

Prevents overlapping USB gates (M24, capture verify, chrome UX) on one serial.

Acquire (default):
  pns_usb_gate_mutex.ps1 -GateName m24_gate

Release:
  pns_usb_gate_mutex.ps1 -Release -GateName m24_gate

Options:
  -Serial         ADB serial (default: scripts/pns_adb_device.env)
  -GateName       Gate id for lock file name
  -StaleMinutes   Treat lock older than N minutes as stale (default 45)
  -Force          Steal stale lock
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
$lockRoot = Join-Path $repoRoot "hfr-runs\.usb_gate_locks"
New-Item -ItemType Directory -Force -Path $lockRoot | Out-Null

if (-not $Serial) {
    $envFile = Join-Path $repoRoot "scripts\pns_adb_device.env"
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') {
                $Serial = $Matches[1].Trim().Trim('"')
                break
            }
        }
    }
}
if (-not $Serial) { $Serial = "default" }
$safeSerial = ($Serial -replace '[^\w\.\-]', '_')
$lockPath = Join-Path $lockRoot "$safeSerial.lock"

if ($Release) {
    if (Test-Path -LiteralPath $lockPath) {
        $existing = Get-Content -LiteralPath $lockPath -Raw -ErrorAction SilentlyContinue
        if ($Force -or -not $GateName -or ($existing -match [regex]::Escape($GateName))) {
            Remove-Item -LiteralPath $lockPath -Force -ErrorAction SilentlyContinue
            Write-Host "[usb_gate_mutex] released serial=$Serial gate=$GateName"
        }
    }
    exit 0
}

if (-not $GateName) {
    Write-Error "-GateName is required unless -Release"
    exit 2
}

if (Test-Path -LiteralPath $lockPath) {
    $meta = Get-Content -LiteralPath $lockPath -Raw
    $ageMin = ((Get-Date) - (Get-Item -LiteralPath $lockPath).LastWriteTime).TotalMinutes
    if ($ageMin -lt $StaleMinutes -and -not $Force) {
        Write-Error "[usb_gate_mutex] BLOCKED serial=$Serial existingLock=`n$meta"
        exit 3
    }
    Write-Warning "[usb_gate_mutex] stale lock (${ageMin}m) replaced by $GateName"
}

@(
    "gate=$GateName",
    "serial=$Serial",
    "pid=$PID",
    "startedUtc=$([DateTime]::UtcNow.ToString('o'))"
) | Set-Content -LiteralPath $lockPath -Encoding utf8

Write-Host "[usb_gate_mutex] acquired serial=$Serial gate=$GateName path=$lockPath"
exit 0
