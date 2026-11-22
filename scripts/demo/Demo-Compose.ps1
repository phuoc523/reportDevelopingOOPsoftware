$script:DemoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$script:DemoEnvFile = Join-Path $script:DemoRoot '.env.demo'
$script:DemoSecretKeys = @('JWT_SECRET','INTERNAL_SERVICE_KEY','REPORTING_SYNC_API_KEY',
    'PAYMENT_CALLBACK_TOKEN','REDIS_PASSWORD')
foreach ($database in 'IDENTITY','CATALOG','ORDER','MACHINE','NOTIFICATION','OPERATION','REPORTING') {
    $script:DemoSecretKeys += "DEMO_${database}_DB_PASSWORD", "DEMO_${database}_DB_ROOT_PASSWORD"
}

function Initialize-DemoCompose {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI unavailable. Start or install Docker Desktop with WSL 2.'
    }
    if (-not (Test-Path -LiteralPath $script:DemoEnvFile)) { throw 'Missing .env.demo.' }
    try { $settings = ConvertFrom-StringData (Get-Content -Raw -LiteralPath $script:DemoEnvFile) }
    catch { throw 'Invalid .env.demo format.' }
    if ($settings.DEMO_PROJECT -cnotmatch '^scentstation-demo-[a-f0-9]{32}$') {
        throw 'Invalid demo project identifier.'
    }
    if (@($settings.Keys | Where-Object { $_ -ne 'DEMO_PROJECT' -and $_ -notin $script:DemoSecretKeys }).Count) {
        throw 'Unexpected settings in .env.demo.'
    }
    foreach ($key in $script:DemoSecretKeys) {
        try { $size = [Convert]::FromBase64String($settings[$key]).Length }
        catch { throw "Missing or invalid demo setting: $key" }
        if ($size -ne 48) { throw "Invalid demo setting length: $key" }
    }
    $context = 'desktop-linux'
    $endpoint = docker context inspect $context --format '{{.Endpoints.docker.Host}}' 2>$null
    if ($LASTEXITCODE -ne 0 -or $endpoint -ne 'npipe:////./pipe/dockerDesktopLinuxEngine') {
        throw 'Local Docker Desktop Linux context is unavailable.'
    }
    $engine = docker --context $context info --format '{{.OSType}}' 2>$null
    if ($LASTEXITCODE -ne 0 -or $engine -ne 'linux') { throw 'Local Docker Desktop Linux Engine unavailable.' }

    $script:DemoNames = $settings
    $script:DemoDocker = @('--context', $context)
    $script:DemoCompose = @('compose','--parallel','1','--project-directory',$script:DemoRoot,
        '--env-file',$script:DemoEnvFile,'--project-name',$settings.DEMO_PROJECT,
        '-f',(Join-Path $script:DemoRoot 'docker-compose.yml'),
        '-f',(Join-Path $PSScriptRoot 'compose.demo.yml'))
}

function Invoke-DemoCompose {
    param([Parameter(ValueFromRemainingArguments)] [string[]] $Arguments)
    $original = @{}
    foreach ($key in $script:DemoNames.Keys) {
        $original[$key] = [Environment]::GetEnvironmentVariable($key, 'Process')
        [Environment]::SetEnvironmentVariable($key, $script:DemoNames[$key], 'Process')
    }
    try {
        docker @script:DemoDocker @script:DemoCompose @Arguments
        if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed: $($Arguments[0])" }
    } finally {
        foreach ($key in $original.Keys) {
            [Environment]::SetEnvironmentVariable($key, $original[$key], 'Process')
        }
    }
}
