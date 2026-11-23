param(
    [switch]$NoComposeUp
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repositoryRoot '.env'

if (-not (Test-Path -LiteralPath $envPath)) {
    throw "Missing $envPath. Copy .env.example to .env and configure the required secrets first."
}

$wifiAdapters = @(
    Get-NetAdapter -Physical |
        Where-Object { $_.Status -eq 'Up' -and [int]$_.NdisPhysicalMedium -eq 9 }
)

if ($wifiAdapters.Count -ne 1) {
    throw "Expected exactly one active physical Wi-Fi adapter; found $($wifiAdapters.Count). Check the Wi-Fi connection and retry."
}

$wifiAddresses = @(
    Get-NetIPAddress -InterfaceIndex $wifiAdapters[0].ifIndex -AddressFamily IPv4 |
        Where-Object {
            $_.AddressState -eq 'Preferred' -and
            $_.IPAddress -notmatch '^(127\.|169\.254\.)'
        } |
        ForEach-Object {
            $address = [System.Net.IPAddress]::Parse($_.IPAddress)
            $bytes = $address.GetAddressBytes()
            $isPrivate = $bytes[0] -eq 10 -or
                ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) -or
                ($bytes[0] -eq 192 -and $bytes[1] -eq 168)
            if ($isPrivate) { $_.IPAddress }
        } |
        Select-Object -Unique
)

if ($wifiAddresses.Count -ne 1) {
    throw "Could not determine one preferred private IPv4 address for Wi-Fi adapter '$($wifiAdapters[0].Name)'. Configure LAN_HOST manually and retry."
}

$lanHost = $wifiAddresses[0]
$certificatePath = Join-Path $repositoryRoot 'infra\docker\tls\mock-payment.crt'
$keyPath = Join-Path $repositoryRoot 'infra\docker\tls\mock-payment.key'
if (-not (Test-Path -LiteralPath $certificatePath) -or -not (Test-Path -LiteralPath $keyPath)) {
    throw "HTTPS certificate files are required for phone camera access. Create infra\docker\tls\mock-payment.crt and mock-payment.key with mkcert for $lanHost; see frontend\mock-payment-web\README.md."
}

$paymentBaseUrl = "https://${lanHost}:3443/"
$httpsOrigin = "https://${lanHost}:3443"
$lines = [System.Collections.Generic.List[string]]::new()
$hasLanHost = $false
$hasPaymentBaseUrl = $false
$hasHttpsOrigin = $false

foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^\s*LAN_HOST\s*=') {
        $lines.Add("LAN_HOST=$lanHost")
        $hasLanHost = $true
    }
    elseif ($line -match '^\s*PAYMENT_MOCK_WEB_BASE_URL\s*=') {
        $lines.Add("PAYMENT_MOCK_WEB_BASE_URL=$paymentBaseUrl")
        $hasPaymentBaseUrl = $true
    }
    elseif ($line -match '^\s*MOCK_PAYMENT_HTTPS_ORIGIN\s*=') {
        $lines.Add("MOCK_PAYMENT_HTTPS_ORIGIN=$httpsOrigin")
        $hasHttpsOrigin = $true
    }
    else {
        $lines.Add($line)
    }
}

if (-not $hasLanHost) { $lines.Add("LAN_HOST=$lanHost") }
if (-not $hasPaymentBaseUrl) { $lines.Add("PAYMENT_MOCK_WEB_BASE_URL=$paymentBaseUrl") }
if (-not $hasHttpsOrigin) { $lines.Add("MOCK_PAYMENT_HTTPS_ORIGIN=$httpsOrigin") }
[System.IO.File]::WriteAllLines($envPath, $lines.ToArray(), [System.Text.Encoding]::UTF8)

Write-Host "Using Wi-Fi adapter '$($wifiAdapters[0].Name)' with IPv4 $lanHost."
Write-Host "Payment QR URL (HTTPS): $paymentBaseUrl"
Write-Host 'Install/trust the mkcert root certificate on the phone before granting camera permission.'

if (-not $NoComposeUp) {
    Push-Location $repositoryRoot
    try {
        docker compose config --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration validation failed.' }
        docker compose --profile https up -d --build
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose startup failed.' }
    }
    finally {
        Pop-Location
    }
}
