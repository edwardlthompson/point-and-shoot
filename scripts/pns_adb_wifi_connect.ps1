# Pair + connect wireless ADB (Android 11+ wireless debugging).
# Dot-source from device scripts or run standalone before gates.
#
#   .\scripts\pns_adb_wifi_connect.ps1 -PairHostPort 10.0.0.9:38833 -PairCode 123456 -ConnectHostPort 10.0.0.9:42219
#
# Env (scripts/pns_adb_device.env, gitignored):
#   PNS_ADB_SERIAL=10.0.0.9:42219
#   PNS_ADB_PAIR_HOST_PORT=10.0.0.9:38833   # optional when mDNS pairing service visible
#   PNS_ADB_PAIR_CODE=123456

param(
    [string]$PairHostPort = "",
    [string]$PairCode = "",
    [string]$ConnectHostPort = "",
    [switch]$SkipPair,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

$resolveAdb = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdb) {
    . $resolveAdb -PrependToPath -Quiet
}

function Read-EnvVal([string]$Key) {
    $envFile = Join-Path $PSScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        if ($t.Substring(0, $eq).Trim() -eq $Key) { return $t.Substring($eq + 1).Trim() }
    }
    return $null
}

function Write-Info([string]$Msg) {
    if (-not $Quiet) { Write-Host $Msg }
}

if ([string]::IsNullOrWhiteSpace($ConnectHostPort)) {
    $ConnectHostPort = Read-EnvVal "PNS_ADB_SERIAL"
}
if ([string]::IsNullOrWhiteSpace($PairCode)) {
    $PairCode = Read-EnvVal "PNS_ADB_PAIR_CODE"
}
if ([string]::IsNullOrWhiteSpace($PairHostPort)) {
    $PairHostPort = Read-EnvVal "PNS_ADB_PAIR_HOST_PORT"
}

if ([string]::IsNullOrWhiteSpace($ConnectHostPort)) {
    throw "Set -ConnectHostPort or PNS_ADB_SERIAL (host:port) in scripts/pns_adb_device.env"
}

$connectTarget = $ConnectHostPort
if ($connectTarget -notmatch ':\d+$') {
    throw "Connect target must be host:port (e.g. 10.0.0.9:42219), got '$connectTarget'"
}

function Get-MdnsServiceEndpoints {
    $pairing = @{}
    $connect = @{}
    foreach ($line in @(adb mdns services 2>&1)) {
        if ($line -match '^(\S+)\s+(_adb-tls-pairing\._tcp)\s+(\S+:\d+)\s*$') {
            $pairing[$Matches[1]] = $Matches[3]
        }
        elseif ($line -match '^(\S+)\s+(_adb-tls-connect\._tcp)\s+(\S+:\d+)\s*$') {
            $connect[$Matches[1]] = $Matches[3]
        }
    }
    return @{ pairing = $pairing; connect = $connect }
}

function Resolve-MdnsForConnect([string]$Target) {
    $mdns = Get-MdnsServiceEndpoints
    if ($mdns.connect.ContainsValue($Target)) { return $Target }
    if ($Target -match '^(\d+\.\d+\.\d+\.\d+):') {
        $ipPrefix = $Matches[1]
        foreach ($ep in $mdns.connect.Values) {
            if ($ep.StartsWith("${ipPrefix}:")) { return $ep }
        }
    }
    return $Target
}

function Resolve-MdnsPairHostPort([string]$ConnectTarget) {
    $mdns = Get-MdnsServiceEndpoints
    if ($ConnectTarget -match '^(\d+\.\d+\.\d+\.\d+):') {
        $ip = $Matches[1]
        foreach ($kv in $mdns.pairing.GetEnumerator()) {
            if ($kv.Value.StartsWith("${ip}:")) { return $kv.Value }
        }
        foreach ($kv in $mdns.connect.GetEnumerator()) {
            if ($kv.Value.StartsWith("${ip}:") -and $mdns.pairing.ContainsKey($kv.Key)) {
                return $mdns.pairing[$kv.Key]
            }
        }
    }
    return $null
}

function Test-TcpOpen([string]$HostPort, [int]$TimeoutMs = 2500) {
    if ($HostPort -notmatch '^(.+):(\d+)$') { return $false }
    $hostName = $Matches[1]
    $port = [int]$Matches[2]
    try {
        $client = New-Object System.Net.Sockets.TcpClient
        $iar = $client.BeginConnect($hostName, $port, $null, $null)
        $ok = $iar.AsyncWaitHandle.WaitOne($TimeoutMs, $false)
        if ($ok -and $client.Connected) { $client.Close(); return $true }
        $client.Close()
    } catch { }
    return $false
}

function Get-OnlineSerials {
    $serials = @()
    foreach ($line in @(adb devices 2>&1)) {
        if ($line -match '^(\S+)\s+device$') { $serials += $Matches[1] }
    }
    return $serials
}

$resolvedConnect = Resolve-MdnsForConnect $connectTarget
if ($resolvedConnect -ne $connectTarget) {
    Write-Info "[adb_wifi] mDNS connect endpoint -> $resolvedConnect"
    $connectTarget = $resolvedConnect
}

if (-not (Test-TcpOpen $connectTarget)) {
    Write-Warning "[adb_wifi] TCP $connectTarget not reachable (use same Wi-Fi as phone; prefer 10.0.0.x on this LAN)."
}

if (-not $SkipPair -and -not [string]::IsNullOrWhiteSpace($PairCode)) {
    if ([string]::IsNullOrWhiteSpace($PairHostPort)) {
        $PairHostPort = Resolve-MdnsPairHostPort $connectTarget
        if ($PairHostPort) { Write-Info "[adb_wifi] mDNS pairing endpoint -> $PairHostPort" }
    }
    if ([string]::IsNullOrWhiteSpace($PairHostPort)) {
        $PairHostPort = $connectTarget
    }
    Write-Info "[adb_wifi] adb pair $PairHostPort"
    $pairOut = (& adb pair $PairHostPort $PairCode 2>&1 | Out-String).Trim()
    if ($pairOut) { Write-Info $pairOut }
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "[adb_wifi] adb pair exit=$LASTEXITCODE (pairing window may have expired)"
    }
    Start-Sleep -Seconds 1
}

Write-Info "[adb_wifi] adb connect $connectTarget"
$connOut = (& adb connect $connectTarget 2>&1 | Out-String).Trim()
if ($connOut) { Write-Info $connOut }
Start-Sleep -Seconds 2

$online = Get-OnlineSerials
if ($online -contains $connectTarget) {
    Write-Info "[adb_wifi] online: $connectTarget"
    exit 0
}
if ($online.Count -eq 1) {
    Write-Info "[adb_wifi] online (mdns): $($online[0])"
    exit 0
}
if ($online.Count -gt 1) {
    Write-Warning "[adb_wifi] multiple devices: $($online -join ', ')"
    exit 0
}

Write-Warning "[adb_wifi] no authorized device after connect"
exit 2
