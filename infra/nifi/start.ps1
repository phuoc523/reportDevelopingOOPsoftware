[CmdletBinding()]
param(
    [switch]$NoBuild
)

$ErrorActionPreference = "Stop"
$nifiDirectory = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $nifiDirectory ".env"
$composeFile = Join-Path $nifiDirectory "docker-compose.yml"

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Thiếu infra/nifi/.env. Hãy sao chép .env.example thành .env và đặt mật khẩu NiFi cục bộ."
}

docker network inspect scentstation-backend *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Network scentstation-backend chưa tồn tại. Hãy khởi động backend bằng docker compose up -d trước."
}

$arguments = @("compose", "--env-file", $envFile, "-f", $composeFile, "up", "-d")
if (-not $NoBuild) {
    $arguments += "--build"
}
docker @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Không thể khởi động NiFi."
}

& (Join-Path $nifiDirectory "verify.ps1")
