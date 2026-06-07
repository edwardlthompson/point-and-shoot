# AnTuTu sample aggregation for leaderboard publish (dot-source from pns_leaderboard_site_publish.ps1).

function Get-AntutuAliases($MarketingEntry) {
    $aliases = @()
    if ($MarketingEntry) {
        if ($MarketingEntry.marketingName) { $aliases += [string]$MarketingEntry.marketingName }
        if ($MarketingEntry.antutuAliases) { $aliases += @($MarketingEntry.antutuAliases) }
    }
    return @($aliases | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Test-AntutuSampleMatchesModel($Sample, [string]$Model, $MarketingEntry) {
    $sm = if ($Sample.model) { [string]$Sample.model } else { "" }
    if ($sm -and $sm -eq $Model) { return $true }
    $aliases = Get-AntutuAliases $MarketingEntry
    $sMarketing = if ($Sample.marketingName) { [string]$Sample.marketingName } else { "" }
    foreach ($a in $aliases) {
        if ($sMarketing -and ($sMarketing -like "*$a*" -or $a -like "*$sMarketing*")) { return $true }
    }
    return $false
}

function Get-AntutuAggregateForModel($SamplesObj, [string]$Model, $MarketingEntry) {
    if (-not $SamplesObj -or -not $SamplesObj.samples) { return $null }
    $matched = @($SamplesObj.samples | Where-Object { Test-AntutuSampleMatchesModel $_ $Model $MarketingEntry })
    if ($matched.Count -eq 0) { return $null }
    $totals = @($matched | ForEach-Object { [double]$_.total } | Where-Object { $_ -ge 50000 -and $_ -le 4500000 })
    if ($totals.Count -eq 0) { return $null }

    function Get-Mean($values) {
        if (-not $values -or $values.Count -eq 0) { return $null }
        return ($values | Measure-Object -Average).Average
    }
    function Get-StdDev($values) {
        if (-not $values -or $values.Count -lt 2) { return $null }
        $avg = Get-Mean $values
        $sum = 0.0
        foreach ($v in $values) { $sum += ([double]$v - $avg) * ([double]$v - $avg) }
        return [math]::Sqrt($sum / $values.Count)
    }

    $cpuVals = @($matched | Where-Object { $null -ne $_.cpu } | ForEach-Object { [double]$_.cpu })
    $gpuVals = @($matched | Where-Object { $null -ne $_.gpu } | ForEach-Object { [double]$_.gpu })
    $memVals = @($matched | Where-Object { $null -ne $_.mem } | ForEach-Object { [double]$_.mem })
    $uxVals = @($matched | Where-Object { $null -ne $_.ux } | ForEach-Object { [double]$_.ux })

    $sourceBreakdown = @{}
    $lastUtc = ""
    foreach ($s in $matched) {
        $src = if ($s.source) { [string]$s.source } else { "unknown" }
        if ($sourceBreakdown.ContainsKey($src)) { $sourceBreakdown[$src]++ } else { $sourceBreakdown[$src] = 1 }
        $sub = if ($s.submittedUtc) { [string]$s.submittedUtc } else { "" }
        if ($sub -gt $lastUtc) { $lastUtc = $sub }
    }

    $matchedName = $null
    if ($MarketingEntry -and $MarketingEntry.marketingName) { $matchedName = [string]$MarketingEntry.marketingName }
    if (-not $matchedName) {
        foreach ($s in $matched) {
            if ($s.marketingName) { $matchedName = [string]$s.marketingName; break }
        }
    }

    $cpuMean = Get-Mean $cpuVals
    $gpuMean = Get-Mean $gpuVals
    $memMean = Get-Mean $memVals
    $uxMean = Get-Mean $uxVals
    $totalStd = Get-StdDev $totals

    return [ordered]@{
        totalMean = [int][math]::Round((Get-Mean $totals))
        cpuMean = if ($cpuMean) { [int][math]::Round($cpuMean) } else { $null }
        gpuMean = if ($gpuMean) { [int][math]::Round($gpuMean) } else { $null }
        memMean = if ($memMean) { [int][math]::Round($memMean) } else { $null }
        uxMean = if ($uxMean) { [int][math]::Round($uxMean) } else { $null }
        sampleCount = $matched.Count
        totalStdDev = if ($totalStd) { [int][math]::Round($totalStd) } else { $null }
        sourceBreakdown = $sourceBreakdown
        lastSubmittedUtc = if ($lastUtc) { $lastUtc } else { $null }
        matchedName = $matchedName
    }
}

function New-AntutuProfileBlock($Agg) {
    if (-not $Agg) { return $null }
    return [ordered]@{
        total = [int]$Agg.totalMean
        cpu = $Agg.cpuMean
        gpu = $Agg.gpuMean
        mem = $Agg.memMean
        ux = $Agg.uxMean
        matchedName = $Agg.matchedName
        matchConfidence = "fleet_sample_mean"
        sampleCount = $Agg.sampleCount
        sourceBreakdown = $Agg.sourceBreakdown
        totalStdDev = $Agg.totalStdDev
        lastSubmittedUtc = $Agg.lastSubmittedUtc
        source = "cross_source_samples"
    }
}

function Merge-AntutuSamplesFromSubmissions($SamplesObj, [string]$ApprovedDir, $MarketingMap) {
    if (-not (Test-Path -LiteralPath $ApprovedDir)) { return $SamplesObj }
    if (-not $SamplesObj) { $SamplesObj = [ordered]@{ schema = "pns.antutu_samples.v1"; samples = @() } }
    $list = [System.Collections.ArrayList]@()
    foreach ($s in @($SamplesObj.samples)) { [void]$list.Add($s) }
    $existingSubmissionIds = @{}
    foreach ($s in @($SamplesObj.samples)) {
        if ($s.submissionId) { $existingSubmissionIds[[string]$s.submissionId] = $true }
    }
    $changed = $false
    foreach ($f in Get-ChildItem -LiteralPath $ApprovedDir -Filter "*.json") {
        try {
            $sub = Get-Content -LiteralPath $f.FullName -Raw | ConvertFrom-Json
        } catch { continue }
        $score = $sub.antutuScore
        if (-not $score -or $null -eq $score.total) { continue }
        $submissionId = if ($sub.submissionId) { [string]$sub.submissionId } else { [string]$f.BaseName }
        if ($existingSubmissionIds.ContainsKey($submissionId)) { continue }
        $model = $null
        if ($sub.matrix -and $sub.matrix.device) { $model = [string]$sub.matrix.device.model }
        if (-not $model) { continue }
        $marketing = Get-MarketingEntry $MarketingMap $model
        $sample = [ordered]@{
            sampleId = "community-$submissionId"
            model = $model
            marketingName = if ($marketing) { [string]$marketing.marketingName } else { $null }
            deviceSlug = if ($sub.clientMeta -and $sub.clientMeta.publicDeviceSlug) { [string]$sub.clientMeta.publicDeviceSlug } else { $null }
            source = "community_submit"
            trustTier = "community"
            submissionId = $submissionId
            submittedUtc = if ($sub.submittedUtc) { [string]$sub.submittedUtc } else { [DateTime]::UtcNow.ToString("o") }
            buildDisplay = if ($sub.buildDisplay) { [string]$sub.buildDisplay } else { $null }
            total = [int]$score.total
            cpu = $score.cpu
            gpu = $score.gpu
            mem = $score.mem
            ux = $score.ux
            antutuAppVersion = $score.antutuAppVersion
        }
        [void]$list.Add($sample)
        $existingSubmissionIds[$submissionId] = $true
        $changed = $true
    }
    if ($changed) {
        $SamplesObj.samples = @($list)
    }
    return $SamplesObj
}

function Match-AntutuFromSamples($SamplesObj, $ScrapeObj, [string]$Model, $MarketingEntry) {
    $agg = Get-AntutuAggregateForModel $SamplesObj $Model $MarketingEntry
    if ($agg) {
        return New-AntutuProfileBlock $agg
    }
    if (-not $ScrapeObj -or -not $ScrapeObj.rankings) { return $null }
    $aliases = Get-AntutuAliases $MarketingEntry
    foreach ($rank in @($ScrapeObj.rankings)) {
        $name = [string]$rank.deviceName
        if ($name -eq "unknown") { continue }
        foreach ($a in $aliases) {
            if ($name -like "*$a*" -or $a -like "*$name*") {
                return [ordered]@{
                    total = $rank.total
                    cpu = $rank.cpu; gpu = $rank.gpu; mem = $rank.mem; ux = $rank.ux
                    matchedName = $name
                    matchConfidence = "web_scrape_fallback"
                    sourceMonth = $ScrapeObj.sourceMonth
                    source = "web_scrape"
                }
            }
        }
    }
    return $null
}

function Save-AntutuSamplesFile($SamplesObj, [string]$Path) {
    $json = ($SamplesObj | ConvertTo-Json -Depth 8)
    [System.IO.File]::WriteAllText($Path, $json, [System.Text.UTF8Encoding]::new($false))
}
