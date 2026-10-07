$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'manual-test.ps1')

function Assert-Launcher([bool]$Condition, [string]$Message) {
    if (!$Condition) { throw $Message }
}

$root = Split-Path -Parent $PSScriptRoot
$targets = @(Get-WesoTargets $root)
Assert-Launcher ($targets.Count -eq 6) 'Expected six manual targets.'
Assert-Launcher ((@($targets.Name | Select-Object -Unique)).Count -eq 6) 'Duplicate target names.'
foreach ($target in $targets) {
    Assert-Launcher ($target.BuildModules.Count -eq 4) 'Each loader must build all three native runtimes and its packager.'
    Assert-Launcher ($target.BuildModules[-1] -eq $target.Packager) 'Packager must be built last.'
    $arguments = @(Get-WesoLaunchArguments $target)
    Assert-Launcher ($arguments -contains "-Pweso_manual_loader=$($target.Loader)") 'Wrong loader argument.'
    Assert-Launcher ($arguments -contains "-Pweso_manual_family=$($target.Family)") 'Wrong family argument.'
    if ($target.Loader -eq 'neoforge') {
        Assert-Launcher ($target.Module -eq $target.Packager) 'NeoForge must run from its universal packager.'
        Assert-Launcher ($arguments -contains "-Pneo_version=$($target.NeoVersion)") 'Missing native NeoForge version.'
        Assert-Launcher ($arguments -contains "-Psmoke_java_version=$($target.Java)") 'Wrong NeoForge JVM.'
    } else {
        Assert-Launcher ($target.Module -eq $target.NativeModule) 'Fabric needs its game-specific Loom project.'
        Assert-Launcher ($arguments.Count -eq 4) 'Fabric must not receive NeoForge overrides.'
    }
    $Loader = $target.Loader
    $Version = $target.Family
    $PlanOnly = $true
    Start-WesoManualTest
}
Assert-Launcher (($targets | Where-Object Name -eq 'neoforge-26.1').Minecraft -eq '26.1.2') 'NeoForge exact game version must be displayed.'

$script:answers = [Collections.Generic.Queue[string]]::new()
function Read-Host { return $script:answers.Dequeue() }
foreach ($answer in @('no', '0', '3', '2')) { $script:answers.Enqueue($answer) }
Assert-Launcher ((Select-WesoOption 'Menu validation' @('Fabric', 'NeoForge')) -eq 1) 'Invalid menu choices must be retried.'
$script:answers.Enqueue('q')
Assert-Launcher ((Select-WesoOption 'Menu exit' @('Fabric', 'NeoForge')) -eq -1) 'Q must cancel.'

$temporaryRoot = Join-Path $root 'build/manual-launcher-unit'
$temporary = Join-Path $temporaryRoot ([guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $temporary | Out-Null
try {
    $propertiesPath = Join-Path $temporary 'gradle.properties'
    [IO.File]::WriteAllLines($propertiesPath, @('# ignored', '', 'mod_version = 1.8.0', 'url=https://test.invalid/?key=value'))
    $properties = Read-WesoProperties $propertiesPath
    Assert-Launcher ($properties.mod_version -eq '1.8.0') 'Properties whitespace was not trimmed.'
    Assert-Launcher ($properties.url -eq 'https://test.invalid/?key=value') 'Property values containing = were truncated.'

    $fixtureRoot = Join-Path $temporary 'repository'
    foreach ($module in ($targets.BuildModules | Select-Object -Unique)) {
        $directory = Join-Path $fixtureRoot $module
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
        Copy-Item -LiteralPath (Join-Path $root "$module/gradle.properties") -Destination (Join-Path $directory 'gradle.properties')
    }
    [IO.File]::WriteAllText((Join-Path $fixtureRoot 'WEaddon_fabric_26.2/gradle.properties'), 'mod_version=invalid')
    $rejected = $false
    try { Get-WesoTargets $fixtureRoot | Out-Null } catch { $rejected = $_.Exception.Message -like 'Version mismatch*' }
    Assert-Launcher $rejected 'Mixed source/artifact versions must stop the launcher.'

    $jdk = Join-Path $temporary 'jdk'
    New-Item -ItemType Directory -Force -Path (Join-Path $jdk 'bin') | Out-Null
    [IO.File]::WriteAllText((Join-Path $jdk 'bin/java.exe'), '')
    [IO.File]::WriteAllText((Join-Path $jdk 'bin/javac.exe'), '')
    [IO.File]::WriteAllText((Join-Path $jdk 'release'), 'JAVA_VERSION="21.0.8"')
    Assert-Launcher (Test-WesoJdk $jdk 21) 'JDK 21 metadata was not detected.'
    Assert-Launcher (!(Test-WesoJdk $jdk 25)) 'Wrong JDK major version was accepted.'
    [IO.File]::WriteAllText((Join-Path $jdk 'release'), 'JAVA_VERSION="212.0.1"')
    Assert-Launcher (!(Test-WesoJdk $jdk 21)) 'Major version prefix was mistaken for an exact major version.'
    [IO.File]::WriteAllText((Join-Path $jdk 'release'), '# no version')
    Assert-Launcher (!(Test-WesoJdk $jdk 21)) 'Missing JDK metadata must be rejected.'

    $wrapper = Join-Path $temporary 'gradlew.bat'
    $log = Join-Path $temporary 'wrapper.log'
    $previousJava = $env:JAVA_HOME
    $previousPath = $env:PATH
    $previousLocation = (Get-Location).Path
    [IO.File]::WriteAllLines($wrapper, @('@echo off', 'echo harmless warning 1>&2', 'exit /b 0'))
    Invoke-WesoGradle $temporary $jdk @('build', '--offline') $log | Out-Null
    Assert-Launcher ($env:JAVA_HOME -eq $previousJava -and $env:PATH -eq $previousPath) 'Environment was not restored after success.'
    Assert-Launcher ((Get-Location).Path -eq $previousLocation) 'Location was not restored after success.'
    [IO.File]::WriteAllLines($wrapper, @('@echo off', 'echo simulated build failure 1>&2', 'exit /b 17'))
    $failed = $false
    try { Invoke-WesoGradle $temporary $jdk @('build') $log | Out-Null } catch {
        $failed = $_.Exception.Message -like 'Gradle failed (exit 17)*'
    }
    Assert-Launcher $failed 'Failed builds must not be silently ignored.'
    Assert-Launcher ($env:JAVA_HOME -eq $previousJava -and $env:PATH -eq $previousPath) 'Environment was not restored after failure.'
    Assert-Launcher ((Get-Location).Path -eq $previousLocation) 'Location was not restored after failure.'
} finally {
    $resolved = [IO.Path]::GetFullPath($temporary)
    $allowed = [IO.Path]::GetFullPath($temporaryRoot) + [IO.Path]::DirectorySeparatorChar
    if (!$resolved.StartsWith($allowed, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe unit fixture cleanup path.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
Write-Host 'MANUAL LAUNCHER UNIT PASS (six targets, menu, versions, JDKs, native errors, environment restoration)'
