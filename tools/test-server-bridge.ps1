param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [switch]$SkipNativeCodecs,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
foreach ($java in @($Java21, $Java25)) {
    if (!(Test-Path -LiteralPath (Join-Path $java 'bin/java.exe'))) { throw "Missing Java: $java" }
}
$logs = Join-Path $root 'build/bridge-validation'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$common = @('--no-daemon', '--max-workers=2', '--console=plain')
if ($Offline) { $common += '--offline' }
$previousJava = $env:JAVA_HOME
$previousTestJava = $env:WESO_TEST_JAVA

function Invoke-BridgeGradle([string]$Module, [string]$Java, [string]$Name, [string[]]$Tasks) {
    $env:JAVA_HOME = $Java
    Push-Location (Join-Path $root $Module)
    try {
        $wrapper = Join-Path $root "$Module/gradlew.bat"
        if (!(Test-Path -LiteralPath $wrapper)) { $wrapper = Join-Path $root 'WEaddon_fabric_1.21.11/gradlew.bat' }
        & $wrapper @Tasks @common 2>&1 |
                Tee-Object -FilePath (Join-Path $logs "$Name.log")
        if ($LASTEXITCODE -ne 0) { throw "Bridge verification failed: $Name" }
        if ($Name -like 'codec-*' -and !(Select-String -LiteralPath (Join-Path $logs "$Name.log") -SimpleMatch 'NATIVE BRIDGE PAYLOAD PASS:' -Quiet)) {
            throw "Native codec marker missing: $Name"
        }
    } finally { Pop-Location }
}

function Assert-PaperArtifact {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $file = Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar'
    if ((Get-Item -LiteralPath $file).Length -gt 1MB) { throw 'Unexpected companion footprint' }
    $zip = [IO.Compression.ZipFile]::OpenRead($file)
    try {
        foreach ($name in @('LICENSE', 'plugin.yml', 'config.yml',
                'org/marshal/weaddon_lightposition/bridge/server/BridgeServer.class',
                'org/marshal/weaddon_lightposition/bridge/protocol/BridgeCodec.class',
                'org/marshal/weaddon_lightposition/bridge/paper/WorldEditOverlayBridgePlugin.class')) {
            if ($null -eq $zip.GetEntry($name)) { throw "Companion entry missing: $name" }
        }
        foreach ($entry in $zip.Entries) {
            if ($entry.FullName -match '^(net/|com/sk89q/|org/bukkit/|org/marshal/wesotest/)|/bridge/(client|fixture)/|Test\.class$') {
                throw "Companion contains client, test or provided dependency: $($entry.FullName)"
            }
            if ($entry.FullName.EndsWith('.class')) {
                $stream = $entry.Open()
                try {
                    $header = New-Object byte[] 8
                    for ($i = 0; $i -lt 8; $i++) {
                        $value = $stream.ReadByte()
                        if ($value -lt 0) { throw 'Truncated class header' }
                        $header[$i] = $value
                    }
                    if ([BitConverter]::ToString([byte[]]$header[0..3]).Replace('-', '') -ne 'CAFEBABE' -or (($header[6] -shl 8) + $header[7]) -gt 65) {
                        throw "Companion bytecode is not Java 21: $($entry.FullName)"
                    }
                } finally { $stream.Dispose() }
            }
        }
    } finally { $zip.Dispose() }
    Write-Output 'PAPER ARTIFACT PASS: Java 21, license present, no client/fixture/provided dependencies.'
}

