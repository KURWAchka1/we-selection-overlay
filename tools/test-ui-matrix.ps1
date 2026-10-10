param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [string[]]$Only = @(),
    [switch]$DesktopWorker
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
if (!$DesktopWorker) {
    & (Join-Path $PSScriptRoot 'test-desktop/probe-cursor.ps1') -Java21 $Java21
    Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Java21=$Java21; Java25=$Java25; Only=$Only; DesktopWorker=$true}
    return
}
Assert-WesoTestDesktop
$root = Split-Path -Parent $PSScriptRoot
$logDirectory = Join-Path $root 'build/ui-validation'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$targets = @(
    @{Name='fabric-1.21.11'; Module='WEaddon_fabric_1.21.11'; Java=$Java21; Args=@('-I', '../WEaddon_fabric_universal_1.21.11-26.2/tools/isolation-test.init.gradle', 'testUniversalIsolation')},
    @{Name='fabric-26.1'; Module='WEaddon_lightposition'; Java=$Java25; Args=@('-I', '../WEaddon_fabric_universal_1.21.11-26.2/tools/isolation-test.init.gradle', 'testUniversalIsolation')},
    @{Name='fabric-26.2'; Module='WEaddon_fabric_26.2'; Java=$Java25; Args=@('-I', '../WEaddon_fabric_universal_1.21.11-26.2/tools/isolation-test.init.gradle', 'testUniversalIsolation')},
    @{Name='neoforge-1.21.11'; Module='WEaddon_neoforge_universal_1.21.11-26.2'; Java=$Java21; Args=@('-I', 'tools/isolation-test.init.gradle', 'runIsolationClient')},
    @{Name='neoforge-26.1'; Module='WEaddon_neoforge_universal_1.21.11-26.2'; Java=$Java25; Args=@('-I', 'tools/isolation-test.init.gradle', 'runIsolationClient', '-Pneo_version=26.1.2.75', '-Psmoke_java_version=25')},
    @{Name='neoforge-26.2'; Module='WEaddon_neoforge_universal_1.21.11-26.2'; Java=$Java25; Args=@('-I', 'tools/isolation-test.init.gradle', 'runIsolationClient', '-Pneo_version=26.2.0.67', '-Psmoke_java_version=25')}
)

$previousJava = $env:JAVA_HOME
try {
    foreach ($target in $targets) {
        if ($Only.Count -gt 0 -and $target.Name -notin $Only) { continue }
        if ($target.Name.StartsWith('neoforge-')) {
            $version = if ($target.Name.EndsWith('1.21.11')) { '1.21.11' } elseif ($target.Name.EndsWith('26.1')) { '26.1' } else { '26.2' }
            $world = Join-Path $root "$($target.Module)/run-isolation-test-$version/saves/weso-fixture/level.dat"
            if (!(Test-Path -LiteralPath $world)) { throw "Missing NeoForge $version test world: $world. See TESTING.md before starting the client." }
        }
        Write-Host "Testing $($target.Name) with the packaged universal JAR"
        $env:JAVA_HOME = $target.Java
        Push-Location (Join-Path $root $target.Module)
        try {
            Assert-WesoTestDesktop
            & .\gradlew.bat @($target.Args) --offline --no-daemon --max-workers=2 --console=plain 2>&1 |
                Tee-Object -FilePath (Join-Path $logDirectory "$($target.Name).log")
            if ($LASTEXITCODE -ne 0) { throw "Runtime test failed: $($target.Name)" }
        } finally { Pop-Location }
    }
} finally { $env:JAVA_HOME = $previousJava }
