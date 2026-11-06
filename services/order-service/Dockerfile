
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY database ./database
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/order-service-0.1.0-SNAPSHOT.jar app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
