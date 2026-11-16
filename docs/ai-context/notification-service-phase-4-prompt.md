# Copilot Agent Prompt — Phase 4: Alert/Event Integration

## Project Context and Prerequisites

Work in `services/notification-service`. Read `docs/ai-context/notification-service-context.md`, the Phase 2–3 implementation logs that exist, and current source. Phase 2 includes an optional unique `(source, source_event_id)` guard; it does not implement a receiver or complete idempotent event processing. Machine Service owns alert lifecycle and currently has `MachineAlertChangedEvent`, `AlertHandoffOutbox`, `AlertHandoffClient`, and a scheduled retry job. The event fields and sender URL/header contract in the AI context are leads only: re-read sender code before acting.

Verify Phase 2 persistence and any Phase 3 recipient/API design in source. If an approved receiving contract, recipient policy, or safe delivery route is absent, stop and report BLOCKED instead of inventing one.

## Objective

Implement a secure, reliable receiver for the source-verified alert/event contract and persist/process events using the approved Notification Service domain model. Machine Service remains the owner of alert lifecycle.

## Before Editing

- Re-read `MachineAlert`, `MachineAlertChangedEvent`, outbox entity/repository, alert service, HTTP handoff client, retry job, and security/controller configuration.
- Confirm actual path/URL, request body, internal authentication header, success/failure status semantics, retry behavior, event ID uniqueness, tenant representation, and relevant tests.
- Verify Gateway behavior and the actual deployment network boundary. The public Gateway may strip internal headers and block internal paths; do not assume direct service routing is available.
- Check API/integration scripts and existing internal-call patterns.

## Implementation Requirements

- Document the exact request contract from current sender code; do not silently change sender URL, payload, retry, or key contract.
- Authenticate the source service using a verified mechanism. If trust policy, secret provisioning/rotation, or direct network route is unresolved, stop for approval.
- Validate required event fields, source, timestamp, severity/status values, and tenant identifier.
- Enforce durable idempotency using the verified event identifier. Re-delivery must not create duplicate notifications or inconsistent partial state.
- Make writes atomic; acknowledge only after safe durable acceptance and use meaningful failure responses compatible with sender retries.
- Add tests for valid/invalid events, unauthorized sender, duplicate delivery, tenant handling, transaction rollback/failure, and retry semantics.
- Never treat event tenant/source values as identity proof; never log secrets or sensitive payloads.

## Constraints

- Use Notification Service's database only; never read Machine Service's database/outbox directly.
- Do not change Machine alert lifecycle/outbox behavior or add a broker without explicit approval.
- Do not expose the receiver through the public Gateway without an approved security design.

## Verification and Documentation

Run Notification Service tests and exercise the real HTTP receiver where the environment permits. Mark unavailable MySQL/network integration NOT TESTED. Do not claim end-to-end handoff works unless actually exercised.

Create `docs/implementation-log/notification-service-phase-4.md` and update `docs/ai-context/notification-service-context.md`. Record verified path/body/header, validation, idempotency, transaction/response/retry behavior, security boundary, test evidence, files changed, and remaining assumptions.

## Completion Checklist

- [ ] Phase 2/3 prerequisites and current sender contract verified.
- [ ] Receiver authenticates source and validates events.
- [ ] Duplicate deliveries are safe and writes atomic.
- [ ] Tenant/source are validated; Machine alert lifecycle is unchanged.
- [ ] Cross-service tests are run or clearly NOT TESTED/BLOCKED.
- [ ] No unapproved broker or unrelated changes.
- [ ] Phase 4 log and context contain actual evidence.
