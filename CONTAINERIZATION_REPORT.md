# Containerization Report: CoolStore (Quarkus)

**Stage**: MigIQ containerize | **Date**: 2026-10-27 | **Branch**: `konveyor/migration-1793137290`

## Executive Summary

- **Application**: CoolStore monolith — storefront + REST services (`/services`), migrated from Java EE 7 / JBoss EAP 7.4 to Quarkus 3.8.1 (Java 11 release target) by the prior mig-execute stage (TG1–TG8 code work complete).
- **Language/Framework**: Java 11 / Quarkus 3.8.1 (RESTEasy Reactive, Hibernate ORM, SmallRye Reactive Messaging Kafka, OIDC, Flyway, Micrometer) + AngularJS static frontend served from `META-INF/resources`.
- **Base Image**: `registry.access.redhat.com/ubi9/openjdk-17` (builder) → `ubi9/openjdk-17-runtime` (runtime).
- **Deployment Target**: OpenShift (namespace `coolstore-dev`), plus podman-compose for local development.
- **Build Time / Image Size / Security Scan**: **Not measured in this environment** — no container runtime, Java, Maven, or scanner available locally (same constraint recorded in the mig-execute report). Image build, tests, and scanning are delegated to the committed Tekton pipeline (`k8s/tekton-pipeline.yaml`: test → secret-scan → buildah) and/or OpenShift integrated vulnerability scanning. This is the CI gate for promotion (TG6.4).

## Graph Analysis Findings (rgctl)

Index re-built for this stage (`rgctl discover . --with-cfg --with-security --with-taint --with-harmonic`) — no prior index existed.

### Application Structure
- **Nodes**: 37,177 (26,977 functions, 251 classes, 14 interfaces, 1,493 modules)
- **Edges**: 129,381 (32,119 calls; 384 uses; 1,834 instantiates)
- **Communities**: 24,779 (modularity 0.47) — the count is dominated by vendored `bower_components` JS; the Java application itself clusters into one cohesive set (`coolstore.model`, `coolstore.service`, `coolstore.rest`, `coolstore.utils`)
- **Primary Language**: Java (src/main/java) + vendored JavaScript frontend assets
- **Framework**: Quarkus 3.8.1 (pom.xml; confirmed by `uses` edges to `io.quarkus.test.*`, `jakarta.persistence.*`)

### External Dependencies (from graph + pom.xml + application.properties)
| Dependency | Usage | Containerization decision |
|---|---|---|
| PostgreSQL | `%prod` datasource, Flyway V1_1/V1_2 migrations | StatefulSet for dev/test (`database-statefulset.yaml`); managed/operator DB for production; creds via Secret `coolstore-db` |
| Kafka (AMQ Streams) | `orders` topic: producer `ShoppingCartOrderProcessor`, consumers `OrderServiceMDB` (group `coolstore-orders`) and `InventoryNotificationMDB` (group `coolstore-inventory`), DLQs `orders-dlq`/`inventory-dlq` | Strimzi `KafkaTopic` CRs (`kafka-topics.yaml`); bootstrap via ConfigMap |
| Keycloak (OIDC) | Bearer-token validation on `/services`; realm `eap`, public client `eap-app` (no client secret) | Provisioned by Keycloak Operator from `realm-export.json`; issuer URL via Secret `coolstore-oidc` |
| H2 / in-memory Kafka | `%dev`/`%test` profiles only | Not shipped in the image's `%prod` runtime path |

### Platform-Specific Code Identified (blockers scan)
- **Hardcoded paths**: none in Java source (zero `FileOutputStream`/`new File`/`Files.` usage — **no PVC required** for the application workload; only an `emptyDir` at `/tmp` for JVM/Kafka client temp on the read-only rootfs).
- **Localhost assumptions**: only as env-overridable defaults in `application.properties` (`${QUARKUS_DATASOURCE_JDBC_URL:jdbc:postgresql://localhost:5432/coolstore}`, `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}`, `${QUARKUS_OIDC_AUTH_SERVER_URL:http://localhost:8081/realms/eap}`) — externalized via ConfigMap/Secret at deploy time. Frontend `keycloak.json` still defaults to `http://localhost:8081/` (recorded by execute stage for the deploy stage to override via ConfigMap).
- **Secrets in config**: application config is clean (empty dev password; all `%prod` credentials env-injected). The 6 "potential secrets" flagged by rgctl `--with-security` trace to vendored frontend assets (e.g., `bower_components/angular-patternfly/deploy_key.enc`, an encrypted artifact) and realm metadata — not application credentials. The Tekton secret-scan step gates promotion regardless.
- **Taint flows**: 1 flagged (vulnerable) — external Kafka message → log statement in the `OrderServiceMDB.onMessage` path (`Log.debugf` of the raw order payload). **Log-injection risk only; no filesystem or exec sink → not a containerization blocker.** Tracked as a security follow-up.
- **Windows/OS-specific code**: none.

