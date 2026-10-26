FROM maven:3.9.9-eclipse-temurin-25-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/order-service-0.0.1-SNAPSHOT.jar app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
