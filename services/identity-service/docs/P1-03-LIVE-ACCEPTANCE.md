# P1-03 Live Acceptance - Lockout and Session Expiry

- Verified at: 2026-10-25 14:45:06 +07:00
- Environment: Windows / Spring Boot 3.5.6 / MySQL 8.0, localhost:3307 / HTTP 8081
- Run type: local development database and live HTTP endpoints

| Scenario | Expected | Actual | Result |
| --- | --- | --- | --- |
| Wrong password attempts 1-4 (disposable QA account) | HTTP 401 | HTTP 401 x4 | PASS |
| Wrong password attempt 5 | HTTP 423 | HTTP 423 | PASS |
| Login while locked | HTTP 423 | HTTP 423 | PASS |
| MySQL account status and audit | failed_attempts=5, locked_until populated, ACCOUNT_LOCKED audit | Confirmed | PASS |
| Valid demo.admin bearer session /me | HTTP 200 | HTTP 200 | PASS |
| Live MySQL session expiry (token hash targeted) | /me returns HTTP 401 | HTTP 401 | PASS |

## Notes

- Temporary QA account was deleted after the lockout test. Audit records were retained.
- The expiry test changed expires_at for exactly one newly issued test token to a past timestamp; it did not change the default 30-minute TTL.
- No bearer tokens, passwords, or database credentials were recorded in this report.
- Tests were performed on a local development environment, not production.
- Previously verified separately: valid login, /me, logout, revoked token, invalid user, login audit, and the existing H2 integration suite.
