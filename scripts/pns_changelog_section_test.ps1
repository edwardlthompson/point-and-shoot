# Host fixture for Get-ChangelogSectionForTag — must not include ## Unreleased.
#
# Usage:
#   .\scripts\pns_changelog_section_test.ps1

param(
  [string]$ProjectRoot = ""
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
  $ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
} else {
  $ProjectRoot = (Resolve-Path -LiteralPath $ProjectRoot).Path
}

. (Join-Path $PSScriptRoot "pns_github_release_lib.ps1")

$fixture = @"
# Changelog

## Unreleased

- leftover placeholder

## [0.14.0-beta.21] - 2026-08-20

Quiet Venmo donate.

### Added

- Donate via Venmo

## [0.14.0-beta.20] - 2026-08-07

Older notes.
"@

$errors = 0
$section = Get-ChangelogSectionForTag -ChangelogText $fixture -SemverTag "0.14.0-beta.21"
if ($null -eq $section) {
    Write-Host "FAIL: section for 0.14.0-beta.21 was null"
    $errors++
} elseif ($section -match 'Unreleased' -or $section -match 'leftover placeholder') {
    Write-Host "FAIL: Unreleased leaked into dated section"
    $errors++
} elseif ($section -notmatch 'Donate via Venmo') {
    Write-Host "FAIL: expected donate bullet in beta.21 section"
    $errors++
} elseif ($section -match 'Older notes') {
    Write-Host "FAIL: next dated section leaked into beta.21"
    $errors++
}

$unreleased = Get-UnreleasedBody $fixture
if ($unreleased -notmatch 'leftover placeholder') {
    Write-Host "FAIL: Unreleased body missing placeholder"
    $errors++
}

if ($errors -gt 0) {
    Write-Host "CHANGELOG SECTION TEST: FAIL ($errors)"
    exit 1
}

Write-Host "CHANGELOG SECTION TEST: PASS"
exit 0
