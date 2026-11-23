Set-StrictMode -Version Latest

function Test-LanPaymentIPv4 {
    param([Parameter(Mandatory = $true)][string]$Address)

    $parsed = $null
    if (-not [System.Net.IPAddress]::TryParse($Address, [ref]$parsed) -or
        $parsed.AddressFamily -ne [System.Net.Sockets.AddressFamily]::InterNetwork) {
        return $false
    }

    $bytes = $parsed.GetAddressBytes()
    if ($bytes[0] -eq 10) { return $true }
    if ($bytes[0] -eq 172 -and $bytes[1] -ge 16 -and $bytes[1] -le 31) { return $true }
    if ($bytes[0] -eq 192 -and $bytes[1] -eq 168) { return $true }
    return $false
}

function Select-LanPaymentIPv4 {
    param(
        [Parameter(Mandatory = $true)][object[]]$Adapters,
        [Parameter(Mandatory = $true)][object[]]$Addresses
    )

    $wifiAdapters = @($Adapters | Where-Object {
        $_.Status -eq 'Up' -and [int]$_.NdisPhysicalMedium -eq 9
    })
    if ($wifiAdapters.Count -ne 1) { return $null }

    $adapter = $wifiAdapters[0]
    if ($adapter.PSObject.Properties.Name -contains 'ifIndex') {
        $adapterIndex = $adapter.ifIndex
    }
    else {
        $adapterIndex = $adapter.InterfaceIndex
    }
    $ips = @($Addresses | Where-Object {
        [int]$_.InterfaceIndex -eq [int]$adapterIndex -and
        $_.AddressState -eq 'Preferred' -and
        (Test-LanPaymentIPv4 -Address ([string]$_.IPAddress))
    } | ForEach-Object { [string]$_.IPAddress } | Sort-Object -Unique)

    if ($ips.Count -ne 1) { return $null }
    return $ips[0]
}

function Get-LanPaymentIPv4 {
    $adapters = @(Get-NetAdapter -Physical -ErrorAction Stop)
    $addresses = @()
    foreach ($adapter in $adapters) {
        if ([int]$adapter.NdisPhysicalMedium -ne 9 -or $adapter.Status -ne 'Up') { continue }
        $addresses += @(Get-NetIPAddress -InterfaceIndex $adapter.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue)
    }
    return Select-LanPaymentIPv4 -Adapters $adapters -Addresses $addresses
}

function Read-LanPaymentDerLength {
    param(
        [Parameter(Mandatory = $true)][byte[]]$Data,
        [Parameter(Mandatory = $true)][ref]$Offset
    )

    if ($Offset.Value -ge $Data.Length) { throw 'Malformed certificate SAN length.' }
    $first = [int]$Data[$Offset.Value]
    $Offset.Value++
    if ($first -lt 128) { return $first }

    $count = $first -band 127
    if ($count -eq 0 -or $count -gt 4 -or ($Offset.Value + $count) -gt $Data.Length) {
        throw 'Malformed certificate SAN length.'
    }
    $length = 0
    for ($i = 0; $i -lt $count; $i++) {
        $length = ($length * 256) + [int]$Data[$Offset.Value]
        $Offset.Value++
    }
    return $length
}

function Get-LanPaymentIPv4SanFromRawData {
    param([Parameter(Mandatory = $true)][byte[]]$RawData)

    if ($RawData.Length -lt 2 -or $RawData[0] -ne 0x30) { throw 'Malformed certificate SAN sequence.' }
    $offset = 1
    $offsetRef = [ref]$offset
    $sequenceLength = Read-LanPaymentDerLength -Data $RawData -Offset $offsetRef
    $sequenceEnd = $offset + $sequenceLength
    if ($sequenceEnd -gt $RawData.Length) { throw 'Malformed certificate SAN sequence length.' }

    $results = @()
    while ($offset -lt $sequenceEnd) {
        $tag = [int]$RawData[$offset]
        $offset++
        $length = Read-LanPaymentDerLength -Data $RawData -Offset $offsetRef
        if (($offset + $length) -gt $sequenceEnd) { throw 'Malformed certificate SAN entry.' }
        if ($tag -eq 0x87 -and $length -eq 4) {
            $ipBytes = New-Object byte[] 4
            [Array]::Copy($RawData, $offset, $ipBytes, 0, 4)
            $results += ([System.Net.IPAddress]::new($ipBytes)).ToString()
        }
        $offset += $length
    }
    return @($results | Sort-Object -Unique)
}

function Get-LanPaymentCertificateInfo {
    param([Parameter(Mandatory = $true)][string]$CertificatePath)

    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($CertificatePath)
    try {
        $san = $certificate.Extensions | Where-Object { $_.Oid.Value -eq '2.5.29.17' } | Select-Object -First 1
        $ipAddresses = @()
        if ($san) {
            $ipAddresses = @(Get-LanPaymentIPv4SanFromRawData -RawData $san.RawData)
        }
        return [pscustomobject]@{
            Subject = $certificate.Subject
            Issuer = $certificate.Issuer
            NotBefore = $certificate.NotBefore
            NotAfter = $certificate.NotAfter
            IPv4San = $ipAddresses
        }
    }
    finally {
        $certificate.Dispose()
    }
}

