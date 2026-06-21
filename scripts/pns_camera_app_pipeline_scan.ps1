# Milestone 28.0 — shallow-clone FOSS camera repos + pipeline needle grep.
#
# Artifacts: hfr-runs/camera_benchmark_*/benchmark_needles.json + benchmark_scan_summary.md
#
# Usage:
#   .\scripts\pns_camera_app_pipeline_scan.ps1
#   .\scripts\pns_camera_app_pipeline_scan.ps1 -SkipClone   # reuse clones in OutDir\clones
#   .\scripts\pns_camera_app_pipeline_scan.ps1 -OutDir hfr-runs\camera_benchmark_manual

param(
    [string]$OutDir = "",
    [switch]$SkipClone,
    [int]$CloneDepth = 1
)

$ErrorActionPreference = "Stop"

$projRoot = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) {
    $utc = [DateTime]::UtcNow.ToString("yyyyMMdd_HHmmss")
    $OutDir = Join-Path $projRoot "hfr-runs\camera_benchmark_$utc"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$cloneRoot = Join-Path $OutDir "clones"
New-Item -ItemType Directory -Force -Path $cloneRoot | Out-Null

$needles = @(
    "ProcessCameraProvider",
    "ImageCapture",
    "ImageAnalysis",
    "VideoCapture",
    "CameraDevice",
    "ImageReader",
    "DngCreator",
    "MediaStore",
    "IS_PENDING",
    "MediaRecorder",
    "SessionConfiguration",
    "ExtensionMode",
    "FACE_RETOUCH",
    "BEAUTY",
    "REMOVE_EXIF",
    "ExifInterface"
)

$repos = @(
    @{
        Id = "android_camera_samples"
        Label = "android/camera-samples"
        Url = "https://github.com/android/camera-samples.git"
        Branch = "main"
        Notes = "Google pattern reference (not a product app)"
    }
    @{
        Id = "opencamera_sf"
        Label = "SourceForge Open Camera (Mark Harman)"
        Url = "https://git.code.sf.net/p/opencamera/code"
        Branch = "master"
        Notes = "F-Droid flagship; Camera2 + DNG"
    }
    @{
        Id = "grapheneos_camera"
        Label = "GrapheneOS/Camera"
        Url = "https://github.com/GrapheneOS/Camera.git"
        Branch = "main"
        Notes = "CameraX consumer app"
    }
    @{
        Id = "fossify_camera"
        Label = "FossifyOrg/Camera"
        Url = "https://github.com/FossifyOrg/Camera.git"
        Branch = "main"
        Notes = "CameraX + Fossify commons"
    }
    @{
        Id = "lineage_aperture"
        Label = "LineageOS Aperture"
        Url = "https://github.com/LineageOS/android_packages_apps_Aperture.git"
        Branch = "lineage-23.2"
        Notes = "OEM CameraX replacement"
    }
    @{
        Id = "photon_camera"
        Label = "eszdman/PhotonCamera"
        Url = "https://github.com/eszdman/PhotonCamera.git"
        Branch = "dev"
        Notes = "Camera2 + GLES/C++ compute"
    }
    @{
        Id = "almalence_opencamera"
        Label = "almalence/OpenCamera"
        Url = "https://github.com/almalence/OpenCamera.git"
        Branch = "master"
        Notes = "Legacy Almalence app (not Mark Harman)"
    }
)

$sampleDirs = @(
    "CameraXBasic",
    "CameraXVideo",
    "CameraX-MLKit",
    "Camera2Basic",
    "Camera2Video",
    "Camera2SlowMotion",
    "Camera2Extensions",
    "CameraXExtensions"
)

function Count-NeedleInTree {
    param(
        [string]$Root,
        [string[]]$Patterns,
        [string[]]$IncludeExt = @("*.kt", "*.java", "*.kts", "*.gradle", "*.xml")
    )
    $counts = @{}
    foreach ($p in $Patterns) { $counts[$p] = 0 }
    if (-not (Test-Path -LiteralPath $Root)) { return $counts }
    $files = Get-ChildItem -LiteralPath $Root -Recurse -File -Include $IncludeExt -ErrorAction SilentlyContinue |
        Where-Object {
            $_.FullName -notmatch '[\\/](build|\.gradle|\.idea)[\\/]' -and
            $_.FullName -notmatch '[\\/]\.git[\\/]'
        }
    foreach ($f in $files) {
        try {
            $text = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction Stop
        } catch {
            continue
        }
        foreach ($p in $Patterns) {
            if ($text -match [regex]::Escape($p)) {
                $counts[$p]++
            }
        }
    }
    return $counts
}

function Ensure-Clone {
    param(
        [hashtable]$Repo
    )
    $dest = Join-Path $cloneRoot $Repo.Id
    if ((Test-Path -LiteralPath (Join-Path $dest ".git")) -and $SkipClone) {
        Write-Host "[benchmark] reuse clone $($Repo.Id)"
        return $dest
    }
    if (Test-Path -LiteralPath $dest) {
        Remove-Item -LiteralPath $dest -Recurse -Force
    }
    Write-Host "[benchmark] git clone --depth $CloneDepth $($Repo.Url) -> $($Repo.Id)"
    $cloneArgs = @("clone", "--depth", "$CloneDepth", "--single-branch", "-b", $Repo.Branch, $Repo.Url, $dest)
    & git @cloneArgs
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "[benchmark] clone failed for $($Repo.Id) exit=$LASTEXITCODE"
        return $null
    }
    return $dest
}

