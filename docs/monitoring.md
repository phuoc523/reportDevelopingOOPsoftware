# Prometheus and Grafana Monitoring

## Architecture and scope

Prometheus scrapes Spring Boot Actuator metrics from backend containers over the internal Compose network. Grafana reads Prometheus and provisions one overview dashboard. Prometheus evaluates the alert rules in `monitoring/prometheus/alerts.yml`; alert state is visible in Prometheus under **Alerts** and **Rules**.

This is the single alert-evaluation path. Alertmanager and Grafana-managed duplicate rules are intentionally not configured. There is no outbound email, chat, or Notification Service delivery in this local setup.

Monitoring helps operators observe service availability, HTTP behavior, JVM resource usage, garbage collection, and connection pools. It complements logs and tracing; it does not replace them or add business behavior.

## Backend audit

The repository currently has seven Spring Boot backend services. Each has Actuator and Micrometer Prometheus registry dependencies, exposes `health`, `info`, and `prometheus` on management port `9090`, and enables the HTTP request histogram. Docker Compose does not publish management port 9090 to the host.

| Backend | Compose service / Prometheus label | Application port | Metrics target | Expected data |
|---|---|---:|---|---|
| API Gateway | `api-gateway` | 8080 | `api-gateway:9090/actuator/prometheus` | HTTP, JVM, process |
| Identity Service | `identity-service` | 8081 | `identity-service:9090/actuator/prometheus` | HTTP, JVM, process |
| Catalog Service | `catalog-service` | 8082 | `catalog-service:9090/actuator/prometheus` | HTTP, JVM, process, HikariCP |
| Order Service | `order-service` | 8083 | `order-service:9090/actuator/prometheus` | HTTP, JVM, process, HikariCP |
| Machine Service | `machine-service` | 8084 | `machine-service:9090/actuator/prometheus` | HTTP, JVM, process, HikariCP |
| Operation Service | `operation-service` | 8085 | `operation-service:9090/actuator/prometheus` | HTTP, JVM, process, HikariCP |
| Reporting Service | `reporting-service` | 8086 | `reporting-service:9090/actuator/prometheus` | HTTP, JVM, process, HikariCP |

HikariCP series appear only when that service creates and uses a Hikari datasource. The Gateway has no datasource, so its Hikari panel series are expected to be absent. No Notification Service exists in the current `services/` tree, so there is no notification target to scrape.

Prometheus scrapes every 15 seconds, uses a 10-second scrape timeout, evaluates rules every 15 seconds, and retains local data for 15 days. Target service names are Compose DNS names and all monitoring/backend containers share the default Compose network.

## Start locally on Windows PowerShell

Set a non-default Grafana password for this local instance. A local `.env` file is git-ignored; alternatively set the variable in the current PowerShell session:

```powershell
$env:GRAFANA_ADMIN_USER = "admin"
$env:GRAFANA_ADMIN_PASSWORD = "choose-a-strong-local-password"
docker compose --profile monitoring up --build -d
docker compose --profile monitoring ps
```

The monitoring Compose profile is opt-in. The default stack can still be configured and started without a Grafana password or monitoring containers. If the `monitoring` profile is enabled without `GRAFANA_ADMIN_PASSWORD`, the Grafana container exits with a clear error instead of starting with a default credential.

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9091`
- Prometheus targets: `http://localhost:9091/targets`
- Prometheus rules/alerts: `http://localhost:9091/rules` and `http://localhost:9091/alerts`

Prometheus and Grafana host ports bind to loopback only. Backend management ports are not host-published and are not routed by the API Gateway. This is a local-development configuration, not a production ingress/TLS/authentication design.

To stop containers without deleting metrics or dashboard state:

```powershell
docker compose --profile monitoring down
```

Prometheus and Grafana use persistent named volumes. Do not pass `-v` to `down` unless you explicitly intend to delete their stored data.

## Grafana dashboard

Grafana automatically provisions the Prometheus datasource and dashboard from `monitoring/grafana/provisioning/` and `monitoring/grafana/dashboards/`. The dashboard has a multi-select **Service** filter and these panels:

