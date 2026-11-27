# syntax=docker/dockerfile:1
# Stage 1: Build (build context = backend/)
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
# Build trên máy chủ dùng chung với cluster: giới hạn heap Maven
ENV MAVEN_OPTS="-Xmx768m"

# Parent pom liệt kê mọi module nên phải COPY đủ 6 pom
COPY pom.xml ./
COPY common/pom.xml ./common/
COPY identity-service/pom.xml ./identity-service/
COPY api-gateway/pom.xml ./api-gateway/
COPY work-service/pom.xml ./work-service/
COPY workspace-service/pom.xml ./workspace-service/
COPY notification-service/pom.xml ./notification-service/

# Source của common và notification-service
COPY common/src ./common/src
COPY notification-service/src ./notification-service/src

# Cache ~/.m2 giữa các lần build (BuildKit)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests -pl notification-service -am

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=builder /app/notification-service/target/*.jar app.jar
USER 10001
EXPOSE 9994
ENTRYPOINT ["java", "-jar", "app.jar"]
