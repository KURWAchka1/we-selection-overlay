[CmdletBinding()]
param(
    [ValidateSet('fabric', 'neoforge')][string]$Loader,
    [ValidateSet('1.21.11', '26.1', '26.2')][string]$Version,
    [string]$Java21,
    [string]$Java25,
    [switch]$Offline,
    [switch]$PlanOnly,
    [switch]$PrepareOnly
)

function Read-WesoProperties([string]$Path) {
    $properties = @{}
    foreach ($line in Get-Content -LiteralPath $Path) {
        $line = $line.Trim()
        if (!$line -or $line.StartsWith('#') -or $line.StartsWith('!')) { continue }
        $pair = $line -split '=', 2
        if ($pair.Count -eq 2) { $properties[$pair[0].Trim()] = $pair[1].Trim() }
    }
    return $properties
}

function Get-WesoTargets([string]$Root) {
    $families = @(
        @{ Family = '1.21.11'; Fabric = 'WEaddon_fabric_1.21.11'; Neo = 'WEaddon_neoforge_1.21.11'; Java = 21 },
        @{ Family = '26.1'; Fabric = 'WEaddon_lightposition'; Neo = 'WEaddon_neoforge_26.1'; Java = 25 },
        @{ Family = '26.2'; Fabric = 'WEaddon_fabric_26.2'; Neo = 'WEaddon_neoforge_26.2'; Java = 25 }
    )
    $releaseVersion = $null
    foreach ($loaderName in @('fabric', 'neoforge')) {
        $packager = "WEaddon_${loaderName}_universal_1.21.11-26.2"
        $packageProperties = Read-WesoProperties (Join-Path $Root "$packager/gradle.properties")
        if (!$releaseVersion) { $releaseVersion = $packageProperties.mod_version }
        if (!$releaseVersion -or $packageProperties.mod_version -ne $releaseVersion) {
            throw 'Universal packagers must have the same mod_version.'
        }
        foreach ($family in $families) {
            $nativeModule = if ($loaderName -eq 'fabric') { $family.Fabric } else { $family.Neo }
            $properties = Read-WesoProperties (Join-Path $Root "$nativeModule/gradle.properties")
            if ($properties.mod_version -ne $releaseVersion) {
                throw "Version mismatch in ${nativeModule}: expected $releaseVersion."
            }
            $minecraft = $properties.minecraft_version
            if ($loaderName -eq 'neoforge' -and $family.Family -ne '1.21.11') {
                $parts = $properties.neo_version.Split('.')
                $minecraft = if ($parts[2] -eq '0') { $parts[0..1] -join '.' } else { $parts[0..2] -join '.' }
            }
            [pscustomobject]@{
                Name = "$loaderName-$($family.Family)"
                Loader = $loaderName
                Family = $family.Family
                Minecraft = $minecraft
                Java = $family.Java
                NativeModule = $nativeModule
                Module = $(if ($loaderName -eq 'fabric') { $nativeModule } else { $packager })
                Packager = $packager
                NeoVersion = $properties.neo_version
                ModVersion = $releaseVersion
                LoaderVersion = $(if ($loaderName -eq 'fabric') { $properties.loader_version } else { $properties.neo_version })
                BuildModules = @($families | ForEach-Object {
                    if ($loaderName -eq 'fabric') { $_.Fabric } else { $_.Neo }
                }) + @($packager)
            }
        }
    }
}

function Select-WesoOption([string]$Title, [string[]]$Options) {
    Write-Host "`n$Title"
    for ($i = 0; $i -lt $Options.Count; $i++) { Write-Host "  $($i + 1). $($Options[$i])" }
    Write-Host '  Q. Exit'
    while ($true) {
        $answer = Read-Host 'Choose a number'
        if ($answer -match '^[qQ]$') { return -1 }
        $number = 0
        if ([int]::TryParse($answer, [ref]$number) -and $number -ge 1 -and $number -le $Options.Count) {
            return $number - 1
        }
        Write-Host 'Invalid choice. Try again.'
    }
}

