# CoolStore (Quarkus 3.8.1, Java 17) — MigIQ containerize stage (Task Group 7)
# Multi-stage build: Maven builder on UBI9 OpenJDK 17 -> minimal UBI9 OpenJDK 17 runtime.
# Runtime runs as non-root UID 1001 (UBI OpenJDK default), matching OpenShift restricted-v2 SCC.

# Pin these via --build-arg in CI (e.g. registry.access.redhat.com/ubi9/openjdk-17:1.20)
ARG BUILDER_IMAGE=registry.access.redhat.com/ubi9/openjdk-17:latest
ARG RUNTIME_IMAGE=registry.access.redhat.com/ubi9/openjdk-17-runtime:latest

# ---------------------------------------------------------------------------
# Stage 1: Build (never ships — runs as root only inside this stage)
# ---------------------------------------------------------------------------
FROM ${BUILDER_IMAGE} AS builder
USER root
WORKDIR /build

# Custom audit library is NOT in any Maven repository: bootstrap it into the
# stage-local ~/.m2 before dependency resolution (mirrors the mig-execute
# `mvn install:install-file` bootstrap recorded in the execution log).
COPY lib/audit-logging-library-2.0.0.jar /tmp/audit-logging-library-2.0.0.jar
RUN mvn -q install:install-file \
      -Dfile=/tmp/audit-logging-library-2.0.0.jar \
      -DgroupId=com.enterprise \
      -DartifactId=audit-logging-library \
      -Dversion=2.0.0 \
      -Dpackaging=jar

# Dependency cache layer: resolve everything the pom declares before copying
# sources, so source-only changes reuse the cached dependency layer.
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline || true

# Sources + build. Tests are intentionally skipped in the image build — they
# must run in CI before the image is built (TG 6.7 pipeline gate); the suite
# currently has 6 known failures tracked by the execute stage.
COPY src ./src
RUN mvn -q -DskipTests package

# ---------------------------------------------------------------------------
# Stage 2: Runtime (UBI9 OpenJDK 17 JRE — default USER 1001, non-root)
# ---------------------------------------------------------------------------
FROM ${RUNTIME_IMAGE}
WORKDIR /deployments

# Quarkus fast-jar (maven-resources-plugin already embedded the AngularJS
# static front end under META-INF/resources inside these layers).
COPY --from=builder --chown=1001:0 /build/target/quarkus-app/ /deployments/

# Configuration is env-driven only (ConfigMaps/Secrets in OpenShift):
#   QUARKUS_DATASOURCE_JDBC_URL / _USERNAME / _PASSWORD   (Secret)
#   QUARKUS_OIDC_AUTH_SERVER_URL / _CLIENT_ID              (ConfigMap)
#   QUARKUS_OIDC_CREDENTIALS_SECRET                        (Secret)
#   KAFKA_BOOTSTRAP_SERVERS                                (ConfigMap)
# No secrets are baked into any image layer.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# UBI OpenJDK images declare USER 1001 (non-root); re-assert for clarity.
USER 1001

EXPOSE 8080

# NOTE: no Dockerfile HEALTHCHECK — OpenShift uses the liveness/readiness
# probes defined in deployment.yaml (/q/health/live, /q/health/ready).
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
