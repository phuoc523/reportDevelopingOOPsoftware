# Prometheus, Alertmanager and Grafana Monitoring

## Scope

Prometheus scrapes Micrometer metrics from all eight backend components on the
internal `scentstation-backend` Docker network. Alertmanager forwards firing
and resolved alerts to Notification Service. Grafana provisions the Prometheus
datasource and the `ScentStation Backend Services` dashboard.

| Component | Metrics target |
|---|---|
| API Gateway | `api-gateway:8080/actuator/prometheus` |
| Identity Service | `identity-service:8081/actuator/prometheus` |
| Catalog Service | `catalog-service:8082/actuator/prometheus` |
| Order Service | `order-service:8083/actuator/prometheus` |
| Machine Service | `machine-service:8084/actuator/prometheus` |
| Operation Service | `operation-service:8085/actuator/prometheus` |
| Reporting Service | `reporting-service:8086/actuator/prometheus` |
| Notification Service | `notification-service:8087/actuator/prometheus` |

Management endpoints remain on each application's internal port. Only
`health`, `info`, and `prometheus` are exposed. Prometheus and Grafana bind to
loopback and are not routed through the API Gateway.

## Start

Set a local Grafana password in the git-ignored root `.env` file:

```dotenv
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=replace-with-a-strong-local-password
NOTIFICATION_MONITORING_DEFAULT_TENANT_ID=1
```

Start the normal stack and the opt-in monitoring profile:

```powershell
docker compose --profile monitoring up -d --build
docker compose --profile monitoring ps
```

- Prometheus: `http://localhost:9091`
- Targets: `http://localhost:9091/targets`
- Rules: `http://localhost:9091/rules`
- Alertmanager: `http://localhost:9093`
- Grafana: `http://localhost:3001`

Grafana uses port `3001` because the Mock Payment Web already uses port
`3000`. The Grafana container refuses to start when
`GRAFANA_ADMIN_PASSWORD` is empty.

## Dashboard and alerts

The provisioned dashboard contains availability, request rate, HTTP 5xx,
p95 latency, JVM heap, CPU, garbage collection, and HikariCP panels. The
service selector is populated from the Prometheus `service` label.

Prometheus evaluates the rules in `monitoring/prometheus/alerts.yml`:

- backend unavailable;
- fewer than eight configured targets;
- HTTP 5xx rate above 5%;
- p95 latency above one second;
- JVM heap usage above 85%.

Alertmanager sends its standard webhook payload to
`POST /internal/events/prometheus-alerts`. The request uses the same
`INTERNAL_SERVICE_KEY` as a Bearer credential. A short-lived initialization
container writes this value to a Docker named volume; the key is never written
to a tracked file. Notification Service:

- resolves recipients through Identity Service and tenant RBAC;
- creates Vietnamese `SYSTEM_ALERT` notifications;
- stores firing and resolved states separately;
- uses a SHA-256 event key so Alertmanager retries do not create duplicates.

Infrastructure alerts do not naturally carry a tenant. For local/demo use,
`NOTIFICATION_MONITORING_DEFAULT_TENANT_ID` selects the recipient tenant. A
rule with a `tenant_id` label takes precedence over this default.

## Verification

```powershell
docker compose --env-file .env.example config --quiet
$env:GRAFANA_ADMIN_PASSWORD = "local-verification-password"
docker compose --env-file .env.example --profile monitoring config --quiet
Invoke-WebRequest http://localhost:9091/-/ready
Invoke-WebRequest http://localhost:9093/-/ready
Invoke-WebRequest http://localhost:3001/api/health
```

Then verify that all eight targets are `UP` and query:

```promql
up{job="scentstation-services"}
http_server_requests_seconds_count
jvm_memory_used_bytes
```

Prometheus, Alertmanager, the generated Alertmanager credential, and Grafana
use named volumes. Do not run `docker compose down -v` unless deleting
monitoring and database data is intentional.
