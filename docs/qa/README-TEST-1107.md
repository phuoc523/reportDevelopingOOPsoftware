# ScentStation — Audit & CI, branch test-2026-11-07-latest

## Scope

- 7 backend Maven modules: api-gateway, identity, catalog, order, machine, operation, reporting.
- 2 frontend Vite apps: admin-web, kiosk-web.
- Git consistency, routing, persistence, MQTT, Docker Compose, port collisions, MySQL health and branch merge coverage.
- Local audit generates independent timestamped logs in `E:\XD\ScentStation\reports\audit-1107-*`.
- GitHub Actions runs seven isolated backend jobs and two frontend builds on branch push / PR to main.

## Known gaps confirmed against the published branch

1. `docker-compose.yml` defines `services: {}` — no full-stack single-command deployment.
2. Gateway `application.yml` only has an Identity route; other service routes are pending.
3. Order README states orders, payments and reviews are stored in memory, lost after restart.
4. Machine branch no longer contains the prior `MqttDeviceMessageListener.java`; functionality must be verified, not assumed.
5. `operation-service` and `reporting-service` both default to port `8085`.
6. `origin/feature/p1-06-p1-11-platform` and `origin/test-order-payment` have commits not merged into 07/11 candidate.
7. Browser UI build success is not an E2E pass; payment/device integration still needs live tests.

## Safe workflow

1. Keep existing working branch `test-2026-11-07-latest` and no pending merge.
2. Extract package outside the Git repo to `E:\XD\ScentStation-Audit-1107`.
3. Run `RUN-CHECK-ALL.cmd` (or `scripts\CHECK-ALL-1107.ps1 -SkipFrontend` to reuse previous frontend PASS). It DOES NOT modify Git or push.
4. Review results and `SUMMARY.md`. Do not claim every service PASS unless the actual Maven tests pass.
5. Run `PUBLISH-CHECK-1107.cmd -IncludeLatestAudit` to stage *only* the audit/CI files and summary, commit, push without force.
6. Check Actions and repair failures; do not merge into `main` before E2E acceptance.

## Local machine constraints

- Java 25 installed, Maven 3.10 executable is on E; JDK declarations vary: 17, 21, 25.
- 12 GB RAM, recent JVM native-memory crash. Audit tests backend sequentially, caps Maven heap and Surefire heap, does not run all Spring services together.
- Original untracked `docker-compose.mysql.e.yml` is intentionally NOT committed.

## Important

This audit is evidence of tests *when run*, not a claim that an unexecuted test passed. CI results must be verified on GitHub Actions. Do not publish local error logs or `.env` secrets without review.
