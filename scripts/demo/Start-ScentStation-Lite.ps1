[CmdletBinding()]
param([switch]$Build)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Lite-Compose.ps1')
$available = Get-LiteFreeMemory
if ($available -lt 4.5) { throw "LITE requires at least 4.5 GiB available before startup. Currently $available GiB." }
& (Join-Path $PSScriptRoot 'Prepare-LocalEnv.ps1') -Lite
Initialize-Lite
$engineBytes = docker @DemoDocker info --format '{{.MemTotal}}'
if ($LASTEXITCODE -ne 0 -or [long]$engineBytes -lt 4GB) { throw 'Docker Linux Engine requires at least 4 GiB for this profile.' }
foreach ($port in $LitePorts) {
    $listener = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() |
        Where-Object Port -eq $port
    if ($listener -and -not (Invoke-DemoCompose ps --status running -q lite-web)) { throw "Port $port is in use." }
}
if ($Build) {
    if ((Get-LiteFreeMemory) -lt 6) { throw 'Build requires 6 GiB available. Build sequentially with the LITE stack stopped.' }
    if (Invoke-DemoCompose ps --status running -q) { throw 'Stop LITE before rebuilding.' }
    Invoke-DemoCompose build identity-service catalog-service order-service machine-service api-gateway lite-web
}
Invoke-DemoCompose up -d --no-build --wait --wait-timeout 240 lite-web
& (Join-Path $PSScriptRoot 'Check-ScentStation-Lite.ps1')
Write-Host 'Kiosk http://127.0.0.1:17515 | Admin http://127.0.0.1:17514 | Mock http://127.0.0.1:17516/mock-payment/<reference>'
