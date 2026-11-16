[CmdletBinding()]
param(
    [string]$Repo = 'E:\XD\NV1\reportDevelopingOOPsoftware-test',
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipDocker
)

$ErrorActionPreference = 'Stop'
$branch = 'test-2026-11-07-latest'
if (-not (Test-Path (Join-Path $Repo '.git'))) { throw "Khong tim thay Git repository: $Repo" }
Set-Location $Repo
if ((git branch --show-current).Trim() -ne $branch) { throw "Chi chay tren branch $branch" }

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out = Join-Path 'E:\XD\ScentStation\reports' "audit-1107-$stamp"
New-Item -ItemType Directory -Path $out -Force | Out-Null
$results = New-Object System.Collections.Generic.List[object]
function Record([string]$component,[string]$status,[string]$detail) {
    $script:results.Add([pscustomobject]@{ Component=$component; Result=$status; Detail=$detail })
    Write-Host ("{0,-28} {1,-8} {2}" -f $component,$status,$detail) -ForegroundColor $(if($status -eq 'PASS'){'Green'}elseif($status -eq 'FAIL'){'Red'}else{'Yellow'})
}
function ReadContains([string]$path,[string]$pattern) {
    if(-not(Test-Path $path)) { return $false }
    return [bool](Select-String -LiteralPath $path -Pattern $pattern -Quiet)
}
function EnsureReady([string]$command,[string]$fallback) {
    $a = Get-Command $command -ErrorAction SilentlyContinue
    if($a) { return $a.Source }
    if((Test-Path $fallback)) { return $fallback }
    return $null
}

$head=(git rev-parse HEAD).Trim()
$remote=(git ls-remote origin "refs/heads/$branch" 2>$null | Select-Object -First 1)
$remoteSha = if($remote){($remote -split '\s+')[0]}else{''}
Record 'Git branch' 'PASS' "$branch @ $($head.Substring(0,7))"
if($remoteSha -eq $head){Record 'Git remote sync' 'PASS' 'Local == remote'}else{Record 'Git remote sync' 'WARN' 'Remote chua trung local (hoac chua ket noi)'}
$dirty = @(git status --porcelain)
$unexpected = @($dirty | Where-Object { $_ -notmatch '^\?\? docker-compose\.mysql\.e\.yml$' })
if($unexpected.Count -eq 0){Record 'Git working tree' 'PASS' 'Khong co sua doi tracked; mysql.e.yml duoc bo qua'}else{Record 'Git working tree' 'WARN' ($unexpected -join '; ')}

# Structural checks do not claim runtime function works.
$gateway = 'services/api-gateway/src/main/resources/application.yml'
if(ReadContains $gateway 'Path=/api/orders|Path=/api/payments'){Record 'Gateway order routing' 'PASS' 'Co routing Order/Payment'}else{Record 'Gateway order routing' 'WARN' 'Chua thay route Order/Payment trong application.yml'}
if((ReadContains 'services/order-service/README.md' 'held in memory|in-memory') -and -not(Test-Path 'services/order-service/src/main/java/com/scentstation/order/repository/JdbcOrderRepository.java')) {
    Record 'Order persistence' 'WARN' 'Dang in-memory; se mat du lieu khi restart'
}else{Record 'Order persistence' 'PASS' 'Co dau hieu persistence; can test DB thuc te'}
if(Test-Path 'services/machine-service/src/main/java/com/scentstation/service/MqttDeviceMessageListener.java') {
    Record 'Machine MQTT listener' 'PASS' 'Co class MQTT listener'
}else{Record 'Machine MQTT listener' 'WARN' 'Khong co MqttDeviceMessageListener.java; can xac nhan MQTT duoc thay the'}
if(ReadContains 'docker-compose.yml' '^services:\s*\{\s*\}') {Record 'Full Docker Compose' 'WARN' 'services: {} (chua the docker compose up full system)'}else{Record 'Full Docker Compose' 'PASS' 'Co khai bao service (chua test start)'}
if((ReadContains 'services/operation-service/src/main/resources/application.yml' '8085') -and (ReadContains 'services/reporting-service/src/main/resources/application.yml' '8085')) {
    Record 'Default service ports' 'WARN' 'Operation va Reporting cung mac dinh 8085'
}else{Record 'Default service ports' 'PASS' 'Khong thay xung dot cong 8085'}
foreach($b in @('origin/feature/p1-06-p1-11-platform','origin/test-order-payment')) {
    git show-ref --verify --quiet "refs/remotes/$b" 2>$null
    if($LASTEXITCODE -ne 0){ Record "Merge $b" 'WARN' 'Khong co remote-tracking ref' ; continue }
    git merge-base --is-ancestor $b HEAD 2>$null
    if($LASTEXITCODE -eq 0){Record "Merge $b" 'PASS' 'Branch da nam trong history'}else{Record "Merge $b" 'WARN' 'Con commit chua duoc merge'}
}

