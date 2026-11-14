# Identity Service diagrams

Scope: authentication, JWT, current principal, RBAC, tenant/account administration and audit.

The internal authorization contract is `POST /internal/auth/introspect`. Diagrams omit exhaustive Role/Permission constants and accessor methods because these do not improve the architectural view.
