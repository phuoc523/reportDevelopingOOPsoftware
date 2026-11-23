[CmdletBinding()]
param(
    [Parameter(Mandatory)][PSCredential]$Customer,
    [Parameter(Mandatory)][PSCredential]$Admin,
    [Parameter(Mandatory)][string]$OrderFixture
)
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'Check-ScentStation-Lite.ps1')
function Send-Lite($base, $path, $method = 'GET', $headers = @{}, $data = $null) {
    $request = @{Uri="$base$path"; Method=$method; Headers=$headers; TimeoutSec=10}
    if ($null -ne $data) { $request.ContentType='application/json'; $request.Body=$data | ConvertTo-Json -Depth 6 -Compress }
    Invoke-RestMethod @request
}
function Login-Lite($origin, [PSCredential]$account) {
    $session = Send-Lite $origin '/api/auth/login' 'POST' @{} @{
        username=$account.UserName; password=$account.GetNetworkCredential().Password
    }
    if (!$session.token) { throw 'Login response missing token.' }
    @{Authorization="Bearer $($session.token)"}
}
$kiosk='http://127.0.0.1:17515'
$adminOrigin='http://127.0.0.1:17514'
$mock='http://127.0.0.1:17516'
$customerHeaders = Login-Lite $kiosk $Customer
$adminHeaders = Login-Lite $adminOrigin $Admin
$fixture = Get-Content -LiteralPath $OrderFixture -Raw | ConvertFrom-Json
if ($fixture.orderType -cne 'TRIAL' -or @($fixture.items).Count -ne 1) { throw 'Provide a single-item TRIAL fixture.' }
$products = Send-Lite $kiosk "/api/products?tenantId=$($fixture.tenantId)" GET $customerHeaders
if (!($products | Where-Object id -eq $fixture.items[0].productId)) { throw 'Fixture product unavailable.' }
$order = Send-Lite $kiosk '/api/v1/orders' POST $customerHeaders $fixture
$payment = Send-Lite $kiosk "/api/v1/orders/$($order.id)/payments/qr" POST $customerHeaders
if ($payment.status -ne 'PENDING' -or !$payment.qrPayload -or $payment.orderId -ne $order.id) { throw 'Invalid PENDING payment.' }
$reference = [uri]::EscapeDataString($payment.reference)
$endpoint = "/api/v1/payments/public/$reference/confirm"
$mockHeaders = @{Origin=$mock}
$event = @{eventId="lite-$([guid]::NewGuid().ToString('N'))"; reference=$payment.reference; orderId=$order.id; amount=$payment.amount; currency=$payment.currency; status='PAID'}
$wrongAmount = $event.Clone()
$wrongAmount.eventId += '-wrong-amount'
$wrongAmount.amount = [decimal]$event.amount + 1
try {
    Send-Lite $mock $endpoint POST $mockHeaders $wrongAmount | Out-Null
    throw 'Payment accepted an incorrect amount.'
} catch [System.Net.WebException] {
    if ([int]$_.Exception.Response.StatusCode -ne 400) { throw }
}
$pending = Send-Lite $mock "/api/v1/payments/public/$reference"
if ($pending.status -ne 'PENDING') { throw 'Rejected payment changed transaction status.' }
$confirmed = Send-Lite $mock $endpoint POST $mockHeaders $event
if ($confirmed.status -ne 'PAID') { throw 'Mock payment did not reach PAID.' }
$replayed = Send-Lite $mock $endpoint POST $mockHeaders $event
if ($replayed.status -ne 'PAID' -or $replayed.paidAt -ne $confirmed.paidAt) { throw 'Replay changed the recorded transaction.' }
$final = $null
for ($attempt=0; $attempt -lt 30; $attempt++) {
    $final = Send-Lite $kiosk "/api/v1/orders/$($order.id)" GET $customerHeaders
    if ($final.status -eq 'DISPENSED') { break }
    Send-Lite $kiosk "/api/v1/orders/$($order.id)/dispensing" GET $customerHeaders | Out-Null
    Start-Sleep -Seconds 1
}
if ($final.status -ne 'DISPENSED' -or !$final.commandId) { throw 'Dispensing did not complete.' }
$payments = Send-Lite $adminOrigin '/api/v1/payments' GET $adminHeaders
$orders = Send-Lite $adminOrigin '/api/v1/orders' GET $adminHeaders
$actual = @($orders | Where-Object id -eq $order.id)
if ($actual.Count -ne 1 -or $actual[0].commandId -ne $final.commandId -or
    @($payments | Where-Object reference -eq $payment.reference).Count -ne 1) { throw 'Admin does not see the same unique order/payment/command.' }
[pscustomobject]@{Result='PASS'; OrderId=$order.id; Reference=$payment.reference; CommandId=$final.commandId; Status=$final.status} | ConvertTo-Json
& (Join-Path $PSScriptRoot 'Check-ScentStation-Lite.ps1')
