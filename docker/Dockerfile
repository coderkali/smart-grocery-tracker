# ══════════════════════════════════════════════════════════════════════════════
# Smart Grocery Tracker — Multi-stage Dockerfile
#
# Stage 1 (deps):    Download dependencies only (layer cached unless pom.xml changes)
# Stage 2 (builder): Compile and package the application
# Stage 3 (runtime): Minimal JRE image — final deployable
#
# Usage:
#   docker build -t smart-grocery-tracker .
#   docker build --target runtime -t smart-grocery-tracker .
# ══════════════════════════════════════════════════════════════════════════════

# ── Stage 1: Dependency cache ──────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS deps
WORKDIR /app
# Copy ONLY the pom.xml first — this layer is cached until pom.xml changes
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -B -q

# ── Stage 2: Build ─────────────────────────────────────────────────────────
FROM deps AS builder
WORKDIR /app
COPY src ./src
RUN ./mvnw package -DskipTests -B -q && \
    # Extract layers for better Docker layer caching
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ── Stage 3: Runtime ───────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy layered JAR components (dependencies first = rarely change = cached)
COPY --from=builder /app/target/extracted/dependencies/          ./
COPY --from=builder /app/target/extracted/spring-boot-loader/    ./
COPY --from=builder /app/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /app/target/extracted/application/           ./

RUN chown -R appuser:appgroup /app
USER appuser

EXPOSE 8080

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dfile.encoding=UTF-8"

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
