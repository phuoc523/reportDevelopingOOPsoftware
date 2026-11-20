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
$paymentBaseUrl = "http://${lanHost}:3000/"
$lines = [System.Collections.Generic.List[string]]::new()
$hasLanHost = $false
$hasPaymentBaseUrl = $false

foreach ($line in [System.IO.File]::ReadAllLines($envPath)) {
    if ($line -match '^\s*LAN_HOST\s*=') {
        $lines.Add("LAN_HOST=$lanHost")
        $hasLanHost = $true
    }
    elseif ($line -match '^\s*PAYMENT_MOCK_WEB_BASE_URL\s*=') {
        $lines.Add("PAYMENT_MOCK_WEB_BASE_URL=$paymentBaseUrl")
        $hasPaymentBaseUrl = $true
    }
    else {
        $lines.Add($line)
    }
}

if (-not $hasLanHost) { $lines.Add("LAN_HOST=$lanHost") }
if (-not $hasPaymentBaseUrl) { $lines.Add("PAYMENT_MOCK_WEB_BASE_URL=$paymentBaseUrl") }
[System.IO.File]::WriteAllLines($envPath, $lines.ToArray(), [System.Text.Encoding]::UTF8)

Write-Host "Using Wi-Fi adapter '$($wifiAdapters[0].Name)' with IPv4 $lanHost."
Write-Host "Payment QR URL: $paymentBaseUrl"

if (-not $NoComposeUp) {
    Push-Location $repositoryRoot
    try {
        docker compose config --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose configuration validation failed.' }
        docker compose up -d --build
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose startup failed.' }
    }
    finally {
        Pop-Location
    }
}
