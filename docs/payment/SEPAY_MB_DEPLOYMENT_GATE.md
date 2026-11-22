# SePay MB real-payment integration — deployment gate

Branch: `feature/phung-sepay-mb-real-payment`

## Confirmed prerequisites
- SePay account is connected to personal MB account **55785111**.
- SePay dashboard reports successful API linking and detected a first **0 VND** event. This is **not** proof of a positive-value payment.
- The existing Order Service currently implements **mock** QR payments. Do not enable real payments until the gates below pass.

## Production requirements
1. Preserve mock mode; isolate real-payment mode using an explicit provider discriminator.
2. Generate bank VietQR with exact VND amount and unique transfer memo per order. Never reuse the mock-payment URL as a bank QR.
3. Configure an HTTPS webhook with SePay HMAC-SHA256 authentication. Validate `X-SePay-Signature` and `X-SePay-Timestamp` using the current official specification, including anti-replay.
4. Validate `gateway`, `accountNumber=55785111`, `transferType=in`, `transferAmount`, and the payment code from `content`/`code`. A transfer with 0 VND or unmatched memo must **never** mark an order paid.
5. Deduplicate by SePay transaction `id` with a durable database UNIQUE constraint; make payment confirmation and dispensing idempotent across processes.
6. Persist the authenticated event before acknowledging it. SePay expects HTTP 200/201 and JSON `{"success":true}` within 30 seconds; retry-safe durable processing is required.
7. Reject all mock confirm/simulate endpoints for SePay payments. Never expose the mock callback token in a public real-payment flow.
8. Add a reconciliation job for missed webhooks and manual review for mismatches/refunds/expired orders.
9. Run unit, integration, and frontend tests; then perform a **user-authorized** small positive-value bank transfer and verify MB, SePay, backend, MySQL, kiosk, and single dispensing trigger.
10. Do not claim REAL PAYMENT VERIFIED before that complete evidence exists.

## User-side steps remaining
- Create the SePay webhook in **Tích hợp WebHooks**, select only incoming transactions for the linked MB account, use HMAC-SHA256, and provide a public HTTPS endpoint.
- Set webhook secret only in a secure local environment/secret manager. Do **not** commit credentials, OTPs, or bank login data.
- At home, pull this branch only after implementation and tests have passed. Configure secrets and public HTTPS callback, then perform the live test.

## Official references
- https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook
- https://developer.sepay.vn/vi/sepay-webhooks/bao-mat
- https://developer.sepay.vn/vi/sepay-webhooks/tao-webhook

## Status
- Git branch created.
- Implementation: **NOT COMPLETE**.
- Automated tests: **NOT RUN**.
- Live bank payment: **NOT VERIFIED**.

This document is a safety gate, not a claim that the real-payment integration is operational.
