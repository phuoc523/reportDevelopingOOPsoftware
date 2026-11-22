[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'Demo-Compose.ps1')
Initialize-DemoCompose
Invoke-DemoCompose stop