function Get-LanPaymentUrls {
    param([Parameter(Mandatory = $true)][string]$IPv4)
    if (-not (Test-LanPaymentIPv4 -Address $IPv4)) {
        throw "Not a valid private Wi-Fi IPv4 address: $IPv4"
    }
    return [pscustomobject]@{
        LAN_HOST = $IPv4
        PAYMENT_MOCK_WEB_BASE_URL = "https://${IPv4}:3443/"
        MOCK_PAYMENT_HTTPS_ORIGIN = "https://${IPv4}:3443"
    }
}

function Get-LanPaymentEnvironmentValues {
    param([Parameter(Mandatory = $true)][string]$Path)

    $values = @{
        LAN_HOST = ''
        PAYMENT_MOCK_WEB_BASE_URL = ''
        MOCK_PAYMENT_HTTPS_ORIGIN = ''
        MKCERT_PATH = ''
    }
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "Missing root .env file: $Path"
    }
    foreach ($line in [System.IO.File]::ReadAllLines($Path)) {
        if ($line -match '^\s*(LAN_HOST|PAYMENT_MOCK_WEB_BASE_URL|MOCK_PAYMENT_HTTPS_ORIGIN|MKCERT_PATH)\s*=\s*(.*)$') {
            $values[$matches[1]] = $matches[2]
        }
    }
    return $values
}

function Set-LanPaymentEnvironment {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$IPv4
    )

    $urls = Get-LanPaymentUrls -IPv4 $IPv4
    $updates = [ordered]@{
        LAN_HOST = $urls.LAN_HOST
        PAYMENT_MOCK_WEB_BASE_URL = $urls.PAYMENT_MOCK_WEB_BASE_URL
        MOCK_PAYMENT_HTTPS_ORIGIN = $urls.MOCK_PAYMENT_HTTPS_ORIGIN
    }
    $lines = [System.Collections.Generic.List[string]]::new()
    $sourceLines = [System.IO.File]::ReadAllLines($Path)
    foreach ($key in $updates.Keys) {
        $matchesForKey = @($sourceLines | Where-Object { $_ -match ("^\s*" + [regex]::Escape($key) + "\s*=") })
        if ($matchesForKey.Count -gt 1) { throw "Duplicate $key entries in .env; refusing an ambiguous update." }
    }

    $seen = @{}
    foreach ($line in $sourceLines) {
        $replaced = $false
        foreach ($key in $updates.Keys) {
            if ($line -match ("^\s*" + [regex]::Escape($key) + "\s*=")) {
                $lines.Add("$key=$($updates[$key])")
                $seen[$key] = $true
                $replaced = $true
                break
            }
        }
        if (-not $replaced) { $lines.Add($line) }
    }
    foreach ($key in $updates.Keys) {
        if (-not $seen.ContainsKey($key)) { $lines.Add("$key=$($updates[$key])") }
    }

    $newContent = [string]::Join([Environment]::NewLine, $lines.ToArray()) + [Environment]::NewLine
    $oldContent = [System.IO.File]::ReadAllText($Path)
    if ($newContent -ceq $oldContent) { return $false }

    $temporaryPath = "$Path.$([guid]::NewGuid().ToString('N')).tmp"
    $backupPath = "$Path.$([guid]::NewGuid().ToString('N')).backup"
    try {
        [System.IO.File]::WriteAllText($temporaryPath, $newContent, [System.Text.UTF8Encoding]::new($false))
        [System.IO.File]::Replace($temporaryPath, $Path, $backupPath, $true)
        return $true
    }
    finally {
        if (Test-Path -LiteralPath $temporaryPath) { Remove-Item -LiteralPath $temporaryPath -Force }
        if (Test-Path -LiteralPath $backupPath) { Remove-Item -LiteralPath $backupPath -Force }
    }
}

function Resolve-LanPaymentMkcert {
    param([string]$ExecutablePath)

    if (-not [string]::IsNullOrWhiteSpace($ExecutablePath)) {
        if (-not (Test-Path -LiteralPath $ExecutablePath -PathType Leaf)) {
            throw "Configured mkcert executable does not exist: $ExecutablePath"
        }
        return $ExecutablePath
    }
    $command = Get-Command mkcert.exe, mkcert -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $command) { throw 'mkcert was not found on PATH. Set MKCERT_PATH once or add mkcert to the Windows user PATH.' }
    return $command.Source
}

