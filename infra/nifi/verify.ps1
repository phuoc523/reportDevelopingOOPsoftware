[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$nifiDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $nifiDirectory ".env"
$composeFile = Join-Path $nifiDirectory "docker-compose.yml"

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Thiếu infra/nifi/.env."
}

$containerId = docker compose --env-file $envFile -f $composeFile ps -q nifi
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($containerId)) {
    throw "Container NiFi chưa chạy."
}

docker compose --env-file $envFile -f $composeFile exec -T nifi `
    test -r /opt/nifi/nifi-current/lib/mysql-connector-j.jar
if ($LASTEXITCODE -ne 0) {
    throw "Không tìm thấy MySQL Connector/J trong NiFi."
}

$networkNames = docker inspect $containerId --format '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}} {{end}}'
if ($networkNames -notmatch 'scentstation-backend') {
    throw "NiFi chưa tham gia network scentstation-backend."
}

Write-Host "NiFi đang chạy, JDBC driver đã sẵn sàng và network backend đã được kết nối."
Write-Host "Mở https://localhost:8443 và đăng nhập bằng infra/nifi/.env."
