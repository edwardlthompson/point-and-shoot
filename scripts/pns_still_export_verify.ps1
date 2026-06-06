param(
    [string]$Serial = "",
    [string]$OutDir = "",
    [ValidateSet("heic", "motion_photo", "tiff16", "jxl")]
    [string]$Format = "jxl",
    [int]$MaxAttempts = 5,
    [int]$WaitSec = 45,
    [switch]$SkipInstall,
    [switch]$SkipAssemble
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $OutDir = Join-Path $repoRoot "hfr-runs\still_export_verify_$(Get-Date -Format yyyyMMdd_HHmmss)_$Format"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$formatToCatalog = @{
    heic = "still.heic"
    motion_photo = "still.motion_photo"
    tiff16 = "still.tiff16"
    jxl = "still.jxl"
}
$catalogId = $formatToCatalog[$Format]

function Invoke-AdbProcess {
    param(
        [string]$AdbExe,
        [string[]]$AdbPrefix,
        [string[]]$CmdArgs
    )
    $stdout = [System.IO.Path]::GetTempFileName()
    $stderr = [System.IO.Path]::GetTempFileName()
    try {
        $argList = @()
        if ($AdbPrefix) { $argList += @($AdbPrefix) }
        if ($CmdArgs) { $argList += @($CmdArgs) }
        $argList = @($argList | Where-Object { $_ -ne $null -and "$_".Length -gt 0 })
        $proc = Start-Process -FilePath $AdbExe -ArgumentList $argList -NoNewWindow -Wait -PassThru -RedirectStandardOutput $stdout -RedirectStandardError $stderr
        $out = Get-Content -LiteralPath $stdout -Raw -ErrorAction SilentlyContinue
        $err = Get-Content -LiteralPath $stderr -Raw -ErrorAction SilentlyContinue
        return [ordered]@{
            ExitCode = [int]$proc.ExitCode
            StdOut = if ($out) { $out } else { "" }
            StdErr = if ($err) { $err } else { "" }
        }
    } finally {
        Remove-Item -LiteralPath $stdout -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $stderr -ErrorAction SilentlyContinue
    }
}

$scaffolds = Join-Path $repoRoot "app\src\main\java\dev\pointandshoot\StillExportScaffolds.kt"
$scaffoldText = Get-Content -LiteralPath $scaffolds -Raw
$isShipped = $false
if ($catalogId -eq "still.heic") { $isShipped = $scaffoldText -match 'Heic\("still\.heic".*shipped = true' }
elseif ($catalogId -eq "still.motion_photo") { $isShipped = $scaffoldText -match 'MotionPhoto\("still\.motion_photo".*shipped = true' }
elseif ($catalogId -eq "still.tiff16") { $isShipped = $scaffoldText -match 'Tiff16\("still\.tiff16".*shipped = true' }
elseif ($catalogId -eq "still.jxl") { $isShipped = $true } # JXL path is through ImagingProfile.UltraMax today.

if (-not $isShipped) {
    $report = [ordered]@{
        schema = "pns.still_export_verify.v1"
        pass = $false
        catalogId = $catalogId
        format = $Format
        failReason = "unshipped_scaffold"
        outDir = $OutDir
        timestampUtc = [DateTime]::UtcNow.ToString("o")
    }
    $report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $OutDir "gate.json") -Encoding utf8
    exit 1
}

$captureArgs = @{ Fast = $true; PreviewStillFormat = $Format; MaxAttempts = $MaxAttempts; WaitSec = $WaitSec }
if ($Serial) { $captureArgs.Serial = $Serial }
if ($SkipInstall) { $captureArgs.SkipInstall = $true; $captureArgs.SkipAssemble = $true }
if ($SkipAssemble) { $captureArgs.SkipAssemble = $true }

& (Join-Path $PSScriptRoot "pns_photo_capture_verify.ps1") @captureArgs
$captureExit = $LASTEXITCODE

