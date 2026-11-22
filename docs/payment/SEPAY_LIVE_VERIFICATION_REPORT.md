# SePay live verification report

**Verification date:** 22 November 2026 (Asia/Ho_Chi_Minh)  
**Repository branch:** [feature/phung-sepay-mb-real-payment](https://github.com/phuoc523/reportDevelopingOOPsoftware/tree/feature/phung-sepay-mb-real-payment)  
**Verified GitHub baseline:** [90c203fe1c06398cb7fddbc82d1018f32bc877a4](https://github.com/phuoc523/reportDevelopingOOPsoftware/commit/90c203fe1c06398cb7fddbc82d1018f32bc877a4)

## Verification results

| Area | Status | Evidence and limits |
| --- | --- | --- |
| GitHub branch and baseline | PASS | GitHub branch HEAD was verified at the baseline commit. Local source tree matches that remote commit's tree; local commit metadata differs. |
| VietQR checkout and webhook code | PASS (code/test scope) | Order Service SePay integration tests cover QR checkout and settlement rules. No bank-app scan was performed. |
| SePay HMAC | PASS (code/test scope) | HMAC-SHA256 uses the timestamp plus the exact raw request body, and rejects timestamps outside the five-minute window. See the official references below. |
| Order Service Maven verification | PASS with skips | JDK 21 Maven clean verify: 73 tests, 0 failures, 0 errors, 2 skipped. The skipped tests require Docker/Testcontainers. Maven completed BUILD SUCCESS. |
| Kiosk Web build | PASS | npm run build completed successfully. |
| Admin Web build | PASS | npm run build completed successfully. |
| Docker Compose validation | BLOCKED | Docker CLI and /var/run/docker.sock are unavailable in this workspace. A GitHub Actions validation job has been added; its run is still pending. |
| MySQL integration | NOT VERIFIED | Local payment integration tests use H2. No MySQL container or deployed MySQL schema was available. |
| FULL DEMO services | NOT VERIFIED | Docker Desktop is not available here. No service was started and no existing volume or database was touched. |
| Public HTTPS webhook | BLOCKED | No public endpoint, domain, reverse proxy, or Cloudflare Tunnel is configured in this workspace. |
| SePay Dashboard | NOT VERIFIED | This run had no authenticated Dashboard session, so account linkage and live webhook settings were not rechecked. |
| Real bank transfer | NOT PERFORMED | No money was sent. A real payment has not been verified. |
| GitHub Actions | PENDING | A branch-specific CI workflow was added in this delivery. Do not treat it as PASS until a run finishes successfully. |

## Test detail

The Maven test suite ran on JDK 21. A first attempt without explicitly loading Byte Buddy's agent hit the sandbox's process-attach restriction; the successful rerun loaded the cached agent explicitly and completed clean verify. Testcontainers skipped two tests because the environment has no Docker engine. This workaround was only for the test process and did not change project files.

The SePay webhook tests use MockMvc and H2. They are not evidence of MySQL production behavior, a public HTTPS callback, a SePay Dashboard delivery, or a real MB credit.

## Official SePay contract checked

- [Webhook authentication](https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc): HMAC-SHA256 signature is computed from `timestamp.raw_body`; timestamp is sent in `X-SePay-Timestamp` and signature in `X-SePay-Signature`.
- [Webhook integration](https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook): a successful delivery needs HTTP 200/201, JSON `{"success":true}`, and a response within 30 seconds; duplicate transaction deliveries must be handled idempotently.
- [Webhook security](https://developer.sepay.vn/vi/sepay-webhooks/bao-mat): use HTTPS, validate the timestamp, transaction account, amount, and payment code.

## Gate

**Current state: CODE VERIFIED; NOT READY FOR LIVE PAYMENT TEST.**

Before live payment can be considered, a public HTTPS endpoint and matching SePay HMAC configuration must be set up, the real MySQL schema and behavior must be verified, and CI must complete. Only then should the user review the displayed order and personally authorize a small transfer. No secret, OTP, or bank credential belongs in this repository or chat.
