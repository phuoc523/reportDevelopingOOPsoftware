$modulePath = Join-Path (Split-Path -Parent $PSScriptRoot) 'LanPayment.Monitor.psm1'
Import-Module $modulePath -Force

Describe 'LanPayment monitor helpers' {
    It 'accepts only private IPv4 addresses for LAN URLs' {
        (Test-LanPaymentIPv4 -Address '192.168.11.37') | Should Be $true
        (Test-LanPaymentIPv4 -Address '127.0.0.1') | Should Be $false
        (Test-LanPaymentIPv4 -Address '169.254.1.2') | Should Be $false
        (Test-LanPaymentIPv4 -Address '2001:db8::1') | Should Be $false
    }

    It 'selects a preferred private address only from the active physical Wi-Fi interface' {
        $adapters = @(
            [pscustomobject]@{ Status = 'Up'; NdisPhysicalMedium = 9; ifIndex = 5 },
            [pscustomobject]@{ Status = 'Up'; NdisPhysicalMedium = 14; ifIndex = 8 }
        )
        $addresses = @(
            [pscustomobject]@{ InterfaceIndex = 5; AddressState = 'Preferred'; IPAddress = '192.168.11.37' },
            [pscustomobject]@{ InterfaceIndex = 8; AddressState = 'Preferred'; IPAddress = '10.0.0.12' }
        )

        (Select-LanPaymentIPv4 -Adapters $adapters -Addresses $addresses) | Should Be '192.168.11.37'
    }

    It 'rejects ambiguous active Wi-Fi adapters or multiple candidate addresses' {
        $adapters = @(
            [pscustomobject]@{ Status = 'Up'; NdisPhysicalMedium = 9; ifIndex = 5 },
            [pscustomobject]@{ Status = 'Up'; NdisPhysicalMedium = 9; ifIndex = 6 }
        )
        $addresses = @(
            [pscustomobject]@{ InterfaceIndex = 5; AddressState = 'Preferred'; IPAddress = '192.168.11.37' }
        )

        (Select-LanPaymentIPv4 -Adapters $adapters -Addresses $addresses) | Should Be $null
    }

    It 'keeps the HTTPS origin and payment URL on port 3443' {
        $urls = Get-LanPaymentUrls -IPv4 '10.1.2.3'

        $urls.PAYMENT_MOCK_WEB_BASE_URL | Should Be 'https://10.1.2.3:3443/'
        $urls.MOCK_PAYMENT_HTTPS_ORIGIN | Should Be 'https://10.1.2.3:3443'
    }

    It 'extracts an IPv4 SAN from certificate DER' {
        $san = [byte[]](0x30, 0x06, 0x87, 0x04, 192, 168, 11, 37)

        (Get-LanPaymentIPv4SanFromRawData -RawData $san) | Should Be '192.168.11.37'
    }

    It 'updates environment values idempotently and preserves unrelated entries' {
        $path = Join-Path $TestDrive 'test.env'
        [System.IO.File]::WriteAllText($path, "KEEP_ME=value`nLAN_HOST=192.168.11.37`nPAYMENT_MOCK_WEB_BASE_URL=https://192.168.11.37:3443/`nMOCK_PAYMENT_HTTPS_ORIGIN=https://192.168.11.37:3443`n", [System.Text.UTF8Encoding]::new($false))

        (Set-LanPaymentEnvironment -Path $path -IPv4 '10.1.2.3') | Should Be $true
        $updated = [System.IO.File]::ReadAllText($path)
        $updated | Should Match 'KEEP_ME=value'
        $updated | Should Match 'LAN_HOST=10.1.2.3'
        $updated | Should Match 'PAYMENT_MOCK_WEB_BASE_URL=https://10.1.2.3:3443/'
        $updated | Should Match 'MOCK_PAYMENT_HTTPS_ORIGIN=https://10.1.2.3:3443'

        (Set-LanPaymentEnvironment -Path $path -IPv4 '10.1.2.3') | Should Be $false
        ([System.IO.File]::ReadAllText($path)) | Should Be $updated
    }
}
