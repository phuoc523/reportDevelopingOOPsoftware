param(
    [switch]$Once,
    [switch]$DryRun,
    [switch]$NoDocker,
    [string]$MkcertPath
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $repositoryRoot '.env'
$logDirectory = Join-Path $env:LOCALAPPDATA 'ScentStation\LanPaymentMonitor'
$logPath = Join-Path $logDirectory 'monitor.log'
$statePath = Join-Path $logDirectory 'state.json'
$composeFile = Join-Path $repositoryRoot 'docker-compose.yml'
$modulePath = Join-Path $PSScriptRoot 'LanPayment.Monitor.psm1'
Import-Module $modulePath -Force

New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

function Write-MonitorLog {
    param([string]$Message, [ValidateSet('INFO', 'WARN', 'ERROR')][string]$Level = 'INFO')

    $line = '{0} [{1}] {2}' -f (Get-Date).ToString('yyyy-MM-dd HH:mm:ss.fffK'), $Level, $Message
    Add-Content -LiteralPath $logPath -Value $line -Encoding UTF8
    if ((Get-Item -LiteralPath $logPath).Length -gt 1048576) {
        $archive = "$logPath.1"
        if (Test-Path -LiteralPath $archive) { Remove-Item -LiteralPath $archive -Force }
        Move-Item -LiteralPath $logPath -Destination $archive
    }
}

function Invoke-Compose {
    param([string[]]$Arguments)

    Push-Location $repositoryRoot
    try {
        $output = @(& docker compose --profile https -f $composeFile @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            Write-MonitorLog -Level ERROR -Message ("Docker Compose action failed (exit {0}): {1}" -f $exitCode, ($Arguments -join ' '))
            return $false
        }
        return $true
    }
    finally {
        Pop-Location
    }
}

function Get-RunningComposeServices {
    param([string[]]$Services)

    $running = @()
    foreach ($service in $Services) {
        Push-Location $repositoryRoot
        try {
            $containerOutput = @(& docker compose --profile https -f $composeFile ps -q $service 2>&1)
            $composeExitCode = $LASTEXITCODE
        }
        finally {
            Pop-Location
        }
        if ($composeExitCode -ne 0) {
            throw "Could not inspect Compose service '$service': $($containerOutput -join ' ')"
        }
        $containerId = $containerOutput | Select-Object -First 1
        if (-not [string]::IsNullOrWhiteSpace([string]$containerId)) {
            $statusOutput = @(& docker inspect --format '{{.State.Status}}' ([string]$containerId) 2>&1)
            if ($LASTEXITCODE -ne 0) {
                throw "Could not inspect container for Compose service '$service': $($statusOutput -join ' ')"
            }
            $status = $statusOutput | Select-Object -First 1
            if ([string]$status -eq 'running') { $running += $service }
        }
    }
    return @($running)
}

function Test-OrderSchemaSafeForRestart {
    $query = "SELECT (SELECT COUNT(*) FROM information_schema.tables WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('orders','order_items','payments','payment_callback_events','payment_event_outbox','dispensing_commands','dispense_event_outbox','transaction_reviews')), (SELECT COUNT(*) FROM information_schema.columns WHERE TABLE_SCHEMA=DATABASE() AND ((TABLE_NAME='orders' AND COLUMN_NAME='order_type') OR (TABLE_NAME='payment_event_outbox' AND COLUMN_NAME IN ('tenant_id','machine_id','slot_id','product_id'))));"
    $shellCommand = 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --execute "' + $query + '"'
    Push-Location $repositoryRoot
    try {
        $output = @(& docker compose -f $composeFile exec -T order-db sh -c $shellCommand 2>$null)
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    if ($exitCode -ne 0 -or $output.Count -eq 0) { return $false }
    $parts = ([string]$output[0]).Trim() -split '\s+'
    return $parts.Count -eq 2 -and $parts[0] -eq '8' -and $parts[1] -eq '5'
}

function Read-MonitorState {
    if (-not (Test-Path -LiteralPath $statePath -PathType Leaf)) { return $null }
    try { return (Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json) }
    catch {
        Write-MonitorLog -Level ERROR -Message "Could not read monitor state; no configuration will be changed. $($_.Exception.Message)"
        throw 'Monitor state is unreadable; refusing to assume configuration was applied.'
    }
}

function Save-MonitorState {
    param(
        [string]$IPv4,
        [switch]$PendingApply,
        [switch]$PendingCertificateReload
    )

    $state = [pscustomobject]@{
        lanHost = $IPv4
        appliedAt = (Get-Date).ToString('o')
        pendingApply = [bool]$PendingApply
        pendingCertificateReload = [bool]$PendingCertificateReload
    }
    $temporaryPath = "$statePath.tmp"
    [System.IO.File]::WriteAllText($temporaryPath, ($state | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryPath -Destination $statePath -Force
}

function Invoke-LanPaymentReconcile {
    param(
        [Parameter(Mandatory = $true)][string]$IPv4,
        [switch]$NoDocker
    )

    $urls = Get-LanPaymentUrls -IPv4 $IPv4
    $values = Get-LanPaymentEnvironmentValues -Path $envPath
    $certificatePath = Join-Path $repositoryRoot 'infra\docker\tls\mock-payment.crt'
    $certificateInfo = Get-LanPaymentCertificateInfo -CertificatePath $certificatePath
    $state = Read-MonitorState
    $oldIPv4 = if ($null -ne $state -and -not [string]::IsNullOrWhiteSpace($state.lanHost)) {
        [string]$state.lanHost
    }
    else {
        [string]$values.LAN_HOST
    }
    $envMatches = $values.LAN_HOST -eq $urls.LAN_HOST -and
        $values.PAYMENT_MOCK_WEB_BASE_URL -eq $urls.PAYMENT_MOCK_WEB_BASE_URL -and
        $values.MOCK_PAYMENT_HTTPS_ORIGIN -eq $urls.MOCK_PAYMENT_HTTPS_ORIGIN
    $networkConfigChanged = -not $envMatches
    $certificateMatches = $certificateInfo.IPv4San -contains $IPv4 -and $certificateInfo.NotAfter -gt (Get-Date).AddDays(14)

    $pendingApply = $null -ne $state -and
        ($state.PSObject.Properties.Name -contains 'pendingApply') -and
        [bool]$state.pendingApply
    $pendingCertificateReload = $null -ne $state -and
        ($state.PSObject.Properties.Name -contains 'pendingCertificateReload') -and
        [bool]$state.pendingCertificateReload
    if ($null -eq $state -and $envMatches -and $certificateMatches -and -not $NoDocker) {
        Save-MonitorState -IPv4 $IPv4
        Write-MonitorLog "Initial configuration already matches Wi-Fi IPv4 $IPv4; no services were restarted."
        return
    }
    if ($envMatches -and $certificateMatches -and $null -ne $state -and
        $state.lanHost -eq $IPv4 -and -not $pendingApply -and
        -not $pendingCertificateReload -and -not $NoDocker) { return }
    Write-MonitorLog "Reconciling Wi-Fi IPv4: old='$oldIPv4', new='$IPv4'."
    Write-MonitorLog ("Certificate SAN before update: [{0}]." -f ($certificateInfo.IPv4San -join ','))

    $running = @()
    if (-not $NoDocker) {
        $running = @(Get-RunningComposeServices -Services @('order-service', 'api-gateway', 'mock-payment-web-https'))
    }
    $runningOrder = $running -contains 'order-service'
    if (-not $NoDocker -and $networkConfigChanged -and $runningOrder) {
        if (-not (Test-OrderSchemaSafeForRestart)) {
            throw 'Order DB schema preflight did not confirm all initialized tables and columns; no configuration or certificate was changed.'
        }
        Write-MonitorLog 'Read-only schema preflight passed; Order Service startup SQL will only encounter existing tables and columns.'
    }

    if ($DryRun) {
        Write-MonitorLog "Dry run: stable Wi-Fi IPv4 would change to $IPv4; services considered: $($running -join ', ')."
        return
    }

    $environmentBackup = Join-Path $logDirectory "env.$([guid]::NewGuid().ToString('N')).backup"
    Copy-Item -LiteralPath $envPath -Destination $environmentBackup
    try {
        $envChanged = Set-LanPaymentEnvironment -Path $envPath -IPv4 $IPv4
        $certResult = Ensure-LanPaymentCertificate -RepositoryRoot $repositoryRoot -IPv4 $IPv4 -MkcertPath $MkcertPath
    }
    catch {
        Copy-Item -LiteralPath $environmentBackup -Destination $envPath -Force
        throw
    }
    finally {
        if (Test-Path -LiteralPath $environmentBackup) { Remove-Item -LiteralPath $environmentBackup -Force }
    }
    Write-MonitorLog ("Certificate SAN after update: [{0}]." -f ($certResult.CurrentSan -join ','))
    if ($certResult.Changed) {
        Write-MonitorLog ("Certificate SAN changed from [{0}] to [{1}]." -f ($certResult.PreviousSan -join ','), ($certResult.CurrentSan -join ','))
    }

    if ($envChanged) { Write-MonitorLog "Updated LAN_HOST and HTTPS origins for IPv4 $IPv4; HTTPS port remains 3443." }
    $certificateReloadNeeded = $pendingCertificateReload -or $certResult.Changed
    Save-MonitorState -IPv4 $IPv4 -PendingApply -PendingCertificateReload:$certificateReloadNeeded
    if ($NoDocker) {
        Write-MonitorLog 'Docker actions were skipped by request; the background monitor will apply the change on its next reconciliation.'
        return
    }

    $backendTargets = @()
    if ($networkConfigChanged) {
        $backendTargets = @($running | Where-Object { $_ -in @('order-service', 'api-gateway') })
    }
    if ($backendTargets.Count -gt 0) {
        if (-not (Invoke-Compose -Arguments (@('up', '-d', '--no-build', '--no-deps', '--wait', '--wait-timeout', '90') + $backendTargets))) {
            throw 'Could not apply the new configuration to running backend services.'
        }
        Write-MonitorLog "Updated running backend services: $($backendTargets -join ', ')."
    }

    if ($running -contains 'mock-payment-web-https' -and $certificateReloadNeeded) {
        if (-not (Invoke-Compose -Arguments @('exec', '-T', 'mock-payment-web-https', 'nginx', '-t')) -or
            -not (Invoke-Compose -Arguments @('exec', '-T', 'mock-payment-web-https', 'nginx', '-s', 'reload'))) {
            throw 'Nginx rejected or failed to reload the new certificate.'
        }
        Save-MonitorState -IPv4 $IPv4 -PendingApply -PendingCertificateReload:$false
        Write-MonitorLog 'Validated and reloaded the new certificate in mock-payment-web-https; container was not recreated.'
    }
    elseif ($certificateReloadNeeded) {
        Write-MonitorLog 'HTTPS Nginx service is not running; the new certificate will be used when it next starts.'
    }

    try {
        $response = Invoke-WebRequest -Uri $urls.PAYMENT_MOCK_WEB_BASE_URL -UseBasicParsing -TimeoutSec 10
        Write-MonitorLog "HTTPS health check returned HTTP $([int]$response.StatusCode) on port 3443."
    }
    catch {
        Write-MonitorLog -Level ERROR -Message "HTTPS health check failed for the new origin; verify Root CA trust and network reachability. $($_.Exception.Message)"
        throw 'HTTPS health check failed; the monitor will retry reconciliation.'
    }

    Save-MonitorState -IPv4 $IPv4
    Write-MonitorLog "Wi-Fi IPv4 reconcile completed for $IPv4."
}

function Get-StableLanPaymentIPv4 {
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $first = Get-LanPaymentIPv4
        if ([string]::IsNullOrWhiteSpace($first)) { return $null }
        Start-Sleep -Seconds 2
        $second = Get-LanPaymentIPv4
        if ($first -eq $second) { return $second }
    }
    return $null
}

if ($Once) {
    $ip = Get-StableLanPaymentIPv4
    if (-not $ip) {
        Write-MonitorLog -Level WARN -Message 'No single stable private IPv4 was found on an active physical Wi-Fi adapter; existing configuration was retained.'
        throw 'No stable private Wi-Fi IPv4 address is available.'
    }
    Invoke-LanPaymentReconcile -IPv4 $ip -NoDocker:$NoDocker
    return
}

$listenerSource = @'
using System;
using System.Net.NetworkInformation;
using System.Threading;
public sealed class ScentStationNetworkChangeSignal : IDisposable
{
    private readonly AutoResetEvent signal = new AutoResetEvent(false);
    public ScentStationNetworkChangeSignal()
    {
        NetworkChange.NetworkAddressChanged += OnNetworkChange;
        NetworkChange.NetworkAvailabilityChanged += OnAvailabilityChange;
    }
    private void OnNetworkChange(object sender, EventArgs args) { signal.Set(); }
    private void OnAvailabilityChange(object sender, NetworkAvailabilityEventArgs args) { signal.Set(); }
    public bool WaitOne(int milliseconds) { return signal.WaitOne(milliseconds); }
    public void Dispose()
    {
        NetworkChange.NetworkAddressChanged -= OnNetworkChange;
        NetworkChange.NetworkAvailabilityChanged -= OnAvailabilityChange;
        signal.Dispose();
    }
}
'@
Add-Type -TypeDefinition $listenerSource -ErrorAction Stop
$listener = New-Object ScentStationNetworkChangeSignal
$lastNoWifiLog = [datetime]::MinValue
Write-MonitorLog 'Background Wi-Fi monitor started; listening for address/availability events with a 30-second recovery check.'
try {
    $reconcile = $true
    $lastFailedIPv4 = $null
    $lastFailureAt = [datetime]::MinValue
    while ($true) {
        if (-not $reconcile) {
            $networkEvent = $listener.WaitOne(30000)
            if ($networkEvent) {
                Start-Sleep -Seconds 2
                $debounceDeadline = (Get-Date).AddSeconds(5)
                while ((Get-Date) -lt $debounceDeadline -and $listener.WaitOne(500)) { }
            }
            $reconcile = $true
        }
        $reconcile = $false
        $ip = $null
        try {
            $ip = Get-StableLanPaymentIPv4
            if ([string]::IsNullOrWhiteSpace($ip)) {
                if (((Get-Date) - $lastNoWifiLog).TotalMinutes -ge 5) {
                    Write-MonitorLog -Level WARN -Message 'No single stable private IPv4 is available on an active Wi-Fi adapter; retaining the last configuration.'
                    $lastNoWifiLog = Get-Date
                }
                continue
            }
            if ($ip -eq $lastFailedIPv4 -and ((Get-Date) - $lastFailureAt).TotalMinutes -lt 2) {
                continue
            }
            Invoke-LanPaymentReconcile -IPv4 $ip
            $lastFailedIPv4 = $null
        }
        catch {
            Write-MonitorLog -Level ERROR -Message $_.Exception.Message
            $lastFailedIPv4 = $ip
            $lastFailureAt = Get-Date
        }
    }
}
finally {
    $listener.Dispose()
}
