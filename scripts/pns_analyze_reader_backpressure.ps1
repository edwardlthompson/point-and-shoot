# Sprint 7.3  -  classify **PNS.Reader** `drop oldest` lines from logcat text (host-only).
#
# Feed one or more **`logcat_*.txt`** files from **`pns_adb_preview_validate.ps1`** **`-OutDir`**
# (or any plain text capture that includes **`PNS.Reader`** lines). Emits Markdown tables: counts
# by **`queue=`** and **`channel=`**, plus **`encode_lane_busy`** / encode-lane drain hints.
# **`-LogDir`** skips **`*_app_pid.txt`** so full ring **`logcat_*.txt`** files are not double-counted
# against their pid-filtered siblings.
#
# Example (two scenario logs — use **-Command** so **`@(...)`** binds to **`[string[]]$LogPath`**; **`-File`** is picky about commas):
#   powershell -NoProfile -Command "& .\scripts\pns_analyze_reader_backpressure.ps1 -LogPath @('.\hfr-runs\...\logcat_raw_still_x10.txt','.\hfr-runs\...\logcat_bracket_bkt3.txt') -OutFile .\perf-runs\reader_backpressure_validate_raw_and_bkt3.md"
#   .\scripts\pns_analyze_reader_backpressure.ps1 -LogDir .\hfr-runs\adb_preview_validate_20260511_005819 -OutFile .\perf-runs\reader_backpressure_rollup.md

param(
    [string[]]$LogPath = @(),
    [string]$LogDir = "",
    [string]$OutFile = ""
)

$ErrorActionPreference = "Stop"

function Resolve-LogFiles {
    param([string[]]$Paths, [string]$Dir)
    $files = New-Object System.Collections.Generic.List[string]
    foreach ($p in $Paths) {
        if ([string]::IsNullOrWhiteSpace($p)) { continue }
        $rp = Resolve-Path -LiteralPath $p -ErrorAction Stop
        foreach ($r in $rp) {
            if (-not (Test-Path -LiteralPath $r -PathType Leaf)) {
                throw "Not a file: $r"
            }
            [void]$files.Add($r.Path)
        }
    }
    if (-not [string]::IsNullOrWhiteSpace($Dir)) {
        $rd = Resolve-Path -LiteralPath $Dir -ErrorAction Stop
        $d = $rd.Path
        if (-not (Test-Path -LiteralPath $d -PathType Container)) {
            throw "Not a directory: $d"
        }
        Get-ChildItem -LiteralPath $d -Filter "logcat_*.txt" -File -Recurse -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notmatch '_app_pid\.txt$' } |
            ForEach-Object {
            [void]$files.Add($_.FullName)
        }
    }
    return @($files | Select-Object -Unique)
}

function Add-Count([hashtable]$Table, [string]$Key) {
    if ([string]::IsNullOrWhiteSpace($Key)) { $Key = "(missing)" }
    if ($Table.ContainsKey($Key)) { $Table[$Key] = [int]$Table[$Key] + 1 } else { $Table[$Key] = 1 }
}

$logFiles = Resolve-LogFiles -Paths $LogPath -Dir $LogDir
if ($logFiles.Count -eq 0) {
    throw "No log files. Pass -LogPath <file> [...] and/or -LogDir <folder with logcat_*.txt>."
}

# Matches: `W PNS.Reader: drop oldest queue=... channel=...` (order fixed in app).
$rxDrop = [regex]'PNS\.Reader:\s*drop oldest\s+queue=(\S+)\s+channel=(\S+)'
$rxDrainTimeout = [regex]'PNS\.Reader:.*encode lane drain timed out'
$rxEncodeBusy = [regex]'PNS\.AdbValidation:.*encode_lane_busy'

$queue = @{}
$channel = @{}
$combo = @{}
$totalDrop = 0
$unparsedDrop = 0
$drainTimeouts = 0
$encodeBusy = 0
$linesRead = 0L

