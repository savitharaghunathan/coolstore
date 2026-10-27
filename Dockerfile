# CoolStore Quarkus — container image (MigIQ containerize stage)
# Multi-stage build on Red Hat UBI9 (see CONTAINERIZATION_REPORT.md for rationale).
#
# Stage 1: build the Quarkus fast-jar with Maven (JDK 17 compiles the Java 11
#          release target defined by maven.compiler.release=11 in pom.xml).
# Stage 2: JRE-only runtime image, non-root, running the fast-jar.
#
# Tests are executed in the Tekton pipeline (k8s/tekton-pipeline.yaml) before
# the image build — hence -DskipTests here.
#
# Build & tag convention (carried over from TG6.3): coolstore:<git-sha>,
# promoted dev -> test -> prod via the OpenShift internal registry.
FROM registry.access.redhat.com/ubi9/openjdk-17:latest AS builder
WORKDIR /build
# Layer-cache dependencies before copying sources
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

# Runtime: UBI9 OpenJDK 17 JRE (matches the JVM mode decision from TG6.2 —
# native image documented as a post-launch follow-up).
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:latest
WORKDIR /work/
COPY --from=builder /build/target/quarkus-app/ /work/

# Runtime env contract (TG6.5) — injected by OpenShift at deploy time, never
# baked into image layers (read by application.properties ${ENV:default}):
#   QUARKUS_DATASOURCE_JDBC_URL / _USERNAME / _PASSWORD   (Secret coolstore-db)
#   QUARKUS_OIDC_AUTH_SERVER_URL / QUARKUS_OIDC_CLIENT_ID (Secret coolstore-oidc)
#   KAFKA_BOOTSTRAP_SERVERS                                (ConfigMap coolstore-config)
#   QUARKUS_HTTP_CORS_ORIGINS                              (ConfigMap coolstore-config)
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

# Security: non-root UID 1001 (UBI9 openjdk default), container-aware heap.
USER 1001
EXPOSE 8080

# Orchestration health checks (liveness/readiness/startup probes on
# /q/health/live and /q/health/ready) are defined in deployment.yaml —
# OpenShift ignores Dockerfile HEALTHCHECK, and the minimal runtime image
# intentionally ships no curl. See CONTAINERIZATION_REPORT.md.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "quarkus-run.jar"]
