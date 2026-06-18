# Resolve fleet Kotlin sources after Sprint TM (:pns-fleet module vs legacy :app paths).

function Resolve-PnsFleetMainKt {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FileName,
        [string]$ProjectRoot = ""
    )
    if ([string]::IsNullOrWhiteSpace($ProjectRoot)) {
        $ProjectRoot = Split-Path -Parent $PSScriptRoot
    }
    $candidates = @(
        (Join-Path $ProjectRoot "modules\pns-fleet\src\main\java\dev\pointandshoot\fleet\$FileName"),
        (Join-Path $ProjectRoot "app\src\main\java\dev\pointandshoot\fleet\$FileName")
    )
    foreach ($path in $candidates) {
        if (Test-Path -LiteralPath $path) { return (Resolve-Path -LiteralPath $path).Path }
    }
    throw "Missing fleet source $FileName (checked: $($candidates -join '; '))"
}