foreach ($lf in $logFiles) {
    foreach ($line in [System.IO.File]::ReadLines($lf)) {
        $linesRead++
        if ($rxEncodeBusy.IsMatch($line)) { $encodeBusy++ }
        if ($rxDrainTimeout.IsMatch($line)) { $drainTimeouts++ }

        if ($line -notmatch 'PNS\.Reader') { continue }
        if ($line -notmatch 'drop oldest') { continue }

        $totalDrop++
        $m = $rxDrop.Match($line)
        if ($m.Success) {
            $q = $m.Groups[1].Value
            $c = $m.Groups[2].Value
            Add-Count $queue $q
            Add-Count $channel $c
            Add-Count $combo "$q + $c"
        }
        else {
            $unparsedDrop++
        }
    }
}

$sb = [System.Text.StringBuilder]::new()
[void]$sb.AppendLine("# PNS.Reader backpressure rollup")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- Generated (local): $([DateTime]::Now.ToString('o'))")
[void]$sb.AppendLine("- Source file(s): $($logFiles.Count)")
foreach ($f in $logFiles) {
    [void]$sb.AppendLine("  - ``$f``")
}
[void]$sb.AppendLine("- Lines scanned: **$linesRead**")
[void]$sb.AppendLine("")
[void]$sb.AppendLine('## `PNS.Reader` ``drop oldest`` (parsed)')
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| Metric | Count |")
[void]$sb.AppendLine("|--------|------:|")
[void]$sb.AppendLine("| Total ``drop oldest`` lines | **$totalDrop** |")
[void]$sb.AppendLine("| Parsed (queue + channel) | **$($totalDrop - $unparsedDrop)** |")
[void]$sb.AppendLine("| Unparsed (tag present, format drift) | **$unparsedDrop** |")
[void]$sb.AppendLine("")

if ($queue.Count -gt 0) {
    [void]$sb.AppendLine("### By ``queue=``")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("| queue | count |")
    [void]$sb.AppendLine("|-------|------:|")
    foreach ($k in ($queue.Keys | Sort-Object)) {
        [void]$sb.AppendLine("| ``$k`` | $($queue[$k]) |")
    }
    [void]$sb.AppendLine("")
}

if ($channel.Count -gt 0) {
    [void]$sb.AppendLine("### By ``channel=``")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("| channel | count |")
    [void]$sb.AppendLine("|---------|------:|")
    foreach ($k in ($channel.Keys | Sort-Object)) {
        [void]$sb.AppendLine("| ``$k`` | $($channel[$k]) |")
    }
    [void]$sb.AppendLine("")
}

if ($combo.Count -gt 0) {
    [void]$sb.AppendLine("### By queue + channel")
    [void]$sb.AppendLine("")
    [void]$sb.AppendLine("| queue + channel | count |")
    [void]$sb.AppendLine("|-----------------|------:|")
    foreach ($k in ($combo.Keys | Sort-Object)) {
        [void]$sb.AppendLine("| ``$k`` | $($combo[$k]) |")
    }
    [void]$sb.AppendLine("")
}

[void]$sb.AppendLine("## Encode-lane pressure signals")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| Signal | Count |")
[void]$sb.AppendLine("|--------|------:|")
[void]$sb.AppendLine("| ``PNS.Reader`` encode lane drain timed out | **$drainTimeouts** |")
[void]$sb.AppendLine("| ``encode_lane_busy`` (``PNS.AdbValidation`` tail match) | **$encodeBusy** |")
[void]$sb.AppendLine("")
[void]$sb.AppendLine('See **`CAPTURE_ARCHITECTURE.md`** backpressure rules and **`BUILD_PLAN.md`** Sprint **7.3** for targets vs evidence.')

$text = $sb.ToString()
if (-not [string]::IsNullOrWhiteSpace($OutFile)) {
    $dir = Split-Path -Parent $OutFile
    if (-not [string]::IsNullOrWhiteSpace($dir) -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    Set-Content -LiteralPath $OutFile -Value $text -Encoding utf8
    Write-Host "`[reader_bp] Wrote $OutFile"
}
else {
    Write-Host $text
}
