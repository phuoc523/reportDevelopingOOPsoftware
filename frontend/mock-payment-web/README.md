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

On the Windows host, generate a locally trusted certificate with `mkcert`
for the Wi-Fi IPv4 address and any DNS name used by the phone:

```powershell
mkcert -install
New-Item -ItemType Directory -Force infra/docker/tls
mkcert -cert-file infra/docker/tls/mock-payment.crt `
  -key-file infra/docker/tls/mock-payment.key `
  192.168.11.37
```

Replace `192.168.11.37` with the current LAN address; do not commit the private
key or generated certificates. Set the following in the root `.env`, then
rebuild/start the HTTPS profile:

```dotenv
PAYMENT_MOCK_WEB_BASE_URL=https://192.168.11.37:3443/
MOCK_PAYMENT_HTTPS_ORIGIN=https://192.168.11.37:3443
```

```powershell
docker compose --profile https up -d --build mock-payment-web mock-payment-web-https api-gateway order-service
```

Open `https://<LAN_HOST>:3443/` on the phone. Install the root certificate
trusted on that phone or use a public trusted reverse proxy/domain. The phone
must reach the LAN host and accept the certificate warning/trust setup.

The HTTP fallback remains available at port `3000`. It offers file-input camera
capture (`capture="environment"`), but live `getUserMedia()` will remain
browser-dependent on HTTP LAN. This project has not been tested with a physical
Android or iOS device.

After installing a locally trusted certificate for the active Wi-Fi IP, run
`scripts/Start-LanPayment.ps1` from Windows PowerShell. It detects the physical
Wi-Fi adapter, updates `LAN_HOST`, `PAYMENT_MOCK_WEB_BASE_URL`, and
`MOCK_PAYMENT_HTTPS_ORIGIN` in the existing root `.env`, then starts the HTTPS
Compose profile and rebuilds the backend so newly generated QR 1 URLs use
HTTPS. The script fails rather than choosing an Ethernet, VPN, Docker, or WSL
address when a unique Wi-Fi IPv4 cannot be determined, or when the TLS files
are missing. Existing orders can retain their previously created QR URL; create
a new payment after startup to get the HTTPS QR 1.

The backend returns the configured HTTPS root separately from the transaction
JSON; neither the reference nor a path/query is added to QR 1.
