param(
    [Parameter(Mandatory)][string]$Java21,
    [Parameter(Mandatory)][string]$Java25
)

$ErrorActionPreference = 'Stop'
$projects = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$previousJava = $env:JAVA_HOME
$targets = @(
    @{ Name = 'WEaddon_fabric_1.21.11'; Java = $Java21 },
    @{ Name = 'WEaddon_lightposition'; Java = $Java25 },
    @{ Name = 'WEaddon_fabric_26.2'; Java = $Java25 },
    @{ Name = 'WEaddon_neoforge_1.21.11'; Java = $Java21 },
    @{ Name = 'WEaddon_neoforge_26.1'; Java = $Java25 },
    @{ Name = 'WEaddon_neoforge_26.2'; Java = $Java25 },
    @{ Name = 'WEaddon_fabric_universal_1.21.11-26.2'; Java = $Java21 },
    @{ Name = 'WEaddon_neoforge_universal_1.21.11-26.2'; Java = $Java21 }
)

try {
    foreach ($target in $targets) {
        if (-not (Test-Path -LiteralPath (Join-Path $target.Java 'bin/java.exe'))) {
            throw "JDK not found: $($target.Java)"
        }
        Push-Location -LiteralPath (Join-Path $projects $target.Name)
        try {
            $env:JAVA_HOME = $target.Java
            & .\gradlew.bat build --console=plain --no-daemon
            if ($LASTEXITCODE -ne 0) { throw "Build failed: $($target.Name)" }
        } finally {
            Pop-Location
        }
    }
} finally {
    $env:JAVA_HOME = $previousJava
}
