# ScentStation — Shared API & Error Conventions

> **Status: DRAFT / PENDING TEAM SIGN-OFF.** This document defines recommended contracts for **new** or intentionally modified APIs. It does **not** assert that every existing service already implements these conventions. Preserve existing public contracts until the owning teams review and approve a migration.
>
> Owner: P1 — Identity & Platform. Reviewers: P2 Catalog/Inventory, P3 Order/Payment, P4 Machine/IoT, P5 Frontend/Reporting.

## 1. Scope and principles

- Applies to clients, API Gateway, Identity, Catalog, Operation, Order, Machine, and Reporting.
- A backend owns its validation, authorization, response payloads, database Tenant isolation, and business outcomes. The Gateway is **not** the sole authority for authentication or access control.
- Use HTTPS/TLS in deployed environments; localhost HTTP is for development only.
- Do not silently change existing response shapes, URLs, status codes, or HTTP semantics. Coordinate breaking changes and version them.

## 2. URL naming, versioning, and HTTP methods

| Topic | Convention |
| --- | --- |
| Public Gateway base | `/api/v1/{service}/...` (the currently documented Gateway scheme) |
| Backend base | `/api/v1/...` unless an owner-approved service contract states otherwise |
| Naming | Lowercase, hyphen-separated path segments; resource-oriented nouns; plural resources where appropriate |
| Resource ID | Stable opaque ID in the path; validate before use |
| GET | Retrieve data; no business mutation |
| POST | Create a resource or execute a declared action; not assumed idempotent |
| PUT | Replace/set a resource or named configuration value; idempotent by design |
| PATCH | Partial update where supported and documented |
| DELETE | Delete/deactivate when business rules allow; document semantics |

**Documented current route example:**

```text
Gateway:  POST /api/v1/identity/auth/login
Downstream: POST /api/v1/auth/login
```

The separately proposed `/api/auth/**` or `/api/admin/**` aliases are **not guaranteed** by the current Gateway contract. Add them only after confirming downstream paths, compatibility, and tests.

Use `/api/v2/...` (or an approved migration strategy) for incompatible changes. Where possible, add optional fields rather than changing the meaning or type of existing fields. Announce deprecations and test old clients before removal.

## 3. Request and response format

- `Content-Type: application/json` for JSON request bodies; `Accept: application/json` is recommended.
- UTF-8. Use **camelCase** JSON property names, e.g. `tenantId`, `createdAt`, `requestId`.
- Stable machine-readable enum values, e.g. `PENDING_PAYMENT`; do not change enum spelling without coordination.
- Timestamps should be ISO 8601 with timezone, preferably UTC, e.g. `2026-10-27T09:00:00Z`.
- Use explicit nullability and required-field rules in DTO/OpenAPI. Avoid leaking persistence entities, credentials, or internal-only fields.
- Document each endpoint's success status, schema, authentication requirements, field constraints, and error codes.
- Preserve current response objects and casing for existing clients until migration is approved.

**Illustrative new resource response (proposal; not a claim about existing APIs):**

```json
{
  "id": "resource-123",
  "tenantId": "tenant-abc",
  "status": "ACTIVE",
  "createdAt": "2026-10-27T09:00:00Z"
}
```

### Pagination and filtering (proposed common contract)

For **new collection endpoints**, propose `page` (zero-based), `size`, `sort` with a documented default and maximum; **suggest** `size=20`, max `100`, subject to team agreement. Use server-side filtering and always apply verified Tenant scope. An illustrative shape:

