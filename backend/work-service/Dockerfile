# Stage 1: Build
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy ALL module poms so the parent pom resolves every declared <module>
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY identity-service/pom.xml ./identity-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY work-service/pom.xml ./work-service/
COPY workspace-service/pom.xml ./workspace-service/
COPY notification-service/pom.xml ./notification-service/

# Copy source of work-service and its dependency module (common)
COPY common/src ./common/src
COPY work-service/src ./work-service/src

# Build work-service JAR package (-am also builds common)
RUN mvn clean package -DskipTests -pl work-service -am

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built jar from builder stage
COPY --from=builder /app/work-service/target/*.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
