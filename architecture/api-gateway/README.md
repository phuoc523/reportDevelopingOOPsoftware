# API Gateway diagrams

- `routing-architecture.puml`: configured route prefixes and downstream services.
- `trust-boundary-sequence.puml`: current header sanitization, internal-path blocking and request ID behavior.

JWT validation and RBAC remain responsibilities of downstream services. The known `x-actor-*` filtering gap is shown explicitly.
