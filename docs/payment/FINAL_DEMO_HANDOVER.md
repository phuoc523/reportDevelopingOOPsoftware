# ScentStation payment and demo handover

## Verified delivery

- Branch: [feature/phung-sepay-mb-real-payment](https://github.com/phuoc523/reportDevelopingOOPsoftware/tree/feature/phung-sepay-mb-real-payment)
- Verified source baseline: [90c203fe1c06398cb7fddbc82d1018f32bc877a4](https://github.com/phuoc523/reportDevelopingOOPsoftware/commit/90c203fe1c06398cb7fddbc82d1018f32bc877a4)
- Latest verified code/CI commit: [26f03770277830250adebc99f77c6f158077fedc](https://github.com/phuoc523/reportDevelopingOOPsoftware/commit/26f03770277830250adebc99f77c6f158077fedc)
- [GitHub Actions run 44981726931](https://github.com/phuoc523/reportDevelopingOOPsoftware/actions/runs/44981726931): all 5 jobs passed.
- Order Service: 71 tests passed, 2 Docker-dependent tests skipped; 6 SePay webhook tests passed on H2.
- Admin Web, Kiosk Web, Compose configuration validation, and Gitleaks passed.
- Live payment and FULL DEMO remain unverified. Payment defaults to MOCK unless explicitly configured.

## Start the local FULL DEMO

From the repository root on Windows, after Docker Desktop is running and the workspace has enough memory and disk space:

```powershell
.\scripts\demo\Start-ScentStation-Full.ps1
.\scripts\demo\Check-ScentStation-Full.ps1
```

The launcher prepares a separate `.env.demo` only when one does not already exist and does not remove volumes. Review its output and health-check output. To stop the demo safely:

```powershell
.\scripts\demo\Stop-ScentStation-Full.ps1
```

The launcher and services have not been run in the current workspace because Docker Engine is unavailable. A passing Compose configuration check does not mean the FULL DEMO is healthy.

## Before enabling live payment

1. Keep `PAYMENT_PROVIDER=MOCK` for local demos until all live gates pass.
2. Back up and inspect the target MySQL database and schema changes before starting Order Service against it.
3. Configure a public HTTPS endpoint for `/api/v1/webhooks/sepay`; keep MySQL and Redis private.
4. In SePay Dashboard, configure incoming transactions for the intended MB account and HMAC-SHA256. Store the matching secret in a protected environment variable; never commit it.
5. Verify an order and QR in the Kiosk, signed webhook delivery, durable MySQL event/payment/order rows, and one dispensing command using a simulator.
6. Stop at **READY FOR LIVE PAYMENT TEST**. The account holder reviews the order and QR and personally decides whether to transfer. Never send OTPs or bank credentials through chat.
7. After the user confirms the transfer, verify the MB history, SePay event, webhook logs, MySQL state, Kiosk state, and one simulator/device command before reporting a real-payment result.

## Remaining gates

- Local Docker/FULL DEMO and service health have not been verified.
- MySQL-backed settlement and schema compatibility have not been verified.
- No public HTTPS endpoint or SePay Dashboard webhook was configured in this run.
- No live bank transfer was performed.

See [the live verification report](SEPAY_LIVE_VERIFICATION_REPORT.md), [webhook setup](SEPAY_WEBHOOK_SETUP.md), and [security checklist](SEPAY_SECURITY_CHECKLIST.md).
