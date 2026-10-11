param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$PaperJar,
    [Parameter(Mandatory = $true)][string]$WorldEditJar,
    [Parameter(Mandatory = $true)][string]$NodeDependencies,
    [switch]$AcceptEula,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
if (!$AcceptEula) { throw 'Owner acceptance of https://www.minecraft.net/eula is required: -AcceptEula' }
$root = Split-Path -Parent $PSScriptRoot
foreach ($path in @((Join-Path $Java21 'bin/java.exe'), $PaperJar, $WorldEditJar)) {
    if (!(Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing fixture dependency: $path" }
}
$previousJava = $env:JAVA_HOME
$env:JAVA_HOME = $Java21
Push-Location $root
try {
    $gradleArgs = @('-p', 'server-bridge', ':paper:jar', ':paper-test-fixture:jar', '--no-daemon', '--max-workers=2', '--console=plain')
    if ($Offline) { $gradleArgs += '--offline' }
    & '.\WEaddon_fabric_1.21.11\gradlew.bat' @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not build Paper bridge/fixture' }
    $server = Join-Path $root 'build/bridge-paper-proof'
    $plugins = Join-Path $server 'plugins'
    New-Item -ItemType Directory -Path $plugins -Force | Out-Null
    Copy-Item -LiteralPath $PaperJar -Destination (Join-Path $server 'server.jar')
    Copy-Item -LiteralPath $WorldEditJar -Destination (Join-Path $plugins 'worldedit.jar')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') -Destination $plugins
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar') -Destination $plugins
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'server-bridge-test/server.properties') -Destination $server
    'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding ASCII
    & node (Join-Path $PSScriptRoot 'server-bridge-test/paper-wire-proof.mjs') $Java21 $server $NodeDependencies
    if ($LASTEXITCODE -ne 0) { throw 'Paper bridge wire proof failed; see build/bridge-paper-proof/' }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
}
