#Requires -Version 5.1
<#
.SYNOPSIS
  Prepare and publish GitHub releases for Point & Shoot.

.DESCRIPTION
  -PrepareOnly: bump versionCode/versionName, cut CHANGELOG Unreleased -> dated section,
                sync scripts/changelog_coverage.v1.json, run pns_changelog_gate.ps1.
  -Publish:     assembleRelease, package APK, git tag, gh release with changelog body + assets.
  Default (no switch): Prepare then Publish.

  Agent workflow: when the user asks for a GitHub release, run -PrepareOnly first (review diff),
  commit, then -Publish (or -Publish -SkipPrepare after commit).

.PARAMETER PrepareOnly
  Version + changelog prep only; no build or GitHub upload.

.PARAMETER Publish
  Build + tag + gh release (runs Prepare first unless -SkipPrepare).

.PARAMETER SkipPrepare
  Publish using current CHANGELOG / coverage / gradle versions as-is.

.PARAMETER Tag
  Semver without leading v (default: auto-increment beta from coverage manifest).

.PARAMETER Date
  Release date YYYY-MM-DD (default: today, local).

.PARAMETER VersionCode
  Override versionCode (default: current + versionCodeStep from release_config).

.PARAMETER Summary
  One-line blurb inserted under the release header in CHANGELOG.

.PARAMETER Draft
  Create GitHub release as draft.

.PARAMETER Prerelease
  Mark GitHub release pre-release (default from release_config when omitted).

.PARAMETER SkipBuild
  Use existing release APK in app/build/outputs or dist/.

.PARAMETER SkipGitTag
  Do not create or push git tag (gh release still uses -Tag).

.PARAMETER AllowEmptyUnreleased
  Permit cutting a release when Unreleased has no user bullets.

.PARAMETER DryRun
  Print planned actions without writing files or calling gh.

.EXAMPLE
  .\scripts\pns_github_release.ps1 -PrepareOnly

.EXAMPLE
  .\scripts\pns_github_release.ps1 -Publish -SkipPrepare -Prerelease
