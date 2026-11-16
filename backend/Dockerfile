# ==============================================================================
# Fairish Chat Backend — Production Multi-Stage Dockerfile
# ==============================================================================

# Stage 1: Build the application JAR with an image that already contains Maven.
# The repository does not vendor a Maven distribution and has no .mvn wrapper
# directory, so relying on ./mvnw here would make the image build fail.
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy the POM first so dependency resolution is cached independently of source.
COPY pom.xml ./

# Pre-fetch dependencies to keep rebuilds fast. A transient repository failure
# must not fail the image build; the real build below will surface it.
RUN mvn -B dependency:go-offline || true

# Copy the source and build the executable (repackaged) JAR.
COPY src/ src/
RUN mvn -B clean package -DskipTests

# Stage 2: Lightweight, secure JRE runtime
FROM eclipse-temurin:21-jre-jammy

# curl is required for the container health check. The base JRE image does not
# guarantee it, so install it explicitly and clean the apt cache.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root system group and user
RUN groupadd -r appgroup && useradd -r -g appgroup -u 10001 -d /app appuser

WORKDIR /app

# Copy the built artifact from builder
COPY --from=builder /build/target/fairish-chat-backend-*.jar app.jar

# Enforce file permissions
RUN chown -R appuser:appgroup /app

# Switch to unprivileged user
USER 10001:10001

EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
