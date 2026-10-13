param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$')][string]$ModVersion = '1.8.0-bridge-dev',
    [switch]$AcceptEula,
    [switch]$EditFaults,
    [switch]$DesktopWorker
)
$ErrorActionPreference = 'Stop'
if (!$AcceptEula) { throw 'Owner acceptance of https://www.minecraft.net/eula is required' }
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
if (!$DesktopWorker) {
    & (Join-Path $PSScriptRoot 'test-desktop/probe-cursor.ps1') -Java21 $Java21
    Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Java21=$Java21; ModVersion=$ModVersion; AcceptEula=$true; EditFaults=$EditFaults.IsPresent; DesktopWorker=$true}
    return
}
Assert-WesoTestDesktop
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'build/bridge-paper-proof'
$server = Join-Path $root $(if ($EditFaults) { 'build/bridge-native-fault-proof' } else { 'build/bridge-native-proof' })
foreach ($loader in @('fabric', 'neoforge')) { Assert-WesoTestArtifact -Loader $loader -ModVersion $ModVersion }
foreach ($file in @('server.jar', 'plugins/worldedit.jar')) {
    if (!(Test-Path -LiteralPath (Join-Path $source $file) -PathType Leaf)) { throw "Missing pinned Paper dependency: $file" }
}
$previousJava = $env:JAVA_HOME
$env:JAVA_HOME = $Java21
Push-Location $root
try {
    & .\WEaddon_fabric_1.21.11\gradlew.bat -p server-bridge :paper:jar :paper-test-fixture:jar --offline --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Paper fixture build failed' }
    New-Item -ItemType Directory -Force -Path (Join-Path $server 'plugins') | Out-Null
    Copy-Item -LiteralPath (Join-Path $source 'server.jar') -Destination $server
    # Reuse the already verified generated Paper bootstrap cache, not a user world.
    foreach ($directory in @('cache', 'libraries', 'versions')) {
        $destination = Join-Path $server $directory
        if (!(Test-Path -LiteralPath $destination) -and (Test-Path -LiteralPath (Join-Path $source $directory))) {
            Copy-Item -LiteralPath (Join-Path $source $directory) -Destination $destination -Recurse
        }
    }
    Copy-Item -LiteralPath (Join-Path $source 'plugins/worldedit.jar') -Destination (Join-Path $server 'plugins/worldedit.jar')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar') -Destination (Join-Path $server 'plugins')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') -Destination (Join-Path $server 'companion.jar')
    $properties = if ($EditFaults) { 'fault-server.properties' } else { 'server.properties' }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "server-bridge-test/$properties") -Destination (Join-Path $server 'server.properties')
    'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding ASCII
    $proof = if ($EditFaults) { 'native-edit-fault-proof.mjs' } else { 'native-paper-proof.mjs' }
    & node (Join-Path $PSScriptRoot "server-bridge-test/$proof") $Java21 $server $ModVersion (Get-Process -Id $PID).Path
    if ($LASTEXITCODE -ne 0) { throw 'Native Paper proof failed; see build/bridge-native-proof/' }
} finally { Pop-Location; $env:JAVA_HOME = $previousJava }
