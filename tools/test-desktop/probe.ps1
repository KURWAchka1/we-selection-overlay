param([switch]$Worker)
$ErrorActionPreference = 'Stop'
. (Join-Path (Split-Path -Parent $PSScriptRoot) 'test-desktop.ps1')
if ($Worker) {
    Assert-WesoTestDesktop
    [WesoTestDesktop]::Probe($env:WESO_TEST_DESKTOP)
    Write-Host 'DESKTOP ISOLATION PROBE PASS: native window created on the private, non-input desktop'
    return
}
[WesoTestDesktop+Rect]$before = New-Object WesoTestDesktop+Rect
if (![WesoTestDesktop]::GetClipCursor([ref]$before)) { throw 'Cannot inspect cursor confinement' }
Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Worker=$true} -TimeoutSeconds 60
[WesoTestDesktop+Rect]$after = New-Object WesoTestDesktop+Rect
if (![WesoTestDesktop]::GetClipCursor([ref]$after)) { throw 'Cannot inspect cursor confinement' }
foreach ($edge in @('left', 'top', 'right', 'bottom')) {
    if ($before.$edge -ne $after.$edge) { throw 'Cursor confinement changed during the isolation probe' }
}
Write-Host 'DESKTOP ISOLATION PROBE PASS: no test windows on the input desktop; cursor confinement unchanged'
