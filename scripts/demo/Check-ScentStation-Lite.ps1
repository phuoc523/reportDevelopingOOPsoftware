$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Lite-Compose.ps1')
Initialize-Lite
$containers = Invoke-DemoCompose ps --all --format json | ForEach-Object { $_ | ConvertFrom-Json }
$failed = @()
foreach ($name in $LiteServices) {
    $service = $containers | Where-Object Service -eq $name
    if ($service.State -ne 'running' -or $service.Health -ne 'healthy') {
        $failed += $name
        Write-Host "FAIL $name"
    } else { Write-Host "PASS $name" }
}
foreach ($port in $LitePorts) {
    try {
        $result = Invoke-WebRequest -Uri "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 5
        Write-Host "PASS frontend $port HTTP $($result.StatusCode)"
    } catch { $failed += "frontend:$port"; Write-Host "FAIL frontend $port" }
}
Write-Host "Windows RAM available: $(Get-LiteFreeMemory) GiB"
$ids = @($containers | Where-Object State -eq running | ForEach-Object ID)
if ($ids.Count) { docker @DemoDocker stats --no-stream --format '{{.Name}} {{.MemUsage}}' @ids }
if ($failed.Count) { throw "LITE unhealthy: $($failed -join ', ')" }
