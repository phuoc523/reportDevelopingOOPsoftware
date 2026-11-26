param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('STATUS', 'SCAN_WIFI', 'WIFI')]
    [string]$Action,
    [string]$Port = '',
    [string]$Ssid = '',
    [string]$Broker = ''
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)

function Write-JsonResult([hashtable]$Data) {
    $Data | ConvertTo-Json -Depth 6 -Compress
}

function Find-Esp32Port {
    $available = @([System.IO.Ports.SerialPort]::GetPortNames())
    if ($available.Count -eq 0) { return $null }
    try {
        $pnp = Get-CimInstance Win32_PnPEntity -Filter "Name LIKE '%(COM%)'" -ErrorAction SilentlyContinue
        foreach ($dev in $pnp) {
            if ($dev.Name -match '(CP210|Silicon Labs|CH340|CH910|USB-SERIAL|USB Serial|UART|JTAG).*\((COM\d+)\)') {
                $candidate = $Matches[2]
                if ($available -contains $candidate) { return $candidate }
            }
        }
    } catch { }
    return ($available | Sort-Object | Select-Object -First 1)
}

$targetPort = if ([string]::IsNullOrWhiteSpace($Port)) { Find-Esp32Port } else { $Port.Trim() }
if ([string]::IsNullOrWhiteSpace($targetPort)) {
    Write-JsonResult @{
        ok = $false
        connected = $false
        port = $null
        error = 'Chua phat hien ESP32 cam qua cong USB (khong tim thay cong COM).'
    }
    exit 0
}

$expectedType = switch ($Action) {
    'STATUS' { 'STATUS' }
    'SCAN_WIFI' { 'WIFI_LIST' }
    'WIFI' { 'WIFI_RESULT' }
}

$timeoutSec = switch ($Action) {
    'STATUS' { 5 }
    'SCAN_WIFI' { 12 }
    'WIFI' { 30 }
}

$commandLine = switch ($Action) {
    'STATUS' { 'STATUS' }
    'SCAN_WIFI' { 'SCAN_WIFI' }
    'WIFI' {
        $wifiSsid = if (-not [string]::IsNullOrWhiteSpace($env:ESP32_WIFI_SSID)) { $env:ESP32_WIFI_SSID.Trim() } else { $Ssid.Trim() }
        $wifiPass = if ($null -ne $env:ESP32_WIFI_PASSWORD) { $env:ESP32_WIFI_PASSWORD } else { '' }
        $brokerHost = if (-not [string]::IsNullOrWhiteSpace($env:ESP32_MQTT_BROKER)) { $env:ESP32_MQTT_BROKER.Trim() } else { $Broker.Trim() }
        if ([string]::IsNullOrWhiteSpace($brokerHost)) {
            "WIFI $wifiSsid|$wifiPass"
        } else {
            "WIFI $wifiSsid|$wifiPass|$brokerHost"
        }
    }
}

$sp = $null
try {
    $sp = New-Object System.IO.Ports.SerialPort $targetPort, 115200, 'None', 8, 'One'
    $sp.DtrEnable = $false
    $sp.RtsEnable = $false
    $sp.ReadTimeout = 500
    $sp.WriteTimeout = 1500
    $sp.NewLine = "`n"
    $sp.Encoding = [System.Text.UTF8Encoding]::new($false)
    $sp.Open()
    Start-Sleep -Milliseconds 150
    $sp.DiscardInBuffer()
    $sp.WriteLine($commandLine)

    $deadline = (Get-Date).AddSeconds($timeoutSec)
    $payload = $null
    $recentLines = New-Object System.Collections.Generic.List[string]
    while ((Get-Date) -lt $deadline) {
        try {
            $line = $sp.ReadLine()
            if ($line) {
                $trimmed = $line.Trim()
                if ($trimmed.Length -gt 0) {
                    $recentLines.Add($trimmed)
                    if ($recentLines.Count -gt 5) { $recentLines.RemoveAt(0) }
                }
                $jsonIdx = $trimmed.IndexOf('@@JSON:')
                if ($jsonIdx -ge 0) {
                    $rawJson = $trimmed.Substring($jsonIdx + 7)
                    $parsed = $rawJson | ConvertFrom-Json
                    if ($parsed.type -eq $expectedType) {
                        $payload = $parsed
                        break
                    }
                }
            }
        } catch [System.TimeoutException] {
            continue
        }
    }

    if ($null -eq $payload) {
        $tail = if ($recentLines.Count -gt 0) { ' [Log: ' + ($recentLines -join ' | ') + ']' } else { '' }
        Write-JsonResult @{
            ok = $false
            connected = $true
            port = $targetPort
            error = "ESP32 tren $targetPort khong phan hoi lenh $Action.$tail"
        }
        exit 0
    }

    $resultOk = if ($Action -eq 'WIFI') { [bool]$payload.ok } else { $true }
    $result = @{
        ok = $resultOk
        connected = $true
        port = $targetPort
        data = $payload
    }
    if (-not $resultOk -and $payload.error) {
        $result.error = [string]$payload.error
    }
    Write-JsonResult $result
} catch {
    Write-JsonResult @{
        ok = $false
        connected = $false
        port = $targetPort
        error = "Khong mo duoc cong $targetPort (dam bao Arduino Serial Monitor da dong): $($_.Exception.Message)"
    }
} finally {
    if ($null -ne $sp -and $sp.IsOpen) {
        try { $sp.Close() } catch { }
    }
    if ($null -ne $sp) {
        try { $sp.Dispose() } catch { }
    }
}
