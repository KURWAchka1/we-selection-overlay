param([string[]]$Only = @())
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$root = Split-Path -Parent $PSScriptRoot
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
    $bitmap = [Drawing.Bitmap]::new($file)
    try {
        if ($bitmap.Width -ne 1280 -or $bitmap.Height -ne 720) { throw "Unexpected fixture viewport: $name" }
        $cyan = 0
        for ($y = 270; $y -lt 490; $y++) {
            for ($x = 520; $x -lt 750; $x++) {
                $pixel = $bitmap.GetPixel($x, $y)
                if ($pixel.G -gt $pixel.R + 15 -and $pixel.B -gt $pixel.R + 15) { $cyan++ }
            }
        }
        # A few grid lines do not prove the diamond texture has actually rendered.
        if ($cyan -lt 8000) { throw "Missing/incorrect diamond ghost texture: $name ($cyan cyan pixels)" }
        Write-Host "$name diamond texture pixels: $cyan"
    } finally { $bitmap.Dispose() }
}