function Ensure-LanPaymentCertificate {
    param(
        [Parameter(Mandatory = $true)][string]$RepositoryRoot,
        [Parameter(Mandatory = $true)][string]$IPv4,
        [string]$MkcertPath
    )

    $tlsDirectory = Join-Path $RepositoryRoot 'infra\docker\tls'
    $certificatePath = Join-Path $tlsDirectory 'mock-payment.crt'
    $keyPath = Join-Path $tlsDirectory 'mock-payment.key'
    if (-not (Test-Path -LiteralPath $certificatePath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $keyPath -PathType Leaf)) {
        throw 'Both existing Mock Payment TLS files are required; monitor will not create or replace a Root CA.'
    }

    $now = Get-Date
    $currentInfo = Get-LanPaymentCertificateInfo -CertificatePath $certificatePath
    if ($currentInfo.IPv4San -contains $IPv4 -and $currentInfo.NotBefore -le $now -and $currentInfo.NotAfter -gt $now.AddDays(14)) {
        return [pscustomobject]@{ Changed = $false; PreviousSan = @($currentInfo.IPv4San); CurrentSan = @($currentInfo.IPv4San) }
    }

    if ([string]::IsNullOrWhiteSpace($MkcertPath)) {
        $environmentValues = Get-LanPaymentEnvironmentValues -Path (Join-Path $RepositoryRoot '.env')
        $MkcertPath = $environmentValues.MKCERT_PATH
    }
    $mkcert = Resolve-LanPaymentMkcert -ExecutablePath $MkcertPath
    $carootOutput = & $mkcert -CAROOT 2>$null
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($carootOutput -join ''))) {
        throw 'Could not query the existing mkcert Root CA directory.'
    }
    $caroot = ($carootOutput -join '').Trim()
    $rootCertificatePath = Join-Path $caroot 'rootCA.pem'
    $rootKeyPath = Join-Path $caroot 'rootCA-key.pem'
    if (-not (Test-Path -LiteralPath $rootCertificatePath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $rootKeyPath -PathType Leaf)) {
        throw "The existing mkcert Root CA and key were not both found under $caroot; refusing to create a new Root CA."
    }

    $rootCertificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($rootCertificatePath)
    $previousCaroot = $env:CAROOT
    $token = [guid]::NewGuid().ToString('N')
    $temporaryCertificate = Join-Path $tlsDirectory "mock-payment.$token.crt"
    $temporaryKey = Join-Path $tlsDirectory "mock-payment.$token.key"
    $backupCertificate = Join-Path $tlsDirectory "mock-payment.$token.crt.backup"
    $backupKey = Join-Path $tlsDirectory "mock-payment.$token.key.backup"
    try {
        $env:CAROOT = $caroot
        $null = & $mkcert -cert-file $temporaryCertificate -key-file $temporaryKey $IPv4 2>&1
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $temporaryCertificate) -or
            -not (Test-Path -LiteralPath $temporaryKey)) {
            throw 'mkcert failed to create the new leaf certificate.'
        }

        $newInfo = Get-LanPaymentCertificateInfo -CertificatePath $temporaryCertificate
        if ($newInfo.Issuer -ne $rootCertificate.Subject -or
            -not ($newInfo.IPv4San -contains $IPv4) -or
            $newInfo.NotBefore -gt (Get-Date) -or
            $newInfo.NotAfter -le (Get-Date).AddDays(14)) {
            throw 'Generated leaf certificate has the wrong issuer, SAN, or validity.'
        }

        Copy-Item -LiteralPath $certificatePath -Destination $backupCertificate
        Copy-Item -LiteralPath $keyPath -Destination $backupKey
        Move-Item -LiteralPath $temporaryCertificate -Destination $certificatePath -Force
        Move-Item -LiteralPath $temporaryKey -Destination $keyPath -Force
        return [pscustomobject]@{
            Changed = $true
            PreviousSan = @($currentInfo.IPv4San)
            CurrentSan = @($newInfo.IPv4San)
        }
    }
    catch {
        if (Test-Path -LiteralPath $backupCertificate) {
            Copy-Item -LiteralPath $backupCertificate -Destination $certificatePath -Force
        }
        if (Test-Path -LiteralPath $backupKey) {
            Copy-Item -LiteralPath $backupKey -Destination $keyPath -Force
        }
        throw
    }
    finally {
        $rootCertificate.Dispose()
        if ($null -eq $previousCaroot) { Remove-Item Env:CAROOT -ErrorAction SilentlyContinue }
        else { $env:CAROOT = $previousCaroot }
        foreach ($path in @($temporaryCertificate, $temporaryKey, $backupCertificate, $backupKey)) {
            if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
        }
    }
}

Export-ModuleMember -Function Test-LanPaymentIPv4, Select-LanPaymentIPv4, Get-LanPaymentIPv4, Get-LanPaymentIPv4SanFromRawData, Get-LanPaymentCertificateInfo, Get-LanPaymentUrls, Get-LanPaymentEnvironmentValues, Set-LanPaymentEnvironment, Resolve-LanPaymentMkcert, Ensure-LanPaymentCertificate
