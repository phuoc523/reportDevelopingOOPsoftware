# Mock Payment Web

The kiosk renders two QR codes to keep navigation and payment identity separate:

- The first encodes only the HTTPS site root, `https://<LAN_HOST>:3443/`.
- The second encodes only `{"type":"SCENTSTATION_PAYMENT","paymentReference":"PAY-..."}`.

The Mock Payment home page scans the second QR, validates its shape, then sends
the reference in the body of `POST /api/v1/payments/public/lookup`. It does not
display or ask customers to type the reference. Each browser tab keeps its own
payment and order state. Confirmations are sent through
`POST /api/v1/payments/public/confirm`; the backend continues to validate the
payment, order, amount, currency, expiry, status, and callback idempotency.
Callback secrets remain server-side.

Live `getUserMedia()` camera access requires a secure context. A page opened at
an HTTP IPv4 LAN address is not a secure context, so Android/iOS browsers can
block live camera access there. The included HTTPS Docker profile serves the
frontend and proxies `/api/*` through the same origin, so no frontend secret or
cross-origin API address is needed.

## One-time Windows setup

The automatic monitor requires Windows PowerShell, Docker Desktop, the existing
mkcert Root CA, and the mkcert executable. Keep the existing Root CA certificate
and private key in the Windows user's mkcert `CAROOT`; the monitor refuses to
create a new Root CA. Do not commit the generated certificate or private key.

For a first installation only, if the leaf certificate files do not exist yet,
generate an initial leaf certificate for the active Wi-Fi IPv4 using the
existing mkcert CA:

```powershell
mkcert -cert-file infra/docker/tls/mock-payment.crt `
  -key-file infra/docker/tls/mock-payment.key `
  <CURRENT_WIFI_IPV4>
```

If mkcert is not on the Windows user `PATH`, provide its full executable path
when installing the monitor or set `MKCERT_PATH` in the ignored root `.env`.
The monitor never runs `mkcert -install`; it reuses the existing Root CA.

Run the one-time setup from the repository root in Windows PowerShell:

```powershell
.\scripts\Install-LanPaymentMonitor.ps1 -MkcertPath "C:\path\to\mkcert.exe"
```

Omit `-MkcertPath` when mkcert is already on `PATH` or `MKCERT_PATH` is set in
`.env`. The installer creates a hidden per-user Scheduled Task that starts at
Windows logon. It does not restart Docker services during installation.
Start the ScentStation Compose stack as usual; if it is already running, the
monitor reconciles the current Wi-Fi IPv4 on startup.

The monitor listens for Windows network/address-change events and performs a
30-second recovery check. It requires one active physical Wi-Fi adapter with
one preferred private IPv4. On an IP change it updates `LAN_HOST`,
`PAYMENT_MOCK_WEB_BASE_URL`, and `MOCK_PAYMENT_HTTPS_ORIGIN` while keeping port
`3443`; if necessary, it creates a new leaf certificate signed by the existing
mkcert Root CA. It only updates running Order Service and API Gateway
containers, and validates/reloads Nginx without recreating the HTTPS container.
It never rebuilds images, restarts unrelated services, or changes database
volumes. Before recreating a running Order Service, a read-only preflight checks
all eight schema tables and all five conditionally added columns. If any are
missing, the monitor aborts before changing configuration. When they are
present, the existing startup SQL only encounters existing tables/columns and
its conditional upgrades do not execute DDL; no persistent SQL-init setting is
changed.

The phone must already trust the same mkcert Root CA for the new leaf
certificate to be accepted. A changed leaf certificate does not install or
trust the CA on the phone. Phone operating-system/browser trust steps may be
required once, and some devices require explicit enabling of user-installed
certificates.

The monitor log and state are stored under
`%LOCALAPPDATA%\ScentStation\LanPaymentMonitor`. If no unique active Wi-Fi IPv4
is available, the previous settings are retained and an error is logged.

## Runtime behavior and limitations

The HTTPS profile listens on `3443`; Nginx is bound on all container interfaces,
not on a specific Windows LAN address. Changing Wi-Fi therefore does not
require changing the port mapping. A newly created payment uses the updated
backend URL for QR 1; QR 2 remains the payment-reference payload above.

A QR 1 already displayed before a network change still contains the old IP and
may no longer open. Generate/display a fresh payment QR after the monitor has
applied the new address. Physical Wi-Fi switching, phone CA trust, and real
mobile-camera payment flows must be validated on the target network/device;
the automated tests do not claim those end-to-end checks passed.

The HTTP fallback remains available at port `3000`. It offers file-input camera
capture (`capture="environment"`), but live `getUserMedia()` will remain
browser-dependent on HTTP LAN.
