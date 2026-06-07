# Shared helpers for on-device AnTuTu benchmark automation.
$ErrorActionPreference = "Stop"

$script:PnsAntutuKnownPackages = @(
    "com.antutu.ABenchMark",
    "com.antutu.benchmark.full",
    "com.antutu.benchmark.full.lite",
    "com.antutu.benchmark"
)

$script:PnsAntutuScoreMin = 50000
$script:PnsAntutuScoreMax = 4500000

$script:PnsAntutuTapTexts = @(
    "Agree", "Accept", "OK", "Ok", "Got it", "Got It", "Continue", "Skip", "Later",
    "Start", "Benchmark", "Run", "Full Test", "Full test", "Retest", "Test again", "Test Again",
    "Allow", "While using the app", "Only this time", "Next", "Done", "Close"
)

function Resolve-PnsAntutuSerial([string]$Serial, [string]$ScriptRoot) {
    if (-not [string]::IsNullOrWhiteSpace($Serial)) { return $Serial }
    $fromEnv = Read-PnsAdbSerialFromEnvFile $ScriptRoot
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if ($fromEnv -match ':' -and $adb) { & $adb connect $fromEnv 2>$null | Out-Null }
    if ($adb) {
        $devices = & $adb devices 2>$null
        $text = if ($devices -is [System.Array]) { $devices -join "`n" } else { [string]$devices }
        if ($fromEnv -and $text -match [regex]::Escape($fromEnv)) { return $fromEnv }
        foreach ($line in $text -split "`n") {
            if ($line -match '^(\S+)\s+device') { return $Matches[1] }
        }
    }
    return $fromEnv
}

function Read-PnsAdbSerialFromEnvFile([string]$ScriptRoot) {
    $envFile = Join-Path $ScriptRoot "pns_adb_device.env"
    if (-not (Test-Path -LiteralPath $envFile)) { return $null }
    foreach ($line in Get-Content -LiteralPath $envFile -ErrorAction SilentlyContinue) {
        $t = $line.Trim()
        if ($t -match '^\s*#' -or [string]::IsNullOrWhiteSpace($t)) { continue }
        if ($t -match '^\s*PNS_ADB_SERIAL\s*=\s*(.+)\s*$') { return $Matches[1].Trim().Trim('"').Trim("'") }
    }
    return $null
}

function New-PnsAntutuAdbInvoker([string]$Serial) {
    $adbExe = $null
    $resolve = Join-Path $PSScriptRoot "pns_resolve_adb.ps1"
    if (Test-Path -LiteralPath $resolve) {
        . $resolve -PrependToPath -Quiet
    }
    $adbExe = (Get-Command adb -ErrorAction Stop).Source
    return @{
        Exe = $adbExe
        Prefix = if ($Serial) { @("-s", $Serial) } else { @() }
    }
}

function Invoke-PnsAntutuAdb($Adb, [string[]]$CmdArgs, [switch]$IgnoreErrors) {
    $all = @($Adb.Prefix) + @($CmdArgs)
    & $Adb.Exe @all
    if (-not $IgnoreErrors -and $LASTEXITCODE -ne 0) {
        throw "adb failed: $($CmdArgs -join ' ') exit=$LASTEXITCODE"
    }
}

function Get-PnsAntutuProp($Adb, [string]$Name) {
    $lines = Invoke-PnsAntutuAdb $Adb @("shell", "getprop", $Name) 2>$null
    $text = if ($lines -is [System.Array]) { ($lines -join "`n").Trim() } else { ([string]$lines).Trim() }
    return $text
}

function Get-PnsAntutuDeviceIdentity($Adb) {
    $manufacturer = Get-PnsAntutuProp $Adb "ro.product.manufacturer"
    $model = Get-PnsAntutuProp $Adb "ro.product.model"
    $display = Get-PnsAntutuProp $Adb "ro.build.display.id"
    $fpFull = Get-PnsAntutuProp $Adb "ro.build.fingerprint"
    $fpPrefix = $null
    if ($fpFull) {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($fpFull)
        $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
        $fpPrefix = ([BitConverter]::ToString($hash).Replace("-", "").ToLower()).Substring(0, 16)
    }
    $deviceKey = "$manufacturer|$model|$fpPrefix"
    $slugBytes = [System.Text.Encoding]::UTF8.GetBytes($deviceKey)
    $slugHash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($slugBytes)
    $slug = ([BitConverter]::ToString($slugHash).Replace("-", "").ToLower()).Substring(0, 16)
    return [ordered]@{
        manufacturer = $manufacturer
        model = $model
        buildDisplay = $display
        fingerprintSha256Prefix = $fpPrefix
        deviceKey = $deviceKey
        deviceSlug = $slug
    }
}