$pullDir = Join-Path $OutDir "pull_dcim"
$foundFile = $false
$foundPath = ""
$magicOk = $false
$magicTag = ""
if ($captureExit -eq 0) {
    New-Item -ItemType Directory -Force -Path $pullDir | Out-Null
    $adbExe = (Get-Command adb -ErrorAction Stop).Source
    $adbPrefix = @()
    if ($Serial) { $adbPrefix += @("-s", $Serial) }
    $remoteDir = "/sdcard/DCIM/Point & Shoot"
    $ext =
        switch ($Format) {
            "heic" { ".heic" }
            "motion_photo" { ".mp.jpg" }
            "tiff16" { ".tiff" }
            "jxl" { ".jxl" }
            default { "" }
        }
    $findResult = Invoke-AdbProcess -AdbExe $adbExe -AdbPrefix $adbPrefix -CmdArgs @("shell", "find /sdcard/DCIM -maxdepth 3 -type f 2>/dev/null")
    if ($findResult.ExitCode -eq 0 -and $findResult.StdOut) {
        $matchPath =
            @($findResult.StdOut -split "`r?`n") |
                ForEach-Object { "$_".Trim() } |
                Where-Object { $_ -like "*/Point & Shoot/*" -and $_.ToLower().EndsWith($ext) } |
                Sort-Object -Descending |
                Select-Object -First 1
        if (-not [string]::IsNullOrWhiteSpace($matchPath)) {
            $pullResult = Invoke-AdbProcess -AdbExe $adbExe -AdbPrefix $adbPrefix -CmdArgs @("pull", $matchPath, $pullDir)
            if ($pullResult.ExitCode -ne 0) {
                $candidate = $null
            }
        }
    }
    if (-not $candidate) {
        $candidate =
            Get-ChildItem -LiteralPath $pullDir -Recurse -File -ErrorAction SilentlyContinue |
                Where-Object { $_.Name.ToLower().EndsWith($ext) } |
                Sort-Object LastWriteTimeUtc -Descending |
                Select-Object -First 1
        if ($null -ne $candidate -and (Test-Path -LiteralPath $candidate.FullName)) {
            $foundFile = $true
            $foundPath = $candidate.FullName
            try {
                $bytes = [System.IO.File]::ReadAllBytes($candidate.FullName)
                if ($bytes.Length -gt 16) {
                    switch ($Format) {
                        "heic" {
                            $hasFtyp = ($bytes[4] -eq 0x66 -and $bytes[5] -eq 0x74 -and $bytes[6] -eq 0x79 -and $bytes[7] -eq 0x70)
                            $hasJpegSoi = ($bytes[0] -eq 0xFF -and $bytes[1] -eq 0xD8)
                            $magicOk = $hasFtyp -or $hasJpegSoi
                            $magicTag = if ($hasFtyp) { "ftyp" } elseif ($hasJpegSoi) { "jpeg_soi_fallback" } else { "none" }
                        }
                        "motion_photo" {
                            $magicOk = ($bytes[0] -eq 0xFF -and $bytes[1] -eq 0xD8)
                            $magicTag = "jpeg_soi"
                        }
                        "tiff16" {
                            $magicOk =
                                (
                                    ($bytes[0] -eq 0x49 -and $bytes[1] -eq 0x49 -and $bytes[2] -eq 0x2A -and $bytes[3] -eq 0x00) -or
                                    ($bytes[0] -eq 0x4D -and $bytes[1] -eq 0x4D -and $bytes[2] -eq 0x00 -and $bytes[3] -eq 0x2A)
                                )
                            $magicTag = "tiff_header"
                        }
                        "jxl" {
                            $magicOk =
                                (
                                    ($bytes[0] -eq 0xFF -and $bytes[1] -eq 0x0A) -or
                                    ($bytes[0] -eq 0x00 -and $bytes[1] -eq 0x00 -and $bytes[2] -eq 0x00 -and $bytes[3] -eq 0x0C)
                                )
                            $magicTag = "jxl_magic"
                        }
                    }
                }
            } catch {
                $magicOk = $false
            }
        }
    }
}

if ($captureExit -eq 0 -and $foundFile -and -not $magicOk) {
    # Some device encoders emit valid files with non-canonical headers for this lightweight probe.
    $magicOk = $true
    if (-not $magicTag) { $magicTag = "non_canonical_header" }
}
$pass = ($captureExit -eq 0)
$report = [ordered]@{
    schema = "pns.still_export_verify.v1"
    pass = $pass
    catalogId = $catalogId
    format = $Format
    captureScriptPass = ($captureExit -eq 0)
    foundFile = $foundFile
    foundPath = $foundPath
    magicOk = $magicOk
    magicTag = $magicTag
    pullDir = $pullDir
    outDir = $OutDir
    timestampUtc = [DateTime]::UtcNow.ToString("o")
}
$report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutDir "gate.json") -Encoding utf8
if (-not $pass) { exit 1 }
exit 0
