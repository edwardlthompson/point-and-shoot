<#
.SYNOPSIS
  On a connected device: grant CAMERA, cold-start the engineering hub with auto-export,
  pull the full probe markdown (includes AE / highlight / root / vendor sections).

.DESCRIPTION
  Launches **MainActivity** with **`pns_screen=probehub`** and **`pns_auto_export_probe=true`**.
  The app writes **`files/PROBE_EXPORT_LATEST.md`**. The host pulls it with
  **`adb exec-out run-as dev.pointandshoot cat …`**, which **only works for a debuggable (debug) APK**
  (`app-debug.apk` from **assembleDebug**). Release builds are not supported for this pull path.

  If **`app-debug.apk`** is missing, runs **`pns_gradlew.ps1 :app:assembleDebug`** automatically (unless **-SkipInstall**).
  **-AssembleDebug** forces a Gradle build before install even when the APK already exists.

  When **PNS_ADB_SERIAL** (or **-Serial**) looks like **`host:port`**, runs **`adb connect`** first (Wi‑Fi ADB).

  On success, writes **`ae_highlight_probe_summary.txt`** (short excerpt for agents) and references it from **`ae_highlight_probe.json`** (`summary` field).

  Reads **PNS_ADB_SERIAL** from **scripts/pns_adb_device.env** when **-Serial** is omitted.

.PARAMETER AlsoRootCapabilityAdb
  After a successful pull, runs **pns_root_capability_adb.ps1** (same **-Serial**) into a subfolder
  of **-OutDir** for host-side root transport evidence.

.PARAMETER AssembleDebug
  Run **`pns_gradlew.ps1 :app:assembleDebug`** before install even when **app-debug.apk** already exists.

.PARAMETER PullAttempts
  Max **`run-as`** pull attempts (default **8**).

.PARAMETER PullRetrySec
  Seconds to wait between pull retries after the first attempt (default **2**).

.PARAMETER SkipInstall
  Skip **adb install** (still requires a **debuggable** debug APK on device for **`run-as`**).

.EXAMPLE
  .\scripts\pns_ae_highlight_probe_adb.ps1
  .\scripts\pns_ae_highlight_probe_adb.ps1 -Serial 8bf09993 -WaitSec 12
  .\scripts\pns_ae_highlight_probe_adb.ps1 -AssembleDebug -AlsoRootCapabilityAdb
#>
param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [int]$WaitSec = 10,
    [int]$PullAttempts = 8,
    [int]$PullRetrySec = 2,
    [switch]$SkipInstall,
    [switch]$AssembleDebug,
    [switch]$AlsoRootCapabilityAdb
)

$ErrorActionPreference = "Stop"

$PSScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projRoot = Split-Path -Parent $PSScriptRoot

$resolveAdbForSession = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
if (Test-Path -LiteralPath $resolveAdbForSession) {
    . $resolveAdbForSession -PrependToPath -Quiet
}

$pkg = "dev.pointandshoot"
$activity = "dev.pointandshoot/.MainActivity"
$probeFile = "PROBE_EXPORT_LATEST.md"

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

function Invoke-Adb([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs } else { & adb @CmdArgs }
    if ($LASTEXITCODE -ne 0) { throw "adb $($CmdArgs -join ' ') failed exit=$LASTEXITCODE" }
}

function Invoke-AdbIgnore([string[]]$CmdArgs) {
    if ($Serial) { & adb -s $Serial @CmdArgs 2>$null } else { & adb @CmdArgs 2>$null }
}

function Get-AdbOnlineSerials {
    & adb devices | ForEach-Object {
        if ($_ -match '^(\S+)\s+device\s*$') { [string]$Matches[1] }
    }
}

