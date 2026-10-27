# P1-11 live gateway smoke test. Identity and Gateway must already be running.
[CmdletBinding()]
param(
    [string]$Gateway = 'http://127.0.0.1:8080',
    [string]$Identity = 'http://127.0.0.1:8081',
    [string]$EvidenceDir = 'E:\XD\Platform-Evidence'
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $EvidenceDir | Out-Null
$checks = New-Object System.Collections.Generic.List[string]
function Probe {
    param([string]$Url,[string]$Method='GET',[hashtable]$Headers=@{},[string]$Body='')
    try {
        $args = @{ Uri=$Url; Method=$Method; Headers=$Headers; UseBasicParsing=$true; TimeoutSec=8 }
        if ($Body) { $args.Body=$Body; $args.ContentType='application/json' }
        $r=Invoke-WebRequest @args
        return @{ Status=[int]$r.StatusCode; Headers=$r.Headers }
    } catch {
        if ($_.Exception.Response) {
            return @{ Status=[int]$_.Exception.Response.StatusCode.value__; Headers=$_.Exception.Response.Headers }
        }
        throw "Network error calling $Url : $($_.Exception.Message)"
    }
}
function Assert-Code([string]$Name,[int]$Actual,[int[]]$Expected) {
    if ($Expected -notcontains $Actual) {throw "[FAIL] $Name expected $($Expected -join '/') received $Actual"}
    $checks.Add("[PASS] $Name HTTP $Actual")
    Write-Host $checks[$checks.Count-1] -ForegroundColor Green
}
$health=Probe "$Gateway/actuator/health"; Assert-Code 'Gateway health' $health.Status @(200)
$none=Probe "$Gateway/api/v1/unconfigured/ping"; Assert-Code 'Unconfigured route' $none.Status @(404)
$internal=Probe "$Gateway/api/v1/identity/internal/tenant/introspect" 'POST' @{} '{}'; Assert-Code 'Internal path blocked' $internal.Status @(404)
$direct=Probe "$Identity/api/v1/rbac/me"; Assert-Code 'Direct Identity anonymous authentication' $direct.Status @(401)
$via=Probe "$Gateway/api/v1/identity/rbac/me"; Assert-Code 'Gateway to Identity route' $via.Status @(401)
$cors=Probe "$Gateway/api/v1/identity/auth/login" 'OPTIONS' @{
  Origin='http://localhost:5173';'Access-Control-Request-Method'='POST'
}
Assert-Code 'Gateway CORS preflight' $cors.Status @(200,204)
$origin=$cors.Headers['Access-Control-Allow-Origin']
if (@($origin) -notcontains 'http://localhost:5173') {throw '[FAIL] CORS allow-origin missing or wrong'}
$checks.Add('[PASS] CORS restricted origin allowlist response')
$checks | Set-Content -Path (Join-Path $EvidenceDir 'P1-11-GATEWAY-SMOKE.txt') -Encoding UTF8
Write-Host 'GATEWAY SMOKE TESTS PASSED. Downstream owners and tenant E2E still pending.' -ForegroundColor Green
