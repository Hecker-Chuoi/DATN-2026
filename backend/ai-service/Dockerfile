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
COPY ai-service/pom.xml ./ai-service/

# Copy source of ai-service and its dependency module (common)
COPY common/src ./common/src
COPY ai-service/src ./ai-service/src

RUN mvn clean package -DskipTests -pl ai-service -am

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/ai-service/target/*.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
