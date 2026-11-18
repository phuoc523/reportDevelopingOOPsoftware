ARG MYSQL_DRIVER_VERSION=8.4.0

FROM alpine:3.21 AS driver
ARG MYSQL_DRIVER_VERSION
RUN apk add --no-cache ca-certificates \
    && wget -q -O /mysql-connector-j.jar \
    "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/${MYSQL_DRIVER_VERSION}/mysql-connector-j-${MYSQL_DRIVER_VERSION}.jar"

FROM apache/nifi:2.4.0
USER root
COPY --from=driver /mysql-connector-j.jar /opt/nifi/nifi-current/lib/mysql-connector-j.jar
RUN chown nifi:nifi /opt/nifi/nifi-current/lib/mysql-connector-j.jar
USER nifi
