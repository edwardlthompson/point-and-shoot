# Shared CHANGELOG helpers for pns_github_release.ps1 (dot-source; no params).

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
    if ($ChangelogText -notmatch "(?ms)^$([regex]::Escape($header)) - .*?\r?\n(.*?)(?=^## (?:Unreleased|\[)|\z)") {
        return $null
    }
    return $Matches[1].Trim()
}

function Get-FdroidChangelogSnippet([string]$Section, [string]$SemverTag) {
    $bullets = @(
        $Section -split "`r?`n" |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_ -match '^- ' }
    )
    if ($bullets.Count -eq 0) {
        return "Point & Shoot $SemverTag"
    }
    $plain = (
        $bullets |
            Select-Object -First 3 |
            ForEach-Object {
                ($_ -replace '^\-\s+', '' -replace '\*\*', '').Trim()
            }
    ) -join " "
    if ($plain.Length -gt 480) {
        return $plain.Substring(0, 477) + "..."
    }
    return $plain
}