- Backend availability (`up`)
- HTTP requests/second
- HTTP 5xx percentage (undefined/no series when no requests have been observed)
- HTTP p95 latency
- JVM heap utilization percentage
- JVM process CPU usage
- JVM time spent in garbage collection
- HikariCP active and maximum connections

HTTP request histograms are explicitly enabled using `management.metrics.distribution.percentiles-histogram.http.server.requests: true` in every service. The p95 panel and alert use the Prometheus `_bucket` series and suppress results when the service has no request traffic. If traffic has not occurred yet, request-rate/latency panels can correctly show no data rather than a fabricated zero.

Metric families used include `http_server_requests_seconds_count`, `http_server_requests_seconds_bucket`, `jvm_memory_used_bytes`, `jvm_memory_max_bytes`, `process_cpu_usage`, `jvm_gc_pause_seconds_sum`, and (where a datasource exists) `hikaricp_connections_active` and `hikaricp_connections_max`. Prometheus attaches the static `service` label from the scrape target.

## Alert rules

Thresholds and hold times are centralized in `monitoring/prometheus/alerts.yml` so they can be tuned for the project without editing application code:

| Alert | Condition | Hold time | Severity |
|---|---|---:|---|
| `BackendDown` | A configured target has `up == 0` | 1 min | critical |
| `PrometheusTargetMissing` | Fewer than the expected 7 targets, or no targets | 2 min | warning |
| `HighHttp5xxRate` | 5xx rate is above 5%, with non-zero request traffic | 5 min | warning |
| `HighHttpLatency` | p95 exceeds 1 second, with non-zero request traffic | 5 min | warning |
| `HighJvmHeapUsage` | Heap exceeds 85% of maximum | 10 min | warning |

To tune a threshold, edit its value and/or `for` duration in `alerts.yml`. If the backend inventory changes, update the static target list in `prometheus.yml` and the expected count in `PrometheusTargetMissing`. The traffic guards prevent 5xx and latency alerts from firing for absent/no-request data; heap rules require a positive maximum. Prometheus target-down series are produced by failed scrapes, while the separate missing-target rule checks the expected inventory count.

The rules evaluate and expose firing/pending state in Prometheus; they do not send notifications because no Alertmanager receiver is configured. This avoids coupling monitoring to the separately owned Notification Service.

## Verification commands

Validate both the normal stack and monitoring profile (these configuration checks do not require Docker Engine):

```powershell
docker compose config --quiet
$env:GRAFANA_ADMIN_PASSWORD = "choose-a-strong-local-password"
docker compose --profile monitoring config --quiet
```

When Prometheus tools are installed, validate configuration and rules:

```powershell
promtool check config monitoring\prometheus\prometheus.yml
promtool check rules monitoring\prometheus\alerts.yml
```

After startup:

1. Run `docker compose --profile monitoring ps`.
2. Confirm all seven scrape targets are `UP` at `http://localhost:9091/targets`.
3. Query `up{job="scentstation-services"}` and confirm seven series.
4. Query `http_server_requests_seconds_count`, `jvm_memory_used_bytes`, and `process_cpu_usage`. Generate a few HTTP requests first if request counters/histograms are not yet present.
5. In Grafana, open **ScentStation → ScentStation Backend Services**, select one or more services, and verify the panels have values where the corresponding meters exist.
6. Open Prometheus `/rules` and `/alerts` to check rule load and alert state.

Optional safe alert smoke test, only when using the local development Compose stack:

```powershell
docker compose stop reporting-service
# Wait at least 2 minutes, then inspect Prometheus /alerts for BackendDown.
docker compose start reporting-service
```

Stopping a service interrupts its local requests; do not use this procedure against shared or production environments.

## Troubleshooting

