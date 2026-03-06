FROM maven:3.9-jdk-21-slim as builder

WORKDIR /app
COPY mvnw mvnw
COPY .mvn .mvn
COPY pom.xml .
COPY src src

RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar

ENV SERVER_PORT=8110 \
    MANAGEMENT_SERVER_PORT=8181 \
    SERVER_SERVLET_CONTEXT_PATH=/api/v1

EXPOSE 8110 8181

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
  CMD curl -f http://localhost:8181/actuator/health || exit 1
