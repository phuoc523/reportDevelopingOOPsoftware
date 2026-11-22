# SePay live verification report

**Verification date:** 22 November 2026 (Asia/Ho_Chi_Minh)  
**Branch:** [feature/phung-sepay-mb-real-payment](https://github.com/phuoc523/reportDevelopingOOPsoftware/tree/feature/phung-sepay-mb-real-payment)  
**Code baseline:** [90c203fe1c06398cb7fddbc82d1018f32bc877a4](https://github.com/phuoc523/reportDevelopingOOPsoftware/commit/90c203fe1c06398cb7fddbc82d1018f32bc877a4)  
**Validated delivery commit:** [26f03770277830250adebc99f77c6f158077fedc](https://github.com/phuoc523/reportDevelopingOOPsoftware/commit/26f03770277830250adebc99f77c6f158077fedc)

## Verification results

| Area | Status | Evidence and limits |
| --- | --- | --- |
| GitHub branch and code baseline | PASS | Branch was checked against GitHub. The local code tree matched the baseline source tree before the documentation/CI commits. |
| VietQR checkout and webhook code | PASS (code/test scope) | Order Service SePay integration tests cover QR checkout and settlement rules. No bank-app QR scan was performed. |
| SePay HMAC | PASS (code/test scope) | HMAC-SHA256 uses timestamp plus the exact raw request body and rejects timestamps outside the five-minute window. |
| Order Service Maven verification | PASS with skips | JDK 21 `mvn clean verify`: 73 tests, 0 failures, 0 errors, 2 skipped. The successful GitHub Actions job confirms the build. |
| SePay webhook regression tests | PASS (H2) | 6 SePay webhook integration tests passed. They use MockMvc and H2; they are not MySQL production tests. |
| Kiosk Web build | PASS | Local `npm run build` and GitHub Actions build passed. |
| Admin Web build | PASS | Local `npm run build` and GitHub Actions build passed. |
| Docker Compose syntax/config | PASS | GitHub Actions `docker compose ... config --quiet` passed. This validates configuration only; it does not start the full system. |
| Secret scan | PASS | Gitleaks passed after marking three verified non-secret examples inline: a documented metrics target, a replaceable NiFi sample password, and a test-only JWT value. |
| MySQL integration / schema | NOT VERIFIED | No real or containerized MySQL deployment was tested. Payment integration tests use H2. |
| FULL DEMO services | NOT VERIFIED | No service was started in this workspace; there is no local Docker Engine/socket. |
| Public HTTPS webhook | BLOCKED | No public endpoint, domain, reverse proxy, or Cloudflare Tunnel is configured here. |
| SePay Dashboard | NOT VERIFIED | No authenticated Dashboard session was available during this run; live account and webhook settings were not rechecked. |
| Real bank transfer | NOT PERFORMED | No money was sent. A real payment has not been verified. |
| GitHub Actions | PASS | [Run 44981726931](https://github.com/phuoc523/reportDevelopingOOPsoftware/actions/runs/44981726931) passed all 5 jobs: Maven, both frontends, Compose validation, and Gitleaks. |

## Test detail

The local Maven run used JDK 21 and explicitly loaded the cached Byte Buddy agent because the sandbox blocks Mockito's self-attach mechanism. With that test-process-only workaround, `clean verify` completed successfully. Testcontainers reported two skipped tests because Docker is unavailable in this workspace. No repository build configuration or payment secret was changed for this workaround.

The passing CI Compose job validates the Compose file; it does not establish that services boot, that databases migrate safely, or that data persists in MySQL.

## Official SePay contract checked

- [Webhook authentication](https://developer.sepay.vn/vi/sepay-webhooks/xac-thuc): HMAC-SHA256 signs `timestamp.raw_body`; the timestamp and signature use `X-SePay-Timestamp` and `X-SePay-Signature`.
- [Webhook integration](https://developer.sepay.vn/vi/sepay-webhooks/tich-hop-webhook): successful delivery requires HTTP 200/201 with JSON `{"success":true}` within 30 seconds; duplicate deliveries must be idempotent.
- [Webhook security](https://developer.sepay.vn/vi/sepay-webhooks/bao-mat): use HTTPS, check timestamp, account, amount, and payment code.

## Gate

**Current state: CODE AND CI VERIFIED; NOT READY FOR LIVE PAYMENT TEST.**

Remaining gates: public HTTPS and SePay HMAC Dashboard setup, real MySQL schema/persistence verification, FULL DEMO health verification, and user review of a generated order before any transfer. Only the account holder can decide to make the small test transfer. No secret, OTP, or bank credential belongs in Git or chat.