function Find-PnsAntutuPackage($Adb) {
    $raw = Invoke-PnsAntutuAdb $Adb @("shell", "pm", "list", "packages") -IgnoreErrors
    $text = if ($raw -is [System.Array]) { $raw -join "`n" } else { [string]$raw }
    $installed = @()
    foreach ($line in $text -split "`n") {
        if ($line -match '^package:(.+)$') { $installed += $Matches[1].Trim() }
    }
    foreach ($pkg in $script:PnsAntutuKnownPackages) {
        if ($installed -contains $pkg) { return $pkg }
    }
    foreach ($pkg in $installed) {
        if ($pkg -match 'antutu|ABenchMark') { return $pkg }
    }
    return $null
}

function Get-PnsAntutuAppVersion($Adb, [string]$Package) {
    if (-not $Package) { return $null }
    $raw = Invoke-PnsAntutuAdb $Adb @("shell", "dumpsys", "package", $Package) -IgnoreErrors
    $text = if ($raw -is [System.Array]) { $raw -join "`n" } else { [string]$raw }
    if ($text -match 'versionName=([^\s]+)') { return $Matches[1] }
    return $null
}

function Get-PnsAntutuBatteryPct($Adb) {
    $raw = Invoke-PnsAntutuAdb $Adb @("shell", "dumpsys", "battery") -IgnoreErrors
    $text = if ($raw -is [System.Array]) { $raw -join "`n" } else { [string]$raw }
    if ($text -match 'level:\s*(\d+)') { return [int]$Matches[1] }
    return $null
}

function Enable-PnsAntutuDeviceInteractive($Adb) {
    Invoke-PnsAntutuAdb $Adb @("shell", "svc", "power", "stayon", "true") -IgnoreErrors | Out-Null
    Invoke-PnsAntutuAdb $Adb @("shell", "input", "keyevent", "KEYCODE_WAKEUP") -IgnoreErrors | Out-Null
    Invoke-PnsAntutuAdb $Adb @("shell", "wm", "dismiss-keyguard") -IgnoreErrors | Out-Null
    Invoke-PnsAntutuAdb $Adb @("shell", "input", "keyevent", "82") -IgnoreErrors | Out-Null
}

function Disable-PnsAntutuDeviceInteractive($Adb) {
    Invoke-PnsAntutuAdb $Adb @("shell", "svc", "power", "stayon", "false") -IgnoreErrors | Out-Null
}

function Invoke-PnsAntutuUiDump($Adb, [string]$RemotePath) {
    Invoke-PnsAntutuAdb $Adb @("shell", "uiautomator", "dump", $RemotePath) -IgnoreErrors | Out-Null
    Start-Sleep -Milliseconds 400
}

function Get-PnsAntutuUiXml($Adb, [string]$LocalPath) {
    $remote = "/sdcard/pns_antutu_ui.xml"
    Invoke-PnsAntutuUiDump $Adb $remote
    Invoke-PnsAntutuAdb $Adb @("pull", $remote, $LocalPath) -IgnoreErrors | Out-Null
    if (-not (Test-Path -LiteralPath $LocalPath)) { return $null }
    return Get-Content -LiteralPath $LocalPath -Raw -Encoding UTF8
}

