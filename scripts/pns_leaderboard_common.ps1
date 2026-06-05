# Shared leaderboard scoring helpers for refresh + site publish.

function Write-LeaderboardJson {
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Path,
        [int]$Depth = 10,
        [switch]$Compress
    )
    $json = if ($Compress) { $Object | ConvertTo-Json -Depth $Depth -Compress } else { $Object | ConvertTo-Json -Depth $Depth }
    $dir = Split-Path -Parent $Path
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Force -Path $dir | Out-Null
    }
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllText($Path, $json, $utf8NoBom)
}

function Get-CategoryScoreFromCells($Cells) {
    $score = 0
    $maxScore = 0
    foreach ($c in @($Cells)) {
        $maxScore += 10
        if ($c.provenOk -eq $true) { $score += 10; continue }
        if ($c.failReason -like "skip:matrix_gate:*" -or $c.proofSkipped -like "matrix_gate:*") { $score += 8; continue }
        if ($c.advertised -eq $true) { $score += 4; continue }
    }
    $pct = if ($maxScore -gt 0) { [math]::Round(($score * 100.0) / $maxScore, 1) } else { 0.0 }
    return [ordered]@{
        score = $score
        maxScore = $maxScore
        percent = $pct
        cellCount = @($Cells).Count
        provenCount = @($Cells | Where-Object { $_.provenOk -eq $true }).Count
    }
}

function Get-CapabilityScoreFromMatrix($MatrixObj) {
    $score = 0
    $maxScore = 0
    $featureGateCount = 0
    if (-not $MatrixObj -or -not $MatrixObj.cameras) {
        return [ordered]@{ score = 0; maxScore = 0; percent = 0.0; gateCount = 0; cameraCount = 0 }
    }
    foreach ($cam in @($MatrixObj.cameras)) {
        if (-not $cam.featureGates) { continue }
        foreach ($prop in $cam.featureGates.PSObject.Properties) {
            $gate = $prop.Value
            if (-not $gate) { continue }
            $featureGateCount++
            $maxScore += 6
            if ($gate.advertised -eq $true) { $score += 1 }
            if ($gate.appEnabled -eq $true) { $score += 2 }
            if ($gate.sessionOk -eq $true) { $score += 3 }
        }
    }
    $pct = if ($maxScore -gt 0) { [math]::Round(($score * 100.0) / $maxScore, 1) } else { 0.0 }
    return [ordered]@{
        score = $score
        maxScore = $maxScore
        percent = $pct
        gateCount = $featureGateCount
        cameraCount = @($MatrixObj.cameras).Count
    }
}

function Get-ParityScoreBreakdown($InAppObj, $MatrixObj) {
    $cells = if ($InAppObj -and $InAppObj.cells) { @($InAppObj.cells) } else { @() }
    $resolutionPattern = '(?i)(\.720p|\.1080p|\.4k|\.8k|video\.hfr\.)'
    $resolutionCells = @($cells | Where-Object { $_.catalogId -match $resolutionPattern })
    $featureCells = @($cells | Where-Object { $_.catalogId -notmatch $resolutionPattern })
    $featureScore = Get-CategoryScoreFromCells $featureCells
    $resolutionScore = Get-CategoryScoreFromCells $resolutionCells
    $capabilityScore = Get-CapabilityScoreFromMatrix $MatrixObj

    $totalScore = [int]($featureScore.score + $resolutionScore.score + $capabilityScore.score)
    $totalMax = [int]($featureScore.maxScore + $resolutionScore.maxScore + $capabilityScore.maxScore)
    $totalPct = if ($totalMax -gt 0) { [math]::Round(($totalScore * 100.0) / $totalMax, 1) } else { 0.0 }
    return [ordered]@{
        features = $featureScore
        resolutions = $resolutionScore
        capabilities = $capabilityScore
        total = [ordered]@{
            score = $totalScore
            maxScore = $totalMax
            percent = $totalPct
        }
    }
}

function Get-MatrixFromSweepDir([string]$SweepDir) {
    $candidates = @(
        (Join-Path $SweepDir "matrix\fleet_device_matrix.json"),
        (Join-Path $SweepDir "fleet_device_matrix_pulled.json")
    )
    foreach ($path in $candidates) {
        if (-not (Test-Path -LiteralPath $path)) { continue }
        try {
            return (Get-Content -LiteralPath $path -Raw | ConvertFrom-Json)
        } catch { }
    }
    return $null
}