### Migration Complexity: **Low (for containerization)**
Single deployable unit (one Maven module, one fast-jar), no filesystem persistence, fully env-driven configuration, SmallRye Health endpoints already present (`/q/health/live`, `/q/health/ready`), metrics at `/q/metrics`. The high coupling inside `coolstore.service` (PageRank hotspots) affects runtime scaling, not packaging.

## Containerization Strategy

### Base Image Selection
- **Builder**: `registry.access.redhat.com/ubi9/openjdk-17:latest` — ships Maven + JDK 17; compiles the Java 11 release target (`maven.compiler.release=11`).
- **Runtime**: `registry.access.redhat.com/ubi9/openjdk-17-runtime:latest` — JRE-only UBI9, smaller attack surface than the JDK image.
- **Reason**: official Red Hat UBI (supported, patched, enterprise-compliant) per the mig-containerize skill; JVM mode per TG6.2 (native image is a documented post-launch follow-up).
- **Alternatives considered**: `quay.io/quarkus/quarkus-micro-image:2.0` (used by the execute-stage `Containerfile`) — kept as a fallback; UBI9 openjdk-runtime chosen for this stage's canonical `Dockerfile` to stay on `registry.access.redhat.com` UBI. **Production hardening note**: pin digest/version tags instead of `latest` in your registry mirror once validated.

### Build Strategy
- **Type**: Multi-stage (2 stages).
- **Stage 1**: `mvn -B dependency:go-offline` (layer-cached) → `mvn -B clean package -DskipTests` → fast-jar in `target/quarkus-app/`.
- **Stage 2**: copies only `quarkus-app/` into the JRE image; `ENTRYPOINT java -XX:MaxRAMPercentage=75.0 -jar quarkus-run.jar` (container-aware heap, carried over from TG6.2).
- **Tests**: run in the Tekton pipeline *before* image build (not inside the image build).

### Security Hardening Applied
- ✅ Multi-stage build (no Maven/JDK/build sources in the runtime image)
- ✅ Non-root execution: `USER 1001` (Dockerfile) + `runAsUser: 1001`, `runAsNonRoot: true` (Deployment)
- ✅ Read-only root filesystem + `emptyDir` at `/tmp`
- ✅ All capabilities dropped; `allowPrivilegeEscalation: false`; `seccompProfile: RuntimeDefault` (restricted-v2 compatible)
- ✅ Secrets externalized (Secret templates with `REPLACE_ME`; real secrets created out-of-band — never in Git)
- ✅ `.dockerignore` excludes `.git`, `.rgctl`, workspaces, docs, `target/`, vendored `lib/` jars
- ✅ Resource requests/limits on every workload; HPA + PDB for availability
- ✅ Image scanning: delegated to OpenShift integrated scan / Tekton `trivy-scan` step (criticals block promotion — TG6.4)
- ℹ️ Dockerfile `HEALTHCHECK` intentionally omitted: OpenShift ignores it and the minimal runtime image ships no curl; health is enforced by the manifest probes below.

## Generated Artifacts (all at repo root)

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage UBI9 image build (canonical; execute-stage `Containerfile` retained as fallback) |
| `.dockerignore` | Minimal build context (src + pom.xml only) |
| `serviceaccount.yaml` | Dedicated least-privilege service account |
| `configmap.yaml` | Non-sensitive config (JDBC URL, Kafka bootstrap, CORS origin) |
| `secret.yaml` | Secret templates `coolstore-db`, `coolstore-oidc` (placeholders only) |
| `deployment.yaml` | Hardened workload: 2 replicas, probes, securityContext, envFrom contract |
| `service.yaml` | ClusterIP service :8080 |
| `route.yaml` | Edge-TLS Route with HTTPS redirect |
| `kafka-topics.yaml` | Strimzi `orders`, `orders-dlq`, `inventory-dlq` |
| `hpa.yaml` / `pdb.yaml` | Autoscale 2→6 @75% CPU; min 1 available during drains |
| `database-statefulset.yaml` | Dev/test PostgreSQL 15 + headless service + 10Gi PVC |
| `podman-compose.yml` | Local dev: app + PostgreSQL + single-node Kafka + Keycloak (realm import) |

The execute-stage manifests in `k8s/` (incl. Tekton pipeline) remain authoritative for CI; the repo-root set above is the containerize-stage deliverable with equivalent (and further hardened) pod security.

## Deployment Instructions

### Prerequisites
1. `oc` CLI logged into the OpenShift cluster
2. AMQ Streams operator (for `KafkaTopic` CRs); Keycloak operator (realm `eap` from `realm-export.json`)
3. Image built and pushed (Tekton `coolstore-ci`, or manually below)