#>
[CmdletBinding(SupportsShouldProcess = $true)]
param(
    [switch]$PrepareOnly,
    [switch]$Publish,
    [switch]$SkipPrepare,
    [string]$Tag = "",
    [string]$Date = "",
    [int]$VersionCode = 0,
    [string]$Summary = "",
    [switch]$Draft,
    [switch]$Prerelease,
    [switch]$SkipBuild,
    [switch]$SkipGitTag,
    [switch]$AllowEmptyUnreleased,
    [switch]$DryRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot\pns_release_naming.ps1"

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$configPath = Join-Path $PSScriptRoot "release_config.v1.json"
$coveragePath = Join-Path $PSScriptRoot "changelog_coverage.v1.json"
$changelogPath = Join-Path $repoRoot "CHANGELOG.md"
$gradlePath = Join-Path $repoRoot "app\build.gradle.kts"
$externalUrlPath = Join-Path $repoRoot "app\src\main\java\dev\pointandshoot\PnsExternalUrl.kt"

function Write-Step([string]$Message) {
    Write-Host "[pns_github_release] $Message"
}

function Read-JsonFile([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing file: $Path"
    }
    return ([System.IO.File]::ReadAllText($Path) | ConvertFrom-Json)
}

function Get-GradleVersionCode([string]$GradleText) {
    if ($GradleText -match 'versionCode\s*=\s*(\d+)') {
        return [int]$Matches[1]
    }
    throw "Could not parse versionCode from app/build.gradle.kts"
}

function Get-GradleVersionName([string]$GradleText) {
    if ($GradleText -match 'versionName\s*=\s*"([^"]+)"') {
        return ConvertTo-PnsSemverTag $Matches[1]
    }
    throw "Could not parse versionName from app/build.gradle.kts"
}

function Get-ReleaseApkFileName([object]$Config, [string]$SemverTag) {
    return Get-PnsReleaseApkFileName `
        -VersionName $SemverTag `
        -AppDisplayName ([string]$Config.appDisplayName) `
        -Template ([string]$Config.apkFileNameTemplate)
}

function Get-UnreleasedBody([string]$ChangelogText) {
    if ($ChangelogText -notmatch '(?ms)^## Unreleased\s*\r?\n(.*?)(?=^## \[|\z)') {
        throw "CHANGELOG.md missing ## Unreleased section"
    }
    return $Matches[1].Trim()
}

function Test-UnreleasedHasContent([string]$Body) {
    $lines = @(
        $Body -split "`r?`n" |
            ForEach-Object { $_.Trim() } |
            Where-Object {
                $_ -and
                $_ -notmatch '^\(_Nothing yet' -and
                $_ -notmatch '^_\(' -and
                $_ -ne '_'
            }
    )
    return ($lines.Length -gt 0)
}

function Get-ChangelogSectionForTag([string]$ChangelogText, [string]$SemverTag) {
    $header = "## [$SemverTag]"
    if ($ChangelogText -notmatch "(?ms)^$([regex]::Escape($header)) - .*?\r?\n(.*?)(?=^## \[|\z)") {
        return $null
    }
    $section = $Matches[1].Trim()
    return $section
}

function Get-GitHubTag([string]$SemverTag, [string]$Prefix) {
    return ConvertTo-PnsGitTag -VersionName $SemverTag -TagPrefix $Prefix
}

function Update-GradleFile([string]$Path, [int]$NewCode, [string]$NewName, [switch]$WhatIf) {
    $text = [System.IO.File]::ReadAllText($Path)
    $text = [regex]::Replace($text, 'versionCode\s*=\s*\d+', "versionCode = $NewCode")
    $text = [regex]::Replace($text, 'versionName\s*=\s*"[^"]*"', "versionName = `"$NewName`"")
    if ($WhatIf) {
        Write-Step "Would update gradle: versionCode=$NewCode versionName=$NewName"
        return
    }
    [System.IO.File]::WriteAllText($Path, $text)
    Write-Step "Updated app/build.gradle.kts (versionCode=$NewCode, versionName=$NewName)"
}

function Update-CoverageManifest(
    [string]$Path,
    [string]$SemverTag,
    [string]$ReleaseDate,
    [int]$NewCode,
    [switch]$WhatIf
) {
    $json = Read-JsonFile $Path
    $json.latestRelease.tag = $SemverTag
    $json.latestRelease.date = $ReleaseDate
    $json.latestRelease.versionCode = $NewCode
    if ($WhatIf) {
        Write-Step "Would update coverage: tag=$SemverTag date=$ReleaseDate versionCode=$NewCode"
        return
    }
    ($json | ConvertTo-Json -Depth 6) + "`n" | Set-Content -Path $Path -Encoding UTF8 -NoNewline
    Write-Step "Updated scripts/changelog_coverage.v1.json"
}

function Update-ExternalUrlLatestTag([string]$Path, [string]$SemverTag, [switch]$WhatIf) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing PnsExternalUrl.kt at $Path"
    }
    $text = [System.IO.File]::ReadAllText($Path)
    if ($text -notmatch 'const val PNS_GITHUB_LATEST_RELEASE_TAG') {
        throw "PnsExternalUrl.kt missing PNS_GITHUB_LATEST_RELEASE_TAG — add GitHub release constants first."
    }
    $replacement = "const val PNS_GITHUB_LATEST_RELEASE_TAG: String = `"$SemverTag`""
    $newText = [regex]::Replace(
        $text,
        'const val PNS_GITHUB_LATEST_RELEASE_TAG: String = "[^"]*"',
        $replacement
    )
    if ($WhatIf) {
        Write-Step "Would update PNS_GITHUB_LATEST_RELEASE_TAG=$SemverTag"
        return
    }
    [System.IO.File]::WriteAllText($Path, $newText)
    Write-Step "Updated PNS_GITHUB_LATEST_RELEASE_TAG=$SemverTag"
}

function Cut-ChangelogRelease(
    [string]$Path,
    [string]$SemverTag,
    [string]$ReleaseDate,
    [string]$SummaryLine,
    [string]$ApkFileName,
    [switch]$AllowEmpty,
    [switch]$WhatIf
) {
    $text = [System.IO.File]::ReadAllText($Path)
    $unreleased = Get-UnreleasedBody $text
    if (-not (Test-UnreleasedHasContent $unreleased)) {
        if (-not $AllowEmpty) {
            throw @"
Unreleased section in CHANGELOG.md has no user-visible bullets.
Add notes under ## Unreleased, or pass -AllowEmptyUnreleased to cut an empty release.
"@
        }
        $unreleased = "_(No user-visible changes in this drop — version bump / packaging only.)_"
    }

    $summaryBlock = ""
    if (-not [string]::IsNullOrWhiteSpace($SummaryLine)) {
        $summaryBlock = "$SummaryLine`n`n"
    }
    $apkLine = "**Release notes:** **APK:** ``$ApkFileName``"
    $newSection = @"
## [$SemverTag] - $ReleaseDate

$summaryBlock$apkLine

$unreleased

"@

    $placeholder = @"
## Unreleased

_(Nothing yet — add user-visible deltas here; run ``pns_changelog_gate.ps1`` before milestone gates.)_

"@

    $unreleasedPattern = '(?ms)^## Unreleased\s*\r?\n.*?(?=^## \[|\z)'
    $regex = [regex]::new($unreleasedPattern)
    $match = $regex.Match($text)
    if (-not $match.Success) {
        throw "Could not locate Unreleased block to replace in CHANGELOG.md"
    }

    $afterUnreleased = $text.Substring($match.Index + $match.Length)
    $beforeUnreleased = $text.Substring(0, $match.Index)
    $newText = $beforeUnreleased + $newSection + $placeholder + $afterUnreleased.TrimStart("`r", "`n")

    if ($WhatIf) {
        Write-Step "Would cut CHANGELOG release [$SemverTag] - $ReleaseDate"
        return
    }
    [System.IO.File]::WriteAllText($Path, $newText)
    Write-Step "Cut CHANGELOG section [$SemverTag] - $ReleaseDate"
}

function Invoke-PreparePhase {
    param(
        [object]$Config,
        [object]$Coverage,
        [string]$SemverTag,
        [string]$ReleaseDate,
        [int]$NewCode,
        [string]$SummaryLine
    )

    $apkName = Get-ReleaseApkFileName -Config $Config -SemverTag $SemverTag

    Cut-ChangelogRelease -Path $changelogPath `
        -SemverTag $SemverTag `
        -ReleaseDate $ReleaseDate `
        -SummaryLine $SummaryLine `
        -ApkFileName $apkName `
        -AllowEmpty:$AllowEmptyUnreleased `
        -WhatIf:$DryRun

    Update-GradleFile -Path $gradlePath -NewCode $NewCode -NewName $SemverTag -WhatIf:$DryRun
    Update-CoverageManifest -Path $coveragePath -SemverTag $SemverTag -ReleaseDate $ReleaseDate -NewCode $NewCode -WhatIf:$DryRun
    Update-ExternalUrlLatestTag -Path $externalUrlPath -SemverTag $SemverTag -WhatIf:$DryRun

    if (-not $DryRun) {
        & (Join-Path $PSScriptRoot "pns_changelog_gate.ps1") -ProjectRoot $repoRoot
        if ($LASTEXITCODE -ne 0) {
            throw "pns_changelog_gate.ps1 failed after prepare"
        }
    }
}

function Invoke-PublishPhase {
    param(
        [object]$Config,
        [object]$Coverage,
        [string]$SemverTag,
        [string]$GitTag,
        [bool]$IsPrerelease,
        [bool]$IsDraft
    )

    $owner = [string]$Config.github.owner
    $repo = [string]$Config.github.repo
    $ghRepo = "$owner/$repo"

    $changelogText = [System.IO.File]::ReadAllText($changelogPath)
    $sectionBody = Get-ChangelogSectionForTag $changelogText $SemverTag
    if ([string]::IsNullOrWhiteSpace($sectionBody)) {
        throw "CHANGELOG.md missing body for [$SemverTag] — run -PrepareOnly first."
    }

    $releaseNotes = @"
# Point & Shoot $SemverTag

Full changelog: https://github.com/$owner/$repo/blob/main/CHANGELOG.md

$sectionBody
"@

    $artifactApk = $null
    if (-not $SkipBuild) {
        if ($DryRun) {
            Write-Step "Would run pns_release_packaging.ps1"
        } else {
            & (Join-Path $PSScriptRoot "pns_release_packaging.ps1")
            if ($LASTEXITCODE -ne 0) {
                throw "pns_release_packaging.ps1 failed"
            }
        }
    }

    $distApk = Join-Path $repoRoot ("dist\{0}" -f (Get-ReleaseApkFileName -Config $Config -SemverTag $SemverTag))
    $builtApk = Join-Path $repoRoot "app\build\outputs\apk\release\app-release.apk"
    if (Test-Path -LiteralPath $distApk) {
        $artifactApk = $distApk
    } elseif (Test-Path -LiteralPath $builtApk) {
        $artifactApk = $builtApk
    }

    if (-not $DryRun -and -not $artifactApk) {
        throw "Release APK not found. Run without -SkipBuild or place APK at $distApk"
    }

    if (-not $SkipGitTag -and -not $DryRun) {
        $existing = & git -C $repoRoot tag -l $GitTag 2>$null
        if ($existing) {
            Write-Step "Git tag $GitTag already exists — skipping tag create"
        } else {
            if ($PSCmdlet.ShouldProcess($GitTag, "Create annotated git tag")) {
                & git -C $repoRoot tag -a $GitTag -m "Release $SemverTag"
                Write-Step "Created git tag $GitTag (push with: git push origin $GitTag)"
            }
        }
    }

    $gh = Get-Command gh -ErrorAction SilentlyContinue
    if (-not $gh -and -not $DryRun) {
        throw "gh CLI not found. Install from https://cli.github.com/ or use -PrepareOnly."
    }

    if ($DryRun) {
        Write-Step "Would create GitHub release $GitTag on $ghRepo (prerelease=$IsPrerelease draft=$IsDraft)"
        Write-Step "Release notes length: $($releaseNotes.Length) chars"
        if ($artifactApk) { Write-Step "Would upload: $artifactApk" }
        Write-Step "Would attach: CHANGELOG.md"
        return
    }

    $notesFile = [System.IO.Path]::GetTempFileName()
    try {
        [System.IO.File]::WriteAllText($notesFile, $releaseNotes)

        $ghArgs = @(
            "release", "create", $GitTag,
            "--repo=$ghRepo",
            "--title=$SemverTag",
            "--notes-file=$notesFile"
        )
        if ($IsDraft) { $ghArgs += "--draft" }
        if ($IsPrerelease) { $ghArgs += "--prerelease" }

        if ($artifactApk) {
            $ghArgs += $artifactApk
        }
        $ghArgs += $changelogPath

        if ($PSCmdlet.ShouldProcess("$GitTag on $ghRepo", "Create GitHub release")) {
            $out = & gh @ghArgs 2>&1 | Out-String
            Write-Host $out
        }
    } finally {
        if (Test-Path -LiteralPath $notesFile) {
            Remove-Item -LiteralPath $notesFile -Force
        }
    }

    $runsDir = Join-Path $repoRoot "hfr-runs"
    if (-not (Test-Path -LiteralPath $runsDir)) {
        New-Item -ItemType Directory -Path $runsDir -Force | Out-Null
    }
    $resultPath = Join-Path $runsDir ("github_release_{0:yyyyMMdd_HHmmss}.json" -f (Get-Date))
    @{
        timestamp = (Get-Date -Format "o")
        semverTag = $SemverTag
        gitTag = $GitTag
        repo = $ghRepo
        prerelease = $IsPrerelease
        draft = $IsDraft
        apk = $artifactApk
        changelogAttached = $true
    } | ConvertTo-Json -Depth 4 | Set-Content -Path $resultPath -Encoding UTF8
    Write-Step "Wrote $resultPath"
}

# --- main ---

$config = Read-JsonFile $configPath
$coverage = Read-JsonFile $coveragePath
$gradleText = [System.IO.File]::ReadAllText($gradlePath)

$currentTag = [string]$coverage.latestRelease.tag
$currentCode = Get-GradleVersionCode $gradleText

if ([string]::IsNullOrWhiteSpace($Tag)) {
    $Tag = Get-PnsNextSemverVersionName -CurrentVersionName $currentTag
}
$SemverTag = ConvertTo-PnsSemverTag $Tag

if ([string]::IsNullOrWhiteSpace($Date)) {
    $Date = (Get-Date -Format "yyyy-MM-dd")
}

if ($VersionCode -le 0) {
    $policy = [string]$config.versionBump.versionCodePolicy
    if ([string]::IsNullOrWhiteSpace($policy)) { $policy = "semverOrIncrement" }
    $VersionCode = Get-PnsNextVersionCode `
        -VersionName $SemverTag `
        -CurrentVersionCode $currentCode `
        -Policy $policy `
        -Step ([int]$config.versionBump.versionCodeStep)
}

$gitTagPrefix = [string]$config.github.tagPrefix
$GitTag = Get-GitHubTag $SemverTag $gitTagPrefix

$doPrepare = $PrepareOnly -or (-not $SkipPrepare -and ($Publish -or (-not $PrepareOnly -and -not $Publish)))
$doPublish = $Publish -or (-not $PrepareOnly -and -not $Publish)

if ($PSBoundParameters.ContainsKey('Prerelease')) {
    $isPrerelease = $Prerelease.IsPresent
} else {
    $isPrerelease = [bool]$config.versionBump.defaultPrerelease
}

Write-Step "Plan: prepare=$doPrepare publish=$doPublish tag=$SemverTag gitTag=$GitTag versionCode=$VersionCode date=$Date"

Push-Location $repoRoot
try {
    if ($doPrepare) {
        Invoke-PreparePhase -Config $config -Coverage $coverage `
            -SemverTag $SemverTag -ReleaseDate $Date -NewCode $VersionCode -SummaryLine $Summary
    }

    if ($doPublish) {
        if ($doPrepare -and -not $DryRun) {
            $coverage = Read-JsonFile $coveragePath
        }
        Invoke-PublishPhase -Config $config -Coverage $coverage `
            -SemverTag $SemverTag -GitTag $GitTag -IsPrerelease $isPrerelease -IsDraft:$Draft.IsPresent
    }

    if ($PrepareOnly -and -not $Publish) {
        Write-Step "PREPARE_DONE — review diff, commit, then run with -Publish -SkipPrepare"
    } elseif ($doPublish) {
        Write-Step "RELEASE_DONE tag=$GitTag"
        if (-not $SkipGitTag) {
            Write-Step "Remember: git push origin main && git push origin $GitTag"
        }
    }
    exit 0
} finally {
    Pop-Location
}