```http
GET /api/v1/catalog/products?page=0&size=20&sort=createdAt,desc
```

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0
}
```

Do **not** retrofit these query parameters or fields onto existing endpoints without checking their current contracts and consumers. Cursor-based pagination can be adopted for high-volume streams by owner agreement.

## 4. HTTP status and application error contract

| Status | Expected meaning |
| --- | --- |
| 200 OK | Successful request with body |
| 201 Created | Resource created; optionally return `Location` |
| 202 Accepted | Accepted for asynchronous processing; not completed |
| 204 No Content | Successful request with no response body |
| 400 Bad Request | Invalid syntax, parameter or configuration value |
| 401 Unauthorized | No valid user/session/token |
| 403 Forbidden | Valid identity, insufficient permission or prohibited Tenant scope |
| 404 Not Found | Missing resource, undisclosed cross-Tenant resource, or unconfigured route |
| 409 Conflict | Resource conflict, duplicate state transition, or concurrency conflict |
| 422 Unprocessable Content | Optional for semantic validation only if owner-approved; do not require existing APIs to change from 400 |
| 429 Too Many Requests | Rate-limited endpoint, if rate limiting is configured |
| 500 Internal Server Error | Unexpected server error; suppress sensitive details |
| 502 Bad Gateway | Bad upstream response, where Gateway maps it |
| 503 Service Unavailable | Service unavailable, required service secret absent, or deliberate maintenance |
| 504 Gateway Timeout | Upstream timeout where Gateway maps it |

**Existing Identity-compatible minimum error envelope** (the deployed APIs may already use `code` and `message`):

```json
{
  "code": "UNAUTHENTICATED",
  "message": "Authentication required"
}
```

**Recommended additive error fields for new endpoints** (optional; requires team approval before changing existing clients):

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request data",
  "requestId": "req-123",
  "details": [
    {
      "field": "quantity",
      "message": "Must be greater than zero"
    }
  ]
}
```

Application error codes must be stable and not derived from exception names. Initial proposed catalog: `VALIDATION_ERROR`, `UNAUTHENTICATED`, `ACCESS_DENIED`, `RESOURCE_NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `UPSTREAM_UNAVAILABLE`, `INTERNAL_ERROR`. Existing codes win until migration is coordinated.

For validation, report safe field-level messages, not exception stack traces or rejected credentials. Unexpected errors: record a server-side request ID and return a non-sensitive public message. A 5xx must never masquerade as 200 success.

## 5. Validation and consistency

- Validate DTOs at service boundaries (required fields, type/range, lengths, format, enum membership).
- Apply ownership and Tenant constraints **inside the underlying read/update/delete query**, e.g. constrain by both `id` and verified `tenant_id`.
- Never treat `tenantId` supplied by frontend path/body/query/header as proof of access.
- Document per-endpoint error codes and whether a denied cross-Tenant resource returns 403 or privacy-preserving 404.
- Make state transitions explicit; avoid side effects on GET.

## 6. Authentication, RBAC, Tenant, and internal communication

- External protected calls use `Authorization: Bearer <opaque-token>`.
- Downstream services validate the session via the protected Identity introspection contract, then derive trusted `userId`, `tenantId`, `roles`, `permissions` from the result. Do not trust client-submitted identity headers.
- Known Identity internal example:

```http
POST /api/v1/internal/tenant/introspect
X-Internal-Service-Key: <server-secret>
Content-Type: application/json

{"accessToken":"<opaque-token>"}
```

Successful validation yields `{userId, username, tenantId, roles, permissions}`. Do not persist access tokens in domain tables. Return 401 for missing/invalid/revoked token; 403 for authenticated users lacking permission/Tenant scope; optionally 404 for hidden cross-Tenant resources.

- Gateway must strip untrusted inbound `X-Verified-*` and `X-Internal-Service-Key`, must not expose `/internal/` externally, and must pass required user `Authorization` headers without logging token values.
- Internal endpoints require **both** a server-controlled authentication mechanism and network isolation; do not rely on header name alone. Use separate independent `TENANT_INTROSPECTION_KEY`, `AUDIT_SERVICE_KEY`, and `NOTIFICATION_SERVICE_KEY` (at least 32 random characters each as in current handover). Store them only in approved environment/secret storage, not Git, logs, Jira, or frontend code; plan secret rotation.
- Restrict production internal traffic and enable TLS/network controls appropriate to the deployment.

## 7. Gateway routing, CORS and downstream failures

- Current documented external convention: `/api/v1/{service}/...` maps to the owner-approved downstream `/api/v1/...` endpoint.
- Current Identity upstream configuration is documented as `IDENTITY_URL` with development default `http://127.0.0.1:8081`. Confirm other upstream addresses with owners before enabling routes.
- Configure `CORS_ALLOWED_ORIGINS` as exact comma-separated allowed frontend origins; development default documented as `http://localhost:5173`. Do not use `*` with credentials.
- Handle preflight `OPTIONS` requests using explicitly permitted methods/headers/origins.
- An unconfigured route returns 404; unavailable upstream produces an appropriate non-success error (often 5xx). Verify actual mapping during integration tests; never invent a successful response.
- Do not forward caller-provided `X-Verified-*` as trusted claims.

