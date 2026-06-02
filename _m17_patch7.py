from pathlib import Path
p = Path("scripts/pns_fleet_matrix_scan.ps1")
t = p.read_text(encoding="utf-8")
if "fleet_device_capability_summary" not in t:
    insert = '''
$summaryFile = "fleet_device_capability_summary.md"
$summaryOut = Join-Path $OutDir $summaryFile
$summaryPulled = $false
if ($pulled) {
    try {
        if ($Serial) {
            & adb -s $Serial exec-out run-as $pkg cat "files/$summaryFile" | Set-Content -LiteralPath $summaryOut -Encoding utf8
        } else {
            & adb exec-out run-as $pkg cat "files/$summaryFile" | Set-Content -LiteralPath $summaryOut -Encoding utf8
        }
        if ((Test-Path -LiteralPath $summaryOut) -and ((Get-Item -LiteralPath $summaryOut).Length -gt 32)) {
            $summaryPulled = $true
            Write-Host "[fleet_matrix] Pulled summary -> $summaryOut"
        }
    } catch {
        Write-Warning "[fleet_matrix] summary pull failed: $_"
    }
}

'''
    t = t.replace("$tierOk = ($scanTierObserved -eq $ScanTier)", insert + "$tierOk = ($scanTierObserved -eq $ScanTier)")
    t = t.replace("    matrixRelPath     = $matrixFile", "    matrixRelPath     = $matrixFile\n    summaryRelPath    = $summaryFile\n    summaryPulled     = $summaryPulled")
    p.write_text(t, encoding="utf-8")
    print("scan script updated")
