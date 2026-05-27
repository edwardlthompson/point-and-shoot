#Requires -Version 5.1
<#
.SYNOPSIS
  Sprint **15.15** — rawpy decode pulled DNGs from latest hfr-run (host) or SKIP.
#>
param(
    [string]$RunDir
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    $dir = $RunDir
    if (-not $dir) {
        $latest = Get-ChildItem -Path "$root\hfr-runs" -Directory -Filter "aux_dng_capture_analyze_*" -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($latest) { $dir = $latest.FullName }
    }
    if (-not $dir -or -not (Test-Path $dir)) {
        Write-Host "DNG RAWPY GATE: SKIP (no hfr-run dir)"
        exit 0
    }
    python -c @"
import sys
from pathlib import Path
try:
    import rawpy
except ImportError:
    print('DNG RAWPY GATE: SKIP (rawpy not installed)')
    sys.exit(0)
root = Path(r'$dir')
dngs = list(root.glob('**/*.dng')) + list(root.glob('**/*.DNG'))
if not dngs:
    print('DNG RAWPY GATE: SKIP (no DNG files)')
    sys.exit(0)
for p in dngs[:6]:
    with rawpy.imread(str(p)) as r:
        post = r.postprocess()
        assert post.size > 0, p.name
        assert post.mean() > 0.01, p.name
print(f'DNG RAWPY GATE: PASS ({len(dngs)} files, sampled {min(6,len(dngs))})')
"@
    exit $LASTEXITCODE
} finally {
    Pop-Location
}