try {
    $nodeTests = @('owned-server.test.mjs', 'frozen-fixture-world.test.mjs', 'wire-codec.test.mjs', 'load-window.test.mjs', 'load-evidence.test.mjs', 'gameplay-workload.test.mjs', 'active-lifecycle.test.mjs', 'fault-relay.test.mjs', 'edit-load-window.test.mjs', 'gesture-evidence.test.mjs', 'gesture-tuples.test.mjs', 'native-lifecycle-evidence.test.mjs', 'native-fault-evidence.test.mjs', 'java-load-clients.test.mjs') |
            ForEach-Object { Join-Path $root "tools/server-bridge-test/$_" }
    & node --test @nodeTests 2>&1 | Tee-Object -FilePath (Join-Path $logs 'harness-node.log')
    if ($LASTEXITCODE -ne 0) { throw 'Bridge harness self-tests failed' }
    Invoke-BridgeGradle 'server-bridge' $Java21 'unit-java21' @('check', 'assemble', '--rerun-tasks', '-PtestJavaVersion=21')
    Assert-PaperArtifact
    Invoke-BridgeGradle 'server-bridge' $Java25 'unit-java25' @('check', '--rerun-tasks', '-PtestJavaVersion=25')
    foreach ($target in @(@{Java=$Java21; Name='java21'}, @{Java=$Java25; Name='java25'})) {
        Invoke-BridgeGradle 'tools/bridge-slice-agent' $target.Java "slice-agent-$($target.Name)" @('check', '--rerun-tasks', "-PtestJavaVersion=$($target.Name.Replace('java',''))")
        $env:WESO_TEST_JAVA = Join-Path $target.Java 'bin/java.exe'
        & node --test (Join-Path $root 'tools/server-bridge-test/jfr-bridge-slices.test.mjs') 2>&1 |
            Tee-Object -FilePath (Join-Path $logs "harness-slices-$($target.Name).log")
        if ($LASTEXITCODE -ne 0) { throw "JFR slice reader self-tests failed: $($target.Name)" }
        & node --test (Join-Path $root 'tools/server-bridge-test/jfr-edit-slices.test.mjs') 2>&1 |
            Tee-Object -FilePath (Join-Path $logs "harness-edit-slices-$($target.Name).log")
        if ($LASTEXITCODE -ne 0) { throw "JFR edit phase reader self-tests failed: $($target.Name)" }
        & node --test (Join-Path $root 'tools/server-bridge-test/jfr-load-summary.test.mjs') 2>&1 |
            Tee-Object -FilePath (Join-Path $logs "harness-summary-$($target.Name).log")
        if ($LASTEXITCODE -ne 0) { throw "JFR load summary self-tests failed: $($target.Name)" }
        & node --test (Join-Path $root 'tools/server-bridge-test/jfr-tick-timeline.test.mjs') 2>&1 |
            Tee-Object -FilePath (Join-Path $logs "harness-jfr-$($target.Name).log")
        if ($LASTEXITCODE -ne 0) { throw "JFR timeline self-tests failed: $($target.Name)" }
        & $env:WESO_TEST_JAVA -Xmx256m -XX:ActiveProcessorCount=2 --class-path (Join-Path $root 'server-bridge/paper/build/libs/worldedit_selection_overlay-bridge-paper-0.1.0-dev.jar') (Join-Path $root 'tools/server-bridge-test/AdmissionOrderingProbe.java') |
            Tee-Object -FilePath (Join-Path $logs "admission-ordering-$($target.Name).ndjson")
        if ($LASTEXITCODE -ne 0) { throw "Admission ordering experiment failed to execute: $($target.Name)" }
    }
    if (!$SkipNativeCodecs) {
        foreach ($target in @(
            @{Name='fabric-1.21.11'; Module='WEaddon_fabric_1.21.11'; Java=$Java21},
            @{Name='fabric-26.1'; Module='WEaddon_lightposition'; Java=$Java25},
            @{Name='fabric-26.2'; Module='WEaddon_fabric_26.2'; Java=$Java25},
            @{Name='neoforge-1.21.11'; Module='WEaddon_neoforge_1.21.11'; Java=$Java21},
            @{Name='neoforge-26.1'; Module='WEaddon_neoforge_26.1'; Java=$Java25},
            @{Name='neoforge-26.2'; Module='WEaddon_neoforge_26.2'; Java=$Java25}
        )) {
            Invoke-BridgeGradle $target.Module $target.Java "codec-$($target.Name)" @('-I', '../tools/bridge-payload-test.init.gradle', 'verifyBridgePayload')
        }
    }
} finally { $env:JAVA_HOME = $previousJava; $env:WESO_TEST_JAVA = $previousTestJava }
Write-Output 'Bridge checks passed. This does not prove dedicated-server joins or gameplay compatibility.'
