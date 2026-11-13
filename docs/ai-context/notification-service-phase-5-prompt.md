# Copilot Agent Prompt — Phase 5: Notification Delivery

## Project Context and Prerequisites

Read `docs/ai-context/notification-service-context.md` and the Phase 2–4 implementation logs that exist, then inspect current source. Phase 2 separates notification content from recipient read state. Recipient resolution, templates, delivery workers/providers, and delivery status are not implemented unless later-phase source proves otherwise. Identity Service has user/tenant/role logic, but no recipient-resolution contract has been established.

Verify all preceding phase prerequisites in source. If recipient policy or the required contracts are absent, stop and report BLOCKED; do not silently implement earlier phases or invent a recipient broadcast policy.

## Objective

Implement only the approved notification delivery architecture, starting with in-app delivery where supported. Add recipient-specific persistence/status and templates only when the prior model and recipient rules are verified and approved.

## Before Editing

- Inspect Identity user/tenant/role APIs and domain, Machine/Operation ownership, event categories, existing templates/localization, background scheduling, outbox/retry, and logging patterns.
- Establish who should receive each notification from verified business rules; do not assume tenant-wide broadcast.
- Verify channels, dependencies, credentials, consent, provider behavior, and runtime configuration. A dependency or environment variable alone does not establish a usable provider.

## Implementation Requirements

- Resolve recipients only through a traceable, verified policy.
- Persist recipient-specific state according to the approved model.
- Use stable template keys and parameterized content; separate notification meaning from channel delivery.
- Implement only approved channels. Prefer in-app persistence. Email/FCM remain planned until provider, credentials, consent, and behavior are approved.
- Track per-recipient delivery status/timestamps only as supported by the approved design; distinguish queued, delivered, failed, and retryable states where appropriate.
- Use bounded retries and idempotency; surface terminal failures. Do not report success when delivery failed or was not attempted.
- Test recipient policy, tenant boundaries, template rendering, status transitions, duplicate requests/events, transient/permanent failures, and retry limits.
- Prevent cross-tenant delivery and access. Protect credentials; do not log secrets or sensitive content.

## Constraints

- Use Notification Service's database only; preserve established Phase 2–4 contracts.
- Do not add fake providers that claim production success or unapproved broker/cache/provider dependencies.
- If Identity Service lacks a suitable verified lookup contract, report the exact missing contract and request a decision.

## Verification and Documentation

Run focused tests and `mvn test`. Use provider fakes only as explicit test doubles; do not claim external delivery. Mark external provider tests NOT TESTED unless run against an approved environment.

Create `docs/implementation-log/notification-service-phase-5.md` and update `docs/ai-context/notification-service-context.md`. Document recipient-selection evidence, tenant boundaries, templates, channels actually implemented, status/retry/idempotency behavior, test results, external integrations not tested, limitations, and files changed.

## Completion Checklist

- [ ] Prior-phase prerequisites verified in source.
- [ ] Recipient policy is evidence-based and not an assumed broadcast.
- [ ] Approved in-app/channel behavior and recipient status are implemented.
- [ ] Retry and duplicate behavior are tested.
- [ ] External delivery is claimed only if integrated and verified.
- [ ] Tenant authorization and secrets handling are covered.
- [ ] Phase 5 log and context accurately reflect implementation.
