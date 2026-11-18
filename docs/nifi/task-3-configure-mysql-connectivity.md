# Task 3: Configure MySQL Connectivity

## Runtime architecture

NiFi runs from `infra/nifi/docker-compose.yml` and joins the existing external
Docker network `scentstation-backend`. Start the main ScentStation Compose
stack first so the network and MySQL containers exist.

The custom NiFi image in `infra/nifi/Dockerfile` downloads MySQL Connector/J
from Maven Central during image build. The version is pinned by
`MYSQL_DRIVER_VERSION`; no driver source tree or binary JAR is committed.

Inside the container, the driver is available at:

```text
/opt/nifi/nifi-current/lib/mysql-connector-j.jar
```

## Local credentials

Create a local, ignored environment file:

```powershell
Copy-Item infra/nifi/.env.example infra/nifi/.env
```

Replace `NIFI_PASSWORD` with a private value of at least 12 characters. Do not
commit `infra/nifi/.env`.

## Start NiFi

```powershell
docker compose up -d
docker compose --env-file infra/nifi/.env -f infra/nifi/docker-compose.yml up -d --build
docker compose --env-file infra/nifi/.env -f infra/nifi/docker-compose.yml ps
```

Open `https://localhost:8443/nifi/`. A browser warning is expected for the
local self-signed certificate.

## DBCP connection pools

Create the following `DBCPConnectionPool` Controller Services in NiFi:

### Order database

- Database Connection URL:
  `jdbc:mysql://order-db:3306/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- Database Driver Class Name: `com.mysql.cj.jdbc.Driver`
- Database Driver Location: leave empty because the driver is installed in
  NiFi's application library.
- Database User: `order_user`
- Password: use the local Compose development password.
- Validation Query: `SELECT 1`

### Reporting database

- Database Connection URL:
  `jdbc:mysql://reporting-db:3306/scentstation_reporting?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
- Database Driver Class Name: `com.mysql.cj.jdbc.Driver`
- Database Driver Location: leave empty.
- Database User: `reporting_app`
- Password: use the local Compose development password.
- Validation Query: `SELECT 1`

Enable both Controller Services and use NiFi's Verify action. The local
database credentials currently live in the root development Compose file;
production deployments must inject secret-managed credentials instead.

## Important boundary

The NiFi flow is an administrative ETL flow that reads Order Service data and
writes Reporting Service projections. It must not be used as an alternative
write path into transactional service-owned tables. The current branch
contains the runtime and documented processor configuration, but it does not
yet contain an automatically versioned NiFi flow definition.
