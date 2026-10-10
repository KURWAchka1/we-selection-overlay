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
    foreach ($requestedScale in @(2, 3, 4)) {
        $file = Get-ChildItem -LiteralPath (Join-Path $root $targets[$name]) -Filter "*selection-edit-axes-x$requestedScale.png" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (!$file) { throw "Missing axis compass screenshot: $name/$requestedScale" }
        $bitmap = [Drawing.Bitmap]::new($file.FullName)
        try {
            $scale = [Math]::Min($requestedScale, [Math]::Min([Math]::Floor($bitmap.Width / 320), [Math]::Floor($bitmap.Height / 240)))
            $red = 0; $green = 0; $blue = 0
            for ($y = $bitmap.Height - 108 * $scale; $y -lt $bitmap.Height - 24 * $scale; $y++) {
                for ($x = $bitmap.Width - 84 * $scale; $x -lt $bitmap.Width; $x++) {
                    $p = $bitmap.GetPixel($x, $y)
                    if ($p.R -gt 210 -and $p.G -gt 80 -and $p.G -lt 155 -and $p.B -gt 80 -and $p.B -lt 170) { $red++ }
                    if ($p.G -gt 190 -and $p.R -gt 110 -and $p.R -lt 190 -and $p.B -gt 120 -and $p.B -lt 200) { $green++ }
                    if ($p.B -gt 220 -and $p.R -gt 80 -and $p.R -lt 155 -and $p.G -gt 140 -and $p.G -lt 200) { $blue++ }
                }
            }
            $minimum = 10 * $scale * $scale
            if ($red -lt $minimum -or $green -lt $minimum -or $blue -lt $minimum) {
                throw "Blank/clipped compass $name/$requestedScale ($red/$green/$blue axis pixels)"
            }
            # The fixture's final camera looks from grip + (9, 4, 11) toward the grip.
            # Check letters separately from arms, allowing antialiasing but rejecting filled discs.
            $logicalWidth = [Math]::Ceiling($bitmap.Width / $scale)
            $logicalHeight = [Math]::Ceiling($bitmap.Height / $scale)
            $offsetX = @(18, 0, -15); $offsetY = @(4, -22, 5); $glyphs = @(0, 0, 0); $strokeOffsets = @(0, 0, 0)
            foreach ($axis in 0..2) {
                $cx = [Math]::Round(($logicalWidth - 42 + $offsetX[$axis]) * $bitmap.Width / $logicalWidth)
                $cy = [Math]::Round(($logicalHeight - 66 + $offsetY[$axis]) * $bitmap.Height / $logicalHeight)
                $minX = $bitmap.Width; $maxX = 0; $minY = $bitmap.Height; $maxY = 0
                for ($y = $cy - 6 * $scale; $y -lt $cy + 6 * $scale; $y++) {
                    for ($x = $cx - 5 * $scale; $x -lt $cx + 5 * $scale; $x++) {
                        $p = $bitmap.GetPixel($x, $y)
                        $match = switch ($axis) {
                            0 { $p.R -gt 210 -and $p.G -gt 80 -and $p.G -lt 155 -and $p.B -gt 80 -and $p.B -lt 170 }
                            1 { $p.G -gt 190 -and $p.R -gt 110 -and $p.R -lt 190 -and $p.B -gt 120 -and $p.B -lt 200 }
                            2 { $p.B -gt 220 -and $p.R -gt 80 -and $p.R -lt 155 -and $p.G -gt 140 -and $p.G -lt 200 }
                        }
                        if ($match) {
                            $glyphs[$axis]++
                            $minX = [Math]::Min($minX, $x); $maxX = [Math]::Max($maxX, $x)
                            $minY = [Math]::Min($minY, $y); $maxY = [Math]::Max($maxY, $y)
                        }
                    }
                }
                if ($glyphs[$axis] -lt 6 * $scale * $scale -or $glyphs[$axis] -gt 60 * $scale * $scale) {
                    throw "Missing/clipped letter or filled endpoint $name/$requestedScale/$axis ($($glyphs[$axis]) colored pixels)"
                }
                $inkX = (($minX + $maxX + 1) / 2.0 - $cx) / $scale
                $inkY = (($minY + $maxY + 1) / 2.0 - $cy) / $scale
                if ([Math]::Abs($inkX) -gt 0.5 -or [Math]::Abs($inkY) -gt 0.35) {
                    throw "Letter ink does not align with axis endpoint $name/$requestedScale/$axis ($inkX/$inkY logical pixels)"
                }
                $length = [Math]::Sqrt($offsetX[$axis] * $offsetX[$axis] + $offsetY[$axis] * $offsetY[$axis])
                $ux = $offsetX[$axis] / $length; $uy = $offsetY[$axis] / $length
                $count = 0; $normalSum = 0
                for ($y = $bitmap.Height - 100 * $scale; $y -lt $bitmap.Height - 30 * $scale; $y++) {
                    for ($x = $bitmap.Width - 80 * $scale; $x -lt $bitmap.Width - 5 * $scale; $x++) {
                        $rx = ($x + 0.5) * $logicalWidth / $bitmap.Width - ($logicalWidth - 42)
                        $ry = ($y + 0.5) * $logicalHeight / $bitmap.Height - ($logicalHeight - 66)
                        $along = $rx * $ux + $ry * $uy; $normal = -$rx * $uy + $ry * $ux
                        if ($along -lt 4 -or $along -gt 7 -or [Math]::Abs($normal) -gt 1.5) { continue }
                        $p = $bitmap.GetPixel($x, $y)
                        # Stems blend with sky or stone. Require chromatic contrast, not opaque-glyph RGB.
                        $match = switch ($axis) {
                            0 { $p.R -gt 140 -and $p.R - $p.G -gt 45 -and $p.R - $p.B -gt 35 }
                            1 { $p.G -gt 140 -and $p.G - $p.R -gt 25 -and $p.G - $p.B -gt 10 }
                            2 { $p.B -gt 175 -and $p.R -gt 80 -and $p.R -lt 155 -and $p.G -lt 200 -and $p.G - $p.R -gt 20 -and $p.B - $p.G -gt 25 }
                        }
                        if ($match) { $count++; $normalSum += $normal }
                    }
                }
                if ($count -lt $scale * $scale -or [Math]::Abs($normalSum / $count) -gt 0.3) {
                    throw "Missing/off-center axis stroke $name/$requestedScale/$axis ($count pixels, normal sum $normalSum)"
                }
                $strokeOffsets[$axis] = [Math]::Round($normalSum / $count, 3)
            }
            Write-Host "$name compass x${requestedScale} RGB pixels: $red/$green/$blue; XYZ glyph pixels: $($glyphs -join '/')"
            Write-Host "$name compass x${requestedScale} XYZ stroke center offsets: $($strokeOffsets -join '/') logical pixels"
        } finally { $bitmap.Dispose() }
    }
}