### Steps
```bash
# 1. Namespace
oc new-project coolstore-dev   # or edit namespace fields to taste

# 2. Real secrets (out-of-band — never commit)
oc create secret generic coolstore-db   --from-literal=QUARKUS_DATASOURCE_USERNAME='<db-user>' --from-literal=QUARKUS_DATASOURCE_PASSWORD='<db-password>'
oc create secret generic coolstore-oidc --from-literal=QUARKUS_OIDC_AUTH_SERVER_URL='https://<keycloak>/realms/eap' --from-literal=QUARKUS_OIDC_CLIENT_ID='eap-app'

# 3. Build & push the image
podman build -t image-registry.openshift-image-registry.svc:5000/coolstore-dev/coolstore:latest -f Dockerfile .
podman login image-registry.openshift-image-registry.svc:5000 -u $(oc whoami) -p $(oc whoami -t)
podman push image-registry.openshift-image-registry.svc:5000/coolstore-dev/coolstore:latest

# 4. Apply (order matters for first roll-out)
oc apply -f serviceaccount.yaml
oc apply -f configmap.yaml
oc apply -f database-statefulset.yaml   # dev/test only
oc apply -f kafka-topics.yaml           # requires AMQ Streams operator
oc apply -f deployment.yaml
oc apply -f service.yaml
oc apply -f route.yaml
oc apply -f hpa.yaml
oc apply -f pdb.yaml

# 5. Verify
oc get pods -l app=coolstore
oc logs deployment/coolstore | head
curl -f https://$(oc get route coolstore -o jsonpath='{.spec.host}')/q/health/ready
```
`secret.yaml` is a template — apply it only after replacing placeholders, or skip it in favor of step 2.

## Configuration Reference

| Variable | Description | Source | Required |
|---|---|---|---|
| `QUARKUS_DATASOURCE_JDBC_URL` | PostgreSQL connection string | ConfigMap `coolstore-config` | Yes (`%prod`) |
| `QUARKUS_DATASOURCE_USERNAME` / `_PASSWORD` | DB credentials | Secret `coolstore-db` | Yes (`%prod`) |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka bootstrap (`my-cluster-kafka-bootstrap:9092`) | ConfigMap | Yes (`%prod`) |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | Keycloak issuer (realm `eap`) | Secret `coolstore-oidc` | Yes (`%prod`) |
| `QUARKUS_OIDC_CLIENT_ID` | OIDC client (`eap-app`, public) | Secret `coolstore-oidc` | Yes (`%prod`) |
| `QUARKUS_HTTP_CORS_ORIGINS` | Allowed storefront origin (Route URL) | ConfigMap | Yes (`%prod`) |

**Resources** — app: 250m/256Mi requests, 1 CPU/512Mi limits. DB (dev/test): 250m/256Mi → 1 CPU/1Gi, 10Gi PVC.

## Health and Monitoring
- **Liveness**: `GET /q/health/live` (initial 10s, period 10s)
- **Readiness**: `GET /q/health/ready` (initial 15s, period 10s — fails on missing DB/Kafka per TG6.6)
- **Startup**: `GET /q/health/live` (period 2s, up to 30 failures)
- **Metrics**: `GET /q/metrics` (Micrometer/Prometheus) — scrape via the OpenShift monitoring stack

## Rollback Procedure
```bash
oc rollout history deployment/coolstore
oc rollout undo deployment/coolstore            # or --to-revision=N
oc get pods -l app=coolstore && oc logs deployment/coolstore
```
Legacy JBoss deployment path remains documented in `README.md` for the 30-day rollback window (TG8.8).

## Known Issues and Limitations
1. **No local build/verification** — this environment has no podman/docker/mvn/java/grype (as recorded by mig-execute). Dockerfile and manifests are statically verified (YAML parsed, layering/env contract cross-checked against `application.properties`). **CI must run `mvn clean verify` + image build + scan before promotion.**
2. **`latest` tags** — builder/runtime/DB images use `latest`; pin to digests in production mirrors.
3. **Frontend `keycloak.json`** still points at `http://localhost:8081/` (execute-stage note) — deploy stage to override via ConfigMap (TG4.3/TG7.4).
4. **1 taint flow** (log sink in `OrderServiceMDB`) — security follow-up, not a container blocker.
5. **`secret.yaml` placeholders** — real credentials must be created out-of-band.
6. **Database StatefulSet is dev/test-grade** — production uses operator/managed PostgreSQL.

## Next Steps
1. ✅ Containerization artifacts complete (this stage)
2. ⏳ CI: `mvn clean verify`, image build + vulnerability scan (Tekton `coolstore-ci`)
3. ⏳ mig-deploy stage: apply manifests, provision Keycloak/PostgreSQL/AMQ Streams, E2E parity (TG8.1), DLQ demo (TG8.2), performance baseline (TG8.3)
4. ⏳ Post-launch: native image evaluation (TG6.2 follow-up), burn down `TODO(port)` markers

## Support and Resources
- Red Hat UBI images: https://catalog.redhat.com/software/containers/search
- OpenShift docs: https://docs.openshift.com/
- Podman: https://docs.podman.io/
- Quarkus container guide: https://quarkus.io/guides/container-image