function Apply-ProofEvidenceToInApp($InAppObj, $ReportObj, [string]$SweepDir) {
    if (-not $InAppObj -or -not $InAppObj.cells) { return $InAppObj }
    $proofPath = $null
    if ($ReportObj -and $ReportObj.proofResultsPath -and (Test-Path -LiteralPath ([string]$ReportObj.proofResultsPath))) {
        $proofPath = [string]$ReportObj.proofResultsPath
    } else {
        $fallback = Join-Path $SweepDir "proof_pack\parity_proof_results.json"
        if (Test-Path -LiteralPath $fallback) { $proofPath = $fallback }
    }
    if (-not $proofPath) { return $InAppObj }
    try {
        $raw = (Get-Content -LiteralPath $proofPath -Raw).TrimStart([char]0xFEFF)
        $proof = $raw | ConvertFrom-Json
        $proofMap = @{}
        foreach ($row in @($proof.rows)) {
            if (-not $row.catalogId) { continue }
            $proofMap[[string]$row.catalogId] = $row
        }
        foreach ($cell in @($InAppObj.cells)) {
            $id = [string]$cell.catalogId
            if (-not $proofMap.ContainsKey($id)) { continue }
            $p = $proofMap[$id]
            $isMatrixSkip = ($p.PSObject.Properties.Name -contains "skippedReason") -and ([string]$p.skippedReason).StartsWith("matrix_gate:")
            $isPass = ($p.PSObject.Properties.Name -contains "pass") -and ($p.pass -eq $true)
            if (-not $isPass -and -not $isMatrixSkip) { continue }
            $cell.provenOk = $true
            $cell.failReason = $null
            $cell.gap = "OK"
            $cell | Add-Member -NotePropertyName proofMerged -NotePropertyValue $true -Force
            if ($isMatrixSkip) {
                $cell | Add-Member -NotePropertyName proofSkipped -NotePropertyValue ([string]$p.skippedReason) -Force
            }
        }
    } catch {}
    return $InAppObj
}

function Parse-Utc([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return [DateTime]::MinValue }
    try { return [DateTime]::Parse($Value).ToUniversalTime() } catch { return [DateTime]::MinValue }
}

function Get-DeviceSlug([string]$DeviceKey) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($DeviceKey)
    $hash = [System.Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    return ([BitConverter]::ToString($hash).Replace("-", "").ToLower()).Substring(0, 16)
}

function Get-AndroidApiLabel([object]$SdkInt) {
    if ($null -eq $SdkInt -or [string]$SdkInt -eq "") { return $null }
    $api = [int]$SdkInt
    $names = @{
        33 = "Android 13"
        34 = "Android 14"
        35 = "Android 15"
        36 = "Android 16"
    }
    $name = if ($names.ContainsKey($api)) { $names[$api] } else { "Android API $api" }
    return "$name (API $api)"
}

function Get-RomFlavor($MatrixObj) {
    $product = $MatrixObj.product
    $buildId = $product.buildIdentity
    $unlock = $product.experimentalUnlockState
    if ($unlock -and $unlock.rootGranted -eq $true) { return "root_unlocked" }
    $tags = if ($buildId) { [string]$buildId.tags } else { "" }
    $display = if ($buildId) { [string]$buildId.display } else { "" }
    $type = if ($buildId) { [string]$buildId.type } else { "" }
    if ($tags -match "test-keys") { return "custom_likely" }
    if ($display -match "(?i)lineage|crdroid|/e/os/|evolution|pixel experience|arrow") { return "custom_likely" }
    if ($type -eq "userdebug") { return "engineering" }
    if ($tags -match "release-keys") { return "stock" }
    return "unknown"
}

function Get-SensorSum($MatrixObj) {
    $sensors = @()
    $sum = 0.0
    $method = "physical_mm2"
    if (-not $MatrixObj -or -not $MatrixObj.cameras) {
        return @{ sensors = $sensors; sensorSumMm2 = 0; sensorSumMethod = "none" }
    }
    foreach ($cam in @($MatrixObj.cameras)) {
        $role = if ($cam.fleetPolicy -and $cam.fleetPolicy.role) { [string]$cam.fleetPolicy.role } else { "UNKNOWN" }
        if ($role -in @("LOGICAL", "FRONT", "UNKNOWN")) { continue }
        $w = $null; $h = $null
        if ($cam.lensInfo -and $cam.lensInfo.sensorPhysicalSizeMm) {
            $w = [double]$cam.lensInfo.sensorPhysicalSizeMm.widthMm
            $h = [double]$cam.lensInfo.sensorPhysicalSizeMm.heightMm
        }
        $area = if ($w -and $h) { $w * $h } else { 0 }
        if ($area -le 0) { $method = "partial_missing_lensInfo" }
        else { $sum += $area }
        $sensors += [ordered]@{
            cameraId = [string]$cam.cameraId
            role = $role
            widthMm = $w
            heightMm = $h
            areaMm2 = if ($area -gt 0) { [math]::Round($area, 2) } else { $null }
            megapixels = if ($cam.fleetPolicy) { $cam.fleetPolicy.activeArrayWidth * $cam.fleetPolicy.activeArrayHeight / 1000000.0 } else { $null }
        }
    }
    return @{ sensors = $sensors; sensorSumMm2 = [math]::Round($sum, 2); sensorSumMethod = $method }
}

