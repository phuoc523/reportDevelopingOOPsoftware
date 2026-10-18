# Shared API Conventions

## 1. Purpose

This document defines common API conventions for ScentStation services.
All services should follow these rules to keep APIs consistent and predictable.

## 2. URL and Resource Naming

- Use the `/api/v1` prefix for public REST API endpoints.
- Use nouns to represent resources.
- Use plural resource names where appropriate.
- Use lowercase letters and hyphens in URL paths.
- Avoid verbs in resource paths when standard HTTP methods can express the action.

Examples:
- `GET /api/v1/products`
- `GET /api/v1/products/{id}`
- `POST /api/v1/products`
- `PUT /api/v1/products/{id}`
- `PATCH /api/v1/products/{id}`
- `DELETE /api/v1/products/{id}`

## 3. HTTP Methods

- `GET`: Retrieve resources without changing server state.
- `POST`: Create a resource or perform an operation that does not fit other methods.
- `PUT`: Replace a resource.
- `PATCH`: Partially update a resource.
- `DELETE`: Delete a resource.

## 4. Request and Response Format

- Use JSON for request and response bodies unless an endpoint specifies otherwise.
- Set `Content-Type: application/json` when sending JSON.
- Use UTF-8 encoding.
- Use consistent field names across services.
- Use ISO 8601 timestamps with an explicit timezone, preferably UTC.

Example response:

    {
      "id": "123",
      "name": "Example product",
      "createdAt": "2026-01-01T10:00:00Z"
    }

## 5. HTTP Status Codes

- `200 OK`: Request completed successfully.
- `201 Created`: Resource created successfully.
- `202 Accepted`: Request accepted for asynchronous processing.
- `204 No Content`: Request succeeded without a response body.
- `400 Bad Request`: Invalid request syntax or parameters.
- `401 Unauthorized`: Authentication is required or invalid.
- `403 Forbidden`: Authenticated caller lacks permission.
- `404 Not Found`: Resource does not exist.
- `409 Conflict`: Request conflicts with current resource state.
- `422 Unprocessable Entity`: Request validation failed, where adopted.
- `429 Too Many Requests`: Rate limit exceeded.
- `500 Internal Server Error`: Unexpected server error.
- `502 Bad Gateway`: Gateway received an invalid upstream response.
- `503 Service Unavailable`: Service is temporarily unavailable.
- `504 Gateway Timeout`: Upstream service timed out.

## 6. Standard Error Response

Services should return a consistent JSON error structure.

Example:

    {
      "timestamp": "2026-01-01T10:00:00Z",
      "status": 400,
      "code": "VALIDATION_ERROR",
      "message": "Request validation failed",
      "path": "/api/v1/products",
      "traceId": "example-trace-id",
      "details": [
        {
          "field": "name",
          "message": "Name is required"
        }
      ]
    }

Do not expose stack traces, passwords, tokens, or internal infrastructure details
in client-facing error responses.

## 7. Validation

- Validate required fields, formats, lengths, and numeric ranges.
- Reject invalid input with an appropriate 4xx status.
- Return actionable but safe validation messages.
- Do not silently accept invalid values.

## 8. Authentication and Authorization

- Protected endpoints should use the project's agreed authentication mechanism.
- When using bearer tokens, send them in the `Authorization: Bearer <token>` header.
- Return `401` for missing or invalid authentication.
- Return `403` when the caller is authenticated but lacks permission.
- Never log access tokens or credentials.

## 9. API Gateway and CORS

- Client applications should use the configured API Gateway entry point.
- The gateway should route requests to the appropriate service.
- CORS origins, methods, and headers should be explicitly configured for each environment.
- Do not use unrestricted origins with credentials in production.
- Forward correlation or trace identifiers when available.

## 10. Pagination, Filtering, and Sorting

Collection endpoints should use consistent query parameters.

Suggested parameters:
- `page`: zero-based page index.
- `size`: number of items per page.
- `sort`: field and direction, for example `name,asc`.
- Resource-specific filters such as `status` or `available`.

Example:

    GET /api/v1/products?page=0&size=20&sort=name,asc

Services should document their default and maximum page sizes.

## 11. Timeouts, Retries, and Idempotency

- Configure explicit connection and response timeouts for service-to-service calls.
- Retry only failures that are safe to retry.
- Use bounded retries and backoff to avoid overwhelming dependencies.
- Avoid automatic retries of non-idempotent operations unless protected by an idempotency mechanism.
- Consider an idempotency key for operations where duplicate submissions could cause harm.

## 12. Logging and Traceability

- Use structured logs where practical.
- Include a request or trace identifier in logs.
- Do not log passwords, tokens, or sensitive personal information.
- Log failures with enough context for troubleshooting without exposing secrets.

## 13. Versioning and Compatibility

- Use an explicit API version for public endpoints, such as `/api/v1`.
- Prefer backward-compatible changes within an existing version.
- Avoid renaming or removing response fields without a migration plan.
- Document breaking changes and coordinate them with dependent services.

## 14. Documentation and Testing

For each endpoint, document:
- Purpose and HTTP method.
- Path and query parameters.
- Request body and response schema.
- Authentication and authorization requirements.
- Possible status codes and error responses.

Tests should cover successful requests, invalid input, missing resources,
authorization failures, and relevant edge cases.

## 15. Adoption

These conventions are the shared baseline for ScentStation services.
Existing services may require incremental updates. Any intentional deviation
should be documented and agreed upon by the team.
