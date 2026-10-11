param(
    [ValidateSet('fabric-1.21.11', 'fabric-26.1', 'fabric-26.2', 'neoforge-1.21.11', 'neoforge-26.1', 'neoforge-26.2')][string[]]$Only = @(),
    [datetime]$NotBefore = [datetime]::MinValue
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
function Assert-FreshCapture([string]$Path) {
    if (!(Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Missing screenshot: $Path" }
    if ((Get-Item -LiteralPath $Path).LastWriteTimeUtc -lt $NotBefore.ToUniversalTime()) {
        throw "Stale screenshot from an earlier run: $Path"
    }
}
$targets = @{
    'fabric-1.21.11' = 'WEaddon_fabric_1.21.11/run-command-test/screenshots/0000_command-set-ghost.png'
    'fabric-26.1' = 'WEaddon_lightposition/run-command-test/screenshots/0000_command-set-ghost.png'
    'fabric-26.2' = 'WEaddon_fabric_26.2/run-command-test/screenshots/0000_command-set-ghost.png'
    'neoforge-1.21.11' = 'WEaddon_neoforge_universal_1.21.11-26.2/run-command-test-1.21.11/screenshots/command-set-ghost.png'
    'neoforge-26.1' = 'WEaddon_neoforge_universal_1.21.11-26.2/run-command-test-26.1/screenshots/command-set-ghost.png'
    'neoforge-26.2' = 'WEaddon_neoforge_universal_1.21.11-26.2/run-command-test-26.2/screenshots/command-set-ghost.png'
}
foreach ($name in $targets.Keys | Sort-Object) {
    if ($Only.Count -gt 0 -and $name -notin $Only) { continue }
    $file = Join-Path $root $targets[$name]
    Assert-FreshCapture $file
    $bitmap = [Drawing.Bitmap]::new($file)
    try {
        if ($bitmap.Width -ne 1280 -or $bitmap.Height -ne 720) { throw "Unexpected fixture viewport: $name" }
        $cyan = 0
        for ($y = 270; $y -lt 490; $y++) {
            for ($x = 520; $x -lt 750; $x++) {
                $pixel = $bitmap.GetPixel($x, $y)
                if ($pixel.G -gt $pixel.R + 20 -and $pixel.B -gt $pixel.R + 20 -and [Math]::Abs($pixel.G - $pixel.B) -lt 35) { $cyan++ }
            }
        }
        # Exclude blue sky; a few grid lines do not prove a diamond model rendered.
        if ($cyan -lt 8000) { throw "Missing/incorrect diamond ghost texture: $name ($cyan cyan pixels)" }
        Write-Host "$name diamond texture pixels: $cyan"
    } finally { $bitmap.Dispose() }
    $directory = Split-Path -Parent $file
    foreach ($kind in @('rotate', 'mirror')) {
        $capture = Get-ChildItem -LiteralPath $directory -Filter "*clipboard-draft-$kind.png" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if (!$capture) { throw "Missing clipboard $kind screenshot: $name" }
        Assert-FreshCapture $capture.FullName
        $bitmap = [Drawing.Bitmap]::new($capture.FullName)
        try {
            $warm = 0
            for ($y = 260; $y -lt 480; $y++) {
                for ($x = 530; $x -lt 760; $x++) {
                    $pixel = $bitmap.GetPixel($x, $y)
                    if (($pixel.R -gt $pixel.G + 35 -and $pixel.R -gt $pixel.B + 35) -or
                        ($pixel.R -gt 110 -and $pixel.G -gt 100 -and $pixel.B -lt $pixel.R - 40 -and $pixel.B -lt $pixel.G - 40)) { $warm++ }
                }
            }
            # Sky, cyan bounds and magenta reference-command messages cannot satisfy this test.
            if ($warm -lt 100) { throw "Missing clipboard $kind models: $name ($warm texture pixels)" }
            Write-Host "$name clipboard $kind texture pixels: $warm"
        } finally { $bitmap.Dispose() }
    }
}