# Docker not being available must NOT abort the other 9 build/test checks.
# Windows PowerShell 5.1 can promote native stderr to terminating NativeCommandError
# when $ErrorActionPreference = Stop, even with `2>$null`.
if($SkipDocker) {
    Record 'Docker engine' 'WARN' 'Skipped explicitly; Docker/MySQL not checked in this run'
}
elseif(-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Record 'Docker engine' 'WARN' 'docker.exe chua co tren PATH; tiep tuc backend/frontend'
}
else {
    $prefBeforeDocker = $ErrorActionPreference
    $dockerExit = 1
    $mysqlExit = 1
    $mysql = ''
    try {
        $ErrorActionPreference = 'SilentlyContinue'
        try {
            $null = & docker info --format '{{.ServerVersion}}' 2>$null
            $dockerExit = $LASTEXITCODE
        } catch { $dockerExit = 1 }
        if($dockerExit -eq 0) {
            try {
                $mysql = & docker inspect scentstation-mysql --format '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{else}}no-healthcheck{{end}}' 2>$null
                $mysqlExit = $LASTEXITCODE
            } catch { $mysqlExit = 1 }
        }
    }
    finally {
        $ErrorActionPreference = $prefBeforeDocker
    }
    if($dockerExit -ne 0) {
        Record 'Docker engine' 'WARN' 'Linux Engine khong ket noi duoc; KHONG dung audit, tiep tuc test backend/frontend'
        Record 'MySQL container' 'WARN' 'Khong kiem tra duoc khi Docker Engine offline'
    }
    else {
        Record 'Docker engine' 'PASS' 'Daemon accessible'
        if($mysqlExit -eq 0 -and $mysql -match '^running/healthy') {
            Record 'MySQL container' 'PASS' "$mysql"
        }
        else {
            Record 'MySQL container' 'WARN' "Container absent/unhealthy: $mysql"
        }
    }
}

