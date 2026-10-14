param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [string]$Java25,
    [ValidateSet('1.21.11', '26.1.2', '26.2')][string]$Minecraft = '1.21.11',
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$')][string]$ModVersion = '1.8.0-bridge-dev',
    [switch]$AcceptEula,
    [switch]$EditFaults,
    [switch]$EditGestures,
    [switch]$DesktopWorker
)
$ErrorActionPreference = 'Stop'
if (!$AcceptEula) { throw 'Owner acceptance of https://www.minecraft.net/eula is required' }
if ($EditFaults -and $EditGestures) { throw 'Choose one isolated edit proof mode' }
if ($Minecraft -ne '1.21.11' -and ($EditFaults -or !$Java25)) { throw '26.x proof requires Java25; edit-fault mode is not ported yet' }
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
if (!$DesktopWorker) {
    & (Join-Path $PSScriptRoot 'test-desktop/probe-cursor.ps1') -Java21 $Java21
    Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Java21=$Java21; Java25=$Java25; Minecraft=$Minecraft; ModVersion=$ModVersion; AcceptEula=$true; EditFaults=$EditFaults.IsPresent; EditGestures=$EditGestures.IsPresent; DesktopWorker=$true}
    return
}
Assert-WesoTestDesktop
$root = Split-Path -Parent $PSScriptRoot
$source = Join-Path $root 'build/bridge-paper-proof'
$server = Join-Path $root $(if ($EditFaults) { 'build/bridge-native-fault-proof' } elseif ($EditGestures) { 'build/bridge-native-gesture-proof' } else { 'build/bridge-native-proof' })
if ($Minecraft -ne '1.21.11') { $server = Join-Path $server $Minecraft }
foreach ($loader in @('fabric', 'neoforge')) { Assert-WesoTestArtifact -Loader $loader -ModVersion $ModVersion }
if ($Minecraft -eq '1.21.11') {
    $paperFile = Join-Path $source 'server.jar'
    $worldEditFile = Join-Path $source 'plugins/worldedit.jar'
} else {
    $downloads = Join-Path $root 'build/bridge-server-downloads'
    $paperFile = Join-Path $downloads $(if ($Minecraft -eq '26.1.2') { 'paper-26.1.2-74.jar' } else { 'paper-26.2-133.jar' })
    $worldEditFile = Join-Path $downloads $(if ($Minecraft -eq '26.1.2') { 'worldedit-bukkit-7.4.3.jar' } else { 'worldedit-bukkit-7.4.5.jar' })
}
foreach ($file in @($paperFile, $worldEditFile)) {
    if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing pinned Paper dependency: $file" }
}
$previousJava = $env:JAVA_HOME
$env:JAVA_HOME = $Java21
Push-Location $root
try {
    & .\WEaddon_fabric_1.21.11\gradlew.bat -p server-bridge :paper:jar :paper-test-fixture:jar --offline --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Paper fixture build failed' }
    New-Item -ItemType Directory -Force -Path (Join-Path $server 'plugins') | Out-Null
    Copy-Item -LiteralPath $paperFile -Destination (Join-Path $server 'server.jar')
    # Reuse the already verified generated Paper bootstrap cache, not a user world.
    foreach ($directory in @('cache', 'libraries', 'versions')) {
        $destination = Join-Path $server $directory
        if ($Minecraft -eq '1.21.11' -and !(Test-Path -LiteralPath $destination) -and (Test-Path -LiteralPath (Join-Path $source $directory))) {
            Copy-Item -LiteralPath (Join-Path $source $directory) -Destination $destination -Recurse
        }
    }
    Copy-Item -LiteralPath $worldEditFile -Destination (Join-Path $server 'plugins/worldedit.jar')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar') -Destination (Join-Path $server 'plugins')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') -Destination (Join-Path $server 'companion.jar')
    $properties = if ($EditFaults) { 'fault-server.properties' } else { 'server.properties' }
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot "server-bridge-test/$properties") -Destination (Join-Path $server 'server.properties')
    'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding ASCII
    $proof = if ($EditFaults) { 'native-edit-fault-proof.mjs' } elseif ($EditGestures) { 'native-edit-gesture-proof.mjs' } else { 'native-paper-proof.mjs' }
    $serverJava = if ($Minecraft -eq '1.21.11') { $Java21 } else { $Java25 }
    $arguments = @($serverJava, $server, $ModVersion, (Get-Process -Id $PID).Path)
    if (!$EditFaults) { $arguments += $Minecraft }
    & node (Join-Path $PSScriptRoot "server-bridge-test/$proof") @arguments
    if ($LASTEXITCODE -ne 0) { throw "Native Paper proof failed; see $server" }
} finally { Pop-Location; $env:JAVA_HOME = $previousJava }
