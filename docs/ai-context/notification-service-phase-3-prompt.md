# Copilot Agent Prompt — Phase 3: Notification Management API

## Project Context and Prerequisites

Work in the independent Java 21 / Spring Boot 3.4.5 Maven service at `services/notification-service`. Read `docs/ai-context/notification-service-context.md`, `docs/implementation-log/notification-service-phase-2.md`, and `docs/database/notification-service-database-design.md`, then inspect current source. Phase 2 persistence/core architecture is implemented and H2-verified; live MySQL is not verified. It includes `Notification`, `NotificationRecipient`, tenant-scoped repositories, `NotificationService`, and internal command/response DTOs. No business controller, authentication, or authorized principal/tenant propagation currently exists.

Implement only this phase. Before editing, verify Phase 2 source and migrations, inspect Identity/Gateway security, controller/DTO/error/pagination conventions, relevant OpenAPI dependencies, and any API test scripts. If the required foundation is absent or the security authority cannot be verified, stop and report BLOCKED. Do not duplicate Phase 2 work or invent a security contract.

## Objective and Scope

Implement the approved user-facing Notification Management API: supported create/list/detail/read-state/bulk-read/unread-count operations, pagination, sorting, and filtering. Derive routes, payloads, response shapes, status codes, and ordering semantics from approved requirements and current service conventions; do not assume all operations are authorized or required.

## Implementation Requirements

- Keep all reads and writes tenant- and recipient-scoped in repository queries.
- Derive caller identity and tenant from a verified trusted principal. Never trust `X-Tenant-Id`, user headers, or caller-provided recipient IDs alone.
- If no safe principal/tenant authority is available, stop before exposing data and report the decision needed.
- Validate and bound pagination, sorting, filtering, and request inputs.
- Follow existing exception handling and response patterns; add only narrowly scoped handling where needed.
- Add OpenAPI only if existing dependencies and conventions support it.
- Test successful and invalid requests, missing records, unauthorized/forbidden access, cross-tenant access, recipient ownership, and pagination boundaries.

## Constraints

- Use Notification Service's database only and preserve Phase 2 semantics/migrations.
- Do not add Machine event ingestion, delivery providers, unapproved fields/tables/statuses, or recipient policies.
- Keep code under `com.scentstation.notification`; avoid unrelated service changes.

## Verification and Documentation

Run focused tests and `mvn test` from `services/notification-service`. Verify API behavior using existing contract scripts; otherwise use API/MockMvc tests and runtime probes where the environment permits. Record unexecuted MySQL/external checks as NOT TESTED.

Create `docs/implementation-log/notification-service-phase-3.md` and update `docs/ai-context/notification-service-context.md`. Record exact routes and shapes, security and tenant rules, validation, tests/results, limitations, and changed files. Do not proceed with Phase 4–7 scope.

## Completion Checklist

- [ ] Phase 2 prerequisites verified in source.
- [ ] Routes and contract follow approved requirements.
- [ ] Authentication and tenant authorization use verified identity, never caller-controlled headers.
- [ ] Tenant/recipient scoping, validation, errors, pagination, and tests are covered.
- [ ] Runtime/API checks were performed or marked NOT TESTED with reasons.
- [ ] Phase 3 log and AI context accurately reflect evidence.
