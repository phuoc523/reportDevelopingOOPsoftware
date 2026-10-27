$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8081/api/v1'
$folder = 'E:\XD\P1-05-Evidence'
New-Item -ItemType Directory -Path $folder -Force | Out-Null
$report = Join-Path $folder 'P1-05-LIVE-TEST.txt'
Set-Content $report "P1-05 Live Tenant Scope Acceptance - $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz')"

function Check-Status {
    param([string]$Path, [string]$Method='GET', [hashtable]$Headers=@{},
        [string]$Body=$null, [int]$Expected=200)
    $actual = 0
    try {
        $args = @{ Uri="$base$Path"; Method=$Method; Headers=$Headers; UseBasicParsing=$true; ErrorAction='Stop' }
        if ($PSBoundParameters.ContainsKey('Body') -and $Method -notin @('GET', 'HEAD')) {
            $args.ContentType = 'application/json'
            $args.Body = $Body
        }
        $result = Invoke-WebRequest @args
        $actual = [int]$result.StatusCode
    } catch {
        if (-not $_.Exception.Response) { throw }
        $actual = [int]$_.Exception.Response.StatusCode
    }
    if ($actual -ne $Expected) { throw "FAIL: $Method $Path expected HTTP $Expected, got $actual" }
    $line = "[PASS] HTTP $actual $Method $Path"
    Write-Host $line -ForegroundColor Green
    Add-Content $report $line
}

$check = Test-NetConnection 127.0.0.1 -Port 8081 -WarningAction SilentlyContinue
if (-not $check.TcpTestSucceeded) { throw 'Identity Service port 8081 is offline. Start Spring Boot first.' }
Check-Status -Path '/tenant-scope/me' -Expected 401

$secure = Read-Host 'Existing demo.admin password' -AsSecureString
$password = [System.Net.NetworkCredential]::new('', $secure).Password
try {
    $body = @{username='demo.admin';password=$password} | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "$base/auth/login" -Method POST -ContentType 'application/json' -Body $body
} finally {
    $password = $null
    $body = $null
}
if ([string]::IsNullOrWhiteSpace($login.accessToken)) { throw 'Login returned no accessToken' }
$h = @{Authorization="Bearer $($login.accessToken)"}
try {
    $me = Invoke-RestMethod -Uri "$base/tenant-scope/me" -Headers $h
    if ([string]::IsNullOrWhiteSpace($me.tenantId)) { throw 'demo.admin has no tenantId' }
    Write-Host '[PASS] Verified tenant derived from authenticated session' -ForegroundColor Green
    Add-Content $report '[PASS] Verified tenant derived from authenticated session'
    $my = [uri]::EscapeDataString($me.tenantId)
    $other = [guid]::NewGuid().ToString()
    Check-Status -Path "/tenant-scope/check?tenantId=$my" -Headers $h -Expected 204
    Check-Status -Path "/tenant-scope/check?tenantId=$other" -Headers $h -Expected 403
    $spoof = @{Authorization=$h.Authorization;'X-Tenant-Id'=$other}
    Check-Status -Path '/tenant-scope/me' -Headers $spoof -Expected 403
    Check-Status -Path '/tenant-scope/check' -Method 'POST' -Headers $h -Body (@{tenantId=$other}|ConvertTo-Json -Compress) -Expected 403
    Check-Status -Path '/tenant-scope/check' -Method 'POST' -Headers $h -Body (@{tenantId=$me.tenantId}|ConvertTo-Json -Compress) -Expected 204
    Check-Status -Path "/tenant-scope/users/$($me.userId)" -Headers $h -Expected 200
    Invoke-RestMethod -Uri "$base/auth/logout" -Method POST -Headers $h | Out-Null
    Check-Status -Path '/tenant-scope/me' -Headers $h -Expected 401
    Write-Host 'P1-05 LIVE TENANT SCOPE TESTS PASSED' -ForegroundColor Green
    Add-Content $report 'P1-05 LIVE TENANT SCOPE TESTS PASSED'
    Write-Host "Evidence: $report"
} finally {
    $h.Clear()
}
