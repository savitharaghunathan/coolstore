# CoolStore Quarkus — multi-stage container image (TG6.1/TG6.2)
# Stage 1: build fast-jar with Maven on UBI8 OpenJDK 17
# Stage 2: minimal Quarkus micro-image runtime, non-root
#
# Build & tag convention (TG6.3): coolstore:<git-sha>, promoted dev → test → prod
# via the OpenShift internal registry (k8s/tekton-pipeline.yaml).
# Image scanning (TG6.4): OpenShift integrated vulnerability scan / Trivy —
# criticals must be remediated before promotion; results recorded in the
# execution report (TG8.7 re-scans the final promoted digest).
FROM registry.access.redhat.com/ubi8/openjdk-17:1.18 AS builder
WORKDIR /project
COPY pom.xml .
COPY src ./src
# Tests run in the Tekton pipeline (before image build), not inside the image build.
RUN mvn -q clean package -DskipTests

# JVM vs native decision (TG6.2): JVM mode for this phase (faster CI, team ramp-up);
# native image documented as a post-launch follow-up.
FROM quay.io/quarkus/quarkus-micro-image:2.0
WORKDIR /work/
COPY --from=builder /project/target/quarkus-app/ /work/
# Runtime env contract (TG6.5) — injected by OpenShift, never baked in:
#   QUARKUS_OIDC_AUTH_SERVER_URL / QUARKUS_OIDC_CLIENT_ID   (Secret coolstore-oidc)
#   QUARKUS_DATASOURCE_JDBC_URL / _USERNAME / _PASSWORD     (Secret coolstore-db)
#   KAFKA_BOOTSTRAP_SERVERS                                   (ConfigMap coolstore-config)
#   QUARKUS_HTTP_CORS_ORIGINS                                 (ConfigMap coolstore-config)
EXPOSE 8080
# Non-root runtime (TG6.2): UID 185 (ubi8 default non-root), container-aware heap.
USER 185
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "quarkus-run.jar"]
