. (Join-Path $PSScriptRoot 'Demo-Compose.ps1')
$script:DemoEnvFile = Join-Path $script:DemoRoot '.env.demo-lite'
$script:LitePorts = @(17514,17515,17516)
$script:LiteServices = @('redis','identity-db','catalog-db','order-db','machine-db','identity-service','catalog-service','order-service','machine-service','api-gateway','lite-web')
function Initialize-Lite {
    Initialize-DemoCompose
    $script:DemoCompose += @('-f',(Join-Path $PSScriptRoot 'compose.lite.yml'),'--profile','demo-lite')
}
function Get-LiteFreeMemory {
    [math]::Round((Get-CimInstance Win32_OperatingSystem -ErrorAction Stop).FreePhysicalMemory / 1MB, 2)
}
