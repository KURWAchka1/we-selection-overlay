$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
$checks = 0

function Assert-InvalidBinding([scriptblock]$Binding, [hashtable]$Arguments) {
    try { & $Binding @Arguments | Out-Null }
    catch [System.Management.Automation.ParameterBindingException] { return }
    throw 'Invalid test parameters were accepted'
}

foreach ($name in @('test-command-matrix.ps1', 'test-ui-matrix.ps1', 'test-ui-layout.ps1')) {
    $tokens = $null; $errors = $null
    $ast = [System.Management.Automation.Language.Parser]::ParseFile(
            (Join-Path $PSScriptRoot $name), [ref]$tokens, [ref]$errors)
    if ($errors.Count -ne 0) { throw "Invalid script syntax: $name" }
    # Exercise the real parameter attributes without running a client or desktop probe.
    $binding = [scriptblock]::Create($ast.ParamBlock.Extent.Text + "`nreturn `$ModVersion")
    foreach ($version in @('1.8.0', '1.8.0-bridge-dev', '1.8.1+qa')) {
        $actual = & $binding -Java21 unused -Java25 unused -ModVersion $version
        if ($actual -ne $version) { throw "Version binding failed: $name" }
        $checks++
    }
    foreach ($version in @('../1.8.0', 'C:\bad', '1.8.0/test', '1.8.0;bad', '')) {
        Assert-InvalidBinding $binding @{Java21='unused'; Java25='unused'; ModVersion=$version}
        $checks++
    }
    if ($name -ne 'test-ui-layout.ps1') {
        Assert-InvalidBinding $binding @{Java21='unused'; Java25='unused'; Only='fabric-26.9'}
        $actual = & $binding -Java21 unused -Java25 unused -Only @('fabric-1.21.11', 'neoforge-26.2') -ModVersion 1.8.0-bridge-dev -DesktopWorker
        if ($actual -ne '1.8.0-bridge-dev') { throw 'Worker parameter forwarding failed' }
        $checks += 2
        if ($name -eq 'test-command-matrix.ps1') {
            Assert-InvalidBinding $binding @{Java21='unused'; Java25='unused'; NativeBridgeScenario='unknown'}
            $actual = & $binding -Java21 unused -Java25 unused -Only fabric-1.21.11 -NativeBridgeScenario allowed -NativeBridgeControl unused -ModVersion 1.8.0-bridge-dev
            if ($actual -ne '1.8.0-bridge-dev') { throw 'Native bridge scenario binding failed' }
            $checks += 2
        }
    }
}
$tokens = $null; $errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot 'test-native-paper-bridge.ps1'), [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw 'Invalid native bridge wrapper syntax' }
$binding = [scriptblock]::Create($ast.ParamBlock.Extent.Text + "`nreturn `$ModVersion")
foreach ($version in @('1.8.0', '1.8.0-bridge-dev', '1.8.1+qa')) {
    if ((& $binding -Java21 unused -ModVersion $version -AcceptEula -DesktopWorker) -ne $version) { throw 'Native bridge binding failed' }
    $checks++
}
foreach ($version in @('../1.8.0', 'C:\bad', '1.8.0/test', '1.8.0;bad', '')) {
    Assert-InvalidBinding $binding @{Java21='unused'; ModVersion=$version}
    $checks++
}
$tokens = $null; $errors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot 'check-command-pixels.ps1'), [ref]$tokens, [ref]$errors)
if ($errors.Count -ne 0) { throw 'Invalid pixel-checker syntax' }
$binding = [scriptblock]::Create($ast.ParamBlock.Extent.Text + "`nreturn `$Only")
$actual = @(& $binding -Only @('fabric-1.21.11', 'neoforge-26.2') -NotBefore ([datetime]::UtcNow))
if ($actual.Count -ne 2) { throw 'Pixel target binding failed' }
Assert-InvalidBinding $binding @{Only='fabric-26.9'}
$checks += 2
foreach ($loader in @('fabric', 'neoforge')) {
    try { Assert-WesoTestArtifact -Loader $loader -ModVersion 'missing-harness-fixture' }
    catch {
        if (!$_.Exception.Message.Contains('no release fallback is allowed')) { throw }
        $checks++
        continue
    }
    throw 'Missing artifact unexpectedly fell back to a release'
}
Write-Host "HARNESS INPUTS PASS: $checks checks; no game or cursor probe launched."
