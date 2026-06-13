# Shared ADB serial resolution for scripts/pns_adb_device.env (PNS_ADB_SERIAL).
# Dot-source from other scripts: . (Join-Path $PSScriptRoot "pns_adb_serial.ps1")

function Read-PnsAdbSerialFromEnvFile {
    param(
        [string]$ScriptRoot = $PSScriptRoot
    )
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim().Trim('"')
        if ($k -eq "PNS_ADB_SERIAL") { return $v }
    }
    return $null
}

function Resolve-PnsAdbSerial {
    param(
        [string]$Serial = "",
        [string]$ScriptRoot = $PSScriptRoot,
        [string]$LogPrefix = ""
    )
    if (-not [string]::IsNullOrWhiteSpace($Serial)) { return $Serial }
    $fromEnv = Read-PnsAdbSerialFromEnvFile -ScriptRoot $ScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        if ($LogPrefix) {
            Write-Host "[$LogPrefix] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $fromEnv"
        }
        return $fromEnv
    }
    return $null
}
