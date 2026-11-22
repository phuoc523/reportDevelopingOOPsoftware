[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Demo-Compose.ps1')
$envFile = $DemoEnvFile
if (Test-Path -LiteralPath $envFile) {
    Write-Host 'Existing .env.demo preserved.'
    return
}

function New-DemoSecret {
    $bytes = New-Object byte[] 48
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}

$project = 'scentstation-demo-' + [Guid]::NewGuid().ToString('N')
$lines = @("DEMO_PROJECT=$project")
foreach ($key in $DemoSecretKeys) {
    $lines += "$key=$(New-DemoSecret)"
}
$file = [IO.File]::Open($envFile, [IO.FileMode]::CreateNew)
try {
    $writer = New-Object IO.StreamWriter($file, [Text.UTF8Encoding]::new($false))
    try { $writer.WriteLine(($lines -join "`n")) } finally { $writer.Dispose() }
} finally { $file.Dispose() }
Write-Host 'Created isolated .env.demo.'