## 8. Trace, audit, logging, privacy

- Use server-generated `X-Request-Id` from the Gateway as the correlation identifier, and propagate it in downstream logs and cross-service calls.
- Log safe fields: timestamp, request ID, service, route/method, HTTP status, duration, sanitized principal/tenant identifiers where permitted.
- Never log raw bearer tokens, passwords, payment credentials, internal service keys, personally sensitive payloads, or full request bodies by default.
- Emit appropriate audit events for sensitive business actions using the documented shared Audit API; service owners are responsible for producing events from business workflows.
- Distinguish operational logs from immutable/auditable business events.

## 9. Timeout, retry, and idempotency

- Set connection/read timeouts per integration according to measured behavior and service owner approval; document them in each service's configuration. Do not assume the same timeout works for machine commands and ordinary reads.
- Retry only **transient failures** with bounded attempts, exponential backoff and jitter where reasonable. Never blindly retry unsafe POST operations, financial settlement, dispensing commands, or webhook processing.
- For payment, order creation and device commands, use server-side idempotency controls (unique business event/operation ID; e.g. owner-approved `Idempotency-Key` convention) before enabling retries. Database uniqueness and atomic state transitions must enforce at-most-once business effects.
- Document whether a timeout leaves an operation uncertain and how callers can reconcile status without reissuing a physical action.
- Do not claim these retry/idempotency mechanisms are implemented everywhere; these are standards for owner implementation and E2E testing.

## 10. OpenAPI, tests, and backward compatibility

- Publish OpenAPI/Swagger for owned endpoints when available, showing URL, authorization, request/response schemas, examples, and errors.
- Test positive and negative cases: authenticated/anonymous, sufficient/insufficient role, Tenant A vs Tenant B, input validation, error schema, pagination bounds, CORS preflight, unknown route, upstream unavailable.
- Contract-test status, error codes and JSON keys. Test forwarding against actual running downstreams (not just stubs) before marking integration Done.
- Gate new changes with Maven tests and CI; request owner review for cross-service path/schema changes.
- Additive optional fields usually preserve compatibility; renamed paths, mandatory fields, changed codes, and changed types require a versioned or documented coordinated migration.

## 11. Developer checklist

- [ ] Confirm the route prefix and HTTP method with the owning service.
- [ ] Document request/response schema, JSON naming, status codes and stable error codes.
- [ ] Validate input bounds and reject malformed requests consistently.
- [ ] Enforce authenticated identity, role/permission and verified Tenant in service and data access layer.
- [ ] Keep internal endpoints private and secrets out of source control and output logs.
- [ ] Propagate `X-Request-Id`; sanitize logs.
- [ ] Define pagination/filtering for list APIs where applicable.
- [ ] Set integration-specific timeouts and safe retry/idempotency behavior.
- [ ] Add positive/negative/unit/integration and compatibility tests.
- [ ] Publish or update Swagger/README and provide example requests/responses.
- [ ] Confirm no frontend or downstream consumer breaks.
- [ ] Obtain reviewer sign-off before modifying shared conventions.

## 12. Team review / sign-off (not completed yet)

| Owner | Required confirmation | State |
| --- | --- | --- |
| P1 Identity & Gateway | Auth/error/headers, Gateway, CORS and documentation | Pending review |
| P2 Catalog & Inventory | Endpoint naming, pagination, Tenant, inventory/audit contracts | Pending review |
| P3 Order & Payment | Error codes, payment timeouts, retries, webhook idempotency | Pending review |
| P4 Machine & IoT | Commands, telemetry, device security, timeout and idempotency | Pending review |
| P5 Frontend & Reporting | JSON/error compatibility, CORS, pagination and reporting access | Pending review |

Record the actual reviewer, review date, branch/PR, approved deviations, and compatible rollout plan here. **Do not mark team sign-off complete until members have explicitly agreed.**

## 13. Handover and verification

- File to commit: `docs/api-conventions.md`.
- Related handover: `docs/P1-06-P1-11-PLATFORM-HANDOVER.md`.
- Verify Markdown headings and examples, and check Git file presence with `git ls-files docs/api-conventions.md`.
- In PR/Jira attach the Git commit, CI checks, document link and team sign-off record.
- This document is a **design/coordination deliverable**, not proof that every downstream service already meets every convention.