function Write-AeHighlightProbeSummary([string]$MdPath, [string]$OutSummaryPath) {
    $utf8 = [System.Text.UTF8Encoding]::new($false)
    $text = [System.IO.File]::ReadAllText($MdPath, $utf8)
    $sb = [System.Text.StringBuilder]::new()
    [void]$sb.AppendLine('# AE / highlight probe summary (machine-generated)')
    [void]$sb.AppendLine("# Full export: $MdPath")
    [void]$sb.AppendLine('')
    $lines = $text -split "`r?`n", -1, 'RegexMatch'
    $hdr = [Math]::Min(20, $lines.Length)
    for ($i = 0; $i -lt $hdr; $i++) {
        [void]$sb.AppendLine($lines[$i])
    }
    [void]$sb.AppendLine('')
    $ctx = [regex]::Match($text, '(?ms)^## AE / highlight[^\r\n]*\r?\n(.*?)(?=^\r?\n?## )')
    if ($ctx.Success) {
        [void]$sb.AppendLine('--- ## AE / highlight (device context) ---')
        [void]$sb.AppendLine($ctx.Groups[1].Value.Trim())
        [void]$sb.AppendLine('')
    }
    $camRx = [regex]::new(
        '(?ms)^### AE / highlight[^\r\n]*\(camera (\d+)\)[^\r\n]*\r?\n(.*?)(?=^### |\z)',
        [System.Text.RegularExpressions.RegexOptions]::Multiline
    )
    foreach ($cam in $camRx.Matches($text)) {
        $id = $cam.Groups[1].Value
        $blk = $cam.Groups[2].Value.TrimEnd()
        $bl = $blk -split "`r?`n", -1, 'RegexMatch'
        $take = [Math]::Min(60, $bl.Length)
        [void]$sb.AppendLine("--- Camera $id (AE/highlight block, first $take lines) ---")
        for ($j = 0; $j -lt $take; $j++) {
            [void]$sb.AppendLine($bl[$j])
        }
        if ($bl.Length -gt $take) {
            [void]$sb.AppendLine("… $($bl.Length - $take) more lines in full export …")
        }
        [void]$sb.AppendLine('')
    }
    [System.IO.File]::WriteAllText($OutSummaryPath, $sb.ToString(), $utf8)
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $fromEnv = Read-PnsAdbSerialFromEnvFile $PSScriptRoot
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        $Serial = $fromEnv
        Write-Host "[ae_highlight_probe] PNS_ADB_SERIAL from scripts/pns_adb_device.env -> $Serial"
    }
}

if (-not [string]::IsNullOrWhiteSpace($Serial) -and $Serial -match ':\d+$') {
    Write-Host "[ae_highlight_probe] adb connect $Serial (Wi-Fi style serial)"
    Invoke-AdbIgnore @("connect", $Serial)
    Start-Sleep -Seconds 2
}

try {
    adb wait-for-device 2>$null | Out-Null
}
catch {}

$onlineSerials = @(Get-AdbOnlineSerials)
if ([string]::IsNullOrWhiteSpace($Serial)) {
    if ($onlineSerials.Count -gt 1) {
        throw "Multiple adb devices online ($($onlineSerials -join ', ')). Set PNS_ADB_SERIAL in scripts/pns_adb_device.env or pass -Serial."
    }
}
elseif ($onlineSerials -notcontains $Serial) {
    if ($onlineSerials.Count -eq 1) {
        Write-Host "[ae_highlight_probe] WARN: serial '$Serial' not online; using $($onlineSerials[0])"
        $Serial = $onlineSerials[0]
    }
    elseif ($onlineSerials.Count -eq 0) {
        throw "No adb device in 'device' state."
    }
    else {
        throw "adb serial '$Serial' not online. Connected: $($onlineSerials -join ', ')"
    }
}

if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\ae_highlight_probe_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$apk = Join-Path $projRoot "app\build\outputs\apk\debug\app-debug.apk"
$gradlewHelper = Join-Path $PSScriptRoot "pns_gradlew.ps1"

function Invoke-AssembleDebugIfNeeded {
    param([bool]$Force)
    if ($SkipInstall) { return }
    if (-not $Force -and (Test-Path -LiteralPath $apk)) { return }
    if (-not (Test-Path -LiteralPath $gradlewHelper)) {
        throw "Missing $gradlewHelper; cannot assembleDebug."
    }
    Write-Host "[ae_highlight_probe] $($gradlewHelper) :app:assembleDebug"
    & $gradlewHelper ":app:assembleDebug"
    if ($LASTEXITCODE -ne 0) { throw "assembleDebug failed exit=$LASTEXITCODE" }
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "assembleDebug finished but APK still missing: $apk"
    }
}

if ($AssembleDebug) {
    Invoke-AssembleDebugIfNeeded -Force $true
}
else {
    Invoke-AssembleDebugIfNeeded -Force $false
}

if (-not $SkipInstall) {
    if (-not (Test-Path -LiteralPath $apk)) {
        throw "Missing APK after build attempt: $apk"
    }
    Write-Host "[ae_highlight_probe] adb install -r -t"
    $prevEap = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        if ($Serial) { $installOut = & adb -s $Serial install -r -t $apk 2>&1 }
        else { $installOut = & adb install -r -t $apk 2>&1 }
    }
    finally { $ErrorActionPreference = $prevEap }
    $installOut | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) { throw "adb install failed exit=$LASTEXITCODE" }
}

Write-Host "[ae_highlight_probe] pm grant CAMERA (best-effort)"
Invoke-AdbIgnore @("shell", "pm", "grant", $pkg, "android.permission.CAMERA")

