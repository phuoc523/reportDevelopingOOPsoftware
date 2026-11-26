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
if ([string]::IsNullOrWhiteSpace($ip)) { throw 'Could not detect current LAN IPv4 address.' }

Write-Host "Current LAN IP: $ip" -ForegroundColor Cyan

# 1. Update PAYMENT_MOCK_WEB_BASE_URL in .env
$envChanged = $false
$targetBaseUrl = "https://${ip}:3443"
if (Test-Path .env) {
    $lines = Get-Content .env
    $found = $false
    $updated = $lines | ForEach-Object {
        if ($_ -match '^PAYMENT_MOCK_WEB_BASE_URL=(.*)$') {
            $found = $true
            if ($Matches[1].Trim() -ne $targetBaseUrl) { $envChanged = $true }
            "PAYMENT_MOCK_WEB_BASE_URL=$targetBaseUrl"
        } else {
            $_
        }
    }
    if (-not $found) {
        $envChanged = $true
        $updated += "PAYMENT_MOCK_WEB_BASE_URL=$targetBaseUrl"
    }
    if ($envChanged) {
        Set-Content -Path .env -Value $updated -Encoding UTF8
        Write-Host "Updated .env -> PAYMENT_MOCK_WEB_BASE_URL=$targetBaseUrl" -ForegroundColor Green
    } else {
        Write-Host ".env already points to $targetBaseUrl" -ForegroundColor DarkGray
    }
}

# 2. Regenerate self-signed TLS cert with SAN matching current IP
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
    $opensslExit = $LASTEXITCODE
    $ErrorActionPreference = $prevPref
    if ($opensslExit -ne 0) { throw "OpenSSL failed with exit code $opensslExit." }
    Write-Host "Regenerated TLS certificate for IP $ip" -ForegroundColor Green
}

# 3. Update MQTT_HOST in ESP32 secrets.h (if present)
$secretsPath = 'firmware\esp32-led-machine\secrets.h'
if (Test-Path $secretsPath) {
    $secrets = Get-Content $secretsPath -Raw
    $newSecrets = [regex]::Replace($secrets, '#define\s+MQTT_HOST\s+"[^"]+"', "#define MQTT_HOST `"$ip`"")
    [IO.File]::WriteAllText((Resolve-Path $secretsPath), $newSecrets, [Text.UTF8Encoding]::new($false))
    Write-Host "Updated MQTT_HOST=$ip in $secretsPath" -ForegroundColor Yellow
}

# 4. Recreate mock-payment-web-https (and order-service only if base URL changed, waiting until healthy)
$prevPref = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
if ($envChanged) {
    docker compose --profile https up -d --no-deps --force-recreate --wait mock-payment-web-https order-service
} else {
    docker compose --profile https up -d --no-deps --force-recreate mock-payment-web-https
}
$dockerExit = $LASTEXITCODE
$ErrorActionPreference = $prevPref
if ($dockerExit -ne 0) { throw "docker compose recreate failed with exit code $dockerExit." }

Write-Host "LAN IP & TLS sync complete for $ip!" -ForegroundColor Green
