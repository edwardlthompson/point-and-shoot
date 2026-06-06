param(
    [string]$RunsRoot = "",
    [string]$OutDir = "",
    [string]$MarketingMapPath = "",
    [string]$CatalogPath = "",
    [string]$AntutuPath = "",
    [switch]$MergeSubmissions,
    [switch]$SkipGsmarenaScrape,
    [switch]$Help
)

$ErrorActionPreference = "Stop"

if ($Help) {
    Write-Host @"
pns_leaderboard_site_publish.ps1

Publishes docs/leaderboard/data/ for GitHub Pages from parity sweeps + optional submissions.

  -RunsRoot           hfr-runs root (default <repo>/hfr-runs)
  -OutDir             output data dir (default <repo>/docs/leaderboard/data)
  -MergeSubmissions   merge docs/leaderboard/submissions/approved/*.json
  -MarketingMapPath   device_marketing_names.json override
  -CatalogPath        catalog_taxonomy.json override
  -AntutuPath         antutu_scores.json override
"@
    exit 0
}

$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot "pns_leaderboard_common.ps1")

if (-not $RunsRoot) { $RunsRoot = Join-Path $repoRoot "hfr-runs" }
if (-not $OutDir) { $OutDir = Join-Path $repoRoot "docs\leaderboard\data" }
if (-not $MarketingMapPath) { $MarketingMapPath = Join-Path $OutDir "device_marketing_names.json" }
if (-not $CatalogPath) { $CatalogPath = Join-Path $OutDir "catalog_taxonomy.json" }
if (-not $AntutuPath) { $AntutuPath = Join-Path $OutDir "antutu_scores.json" }
$GsmarenaPath = Join-Path $OutDir "gsmarena_sensor_specs.json"
$GsmarenaSpecsPath = Join-Path $OutDir "gsmarena_device_specs.json"

$devicesDir = Join-Path $OutDir "devices"
$historyDir = Join-Path $OutDir "history"
$feedPath = Join-Path $OutDir "feed.json"
$blocklistPath = Join-Path $repoRoot "docs\leaderboard\blocklist.json"

New-Item -ItemType Directory -Force -Path $OutDir, $devicesDir, $historyDir | Out-Null

function Load-JsonMap([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) { return @{} }
    try {
        $obj = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
        return $obj
    } catch { return @{} }
}

function Test-GsmarenaCacheForMarketingMap($MarketingMap, $GsmarenaObj) {
    if (-not $MarketingMap -or -not $MarketingMap.devices) { return @() }
    $byModel = Get-GsmarenaSensorByModel $GsmarenaObj
    $warnings = @()
    foreach ($d in @($MarketingMap.devices)) {
        $model = [string]$d.model
        if (-not $byModel.ContainsKey($model)) { continue }
        if (-not (Test-GsmarenaMarketingTitleMatch $byModel[$model] $d)) {
            $warnings += "GSMArena title mismatch for $model / $($d.marketingName): page=$($byModel[$model].pageTitle)"
        }
    }
    return $warnings
}

function Match-Antutu($AntutuObj, $MarketingEntry) {
    if ($MarketingEntry -and $MarketingEntry.antutuScoreOverride) {
        $o = $MarketingEntry.antutuScoreOverride
        return [ordered]@{
            total = [int]$o.total
            cpu = $o.cpu; gpu = $o.gpu; mem = $o.mem; ux = $o.ux
            matchedName = [string]$MarketingEntry.marketingName
            matchConfidence = "manual_override"
            sourceMonth = if ($AntutuObj.sourceMonth) { $AntutuObj.sourceMonth } else { $null }
        }
    }
    if (-not $AntutuObj -or -not $AntutuObj.rankings) { return $null }
    $aliases = @()
    if ($MarketingEntry) {
        if ($MarketingEntry.marketingName) { $aliases += [string]$MarketingEntry.marketingName }
        if ($MarketingEntry.antutuAliases) { $aliases += @($MarketingEntry.antutuAliases) }
    }
    foreach ($rank in @($AntutuObj.rankings)) {
        $name = [string]$rank.deviceName
        foreach ($a in $aliases) {
            if ($name -like "*$a*" -or $a -like "*$name*") {
                return [ordered]@{
                    total = $rank.total
                    cpu = $rank.cpu; gpu = $rank.gpu; mem = $rank.mem; ux = $rank.ux
                    matchedName = $name
                    matchConfidence = "high"
                    sourceMonth = $AntutuObj.sourceMonth
                }
            }
        }
    }
    return $null
}

function Get-VideoSummary($MatrixObj, $InAppObj, $ReportObj) {
    $hfrMax = 0
    if ($MatrixObj -and $MatrixObj.cameras) {
        foreach ($cam in @($MatrixObj.cameras)) {
            $v = 0
            if ($cam.hfrMaxFpsAt1080) { $v = [int]$cam.hfrMaxFpsAt1080 }
            if ($v -gt $hfrMax) { $hfrMax = $v }
            $v2 = 0
            if ($cam.hfrMaxFps) { $v2 = [int]$cam.hfrMaxFps }
            if ($v2 -gt $hfrMax) { $hfrMax = $v2 }
        }
    }
    $enc = $MatrixObj.encoder
    $class = if ($ReportObj.video4k120TruthClass) { [string]$ReportObj.video4k120TruthClass } else { "unknown" }
    return [ordered]@{
        hfrMaxFps1080 = if ($hfrMax -gt 0) { $hfrMax } else { $null }
        video4k120Class = $class
        codecs = [ordered]@{
            h264 = $true
            hevc = if ($enc -and $enc.supportsHevc -ne $false) { $true } else { $false }
            av1 = if ($enc -and $enc.supportsAv1 -eq $true) { $true } else { $false }
            hevc10 = if ($enc -and $enc.supportsMain10 -eq $true) { $true } else { $false }
        }
    }
}

function Get-RawSummary($Cells, $MatrixObj) {
    $dng = @($Cells | Where-Object { $_.catalogId -eq "raw.dng" -and $_.provenOk -eq $true })
    $formats = @()
    $maxMp = 0.0
    if ($MatrixObj -and $MatrixObj.cameras) {
        foreach ($cam in @($MatrixObj.cameras)) {
            if ($cam.rawReadiness -and $cam.rawReadiness.formats) {
                foreach ($f in @($cam.rawReadiness.formats)) { if ($formats -notcontains $f) { $formats += [string]$f } }
            }
            if ($cam.fleetPolicy) {
                $mp = [double]$cam.fleetPolicy.activeArrayWidth * [double]$cam.fleetPolicy.activeArrayHeight / 1000000.0
                if ($mp -gt $maxMp) { $maxMp = $mp }
            }
        }
    }
    return [ordered]@{
        dngProven = ($dng.Count -gt 0)
        rawFormats = $formats
        maxRawMp = if ($maxMp -gt 0) { [math]::Round($maxMp, 2) } else { $null }
    }
}

function Get-GatesByCamera($MatrixObj) {
    $out = @()
    if (-not $MatrixObj -or -not $MatrixObj.cameras) { return $out }
    foreach ($cam in @($MatrixObj.cameras)) {
        $gates = @()
        if ($cam.featureGates) {
            foreach ($prop in $cam.featureGates.PSObject.Properties) {
                $g = $prop.Value
                $gates += [ordered]@{
                    name = $prop.Name
                    advertised = ($g.advertised -eq $true)
                    appEnabled = ($g.appEnabled -eq $true)
                    sessionOk = ($g.sessionOk -eq $true)
                }
            }
        }
        $out += [ordered]@{ cameraId = [string]$cam.cameraId; gates = $gates }
    }
    return $out
}

function Normalize-SpecLinks($Links) {
    if (-not $Links) { return @() }
    if ($Links -is [System.Array]) { return @($Links) }
    return @($Links)
}

function Build-DeviceProfile($Report, $InApp, $Matrix, $CatalogMap, $MarketingMap, $AntutuObj, $GsmarenaObj, [string]$TrustTier) {
    $manufacturer = if ($Matrix -and $Matrix.device) { [string]$Matrix.device.manufacturer } else { "Unknown" }
    $model = if ($Matrix -and $Matrix.device) { [string]$Matrix.device.model } else { "Unknown" }
    if ($manufacturer -eq "Unknown" -and $model -eq "Unknown") { return $null }

    $fp = if ($Matrix.scanMeta) { [string]$Matrix.scanMeta.fingerprintSha256Prefix } else { [string]$InApp.fingerprintSha256Prefix }
    $deviceKey = "$manufacturer|$model|$fp"
    $slug = Get-DeviceSlug $deviceKey
    $marketing = Get-MarketingEntry $MarketingMap $model
    $score = Get-ParityScoreBreakdown $InApp $Matrix
    $cells = @($InApp.cells)
    $advertisedCount = @($cells | Where-Object { $_.advertised -eq $true }).Count
    $provenAdvertised = @($cells | Where-Object { $_.advertised -eq $true -and $_.provenOk -eq $true }).Count
    $honesty = if ($advertisedCount -gt 0) { [math]::Round(100.0 * $provenAdvertised / $advertisedCount, 1) } else { 0.0 }
    $sensor = Get-MergedSensorSpecs $Matrix (Get-GsmarenaSensorByModel $GsmarenaObj) $marketing
    $withheld = @(Get-WithheldFeatures $cells $CatalogMap | Select-Object -First 20)
    $cellsByCat = Get-CellsByCategory $cells $CatalogMap
    $rom = Get-RomFlavor $Matrix
    $scanMeta = $Matrix.scanMeta
    $unlock = $Matrix.product.experimentalUnlockState
    $lensLineup = @(Get-LensLineup $Matrix)
    $rawStillEntries = if ($InApp.stillResolutionAdvertised) {
        @($InApp.stillResolutionAdvertised)
    }
    elseif ($Matrix.product.stillResolutionAdvertised) {
        @($Matrix.product.stillResolutionAdvertised)
    }
    else {
        @()
    }
    $stillHonesty = @(Enrich-StillResolutionHonesty $rawStillEntries $lensLineup)
    $resolutionBetrayal = Get-ResolutionBetrayal $InApp $Matrix $stillHonesty $lensLineup -ForceRecompute
    $fullMpBreakthrough = Get-Camera2FullMpBreakthrough $Matrix $InApp
    $commercialName = Get-MarketingCommercialName $manufacturer $marketing
    $displayLabel = Get-DeviceDisplayLabel $manufacturer $model $marketing $null
    if ($displayLabel -match '\[\?\]$') {
        $displayLabel = Get-DeviceDisplayLabel $manufacturer $model $marketing ""
        $displayLabel = $displayLabel -replace '\s*\[\?\]$', ''
    }
    $oemLossSummary = Get-OemLossSummary $cells $CatalogMap
    $cameraXSummary = Get-CameraXSummary $Matrix
    $measurementContext = Get-MeasurementContext $InApp $Matrix
    $productGroupId = Get-ProductGroupId $(if ($marketing) { [string]$marketing.marketingName } else { "$manufacturer $model" }) $manufacturer $model
    $buildDisplay = $null
    if ($Matrix.product.buildIdentity) { $buildDisplay = [string]$Matrix.product.buildIdentity.display }
    if ($buildDisplay -and $buildDisplay.Length -gt 80) { $buildDisplay = $buildDisplay.Substring(0, 80) }
    $msrp = if ($marketing) { $marketing.msrpUsd } else { $null }
    $parityPerUsd = $null
    if ($msrp -and [double]$msrp -gt 0) {
        $parityPerUsd = [math]::Round([double]$score.total.score / [double]$msrp, 2)
    }

    $profile = [ordered]@{
        schema = "pns.fleet_leaderboard_device.v1"
        slug = $slug
        deviceKey = $deviceKey
        identity = [ordered]@{
            manufacturer = $manufacturer
            model = $model
            marketingName = if ($commercialName) { $commercialName } elseif ($marketing) { [string]$marketing.marketingName } else { "$manufacturer $model" }
            displayLabel = if ($displayLabel) { $displayLabel } else { "$manufacturer $model" }
            fingerprintPrefix = $fp
            specLinks = @(Normalize-SpecLinks $(if ($marketing) { $marketing.specLinks } else { $null }))
            msrpUsd = if ($marketing) { $marketing.msrpUsd } else { $null }
            variants = if ($marketing -and $marketing.variants) { @($marketing.variants) } else { @() }
            gsmarenaUrl = Get-GsmarenaUrl $marketing (Get-GsmarenaSensorByModel $GsmarenaObj) $model
            productGroupId = $productGroupId
            externalScores = if ($marketing -and $marketing.externalScores) { @($marketing.externalScores) } else { @() }
        }
        software = [ordered]@{
            sdkInt = if ($scanMeta) { $scanMeta.sdkInt } else { $null }
            apiLevelLabel = if ($scanMeta -and $scanMeta.sdkInt) { Get-AndroidApiLabel $scanMeta.sdkInt } else { $null }
            firstApiLevel = if ($scanMeta) { $scanMeta.firstApiLevel } else { $null }
            securityPatch = if ($scanMeta) { [string]$scanMeta.securityPatch } else { $null }
            rootGranted = if ($unlock) { ($unlock.rootGranted -eq $true) } else { $false }
            romFlavor = $rom
            buildTags = if ($Matrix.product.buildIdentity) { [string]$Matrix.product.buildIdentity.tags } else { $null }
            buildDisplay = $buildDisplay
        }
        measurementContext = $measurementContext
        sensors = [ordered]@{
            sensorSumMm2 = $sensor.sensorSumMm2
            sensorSumMethod = $sensor.sensorSumMethod
            source = $sensor.source
            sourceLabel = $sensor.sourceLabel
            sourceUrl = $sensor.sourceUrl
            sensors = @($sensor.sensors)
            rearLenses = @($sensor.rearLenses)
        }
        lensLineup = $lensLineup
        scores = $score
        disparity = [ordered]@{
            advertisedCellCount = $advertisedCount
            provenAdvertisedCount = $provenAdvertised
            honestyPercent = $honesty
            gapBreakdown = $InApp.gapBreakdown
        }
        withheldFeatures = $withheld
        cellsByCategory = $cellsByCat
        gatesByCamera = @(Get-GatesByCamera $Matrix)
        formatPickerHonestyScore = $InApp.formatPickerHonestyScore
        resolutionBetrayal = $resolutionBetrayal
        camera2FullMpBreakthrough = $fullMpBreakthrough
        oemLossSummary = $oemLossSummary
        cameraXSummary = $cameraXSummary
        value = [ordered]@{
            msrpUsd = $msrp
            parityPerUsd = $parityPerUsd
        }
        videoSummary = Get-VideoSummary $Matrix $InApp $Report
        rawSummary = Get-RawSummary $cells $Matrix
        stillResolutionHonesty = $stillHonesty
        antutu = Match-Antutu $AntutuObj $marketing
        meta = [ordered]@{
            lastSweepUtc = if ($Report.timestampUtc) { [string]$Report.timestampUtc } else { [DateTime]::UtcNow.ToString("o") }
            lastSweepMode = if ($Report.mode) { [string]$Report.mode } else { [string]$InApp.mode }
            appVersionCode = if ($scanMeta) { $scanMeta.appVersionCode } else { $InApp.appVersionCode }
            scanTier = if ($scanMeta) { [string]$scanMeta.scanTier } else { [string]$InApp.scanTier }
            trustTier = $TrustTier
            testedApiLevel = if ($scanMeta -and $scanMeta.sdkInt) { Get-AndroidApiLabel $scanMeta.sdkInt } else { $null }
        }
    }
    return $profile
}

# Load catalog map
$catalogMap = @{}
if (Test-Path -LiteralPath $CatalogPath) {
    $catObj = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json
    foreach ($row in @($catObj.rows)) {
        $catalogMap[[string]$row.id] = @{ displayName = [string]$row.displayName; category = [string]$row.category }
    }
}

$marketingMap = Load-JsonMap $MarketingMapPath
$antutuObj = Load-JsonMap $AntutuPath
$gsmScrapePy = Join-Path $PSScriptRoot "gsmarena_sensor_scrape.py"
$gsmSpecsPy = Join-Path $PSScriptRoot "gsmarena_device_specs_scrape.py"
if (-not $SkipGsmarenaScrape -and (Test-Path -LiteralPath $gsmScrapePy)) {
    try {
        & python $gsmScrapePy 2>&1 | Out-Host
    } catch {
        Write-Warning "gsmarena sensor scrape skipped: $_"
    }
}
if (-not $SkipGsmarenaScrape -and (Test-Path -LiteralPath $gsmSpecsPy)) {
    try {
        & python $gsmSpecsPy 2>&1 | Out-Host
    } catch {
        Write-Warning "gsmarena device specs scrape skipped: $_"
    }
}
$gsmarenaObj = Load-JsonMap $GsmarenaPath
$gsmarenaSpecsObj = Load-JsonMap $GsmarenaSpecsPath
foreach ($warn in @(Test-GsmarenaCacheForMarketingMap $marketingMap $gsmarenaObj)) {
    Write-Warning $warn
}
$gsmarenaSpecsMap = Get-GsmarenaDeviceSpecsByModel $gsmarenaSpecsObj
$blocklist = @()
if (Test-Path -LiteralPath $blocklistPath) {
    $bl = Get-Content -LiteralPath $blocklistPath -Raw | ConvertFrom-Json
    if ($bl.deviceKeys) { $blocklist = @($bl.deviceKeys) }
}

$byDevice = @{}

# Maintainer sweeps from hfr-runs
if (Test-Path -LiteralPath $RunsRoot) {
    $sweepDirs = @(Get-ChildItem -LiteralPath $RunsRoot -Directory -Filter "parity_sweep_*" -ErrorAction SilentlyContinue)
    foreach ($dir in $sweepDirs) {
        $reportPath = Join-Path $dir.FullName "parity_report.json"
        $inAppPath = Join-Path $dir.FullName "in_app_parity_report.json"
        if (-not (Test-Path -LiteralPath $reportPath) -or -not (Test-Path -LiteralPath $inAppPath)) { continue }
        try {
            $report = Get-Content -LiteralPath $reportPath -Raw | ConvertFrom-Json
            $inApp = Get-Content -LiteralPath $inAppPath -Raw | ConvertFrom-Json
        } catch { continue }
        if (-not $inApp.cells) { continue }
        $inApp = Apply-ProofEvidenceToInApp $inApp $report $dir.FullName
        $matrix = Get-MatrixFromSweepDir $dir.FullName
        if (-not $matrix) { continue }
        $profile = Build-DeviceProfile $report $inApp $matrix $catalogMap $marketingMap $antutuObj $gsmarenaObj "maintainer"
        if (-not $profile) { continue }
        if ($blocklist -contains $profile.deviceKey) { continue }
        $mode = if ($inApp.mode) { [string]$inApp.mode } elseif ($report.mode) { [string]$report.mode } else { "" }
        if ($mode -notmatch '(?i)^full$') { continue }
        $key = $profile.deviceKey
        if ($byDevice.ContainsKey($key)) {
            $existing = $byDevice[$key]
            if ((Parse-Utc $profile.meta.lastSweepUtc) -gt (Parse-Utc $existing.meta.lastSweepUtc)) {
                $byDevice[$key] = $profile
            }
        } else {
            $byDevice[$key] = $profile
        }
    }
}

# Community approved submissions
if ($MergeSubmissions) {
    $approvedDir = Join-Path $repoRoot "docs\leaderboard\submissions\approved"
    if (Test-Path -LiteralPath $approvedDir) {
        foreach ($f in Get-ChildItem -LiteralPath $approvedDir -Filter "*.json") {
            try {
                $sub = Get-Content -LiteralPath $f.FullName -Raw | ConvertFrom-Json
                $report = [pscustomobject]@{ timestampUtc = $sub.submittedUtc; mode = $sub.parityReport.mode; video4k120TruthClass = $null }
                $tier = if ($sub.trustTier) { [string]$sub.trustTier } else { "community_verified" }
                $profile = Build-DeviceProfile $report $sub.parityReport $sub.matrix $catalogMap $marketingMap $antutuObj $gsmarenaObj $tier
                if (-not $profile) { continue }
                if ($blocklist -contains $profile.deviceKey) { continue }
                $key = $profile.deviceKey
                if (-not $byDevice.ContainsKey($key)) {
                    $byDevice[$key] = $profile
                }
            } catch { Write-Warning "Skip submission $($f.Name): $_" }
        }
    }
}

$profiles = @($byDevice.Values)

# Merge existing published profiles when sweep missing or older
if (Test-Path -LiteralPath $devicesDir) {
    foreach ($f in Get-ChildItem -LiteralPath $devicesDir -Filter "*.json") {
        try {
            $existing = Get-Content -LiteralPath $f.FullName -Raw | ConvertFrom-Json
            if (-not $existing.deviceKey) { continue }
            $key = [string]$existing.deviceKey
            if ($blocklist -contains $key) { continue }
            if (-not $byDevice.ContainsKey($key)) {
                $byDevice[$key] = $existing
            } else {
                $cur = $byDevice[$key]
                $curUtc = Parse-Utc ([string]$cur.meta.lastSweepUtc)
                $exUtc = Parse-Utc ([string]$existing.meta.lastSweepUtc)
                if ($exUtc -gt $curUtc) { $byDevice[$key] = $existing }
            }
        } catch { }
    }
}
$profiles = @($byDevice.Values)

foreach ($p in @($profiles)) {
    $null = Enrich-DeviceProfile $p $catalogMap
}

$sorted = @($profiles | Where-Object { $_ -and $_.slug } | Sort-Object { [double]$_.scores.total.score } -Descending)
for ($i = 0; $i -lt $sorted.Count; $i++) {
    $p = $sorted[$i]
    $p.scores = [ordered]@{
        features = $p.scores.features
        resolutions = $p.scores.resolutions
        capabilities = $p.scores.capabilities
        total = $p.scores.total
        rank = ($i + 1)
    }
}

$oemRankings = Get-OemRankings $sorted
$oemAccountability = Get-OemAccountability $sorted
$productGroups = Build-ProductGroups $sorted $marketingMap $gsmarenaSpecsMap
$catalogVersion = 3

foreach ($p in $sorted) {
    $path = Join-Path $devicesDir "$($p.slug).json"
    Write-LeaderboardJson -Object $p -Path $path -Depth 12

    # History jsonl — dedupe same timestamp + scores
    $histPath = Join-Path $historyDir "$($p.slug).jsonl"
    $snap = [ordered]@{
        timestampUtc = $p.meta.lastSweepUtc
        totalPercent = $p.scores.total.percent
        totalScore = $p.scores.total.score
        honestyPercent = $p.disparity.honestyPercent
        trustTier = $p.meta.trustTier
        catalogVersion = $catalogVersion
    }
    $snapLine = ($snap | ConvertTo-Json -Compress)
    $existingLines = @()
    if (Test-Path -LiteralPath $histPath) {
        $existingLines = @(Get-Content -LiteralPath $histPath -Encoding utf8 | Where-Object { $_.Trim().Length -gt 0 })
    }
    $isDupe = $false
    foreach ($line in $existingLines) {
        try {
            $prev = $line | ConvertFrom-Json
            if ($prev.timestampUtc -eq $snap.timestampUtc -and [double]$prev.totalScore -eq [double]$snap.totalScore) {
                $isDupe = $true
                break
            }
        } catch { }
    }
    if (-not $isDupe) {
        Add-Content -LiteralPath $histPath -Value $snapLine -Encoding utf8
    }
}

$productGroupsPath = Join-Path $OutDir "product_groups.json"
Write-LeaderboardJson -Object ([ordered]@{
    schema = "pns.leaderboard_product_groups.v1"
    updatedUtc = [DateTime]::UtcNow.ToString("o")
    groups = $productGroups
}) -Path $productGroupsPath -Depth 12

$oemAccountabilityPath = Join-Path $OutDir "oem_accountability.json"
Write-LeaderboardJson -Object ([ordered]@{
    schema = "pns.leaderboard_oem_accountability.v1"
    updatedUtc = [DateTime]::UtcNow.ToString("o")
    subtitle = "Rankings reflect Camera2/CameraX access for third-party apps, not OEM camera app quality."
    oems = $oemAccountability
}) -Path $oemAccountabilityPath -Depth 10

$gsmarenaSpecsStale = $false
if ($gsmarenaSpecsObj -and $gsmarenaSpecsObj.stale -eq $true) { $gsmarenaSpecsStale = $true }

$breakthroughHighlights = @($sorted | Where-Object { $_.camera2FullMpBreakthrough -and $_.camera2FullMpBreakthrough.proven -eq $true } | ForEach-Object {
    [ordered]@{
        slug = $_.slug
        marketingName = $_.identity.marketingName
        maxMpPerSensor = $_.camera2FullMpBreakthrough.maxMpPerSensor
        evidenceTier = $_.camera2FullMpBreakthrough.evidenceTier
    }
})

$site = [ordered]@{
    schema = "pns.fleet_leaderboard_site.v1"
    updatedUtc = [DateTime]::UtcNow.ToString("o")
    catalogVersion = $catalogVersion
    deviceCount = $sorted.Count
    breakthroughCount = $breakthroughHighlights.Count
    highlights = $breakthroughHighlights
    oemRankings = $oemRankings
    oemAccountabilityPath = "oem_accountability.json"
    productGroupsPath = "product_groups.json"
    gsmarenaSpecsStale = $gsmarenaSpecsStale
    gsmarenaSpecsFromCache = ($gsmarenaSpecsObj.devices | Where-Object { $_.fromSensorCache -eq $true }).Count -gt 0
    deviceSlugs = @($sorted | ForEach-Object { $_.slug })
    devices = @($sorted | ForEach-Object { $_.deviceKey })
}
Write-LeaderboardJson -Object $site -Path (Join-Path $OutDir "site.json") -Depth 10

# Feed
$feedItems = @()
if (Test-Path -LiteralPath $feedPath) {
    try {
        $existingFeed = Get-Content -LiteralPath $feedPath -Raw | ConvertFrom-Json
        $feedItems = @($existingFeed.items)
    } catch { }
}
foreach ($p in $sorted) {
    $exists = @($feedItems | Where-Object { $_.slug -eq $p.slug -and $_.event -eq "device_added" }).Count -gt 0
    if (-not $exists) {
        $feedItems = @([ordered]@{
            timestampUtc = [DateTime]::UtcNow.ToString("o")
            slug = $p.slug
            marketingName = $p.identity.marketingName
            event = "device_added"
        }) + $feedItems
    }
    if ($p.camera2FullMpBreakthrough -and $p.camera2FullMpBreakthrough.proven -eq $true) {
        $btExists = @($feedItems | Where-Object { $_.slug -eq $p.slug -and $_.event -eq "breakthrough" }).Count -gt 0
        if (-not $btExists) {
            $feedItems = @([ordered]@{
                timestampUtc = [DateTime]::UtcNow.ToString("o")
                slug = $p.slug
                marketingName = $p.identity.marketingName
                event = "breakthrough"
                maxMpPerSensor = $p.camera2FullMpBreakthrough.maxMpPerSensor
                evidenceTier = $p.camera2FullMpBreakthrough.evidenceTier
            }) + $feedItems
            Write-FleetParityHistoryEvent $repoRoot "camera2_full_mp_breakthrough" ([ordered]@{
                slug = $p.slug
                deviceKey = $p.deviceKey
                maxMpPerSensor = $p.camera2FullMpBreakthrough.maxMpPerSensor
                evidenceTier = $p.camera2FullMpBreakthrough.evidenceTier
            })
        }
    }
}
$feed = [ordered]@{ schema = "pns.leaderboard_feed.v1"; updatedUtc = [DateTime]::UtcNow.ToString("o"); items = @($feedItems | Select-Object -First 50) }
Write-LeaderboardJson -Object $feed -Path $feedPath -Depth 6

& (Join-Path $PSScriptRoot "pns_fleet_parity_leaderboard_refresh.ps1") -RunsRoot $RunsRoot

$csvScript = Join-Path $PSScriptRoot "pns_leaderboard_export_csv.ps1"
if (Test-Path -LiteralPath $csvScript) {
    & $csvScript -OutDir $OutDir
}
$rssScript = Join-Path $PSScriptRoot "pns_leaderboard_feed_rss.ps1"
if (Test-Path -LiteralPath $rssScript) {
    & $rssScript -OutDir $OutDir
}
$catalogScript = Join-Path $PSScriptRoot "pns_leaderboard_export_catalog.ps1"
if (Test-Path -LiteralPath $catalogScript) {
    & $catalogScript -OutDir $OutDir
}

Write-Host "[leaderboard_publish] devices=$($sorted.Count) groups=$($productGroups.Count) out=$OutDir"
exit 0
