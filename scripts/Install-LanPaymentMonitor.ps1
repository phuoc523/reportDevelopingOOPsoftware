param([string]$MkcertPath)

$ErrorActionPreference = 'Stop'
$taskName = 'ScentStation-LanPayment-Monitor'
$monitorPath = Join-Path $PSScriptRoot 'Monitor-LanPayment.ps1'
if (-not (Test-Path -LiteralPath $monitorPath -PathType Leaf)) {
    throw "Monitor script was not found: $monitorPath"
}

$modulePath = Join-Path $PSScriptRoot 'LanPayment.Monitor.psm1'
Import-Module $modulePath -Force
if ([string]::IsNullOrWhiteSpace($MkcertPath)) {
    $environmentValues = Get-LanPaymentEnvironmentValues -Path (Join-Path (Split-Path -Parent $PSScriptRoot) '.env')
    $MkcertPath = $environmentValues.MKCERT_PATH
}
$resolvedMkcert = Resolve-LanPaymentMkcert -ExecutablePath $MkcertPath
$windowsPowerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$arguments = '-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -ExecutionPolicy Bypass -File "{0}" -MkcertPath "{1}"' -f $monitorPath, $resolvedMkcert
$action = New-ScheduledTaskAction -Execute $windowsPowerShell -Argument $arguments
$trigger = New-ScheduledTaskTrigger -AtLogOn -User "$env:USERDOMAIN\$env:USERNAME"
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew `
    -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1) -ExecutionTimeLimit ([TimeSpan]::Zero)
$principal = New-ScheduledTaskPrincipal -UserId "$env:USERDOMAIN\$env:USERNAME" -LogonType Interactive -RunLevel Limited
$task = New-ScheduledTask -Action $action -Trigger $trigger -Settings $settings -Principal $principal `
    -Description 'Monitors the active Windows Wi-Fi IPv4 and safely updates ScentStation Mock Payment HTTPS configuration.'

Register-ScheduledTask -TaskName $taskName -InputObject $task -Force | Out-Null
Start-ScheduledTask -TaskName $taskName
Write-Host "Installed and started hidden scheduled task '$taskName' for the current Windows user."
Write-Host "Monitor log: $env:LOCALAPPDATA\ScentStation\LanPaymentMonitor\monitor.log"