function Get-GsmarenaUrl($MarketingEntry, $GsmarenaByModel, [string]$Model) {
    if ($MarketingEntry -and $MarketingEntry.specLinks) {
        foreach ($link in @($MarketingEntry.specLinks)) {
            $url = if ($link.url) { [string]$link.url } else { "" }
            if ($url -match "gsmarena\.com") { return $url }
        }
    }
    if ($Model -and $GsmarenaByModel -and $GsmarenaByModel.ContainsKey($Model)) {
        $gsm = $GsmarenaByModel[$Model]
        if ($gsm.gsmarenaUrl) { return [string]$gsm.gsmarenaUrl }
    }
    return $null
}

function Get-GsmarenaSensorByModel($GsmarenaObj) {
    $map = @{}
    if (-not $GsmarenaObj -or -not $GsmarenaObj.devices) { return $map }
    foreach ($d in @($GsmarenaObj.devices)) {
        if ($d.model) { $map[[string]$d.model] = $d }
    }
    return $map
}

function Get-MergedSensorSpecs($MatrixObj, $GsmarenaByModel, $MarketingEntry) {
    $hal = Get-SensorSum $MatrixObj
    $model = if ($MarketingEntry) { [string]$MarketingEntry.model } else { $null }
    $gsm = if ($model -and $GsmarenaByModel.ContainsKey($model)) { $GsmarenaByModel[$model] } else { $null }
    $override = if ($MarketingEntry) { $MarketingEntry.sensorSpecOverride } else { $null }

    $scanTier = if ($MatrixObj.scanMeta) { [string]$MatrixObj.scanMeta.scanTier } else { "" }
    if ($hal.sensorSumMm2 -gt 0 -and (
            $hal.sensorSumMethod -eq "physical_mm2" -or
            ($scanTier -eq "full" -and $hal.sensorSumMethod -eq "partial_missing_lensInfo")
        )) {
        return [ordered]@{
            sensorSumMm2 = $hal.sensorSumMm2
            sensorSumMethod = $hal.sensorSumMethod
            sensors = @($hal.sensors)
            rearLenses = @()
            source = "camera2_hal"
            sourceLabel = "Camera2 HAL (device scan)"
            sourceUrl = $null
        }
    }

    if ($override -and $override.sensorSumMm2) {
        return [ordered]@{
            sensorSumMm2 = [double]$override.sensorSumMm2
            sensorSumMethod = if ($override.method) { [string]$override.method } else { "manual_override" }
            sensors = @()
            rearLenses = @($override.lenses)
            source = "manual_override"
            sourceLabel = "Curated spec sheet"
            sourceUrl = if ($override.sourceUrl) { [string]$override.sourceUrl } else { $null }
        }
    }

    if ($gsm -and $gsm.sensorSumMm2) {
        $rear = @($gsm.lenses | Where-Object { $_.role -ne "selfie" })
        return [ordered]@{
            sensorSumMm2 = [double]$gsm.sensorSumMm2
            sensorSumMethod = [string]$gsm.sensorSumMethod
            sensors = @()
            rearLenses = @($rear)
            source = "gsmarena"
            sourceLabel = "GSMArena sensor type"
            sourceUrl = [string]$gsm.gsmarenaUrl
        }
    }

    return [ordered]@{
        sensorSumMm2 = if ($hal.sensorSumMm2 -gt 0) { $hal.sensorSumMm2 } else { 0 }
        sensorSumMethod = if ($hal.sensorSumMethod) { $hal.sensorSumMethod } else { "none" }
        sensors = @($hal.sensors)
        rearLenses = @()
        source = "unavailable"
        sourceLabel = $null
        sourceUrl = $null
    }
}

function Get-WithheldFeatures($Cells, $CatalogMap) {
    $impactOrder = @{ SHIP_BLOCKER = 0; ENGINEERING_ONLY = 1; INFORMATIONAL = 2 }
    $list = @()
    foreach ($c in @($Cells)) {
        if ($c.advertised -ne $true -or $c.provenOk -eq $true) { continue }
        $gap = if ($c.gap) { [string]$c.gap } else { "GAP_ADVERTISED_NOT_PROVEN" }
        if ($gap -notin @("GAP_ADVERTISED_NOT_PROVEN", "GAP_DELIVERY_MISMATCH")) { continue }
        $meta = $CatalogMap[[string]$c.catalogId]
        $list += [ordered]@{
            catalogId = [string]$c.catalogId
            displayName = if ($meta) { $meta.displayName } else { [string]$c.catalogId }
            category = if ($meta) { $meta.category } else { "Unknown" }
            gap = $gap
            failReason = if ($c.failReason) { [string]$c.failReason } else { $null }
            consumerImpact = if ($c.consumerImpact) { [string]$c.consumerImpact } else { "INFORMATIONAL" }
        }
    }
    return @($list | Sort-Object { $impactOrder[$_.consumerImpact] }, { $_.catalogId })
}