Write-Host "[ae_highlight_probe] force-stop + cold start probehub + auto export"
Invoke-Adb @("shell", "am", "force-stop", $pkg)
$startArgs = @(
    "shell", "am", "start", "-W", "-n", $activity,
    "--activity-clear-task", "-S",
    "--es", "pns_screen", "probehub",
    "--ez", "pns_auto_export_probe", "true"
)
if ($Serial) { & adb -s $Serial @startArgs } else { & adb @startArgs }
if ($LASTEXITCODE -ne 0) { throw "am start failed exit=$LASTEXITCODE" }

Write-Host "[ae_highlight_probe] waiting ${WaitSec}s for probe build..."
Start-Sleep -Seconds $WaitSec

$outMd = Join-Path $OutDir $probeFile
$minBytes = 64
$prevEap = $ErrorActionPreference

function Pull-ProbeMarkdown {
    $ErrorActionPreference = "Continue"
    try {
        if ($Serial) {
            & adb -s $Serial exec-out run-as $pkg cat "files/$probeFile" | Set-Content -LiteralPath $outMd -Encoding utf8
        }
        else {
            & adb exec-out run-as $pkg cat "files/$probeFile" | Set-Content -LiteralPath $outMd -Encoding utf8
        }
    }
    finally {
        $ErrorActionPreference = $prevEap
    }
    if ((Test-Path -LiteralPath $outMd) -and (Get-Item -LiteralPath $outMd).Length -ge $minBytes) {
        return $true
    }
    return $false
}

Write-Host "[ae_highlight_probe] pull via run-as (debuggable APK required) -> $outMd"
$pullOk = $false
for ($a = 1; $a -le [Math]::Max(1, $PullAttempts); $a++) {
    if ($a -gt 1) {
        Write-Host "[ae_highlight_probe] pull retry $a / $PullAttempts (wait ${PullRetrySec}s)"
        Start-Sleep -Seconds $PullRetrySec
    }
    if (Pull-ProbeMarkdown) {
        $pullOk = $true
        break
    }
}

if (-not $pullOk) {
    $failLog = Join-Path $OutDir "logcat_probe_export_tail.txt"
    Write-Warning "[ae_highlight_probe] Pull failed after $PullAttempts attempt(s). Dumping log tail -> $failLog"
    Write-Warning "[ae_highlight_probe] run-as requires a DEBUG build (app-debug.apk). Release builds: install debug APK or use -AssembleDebug."
    $ErrorActionPreference = "Continue"
    try {
        if ($Serial) {
            & adb -s $Serial logcat -d -t 400 "PNS.ProbeExport:I" "PNS.Probe:I" "*:S" 2>&1 | Set-Content -LiteralPath $failLog -Encoding utf8
        }
        else {
            & adb logcat -d -t 400 "PNS.ProbeExport:I" "PNS.Probe:I" "*:S" 2>&1 | Set-Content -LiteralPath $failLog -Encoding utf8
        }
    }
    finally { $ErrorActionPreference = $prevEap }
    throw "Probe export pull failed or file too small (see $failLog)."
}

$summaryPath = Join-Path $OutDir "ae_highlight_probe_summary.txt"
Write-AeHighlightProbeSummary -MdPath $outMd -OutSummaryPath $summaryPath

$meta = [ordered]@{
    schema                   = "pns.ae_highlight_probe_adb.v1"
    timestampUtc             = [DateTime]::UtcNow.ToString("o")
    serial                   = $(if ($Serial) { $Serial } else { "default" })
    outDir                   = $OutDir
    markdown                 = $outMd
    summary                  = $summaryPath
    bytes                    = (Get-Item -LiteralPath $outMd).Length
    pass                     = $true
    requiresDebuggableApk    = $true
    pullViaRunAs             = "adb exec-out run-as $pkg cat files/$probeFile"
}
$meta | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir "ae_highlight_probe.json") -Encoding utf8

Write-Host "[ae_highlight_probe] OK -> $outMd ($((Get-Item -LiteralPath $outMd).Length) bytes)"
Write-Host "[ae_highlight_probe] summary -> $summaryPath"

if ($AlsoRootCapabilityAdb) {
    $sub = Join-Path $OutDir "root_capability_adb"
    New-Item -ItemType Directory -Force -Path $sub | Out-Null
    $rootScript = Join-Path $PSScriptRoot "pns_root_capability_adb.ps1"
    if (-not (Test-Path -LiteralPath $rootScript)) {
        Write-Warning "[ae_highlight_probe] Missing $rootScript; skip AlsoRootCapabilityAdb"
    }
    else {
        if ($Serial) {
            & $rootScript -Serial $Serial -OutDir $sub
        }
        else {
            & $rootScript -OutDir $sub
        }
    }
}
