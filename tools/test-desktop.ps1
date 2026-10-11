$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'The isolated test desktop requires Windows. No visible fallback is allowed.' }
if (!('WesoTestDesktop' -as [type])) { Add-Type -Path (Join-Path $PSScriptRoot 'test-desktop/WesoTestDesktop.cs') }

function Invoke-WesoDesktopTest {
    param(
        [Parameter(Mandatory = $true)][string]$Script,
        [hashtable]$Parameters = @{},
        [ValidateRange(1, 7200)][int]$TimeoutSeconds = 2400
    )
    $root = Split-Path -Parent $PSScriptRoot
    $name = 'WesoTest-' + [Guid]::NewGuid().ToString('N')
    $directory = Join-Path $root 'build/desktop-tests'
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $log = Join-Path $directory "$name.log"
    $payload = @{
        Script = (Resolve-Path -LiteralPath $Script).Path
        Parameters = $Parameters
        Helper = $PSCommandPath
        Desktop = $name
        Log = $log
    } | ConvertTo-Json -Depth 6 -Compress
    $data = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($payload))
    $worker = @'
$ErrorActionPreference = 'Stop'
$payload = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__DATA__')) | ConvertFrom-Json
try {
    . $payload.Helper
    [WesoTestDesktop]::AssertIsolated($payload.Desktop)
    $env:WESO_TEST_DESKTOP = $payload.Desktop
    $parameters = @{}
    foreach ($entry in $payload.Parameters.psobject.Properties) { $parameters[$entry.Name] = $entry.Value }
    & $payload.Script @parameters *> $payload.Log
    if (!$?) { exit 1 }
    exit 0
} catch {
    $_ | Out-String | Add-Content -LiteralPath $payload.Log
    exit 1
}
'@
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($worker.Replace('__DATA__', $data)))
    $executable = (Get-Process -Id $PID).Path
    $session = $null
    Write-Host "Running tests on private desktop $name. Your desktop will not be switched."
    Write-Host "Background log: $log"
    try {
        $session = [WesoTestDesktop]::Start($name, $executable, "-NoProfile -NonInteractive -WindowStyle Hidden -EncodedCommand $encoded", $root)
        $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
        while (!$session.Poll()) {
            if ([DateTime]::UtcNow -gt $deadline) { throw "Background test timeout. See $log" }
            Start-Sleep -Milliseconds 500
        }
        if ($session.ExitCode() -ne 0) {
            Get-Content -LiteralPath $log -Tail 35 | Write-Host
            throw "Background test failed. See $log"
        }
        Write-Host "Private desktop test passed. Log: $log"
    } finally {
        if ($null -ne $session) { $session.Dispose() }
    }
}

function Assert-WesoTestDesktop {
    [WesoTestDesktop]::AssertIsolated($env:WESO_TEST_DESKTOP)
}

function Assert-WesoTestArtifact {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('fabric', 'neoforge')][string]$Loader,
        [Parameter(Mandatory = $true)][ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$')][string]$ModVersion
    )
    $root = Split-Path -Parent $PSScriptRoot
    $artifact = Join-Path $root "WEaddon_${Loader}_universal_1.21.11-26.2/build/libs/worldedit_selection_overlay-$ModVersion-$Loader-1.21.11-26.2.jar"
    if (!(Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "Missing requested $Loader test artifact: $artifact. Build this version first; no release fallback is allowed."
    }
    $hash = (Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash
    Write-Host "TEST ARTIFACT: $artifact SHA256=$hash"
}