function Get-CellsByCategory($Cells, $CatalogMap) {
    $byCat = @{}
    foreach ($c in @($Cells)) {
        $id = [string]$c.catalogId
        $meta = $CatalogMap[$id]
        $cat = if ($meta) { $meta.category } else { "Unknown" }
        if (-not $byCat.ContainsKey($cat)) { $byCat[$cat] = @() }
        $byCat[$cat] += [ordered]@{
            catalogId = $id
            displayName = if ($meta) { $meta.displayName } else { $id }
            advertised = ($c.advertised -eq $true)
            provenOk = ($c.provenOk -eq $true)
            sessionOk = $c.sessionOk
            gap = if ($c.gap) { [string]$c.gap } else { $null }
            failReason = if ($c.failReason) { [string]$c.failReason } else { $null }
            consumerImpact = if ($c.consumerImpact) { [string]$c.consumerImpact } else { $null }
        }
    }
    return $byCat
}

function Get-OemRankings($DeviceProfiles) {
    $byOem = @{}
    foreach ($d in @($DeviceProfiles)) {
        $oem = [string]$d.identity.manufacturer
        if (-not $byOem.ContainsKey($oem)) {
            $byOem[$oem] = @{ advertised = 0; provenAdvertised = 0; gateAdvertised = 0; gateSessionOk = 0; withheld = 0; devices = @() }
        }
        $b = $byOem[$oem]
        $b.advertised += [int]$d.disparity.advertisedCellCount
        $b.provenAdvertised += [int]$d.disparity.provenAdvertisedCount
        $b.withheld += @($d.withheldFeatures).Count
        $b.devices += $d.slug
        foreach ($cam in @($d.gatesByCamera)) {
            foreach ($g in @($cam.gates)) {
                if ($g.advertised -eq $true) {
                    $b.gateAdvertised++
                    if ($g.sessionOk -eq $true) { $b.gateSessionOk++ }
                }
            }
        }
    }
    $rankings = @()
    foreach ($kv in $byOem.GetEnumerator()) {
        $oem = $kv.Key
        $b = $kv.Value
        $openness = if ($b.advertised -gt 0) { [math]::Round(100.0 * $b.provenAdvertised / $b.advertised, 1) } else { 0.0 }
        $gateHonesty = if ($b.gateAdvertised -gt 0) { [math]::Round(100.0 * $b.gateSessionOk / $b.gateAdvertised, 1) } else { 0.0 }
        $composite = [math]::Round(0.6 * $openness + 0.4 * $gateHonesty, 1)
        $restrictionIndex = [math]::Round(100.0 - $composite, 1)
        $rankings += [ordered]@{
            manufacturer = $oem
            opennessPercent = $openness
            gateHonestyPercent = $gateHonesty
            compositePercent = $composite
            restrictionIndex = $restrictionIndex
            withheldFeatureCount = $b.withheld
            deviceCount = @($b.devices).Count
            deviceSlugs = @($b.devices)
        }
    }
    return @($rankings | Sort-Object restrictionIndex -Descending)
}

function Get-ResolutionSizeMp($SizeObj) {
    if (-not $SizeObj) { return 0.0 }
    if ($SizeObj.PSObject.Properties.Name -contains "mp" -and $null -ne $SizeObj.mp) {
        return [double]$SizeObj.mp
    }
    if ($SizeObj.PSObject.Properties.Name -contains "width" -and $SizeObj.PSObject.Properties.Name -contains "height") {
        $w = [double]$SizeObj.width
        $h = [double]$SizeObj.height
        if ($w -gt 0 -and $h -gt 0) { return ($w * $h) / 1000000.0 }
    }
    return 0.0
}

function Get-ResolutionDefaultMp($Entry) {
    $jpeg = Get-ResolutionSizeMp $(if ($Entry.defaultJpeg) { $Entry.defaultJpeg } else { $null })
    $raw = Get-ResolutionSizeMp $(if ($Entry.defaultRawSensor) { $Entry.defaultRawSensor } else { $null })
    return [Math]::Max($jpeg, $raw)
}