$mvn = EnsureReady 'mvn.cmd' 'E:\XD\NV1\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd'
$node = EnsureReady 'npm.cmd' 'D:\wed bán hàng\npm.cmd'
$services = @('api-gateway','identity-service','catalog-service','order-service','machine-service','operation-service','reporting-service')
$oldOpts = $env:MAVEN_OPTS
if(-not $SkipBackend) {
    if(-not $mvn) { Record 'Maven' 'FAIL' 'Khong tim thay mvn.cmd' }
    else {
        $env:MAVEN_OPTS = '-Xms64m -Xmx448m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Xss512k'
        try {
            foreach($name in $services) {
                $pom = Join-Path $Repo "services\$name\pom.xml"
                $log = Join-Path $out "$name-test.log"
                if(-not(Test-Path $pom)){Record $name 'FAIL' 'Missing pom.xml';continue}
                Write-Host "Testing $name (log: $log)" -ForegroundColor Cyan
                $code=1
                try {
                    & $mvn -B -ntp -f $pom '-DforkCount=1' '-DreuseForks=true' '-DargLine=-Xmx320m' test 2>&1 | Out-File -LiteralPath $log -Encoding utf8
                    $code=$LASTEXITCODE
                }catch { $_.Exception.Message | Out-File -LiteralPath $log -Append -Encoding utf8; $code=1 }
                $fatal=ReadContains $log 'There is insufficient memory|OutOfMemoryError|malloc\) failed'
                if($code -eq 0){Record $name 'PASS' 'Maven test exit=0'}
                elseif($fatal){Record $name 'FAIL' 'JVM out of memory (xem log)'}
                else{Record $name 'FAIL' "Maven exit=$code (xem log)"}
            }
        }finally{$env:MAVEN_OPTS=$oldOpts}
    }
}
if($SkipFrontend) { Record 'Frontend builds' 'WARN' 'Skipped in this run; previous PASS logs remain separate' }
if(-not $SkipFrontend) {
    if(-not $node) {Record 'npm' 'FAIL' 'Khong tim thay npm.cmd'}
    else {
        foreach($name in @('admin-web','kiosk-web')) {
            $dir=Join-Path $Repo "frontend\$name"
            if(-not (Test-Path (Join-Path $dir 'package.json'))) {Record $name 'FAIL' 'Missing package.json';continue}
            Push-Location $dir
            try {
                $installLog=Join-Path $out "$name-install.log"
                & $node ci --no-audit --no-fund 2>&1 | Out-File -LiteralPath $installLog -Encoding utf8
                if($LASTEXITCODE -ne 0){Record $name 'FAIL' 'npm ci failed';continue}
                $buildLog=Join-Path $out "$name-build.log"
                & $node run build 2>&1 | Out-File -LiteralPath $buildLog -Encoding utf8
                if($LASTEXITCODE -eq 0){Record $name 'PASS' 'npm ci + npm run build'}else{Record $name 'FAIL' 'npm run build failed'}
            }catch {Record $name 'FAIL' $_.Exception.Message}
            finally{Pop-Location}
        }
    }
}

$csv=Join-Path $out 'results.csv'
$results | Export-Csv -LiteralPath $csv -NoTypeInformation -Encoding utf8
$md=Join-Path $out 'SUMMARY.md'
$lines=@(
 '# ScentStation integration audit — 2026-11-07',
 '',
 "- Branch: ``$branch``",
 "- Checked HEAD: ``$head``",
 "- Run: ``$stamp`` (local machine; no claims of remote test execution)",
 '- PASS: tool/build/test result as described; WARN: missing evidence or known gap; FAIL: test/tool failed.',
 '',
 '| Component | Result | Detail |', '|---|---|---|'
)
foreach($r in $results){$detail=([string]$r.Detail).Replace('|','/').Replace('`','\`');$lines += "| $($r.Component) | $($r.Result) | $detail |"}
$lines += @('', '## Notes', '- Audit does not resolve Git conflicts, auto-merge branches, or assert E2E completion.', '- Local logs may expose environment details; review before sharing.', '- The report is separate from the repository until explicitly published.')
$lines | Set-Content -LiteralPath $md -Encoding utf8
Write-Host "`nRESULTS: $csv`nSUMMARY: $md" -ForegroundColor Cyan
Write-Host 'Git repository was not modified or pushed by this audit.' -ForegroundColor Yellow
$bad=@($results | Where-Object {$_.Result -eq 'FAIL'})
if($bad.Count -gt 0){ Write-Host "$($bad.Count) FAILED components. Not release-ready." -ForegroundColor Red; exit 1 }
$warnings=@($results | Where-Object {$_.Result -eq 'WARN'})
if($warnings.Count -gt 0){Write-Host "$($warnings.Count) WARN items. Integration is NOT fully verified." -ForegroundColor Yellow; exit 2}
Write-Host 'All defined checks PASS. End-to-end product acceptance is still separate.' -ForegroundColor Green
exit 0
