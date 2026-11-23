param(
    [switch]$NoComposeUp,
    [string]$MkcertPath
)

$ErrorActionPreference = 'Stop'
$monitorPath = Join-Path $PSScriptRoot 'Monitor-LanPayment.ps1'
$monitorArguments = @('-Once')
if ($NoComposeUp) { $monitorArguments += '-NoDocker' }
if (-not [string]::IsNullOrWhiteSpace($MkcertPath)) {
    $monitorArguments += @('-MkcertPath', $MkcertPath)
}

& $monitorPath @monitorArguments
