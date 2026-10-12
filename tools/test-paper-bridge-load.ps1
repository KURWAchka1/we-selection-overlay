param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [ValidateRange(2, 32)][int]$Players = 16,
    [ValidateRange(100, 2400)][int]$Samples = 600,
    [ValidateRange(1, 4)][int]$Repetitions = 2,
    [switch]$ControlledBaseline,
    [switch]$RestartLoad,
    [switch]$TickTimeline,
    [switch]$AcceptEula
)
$ErrorActionPreference = 'Stop'
if (!$AcceptEula) { throw 'Owner acceptance of https://www.minecraft.net/eula is required' }
if (@($ControlledBaseline, $RestartLoad, $TickTimeline).Where({ $_ }).Count -gt 1) { throw 'Choose one workload mode: frozen baseline, restart load or tick timeline' }
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'build/bridge-paper-proof'
$server = Join-Path $root $(if ($ControlledBaseline) { 'build/bridge-baseline-proof' } elseif ($RestartLoad) { 'build/bridge-restart-load-proof' } elseif ($TickTimeline) { 'build/bridge-tick-timeline-proof' } else { 'build/bridge-load-proof' })
foreach ($file in @((Join-Path $Java21 'bin/java.exe'), (Join-Path $source 'server.jar'),
        (Join-Path $source 'plugins/worldedit.jar'), (Join-Path $root 'build/bridge-wire-deps/package.json'))) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing verified fixture dependency: $file" }
}
$previousJava = $env:JAVA_HOME
$env:JAVA_HOME = $Java21
Push-Location $root
try {
    & .\WEaddon_fabric_1.21.11\gradlew.bat -p server-bridge :paper:jar :paper-test-fixture:jar --offline --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Paper fixture build failed' }
    New-Item -ItemType Directory -Force -Path (Join-Path $server 'plugins') | Out-Null
    Copy-Item -LiteralPath (Join-Path $source 'server.jar') -Destination $server
    foreach ($directory in @('cache', 'libraries', 'versions')) {
        $destination = Join-Path $server $directory
        if (!(Test-Path -LiteralPath $destination)) {
            Copy-Item -LiteralPath (Join-Path $source $directory) -Destination $destination -Recurse
        }
    }
    Copy-Item -LiteralPath (Join-Path $source 'plugins/worldedit.jar') -Destination (Join-Path $server 'plugins/worldedit.jar')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar') -Destination (Join-Path $server 'plugins')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') -Destination (Join-Path $server 'companion.jar')
    (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'server-bridge-test/server.properties')) -replace '^max-players=4$', 'max-players=32' |
        Set-Content -LiteralPath (Join-Path $server 'server.properties') -Encoding ASCII
    'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding ASCII
    $mode = if ($ControlledBaseline) { 'baseline' } elseif ($RestartLoad) { 'restart' } elseif ($TickTimeline) { 'timeline' } else { 'load' }
    & node (Join-Path $PSScriptRoot 'server-bridge-test/paper-load-proof.mjs') $Java21 $server $Players $Samples $Repetitions $mode
    if ($LASTEXITCODE -ne 0) { throw "Paper load proof failed; inspect $server" }
} finally { Pop-Location; $env:JAVA_HOME = $previousJava }
