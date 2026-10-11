param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$previousJava = $env:JAVA_HOME
$logs = Join-Path $root 'build/bridge-validation'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$targets = @(
    @{Module='WEaddon_fabric_1.21.11'; Java=$Java21; Tasks=@('remapJar')},
    @{Module='WEaddon_lightposition'; Java=$Java25; Tasks=@('jar')},
    @{Module='WEaddon_fabric_26.2'; Java=$Java25; Tasks=@('jar')},
    @{Module='WEaddon_neoforge_1.21.11'; Java=$Java21; Tasks=@('jar')},
    @{Module='WEaddon_neoforge_26.1'; Java=$Java25; Tasks=@('jar')},
    @{Module='WEaddon_neoforge_26.2'; Java=$Java25; Tasks=@('jar')},
    @{Module='WEaddon_fabric_universal_1.21.11-26.2'; Java=$Java21; Tasks=@('test')},
    @{Module='WEaddon_neoforge_universal_1.21.11-26.2'; Java=$Java21; Tasks=@('test')}
)
try {
    foreach ($target in $targets) {
        if (!(Test-Path -LiteralPath (Join-Path $target.Java 'bin/java.exe'))) { throw 'Missing JDK' }
        $env:JAVA_HOME = $target.Java
        Push-Location (Join-Path $root $target.Module)
        try {
            $gradleArgs = @($target.Tasks) + @('-Pmod_version=1.8.0-bridge-dev', '--no-daemon', '--max-workers=2', '--console=plain')
            if ($Offline) { $gradleArgs += '--offline' }
            & '.\gradlew.bat' @gradleArgs 2>&1 | Tee-Object -FilePath (Join-Path $logs "package-$($target.Module).log")
            if ($LASTEXITCODE -ne 0) { throw "Bridge packaging failed: $($target.Module)" }
        } finally { Pop-Location }
    }
} finally { $env:JAVA_HOME = $previousJava }
Write-Output 'Development universal artifacts verified; published 1.8.0 archives were not overwritten.'
