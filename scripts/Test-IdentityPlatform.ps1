[CmdletBinding(DefaultParameterSetName = 'All')]
param(
    [Parameter(ParameterSetName = 'Test')][switch]$TestsOnly,
    [Parameter(ParameterSetName = 'Smoke')][switch]$SmokeOnly,
    [string]$GatewayUrl = 'http://127.0.0.1:8080'
)

$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path

if (-not $SmokeOnly) {
    $docker = Get-Command docker -ErrorAction Stop
    $runner = 'set -eu; result=0; for module in identity-service api-gateway; do mkdir -p /tmp/$module; cp /source/services/$module/pom.xml /tmp/$module/; cp -r /source/services/$module/src /tmp/$module/; mvn -B -ntp -f /tmp/$module/pom.xml test || result=1; done; exit $result'
    $dockerArgs = @('run', '--rm', '--cpus=2', '--memory=2g', '--mount',
        "type=bind,source=$repo,destination=/source,readonly",
        'maven:3.9.9-eclipse-temurin-21', 'sh', '-c', $runner)
    & $docker.Source @dockerArgs
    if ($LASTEXITCODE -ne 0) { throw "Maven container failed (exit $LASTEXITCODE)." }
}

if (-not $TestsOnly) {
    $base = $GatewayUrl.TrimEnd('/')
    $checks = @(
        @{ Method = 'Get'; Path = '/actuator/health'; Status = 200 }
        @{ Method = 'Get'; Path = '/api/auth/health'; Status = 200 }
        @{ Method = 'Get'; Path = '/api/auth/tenants'; Status = 200 }
        @{ Method = 'Get'; Path = '/api/auth/me'; Status = 401 }
        @{ Method = 'Post'; Path = '/internal/auth/introspect'; Status = 404 }
    )
    $failures = @()
    foreach ($check in $checks) {
        $uri = $base + $check.Path
        try {
            $response = Invoke-WebRequest -Uri $uri -Method $check.Method -UseBasicParsing -TimeoutSec 10
            $status = [int]$response.StatusCode
        } catch {
            if (-not $_.Exception.Response) {
                $failures += "$($check.Method) $($check.Path): $($_.Exception.Message)"
                continue
            }
            $status = [int]$_.Exception.Response.StatusCode
        }
        if ($status -ne $check.Status) {
            $failures += "$($check.Method) $($check.Path) returned $status (expected $($check.Status))."
        } else {
            Write-Host "$($check.Method) $($check.Path) -> $status"
        }
    }
    if ($failures.Count -gt 0) { throw ($failures -join [Environment]::NewLine) }
}
