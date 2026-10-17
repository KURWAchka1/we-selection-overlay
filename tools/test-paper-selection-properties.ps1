param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [ValidateSet('1.21.11', '26.1.2', '26.2')][string[]]$Minecraft = @('1.21.11', '26.1.2', '26.2'),
    [switch]$AcceptEula
)
$ErrorActionPreference = 'Stop'
if (!$AcceptEula) { throw 'Owner acceptance of https://www.minecraft.net/eula is required' }
if (@($Minecraft | Select-Object -Unique).Count -ne $Minecraft.Count) { throw 'Duplicate test version' }
$root = Split-Path -Parent $PSScriptRoot
$build = [IO.Path]::GetFullPath((Join-Path $root 'build'))
$proofRoot = Join-Path $build 'bridge-selection-properties'
foreach ($java in @($Java21, $Java25)) {
    if (!(Test-Path -LiteralPath (Join-Path $java 'bin/java.exe'))) { throw "Missing Java: $java" }
}
foreach ($version in $Minecraft) {
    $server = [IO.Path]::GetFullPath((Join-Path $proofRoot $version))
    if (!$server.StartsWith($build + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Fixture escaped build' }
    foreach ($path in @($build, $proofRoot, $server)) {
        if (Test-Path -LiteralPath $path) {
            if ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse point not allowed: $path" }
        }
    }
    if ((Test-Path -LiteralPath $server) -and @(Get-ChildItem -LiteralPath $server -Force).Count -gt 0) { throw "Preserve previous attempt first: $server" }
}
$previousJava = $env:JAVA_HOME
Push-Location $root
try {
    $env:JAVA_HOME = $Java21
    & .\WEaddon_fabric_1.21.11\gradlew.bat -p server-bridge :paper:jar :paper-test-fixture:jar --offline --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Companion/property fixture build failed' }
    if ($Minecraft -contains '26.2') {
        $env:JAVA_HOME = $Java25
        & .\WEaddon_fabric_1.21.11\gradlew.bat -p tools/bridge-load-clients check installDist --offline --no-daemon --max-workers=2 --console=plain
        if ($LASTEXITCODE -ne 0) { throw 'Pinned 26.2 wire helper checks failed' }
    }
    foreach ($version in $Minecraft) {
        $server = Join-Path $proofRoot $version
        $legacy = $version -eq '1.21.11'
        $bootstrap = Join-Path $build $(if ($legacy) { 'bridge-paper-proof' } else { "bridge-native-fault-proof/$version" })
        $paper = if ($legacy) { Join-Path $bootstrap 'server.jar' } else { Join-Path $build $(if ($version -eq '26.1.2') { 'bridge-server-downloads/paper-26.1.2-74.jar' } else { 'bridge-server-downloads/paper-26.2-133.jar' }) }
        $worldEdit = if ($legacy) { Join-Path $bootstrap 'plugins/worldedit.jar' } else { Join-Path $build $(if ($version -eq '26.1.2') { 'bridge-server-downloads/worldedit-bukkit-7.4.3.jar' } else { 'bridge-server-downloads/worldedit-bukkit-7.4.5.jar' }) }
        New-Item -ItemType Directory -Path (Join-Path $server 'plugins') -Force | Out-Null
        Copy-Item -LiteralPath $paper -Destination (Join-Path $server 'server.jar')
        Copy-Item -LiteralPath $worldEdit -Destination (Join-Path $server 'plugins/worldedit.jar')
        foreach ($name in @('cache', 'libraries', 'versions')) {
            $source = Join-Path $bootstrap $name
            if (!(Test-Path -LiteralPath $source -PathType Container)) { throw "Missing verified bootstrap cache: $source" }
            Copy-Item -LiteralPath $source -Destination (Join-Path $server $name) -Recurse
        }
        Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') -Destination (Join-Path $server 'plugins')
        Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar') -Destination (Join-Path $server 'plugins')
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'server-bridge-test/server.properties') -Destination (Join-Path $server 'server.properties')
        'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding ASCII
        $serverJava = if ($legacy) { $Java21 } else { $Java25 }
        & node (Join-Path $PSScriptRoot 'server-bridge-test/paper-selection-properties.mjs') $serverJava $server $version
        if ($LASTEXITCODE -ne 0) { throw "Selection property proof failed: $version" }
    }
} finally { Pop-Location; $env:JAVA_HOME = $previousJava }
