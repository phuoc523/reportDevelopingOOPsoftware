$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
Set-Location $repo

$ip = Get-NetIPAddress -InterfaceAlias 'Wi-Fi' -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '169.254*' } |
    Select-Object -First 1 -ExpandProperty IPAddress
if ([string]::IsNullOrWhiteSpace($ip)) {
    $ip = Get-NetIPAddress -AddressFamily IPv4 |
        Where-Object {
            $_.IPAddress -notlike '127.*' -and
            $_.IPAddress -notlike '169.254*' -and
            $_.InterfaceAlias -notmatch 'vEthernet|VMware|VirtualBox|WSL|Loopback|Tailscale'
        } |
        Select-Object -First 1 -ExpandProperty IPAddress
}
if ([string]::IsNullOrWhiteSpace($ip)) { throw 'Không tìm thấy địa chỉ IPv4 LAN hiện tại.' }

Write-Host "IP mạng hiện tại: $ip" -ForegroundColor Cyan

# 1. Cập nhật PAYMENT_MOCK_WEB_BASE_URL trong .env
if (Test-Path .env) {
    $lines = Get-Content .env
    $found = $false
    $updated = $lines | ForEach-Object {
        if ($_ -match '^PAYMENT_MOCK_WEB_BASE_URL=') {
            $found = $true
            "PAYMENT_MOCK_WEB_BASE_URL=https://${ip}:3443"
        } else {
            $_
        }
    }
    if (-not $found) { $updated += "PAYMENT_MOCK_WEB_BASE_URL=https://${ip}:3443" }
    Set-Content -Path .env -Value $updated -Encoding UTF8
    Write-Host "Đã cập nhật .env -> PAYMENT_MOCK_WEB_BASE_URL=https://${ip}:3443" -ForegroundColor Green
}

# 2. Tạo lại chứng chỉ TLS với SAN khớp IP mới
New-Item -ItemType Directory -Path 'infra\docker\tls' -Force | Out-Null
$openssl = 'C:\Program Files\Git\usr\bin\openssl.exe'
if (Test-Path $openssl) {
    $prevPref = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    & $openssl req -x509 -nodes -days 365 -newkey rsa:2048 `
        -keyout 'infra\docker\tls\mock-payment.key' `
        -out 'infra\docker\tls\mock-payment.crt' `
        -subj "/CN=$ip" `
        -addext "subjectAltName=IP:$ip,IP:127.0.0.1,DNS:localhost" *> $null
    $ErrorActionPreference = $prevPref
    Write-Host "Đã cập nhật chứng chỉ TLS cho IP $ip" -ForegroundColor Green
}

# 3. Cập nhật MQTT_HOST trong secrets.h của ESP32 (nếu có)
$secretsPath = 'firmware\esp32-led-machine\secrets.h'
if (Test-Path $secretsPath) {
    $secrets = Get-Content $secretsPath -Raw
    $newSecrets = [regex]::Replace($secrets, '#define\s+MQTT_HOST\s+"[^"]+"', "#define MQTT_HOST `"$ip`"")
    [IO.File]::WriteAllText((Resolve-Path $secretsPath), $newSecrets, [Text.UTF8Encoding]::new($false))
    Write-Host "Đã cập nhật MQTT_HOST=$ip trong $secretsPath (nạp lại ESP32 nếu đổi mạng mới)" -ForegroundColor Yellow
}

# 4. Khởi động lại mock-payment-web-https và order-service để nhận IP/cert mới
docker compose --profile https up -d --no-deps --force-recreate mock-payment-web-https order-service
Write-Host "Hoàn tất đồng bộ IP $ip!" -ForegroundColor Green
