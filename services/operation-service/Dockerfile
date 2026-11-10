FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package \
    && mv target/operation-service-0.0.1-SNAPSHOT.jar /app/app.jar

FROM eclipse-temurin:21.0.9_10-jre-jammy
WORKDIR /app
RUN groupadd --system appuser \
    && useradd --system --gid appuser --home-dir /app --shell /usr/sbin/nologin appuser
COPY --from=build --chown=appuser:appuser /app/app.jar app.jar
USER appuser
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]