- **MySQL stays unhealthy during first startup:** the database healthcheck uses the configured application account and runs `SELECT 1`. Initial MySQL datadir initialization can take several minutes; all six database healthchecks now have a seven-minute `start_period` so connection refusals during cold startup do not exhaust retries before MySQL listens on port 3306. Keep existing named volumes; do not recreate or delete datadirs to address startup delay.
- **Spring service reports `UnknownHostException` for a database:** confirm the database container is healthy and attached to the Compose network, then inspect Docker Engine/network logs. Do not change application database configuration unless the hostname or network is actually incorrect.
- **Docker CLI returns Engine API 500 or host monitoring ports time out:** Docker Desktop can report `running` while its Engine API or host-port forwarding is unresponsive. Avoid resetting/removing volumes; collect `docker info`, `docker compose ps -a`, and container logs, then recover the Engine before claiming runtime acceptance.
- **Target is DOWN:** inspect `docker compose --profile monitoring logs <service>`, ensure the service listens on management port 9090, and verify the Compose service name/target in `prometheus.yml`.
- **Prometheus or Grafana does not start:** check Docker Engine with `docker info`, then inspect `docker compose --profile monitoring ps` and logs.
- **Grafana exits immediately:** set non-empty `GRAFANA_ADMIN_PASSWORD`; no default password is installed by the Compose file.
- **Dashboard panels show no data:** check target status, choose the correct service filter, generate traffic, and confirm the metric exists at that service's `/actuator/prometheus`. Hikari is not expected for the API Gateway.
- **p95 panel is empty:** verify `_bucket` metrics exist and there has been request traffic; the HTTP histogram is enabled in each backend configuration.
- **Prometheus rule check is unavailable:** install/use `promtool` matching the Prometheus version. Do not treat Compose YAML validation as PromQL/rule validation.

## Runtime diagnosis and acceptance evidence (2026-11-17)

### MySQL healthcheck diagnosis

The healthcheck and credentials were valid; failing probes recorded `ERROR 2003 ... Can't connect to MySQL server on '127.0.0.1:3306'` while MySQL was still initializing. Cold-start logs show initialization beginning at `06:25:36Z`, the initialization phase ending at `06:27:51Z`, and the final TCP server becoming ready on port 3306 at `06:32:01Z` (about 6 minutes 25 seconds after initialization began). Previously, the healthcheck had a five-second interval and 30 retries but no `start_period`, so it could become unhealthy after about 2 minutes 30 seconds, before the first full startup completed.

The only database configuration change was adding `start_period: 7m` to all six MySQL healthchecks. Existing named volumes were retained. On the observed startup after the change, initial connection refusals were followed by successful healthcheck output `1` and all six databases reached `healthy`. No credentials, schema, migrations, or datadirs were reset.

### Runtime limitations

`docker compose --profile monitoring up -d` completed successfully on retry (exit code 0). The last successful Compose status showed Prometheus, Grafana, API Gateway, Identity, Operation, and Reporting running, with all six databases healthy. Catalog, Machine, and Order exited with code 1; their logs show `UnknownHostException` for the configured database hostname during Docker networking. No application source or database configuration was changed.

Prometheus startup logs report successful configuration loading, `Server is ready to receive web requests`, and startup of the rule manager. However, the Docker Engine later returned HTTP 500 for API `_ping`/container inspection, and direct host requests to Prometheus and Grafana timed out. Prometheus targets, Grafana datasource/dashboard provisioning, dashboard panels, and alert pending/firing state could therefore not be queried. A running container or a loaded rule file is not evidence that these HTTP/API checks passed.

### Seven configured scrape targets

The following are the last backend container states returned by Compose. The actual Prometheus UP/DOWN value was unavailable because its API did not respond:

| Prometheus service label | Last container state | Scrape state |
|---|---|---|
| `api-gateway` | Running | UNVERIFIED |
| `identity-service` | Running | UNVERIFIED |
| `catalog-service` | Exited (1) | UNVERIFIED; expected target unavailable |
| `order-service` | Exited (1) | UNVERIFIED; expected target unavailable |
| `machine-service` | Exited (1) | UNVERIFIED; expected target unavailable |
| `operation-service` | Running | UNVERIFIED |
| `reporting-service` | Running | UNVERIFIED |

