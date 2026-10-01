param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [string[]]$Only = @()
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$logs = Join-Path $root 'build/command-validation'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
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
        $run = if ($target.Family) { "run-command-test-$($target.Family)" } else { 'run-command-test' }
        foreach ($name in @('client.properties', 'command-preview.properties')) {
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
            & .\gradlew.bat @($target.Args) --offline --no-daemon --console=plain 2>&1 |
                Tee-Object -FilePath (Join-Path $logs "$($target.Name).log")
            if ($LASTEXITCODE -ne 0) { throw "Command test failed: $($target.Name)" }
        } finally { Pop-Location }
    }
} finally { $env:JAVA_HOME = $previousJava }
