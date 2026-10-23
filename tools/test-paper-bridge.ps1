param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$PaperJar,
    [Parameter(Mandatory = $true)][string]$WorldEditJar,
    [Parameter(Mandatory = $true)][string]$NodeDependencies,
    [ValidatePattern('^\d{8}-\d{6}-[0-9a-f]{8}$')][string]$RunId = (
        [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)),
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
    $server = Join-Path $root "build/bridge-paper-proof/$RunId"
    if (Test-Path -LiteralPath $server) { throw 'Never reuse an existing wire-run directory; choose a new RunId' }
    $gradleArgs = @('-p', 'server-bridge', ':paper:jar', ':paper-test-fixture:jar', '--no-daemon', '--max-workers=2', '--console=plain')
    if ($Offline) { $gradleArgs += '--offline' }
    & '.\WEaddon_fabric_1.21.11\gradlew.bat' @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not build Paper bridge/fixture' }
    $plugins = Join-Path $server 'plugins'
    New-Item -ItemType Directory -Path $plugins -Force | Out-Null
    Copy-Item -LiteralPath $PaperJar -Destination (Join-Path $server 'server.jar')
    $bootstrap = Join-Path $root 'build/bridge-paper-proof'
    $bootstrapJar = Join-Path $bootstrap 'server.jar'
    if ((Test-Path -LiteralPath $bootstrapJar -PathType Leaf) -and
        (Get-FileHash -LiteralPath $bootstrapJar -Algorithm SHA256).Hash -eq (Get-FileHash -LiteralPath $PaperJar -Algorithm SHA256).Hash) {
        foreach ($directory in @('cache','libraries','versions')) {
            $source = Join-Path $bootstrap $directory
            if (Test-Path -LiteralPath $source -PathType Container) {
                Copy-Item -LiteralPath $source -Destination (Join-Path $server $directory) -Recurse
            }
        }
    }
    Copy-Item -LiteralPath $WorldEditJar -Destination (Join-Path $plugins 'worldedit.jar')
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') -Destination $plugins
    Copy-Item -LiteralPath (Join-Path $root 'server-bridge/paper-test-fixture/build/libs/weso-paper-test-fixture.jar') -Destination $plugins
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'server-bridge-test/server.properties') -Destination $server
    'eula=true' | Set-Content -LiteralPath (Join-Path $server 'eula.txt') -Encoding ASCII
    & node (Join-Path $PSScriptRoot 'server-bridge-test/paper-wire-proof.mjs') $Java21 $server $NodeDependencies
    if ($LASTEXITCODE -ne 0) { throw "Paper bridge wire proof failed; see $server" }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
}
