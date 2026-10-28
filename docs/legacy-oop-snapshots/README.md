# Historical OOP implementations (review only)

These are snapshots of historical, individually authored Identity/Platform OOP branches.
They preserve the work in branch `test` WITHOUT adding duplicate Java classes
to Spring Boot source roots. **Not a claim that every historical API is active.**

The active Java code remains under `services/identity-service` and `services/api-gateway`.
The P1-03..P1-11 and OOP-16..19 implementations are in existing `test` history.
Account lifecycle and original OOP-11..15 variants need functional equivalence review before Jira Done.

| Source branch | Snapshot | Source files | Commit |
|---|---|---:|---|
| `feature/OOP-11-identity-platform` | `OOP-11-identity-platform/` | 26 | `e5921a3` |
| `feature/OOP-11-p102-account-lifecycle` | `OOP-11-p102-account-lifecycle/` | 30 | `b1d06f5` |
| `feature/OOP-12-api-gateway` | `OOP-12-api-gateway/` | 19 | `573d7d7` |
| `feature/OOP-13-role-based-authorization-final` | `OOP-13-role-based-authorization-final/` | 21 | `1949579` |
| `feature/OOP-14-api-gateway` | `OOP-14-api-gateway/` | 22 | `efe39d8` |
| `feature/OOP-15-shared-api-conventions` | `OOP-15-shared-api-conventions/` | 1 | `3a9f39f` |

## Jira issue coverage (as shown in the supplied Jira screenshots)

- Requirements/docs: OOP-7, OOP-8, OOP-10
- Original platform and API tasks: OOP-11, OOP-12, OOP-13, OOP-14, OOP-15
- New requirements: OOP-16, OOP-17, OOP-18, OOP-19
- Backend/Identity tracking: OOP-57, OOP-58, OOP-59, OOP-61, OOP-62, OOP-63
- P1-01..P1-11: OOP-110 through OOP-120

**Tracking != completion**: some issues remain To Do/In Review in Jira.
Code stored here is inactive historical reference, not enabled API endpoints.
Do not copy old SecurityConfig/JwtAuthenticationFilter/controllers into active services without review.
