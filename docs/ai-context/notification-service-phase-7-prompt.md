# Copilot Agent Prompt — Phase 7: Testing and Finalization

## Project Context and Prerequisites

Read `docs/ai-context/notification-service-context.md` and all Phase 2–6 implementation logs that exist. Inspect complete current Notification Service source and actual deployment/test configuration. Compare every claim in prior reports against code and configuration; source and command output are evidence. Do not describe a planned feature as complete based only on documentation.

Inspect controllers/security, tenant scoping, migrations, event idempotency, recipient handling, delivery/retry, logging, environment configuration, Docker/Gateway/deployment changes, and tests. Identify test scripts and follow their exact methods, paths, response shapes, and statuses. Confirm JDK/Maven/DB/service/credential/Docker prerequisites without changing protected resources. Prepare a test matrix before execution.

## Objective

Perform evidence-based final verification of the service and approved integrations, fix only reproducible Notification Service defects, and produce a final report with explicit PASSED, FAILED, NOT TESTED, and BLOCKED classifications.

## Verification Tasks

- Run unit, integration, API, and relevant build/package checks.
- Verify migrations/schema and persistence using an approved isolated database. If MySQL is unavailable, state NOT TESTED; H2 does not substitute for MySQL verification.
- Probe implemented APIs/receivers and test authentication, authorization, tenant isolation, invalid input, duplicate events, failure/retry, and delivery status.
- Verify Machine/Gateway interactions only when environment permits and tests use actual current contracts.
- Review logs for correlation and absence of secrets/sensitive payloads.
- Build/inspect Docker image where tooling permits; verify non-root user, runtime config, health behavior, and no embedded credentials.
- Fix only reproducible in-scope defects; do not suppress assertions or broaden unrelated work. Re-run affected tests and report residual failures.

## Security and Scope Constraints

Review public auth, internal caller auth, tenant isolation, authorization, secrets, and Gateway trust boundaries. Test negative cases. Unexercised security properties are NOT TESTED, not PASSED.

Do not modify unrelated services except for a proven, minimal Phase 6 integration defect with explicit documentation. Do not claim production readiness, external delivery, MySQL, or cross-service success without actual evidence. Do not alter/delete evidence or environment state outside approved test resources. Keep secrets out of reports/logs.

## Result Classifications

- **PASSED** — command/probe ran and all assertions passed.
- **FAILED** — command/probe ran and an assertion or execution failed.
- **NOT TESTED** — not run due to missing/unavailable environment or scope.
- **BLOCKED** — prerequisite or approval prevents execution.

Record exact command, runtime/tool versions, exit status, test count/result, endpoint status, and limitations. Do not mark checks PASSED from source inspection, compilation alone, or mocked provider results.

## Deliverables

Create `docs/implementation-log/notification-service-phase-7.md` and update `docs/ai-context/notification-service-context.md`. Include test matrix, exact evidence, database/integration/security/Docker results, failures, not-tested and blocked items, fixes, and residual risks. Reconcile all prior docs with current code; do not add new product features or redesign contracts.

## Completion Checklist

- [ ] Every phase claim checked against source.
- [ ] Unit, integration, API, database, security, event, retry, delivery, logging, config, and Docker checks classified individually.
- [ ] PASSED is reserved for checks actually run successfully.
- [ ] Failures are fixed in scope and retested, or clearly reported.
- [ ] No secrets/sensitive payloads in docs or logs.
- [ ] Phase 7 log and final context are accurate and present.