$report = @{
    schema = "pns.camera_benchmark_scan.v1"
    generatedUtc = [DateTime]::UtcNow.ToString("o")
    outDir = (Resolve-Path -LiteralPath $OutDir).Path
    needles = $needles
    repos = @()
    cameraSamples = @()
}

$summaryLines = @(
    "# Camera benchmark scan summary",
    "",
    "Generated: $($report.generatedUtc)",
    "OutDir: $($report.outDir)",
    "",
    "## Repo needle hits (file count per pattern)",
    ""
)

foreach ($repo in $repos) {
    $path = if ($SkipClone -and (Test-Path -LiteralPath (Join-Path $cloneRoot $repo.Id))) {
        Join-Path $cloneRoot $repo.Id
    } else {
        Ensure-Clone -Repo $repo
    }
    $counts = if ($path) { Count-NeedleInTree -Root $path -Patterns $needles } else { @{} }
    $repoRow = @{
        id = $repo.Id
        label = $repo.Label
        url = $repo.Url
        branch = $repo.Branch
        notes = $repo.Notes
        clonePath = $path
        needleHits = $counts
    }
    $report.repos += $repoRow

    $summaryLines += "### $($repo.Label)"
    $summaryLines += ""
    if (-not $path) {
        $summaryLines += "Clone: **FAILED**"
    } else {
        $summaryLines += "Clone: ``$path``"
        foreach ($n in $needles) {
            $c = if ($counts.ContainsKey($n)) { $counts[$n] } else { 0 }
            if ($c -gt 0) {
                $summaryLines += "- $n : $c files"
            }
        }
    }
    $summaryLines += ""
}

$samplesPath = Join-Path $cloneRoot "android_camera_samples"
if ((Test-Path -LiteralPath $samplesPath) -or -not $SkipClone) {
    if (-not (Test-Path -LiteralPath $samplesPath)) {
        $samplesPath = Ensure-Clone -Repo $repos[0]
    }
    if ($samplesPath) {
        $summaryLines += "## android/camera-samples subprojects"
        $summaryLines += ""
        foreach ($sd in $sampleDirs) {
            $sub = Join-Path $samplesPath $sd
            $subCounts = Count-NeedleInTree -Root $sub -Patterns $needles
            $report.cameraSamples += @{
                sample = $sd
                path = $sub
                exists = Test-Path -LiteralPath $sub
                needleHits = $subCounts
            }
            $summaryLines += "### $sd"
            if (-not (Test-Path -LiteralPath $sub)) {
                $summaryLines += "Missing in clone."
            } else {
                foreach ($n in $needles) {
                    $c = $subCounts[$n]
                    if ($c -gt 0) { $summaryLines += "- $n : $c files" }
                }
            }
            $summaryLines += ""
        }
    }
}

$jsonPath = Join-Path $OutDir "benchmark_needles.json"
$mdPath = Join-Path $OutDir "benchmark_scan_summary.md"
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $jsonPath -Encoding utf8
$summaryLines | Set-Content -LiteralPath $mdPath -Encoding utf8

Write-Host ""
Write-Host "BENCHMARK SCAN: PASS"
Write-Host "  JSON: $jsonPath"
Write-Host "  MD:   $mdPath"
Write-Host "  Clones: $cloneRoot"
