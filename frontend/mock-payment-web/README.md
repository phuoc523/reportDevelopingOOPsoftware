# Mock Payment Web

The kiosk renders one QR per order. It contains only the payment identity:

`{"type":"SCENTSTATION_PAYMENT","paymentReference":"PAY-..."}`.

The shared site root, `http://<LAN_HOST>:3000/`, is shown as plain text/link,
not encoded in the transaction QR. The Mock Payment home page scans the QR,
validates its shape, then sends the reference in the body of
`POST /api/v1/payments/public/lookup`. It does not display or ask customers to
type the reference. Each browser tab keeps its own payment and order state.
Confirmations are sent through `POST /api/v1/payments/public/confirm`; the
backend continues to validate the payment, order, amount, currency, expiry,
status, and callback idempotency. Callback secrets remain server-side.

Live `getUserMedia()` camera access requires a secure context. A page opened at
an HTTP IPv4 LAN address is not a secure context, so modern Android/iOS browsers
generally block live camera access there. The page reports the restriction and
does not provide an alternate capture path. This project has not been tested
with a physical Android or iOS device. See
[MDN: getUserMedia](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia).

Run `scripts/Start-LanPayment.ps1` from Windows PowerShell to detect the active
physical Wi-Fi adapter, update only `LAN_HOST` and
`PAYMENT_MOCK_WEB_BASE_URL` in the existing root `.env`, and start Docker
Compose. The script fails rather than selecting an Ethernet, VPN, Docker, or
WSL address when a unique Wi-Fi IPv4 cannot be determined. The backend returns
the configured root URL separately from the transaction JSON; neither the
reference nor a path/query is added to that URL.
