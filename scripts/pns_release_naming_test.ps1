#Requires -Version 5.1
# Host unit checks for scripts/pns_release_naming.ps1 (wired into pns_changelog_gate.ps1).
$ErrorActionPreference = "Stop"
. "$PSScriptRoot\pns_release_naming.ps1"

$failures = New-Object System.Collections.Generic.List[string]

function Expect-Eq([string]$Actual, [string]$Expected, [string]$Label) {
    if ($Actual -ne $Expected) {
        $failures.Add("FAIL: $Label expected='$Expected' actual='$Actual'")
    }
}

Expect-Eq (Get-PnsNextSemverVersionName "0.14.0-beta.22") "0.14.0" "graduate beta to stable"
Expect-Eq (Get-PnsNextSemverVersionName "0.14.0-rc.1") "0.14.0" "graduate rc to stable"
Expect-Eq (Get-PnsNextSemverVersionName "0.14.0") "0.14.1" "stable patch increment"
Expect-Eq (Get-PnsReleaseTitle "0.14.0") "Point & Shoot 0.14.0" "default release title"
Expect-Eq (Get-PnsReleaseApkFileName -VersionName "0.14.0") "Point-and-Shoot-0.14.0.apk" "stable APK name"
Expect-Eq (ConvertTo-PnsGitTag "0.14.0") "v0.14.0" "git tag"

if ($failures.Count -gt 0) {
    foreach ($line in $failures) { Write-Host $line }
    exit 1
}
Write-Host "OK: pns_release_naming_test.ps1"
exit 0