function Test-WesoJdk([string]$Path, [int]$Major) {
    if (!$Path -or !(Test-Path -LiteralPath (Join-Path $Path 'bin/java.exe')) -or
            !(Test-Path -LiteralPath (Join-Path $Path 'bin/javac.exe'))) { return $false }
    $release = Join-Path $Path 'release'
    if (!(Test-Path -LiteralPath $release)) { return $false }
    $properties = Read-WesoProperties $release
    if (!$properties.JAVA_VERSION) { return $false }
    return $properties.JAVA_VERSION.Trim('"') -match "^$Major([.+]|$)"
}

function Find-WesoJdk([int]$Major, [string]$ExplicitPath) {
    if ($ExplicitPath) {
        if (!(Test-WesoJdk $ExplicitPath $Major)) { throw "Not a JDK ${Major}: $ExplicitPath" }
        return [IO.Path]::GetFullPath($ExplicitPath)
    }
    $candidates = @([Environment]::GetEnvironmentVariable("WESO_JAVA_$Major"), $env:JAVA_HOME)
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) { $candidates += Split-Path -Parent (Split-Path -Parent $javaCommand.Source) }
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, (Join-Path $env:USERPROFILE '.jdks'))) {
        if (!$base -or !(Test-Path -LiteralPath $base)) { continue }
        $directories = if ((Split-Path -Leaf $base) -eq '.jdks') { @($base) } else {
            @('Java', 'Eclipse Adoptium', 'Microsoft', 'Amazon Corretto', 'Zulu') | ForEach-Object { Join-Path $base $_ }
        }
        foreach ($directory in $directories) {
            if (Test-Path -LiteralPath $directory) {
                $candidates += @(Get-ChildItem -LiteralPath $directory -Directory | Select-Object -ExpandProperty FullName)
            }
        }
    }
    foreach ($candidate in $candidates) {
        if (Test-WesoJdk $candidate $Major) { return [IO.Path]::GetFullPath($candidate) }
    }
    throw "JDK $Major not found. Install it or pass -Java$Major 'C:\path\to\jdk-$Major'."
}

function Get-WesoLaunchArguments($Target) {
    $arguments = @('-I', '../tools/manual-test.init.gradle', "-Pweso_manual_loader=$($Target.Loader)",
        "-Pweso_manual_family=$($Target.Family)")
    if ($Target.Loader -eq 'neoforge') {
        $arguments += @("-Pneo_version=$($Target.NeoVersion)", "-Psmoke_java_version=$($Target.Java)")
    }
    return $arguments
}

function Invoke-WesoGradle([string]$Directory, [string]$Jdk, [string[]]$Arguments, [string]$Log) {
    $previousJava = $env:JAVA_HOME
    $previousPath = $env:PATH
    $previousErrorPreference = $ErrorActionPreference
    Push-Location -LiteralPath $Directory
    try {
        $env:JAVA_HOME = $Jdk
        $env:PATH = (Join-Path $Jdk 'bin') + [IO.Path]::PathSeparator + $previousPath
        # Windows PowerShell treats native stderr as errors even for harmless JVM warnings.
        $ErrorActionPreference = 'Continue'
        & .\gradlew.bat @Arguments --no-daemon --console=plain --no-configuration-cache 2>&1 |
            Tee-Object -FilePath $Log -Append
        $exitCode = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if ($exitCode -ne 0) { throw "Gradle failed (exit $exitCode). Log: $Log" }
    } finally {
        $ErrorActionPreference = $previousErrorPreference
        $env:JAVA_HOME = $previousJava
        $env:PATH = $previousPath
        Pop-Location
    }
}

