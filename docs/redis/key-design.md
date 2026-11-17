# Key design and tenant isolation

## Namespace

Every key starts with `scentstation:{service}:v1:`. This separates service ownership and allows an incompatible key or payload change to introduce `v2` without colliding with old data. Gateway counters use the same service/version convention.

## Tenant keys

Tenant keys are built only after the service has resolved the authenticated actor and authorized the requested scope:

- Identity tenant display reads are keyed by the resolved tenant ID; current tenant status is queried independently from the database.
- Catalog verifies read permission and resolves the effective tenant before reading or writing a product-list key. A request cannot select a cache key by directly injecting another tenant ID.
- Machine authorizes the location-list request before the tenant-scoped service read.
- Operation checks the requested ticket belongs to the tenant before reading a `(tenantId, ticketId)` key.
- Reporting derives the tenant/user rate-limit scope from the authenticated actor after Identity authorization; it does not cache dashboard payloads because they include live alerts, machine state, and inventory.
- Notification applies read rate limiting only after the Identity authorization response is verified. Keys are scoped by tenant and user; service-principal reads use a tenant-scoped counter. Notification payloads, read state, active alerts, delivery jobs, and FCM tokens are not cached.
- Order uses the authenticated principal's tenant for static status metadata, even though the payload is static.

Tenant identifiers are part of data cache keys. Reporting encodes the tenant ID as URL-safe Base64 to keep the key component unambiguous. Cache values are never used to establish identity, tenant status, permission, or ticket ownership.

The Gateway, Reporting, and Notification rate-limit keys are counters, not data caches. They hash verified tenant/user identity; the Gateway uses the immediate peer IP for absent or invalid tokens and never trusts forwarded headers. Redis stores no authentication result, authorization decision, or token.

## Serialization and versioning

Redis stores strings and JSON payloads with explicit DTO/record representations. This avoids Java native object serialization and polymorphic type metadata. Cache keys have a `v1` marker; bump the version when key semantics or JSON shape becomes incompatible.

## Security boundaries that must remain live

Never use these Redis values as a decision source:

- Access/refresh tokens, sessions, JWT revocation, or token validity.
- RBAC grants or authentication/authorization decisions.
- Tenant suspension/active status.
- Payment state, order transitions, dispensing idempotency, device safety, heartbeat, or inventory movement/availability.

Identity and endpoint security checks execute before cache access. The service remains the source of truth for the protected action.