function Get-PnsAntutuUiNodes([string]$Xml) {
    if ([string]::IsNullOrWhiteSpace($Xml)) { return @() }
    $nodes = @()
    $pattern = '<node[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*?(?:text="([^"]*)")?[^>]*?(?:content-desc="([^"]*)")?[^>]*/>'
    foreach ($m in [regex]::Matches($Xml, '<node[^>]*/>')) {
        $chunk = $m.Value
        $bounds = [regex]::Match($chunk, 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
        if (-not $bounds.Success) { continue }
        $textM = [regex]::Match($chunk, 'text="([^"]*)"')
        $descM = [regex]::Match($chunk, 'content-desc="([^"]*)"')
        $text = if ($textM.Success) { $textM.Groups[1].Value } else { "" }
        $desc = if ($descM.Success) { $descM.Groups[1].Value } else { "" }
        $x1 = [int]$bounds.Groups[1].Value
        $y1 = [int]$bounds.Groups[2].Value
        $x2 = [int]$bounds.Groups[3].Value
        $y2 = [int]$bounds.Groups[4].Value
        $nodes += [pscustomobject]@{
            Text = $text
            Desc = $desc
            X = [int](($x1 + $x2) / 2)
            Y = [int](($y1 + $y2) / 2)
            Label = if ($text) { $text } else { $desc }
        }
    }
    return $nodes
}

function Invoke-PnsAntutuTap($Adb, [int]$X, [int]$Y) {
    Invoke-PnsAntutuAdb $Adb @("shell", "input", "tap", "$X", "$Y") -IgnoreErrors | Out-Null
    Start-Sleep -Milliseconds 650
}

function Invoke-PnsAntutuTapMatchingNode($Adb, $Nodes, [string[]]$Needles) {
    foreach ($needle in $Needles) {
        foreach ($n in @($Nodes)) {
            $label = [string]$n.Label
            if ([string]::IsNullOrWhiteSpace($label)) { continue }
            if ($label -eq $needle -or $label -like "*$needle*") {
                Invoke-PnsAntutuTap $Adb $n.X $n.Y
                return $true
            }
        }
    }
    return $false
}

function Test-PnsAntutuBenchmarkInProgress([string]$Xml) {
    if ([string]::IsNullOrWhiteSpace($Xml)) { return $false }
    if ($Xml -match 'mainTestPercent|mainTestRootView|mainTestStop|Testing ') { return $true }
    if ($Xml -match 'com\.antutu\.benchmark\.(full|full\.lite)' -and $Xml -match 'Game view') { return $true }
    return $false
}

function Get-PnsAntutuBoundsCenter([string]$Xml, [string]$ResourceId) {
    if ([string]::IsNullOrWhiteSpace($Xml)) { return $null }
    $esc = [regex]::Escape($ResourceId)
    $m = [regex]::Match($Xml, "resource-id=`"$esc`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
    if (-not $m.Success) { return $null }
    $x1 = [int]$m.Groups[1].Value; $y1 = [int]$m.Groups[2].Value
    $x2 = [int]$m.Groups[3].Value; $y2 = [int]$m.Groups[4].Value
    return @{ X = [int](($x1 + $x2) / 2); Y = [int](($y1 + $y2) / 2) }
}

function Test-PnsAntutuAiPluginDialog([string]$Xml) {
    return ($Xml -match 'aiPluginTitle|AITUTU Benchmark')
}

function Invoke-PnsAntutuDismissAiPlugin($Adb, [string]$Xml) {
    if (-not (Test-PnsAntutuAiPluginDialog $Xml)) { return $false }
    $esc = [regex]::Escape("com.antutu.ABenchMark:id/design_bottom_sheet")
    $m = [regex]::Match($Xml, "resource-id=`"$esc`"[^>]*bounds=`"\[(\d+),(\d+)\]\[(\d+),(\d+)\]`"")
    if ($m.Success) {
        $sheetTop = [int]$m.Groups[2].Value
        $centerX = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        $tapY = [Math]::Max(300, $sheetTop - 180)
        Invoke-PnsAntutuTap $Adb $centerX $tapY
    } else {
        Invoke-PnsAntutuTap $Adb 658 500
    }
    Start-Sleep -Milliseconds 900
    return $true
}

function Invoke-PnsAntutuDismissDialogs($Adb, [string]$Xml) {
    if (Test-PnsAntutuAiPluginDialog $Xml) {
        return (Invoke-PnsAntutuDismissAiPlugin $Adb $Xml)
    }
    $nodes = Get-PnsAntutuUiNodes $Xml
    return (Invoke-PnsAntutuTapMatchingNode $Adb $nodes $script:PnsAntutuTapTexts)
}

function Test-PnsAntutuScoreInRange([int]$Value) {
    return ($Value -ge $script:PnsAntutuScoreMin -and $Value -le $script:PnsAntutuScoreMax)
}

function Get-PnsAntutuScoresFromXml([string]$Xml) {
    if ([string]::IsNullOrWhiteSpace($Xml)) { return $null }
    if (Test-PnsAntutuBenchmarkInProgress $Xml) { return $null }

    $total = $null
    $mFinish = [regex]::Match($Xml, 'resource-id="com\.antutu\.ABenchMark:id/main_test_finish_score"[^>]*text="([\d,]+)"')
    if ($mFinish.Success) {
        $v = [int]($mFinish.Groups[1].Value -replace ',', '')
        if (Test-PnsAntutuScoreInRange $v) { $total = $v }
    }

    $nodes = Get-PnsAntutuUiNodes $Xml
    $scores = @()
    if ($total) { $scores += $total }
    foreach ($n in @($nodes)) {
        $label = [string]$n.Label
        if ([string]::IsNullOrWhiteSpace($label)) { continue }
        $clean = $label -replace ',', '' -replace '\s', ''
        if ($clean -match '^(\d{5,7})$') {
            $val = [int]$Matches[1]
            if (Test-PnsAntutuScoreInRange $val) { $scores += $val }
        }
    }
    foreach ($m in [regex]::Matches($Xml, 'resource-id="[^"]*(?:score|Score|total|Total)[^"]*"[^>]*text="([\d,]{5,7})"')) {
        $val = [int]($m.Groups[1].Value -replace ',', '')
        if (Test-PnsAntutuScoreInRange $val) { $scores += $val }
    }
    if ($scores.Count -eq 0) { return $null }
    if (-not $total) { $total = ($scores | Sort-Object -Descending | Select-Object -First 1) }
    $cpu = $null; $gpu = $null; $mem = $null; $ux = $null
    foreach ($n in @($nodes)) {
        $label = [string]$n.Label
        if ($label -match '(?i)CPU[^\d]*([\d,]{5,7})') { $cpu = [int]($Matches[1] -replace ',', '') }
        if ($label -match '(?i)GPU[^\d]*([\d,]{5,7})') { $gpu = [int]($Matches[1] -replace ',', '') }
        if ($label -match '(?i)(MEM|Memory)[^\d]*([\d,]{5,7})') { $mem = [int]($Matches[2] -replace ',', '') }
        if ($label -match '(?i)UX[^\d]*([\d,]{5,7})') { $ux = [int]($Matches[1] -replace ',', '') }
    }
    return [ordered]@{
        total = [int]$total
        cpu = $cpu
        gpu = $gpu
        mem = $mem
        ux = $ux
    }
}

function Start-PnsAntutuApp($Adb, [string]$Package) {
    $resolve = Invoke-PnsAntutuAdb $Adb @(
        "shell", "cmd", "package", "resolve-activity", "--brief", $Package
    ) -IgnoreErrors
    $line = @($resolve | Where-Object { $_ -and $_ -notmatch 'No activity' } | Select-Object -Last 1)
    $component = if ($line) { [string]$line.Trim() } else { "$Package/.activity.SplashActivity" }
    if ($component -notmatch '/') { $component = "$Package/$component" }
    Invoke-PnsAntutuAdb $Adb @("shell", "am", "start", "-W", "-n", $component) -IgnoreErrors | Out-Null
    Start-Sleep -Seconds 3
}

function Test-PnsAntutuHomeReady([string]$Xml) {
    return ($Xml -match 'main_test_finish_score|main_test_finish_retest|main_test_finish_show')
}

function Invoke-PnsAntutuUiPass($Adb, [string]$ArtifactDir, [string]$Tag) {
    $xmlPath = Join-Path $ArtifactDir ("ui_{0}.xml" -f $Tag)
    $xml = Get-PnsAntutuUiXml $Adb $xmlPath
    for ($d = 0; $d -lt 4; $d++) {
        if (-not (Test-PnsAntutuAiPluginDialog $xml)) { break }
        Invoke-PnsAntutuDismissAiPlugin $Adb $xml | Out-Null
        $xml = Get-PnsAntutuUiXml $Adb ($xmlPath + ".dismiss_$d")
    }
    if (Test-PnsAntutuHomeReady $xml) {
        return @{ tapped = $false; xmlPath = $xmlPath; nodes = (Get-PnsAntutuUiNodes $xml); xml = $xml }
    }
    $nodes = Get-PnsAntutuUiNodes $xml
    $tapped = Invoke-PnsAntutuTapMatchingNode $Adb $nodes $script:PnsAntutuTapTexts
    return @{ tapped = $tapped; xmlPath = $xmlPath; nodes = $nodes; xml = $xml }
}

function Start-PnsAntutuBenchmarkFromHome($Adb, [string]$ArtifactDir) {
    for ($pass = 1; $pass -le 5; $pass++) {
        $xmlPath = Join-Path $ArtifactDir ("ui_start_test_{0:D2}.xml" -f $pass)
        $xml = Get-PnsAntutuUiXml $Adb $xmlPath
        if (Test-PnsAntutuAiPluginDialog $xml) {
            Invoke-PnsAntutuDismissAiPlugin $Adb $xml | Out-Null
            continue
        }
        $retest = Get-PnsAntutuBoundsCenter $xml "com.antutu.ABenchMark:id/main_test_finish_retest"
        if ($retest) {
            Invoke-PnsAntutuTap $Adb $retest.X $retest.Y
            return "retest"
        }
        $start = Get-PnsAntutuBoundsCenter $xml "com.antutu.ABenchMark:id/main_test_finish_show"
        if ($start) {
            Invoke-PnsAntutuTap $Adb $start.X $start.Y
            return "show_tap"
        }
        $nodes = Get-PnsAntutuUiNodes $xml
        if (Invoke-PnsAntutuTapMatchingNode $Adb $nodes @("Test Again", "Retest", "Full Test", "Benchmark", "Start")) {
            return "text_tap"
        }
    }
    return "none"
}

function Wait-PnsAntutuBenchmarkResult($Adb, [string]$ArtifactDir, [int]$TimeoutSec) {
    $deadline = (Get-Date).AddSeconds($TimeoutSec)
    $attempt = 0
    while ((Get-Date) -lt $deadline) {
        $attempt++
        $tag = "{0:D3}" -f $attempt
        $xmlPath = Join-Path $ArtifactDir ("poll_{0}.xml" -f $tag)
        $xml = Get-PnsAntutuUiXml $Adb $xmlPath
        if (-not (Test-PnsAntutuBenchmarkInProgress $xml)) {
            $parsed = Get-PnsAntutuScoresFromXml $xml
            if ($parsed -and (Test-PnsAntutuScoreInRange $parsed.total)) {
                return @{ ok = $true; scores = $parsed; xmlPath = $xmlPath; attempt = $attempt }
            }
            if (Test-PnsAntutuAiPluginDialog $xml) {
                Invoke-PnsAntutuDismissAiPlugin $Adb $xml | Out-Null
            } elseif (-not (Test-PnsAntutuBenchmarkInProgress $xml)) {
                $nodes = Get-PnsAntutuUiNodes $xml
                [void](Invoke-PnsAntutuTapMatchingNode $Adb $nodes @("Agree", "Accept", "OK", "Continue", "Skip", "Later", "Done", "Close"))
            }
        }
        Start-Sleep -Seconds 10
    }
    return @{ ok = $false; scores = $null; attempt = $attempt }
}

function Get-PnsAntutuSamplesPath([string]$RepoRoot) {
    return Join-Path $RepoRoot "docs\leaderboard\data\antutu_samples.json"
}

function Load-PnsAntutuSamples([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        return [ordered]@{ schema = "pns.antutu_samples.v1"; samples = @() }
    }
    $obj = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    if (-not $obj.schema) { $obj | Add-Member -NotePropertyName schema -NotePropertyValue "pns.antutu_samples.v1" -Force }
    if (-not $obj.samples) { $obj | Add-Member -NotePropertyName samples -NotePropertyValue @() -Force }
    return $obj
}

function Save-PnsAntutuSamples($Obj, [string]$Path) {
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    $json = ($Obj | ConvertTo-Json -Depth 8)
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}

function Add-PnsAntutuSample(
    [string]$SamplesPath,
    [string]$HistoryPath,
    $Sample
) {
    $obj = Load-PnsAntutuSamples $SamplesPath
    $list = [System.Collections.ArrayList]@()
    foreach ($s in @($obj.samples)) { [void]$list.Add($s) }
    [void]$list.Add($Sample)
    $obj.samples = @($list)
    Save-PnsAntutuSamples $obj $SamplesPath
    $histDir = Split-Path -Parent $HistoryPath
    if (-not (Test-Path -LiteralPath $histDir)) { New-Item -ItemType Directory -Force -Path $histDir | Out-Null }
    ($Sample | ConvertTo-Json -Compress) | Add-Content -LiteralPath $HistoryPath -Encoding utf8
}

function Get-PnsAntutuMarketingName([string]$RepoRoot, [string]$Model) {
    $mapPath = Join-Path $RepoRoot "docs\leaderboard\data\device_marketing_names.json"
    if (-not (Test-Path -LiteralPath $mapPath)) { return $null }
    $map = Get-Content -LiteralPath $mapPath -Raw | ConvertFrom-Json
    foreach ($d in @($map.devices)) {
        if ([string]$d.model -eq $Model) { return [string]$d.marketingName }
    }
    return $null
}
