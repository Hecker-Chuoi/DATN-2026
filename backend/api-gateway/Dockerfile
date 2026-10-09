# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy pom files for monorepo dependency caching
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY identity-service/pom.xml ./identity-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY work-service/pom.xml ./work-service/
COPY notification-service/pom.xml ./notification-service/
COPY workspace-service/pom.xml ./workspace-service/

# Copy source code of common & api-gateway
COPY common/src ./common/src
COPY api-gateway/src ./api-gateway/src

# Build api-gateway JAR package
RUN mvn clean package -DskipTests -pl api-gateway -am

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/api-gateway/target/*.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