| Check | Result | Evidence / limitation |
|---|---|---|
| Git branch and merge state | PASS | Branch `feature/machine-iot`; no `MERGE_HEAD` and no unmerged paths. Existing worktree modifications were preserved. |
| Backend inventory and endpoint configuration | PASS | Seven backend POMs have Actuator and Prometheus registry; seven application configs expose management port 9090, health/info/prometheus, and HTTP histograms. |
| Default Compose config without Grafana password | PASS | `docker compose config --quiet` exited successfully with `GRAFANA_ADMIN_PASSWORD` unset. |
| Monitoring profile Compose config without password | PASS | `docker compose --profile monitoring config --quiet` parsed; Grafana runtime guard requires the password if the profile is started. |
| Prometheus/Grafana YAML syntax | PASS | All four YAML files parsed with the repository's available SnakeYAML library. This does not replace Prometheus semantic/rule validation. |
| Grafana dashboard JSON | PASS | Parsed with PowerShell `ConvertFrom-Json`; eight required panels and the Service variable were checked. |
| Backend package build | PARTIAL | Machine, Order, Catalog, Identity, Gateway, and Reporting builds passed. Operation Service build failed on existing Lombok/annotation-processing errors under installed JDK 25; no Operation Service source was changed for monitoring. |
| Live Actuator metrics endpoint | PASS | Local API Gateway returned HTTP 200 on `http://127.0.0.1:9090/actuator/prometheus`; output included `http_server_requests_seconds_bucket`, `jvm_memory_used_bytes`, `jvm_gc_pause_seconds`, and `process_cpu_usage`. This proves the Gateway endpoint only, not Docker-network scraping. |
| MySQL healthcheck and named data volumes | PASS | All six databases reached `healthy` after increasing cold-start `start_period`; their named volumes were retained. |
| Docker monitoring stack `up -d` / `ps` | PARTIAL | `up -d` succeeded on retry (exit 0). The last successful `ps` showed all six DBs healthy, Prometheus/Grafana and four backends running; Catalog, Machine, and Order exited with code 1. |
| Backend startup errors | BLOCKED | Catalog, Machine, and Order logs ended in database-host `UnknownHostException` during Docker network startup. Retrying them was blocked by subsequent Engine API `_ping` HTTP 500. |
| Prometheus container/config/rule startup | PARTIAL | Prometheus logged successful config loading, server readiness, and rule-manager startup. Host `/-/ready`, targets, query, and rules requests timed out, so live target and alert states were not retrieved. |
| Prometheus seven target states | BLOCKED | Seven targets are configured in `prometheus.yml`, but the Prometheus HTTP API was not reachable; no target is claimed UP from static configuration. |
| Grafana container/API/provisioning | BLOCKED | Grafana container was running in the last successful Compose status, but `/api/health` timed out; datasource and dashboard provisioning were not verified through the API. |
| Dashboard panel data and live metrics queries | BLOCKED | No successful Prometheus API query or Grafana dashboard query was obtained in this run. |
| `promtool` config/rule validation | SKIPPED | `promtool` is not installed. Prometheus loaded its configuration and started its rule manager, but API rule state and `promtool` validation remain unavailable. |
| Alert state/smoke test | BLOCKED | Prometheus `/api/v1/rules` and `/alerts` did not respond; no service-down smoke test was run while the Engine API was returning 500. |
| Docker Engine stability | BLOCKED | Docker Desktop reported `running`, but later Engine `_ping`/inspect calls returned HTTP 500 or hung; host probes to ports 9091 and 3000 timed out. |
| Notification delivery | OUT OF SCOPE | No Notification Service integration or Alertmanager receiver is configured. |

Overall result: **PARTIAL / BLOCKED**. The MySQL cold-start healthcheck issue is corrected and all six databases were observed healthy. Full monitoring acceptance still requires a stable Docker Engine, all seven backend containers running, successful Prometheus target/metric API queries, Grafana datasource/dashboard API verification, and alert-state verification. Do not report the seven targets or Grafana dashboard as runtime PASS until those checks succeed.
