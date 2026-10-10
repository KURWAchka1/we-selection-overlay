param([Parameter(Mandatory = $true)][string]$Java21, [switch]$Worker)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'test-desktop.ps1')
if (!$Worker) {
    & (Join-Path $PSScriptRoot 'build-cursor-guard.ps1') -Java21 $Java21
    [WesoTestDesktop+Rect]$before = New-Object WesoTestDesktop+Rect
    if (![WesoTestDesktop]::GetClipCursor([ref]$before)) { throw 'Cannot inspect cursor confinement' }
    Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Java21=$Java21; Worker=$true} -TimeoutSeconds 180
    [WesoTestDesktop+Rect]$after = New-Object WesoTestDesktop+Rect
    if (![WesoTestDesktop]::GetClipCursor([ref]$after)) { throw 'Cannot inspect cursor confinement' }
    foreach ($edge in @('left', 'top', 'right', 'bottom')) {
        if ($before.$edge -ne $after.$edge) { throw 'Native cursor probe changed system cursor confinement' }
    }
    Write-Host 'CURSOR DESKTOP PREFLIGHT PASS: both native LWJGL versions tested; system cursor confinement unchanged'
    return
}
Assert-WesoTestDesktop
$env:JAVA_HOME = $Java21
foreach ($version in @('3.3.3', '3.4.1')) {
    & (Join-Path $root 'WEaddon_fabric_universal_1.21.11-26.2/gradlew.bat') -p (Join-Path $PSScriptRoot 'agent') verifyNativeGuard "-Pprobe_lwjgl_version=$version" --offline --no-daemon --max-workers=2 --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Native cursor probe failed: LWJGL $version" }
}
