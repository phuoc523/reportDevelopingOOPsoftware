[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '..\..')
$composeFile = Join-Path $repositoryRoot 'docker-compose.yml'

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Không tìm thấy docker-compose.yml tại $repositoryRoot"
}

$seedTargets = @(
    @{ Service = 'identity-db'; File = 'identity.sql' },
    @{ Service = 'catalog-db'; File = 'catalog.sql' },
    @{ Service = 'machine-db'; File = 'machine.sql' },
    @{ Service = 'operation-db'; File = 'operation.sql' },
    @{ Service = 'order-db'; File = 'order.sql' },
    @{ Service = 'reporting-db'; File = 'reporting.sql' }
)

Push-Location $repositoryRoot
$originalOutputEncoding = $OutputEncoding
try {
    # Windows PowerShell 5 defaults native-process stdin to ASCII. Force UTF-8 so
    # Vietnamese seed values are not replaced by question marks in MySQL.
    $OutputEncoding = [System.Text.UTF8Encoding]::new($false)
    foreach ($target in $seedTargets) {
        $service = $target.Service
        $seedFile = Join-Path $PSScriptRoot $target.File
        $containerId = (docker compose ps -q $service).Trim()
        if (-not $containerId) {
            throw "Database service '$service' chưa chạy. Hãy chạy docker compose up -d trước."
        }

        $environment = docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' $containerId
        $values = @{}
        foreach ($line in $environment) {
            $separator = $line.IndexOf('=')
            if ($separator -gt 0) {
                $values[$line.Substring(0, $separator)] = $line.Substring($separator + 1)
            }
        }

        $database = $values['MYSQL_DATABASE']
        $username = $values['MYSQL_USER']
        $password = $values['MYSQL_PASSWORD']
        if (-not $database -or -not $username -or -not $password) {
            throw "Thiếu MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD của '$service'."
        }

        Write-Host "Đang nạp $($target.File) vào $service/$database..."
        Get-Content -LiteralPath $seedFile -Raw -Encoding UTF8 |
            docker compose exec -T -e "MYSQL_PWD=$password" $service mysql `
                --default-character-set=utf8mb4 -u $username $database
        if ($LASTEXITCODE -ne 0) {
            throw "Nạp dữ liệu thất bại tại $service."
        }
    }

    Write-Host 'Đã nạp xong dữ liệu demo ScentStation.' -ForegroundColor Green
    Write-Host 'Mật khẩu chung cho các tài khoản demo mới: Demo@2026!'
}
finally {
    $OutputEncoding = $originalOutputEncoding
    Pop-Location
}
