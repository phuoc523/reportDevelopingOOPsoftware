# Copilot Agent Prompt — Phase 6: System Integration

## Project Context and Prerequisites

Read `docs/ai-context/notification-service-context.md` and the Phase 2–5 implementation logs that exist. Verify each claimed route, receiver, security feature, and delivery channel against current source. Notification Service defaults to port 8087 and is not currently routed by API Gateway. Machine Service has a configurable notification URL and internal handoff; re-check current source/configuration before integration. The known Operation/Reporting port discrepancy is documented in the AI context; do not silently resolve it.

Inspect Gateway routes/filters, Identity JWT/security/tenant behavior, Machine URL/key/retry configuration, Docker/Compose conventions, environment examples, health checks, and existing smoke tests. Verify actual service networking/launch topology; do not assume DNS, compose membership, service discovery, or production routing.

## Objective

Integrate only verified, completed Notification Service capabilities with the system while preserving service boundaries and existing behavior.

## Implementation Requirements

- Add a Gateway route only for approved public Notification APIs. Keep internal event receiver paths inaccessible through the public Gateway unless an approved design explicitly secures them.
- Configure direct Machine-to-Notification service URL/authentication only after verifying network topology and secret configuration. Never route internal credentials through a Gateway that strips them.
- Wire Identity/tenant authorization according to the verified contract; test unauthorized and cross-tenant requests.
- Update Compose/deployment only if present and in scope. Keep environment-based secrets, health checks, and dependency/network semantics consistent with repository patterns.
- Resolve the Operation/Reporting port conflict only with documented technical reason and explicit coordination.
- Add supported end-to-end integration tests for routes, internal handoff, auth, tenant isolation, health, and delivery.
- Preserve backward compatibility and each service's private database. Do not introduce service discovery/brokers or expose internal paths without approval.

## Security Requirements

Respect Gateway trust filtering and internal-path blocking. Authenticate public requests with the established Identity mechanism and internal calls with the verified internal authentication contract. Derive tenant identity from trusted authentication, deny by default, and keep secrets out of checked-in config.

## Verification and Documentation

Run service and focused integration tests. Probe public Gateway and direct internal communication against the actual topology where possible. Record unavailable infrastructure as NOT TESTED or BLOCKED with reasons; do not claim interactions work without execution. Confirm existing Gateway route tests still pass.

Create `docs/implementation-log/notification-service-phase-6.md` and update `docs/ai-context/notification-service-context.md`. Record routes and public/internal boundaries, environment variable names only, health/deployment changes, files changed, exact tests, topology limitations, and the known port discrepancy.

## Completion Checklist

- [ ] Prior phases and runtime configuration verified.
- [ ] Public/internal routes and authentication are separated correctly.
- [ ] Tenant isolation and internal service authentication tested.
- [ ] Deployment changes follow existing patterns and expose no secrets.
- [ ] Port conflict is not silently changed.
- [ ] Existing route tests and new integration checks pass or are classified accurately.
- [ ] Phase 6 log and context contain evidence.
