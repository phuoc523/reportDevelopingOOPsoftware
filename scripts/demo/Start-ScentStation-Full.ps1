[CmdletBinding()]
param([switch] $Build)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Demo-Compose.ps1')
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'BLOCKED: Docker Desktop CLI unavailable.'
}
$os = Get-CimInstance Win32_OperatingSystem
$disk = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($DemoRoot))
if ($os.FreePhysicalMemory -lt 8MB -or $disk.AvailableFreeSpace -lt 20GB) {
    throw 'BLOCKED: Demo startup requires at least 8 GiB available RAM and 20 GiB free on the workspace drive.'
}
& (Join-Path $PSScriptRoot 'Prepare-LocalEnv.ps1')
Initialize-DemoCompose
Invoke-DemoCompose config --quiet

$ports = [ordered]@{'api-gateway'=8080; 'admin-web'=7514; 'kiosk-web'=7515; 'mock-payment-web'=3000}
$listeners = @(Get-NetTCPConnection -State Listen)
foreach ($service in $ports.Keys) {
    if ($ports[$service] -in $listeners.LocalPort) {
        $running = Invoke-DemoCompose ps --status running --quiet $service
        if (-not $running) { throw "BLOCKED: Port $($ports[$service]) is occupied outside this demo project." }
    }
}

Invoke-DemoCompose up -d --wait --wait-timeout 240 redis kafka identity-db catalog-db order-db machine-db operation-db reporting-db notification-db
$up = @('up','-d','--wait','--wait-timeout','360')
if ($Build) { $up += '--build' }
Invoke-DemoCompose @up api-gateway admin-web kiosk-web mock-payment-web
& (Join-Path $PSScriptRoot 'Check-ScentStation-Full.ps1')
