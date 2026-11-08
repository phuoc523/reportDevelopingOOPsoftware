# Giai đoạn 1: Build ứng dụng Spring Boot
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy mã nguồn và cấu hình Maven
COPY pom.xml .
COPY src ./src

# Đóng gói ứng dụng thành JAR
RUN mvn clean package -DskipTests -B

# Giai đoạn 2: Chạy ứng dụng bằng Java 21
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
