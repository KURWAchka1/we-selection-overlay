param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [ValidateSet('fabric-1.21.11', 'fabric-26.1', 'fabric-26.2', 'neoforge-1.21.11', 'neoforge-26.1', 'neoforge-26.2')][string[]]$Only = @(),
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$')][string]$ModVersion = '1.8.0',
    [ValidateSet('allowed', 'denied', 'absent', 'fault-slow', 'fault-late', 'fault-revoked', 'gestures')][string]$NativeBridgeScenario,
    [string]$NativeBridgeControl,
    [ValidateSet('1.21.11', '26.1.2', '26.2')][string]$NativeBridgeMinecraft = '1.21.11',
    [switch]$DesktopWorker
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
if (!$DesktopWorker) {
    & (Join-Path $PSScriptRoot 'test-desktop/probe-cursor.ps1') -Java21 $Java21
    $parameters = @{Java21=$Java21; Java25=$Java25; Only=$Only; ModVersion=$ModVersion; DesktopWorker=$true}
    if ($NativeBridgeScenario) { $parameters.NativeBridgeScenario=$NativeBridgeScenario; $parameters.NativeBridgeControl=$NativeBridgeControl; $parameters.NativeBridgeMinecraft=$NativeBridgeMinecraft }
    Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters $parameters
    return
}
Assert-WesoTestDesktop
$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root 'build/command-validation'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
if ($NativeBridgeScenario) {
    $family = if ($NativeBridgeMinecraft -eq '26.1.2') { '26.1' } else { $NativeBridgeMinecraft }
    if ($Only.Count -ne 1 -or $Only[0] -notin @("fabric-$family", "neoforge-$family")) { throw 'Native Paper proof requires one version-matched client' }
    if ($family -ne '1.21.11' -and $NativeBridgeScenario -ne 'gestures') { throw '26.x native proof currently supports gestures only' }
    $control = [IO.Path]::GetFullPath($NativeBridgeControl)
    $directory = if ($NativeBridgeScenario.StartsWith('fault-')) { 'build/bridge-native-fault-proof' } elseif ($NativeBridgeScenario -eq 'gestures') { 'build/bridge-native-gesture-proof' } else { 'build/bridge-native-proof' }
    $allowed = [IO.Path]::GetFullPath((Join-Path $root $directory)) + [IO.Path]::DirectorySeparatorChar
    if (!$control.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase) -or !(Test-Path -LiteralPath $control -PathType Leaf)) { throw 'Missing isolated native bridge control file' }
    $loader = if ($Only[0].StartsWith('fabric-')) { 'fabric' } else { 'neoforge' }
    Assert-WesoTestArtifact -Loader $loader -ModVersion $ModVersion
    $module = if ($loader -ne 'fabric') { 'WEaddon_neoforge_universal_1.21.11-26.2' } elseif ($family -eq '26.1') { 'WEaddon_lightposition' } else { "WEaddon_fabric_$family" }
    $task = if ($loader -eq 'fabric') { 'testNativePaperBridge' } else { 'runNativePaperBridge' }
    $previousJava = $env:JAVA_HOME
    $env:JAVA_HOME = if ($family -eq '1.21.11') { $Java21 } else { $Java25 }
    Push-Location (Join-Path $root $module)
    try {
        Assert-WesoTestDesktop
        $log = Join-Path $logs "$($Only[0])-bridge-$NativeBridgeScenario.log"
        $nativeArgs = @()
        if ($family -ne '1.21.11') {
            if ($loader -eq 'fabric') { $nativeArgs += "-Pminecraft_version=$NativeBridgeMinecraft" }
            else { $nativeArgs += @("-Pneo_version=$(if ($family -eq '26.1') { '26.1.2.75' } else { '26.2.0.67' })", '-Psmoke_java_version=25') }
        }
        & .\gradlew.bat -I '../tools/native-paper-bridge.init.gradle' $task @nativeArgs "-Pmod_version=$ModVersion" "-Pbridge_scenario=$NativeBridgeScenario" "-Pbridge_control=$control" --offline --no-daemon --max-workers=2 --console=plain 2>&1 | Tee-Object -FilePath $log
        if ($LASTEXITCODE -ne 0) { throw 'Native Paper client failed' }
        $result = [IO.File]::ReadAllText($log)
        foreach ($marker in @('CURSOR ISOLATION GUARD PASS:', 'NATIVE PAPER CUI PASS:', "NATIVE PAPER BRIDGE PASS: $NativeBridgeScenario")) {
            if (!$result.Contains($marker)) { throw "Incomplete native Paper proof: missing $marker" }
        }
    } finally { Pop-Location; $env:JAVA_HOME = $previousJava }
    return
}
& node (Join-Path $PSScriptRoot 'prepare-command-tests.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Failed to prepare command fixtures' }
$targets = @(
    @{Name='fabric-1.21.11'; Module='WEaddon_fabric_1.21.11'; Java=$Java21; Args=@('-I', '../tools/command-preview-test.init.gradle', 'testUniversalCommands')},
    @{Name='fabric-26.1'; Module='WEaddon_lightposition'; Java=$Java25; Args=@('-I', '../tools/command-preview-test.init.gradle', 'testUniversalCommands')},
    @{Name='fabric-26.2'; Module='WEaddon_fabric_26.2'; Java=$Java25; Args=@('-I', '../tools/command-preview-test.init.gradle', 'testUniversalCommands')},
    @{Name='neoforge-1.21.11'; Module='WEaddon_neoforge_universal_1.21.11-26.2'; Java=$Java21; Family='1.21.11'; Args=@('-I', '../tools/neo-command-preview-test.init.gradle', 'runCommandClient')},
    @{Name='neoforge-26.1'; Module='WEaddon_neoforge_universal_1.21.11-26.2'; Java=$Java25; Family='26.1'; Args=@('-I', '../tools/neo-command-preview-test.init.gradle', 'runCommandClient', '-Pneo_version=26.1.2.75', '-Psmoke_java_version=25')},
    @{Name='neoforge-26.2'; Module='WEaddon_neoforge_universal_1.21.11-26.2'; Java=$Java25; Family='26.2'; Args=@('-I', '../tools/neo-command-preview-test.init.gradle', 'runCommandClient', '-Pneo_version=26.2.0.67', '-Psmoke_java_version=25')}
)
$previousJava = $env:JAVA_HOME
try {
    foreach ($target in $targets) {
        if ($Only.Count -gt 0 -and $target.Name -notin $Only) { continue }
        $module = Join-Path $root $target.Module
        Assert-WesoTestArtifact -Loader $(if ($target.Family) { 'neoforge' } else { 'fabric' }) -ModVersion $ModVersion
        $run = if ($target.Family) { "run-command-test-$($target.Family)" } else { 'run-command-test' }
        foreach ($name in @('client.properties', 'command-preview.properties', 'selection-editing.properties')) {
            $config = [IO.Path]::GetFullPath((Join-Path $module "$run/config/worldedit_selection_overlay/$name"))
            if (!$config.StartsWith([IO.Path]::GetFullPath($module) + [IO.Path]::DirectorySeparatorChar)) { throw 'Fixture config escaped the module' }
            if (Test-Path -LiteralPath $config) { Remove-Item -LiteralPath $config -Force }
        }
        if ($target.Family) {
            $save = Join-Path $module "run-command-test-$($target.Family)/saves/weso-fixture"
            if (!(Test-Path -LiteralPath (Join-Path $save 'level.dat'))) {
                $source = Join-Path $module "run-isolation-test-$($target.Family)/saves/weso-fixture"
                if (!(Test-Path -LiteralPath (Join-Path $source 'level.dat'))) { throw "Missing disposable $($target.Family) fixture world. See TESTING.md." }
                New-Item -ItemType Directory -Force -Path (Split-Path -Parent $save) | Out-Null
                Copy-Item -LiteralPath $source -Destination $save -Recurse
            }
        }
        Write-Host "Testing command preview: $($target.Name)"
        $env:JAVA_HOME = $target.Java
        Push-Location $module
        try {
            Assert-WesoTestDesktop
            $runStarted = [datetime]::UtcNow
            & .\gradlew.bat @($target.Args) "-Pmod_version=$ModVersion" --offline --no-daemon --max-workers=2 --console=plain 2>&1 |
                Tee-Object -FilePath (Join-Path $logs "$($target.Name).log")
            if ($LASTEXITCODE -ne 0) { throw "Command test failed: $($target.Name)" }
            $result = [IO.File]::ReadAllText((Join-Path $logs "$($target.Name).log"))
            $commandMarker = if ($target.Family) { 'WESO NEO COMMAND PASS:' } else { 'WESO COMMAND PASS:' }
            foreach ($marker in @($commandMarker, 'CURSOR ISOLATION GUARD PASS:', 'BACKGROUND INPUT VERIFY PASS:', 'CUI COMMAND DISCOVERY VERIFY PASS:', 'CLIPBOARD TRANSFORM VERIFY PASS:', 'SELECTION EDIT VERIFY PASS:', 'EDIT OUTLINE VERIFY PASS:', 'MOVE AXES VERIFY PASS:', 'CROSSHAIR VERIFY PASS:', 'PALETTE HISTORY VERIFY PASS:', 'EDITING PALETTE VERIFY PASS:')) {
                if (!$result.Contains($marker)) { throw "Incomplete $($target.Name) fixture: missing '$marker'" }
            }
            if ($result.Contains('Client gametests failed with an exception')) { throw "Client fixture failed: $($target.Name)" }
            & (Join-Path $PSScriptRoot 'check-command-pixels.ps1') -Only $target.Name -NotBefore $runStarted
            Write-Host "Verified commands, selection editing and palette history: $($target.Name)"
        } finally { Pop-Location }
    }
} finally { $env:JAVA_HOME = $previousJava }
