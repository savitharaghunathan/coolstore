# CoolStore (Quarkus 3.8.1, Java 17) — MigIQ containerize-stage deliverable
# Build:  podman build -t coolstore:1.0.0 -f Dockerfile .
# Run:    podman run -p 8080:8080 coolstore:1.0.0   (all config env-injected — see below)
# Base images: Red Hat UBI9 (registry.access.redhat.com — no pull secret needed), tags verified.
# Supersedes the migration-stage Containerfile (kept in repo history); UBI9 is the
# current long-lived UBI major (skill guidance: language-specific UBI image, pinned tags).

# ---- Stage 1: Maven build on UBI9 OpenJDK 17 (JDK) ----
FROM registry.access.redhat.com/ubi9/openjdk-17:1.21-2 AS builder
USER root
WORKDIR /build
# Dependency layer (cached unless pom.xml changes)
COPY pom.xml .
RUN mvn -q -e dependency:go-offline
# Source layer
COPY src ./src
# CI gate: tests run in CI before image build; -DskipTests keeps image builds fast
RUN mvn -q package -DskipTests

# ---- Stage 2: minimal hardened runtime (UBI9 OpenJDK 17 JRE, non-root) ----
FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:1.22-1
LABEL io.k8s.description="CoolStore storefront (Quarkus 3.8.1)" \
      io.k8s.display-name="coolstore" \
      io.openshift.tags="quarkus,ubi9,coolstore" \
      maintainer="CoolStore Team"
WORKDIR /deployments
COPY --from=builder /build/target/quarkus-app ./quarkus-app
# curl is absent from the minimal JRE image but needed for the container-level
# HEALTHCHECK below (Kubernetes probes in deployment.yaml remain the primary mechanism)
USER root
RUN microdnf install -y curl && microdnf clean all
# Default non-root user of the UBI OpenJDK images (skill: run as non-root)
USER 185
# All runtime configuration is env-injected — no secrets baked into image layers.
# %prod profile activates: PostgreSQL, Kafka, Keycloak OIDC (see application.properties).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" \
    QUARKUS_DATASOURCE_URL="" \
    QUARKUS_DATASOURCE_USERNAME="" \
    QUARKUS_DATASOURCE_PASSWORD="" \
    KAFKA_BOOTSTRAP_SERVERS="" \
    COOLSTORE_OIDC_AUTH_SERVER_URL="" \
    COOLSTORE_OIDC_SECRET="" \
    COOLSTORE_CORS_ORIGINS=""
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -fsS http://localhost:8080/q/health/ready || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/quarkus-app/quarkus-run.jar"]
