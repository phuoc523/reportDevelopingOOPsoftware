# Identity Service - Local Verification Report

Date: 2026-11-15
Service: identity-service
API: POST /api/auth/staff/login
Port: 8081
Database: MySQL

## Verification Results

| Test | Result |
|---|---|
| Application startup | PASS |
| MySQL connection | PASS |
| Demo staff login | 6/6 PASS (HTTP 200) |
| JWT compact format | 6/6 PASS |
| Maven automated tests | 70 passed, 0 failures, 0 errors |
| Maven build | SUCCESS |

## Verified Demo Accounts

- platform.admin
- demo.admin
- demo.a
- demo.b
- demo.c
- demo.d

## Remaining Verification

- JWT signature and claims validation against live responses
- RBAC authorization against protected endpoints
- Cross-tenant access denial
- Incorrect-password and disabled-account scenarios

## Security

No plaintext passwords, JWT tokens, database credentials,
or environment secrets are included in this report.
