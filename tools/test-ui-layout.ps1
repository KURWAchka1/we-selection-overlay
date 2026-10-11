param(
    [Parameter(Mandatory = $true)][string]$Java21,
    [Parameter(Mandatory = $true)][string]$Java25,
    [switch]$Performance,
    [switch]$Showcase,
    [switch]$Beauty,
    [switch]$LocationPreview,
    [switch]$CreditedLocation,
    [switch]$Closing,
    [switch]$EditingPickups,
    [switch]$DesktopWorker
)
$ErrorActionPreference = 'Stop'
if ($Beauty -and !$Showcase) { throw '-Beauty requires -Showcase' }
if ($LocationPreview -and !$Showcase) { throw '-LocationPreview requires -Showcase' }
if ($CreditedLocation -and !$Showcase) { throw '-CreditedLocation requires -Showcase' }
if ($Closing -and !($Showcase -and $Beauty -and $CreditedLocation)) { throw '-Closing requires -Showcase -Beauty -CreditedLocation' }
if ($EditingPickups -and (!($Showcase -and $CreditedLocation) -or $Beauty -or $Closing)) { throw '-EditingPickups requires a separate -Showcase -CreditedLocation pass' }
if ($LocationPreview -and $CreditedLocation) { throw 'Choose scout or full capture, not both' }
. (Join-Path $PSScriptRoot 'test-desktop.ps1')
if (!$DesktopWorker) {
    & (Join-Path $PSScriptRoot 'test-desktop/probe-cursor.ps1') -Java21 $Java21
    Invoke-WesoDesktopTest -Script $PSCommandPath -Parameters @{Java21=$Java21; Java25=$Java25; Performance=[bool]$Performance; Showcase=[bool]$Showcase; Beauty=[bool]$Beauty; LocationPreview=[bool]$LocationPreview; CreditedLocation=[bool]$CreditedLocation; Closing=[bool]$Closing; EditingPickups=[bool]$EditingPickups; DesktopWorker=$true}
    return
}
Assert-WesoTestDesktop
$logDirectory = Join-Path (Split-Path -Parent $PSScriptRoot) 'build/ui-validation'
New-Item -ItemType Directory -Force -Path $logDirectory | Out-Null
$log = Join-Path $logDirectory $(if ($Showcase) { 'showcase.log' } else { 'layout.log' })
$previousJava = $env:JAVA_HOME
$previousBeauty = $env:WESO_VIDEO_BEAUTY
$previousLocation = $env:WESO_VIDEO_LOCATION
$previousClosing = $env:WESO_VIDEO_CLOSING
$previousEditing = $env:WESO_VIDEO_EDITING_PICKUPS
Push-Location (Join-Path (Split-Path -Parent $PSScriptRoot) 'WEaddon_fabric_26.2')
try {
    $env:JAVA_HOME = $Java25
    $env:WESO_VIDEO_BEAUTY = [string][int][bool]$Beauty
    $env:WESO_VIDEO_CLOSING = [string][int][bool]$Closing
    $env:WESO_VIDEO_EDITING_PICKUPS = [string][int][bool]$EditingPickups
    $env:WESO_VIDEO_LOCATION = if ($CreditedLocation) { 'full' } elseif ($LocationPreview) { 'scout' } else { 'none' }
    $arguments = if ($Showcase) {
        @('-I', '../tools/video/showcase.init.gradle', 'testVideoShowcase', '--offline', '--no-daemon', '--max-workers=2', '--console=plain')
    } else {
        @('-I', '../tools/ui-test.init.gradle', 'testUniversalUi', '--offline', '--no-daemon', '--max-workers=2', '--console=plain')
    }
    if (!$Performance) { $arguments += '-Pui_fast=true' }
    & .\gradlew.bat @arguments 2>&1 | Tee-Object -FilePath $log
    if ($LASTEXITCODE -ne 0) { throw 'Background UI layout test failed' }
    $result = [IO.File]::ReadAllText($log)
    $successMarker = if ($Showcase) { 'WESO VIDEO CAPTURE PASS:' } else { 'WESO UI PASS:' }
    foreach ($marker in @('CURSOR ISOLATION GUARD PASS:', $successMarker)) {
        if (!$result.Contains($marker)) { throw "Incomplete background UI layout test: missing '$marker'" }
    }
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJava
    $env:WESO_VIDEO_BEAUTY = $previousBeauty
    $env:WESO_VIDEO_LOCATION = $previousLocation
    $env:WESO_VIDEO_CLOSING = $previousClosing
    $env:WESO_VIDEO_EDITING_PICKUPS = $previousEditing
}
