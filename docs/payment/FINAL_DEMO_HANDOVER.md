# ScentStation payment and demo handover

## Current delivery

- Branch: [feature/phung-sepay-mb-real-payment](https://github.com/phuoc523/reportDevelopingOOPsoftware/tree/feature/phung-sepay-mb-real-payment)
- Baseline verified: [90c203fe1c06398cb7fddbc82d1018f32bc877a4](https://github.com/phuoc523/reportDevelopingOOPsoftware/commit/90c203fe1c06398cb7fddbc82d1018f32bc877a4)
- Order Service: Maven verification passed with 71 passing tests and 2 Docker-dependent tests skipped.
- Admin Web and Kiosk Web production builds passed.
- Live payment and FULL DEMO remain unverified. Payment defaults to MOCK unless explicitly configured.

## Start the local FULL DEMO

From the repository root on Windows, after Docker Desktop is running and the workspace has enough memory and disk space:

```powershell
.\scripts\demo\Start-ScentStation-Full.ps1
.\scripts\demo\Check-ScentStation-Full.ps1
```

The launcher prepares a separate `.env.demo` only when one does not already exist and does not remove volumes. Review its output and the health-check output. To stop the demo safely:

```powershell
.\scripts\demo\Stop-ScentStation-Full.ps1
```

These commands have not been run in the current workspace because Docker Desktop/Engine is unavailable here. Do not treat the demo as verified until every required service is healthy.

## Before enabling live payment

1. Keep `PAYMENT_PROVIDER=MOCK` for local demos until all live gates pass.
2. Back up and inspect the target MySQL database and migration/schema changes before starting Order Service against it.
3. Configure an Internet-reachable HTTPS endpoint for `/api/v1/webhooks/sepay`; keep MySQL and Redis private.
4. In SePay Dashboard, configure only incoming transactions for the intended MB account and HMAC-SHA256. Store the matching secret in a protected environment variable; never commit it.
5. Verify a QR and order in the Kiosk, the signed webhook delivery, durable MySQL event/payment/order records, and a single dispensing command using a simulator.
6. Stop at **READY FOR LIVE PAYMENT TEST**. The account holder reviews the order and QR and personally decides whether to transfer. Never send OTPs or bank credentials through chat.
7. After the user confirms the transfer, verify the MB history, SePay event, webhook logs, MySQL state, Kiosk state, and one simulator/device command before reporting a real-payment result.

## Current blockers

- No Docker CLI/Engine or Docker socket in the current workspace; Testcontainers tests were skipped.
- No MySQL instance, public HTTPS endpoint, or Cloudflare Tunnel is available here.
- SePay Dashboard was not authenticated during this run.
- No live bank transfer was performed.
- The new GitHub Actions workflow must finish successfully before CI can be marked PASS.

See [the live verification report](SEPAY_LIVE_VERIFICATION_REPORT.md), [webhook setup](SEPAY_WEBHOOK_SETUP.md), and [security checklist](SEPAY_SECURITY_CHECKLIST.md).
