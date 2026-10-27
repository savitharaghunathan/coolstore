# CoolStore Quarkus — multi-stage container build (migration TG8.1/TG8.2)
# Build:  podman build -t coolstore:dev -f Containerfile .
# Run:    podman run -p 8080:8080 --env QUARKUS_DATASOURCE_URL=... coolstore:dev
# All runtime config is env-injected (no secrets baked in): QUARKUS_DATASOURCE_URL /
# USERNAME / PASSWORD, KAFKA_BOOTSTRAP_SERVERS, COOLSTORE_OIDC_AUTH_SERVER_URL, COOLSTORE_OIDC_SECRET.

# ---- Stage 1: Maven build on UBI8 OpenJDK 17 ----
FROM registry.access.redhat.com/ubi8/openjdk-17:latest AS builder
USER root
WORKDIR /build
# Dependency layer (cached unless pom.xml changes)
COPY pom.xml .
RUN mvn -q -e dependency:go-offline
# Source layer
COPY src ./src
# CI gate: tests run in CI before image build; -DskipTests keeps image builds fast
RUN mvn -q package -DskipTests

# ---- Stage 2: minimal runtime (UBI8 OpenJDK 17 runtime, non-root) ----
FROM registry.access.redhat.com/ubi8/openjdk-17-runtime:latest
LABEL io.k8s.description="CoolStore storefront (Quarkus 3.8.1)" \
      io.k8s.display-name="coolstore" \
      io.openshift.tags="quarkus,coolstore" \
      maintainer="CoolStore Team"
WORKDIR /deployments
COPY --from=builder /build/target/quarkus-app ./quarkus-app
EXPOSE 8080
USER 185
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /deployments/quarkus-app/quarkus-run.jar"]