function Get-ResolutionMaxHalMp($Entry) {
    $max = 0.0
    foreach ($prop in @("highResJpeg", "maxResMapJpeg", "multiResJpeg", "highResRawSensor", "maxResMapRawSensor")) {
        if ($Entry.$prop) {
            $mp = Get-ResolutionSizeMp $Entry.$prop
            if ($mp -gt $max) { $max = $mp }
        }
    }
    if ($Entry.maxAdvertisedJpegMp) {
        $flat = [double]$Entry.maxAdvertisedJpegMp
        if ($flat -gt $max) { $max = $flat }
    }
    return $max
}

function Get-LensLineup($MatrixObj) {
    $slots = @()
    $product = $MatrixObj.product
    if ($product -and $product.focalSlots) {
        foreach ($s in @($product.focalSlots)) {
            $slots += [ordered]@{
                cameraId = [string]$s.cameraId
                focalMm35 = $s.focalMm35
                megapixels = $s.megapixels
            }
        }
    }
    return $slots
}

function Get-SpecMpByCamera($Lineup) {
    $byCam = @{}
    foreach ($s in @($Lineup)) {
        if ($null -ne $s.cameraId -and $null -ne $s.megapixels) {
            $byCam[[string]$s.cameraId] = [double]$s.megapixels
        }
    }
    return $byCam
}

function Enrich-StillResolutionHonesty($Entries, $Lineup) {
    if (-not $Entries -or $Entries.Count -eq 0) { return @() }
    $byCam = Get-SpecMpByCamera $Lineup
    $out = @()
    foreach ($e in @($Entries)) {
        $row = [ordered]@{}
        foreach ($prop in $e.PSObject.Properties) {
            $row[$prop.Name] = $prop.Value
        }
        $cid = [string]$e.cameraId
        if ($byCam.ContainsKey($cid)) {
            $row["advertisedMegapixels"] = $byCam[$cid]
        }
        $out += $row
    }
    return $out
}

function Test-ResolutionEntryBetrayed($Entry, $SpecMpByCamera, [double]$RatioThreshold = 1.25) {
    if ($Entry.hasLargerThanDefault -eq $true) { return $true }
    $default = Get-ResolutionDefaultMp $Entry
    if ($default -le 0) { return $false }
    $halMax = Get-ResolutionMaxHalMp $Entry
    if ($halMax -gt 0 -and ($halMax / $default) -ge $RatioThreshold) { return $true }
    $spec = 0.0
    if ($null -ne $Entry.advertisedMegapixels) {
        $spec = [double]$Entry.advertisedMegapixels
    }
    elseif ($SpecMpByCamera -and $Entry.cameraId) {
        $cid = [string]$Entry.cameraId
        if ($SpecMpByCamera.ContainsKey($cid)) { $spec = [double]$SpecMpByCamera[$cid] }
    }
    if ($spec -gt 0 -and ($spec / $default) -ge $RatioThreshold) { return $true }
    return $false
}

function Compute-ResolutionBetrayalIndex($Entries, $SpecMpByCamera = $null) {
    if (-not $Entries -or $Entries.Count -eq 0) { return 0 }
    $betrayed = 0
    foreach ($e in @($Entries)) {
        if (Test-ResolutionEntryBetrayed $e $SpecMpByCamera) { $betrayed++ }
    }
    return [int][math]::Round(100.0 * $betrayed / $Entries.Count, 0)
}

function Get-ResolutionBetrayal($InApp, $Matrix, $EnrichedEntries = $null, $Lineup = $null, [switch]$ForceRecompute) {
    $entries = @()
    if ($EnrichedEntries -and $EnrichedEntries.Count -gt 0) {
        $entries = @($EnrichedEntries)
    }
    elseif ($InApp -and $InApp.stillResolutionAdvertised) {
        $entries = @($InApp.stillResolutionAdvertised)
    }
    elseif ($Matrix -and $Matrix.product -and $Matrix.product.stillResolutionAdvertised) {
        $entries = @($Matrix.product.stillResolutionAdvertised)
    }

    $specMpByCamera = Get-SpecMpByCamera $(if ($Lineup) { @($Lineup) } else { @(Get-LensLineup $Matrix) })

    $index = $null
    if (-not $ForceRecompute -and $InApp -and $InApp.PSObject.Properties.Name -contains "resolutionBetrayalIndex") {
        $index = [int]$InApp.resolutionBetrayalIndex
    }
    if ($ForceRecompute -or $null -eq $index) {
        $index = if ($entries.Count -gt 0) { Compute-ResolutionBetrayalIndex $entries $specMpByCamera } else { 0 }
    }
    return [ordered]@{
        index = [int]$index
        cameraCount = $entries.Count
        entries = $entries
    }
}

