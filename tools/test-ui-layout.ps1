param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [switch]$Performance,
    [switch]$DesktopWorker
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
if (!$DesktopWorker) {
    & (Join-Path $PSScriptRoot 'test-desktop/probe-cursor.ps1') -Java21 $Java21
    Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Java21=$Java21; Java25=$Java25; Performance=[bool]$Performance; DesktopWorker=$true}
    return
}
Assert-WesoTestDesktop
$logDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'build/ui-validation'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$log = Join-Path $logDirectory 'layout.log'
$previousJava = $env:JAVA_HOME
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'WEaddon_fabric_26.2')
try {
    $env:JAVA_HOME = $Java25
    $arguments = @('-I', '../tools/ui-test.init.gradle', 'testUniversalUi', '--offline', '--no-daemon', '--max-workers=2', '--console=plain')
    if (!$Performance) { $arguments += '-Pui_fast=true' }
    & .\gradlew.bat @arguments 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw 'Background UI layout test failed' }
    $result = [IO.File]::ReadAllText($log)
    foreach ($marker in @('CURSOR ISOLATION GUARD PASS:', 'WESO UI PASS:')) {
        if (!$result.Contains($marker)) { throw "Incomplete background UI layout test: missing '$marker'" }
    }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
}
