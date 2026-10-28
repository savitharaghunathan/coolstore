# CoolStore (coolstore-quarkus) — multi-stage container image for OpenShift
#
# Build:  podman build -t coolstore:1.0.0 -f Dockerfile .
# Run:    podman run -p 8080:8080 coolstore:1.0.0   (see README.md for env vars)
#
# Stage 1 builds with Maven on JDK 21; the audit-logging-library 2.0.0 dependency
# resolves from the project-local Maven repository (lib/, declared in pom.xml).
# Stage 2 ships only the Quarkus fast-jar tree on a JRE-only image — no Maven,
# no build tools, no source, no lib/ jars, no .rgctl/, no build junk.
#
# PRODUCTION NOTE (task 6.4): pin both base images by digest once validated:
#   skopeo inspect registry.access.redhat.com/ubi9/openjdk-21:latest | jq -r .Digest
#   skopeo inspect registry.access.redhat.com/ubi9/openjdk-21-runtime:latest | jq -r .Digest
# and replace the tags below with registry/...@sha256:<digest>.

# ---------------------------------------------------------------------------
# Stage 1 — builder (Maven + JDK 21)
# ---------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS builder
USER root
WORKDIR /build

# Dependency layer first (cached across source-only changes):
# pom.xml + project-local repo (audit-logging-library 2.0.0)
COPY pom.xml .
COPY lib/ ./lib/
RUN mvn -B -q dependency:go-offline

# Application source (incl. SPA static assets under META-INF/resources)
COPY src/ ./src/

# Tests run in CI; the image build only packages
RUN mvn -B clean package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 — runtime (JRE-only, non-root)
# ---------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest
WORKDIR /deployments

# Quarkus fast-jar: runner + lib/ + static resources
COPY --from=builder --chown=1001:0 /build/target/quarkus-app/ ./

# Container-aware JVM: heap sized from the pod memory limit; JBoss LogManager
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# Security: non-root, fixed UID 1001 (arbitrary-UID safe on OpenShift)
USER 1001

EXPOSE 8080

# Process-level check for plain podman/docker runs (JRE image has no curl, so
# TCP connect only). OpenShift uses the HTTP probes on /q/health/* defined in
# deployment.yaml.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/quarkus-run.jar"]
