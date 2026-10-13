param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [ValidateRange(2, 32)][int]$Players = 16,
    [ValidateRange(100, 2400)][int]$Samples = 600,
    [ValidateRange(1, 4)][int]$Repetitions = 2,
    [switch]$ControlledBaseline,
    [switch]$RestartLoad,
    [switch]$TickTimeline,
    [switch]$FairDispatch,
    [switch]$GameplayLoad,
    [switch]$LifecycleLoad,
    [switch]$EditLoad,
    [switch]$SliceDiagnostics,
    [switch]$WithoutSpark,
    [switch]$AcceptEula
)
$ErrorActionPreference = 'Stop'
if (!$AcceptEula) { throw 'Owner acceptance of https://www.minecraft.net/eula is required' }
if (@($ControlledBaseline, $RestartLoad, $TickTimeline, $FairDispatch, $GameplayLoad, $LifecycleLoad, $EditLoad).Where({ $_ }).Count -gt 1) { throw 'Choose one workload mode: frozen baseline, restart load, tick timeline, fair dispatch, gameplay, active lifecycle or edit load' }
if ($EditLoad -and ($Players -ne 32 -or $Samples -ne 400)) { throw 'Edit load requires 32 actors and 400 ticks per phase' }
if ($GameplayLoad -and ($Players -lt 4 -or $Samples % 20 -ne 0)) { throw 'Gameplay requires at least 4 players and a sample count divisible by 20' }
if ($LifecycleLoad -and ($Players -ne 32 -or $Samples % 20 -ne 0)) { throw 'Active lifecycle requires 32 players and a sample count divisible by 20' }
if ($SliceDiagnostics -and !$LifecycleLoad) { throw 'Slice diagnostics require the active lifecycle workload' }
if ($WithoutSpark -and !($SliceDiagnostics -or $GameplayLoad)) { throw 'Spark control requires slice diagnostics or matched gameplay' }
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'build/bridge-paper-proof'
$server = Join-Path $root $(if ($EditLoad) { 'build/bridge-edit-load-proof' } elseif ($WithoutSpark -and $GameplayLoad) { 'build/bridge-gameplay-control-proof' } elseif ($WithoutSpark) { 'build/bridge-slice-diagnostics-no-spark' } elseif ($SliceDiagnostics) { 'build/bridge-slice-diagnostics' } elseif ($ControlledBaseline) { 'build/bridge-baseline-proof' } elseif ($RestartLoad) { 'build/bridge-restart-load-proof' } elseif ($TickTimeline) { 'build/bridge-tick-timeline-proof' } elseif ($FairDispatch) { 'build/bridge-fair-dispatch-proof' } elseif ($GameplayLoad) { 'build/bridge-gameplay-proof' } elseif ($LifecycleLoad) { 'build/bridge-active-lifecycle-proof' } else { 'build/bridge-load-proof' })
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
    if ($SliceDiagnostics) {
        & .\WEaddon_fabric_1.21.11\gradlew.bat -p tools/bridge-slice-agent check --offline --no-daemon --max-workers=2 --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Slice diagnostic agent checks failed' }
    }
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
    $mode = if ($ControlledBaseline) { 'baseline' } elseif ($RestartLoad) { 'restart' } elseif ($TickTimeline) { 'timeline' } elseif ($FairDispatch) { 'fair' } elseif ($GameplayLoad) { 'gameplay' } else { 'load' }
    $runner = if ($EditLoad) { 'server-bridge-test/paper-edit-load-proof.mjs' } elseif ($LifecycleLoad) { 'server-bridge-test/paper-active-lifecycle-proof.mjs' } else { 'server-bridge-test/paper-load-proof.mjs' }
    [string[]]$diagnosticArguments = @(if ($SliceDiagnostics) { '--slice-diagnostics' }; if ($WithoutSpark) { '--without-spark' })
    & node (Join-Path $PSScriptRoot $runner) $Java21 $server $Players $Samples $Repetitions $mode @diagnosticArguments
    if ($LASTEXITCODE -ne 0) { throw "Paper load proof failed; inspect $server" }
} finally { Pop-Location; $env:JAVA_HOME = $previousJava }
