$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Lite-Compose.ps1')
if (-not (Test-Path -LiteralPath $DemoEnvFile)) { Write-Host 'LITE has not been configured.'; return }
Initialize-Lite
Invoke-DemoCompose stop @LiteServices
