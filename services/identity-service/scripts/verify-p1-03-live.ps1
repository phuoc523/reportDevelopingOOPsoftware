# ScentStation P1-03: local development acceptance test against live MySQL.
# Run ONLY on the developer's isolated MySQL instance (127.0.0.1:3307).
# Requires running Identity Service on 127.0.0.1:8081 and the existing DPAPI secret.
$ErrorActionPreference = 'Stop'

$serviceDir = Split-Path -Parent $PSScriptRoot
$mysql = 'E:\MySQL\Server\bin\mysql.exe'
$secretPath = 'E:\XD\.secrets\scentstation-db-password.dpapi'
$api = 'http://127.0.0.1:8081/api/v1/auth'

if (!(Test-Path $mysql)) { throw 'mysql.exe not found' }
if (!(Test-Path $secretPath)) { throw 'Encrypted database password not found' }
if (!(Test-Path (Join-Path $serviceDir 'pom.xml'))) { throw 'Script must be saved under identity-service\scripts\' }

$secret = Get-Content $secretPath | ConvertTo-SecureString
$dbPassword = [System.Net.NetworkCredential]::new('', $secret).Password

function Invoke-Db {
    param([Parameter(Mandatory=$true)][string]$Sql)
    $env:MYSQL_PWD = $dbPassword
    try {
        $out = @(& $mysql --no-defaults --protocol=TCP -h 127.0.0.1 -P 3307 `
            -u scentstation_auth -D scentstation_identity `
            --batch --skip-column-names -e $Sql)
        if ($LASTEXITCODE -ne 0) { throw 'MySQL operation failed. See preceding error.' }
        return ($out -join "`n").Trim()
    }
    finally {
        Remove-Item Env:\MYSQL_PWD -ErrorAction SilentlyContinue
    }
}

function Login-HttpStatus {
    param([string]$Username, [string]$Password)
    $payload = @{ username=$Username; password=$Password } | ConvertTo-Json -Compress
    try {
        $null = Invoke-RestMethod -Method POST -Uri "$api/login" -ContentType 'application/json' `
            -Body $payload -ErrorAction Stop
        return 200
    }
    catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

function Assert-HttpStatus {
    param([int]$Expected,[int]$Actual,[string]$Test)
    if ($Actual -ne $Expected) { throw "$Test FAILED: expected HTTP $Expected, got $Actual" }
    Write-Host "[PASS] $Test - HTTP $Actual" -ForegroundColor Green
}

Write-Host '=== P1-03 LIVE MYSQL ACCEPTANCE ===' -ForegroundColor Cyan
$null = Invoke-Db 'SELECT 1;'
Write-Host '[PASS] MySQL 3307 verified' -ForegroundColor Green

try {
    $probe = New-Object System.Net.Sockets.TcpClient
    $probe.Connect('127.0.0.1', 8081)
    $probe.Close()
}
catch { throw 'Identity Service is not running on port 8081. Keep its terminal open.' }
Write-Host '[PASS] Identity Service 8081 reachable' -ForegroundColor Green

# Test 1: lockout with disposable staff account; never lock demo.admin.
$qaUser = 'qa.p103.lock.' + ([guid]::NewGuid().ToString('N').Substring(0,8))
$qaId = [guid]::NewGuid().ToString()
$created = $false
try {
    $createSql = "INSERT INTO staff_users (id, username, password_hash, tenant_id, enabled, manually_locked, failed_attempts) " + `
        "SELECT '$qaId', '$qaUser', password_hash, tenant_id, 1, 0, 0 FROM staff_users WHERE username='demo.admin';"
    $null = Invoke-Db $createSql
    if ((Invoke-Db "SELECT COUNT(*) FROM staff_users WHERE id='$qaId';") -ne '1') {
        throw 'Could not create the disposable QA staff user'
    }
    $created = $true
    Write-Host '[PASS] Disposable QA account created (demo.admin not affected)' -ForegroundColor Green

    $wrong = 'not-the-password-' + [guid]::NewGuid().ToString('N')
    for ($i = 1; $i -le 5; $i++) {
        $expected = if ($i -eq 5) { 423 } else { 401 }
        Assert-HttpStatus -Expected $expected -Actual (Login-HttpStatus $qaUser $wrong) -Test "Lockout attempt $i/5"
    }
    Assert-HttpStatus -Expected 423 -Actual (Login-HttpStatus $qaUser $wrong) -Test 'Still locked after threshold'

    $lockState = Invoke-Db "SELECT CONCAT(failed_attempts, ':', IF(locked_until IS NULL, 'UNLOCKED', 'LOCKED')) FROM staff_users WHERE id='$qaId';"
    if ($lockState -ne '5:LOCKED') { throw "Unexpected database account lock state: $lockState" }
    $auditState = Invoke-Db "SELECT result FROM login_attempts WHERE attempted_username='$qaUser' ORDER BY attempted_at DESC LIMIT 1;"
    if ($auditState -ne 'ACCOUNT_LOCKED') { throw "Unexpected login audit state: $auditState" }
    Write-Host '[PASS] MySQL lock state 5:LOCKED and ACCOUNT_LOCKED audit recorded' -ForegroundColor Green
}
finally {
    if ($created) {
        $null = Invoke-Db "DELETE FROM staff_users WHERE id='$qaId' AND username='$qaUser';"
        Write-Host '[INFO] Disposable QA account deleted; audit evidence retained' -ForegroundColor DarkGray
    }
}

# Test 2: issue a fresh token, prove /me works, expire only THAT token in MySQL,
# and confirm the live API rejects it. No waiting for the default 30-minute TTL.
$secureDemo = Read-Host 'Enter EXISTING demo.admin password' -AsSecureString
$demoPassword = [System.Net.NetworkCredential]::new('', $secureDemo).Password
try {
    $payload = @{ username='demo.admin'; password=$demoPassword } | ConvertTo-Json -Compress
    $login = Invoke-RestMethod -Uri "$api/login" -Method POST -ContentType 'application/json' -Body $payload -ErrorAction Stop
}
finally { $demoPassword = $null; $payload = $null }
if ([string]::IsNullOrWhiteSpace($login.accessToken)) { throw 'Missing token from demo.admin login' }

$headers = @{ Authorization = "Bearer $($login.accessToken)" }
$me = Invoke-RestMethod -Uri "$api/me" -Method GET -Headers $headers -ErrorAction Stop
if ($me.username -ne 'demo.admin') { throw 'Fresh token failed /me check' }
Write-Host '[PASS] Fresh session token accepted by /me - HTTP 200' -ForegroundColor Green

$sha = [System.Security.Cryptography.SHA256]::Create()
try {
    $tokenHash = [BitConverter]::ToString($sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($login.accessToken))).Replace('-', '').ToLowerInvariant()
}
finally { $sha.Dispose() }

$updated = Invoke-Db "UPDATE auth_sessions SET expires_at='2000-01-01 00:00:00' WHERE token_hash='$tokenHash' AND revoked_at IS NULL; SELECT ROW_COUNT();"
if ($updated -ne '1') { throw "Expected to expire exactly one session; matched: $updated" }
Write-Host '[PASS] Expired exactly one new session in live MySQL' -ForegroundColor Green

try {
    $null = Invoke-RestMethod -Uri "$api/me" -Method GET -Headers $headers -ErrorAction Stop
    $expiredStatus = 200
}
catch {
    if (!$_.Exception.Response) { throw }
    $expiredStatus = [int]$_.Exception.Response.StatusCode
}
Assert-HttpStatus -Expected 401 -Actual $expiredStatus -Test 'Expired token denied'
$headers = $null
$login = $null

# Safe evidence for Git and Jira (never persist passwords or bearer tokens).
$reportDir = Join-Path $serviceDir 'docs'
New-Item -ItemType Directory -Force -Path $reportDir | Out-Null
$reportPath = Join-Path $reportDir 'P1-03-LIVE-ACCEPTANCE.md'
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'
$report = @"
# P1-03 Live Acceptance - Lockout and Session Expiry

- Verified at: $stamp
- Environment: Windows / Spring Boot 3.5.6 / MySQL 8.0, localhost:3307 / HTTP 8081
- Run type: local development database and live HTTP endpoints

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| Wrong password attempts 1-4 (disposable QA account) | HTTP 401 | HTTP 401 x4 | PASS |
| Wrong password attempt 5 | HTTP 423 | HTTP 423 | PASS |
| Login while locked | HTTP 423 | HTTP 423 | PASS |
| MySQL account status and audit | failed_attempts=5, locked_until populated, ACCOUNT_LOCKED audit | Confirmed | PASS |
| Valid demo.admin bearer session /me | HTTP 200 | HTTP 200 | PASS |
| Live MySQL session expiry (token hash targeted) | /me returns HTTP 401 | HTTP 401 | PASS |

## Notes

- Temporary QA account was deleted after the lockout test. Audit records were retained.
- The expiry test changed `expires_at` for exactly one newly issued test token to a past timestamp; it did not change the default 30-minute TTL.
- No bearer tokens, passwords, or database credentials were recorded in this report.
- Tests were performed on a local development environment, not production.
- Previously verified separately: valid login, /me, logout, revoked token, invalid user, login audit, and the existing H2 integration suite.
"@
Set-Content -Path $reportPath -Value $report -Encoding UTF8
Write-Host "[PASS] Saved sanitized evidence: $reportPath" -ForegroundColor Green
Write-Host 'ALL P1-03 LIVE ACCEPTANCE TESTS PASSED' -ForegroundColor Green
