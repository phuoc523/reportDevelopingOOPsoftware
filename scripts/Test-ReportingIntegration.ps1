param(
    [string]$GatewayUrl = 'http://localhost:8080',
    [string]$ReportingUrl = 'http://localhost:8086',
    [string]$SyncApiKey = $(if ($env:REPORTING_SYNC_API_KEY) { $env:REPORTING_SYNC_API_KEY } else { 'local-reporting-sync-key' })
)

$ErrorActionPreference = 'Stop'
$GatewayUrl = $GatewayUrl.TrimEnd('/')
$ReportingUrl = $ReportingUrl.TrimEnd('/')
$deadline = (Get-Date).AddMinutes(2)
do {
    try {
        $null = Invoke-RestMethod "$GatewayUrl/api/reporting/health" -TimeoutSec 5
        $null = Invoke-RestMethod "$GatewayUrl/api/v1/orders" -TimeoutSec 5
        $dashboard = Invoke-RestMethod "$GatewayUrl/api/reporting/dashboard?tenantId=1&period=day" -TimeoutSec 30
        foreach ($service in @('machine-service', 'machine-alerts', 'operation-service')) {
            if ($dashboard.upstream.$service -ne 'ok') {
                throw "Dashboard source $service returned $($dashboard.upstream.$service)"
            }
        }
        break
    } catch {
        if ((Get-Date) -ge $deadline) { throw }
        Start-Sleep -Seconds 2
    }
} while ($true)

$orderBody = @{
    tenantId = '1'
    customerId = 7
    machineId = 'REPORTING-DEMO'
    slotId = 'SLOT-01'
    items = @(@{
        productId = 3
        productName = 'Reporting smoke test'
        unitPrice = 25000
        quantity = 1
    })
} | ConvertTo-Json -Depth 4
$order = Invoke-RestMethod "$GatewayUrl/api/v1/orders" -Method Post -ContentType 'application/json' -Body $orderBody
$payment = Invoke-RestMethod "$GatewayUrl/api/v1/orders/$($order.id)/payments/qr" -Method Post
$reference = $payment.reference
$reportUrl = "$GatewayUrl/api/reporting/transactions/reference/$reference"
$pending = Invoke-RestMethod $reportUrl
if ($pending.status -ne 'PENDING') { throw "Expected PENDING for $reference" }

$null = Invoke-RestMethod "$GatewayUrl/api/v1/payments/$reference/simulate" -Method Post
$paid = Invoke-RestMethod $reportUrl
if ($paid.status -ne 'PAID' -or $paid.id -ne $pending.id -or
    $paid.orderId -ne [string]$order.id -or $paid.amount -ne 25000) {
    throw "Expected the same Reporting row to transition to PAID for $reference"
}

$replayUrl = "$ReportingUrl/api/reporting/internal/transactions/sync-from-order/$reference"
$unauthorized = Invoke-WebRequest $replayUrl -Method Post -SkipHttpErrorCheck
if ([int]$unauthorized.StatusCode -ne 401) {
    throw 'Expected Reporting to reject synchronization without the internal API key'
}
$replayed = Invoke-RestMethod $replayUrl -Method Post -Headers @{ 'X-Internal-Api-Key' = $SyncApiKey }
if ($replayed.id -ne $paid.id -or $replayed.status -ne 'PAID') {
    throw 'Expected replay from Order to preserve the same paid transaction'
}

$internal = Invoke-WebRequest "$GatewayUrl/api/reporting/internal/transactions/sync" -Method Post -SkipHttpErrorCheck
if ([int]$internal.StatusCode -ne 404) {
    throw "Expected Gateway to return 404 for the internal Reporting endpoint"
}

Write-Output "PASS: Gateway, Machine, Operation, and Order -> Reporting ($reference)."
