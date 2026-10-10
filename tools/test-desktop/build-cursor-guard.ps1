param([Parameter(Mandatory = $true)][string]$Java21, [switch]$Offline)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$previous = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $Java21
    $arguments = @('-p', (Join-Path $PSScriptRoot 'agent'), 'build', '--no-daemon', '--max-workers=2', '--console=plain')
    if ($Offline) { $arguments += '--offline' }
    & (Join-Path $root 'WEaddon_fabric_universal_1.21.11-26.2/gradlew.bat') @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Cursor isolation guard verification failed. No Minecraft client will be launched.' }
} finally { $env:JAVA_HOME = $previous }
