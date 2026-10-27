# ScentStation API Gateway (P1-07)

Java 21 / Spring Boot 3.5.6 / Spring Cloud 2025.0.3 Gateway WebFlux.

## Local build

PowerShell in `services/api-gateway`:

```powershell
$env:JAVA_HOME='E:\XD\jdk-21\jdk-21.0.12.1+1'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:MAVEN_OPTS='-Xms32m -Xmx384m -XX:+UseSerialGC -Djava.io.tmpdir=E:\XD\temp'
& 'E:\XD\apache-maven-3.10.0-bin\apache-maven-3.10.0\bin\mvn.cmd' '-Dmaven.repo.local=E:\XD\.m2\repository' '-DforkCount=0' test
```

## Runtime

`GATEWAY_PORT=8080` default. `IDENTITY_URL=http://127.0.0.1:8081` default; remaining `CATALOG_URL`, `OPERATION_URL`, `ORDER_URL`, `MACHINE_URL`, `REPORTING_URL` **disabled until confirmed**. `CORS_ALLOWED_ORIGINS` comma-separated exact origins.

```powershell
& "$env:JAVA_HOME\bin\java.exe" -Xms32m -Xmx256m -XX:+UseSerialGC -jar '.\target\api-gateway-0.1.0-SNAPSHOT.jar'
```

Package with `mvn -DskipTests package` first. Proxy `/api/v1/identity/auth/login` to Identity `/api/v1/auth/login`; do not expose `/api/v1/identity/internal/...`.

**Security:** Identity and downstream services independently validate opaque tokens and resource tenants. Filter strips trusted/internal headers; this is not a replacement for private networking and service authentication.
