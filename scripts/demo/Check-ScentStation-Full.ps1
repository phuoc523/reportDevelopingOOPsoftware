[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Demo-Compose.ps1')
Initialize-DemoCompose
$containers = @((Invoke-DemoCompose ps --all --format json) | ForEach-Object { $_ | ConvertFrom-Json })
$urls = @{
    'api-gateway'='http://127.0.0.1:8080/actuator/health'
    'admin-web'='http://127.0.0.1:7514/'
    'kiosk-web'='http://127.0.0.1:7515/'
    'mock-payment-web'='http://127.0.0.1:3000/'
}
$services = 'redis','kafka','kafka-init','identity-db','catalog-db','order-db','machine-db',
    'operation-db','reporting-db','notification-db','identity-service','catalog-service',
    'order-service','machine-service','operation-service','reporting-service','notification-service',
    'api-gateway','admin-web','kiosk-web','mock-payment-web'
$rows = foreach ($service in $services) {
    $container = $containers | Where-Object Service -eq $service | Select-Object -First 1
    $url = $urls[$service]
    $http = '-'
    if ($url) {
        try { $http = [int](Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5).StatusCode }
        catch {
            $http = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 'unreachable' }
        }
    }
    $pass = if ($service -eq 'kafka-init') {
        $container.State -eq 'exited' -and $container.ExitCode -eq 0
    } elseif ($url -and $service -ne 'api-gateway') {
        $container.State -eq 'running' -and $http -eq 200
    } else {
        $container.Health -eq 'healthy' -and (-not $url -or $http -eq 200)
    }
    [pscustomobject]@{Service=$service; Port=$(if ($url) { ([uri]$url).Port } else { '-' });
        Container=$container.Name; Health=$container.Health; HTTP=$http; Result=$(if ($pass) {'PASS'} else {'FAIL'})}
}
$rows | Format-Table -AutoSize | Out-Host
foreach ($service in $urls.Keys | Sort-Object) { Write-Host "$service $($urls[$service])" }
if ($rows.Result -contains 'FAIL') { throw 'One or more demo health checks failed.' }
