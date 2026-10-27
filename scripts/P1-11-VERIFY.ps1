# P1-11: run test suites independently, preserving logs even if the terminal crashes.
[CmdletBinding()]
param(
    [ValidateSet('all','identity','gateway')][string]$Module = 'all',
    [string]$RepoRoot = 'E:\XD\reportDevelopingOOPsoftware',
    [string]$EvidenceDir = 'E:\XD\Platform-Evidence'
)
$ErrorActionPreference = 'Stop'
$javaHome = 'E:\XD\jdk-21\jdk-21.0.12.1+1'
$mvn = 'E:\XD\NV1\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd'
$repository = 'E:\XD\.m2\repository'
if (-not (Test-Path $mvn)) { throw "Maven not found: $mvn" }
if (-not (Test-Path "$javaHome\bin\java.exe")) { throw "Java not found: $javaHome" }
if (-not (Test-Path "$RepoRoot\services\identity-service\pom.xml")) { throw "Invalid repo: $RepoRoot" }
New-Item -ItemType Directory -Force $EvidenceDir,'E:\XD\temp' | Out-Null
$env:JAVA_HOME = $javaHome
$env:Path = "$javaHome\bin;$env:Path"
$env:TEMP = 'E:\XD\temp'
$env:TMP = 'E:\XD\temp'
$env:MAVEN_OPTS = '-Xms32m -Xmx384m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Djava.io.tmpdir=E:\XD\temp'
Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
$failures = @()
$targets = if ($Module -eq 'all') {@('identity-service','api-gateway')} elseif ($Module -eq 'identity') {@('identity-service')} else {@('api-gateway')}
foreach ($name in $targets) {
    $path = Join-Path $RepoRoot "services\$name"
    if (-not (Test-Path "$path\pom.xml")) { throw "Missing pom: $path" }
    Push-Location $path
    try {
        $log = Join-Path $EvidenceDir "$name-maven-test.log"
        Write-Host "Testing $name ... output: $log" -ForegroundColor Cyan
        try {
            $ErrorActionPreference = 'Continue'
            & $mvn "-Dmaven.repo.local=$repository" '-DforkCount=0' test *> $log
        } finally {
            $ErrorActionPreference = 'Stop'
        }
        $exitCode = $LASTEXITCODE
        Get-Content $log -Tail 12
        if ($exitCode -ne 0 -or -not (Select-String -Path $log -Pattern 'BUILD SUCCESS' -Quiet)) {
            $failures += $name
            Write-Host "[FAIL] ${name}: inspect $log" -ForegroundColor Red
        } else {
            Write-Host "[PASS] $name" -ForegroundColor Green
        }
    } finally { Pop-Location }
}
if ($failures.Count -gt 0) { throw "Tests failed: $($failures -join ', ')" }
Write-Host 'MAVEN TESTS PASSED. Live/tenant/owner sign-off still required.' -ForegroundColor Green
