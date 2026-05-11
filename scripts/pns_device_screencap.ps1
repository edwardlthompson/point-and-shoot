# Capture a PNG device screenshot via `adb exec-out screencap -p` using cmd.exe redirection.
# PowerShell pipelines break binary PNG output (`Set-Content -AsByteStream` without raw bytes fails on Windows PowerShell 5.x).
#
# Usage:
#   .\scripts\pns_device_screencap.ps1 -OutPath .\docs\screenshots\shot.png
#   .\scripts\pns_device_screencap.ps1 -Serial 8bf09993 -OutPath .\docs\screenshots\shot.png
#
# Serial: omit for default device, or set `scripts/pns_adb_device.env` (`PNS_ADB_SERIAL`).

param(
    [string]$Serial = "",
    [Parameter(Mandatory = $true)]
    [string]$OutPath,
    [switch]$ShowSize
)

$ErrorActionPreference = "Stop"

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) {
        return $null
    }
    foreach ($line in Get-Content -LiteralPath $envFile) {
        $t = $line.Trim()
        if ($t.StartsWith("#") -or $t.Length -eq 0) { continue }
        $eq = $t.IndexOf("=")
        if ($eq -lt 1) { continue }
        $k = $t.Substring(0, $eq).Trim()
        $v = $t.Substring($eq + 1).Trim()
        if ($k -eq "PNS_ADB_SERIAL") {
            return $v
        }
    }
    return $null
}

function Resolve-AdbExe {
    # Join-Path throws if the base path is null  -  ANDROID_HOME / ANDROID_SDK_ROOT are often unset on Windows.
    $candidates = @()
    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
    }
    if ($env:ANDROID_HOME) {
        $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe")
    }
    if ($env:ANDROID_SDK_ROOT) {
        $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe")
    }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) {
            return $c
        }
    }
    return "adb"
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "`[pns_device_screencap] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) {
        & adb -s $Serial @CmdArgs 2>$null
    }
    else {
        & adb @CmdArgs 2>$null
    }
}

if ($Serial -match '^\d+\.\d+\.\d+\.\d+:\d+$') {
    Write-Host "`[pns_device_screencap] adb connect $Serial (TCP/IP)"
    Invoke-AdbIgnore @("connect", $Serial)
}

$adbExe = Resolve-AdbExe
# Avoid Get-Location (some hosts leave ProviderPath empty); CurrentDirectory is stable for relative paths.
if ([System.IO.Path]::IsPathRooted($OutPath)) {
    $resolvedOut = [System.IO.Path]::GetFullPath($OutPath)
}
else {
    $resolvedOut = [System.IO.Path]::GetFullPath(
        [System.IO.Path]::Combine([Environment]::CurrentDirectory, $OutPath))
}
$parent = Split-Path -Parent $resolvedOut
if ($parent -and -not (Test-Path -LiteralPath $parent)) {
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
}

# Stream raw bytes from adb  -  avoids cmd.exe quoting bugs and broken PS pipelines for PNG output.
$argList = New-Object System.Collections.Generic.List[string]
if (-not [string]::IsNullOrWhiteSpace($Serial)) {
    [void]$argList.Add("-s")
    [void]$argList.Add($Serial)
}
[void]$argList.Add("exec-out")
[void]$argList.Add("screencap")
[void]$argList.Add("-p")

$pinfo = New-Object System.Diagnostics.ProcessStartInfo
$pinfo.FileName = $adbExe
$pinfo.Arguments = ($argList | ForEach-Object {
        if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ }
    }) -join " "
$pinfo.RedirectStandardOutput = $true
$pinfo.RedirectStandardError = $true
$pinfo.UseShellExecute = $false
$pinfo.CreateNoWindow = $true

$proc = New-Object System.Diagnostics.Process
$proc.StartInfo = $pinfo
[void]$proc.Start()
$outStream = $proc.StandardOutput.BaseStream
$fileStream = [System.IO.File]::Create($resolvedOut)
try {
    $outStream.CopyTo($fileStream)
}
finally {
    $fileStream.Close()
    $proc.WaitForExit()
}
if ($proc.ExitCode -ne 0) {
    $err = $proc.StandardError.ReadToEnd()
    Write-Error "adb screencap failed (exit $($proc.ExitCode)): $err"
}

if (-not (Test-Path -LiteralPath $resolvedOut)) {
    Write-Error "Expected PNG not written: $resolvedOut"
}

$len = (Get-Item -LiteralPath $resolvedOut).Length
if ($len -lt 512) {
    Write-Error "PNG too small ($len bytes); capture likely failed."
}

Write-Host "`[pns_device_screencap] Wrote $resolvedOut ($len bytes)"
if ($ShowSize.IsPresent) {
    Get-Item -LiteralPath $resolvedOut | Select-Object FullName, Length
}
exit 0
