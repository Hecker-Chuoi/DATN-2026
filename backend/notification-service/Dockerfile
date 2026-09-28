# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy pom files for monorepo dependency caching (parent pom lists every module)
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY identity-service/pom.xml ./identity-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY work-service/pom.xml ./work-service/
COPY notification-service/pom.xml ./notification-service/

# Copy source code of common & notification-service
COPY common/src ./common/src
COPY notification-service/src ./notification-service/src

# Build notification-service JAR package
RUN mvn clean package -DskipTests -pl notification-service -am

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=builder /app/notification-service/target/*.jar app.jar

EXPOSE 9994

ENTRYPOINT ["java", "-jar", "app.jar"]
