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
    [switch]$AcceptEula,
    [string]$Java25,
    [ValidateSet('1.21.11', '26.1.2', '26.2')][string]$Minecraft = '1.21.11',
    [switch]$MatchedEditDiagnostics,
    [switch]$EditProfilerControl,
    [switch]$MovingEditLoad,
    [switch]$BudgetComparison,
    [switch]$DrainObserverControl,
    [switch]$RevisionComparison,
    [switch]$WorldReadComparison,
    [switch]$GuardBandComparison,
    [switch]$HotPathComparison,
    [switch]$PriorityControl,
    [switch]$EncodingComparison
)
$ErrorActionPreference = 'Stop'
if (!$AcceptEula) { throw 'Owner acceptance of https://www.minecraft.net/eula is required' }
if ($Minecraft -ne '1.21.11' -and (!$Java25 -or !($EditLoad -or ($Minecraft -eq '26.2' -and $GameplayLoad -and $WithoutSpark)))) { throw '26.x requires Java25 and EditLoad, or exact 26.2 matched no-spark GameplayLoad' }
if ($Minecraft -eq '26.2' -and $GameplayLoad -and ($Players -ne 32 -or $Samples -ne 400 -or $Repetitions -ne 2)) { throw '26.2 gameplay requires 32 actors, 400 ticks and two reversed-order pairs' }
if (@($ControlledBaseline, $RestartLoad, $TickTimeline, $FairDispatch, $GameplayLoad, $LifecycleLoad, $EditLoad).Where({ $_ }).Count -gt 1) { throw 'Choose one workload mode: frozen baseline, restart load, tick timeline, fair dispatch, gameplay, active lifecycle or edit load' }
if ($EditLoad -and ($Players -ne 32 -or $Samples -ne 400)) { throw 'Edit load requires 32 actors and 400 ticks per phase' }
if (@($MatchedEditDiagnostics,$EditProfilerControl,$MovingEditLoad,$BudgetComparison,$DrainObserverControl,$RevisionComparison,$WorldReadComparison,$GuardBandComparison,$HotPathComparison,$PriorityControl,$EncodingComparison).Where({$_}).Count -gt 1) { throw 'Choose one edit study' }
if ($EditProfilerControl -and $Minecraft -ne '26.2') { throw 'Edit profiler control is currently pinned to the exact 26.2 host' }
$editStudy = $MatchedEditDiagnostics -or $EditProfilerControl -or $BudgetComparison -or $DrainObserverControl -or $RevisionComparison -or $WorldReadComparison -or $GuardBandComparison -or $HotPathComparison -or $PriorityControl -or $EncodingComparison
if ($BudgetComparison -and $Minecraft -ne '26.2') { throw 'Budget comparison is pinned to exact 26.2' }
if ($DrainObserverControl -and $Minecraft -ne '26.2') { throw 'Drain observer control is pinned to exact 26.2' }
if ($RevisionComparison -and $Minecraft -ne '26.2') { throw 'Revision comparison is pinned to exact 26.2' }
if ($WorldReadComparison -and $Minecraft -ne '26.2') { throw 'World-read comparison is pinned to exact 26.2' }
if ($GuardBandComparison -and $Minecraft -ne '26.2') { throw 'Guard-band comparison is pinned to exact 26.2' }
if ($HotPathComparison -and $Minecraft -ne '26.2') { throw 'Hot-path comparison is pinned to exact 26.2' }
if ($PriorityControl -and $Minecraft -ne '26.2') { throw 'Priority control is pinned to exact 26.2' }
if ($EncodingComparison -and $Minecraft -ne '26.2') { throw 'Encoding comparison is pinned to exact 26.2' }
if ($editStudy -and (!$EditLoad -or $Repetitions -ne 4 -or $SliceDiagnostics -or $WithoutSpark)) { throw 'Matched edit studies require EditLoad and four repetitions; no unrelated profiler/diagnostic overrides' }
if ($MovingEditLoad -and (!$EditLoad -or $Minecraft -ne '26.2' -or $Repetitions -ne 2 -or $SliceDiagnostics -or $WithoutSpark)) { throw 'Moving edits require exact 26.2, EditLoad and two repetitions; profiler policy is fixed' }
if ($GameplayLoad -and ($Players -lt 4 -or $Samples % 20 -ne 0)) { throw 'Gameplay requires at least 4 players and a sample count divisible by 20' }
if ($LifecycleLoad -and ($Players -ne 32 -or $Samples % 20 -ne 0)) { throw 'Active lifecycle requires 32 players and a sample count divisible by 20' }
if ($SliceDiagnostics -and !$LifecycleLoad) { throw 'Slice diagnostics require the active lifecycle workload' }
if ($WithoutSpark -and !($SliceDiagnostics -or $GameplayLoad)) { throw 'Spark control requires slice diagnostics or matched gameplay' }
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'build/bridge-paper-proof'
$server = Join-Path $root $(if ($EditLoad) { 'build/bridge-edit-load-proof' } elseif ($WithoutSpark -and $GameplayLoad) { 'build/bridge-gameplay-control-proof' } elseif ($WithoutSpark) { 'build/bridge-slice-diagnostics-no-spark' } elseif ($SliceDiagnostics) { 'build/bridge-slice-diagnostics' } elseif ($ControlledBaseline) { 'build/bridge-baseline-proof' } elseif ($RestartLoad) { 'build/bridge-restart-load-proof' } elseif ($TickTimeline) { 'build/bridge-tick-timeline-proof' } elseif ($FairDispatch) { 'build/bridge-fair-dispatch-proof' } elseif ($GameplayLoad) { 'build/bridge-gameplay-proof' } elseif ($LifecycleLoad) { 'build/bridge-active-lifecycle-proof' } else { 'build/bridge-load-proof' })
if ($MatchedEditDiagnostics) { $server = Join-Path $root 'build/bridge-edit-diagnostics' }
if ($EditProfilerControl) { $server = Join-Path $root 'build/bridge-edit-profiler-control' }
if ($MovingEditLoad) { $server = Join-Path $root 'build/bridge-moving-edit-proof' }
if ($BudgetComparison) { $server = Join-Path $root 'build/bridge-dispatch-budget-proof' }
if ($DrainObserverControl) { $server = Join-Path $root 'build/bridge-drain-observer-proof' }
if ($RevisionComparison) { $server = Join-Path $root 'build/bridge-revision-proof' }
if ($WorldReadComparison) { $server = Join-Path $root 'build/bridge-world-read-proof' }
if ($GuardBandComparison) { $server = Join-Path $root 'build/bridge-guard-band-proof' }
if ($HotPathComparison) { $server = Join-Path $root 'build/bridge-hotpath-proof' }
if ($PriorityControl) { $server = Join-Path $root 'build/bridge-priority-proof' }
if ($EncodingComparison) { $server = Join-Path $root 'build/bridge-encoding-proof' }
if ($Minecraft -eq '1.21.11') {
    if ($editStudy) { $server = Join-Path $server $Minecraft }
    $paper = Join-Path $source 'server.jar'
    $worldEdit = Join-Path $source 'plugins/worldedit.jar'
    $bootstrap = $source
} else {
    $server = Join-Path $server $Minecraft
    $downloads = Join-Path $root 'build/bridge-server-downloads'
    $paper = Join-Path $downloads $(if ($Minecraft -eq '26.1.2') { 'paper-26.1.2-74.jar' } else { 'paper-26.2-133.jar' })
    $worldEdit = Join-Path $downloads $(if ($Minecraft -eq '26.1.2') { 'worldedit-bukkit-7.4.3.jar' } else { 'worldedit-bukkit-7.4.5.jar' })
    $bootstrap = Join-Path $root "build/bridge-native-fault-proof/$Minecraft"
}
if ($editStudy -or $MovingEditLoad) {
    foreach ($name in @('edit-load-proof.json','edit-load-failure.json','edit-0.log','edit-1.log')) {
        if (Test-Path -LiteralPath (Join-Path $server $name)) {
            throw "Archive existing edit study before build/copy: $server"
        }
    }
}
if ($BudgetComparison -or $RevisionComparison -or $WorldReadComparison -or $GuardBandComparison -or $HotPathComparison -or $EncodingComparison) {
    $previous = Join-Path $server 'previous-companion.jar'
    $expected = if ($GuardBandComparison -or $EncodingComparison) {'aab25479af9b47d330e0fbfd7de495303ae23aec06617c103b5ee2404505d4d6'} elseif ($WorldReadComparison) {'42528d3b606e6e2cba9142a482d5ba6e95a589ec16fc3b693a57dfe1af9186cd'} elseif ($RevisionComparison -or $HotPathComparison) {'78ff34e46ef58fa61012612d799ac943815adfad33e0f1bf7c44544ea4107ed1'} else {'7e40f70b3fab486358a929da73db4132d31bcd3d10e5bc90b99392dce3c4d956'}
    if (!(Test-Path -LiteralPath $previous -PathType Leaf) -or (Get-FileHash -LiteralPath $previous -Algorithm SHA256).Hash.ToLower() -ne $expected) { throw 'Preserve the exact pre-change companion before building a comparison' }
}
if ($Minecraft -eq '26.2' -and $GameplayLoad) {
    foreach ($name in @('load-proof.json','load-failure.json','prepare.log','1-absent.log')) {
        if (Test-Path -LiteralPath (Join-Path $server $name)) { throw "Archive existing gameplay study before build/copy: $server" }
    }
}
$dependencies = @((Join-Path $Java21 'bin/java.exe'), $paper, $worldEdit)
if ($Minecraft -ne '1.21.11') { $dependencies += (Join-Path $Java25 'bin/java.exe') }
if ($Minecraft -ne '26.2') { $dependencies += (Join-Path $root 'build/bridge-wire-deps/package.json') }
foreach ($file in $dependencies) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing verified fixture dependency: $file" }
}
$previousJava = $env:JAVA_HOME
$env:JAVA_HOME = $Java21
Push-Location $root
try {
    & .\WEaddon_fabric_1.21.11\gradlew.bat -p server-bridge :paper:jar :paper-test-fixture:jar --offline --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Paper fixture build failed' }
    if ($Minecraft -eq '26.2') {
        $env:JAVA_HOME = $Java25
        & .\WEaddon_fabric_1.21.11\gradlew.bat -p tools/bridge-load-clients check installDist --offline --no-daemon --max-workers=2 --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Pinned 26.2 load-client build/verification failed' }
        $env:JAVA_HOME = $Java21
    }
    if ($SliceDiagnostics -or $editStudy) {
        & .\WEaddon_fabric_1.21.11\gradlew.bat -p tools/bridge-slice-agent check --offline --no-daemon --max-workers=2 --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Slice diagnostic agent checks failed' }
    }
    New-Item -ItemType Directory -Force -Path (Join-Path $server 'plugins') | Out-Null
    Copy-Item -LiteralPath $paper -Destination (Join-Path $server 'server.jar')
    foreach ($directory in @('cache', 'libraries', 'versions')) {
        $destination = Join-Path $server $directory
        if (!(Test-Path -LiteralPath $destination) -and (Test-Path -LiteralPath (Join-Path $bootstrap $directory))) {
            Copy-Item -LiteralPath (Join-Path $bootstrap $directory) -Destination $destination -Recurse
        }
    }
    Copy-Item -LiteralPath $worldEdit -Destination (Join-Path $server 'plugins/worldedit.jar')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar') -Destination (Join-Path $server 'plugins')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') -Destination (Join-Path $server 'companion.jar')
    (Get-Content -LiteralPath (Join-Path $PSScriptRoot 'server-bridge-test/server.properties')) -replace '^max-players=4$', 'max-players=32' |
        Set-Content -LiteralPath (Join-Path $server 'server.properties') -Encoding ASCII
    'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding ASCII
    $mode = if ($ControlledBaseline) { 'baseline' } elseif ($RestartLoad) { 'restart' } elseif ($TickTimeline) { 'timeline' } elseif ($FairDispatch) { 'fair' } elseif ($GameplayLoad) { 'gameplay' } else { 'load' }
    $runner = if ($EditLoad) { 'server-bridge-test/paper-edit-load-proof.mjs' } elseif ($LifecycleLoad) { 'server-bridge-test/paper-active-lifecycle-proof.mjs' } else { 'server-bridge-test/paper-load-proof.mjs' }
    [string[]]$diagnosticArguments = @(if ($SliceDiagnostics) { '--slice-diagnostics' }; if ($WithoutSpark) { '--without-spark' })
    if ($EditLoad) { $diagnosticArguments += $Minecraft }
    if ($GameplayLoad -and $Minecraft -eq '26.2') { $diagnosticArguments += $Minecraft }
    if ($MatchedEditDiagnostics) { $diagnosticArguments += '--matched-slices' }
    if ($EditProfilerControl) { $diagnosticArguments += '--spark-control' }
    if ($MovingEditLoad) { $diagnosticArguments += '--moving-gameplay' }
    if ($BudgetComparison) { $diagnosticArguments += '--budget-control' }
    if ($DrainObserverControl) { $diagnosticArguments += '--drain-control' }
    if ($RevisionComparison) { $diagnosticArguments += '--revision-control' }
    if ($WorldReadComparison) { $diagnosticArguments += '--world-control' }
    if ($GuardBandComparison) { $diagnosticArguments += '--guard-control' }
    if ($HotPathComparison) { $diagnosticArguments += '--hotpath-control' }
    if ($PriorityControl) { $diagnosticArguments += '--priority-control' }
    if ($EncodingComparison) { $diagnosticArguments += '--encoding-control' }
    $serverJava = if ($Minecraft -eq '1.21.11') { $Java21 } else { $Java25 }
    & node (Join-Path $PSScriptRoot $runner) $serverJava $server $Players $Samples $Repetitions $mode @diagnosticArguments
    if ($LASTEXITCODE -ne 0) { throw "Paper load proof failed; inspect $server" }
} finally { Pop-Location; $env:JAVA_HOME = $previousJava }