function Start-WesoManualTest {
    $ErrorActionPreference = 'Stop'
    $root = Split-Path -Parent $PSScriptRoot
    $targets = @(Get-WesoTargets $root)
    Write-Host "WorldEdit Selection Overlay $($targets[0].ModVersion) - manual test"
    if (!$Loader) {
        $choice = Select-WesoOption 'Loader' @('Fabric', 'NeoForge')
        if ($choice -lt 0) { return }
        $Loader = @('fabric', 'neoforge')[$choice]
    }
    $available = @($targets | Where-Object Loader -eq $Loader)
    if (!$Version) {
        $labels = @($available | ForEach-Object {
            $label = "Minecraft $($_.Minecraft) / Java $($_.Java)"
            if ($_.Family -eq '26.1') { $label += ' (26.1.x runtime)' }
            $label
        })
        $choice = Select-WesoOption 'Minecraft version' $labels
        if ($choice -lt 0) { return }
        $Version = $available[$choice].Family
    }
    $target = $available | Where-Object Family -eq $Version
    $run = Join-Path $root "build/manual-test/$($target.Name)"
    $launchArguments = @(Get-WesoLaunchArguments $target)
    if ($Offline) { $launchArguments += '--offline' }
    $artifact = Join-Path $root "$($target.Packager)/build/libs/worldedit_selection_overlay-$($target.ModVersion)-$Loader-1.21.11-26.2.jar"
    Write-Host "`nTarget: $($target.Name), Minecraft $($target.Minecraft), loader $($target.LoaderVersion)"
    Write-Host "Game directory: $run"
    Write-Host "Release JAR: $artifact"
    Write-Host "Build modules: $($target.BuildModules -join ', ')"
    if ($PlanOnly) {
        Write-Host "Launch: $($launchArguments -join ' ') runManualTestClient"
        return
    }
    $jdks = @{ 21 = Find-WesoJdk 21 $Java21; 25 = Find-WesoJdk 25 $Java25 }
    Write-Host "Java 21: $($jdks[21])"
    Write-Host "Java 25: $($jdks[25])"
    $logs = Join-Path $root 'build/manual-test/logs'
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
    $log = Join-Path $logs "$($target.Name)-$(Get-Date -Format 'yyyyMMdd-HHmmss-fff').log"
    $lock = $null
    try {
        try {
            $lock = [IO.File]::Open((Join-Path $logs 'launcher.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
        } catch { throw 'Another manual test is already running. Close it before starting a new one.' }
        foreach ($module in $target.BuildModules) {
            $directory = Join-Path $root $module
            $properties = Read-WesoProperties (Join-Path $directory 'gradle.properties')
            $major = if ($module -eq $target.Packager -or $properties.minecraft_version -eq '1.21.11') { 21 } else { 25 }
            $buildArguments = @('build')
            if ($Offline) { $buildArguments += '--offline' }
            Write-Host "`nBuilding $module"
            Invoke-WesoGradle $directory $jdks[$major] $buildArguments $log
        }
        if (!(Test-Path -LiteralPath $artifact)) { throw "Release artifact missing: $artifact" }
        Write-Host "`nBuilt JAR SHA256: $((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash)"
        $launchModule = Join-Path $root $target.Module
        if ($PrepareOnly) {
            Invoke-WesoGradle $launchModule $jdks[$target.Java] ($launchArguments + @('prepareManualTest')) $log
            Invoke-WesoGradle $launchModule $jdks[$target.Java] ($launchArguments + @('runManualTestClient', '--dry-run')) $log
            Write-Host "`nPrepared successfully. No game was launched. Log: $log"
            return
        }
        New-Item -ItemType Directory -Force -Path $run | Out-Null
        $options = Join-Path $run 'options.txt'
        if (!(Test-Path -LiteralPath $options)) {
            [IO.File]::WriteAllLines($options, @('maxFps:100', 'guiScale:2', 'renderDistance:8', 'simulationDistance:8'))
        }
        Write-Host "`nStarting Minecraft. Create a disposable Creative world with cheats enabled."
        Write-Host 'O: settings / B: selection blocks / F8: command preview toggle.'
        Write-Host "Game logs: $(Join-Path $run 'logs/latest.log')"
        Write-Host "Launcher log: $log"
        Invoke-WesoGradle $launchModule $jdks[$target.Java] ($launchArguments + @('runManualTestClient')) $log
    } finally {
        if ($lock) { $lock.Dispose() }
    }
}

if ($MyInvocation.InvocationName -ne '.') {
    try { Start-WesoManualTest } catch { Write-Error $_; exit 1 }
}
