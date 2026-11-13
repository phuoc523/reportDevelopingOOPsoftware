# Notification Service Phase 1 — Implementation Log

## 1. Phase objective

Create the foundation for an independent `notification-service` microservice that follows the repository’s Spring Boot and Maven conventions without changing existing application logic or cross-service code. This phase includes the Spring Boot application, Maven setup, environment-based configuration, health endpoint, and Docker packaging, but intentionally excludes any notification business logic or recipient APIs.

## 2. CodeGraph usage

The required CodeGraph pass was performed before implementation changes. The repository index was used to confirm existing service conventions and inspect the new `NotificationServiceApplication` class. CodeGraph did not uniquely index the new POM or `application.yml`, so those files were verified through targeted source inspection instead. The repository index was used to verify relevant patterns from:

- `services/api-gateway` (gateway routing and trust filtering)
- `services/identity-service` (Spring Security and JWT handling)
- `services/order-service` (HTTP client conventions and service layout)
- `services/machine-service` (alerting + integration pattern)

Direct source verification was then used for the exact files needed to match the repo’s established structure, especially:

- `services/catalog-service/pom.xml`
- `services/catalog-service/Dockerfile`
- `services/api-gateway/src/main/resources/application.yml`
- `services/machine-service/src/main/resources/application.properties`
- `services/identity-service/src/main/resources/application.yml`

## 3. Existing services inspected

The following existing services were inspected for convention matching:

- `api-gateway` — route patterns, trust boundary filtering, port conventions
- `identity-service` — Spring Boot 3, Java 21, MySQL config, JWT security model
- `catalog-service` — Maven dependency pattern, Docker layout, actuator usage
- `machine-service` — port selection, MySQL configuration, alerting/outbox integration conventions

## 4. New service directory structure

Created under `services/notification-service/`:

- `services/notification-service/pom.xml`
- `services/notification-service/Dockerfile`
- `services/notification-service/src/main/java/com/scentstation/notification/NotificationServiceApplication.java`
- `services/notification-service/src/main/resources/application.yml`
- `services/notification-service/src/test/java/com/scentstation/notification/NotificationServiceApplicationTests.java`
- `services/notification-service/src/test/resources/application-test.properties`

This is intentionally limited to the service foundation; no notification entities, repositories, business services, or API endpoints were created.

## 5. Maven and Spring Boot versions

- Java version: `21`
- Spring Boot version: `3.4.5`
- Build tool: Maven
- Source of the version selection: repository-aligned parent version used by the current service set, especially `catalog-service/pom.xml`.

## 6. Dependencies added and reasons

`services/notification-service/pom.xml` contains:

- `spring-boot-starter-web` — to allow Spring Boot startup and app lifecycle handling
- `spring-boot-starter-actuator` — repository convention already uses actuator for health endpoints in catalog-service and other modules
- `spring-boot-starter-data-jpa` — reserved for the independent notification database foundation, even though no entities are yet implemented
- `spring-boot-starter-validation` — standard in ScentStation backend services
- `mysql-connector-j` — required for the independent MySQL database configuration
- `spring-boot-starter-test` — standard testing support
- `h2` — test-scope database for local build verification without a live MySQL service

No unrelated messaging or cloud integrations were added.

## 7. Application configuration

File: `services/notification-service/src/main/resources/application.yml`

The service uses environment-variable-based configuration for:

- server port
- MySQL URL
- database username
- database password
- JPA options

The configuration is intentionally limited to the foundation layer and does not create or validate notification tables yet.

## 8. Selected port and reason

Selected port: `8087`

Reason:

- `api-gateway` routes to 8080 through 8086 (`services/api-gateway/src/main/resources/application.yml`)
- `identity-service`=8081, `catalog-service`=8082, `order-service`=8083, `machine-service`=8084, `operation-service`=8085, `reporting-service`=8086
- 8087 is not currently assigned to any verified service in the repo in the active configuration

This port was chosen to avoid conflicts while keeping an independent service port footprint.

## 9. MySQL database configuration

Configured database name: `scentstation_notification`

Runtime configuration in `application.yml`:

- `NOTIFICATION_DB_URL` default: `jdbc:mysql://127.0.0.1:3306/scentstation_notification?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- `NOTIFICATION_DB_USERNAME` default: `notification_app`
- `NOTIFICATION_DB_PASSWORD` has no hardcoded default and should be provided through the environment.

This is a separate database from all other currently verified backend service databases.

The schema is not created yet (`ddl-auto: validate`), in line with the “Phase 2 will implement schema” requirement.

## 10. Health endpoint

The service uses Spring Boot Actuator and exposes the health endpoint at:

- `/actuator/health`

This matches the repository convention of service health checks without introducing custom auth or business APIs.

## 11. Dockerfile implementation

File: `services/notification-service/Dockerfile`

Implemented Dockerfile pattern follows the repository’s existing Java 21 Docker conventions (for example `catalog-service/Dockerfile`):

- multi-stage Maven build
- Java 21 runtime image
- non-root app user
- `EXPOSE 8087`
- `java -jar app.jar`

No root Docker Compose files were modified.

## 12. Build and test commands

Executed successfully:

- `cd services/notification-service && mvn test -q`
- `cd services/notification-service && mvn package -q`

## 13. Actual verification results

### Maven test result

`mvn test -q` passed successfully.

The application context loaded successfully using the H2 test profile and the actuator health endpoint returned `UP`.

### Maven package result

`mvn package -q` passed successfully.

The verification environment used Maven 3.10.0 with JDK 25.0.1; the project compiler target remains Java 21 as configured in `pom.xml`.

### Database verification

A live MySQL server was not started or connected as part of this phase. The runtime configuration is defined for an independent notification database, but database connectivity was not verified against a running MySQL instance.

## 14. Problems and limitations

- The repository does not currently have a live, running MySQL instance for this service in the local environment, so the configured database was not connectivity-tested.
- The service intentionally has no notification entity, repository, or controller logic; this is valid for Phase 1 but does not yet provide business behavior.
- The root Docker Compose remains a skeleton; no service orchestration was modified.
- CodeGraph indexed and returned the new Spring Boot application class, but did not uniquely index the new POM or YAML configuration; those were verified by targeted source inspection.
- CodeGraph does not validate runtime DB connectivity.

## 15. Files created or modified

Created under `services/notification-service/`:

- `pom.xml`
- `Dockerfile`
- `src/main/java/com/scentstation/notification/NotificationServiceApplication.java`
- `src/main/resources/application.yml`
- `src/test/java/com/scentstation/notification/NotificationServiceApplicationTests.java`
- `src/test/resources/application-test.properties`

No existing application source code or configuration outside `services/notification-service/` was modified.

## 16. Final assessment

The Phase 1 foundation for the independent `notification-service` is now in place and builds successfully. The module has its own Spring Boot application, Java 21/Maven configuration, environment-based MySQL settings for `scentstation_notification`, actuator health support, and a Dockerfile following the repo’s standard pattern.

This phase intentionally stops at the foundation layer and does not implement notification entities, routing, delivery logic, or inter-service alert consumption. Those remain for later phases.
