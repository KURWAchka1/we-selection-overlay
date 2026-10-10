param([string[]]$Only = @())
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
$targets = @{
    'fabric-1.21.11' = 'WEaddon_fabric_1.21.11/run-command-test/screenshots'
    'fabric-26.1' = 'WEaddon_lightposition/run-command-test/screenshots'
    'fabric-26.2' = 'WEaddon_fabric_26.2/run-command-test/screenshots'
    'neoforge-1.21.11' = 'WEaddon_neoforge_universal_1.21.11-26.2/run-command-test-1.21.11/screenshots'
    'neoforge-26.1' = 'WEaddon_neoforge_universal_1.21.11-26.2/run-command-test-26.1/screenshots'
    'neoforge-26.2' = 'WEaddon_neoforge_universal_1.21.11-26.2/run-command-test-26.2/screenshots'
}
foreach ($name in $targets.Keys | Sort-Object) {
    if ($Only.Count -gt 0 -and $name -notin $Only) { continue }
    $file = Get-ChildItem -LiteralPath (Join-Path $root $targets[$name]) -Filter '*selection-edit-carry.png' |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (!$file) { throw "Missing carry screenshot: $name" }
    $bitmap = [Drawing.Bitmap]::new($file.FullName)
    try {
        if ($bitmap.Width -ne 1280 -or $bitmap.Height -ne 720) { throw "Unexpected fixture viewport: $name" }
        $light = 0; $dark = 0
        # Inside the fixed 2x fixture HUD. Neutral bright glyphs exclude the blue sky.
        for ($y = 534; $y -lt 580; $y++) {
            for ($x = 508; $x -lt 770; $x++) {
                $pixel = $bitmap.GetPixel($x, $y)
                $low = [Math]::Min($pixel.R, [Math]::Min($pixel.G, $pixel.B))
                $high = [Math]::Max($pixel.R, [Math]::Max($pixel.G, $pixel.B))
                if ($low -gt 180 -and $high - $low -lt 35) { $light++ }
                if ($high -lt 80) { $dark++ }
            }
        }
        if ($dark -lt 6000 -or $light -lt 120) {
            throw "Missing carry HUD background/text: $name ($dark dark, $light light pixels)"
        }
        Write-Host "$name carry HUD pixels: $dark background, $light text"
    } finally { $bitmap.Dispose() }
    $crosshair = @{}
    foreach ($state in @('visible', 'hidden', 'restored')) {
        $file = Get-ChildItem -LiteralPath (Join-Path $root $targets[$name]) -Filter "*crosshair-$state.png" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (!$file) { throw "Missing crosshair screenshot: $name/$state" }
        $bitmap = [Drawing.Bitmap]::new($file.FullName)
        try {
            if ($bitmap.Width -ne 1280 -or $bitmap.Height -ne 720) { throw "Unexpected crosshair viewport: $name" }
            $cx = 640; $cy = 360; $background = $bitmap.GetPixel($cx - 24, $cy - 24); $contrast = 0
            # The Creative fixture looks into empty sky with no selection/marker at its center.
            for ($y = $cy - 16; $y -le $cy + 16; $y++) {
                for ($x = $cx - 16; $x -le $cx + 16; $x++) {
                    $pixel = $bitmap.GetPixel($x, $y)
                    $red = [Math]::Abs([int]$pixel.R - $background.R)
                    $green = [Math]::Abs([int]$pixel.G - $background.G)
                    $blue = [Math]::Abs([int]$pixel.B - $background.B)
                    # Cloud rims differ mainly in one channel; the inverted vanilla glyph differs in two.
                    if (($red -gt 60 -and $green -gt 60) -or ($red -gt 60 -and $blue -gt 60) -or
                        ($green -gt 60 -and $blue -gt 60)) { $contrast++ }
                }
            }
            $crosshair[$state] = $contrast
        } finally { $bitmap.Dispose() }
    }
    if ($crosshair.visible -lt 50 -or $crosshair.hidden -gt 4 -or $crosshair.restored -lt $crosshair.visible * 0.9) {
        throw "Crosshair did not hide/restore: $name ($($crosshair.visible)/$($crosshair.hidden)/$($crosshair.restored) contrast pixels)"
    }
    Write-Host "$name crosshair visible/hidden/restored pixels: $($crosshair.visible)/$($crosshair.hidden)/$($crosshair.restored)"
}
