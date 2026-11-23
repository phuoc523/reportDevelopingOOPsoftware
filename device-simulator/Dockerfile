FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/device-simulator-1.0.0-SNAPSHOT.jar /app/device-simulator.jar
COPY config /app/config
ENV SIMULATOR_SLOT_CONFIG=/app/config/demo-9101.json
ENTRYPOINT ["java", "-jar", "/app/device-simulator.jar"]
