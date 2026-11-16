FROM maven:3.9-eclipse-temurin-21-alpine AS build
ARG SERVICE
WORKDIR /workspace
COPY services/${SERVICE}/ .
RUN --mount=type=cache,target=/root/.m2,sharing=shared mvn -B -ntp -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S -G app app
COPY --from=build /workspace/app.jar .
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
