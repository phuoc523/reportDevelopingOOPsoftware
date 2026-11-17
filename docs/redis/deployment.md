# Redis deployment notes

## Docker Compose

The root stack has one `redis:7.4-alpine` service with AOF enabled, a configurable port, a `redis-cli ping` health check, and optional `REDIS_PASSWORD` authentication. It publishes no port. Redis lives on the internal `scentstation-redis` network; all eight backend components join that network and retain `scentstation-backend` for databases and inter-service HTTP calls.

Compose backend health checks target the established Identity and Reporting health routes and Actuator/readiness health for Gateway, Catalog, Order, Machine, Operation, and Notification. Redis-dependent backends wait for Redis `service_healthy`; Gateway waits for Redis and all seven Spring Boot services. Order and Machine expose only the Actuator health endpoint; no other Actuator endpoints are opened.

Root Compose environment values point to hostname `redis`. Host-run applications use `REDIS_HOST=localhost`; standalone service Compose uses `REDIS_CONTAINER_HOST=redis`. Use the same `REDIS_PORT`, `REDIS_PASSWORD`, `REDIS_CONNECT_TIMEOUT`, `REDIS_TIMEOUT`, and `REDIS_CACHE_TTL` values in every backend.

## Standalone service Compose files

The standalone Catalog, Order, and Operation Compose files connect to the external networks created by root Compose. Bring up the root Redis/network before starting a standalone service. Do not add a second Redis container to those files.

## Local production-like hygiene

- Keep Redis un-published and on a private network.
- Supply `REDIS_PASSWORD`, `JWT_SECRET`, `INTERNAL_SERVICE_KEY`, and reporting sync credentials through a protected `.env` or secret manager.
- The checked-in `.env.example` contains placeholders/defaults for local development, not deployable secrets.
- Redis AOF helps local restart persistence but Redis remains a cache/rate-limit store, not a business source of truth.
- Monitor Redis health and memory. Do not use `FLUSHALL` on shared/staging/production Redis.