function Get-OemLossSummary($Cells, $CatalogMap) {
    $withheld = @(Get-WithheldFeatures $Cells $CatalogMap)
    $shipBlockers = @($withheld | Where-Object { $_.consumerImpact -eq "SHIP_BLOCKER" })
    $delivery = @($Cells | Where-Object { $_.gap -eq "GAP_DELIVERY_MISMATCH" })
    $top = @($withheld | Select-Object -First 8)
    return [ordered]@{
        measurementApi = "camera2"
        oemCameraAppTested = $false
        shipBlockerCount = $shipBlockers.Count
        deliveryMismatchCount = $delivery.Count
        topLosses = $top
    }
}

function Get-ProductGroupId($MarketingName, $Manufacturer, $Model) {
    if (-not [string]::IsNullOrWhiteSpace($MarketingName)) {
        $slug = [regex]::Replace($MarketingName.ToLower(), '[^a-z0-9]+', '-').Trim('-')
        if ($slug.Length -gt 0) { return $slug }
    }
    $m = if ($Manufacturer) { [string]$Manufacturer } else { "unknown" }
    $mod = if ($Model) { [string]$Model } else { "unknown" }
    return ([regex]::Replace("$m-$mod".ToLower(), '[^a-z0-9]+', '-')).Trim('-')
}

function Get-CameraXSummary($Matrix) {
    $cx = $Matrix.cameraX
    if (-not $cx) { return $null }
    $byCam = $cx.availableByCamera
    $extensions = @()
    if ($byCam) {
        foreach ($prop in $byCam.PSObject.Properties) {
            foreach ($mode in @($prop.Value)) {
                if ($mode.label -and ($extensions -notcontains $mode.label)) { $extensions += [string]$mode.label }
            }
        }
    }
    return [ordered]@{
        probeComplete = ($cx.probeComplete -eq $true)
        hasAny = ($cx.hasAny -eq $true)
        extensions = $extensions
        informational = ($cx.informational -eq $true)
        honestyScore = Get-CameraXHonestyScore $extensions
    }
}

function Get-CameraXHonestyScore($Extensions) {
    $expected = @("NIGHT", "BOKEH", "HDR")
    if (-not $Extensions -or $Extensions.Count -eq 0) { return 0 }
    $found = @($Extensions | ForEach-Object { [string]$_ })
    $hit = ($expected | Where-Object { $found -contains $_ }).Count
    return [math]::Round(($hit * 100.0) / $expected.Count)
}

function Get-MeasurementContext($InApp, $Matrix) {
    $ctx = $InApp.measurementContext
    if ($ctx) {
        return [ordered]@{
            api = if ($ctx.api) { [string]$ctx.api } else { "camera2" }
            cameraXProbed = ($ctx.cameraXProbed -eq $true)
            oemCameraAppTested = ($ctx.oemCameraAppTested -eq $true)
        }
    }
    return [ordered]@{
        api = "camera2"
        cameraXProbed = ($Matrix.cameraX -ne $null)
        oemCameraAppTested = $false
    }
}

function Get-GsmarenaDeviceSpecsByModel($SpecsObj) {
    $map = @{}
    if (-not $SpecsObj -or -not $SpecsObj.devices) { return $map }
    foreach ($d in @($SpecsObj.devices)) {
        if ($d.model) { $map[[string]$d.model] = $d }
    }
    return $map
}

