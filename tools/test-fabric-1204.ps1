param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java17,
    [string]$RunId,
    [switch]$Worker
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
$root = Split-Path -Parent $PSScriptRoot
$wrapper = Join-Path $root 'WEaddon_fabric_1.21.11/gradlew.bat'
$module = Join-Path $root 'WEaddon_fabric_1.20.4'
$agent = Join-Path $PSScriptRoot 'test-desktop/agent'
$env:JAVA_HOME = $Java21
if (!(Test-Path -LiteralPath (Join-Path $Java17 'bin/java.exe'))) { throw 'A Java 17 JDK is required.' }
if (!$Worker) {
    & $wrapper -p $agent build --max-workers=2 --console=plain
    if ($LASTEXITCODE) { throw 'Cursor isolation agent build failed.' }
    $RunId = [Guid]::NewGuid().ToString('N')
    [WesoTestDesktop+Rect]$before = New-Object WesoTestDesktop+Rect
    if (![WesoTestDesktop]::GetClipCursor([ref]$before)) { throw 'Cannot inspect cursor confinement.' }
    try {
        Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Java21=$Java21; Java17=$Java17; RunId=$RunId; Worker=$true} -TimeoutSeconds 1100
    } finally {
        [WesoTestDesktop+Rect]$after = New-Object WesoTestDesktop+Rect
        if (![WesoTestDesktop]::GetClipCursor([ref]$after)) { throw 'Cannot inspect cursor confinement after test.' }
        foreach ($edge in @('left', 'top', 'right', 'bottom')) {
            if ($before.$edge -ne $after.$edge) { throw 'Test changed cursor confinement.' }
        }
    }
    return
}
Assert-WesoTestDesktop
if ($RunId -notmatch '^[a-f0-9]{32}$') { throw 'Invalid run ID.' }
& $wrapper -p $agent verifyNativeGuard '-Pprobe_lwjgl_version=3.3.2' --offline --max-workers=2 --console=plain
if ($LASTEXITCODE) { throw 'LWJGL 3.3.2 cursor preflight failed.' }
$output = Join-Path $root "build/fabric-1204-test/$RunId"
New-Item -ItemType Directory -Path $output | Out-Null
$init = Join-Path $PSScriptRoot 'fabric-1204-test/init.gradle'
$artifact = Join-Path $module 'build/libs/worldedit_selection_overlay-1.8.1-fabric-1.20.4.jar'
$expectedHash = $null
foreach ($profile in @('withoutWorldEdit', 'withWorldEdit')) {
    Assert-WesoTestDesktop
    $log = Join-Path $output "$profile.log"
    & $wrapper -p $module -I $init verifyLegacyJar testFabric1204 "-Porg.gradle.java.installations.paths=$Java17" "-Pweso1204Java=$Java17" "-Pweso1204Run=$output" "-Pweso1204Profile=$profile" --no-daemon --max-workers=2 --console=plain *> $log
    if ($LASTEXITCODE) {
        Get-Content -LiteralPath $log -Tail 45
        throw "Minecraft 1.20.4 failed: $log"
    }
    foreach ($marker in @('CURSOR ISOLATION GUARD PASS:', "WESO FABRIC 1.20.4 PASS: $profile")) {
        if (!(Select-String -LiteralPath $log -SimpleMatch $marker -Quiet)) { throw "Missing proof '$marker': $log" }
    }
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
    if ($expectedHash -and $hash -ne $expectedHash) { throw 'Profiles tested different client JARs.' }
    $expectedHash = $hash
    Write-Host "TEST ARTIFACT: $artifact SHA256=$hash"
}
Write-Host "Fabric 1.20.4 results: $output"