function Map-GsmarenaAdvertisedClaims($SpecDevice) {
    if (-not $SpecDevice) { return @() }
    $claims = @()
    $url = [string]$SpecDevice.gsmarenaUrl
    $maxMp = 0.0
    foreach ($l in @($SpecDevice.lenses)) {
        if ($l.role -eq "selfie") { continue }
        $role = if ($l.role) { [string]$l.role } else { "rear" }
        $mp = if ($l.megapixels) { [double]$l.megapixels } else { 0 }
        if ($mp -gt $maxMp) { $maxMp = $mp }
        if ($mp -gt 0) {
            $claims += [ordered]@{ catalogId = "still.resolution_$role"; advertisedValue = "$mp MP"; source = "gsmarena"; sourceUrl = $url; verified = $false }
        }
        $raw = if ($l.rawLine) { [string]$l.rawLine } else { "" }
        if ($raw -match "(?i)\bOIS\b") {
            $claims += [ordered]@{ catalogId = "lens.ois"; advertisedValue = "yes ($role)"; source = "gsmarena"; sourceUrl = $url; verified = $false }
        }
        if ($l.role -eq "tele" -or $raw -match "(?i)telephoto|periscope|optical zoom") {
            $teleVal = if ($l.focalLengthMm) { "$($l.focalLengthMm)mm tele" } else { "yes" }
            $claims += [ordered]@{ catalogId = "lens.tele"; advertisedValue = $teleVal; source = "gsmarena"; sourceUrl = $url; verified = $false }
        }
    }
    if ($maxMp -gt 0) {
        $claims += [ordered]@{ catalogId = "still.resolution_max"; advertisedValue = "$maxMp MP"; source = "gsmarena"; sourceUrl = $url; verified = $false }
    }
    $video = $SpecDevice.video
    if ($video) {
        if ($video.max4k) { $claims += [ordered]@{ catalogId = "video.4k"; advertisedValue = "yes"; source = "gsmarena"; sourceUrl = $url; verified = $false } }
        if ($video.max8k) { $claims += [ordered]@{ catalogId = "video.8k"; advertisedValue = "yes"; source = "gsmarena"; sourceUrl = $url; verified = $false } }
        if ($video.maxFps -ge 120) { $claims += [ordered]@{ catalogId = "video.hfr.120"; advertisedValue = "$($video.maxFps)fps"; source = "gsmarena"; sourceUrl = $url; verified = $false } }
    }
    if ($SpecDevice.launched) {
        $claims += [ordered]@{ catalogId = "product.launch_date"; advertisedValue = [string]$SpecDevice.launched; source = "gsmarena"; sourceUrl = $url; verified = $false }
    }
    if ($SpecDevice.priceText) {
        $claims += [ordered]@{ catalogId = "product.msrp"; advertisedValue = [string]$SpecDevice.priceText; source = "gsmarena"; sourceUrl = $url; verified = $false }
    }
    $dedup = @{}
    foreach ($c in $claims) { $dedup["$($c.catalogId)|$($c.advertisedValue)"] = $c }
    return @($dedup.Values)
}

function Build-ProductGroups($Profiles, $MarketingMap, $GsmarenaSpecsMap) {
    $byGroup = @{}
    foreach ($p in @($Profiles)) {
        $gid = Get-ProductGroupId $p.identity.marketingName $p.identity.manufacturer $p.identity.model
        if (-not $byGroup.ContainsKey($gid)) {
            $byGroup[$gid] = [ordered]@{
                groupId = $gid
                marketingName = [string]$p.identity.marketingName
                testedVariants = @()
                advertisedSpec = $null
            }
        }
        $g = $byGroup[$gid]
        $g.testedVariants += [ordered]@{
            lineItemKind = "camera2_tested"
            romFlavor = [string]$p.software.romFlavor
            slug = [string]$p.slug
            testedApiLevel = $p.meta.testedApiLevel
            parityScore = [int]$p.scores.total.score
            honestyPercent = [double]$p.disparity.honestyPercent
            trustTier = [string]$p.meta.trustTier
            resolutionBetrayalIndex = [int]$p.resolutionBetrayal.index
        }
    }
    if ($MarketingMap -and $MarketingMap.devices) {
        foreach ($m in @($MarketingMap.devices)) {
            $gid = Get-ProductGroupId $m.marketingName $null $m.model
            if (-not $byGroup.ContainsKey($gid)) {
                $byGroup[$gid] = [ordered]@{
                    groupId = $gid
                    marketingName = [string]$m.marketingName
                    testedVariants = @()
                    advertisedSpec = $null
                }
            }
            $specDev = if ($GsmarenaSpecsMap.ContainsKey([string]$m.model)) { $GsmarenaSpecsMap[[string]$m.model] } else { $null }
            if ($specDev) {
                $byGroup[$gid].advertisedSpec = [ordered]@{
                    lineItemKind = "advertised_gsmarena"
                    measurementStatus = "advertised_spec_untested"
                    trustTier = "advertised_spec"
                    source = "gsmarena"
                    gsmarenaUrl = [string]$specDev.gsmarenaUrl
                    advertisedClaims = @(Map-GsmarenaAdvertisedClaims $specDev)
                }
            }
        }
    }
    return @(
        $byGroup.Values |
            Where-Object { @($_.testedVariants).Count -gt 0 -or $null -ne $_.advertisedSpec } |
            Sort-Object { [int](@($_.testedVariants | ForEach-Object { $_.parityScore } | Measure-Object -Maximum).Maximum) } -Descending
    )
}

function Get-OemAccountability($DeviceProfiles) {
    $rankings = Get-OemRankings $DeviceProfiles
    $byOem = @{}
    foreach ($d in @($DeviceProfiles)) {
        $oem = [string]$d.identity.manufacturer
        if (-not $byOem.ContainsKey($oem)) {
            $byOem[$oem] = @{ betrayalSum = 0; betrayalCount = 0; shipBlockers = 0; withheldFeatures = @{} }
        }
        $b = $byOem[$oem]
        if ($d.resolutionBetrayal -and $null -ne $d.resolutionBetrayal.index) {
            $b.betrayalSum += [int]$d.resolutionBetrayal.index
            $b.betrayalCount++
        }
        if ($d.oemLossSummary) { $b.shipBlockers += [int]$d.oemLossSummary.shipBlockerCount }
        foreach ($w in @($d.withheldFeatures)) {
            $id = [string]$w.catalogId
            if (-not $b.withheldFeatures.ContainsKey($id)) { $b.withheldFeatures[$id] = 0 }
            $b.withheldFeatures[$id]++
        }
    }
    $out = @()
    foreach ($r in $rankings) {
        $oem = [string]$r.manufacturer
        $b = $byOem[$oem]
        $avgBetrayal = if ($b.betrayalCount -gt 0) { [math]::Round($b.betrayalSum / $b.betrayalCount, 1) } else { $null }
        $worst = @()
        if ($b.withheldFeatures.Count -gt 0) {
            $worst = @($b.withheldFeatures.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 5 | ForEach-Object {
                [ordered]@{ catalogId = $_.Key; deviceCount = $_.Value }
            })
        }
        $out += [ordered]@{
            manufacturer = $oem
            restrictionIndex = $r.restrictionIndex
            opennessPercent = $r.opennessPercent
            gateHonestyPercent = $r.gateHonestyPercent
            avgResolutionBetrayal = $avgBetrayal
            totalShipBlockers = $b.shipBlockers
            withheldFeatureCount = $r.withheldFeatureCount
            deviceCount = $r.deviceCount
            worstOffenders = $worst
            deviceSlugs = @($r.deviceSlugs)
        }
    }
    return @($out | Sort-Object restrictionIndex -Descending)
}

function Enrich-DeviceProfile($Profile, $CatalogMap) {
    if (-not $Profile) { return $Profile }
    $cells = @()
    if ($Profile.cellsByCategory) {
        foreach ($cat in $Profile.cellsByCategory.PSObject.Properties) {
            foreach ($c in @($cat.Value)) {
                $cells += [pscustomobject]@{
                    catalogId = $c.catalogId
                    advertised = $c.advertised
                    provenOk = $c.provenOk
                    gap = $c.gap
                    failReason = $c.failReason
                    consumerImpact = $c.consumerImpact
                }
            }
        }
    }
    $inApp = [pscustomobject]@{
        formatPickerHonestyScore = $Profile.formatPickerHonestyScore
        stillResolutionAdvertised = $Profile.stillResolutionHonesty
        measurementContext = $Profile.measurementContext
    }
    if (-not $Profile.measurementContext) {
        $Profile | Add-Member -NotePropertyName measurementContext -NotePropertyValue (Get-MeasurementContext $inApp $null) -Force
    }
    $lineup = @($Profile.lensLineup)
    $stillRows = @()
    if ($Profile.stillResolutionHonesty) { $stillRows = @($Profile.stillResolutionHonesty) }
    elseif ($Profile.resolutionBetrayal -and $Profile.resolutionBetrayal.entries) {
        $stillRows = @($Profile.resolutionBetrayal.entries)
    }
    if ($stillRows.Count -gt 0) {
        $enrichedStill = @(Enrich-StillResolutionHonesty $stillRows $lineup)
        $recomputed = Get-ResolutionBetrayal $inApp $null $enrichedStill $lineup -ForceRecompute
        $Profile | Add-Member -NotePropertyName stillResolutionHonesty -NotePropertyValue $enrichedStill -Force
        $Profile | Add-Member -NotePropertyName resolutionBetrayal -NotePropertyValue $recomputed -Force
    }
    elseif (-not $Profile.resolutionBetrayal) {
        $Profile | Add-Member -NotePropertyName resolutionBetrayal -NotePropertyValue (Get-ResolutionBetrayal $inApp $null) -Force
    }
    if (-not $Profile.oemLossSummary) {
        $Profile | Add-Member -NotePropertyName oemLossSummary -NotePropertyValue (Get-OemLossSummary $cells $CatalogMap) -Force
    }
    if (-not $Profile.identity.productGroupId) {
        $Profile.identity | Add-Member -NotePropertyName productGroupId -NotePropertyValue (
            Get-ProductGroupId $Profile.identity.marketingName $Profile.identity.manufacturer $Profile.identity.model
        ) -Force
    }
    if (-not $Profile.value) {
        $msrp = $Profile.identity.msrpUsd
        $parityPerUsd = $null
        if ($msrp -and [double]$msrp -gt 0) {
            $parityPerUsd = [math]::Round([double]$Profile.scores.total.score / [double]$msrp, 2)
        }
        $Profile | Add-Member -NotePropertyName value -NotePropertyValue ([ordered]@{ msrpUsd = $msrp; parityPerUsd = $parityPerUsd }) -Force
    }
    return $Profile
}
